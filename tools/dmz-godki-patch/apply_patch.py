"""apply_patch.py <srcDir> [--fixup-only]: the godki copy / divine factor patch on decompiled DMZ 2.2.1 sources."""
import sys

ROOT = sys.argv[1]
SD = ROOT + "/com/dragonminez/common/stats/StatsData.java"
SH = ROOT + "/com/dragonminez/server/events/players/actionmode/StackFormModeHandler.java"


def rep(text, old, new):
    assert text.count(old) == 1, old[:80]
    return text.replace(old, new)


s = open(SD, encoding="utf-8").read()

# Decompiler fixup (not a behaviour change): Vineflower reuses the name "mastery" for the stat key
# string and the mastery double in getFormMultiplier, which javac rejects.
if "String mastery = statName.toUpperCase();" in s:
    s = rep(s, "String mastery = statName.toUpperCase();", "String statKey = statName.toUpperCase();")
    s = rep(s, "double baseMult = switch (mastery) {", "double baseMult = switch (statKey) {")
if "--fixup-only" in sys.argv:
    open(SD, "w", encoding="utf-8").write(s)
    print("fixup applied")
    sys.exit(0)

# 5. Divine: a factor on the combined total, after the add-or-multiply step.
s = rep(s, """      double kiShare = GodRitualHelper.kiShareMultiplier(this, statName);
      return ConfigManager.getServerConfig().getGameplay().getMultiplicationInsteadOfAdditionForMultipliers()
         ? form * stack * effect * secondary * kiShare
         : (1.0 + (form - 1.0) + (stack - 1.0) + (effect - 1.0) + (secondary - 1.0)) * kiShare;
   }""", """      double kiShare = GodRitualHelper.kiShareMultiplier(this, statName);
      double total = ConfigManager.getServerConfig().getGameplay().getMultiplicationInsteadOfAdditionForMultipliers()
         ? form * stack * effect * secondary * kiShare
         : (1.0 + (form - 1.0) + (stack - 1.0) + (effect - 1.0) + (secondary - 1.0)) * kiShare;
      return total * this.getDivineKiFactor();
   }""")

# 3. God Ki standalone: no base-form multiplier while the godki form is active.
s = rep(s, """   public double getFormMultiplier(String statName) {
      String currentForm = this.character.getActiveForm();""", """   public double getFormMultiplier(String statName) {
      if (this.isGodKiCopyActive()) {
         return 1.0;
      }

      String currentForm = this.character.getActiveForm();""")

# 1, 2, 5. Stack multiplier: godki copies the strongest god form; divineki's own multipliers are inert.
s = rep(s, """            if (formData == null) {
               return 1.0;
            } else if ("ultimate".equalsIgnoreCase(currentFormGroup) && !ConfigManager.getServerConfig().getGameplay().getUltimateFormFixedValue()) {""",
        """            if (formData == null) {
               return 1.0;
            } else if (this.isGodKiCopyActive()) {
               return this.getGodKiCopyMultiplier(statName);
            } else if (this.isDivineKiActive()) {
               return 1.0;
            } else if ("ultimate".equalsIgnoreCase(currentFormGroup) && !ConfigManager.getServerConfig().getGameplay().getUltimateFormFixedValue()) {""")

