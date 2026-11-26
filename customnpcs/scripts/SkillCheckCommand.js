/*
============================================================
 SkillCheckCommand.js — DISABLED STUB → Java
 Trigger ID: 21

 Forwards to SkillCheckService.trigger21. Any player may open it from the NPC.
 Prefer: LM-SkillCheck-NPC.js on the NPC, or /lm skillcheck (donator perm).
============================================================
*/

var TRIGGER_ID = 21;

function init(e) { /* owned by LegacyMechanics */ }

function trigger(e) {
    try {
        if (e == null || Number(e.id) != Number(TRIGGER_ID)) return;
        var SkillCheck = Java.type(
            "com.dbzlegacy.adaptivedifficulty.progression.shop.SkillCheckService"
        );
        var actor = null;
        try {
            if (e.player != null) actor = e.player.getMCEntity();
        } catch (ignoredMc) {}
        SkillCheck.trigger21(actor);
    } catch (err) {
        try {
            print("[SkillCheck 21] Java forward failed: " + err);
        } catch (ignoredPrint) {}
    }
}

function command(e) { /* owned by LegacyMechanics — use /lm skillcheck */ }
