/*
============================================================
 End Dragon Forge Trigger Bridge (STUB → Java)
 Triggers 50 / 51

 Forwards to LegacyMechanics EndDimensionStrength.
 Prefer Forge commands (no CMI alias):
   /lm admin enddragon · /lm admin enddragon spawn
   /cleardragons · /lm admin enddragon clear
============================================================
*/

function init(e) { /* owned by LegacyMechanics */ }

function trigger(e) {
    try {
        if (e == null) return;
        var id = Number(e.id);
        var End = Java.type(
            "com.dbzlegacy.adaptivedifficulty.progression.end.EndDimensionStrength"
        );
        var actor = null;
        try {
            if (e.player != null) actor = e.player.getMCEntity();
        } catch (ignoredMc) {}
        if (id === 50) {
            End.cmdSpawnDragon(actor);
            return;
        }
        if (id === 51) {
            End.cmdCleanupDragons(actor);
            return;
        }
    } catch (err) {
        try {
            print("[EndDragon Forge] Java forward failed: " + err);
        } catch (ignoredPrint) {}
    }
}
