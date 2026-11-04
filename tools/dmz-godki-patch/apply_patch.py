"""apply_patch.py <srcDir> [--fixup-only]: the godki copy / divine patch (v3) on decompiled DMZ 2.2.1 sources."""
import sys

ROOT = sys.argv[1]
SD = ROOT + "/com/dragonminez/common/stats/StatsData.java"
SH = ROOT + "/com/dragonminez/server/events/players/actionmode/StackFormModeHandler.java"
FH = ROOT + "/com/dragonminez/server/events/players/actionmode/FormModeHandler.java"
EA = ROOT + "/com/dragonminez/common/network/C2S/ExecuteActionC2S.java"


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

# 3. God Ki standalone: no base-form multiplier while God Ki is copying. With no god form to copy
#    (no pool for the race), isGodKiCopyActive is false and God Ki is the stock stack form.
s = rep(s, """   public double getFormMultiplier(String statName) {
      String currentForm = this.character.getActiveForm();""", """   public double getFormMultiplier(String statName) {
      if (this.isGodKiCopyActive()) {
         return 1.0;
      }

      String currentForm = this.character.getActiveForm();""")

# 1, 2, 5. Stack multiplier: godki copies the strongest god form. Divine on a god form: stack term 1.0,
#    the factor does the work. Divine on a normal form: falls through to its mastery-adjusted config.
s = rep(s, """            if (formData == null) {
               return 1.0;
            } else if ("ultimate".equalsIgnoreCase(currentFormGroup) && !ConfigManager.getServerConfig().getGameplay().getUltimateFormFixedValue()) {""",
        """            if (formData == null) {
               return 1.0;
            } else if (this.isGodKiCopyActive()) {
               return this.getGodKiCopyMultiplier(statName);
            } else if (this.isDivineKiActive() && this.isInGodForm()) {
               return 1.0;
            } else if ("ultimate".equalsIgnoreCase(currentFormGroup) && !ConfigManager.getServerConfig().getGameplay().getUltimateFormFixedValue()) {""")

HELPERS = '''
   private static final String GODKI_GROUP = "godki";
   private static final String GODKI_FORM = "godki";
   private static final String DIVINEKI_FORM = "divineki";
   private static final double DIVINEKI_FULL_MASTERY = 10000.0;
   private static Method kaiPathManaged;
   private static boolean kaiPathLooked;

   private boolean isGodKiStackForm(String form) {
      return this.character.hasActiveStackForm()
         && GODKI_GROUP.equalsIgnoreCase(this.character.getActiveStackFormGroup())
         && form.equalsIgnoreCase(this.character.getActiveStackForm());
   }

   public boolean isGodKiCopyActive() {
      return this.isGodKiStackForm(GODKI_FORM) && this.hasGodKiCopy();
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
      return !this.isGodKiCopyActive() && this.character.hasActiveForm() && !this.isInBaseForm();
   }

   public static boolean isGodKiForm(FormData form) {
      return form != null && GODKI_FORM.equalsIgnoreCase(form.getName());
   }

   public boolean isInBaseForm() {
      return !this.character.hasActiveForm() || "base".equals(this.character.getActiveForm());
   }

   public boolean canActivateGodKi() {
      return this.isInBaseForm() || !this.hasGodKiCopy();
   }

   public boolean hasGodKiCopy() {
      return this.findGodKiCopySource() != null;
   }

   private static boolean isKaiPathDivineGroup(String group) {
      if (!kaiPathLooked) {
         kaiPathLooked = true;

         try {
            kaiPathManaged = Class.forName("com.dmzlegacy.kaipath.DivineForms", true, StatsData.class.getClassLoader())
               .getMethod("managed", String.class);
         } catch (Throwable var2) {
            kaiPathManaged = null;
         }
      }

      if (kaiPathManaged != null && group != null) {
         try {
            return Boolean.TRUE.equals(kaiPathManaged.invoke(null, group));
         } catch (Throwable var3) {
            return false;
         }
      } else {
         return false;
      }
   }

   private double getDivineKiFactor() {
      if (this.isDivineKiActive() && this.isInGodForm()) {
         double mastery = this.character.getStackFormMasteries().getMastery(GODKI_GROUP, DIVINEKI_FORM);
         double m = Double.isNaN(mastery) ? 0.0 : Math.max(0.0, Math.min(DIVINEKI_FULL_MASTERY, mastery));
         return 1.0 + 0.01 + 0.09 * (m / DIVINEKI_FULL_MASTERY);
      } else {
         return 1.0;
      }
   }

   private double getGodKiCopyMultiplier(String statName) {
      Object[] source = this.findGodKiCopySource();
      if (source == null) {
         return 1.0;
      } else {
         FormData form = (FormData)source[0];
         if ((Boolean)source[2]) {
            double mastery = this.character.getFormMasteries().getMastery((String)source[1], form.getName());
            return this.getMasteryAdjustedMultiplier(form, statName, mastery);
         } else {
            return this.getBaseFormMultiplier(form, statName);
         }
      }
   }

   private Object[] findGodKiCopySource() {
      Map<String, FormConfig> groups = FusionForms.allFormGroups(this);
      if (groups == null || groups.isEmpty()) {
         return null;
      } else {
         String race = this.character.getRaceName();
         String[] six = new String[]{"STR", "SKP", "DEF", "VIT", "PWR", "ENE"};
         FormData bestForm = null;
         String bestGroup = null;
         double bestAvg = -1.0;
         FormData lowestForm = null;
         String lowestGroup = null;
         double lowestAvg = Double.MAX_VALUE;

         for (Entry<String, FormConfig> entry : groups.entrySet()) {
            String group = entry.getKey();
            FormConfig config = entry.getValue();
            boolean godGroup = config != null && isGodFormGroup(group, config);
            boolean kaiPathGroup = config != null && !godGroup && isKaiPathDivineGroup(group);
            if (godGroup || kaiPathGroup) {
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

               if (godGroup && config.getForms() != null) {
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
                           lowestGroup = group;
                        }
                     }
                  }
               }
            }
         }

         if (bestForm != null) {
            return new Object[]{bestForm, bestGroup, true};
         } else {
            return lowestForm != null ? new Object[]{lowestForm, lowestGroup, false} : null;
         }
      }
   }

   public double getStackFormMultiplier(String statName) {'''