HELPERS = '''
   private static final String GODKI_GROUP = "godki";
   private static final String GODKI_FORM = "godki";
   private static final String DIVINEKI_FORM = "divineki";
   private static final double DIVINEKI_FULL_MASTERY = 5000.0;

   private boolean isGodKiStackForm(String form) {
      return this.character.hasActiveStackForm()
         && GODKI_GROUP.equalsIgnoreCase(this.character.getActiveStackFormGroup())
         && form.equalsIgnoreCase(this.character.getActiveStackForm());
   }

   public boolean isGodKiCopyActive() {
      return this.isGodKiStackForm(GODKI_FORM);
   }

   public boolean isDivineKiActive() {
      return this.isGodKiStackForm(DIVINEKI_FORM);
   }

   public static boolean isDivineKiForm(FormData form) {
      return form != null && DIVINEKI_FORM.equalsIgnoreCase(form.getName());
   }

   public static boolean isGodFormGroup(String group, FormConfig config) {
      if (group != null) {
         String g = group.toLowerCase(Locale.ROOT);
         if (g.equals("godforms") || g.equals("biogodforms") || g.equals("divineforms") || g.equals("evildivineforms")) {
            return true;
         }
      }

      String type = config == null ? null : config.getFormType();
      return type != null && type.toLowerCase(Locale.ROOT).contains("godform");
   }

   public boolean isInGodForm() {
      if (!this.character.hasActiveForm()) {
         return false;
      } else {
         String group = this.character.getActiveFormGroup();
         if (group == null || group.isEmpty()) {
            return false;
         } else {
            FormConfig config = ConfigManager.getFormGroup(this.character.getRaceName(), group);
            if (config == null) {
               Map<String, FormConfig> all = FusionForms.allFormGroups(this);
               config = all == null ? null : all.get(group);
            }

            return isGodFormGroup(group, config);
         }
      }
   }

   public boolean canActivateDivineKi() {
      return !this.isGodKiCopyActive() && this.isInGodForm();
   }

   private double getDivineKiFactor() {
      if (this.isDivineKiActive() && this.isInGodForm()) {
         double mastery = this.character.getStackFormMasteries().getMastery(GODKI_GROUP, DIVINEKI_FORM);
         double m = Double.isNaN(mastery) ? 0.0 : Math.max(0.0, Math.min(DIVINEKI_FULL_MASTERY, mastery));
         return 1.0 + 0.01 + 0.04 * (m / DIVINEKI_FULL_MASTERY);
      } else {
         return 1.0;
      }
   }

   private double getGodKiCopyMultiplier(String statName) {
      Map<String, FormConfig> groups = FusionForms.allFormGroups(this);
      if (groups == null || groups.isEmpty()) {
         return 1.0;
      } else {
         String race = this.character.getRaceName();
         String[] six = new String[]{"STR", "SKP", "DEF", "VIT", "PWR", "ENE"};
         FormData bestForm = null;
         String bestGroup = null;
         double bestAvg = -1.0;
         FormData lowestForm = null;
         double lowestAvg = Double.MAX_VALUE;

         for (Entry<String, FormConfig> entry : groups.entrySet()) {
            String group = entry.getKey();
            FormConfig config = entry.getValue();
            if (config != null && isGodFormGroup(group, config)) {
               for (FormData form : TransformationsHelper.getUnlockedForms(this, race, group)) {
                  if (form != null && !form.isIncompatibleWith(GODKI_GROUP, GODKI_FORM)) {
                     double mastery = this.character.getFormMasteries().getMastery(group, form.getName());
                     double sum = 0.0;

                     for (String stat : six) {
                        sum += this.getMasteryAdjustedMultiplier(form, stat, mastery);
                     }

                     double avg = sum / 6.0;
                     if (avg > bestAvg) {
                        bestAvg = avg;
                        bestForm = form;
                        bestGroup = group;
                     }
                  }
               }

               if (config.getForms() != null) {
                  for (FormData form : config.getForms().values()) {
                     if (form != null && !form.isIncompatibleWith(GODKI_GROUP, GODKI_FORM)) {
                        double sum = 0.0;

                        for (String stat : six) {
                           sum += this.getBaseFormMultiplier(form, stat);
                        }

                        double avg = sum / 6.0;
                        if (avg < lowestAvg) {
                           lowestAvg = avg;
                           lowestForm = form;
                        }
                     }
                  }
               }
            }
         }

         if (bestForm != null) {
            double bestMastery = this.character.getFormMasteries().getMastery(bestGroup, bestForm.getName());
            return this.getMasteryAdjustedMultiplier(bestForm, statName, bestMastery);
         } else {
            return lowestForm != null ? this.getBaseFormMultiplier(lowestForm, statName) : 1.0;
         }
      }
   }

   public double getStackFormMultiplier(String statName) {'''
s = rep(s, "\n   public double getStackFormMultiplier(String statName) {", HELPERS)
if "import java.util.Locale;" not in s:
    s = rep(s, "import java.util.Map.Entry;", "import java.util.Locale;\nimport java.util.Map.Entry;")
open(SD, "w", encoding="utf-8").write(s)

h = open(SH, encoding="utf-8").read()
# canCharge: no charge-up into Divine Ki on God Ki or off a god form.
h = rep(h, """      FormData nextForm = TransformationsHelper.getNextAvailableStackForm(data);
      if (nextForm == null) {
         return false;
      } else {
         if (data.getCharacter().hasActiveForm()) {""", """      FormData nextForm = TransformationsHelper.getNextAvailableStackForm(data);
      if (nextForm == null) {
         return false;
      } else if (StatsData.isDivineKiForm(nextForm) && !data.canActivateDivineKi()) {
         return false;
      } else {
         if (data.getCharacter().hasActiveForm()) {""")
# attemptTransform: refuse with the existing not_stackable message.
h = rep(h, """      FormData nextForm = TransformationsHelper.getNextAvailableStackForm(data);
      if (nextForm != null) {
         if (data.getCharacter().hasActiveForm()) {""", """      FormData nextForm = TransformationsHelper.getNextAvailableStackForm(data);
      if (nextForm != null) {
         if (StatsData.isDivineKiForm(nextForm) && !data.canActivateDivineKi()) {
            player.m_5661_(Component.m_237115_("message.dragonminez.form.not_stackable"), true);
            return;
         }

         if (data.getCharacter().hasActiveForm()) {""")
open(SH, "w", encoding="utf-8").write(h)
print("patched")
