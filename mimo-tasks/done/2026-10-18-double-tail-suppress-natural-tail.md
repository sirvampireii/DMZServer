# Fix double-tail visual (body-parts sync)

## Boundary

Do NOT modify the KaiPath JAR. Only change LM server code, the ultra-gui-kit reference, and bridge docs in this repo. JLDK updates the KaiPath JAR himself from the docs.

## Problem

A player with a natural Saiyan tail who equips a shop tail sees two tails. The server-side single-tail rule in `PlayerModelParts.bodyParts()` only dedupes within the sync array — it can't suppress the tail that dmzultra's FormParts draws directly on the client via the race layer. The kit's `noTail`/`aliasTailOnly` logic only covers the case where the array carries the natural-tail alias, not the case where the array carries a shop tail while FormParts draws the race tail independently.

## Fix (protocol addition)

1. In `PlayerModelParts.bodyParts()` (or the sync payload builder), add a `suppressNaturalTail` boolean to the `parts` object. Set it `true` when the resolved array contains a shop tail (any `SLOT_TAIL` piece with a non-`saiyan_tail` id) — i.e. the player has explicitly chosen a tail piece, so the client must hide the race-layer tail.

2. In the kit's `BodyPartPreview` (`tools/ultra-gui-kit/kaipath-client/.../BodyPartPreview.java`):
   - Parse `suppressNaturalTail` in `applySync`/`stateFromParts` and store it in `WorldState`.
   - In `meshesFor` (both the menu-preview and world-sync paths), when `suppressNaturalTail` is true and `raceTailAlreadyShown(entity)` is true, ensure the FormParts mixin path skips its own race-tail rendering so only the shop tail mesh renders. Mirror the existing `noTail` pattern.
   - Add a kit test: payload with a shop tail + `suppressNaturalTail:true` for an entity with `isHasSaiyanTail()=true` must resolve to exactly one tail mesh.

3. Document the new field in `docs/KAIPATH_GUI_PROTOCOL.md` message-3 section and note it in `docs/KAIPATH_JLDK_CLIENT_BRIEF.md` as a required KaiPath-side handling for JLDK to implement in the KaiPath JAR on his own.

Do NOT change the natural-tail-always-returns behavior — when the shop tail is unequipped, `suppressNaturalTail` goes back to false and the race tail renders as before.

Standard packaging order: clean before build, scan jar for missing/referenced classes + inner classes, scan every @Mixin class for non-private helper methods, version pins match (AdaptiveDifficultyMod.VERSION, mods.toml, plugin.yml).