s = rep(s, "\n   public double getStackFormMultiplier(String statName) {", HELPERS)
if "import java.lang.reflect.Method;" not in s:
    s = rep(s, "import java.util.Collection;", "import java.lang.reflect.Method;\nimport java.util.Collection;")
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
      } else if (StatsData.isGodKiForm(nextForm) && !data.canActivateGodKi()) {
         return false;
      } else {
         if (data.getCharacter().hasActiveForm()) {""")
# attemptTransform: refuse with the existing not_stackable message.
h = rep(h, """      FormData nextForm = TransformationsHelper.getNextAvailableStackForm(data);
      if (nextForm != null) {
         if (data.getCharacter().hasActiveForm()) {""", """      FormData nextForm = TransformationsHelper.getNextAvailableStackForm(data);
      if (nextForm != null) {
         if (StatsData.isDivineKiForm(nextForm) && !data.canActivateDivineKi()
            || StatsData.isGodKiForm(nextForm) && !data.canActivateGodKi()) {
            player.m_5661_(Component.m_237115_("message.dragonminez.form.not_stackable"), true);
            return;
         }

         if (data.getCharacter().hasActiveForm()) {""")
open(SH, "w", encoding="utf-8").write(h)

f = open(FH, encoding="utf-8").read()
# canCharge: no charge-up into any form while God Ki is active.
f = rep(f, """      FormData nextForm = TransformationsHelper.getNextAvailableForm(data);
      if (nextForm == null) {
         return false;
      } else {
         return TransformationsHelper.isOozaruForm(nextForm)""", """      FormData nextForm = TransformationsHelper.getNextAvailableForm(data);
      if (nextForm == null) {
         return false;
      } else if (data.isGodKiCopyActive()) {
         return false;
      } else {
         return TransformationsHelper.isOozaruForm(nextForm)""")
# attemptTransform: refuse with not_stackable instead of stock's stack_removed path.
f = rep(f, """      FormData nextForm = TransformationsHelper.getNextAvailableForm(data);
      if (nextForm != null) {
         String targetGroup = TransformationsHelper.getTransformTargetGroup(data);""", """      FormData nextForm = TransformationsHelper.getNextAvailableForm(data);
      if (nextForm != null) {
         if (data.isGodKiCopyActive()) {
            player.m_5661_(Component.m_237115_("message.dragonminez.form.not_stackable"), true);
            return;
         }

         String targetGroup = TransformationsHelper.getTransformTargetGroup(data);""")
open(FH, "w", encoding="utf-8").write(f)

e = open(EA, encoding="utf-8").read()
# Instant transform (the instant key) skips both action-mode handlers: carry the same gates.
e = rep(e, """      FormData nextForm = TransformationsHelper.getNextAvailableStackForm(data);
      if (nextForm == null) {
         return false;
      } else if (!nextForm.getFormStackable()) {""", """      FormData nextForm = TransformationsHelper.getNextAvailableStackForm(data);
      if (nextForm == null) {
         return false;
      } else if (StatsData.isDivineKiForm(nextForm) && !data.canActivateDivineKi()
         || StatsData.isGodKiForm(nextForm) && !data.canActivateGodKi()) {
         player.m_5661_(Component.m_237115_("message.dragonminez.form.not_stackable"), true);
         return false;
      } else if (!nextForm.getFormStackable()) {""")
e = rep(e, """      FormData nextForm = TransformationsHelper.getNextAvailableForm(data);
      if (nextForm == null) {
         return false;
      } else if (TransformationsHelper.isOozaruForm(nextForm)) {
         return false;
      } else {""", """      FormData nextForm = TransformationsHelper.getNextAvailableForm(data);
      if (nextForm == null) {
         return false;
      } else if (TransformationsHelper.isOozaruForm(nextForm)) {
         return false;
      } else if (data.isGodKiCopyActive()) {
         player.m_5661_(Component.m_237115_("message.dragonminez.form.not_stackable"), true);
         return false;
      } else {""")
open(EA, "w", encoding="utf-8").write(e)
print("patched")
