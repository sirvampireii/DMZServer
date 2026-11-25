/*
 * ============================================================
 * LegacyMechanics — Skill Check CNPC (interact)
 * ============================================================
 * Same behavior as SkillCheckPlayerNpc.js.
 * Right-click opens Skill Check for every player.
 * Slash /lm skillcheck still requires legacymechanics.skillcheck.
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
        print("[LM SkillCheck NPC] " + err);
    }
}
