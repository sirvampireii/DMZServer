/*
============================================================
 DBZ Legacy Reborn - Android Conversion (STUB → Java)
 Trigger ID: 45

 Forwards to LegacyMechanics AndroidConversion.
 Prefer: /lm admin progression android [player]
============================================================
*/

var COLOR = "\u00A7";
var TRIGGER_ID = 45;

function trigger(event) {
    if (event == null) return;
    try {
        if (Number(event.id) != Number(TRIGGER_ID)) return;
    } catch (ignored) {
        return;
    }
    try {
        var ProgressionGuiApi = Java.type(
            "com.dbzlegacy.adaptivedifficulty.gui.ProgressionGuiApi"
        );
        var actor = null;
        try {
            if (event.player != null) actor = event.player.getMCEntity();
        } catch (ignoredMc) {}
        var targetName = "";
        try {
            if (event.arguments != null && event.arguments.length > 0) {
                targetName = String(event.arguments[0]);
            }
        } catch (ignoredArgs) {}
        var msg = ProgressionGuiApi.androidConvert(actor, targetName);
        if (msg && event.player != null && typeof event.player.message === "function") {
            event.player.message(String(msg));
        }
    } catch (err) {
        try {
            print("[Android Trigger 45] Java forward failed: " + err);
        } catch (ignoredPrint) {}
        try {
            if (event.player != null) {
                event.player.message(
                    COLOR + "e[Android] " + COLOR + "7Use " + COLOR + "f/lm admin progression android"
                    + COLOR + "7 (LegacyMechanics)."
                );
            }
        } catch (ignoredMsg) {}
    }
}
