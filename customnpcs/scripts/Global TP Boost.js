/*
============================================================
 DBZ Legacy Reborn - Global TP Boost (STUB → Java)
 Trigger ID: 30

 Forwards to LegacyMechanics GlobalTpBoost (no double-apply).
 Prefer: /lm admin progression boost start <encoded> [name]
============================================================
*/

var COLOR = "\u00A7";
var TRIGGER_ID = 30;

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
        var encoded = 0;
        var purchaser = "";
        try {
            if (event.arguments != null && event.arguments.length > 0) {
                encoded = parseInt(String(event.arguments[0]), 10);
            }
            if (event.arguments != null && event.arguments.length > 1) {
                purchaser = String(event.arguments[1]);
            }
        } catch (ignoredArgs) {}
        if (!(encoded > 0)) {
            if (event.player != null) {
                event.player.message(
                    COLOR + "c[TP Boost] " + COLOR + "7Need encoded value. Use "
                    + COLOR + "f/progression boost"
                );
            }
            return;
        }
        var mcPlayer = null;
        try {
            if (event.player != null) mcPlayer = event.player.getMCEntity();
        } catch (ignoredMc) {}
        if (purchaser == null || purchaser === "") {
            try {
                if (event.player != null) purchaser = String(event.player.getName());
            } catch (ignoredName) {}
        }
        var msg = GlobalTpBoost.startBoost(mcPlayer, encoded | 0, purchaser);
        if (msg && event.player != null && typeof event.player.message === "function") {
            event.player.message(String(msg));
        }
    } catch (err) {
        try {
            print("[Global TP Boost 30] Java forward failed: " + err);
        } catch (ignoredPrint) {}
        try {
            if (event.player != null) {
                event.player.message(
                    COLOR + "e[TP Boost] " + COLOR + "7Use " + COLOR + "f/progression boost"
                    + COLOR + "7 (LegacyMechanics)."
                );
            }
        } catch (ignoredMsg) {}
    }
}
