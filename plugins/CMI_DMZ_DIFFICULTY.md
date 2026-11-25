# CMI GUI for AdaptiveDifficulty

Requires:
- CMILib (shipped as CMILib1.5.9.6.jar)
- CMI
- `AdaptiveDifficultyGUI-1.0.14.jar` (must match Forge `AdaptiveDifficulty-1.0.14.jar`)

The companion plugin registers Bukkit `/lm difficulty` and opens a CMILib inventory GUI.
Fallback order: CMI → Bukkit chest → clickable chat.

If `/lm difficulty` opens chat instead of an inventory:
1. Confirm both jars above are installed and the server was fully restarted
2. Confirm CMILib + CMI are enabled (`/plugins` should list CMILib, CMI, AdaptiveDifficultyGUI)
3. Config `guiBackend` should be `cmi` (or `auto`) in `config/adaptivedifficulty.json`
4. Force inventory: `/dmzdiffgui` (alias `/adiffgui`)
