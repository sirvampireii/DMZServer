# Bridge work rules + verify bridge tools are updated

## Rules (standing, do not violate)

1. NEVER modify the KaiPath JAR. Not the binary, not its source, nothing. The KaiPath mod is JLDK's — he updates it himself.
2. Only these are in scope: LM server code (`tools/dmz-adaptive-difficulty/`), the ultra-gui-kit reference (`tools/ultra-gui-kit/`), and bridge docs (`docs/KAIPATH_*.md`) in this GitHub repo.
3. When the bridge protocol changes, update the docs so JLDK can implement it on his side. Docs are the handoff — keep them current.
4. The kit (`tools/ultra-gui-kit/`) is the reference implementation JLDK copies from. Keep it in sync with the LM server's actual wire output.

## Task: verify the bridge tools are fully updated

The `suppressNaturalTail` protocol addition just landed in `PlayerModelParts`. Verify everything downstream is consistent:

1. **Kit reference** (`tools/ultra-gui-kit/kaipath-client/.../BodyPartPreview.java`):
   - Parses `suppressNaturalTail` from the sync payload
   - Stores it in `WorldState`
   - `meshesFor` honors it (skips race-tail rendering when true + race tail already shown)
   - Kit test covers the shop-tail + natural-tail case resolving to exactly one tail mesh

2. **Protocol docs** (`docs/KAIPATH_GUI_PROTOCOL.md`):
   - Message 3 section documents the `suppressNaturalTail` boolean field, when the server sets it, and what the client must do

3. **JLDK brief** (`docs/KAIPATH_JLDK_CLIENT_BRIEF.md`):
   - Lists `suppressNaturalTail` as a required KaiPath-side implementation item

4. **Gap matrix** (`docs/KAIPATH_BRIDGE_GAPS.md`):
   - If the double-tail visual was listed as an open gap, mark it closed on the LM/kit side with a note that KaiPath JAR implementation is pending JLDK

Report back what was already correct and what you had to fix. Do not bump the version for docs/kit-only changes.

Standard packaging order still applies for any code changes: clean before build, scan jar for missing/referenced classes + inner classes, scan every @Mixin class for non-private helper methods, version pins match.
