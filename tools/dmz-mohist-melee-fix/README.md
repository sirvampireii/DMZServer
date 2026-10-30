# DMZ Mohist Melee Fix

**v2.12.27** — ported to DragonMineZ 2.2: the party quest guard runs on the 2.2 party API, the NPC master disables match the new boolean handlers, and melee keeps DMZ's windup. Details: `docs/MOHIST_MELEE_BUG.md`.

Jar: `mods/dmz_mohist_melee_fix-2.12.27.jar` (needs DragonMineZ 2.2; it will not load on 2.1.3)

**Old Kai Precision skip is off on 2.2.** The stage list is checked by the server in 2.2, so it would have to change on the server and on every client together. The client modpack does not ship this jar.

**Debug logging:** set `debugLogging=true` in `config/dmz_mohist_melee_fix-debug.properties` to see per-event lines in the console.

Build: `./tools/dmz-mohist-melee-fix/build.sh`
