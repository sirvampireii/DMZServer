/*
 * ============================================================
 * Skill Check — player NPC
 * ============================================================
 * Right-click opens Skill Check for every player. No donator
 * permission. Slash /lm skillcheck still needs
 * legacymechanics.skillcheck.
 *
 * Setup (CustomNPCs editor):
 *   1. Create an NPC. Name it Skill Check.
 *   2. Advanced → Scripts. Enable scripts. Language: ECMAScript.
 *   3. Paste this file into the NPC script.
 *   4. Turn the NPC dialog off, or the click opens the dialog
 *      instead of this script.
 *   5. Do not put Rival, Spar, or other menu scripts on this NPC.
 *
 * Needs LegacyMechanics 4.5.156 or newer, and a panel restart after the jar upload.
 * ============================================================
 */

function interact(event) {
    try {
        try {
            if (event != null && typeof event.setCanceled === "function") {
                event.setCanceled(true);
            }
        } catch (ignoredCancel) {}

        var player = event.player;
        if (player == null) {
            return;
        }

        var SkillCheck = Java.type(
            "com.dbzlegacy.adaptivedifficulty.progression.shop.SkillCheckService"
        );

        var mcPlayer = null;
        try {
            mcPlayer = player.getMCEntity();
        } catch (e1) {
            try {
                mcPlayer = player.getMinecraftEntity();
            } catch (e2) {
                mcPlayer = null;
            }
        }
        if (mcPlayer == null) {
            player.message("\u00A7cSkill Check: could not resolve your player.");
            return;
        }

        SkillCheck.openAtNpc(mcPlayer);
    } catch (err) {
        try {
            if (event.player != null) {
                event.player.message(
                    "\u00A7cSkill Check failed. Is LegacyMechanics loaded?\n\u00A78" + err
                );
            }
        } catch (ignored) {}
        print("[Skill Check NPC] " + err);
    }
}
