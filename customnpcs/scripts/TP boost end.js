/*
============================================================
 DBZ Legacy Reborn - TP Boost End (STUB → Java)
 Trigger ID: 31

 Forwards to LegacyMechanics GlobalTpBoost.endBoost.
 Prefer: /lm admin progression boost end
============================================================
*/

var COLOR = "\u00A7";
var TRIGGER_ID = 31;

function trigger(event) {
    if (event == null) return;
    try {
        if (Number(event.id) != Number(TRIGGER_ID)) return;
    } catch (ignored) {
        return;
    }
    try {
        var GlobalTpBoost = Java.type(
            "com.dbzlegacy.adaptivedifficulty.progression.tp.GlobalTpBoost"
        );
        var msg = GlobalTpBoost.endBoost(true);
        if (msg && event.player != null && typeof event.player.message === "function") {
            event.player.message(String(msg));
        }
    } catch (err) {
        try {
            print("[TP Boost End 31] Java forward failed: " + err);
        } catch (ignoredPrint) {}
        try {
            if (event.player != null) {
                event.player.message(
                    COLOR + "e[TP Boost] " + COLOR + "7Use " + COLOR + "f/progression boost end"
                    + COLOR + "7 (LegacyMechanics)."
                );
            }
        } catch (ignoredMsg) {}
    }
}
