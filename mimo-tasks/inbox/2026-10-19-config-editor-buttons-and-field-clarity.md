# Config editor: button sizing and field clarity

The config editor fits the screen now, but buttons are sometimes too big and fields are hard to understand. Scope: `UltraConfigScreens` only. The paging-row overlap (Prev / Review / Next) was already fixed — do not touch that again.

## What to do

1. Tighten vertical button padding across the config editor. Buttons are tall with excess empty space around the text (sidebar buttons, section rows, the Review changes bar). Keep `MIN_BUTTON_WIDTH=80` and 8px horizontal padding.

2. Under every editable field, show the accepted range or values in plain words (for example `0.0 – 100.0`, `true / false`, or the named options for a choice field) plus one plain-language line saying what the setting does. Never show field names, snake_case, or raw IDs to the player.

3. Out-of-range or wrong-type input must show a friendly inline message saying what went wrong and what to enter instead. Never show "Invalid input" or raw error text.

4. Derive the ranges from the config field definitions. Where no range exists, show the field type in plain words (a number, on/off, text, a choice).

5. Test on an inner screen that has numeric, boolean, and text fields. Verify the buttons no longer feel oversized and every field shows its range and plain-language description.

## Packaging

Clean before build. Scan the jar for missing/referenced classes and inner classes. Scan every @Mixin class for non-private helper methods. Version pins must match (AdaptiveDifficultyMod.VERSION, mods.toml; Forge-only jar has no plugin.yml — absence is expected). Do not bump the version for this change alone.
