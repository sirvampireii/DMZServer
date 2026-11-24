# GuiKit sources (LM skin extension)

These files **replace the copies in `mod-toolkit\fx\` and `mod-toolkit\tests\`**. mod-toolkit is not a git repository,
so this folder is the durable copy. After a fresh mod-toolkit checkout, or if its GuiKit is older than these, copy
them back:

| This folder | Goes to |
|---|---|
| `fx\GuiKit.java` | `mod-toolkit\fx\GuiKit.java` (replaces) |
| `fx\GuiPaint.java` | `mod-toolkit\fx\GuiPaint.java` (replaces) |
| `fx\LmLook.java` | `mod-toolkit\fx\LmLook.java` (new) |
| `tests\GuiKitTest.java` | `mod-toolkit\tests\GuiKitTest.java` (replaces) |

`scripts\guikit.ps1` and every other mod-toolkit file are unchanged.

Check it with `mod-toolkit\scripts\guikit.ps1 -SelfTest`. It must print `92 passed, 0 failed`.

## What changed

- **`LmLook.java` (new).** A port of LM's `gui/ultra/MenuSkins.java` and `SaiyanRoyal.java`:
  - all five skins and every derived tone (faces, edges, insets, shadows, glow, muted);
  - the per-system accents;
  - the colour-letter inks, made readable;
  - the themed background motifs.

  One deliberate difference: a light skin's `muted()` blends 0.30, not 0.42. That is the target; see
  `findings/layout-findings.md`.
- **`GuiPaint.java`.** Three additions:
  - a design transform, so a menu can be scaled to fit the way `NativeMenuFit` does;
  - rounded gradient shapes cut per real pixel row, for the 14 px panel and 8 px buttons;
  - the baked skin background.
- **`GuiKit.java`.**
  - Panel style `"lm"`, which draws the native depth system: two soft shadows (one in Lite), the trim border, a
    vertical gradient, the accent frame, the divider, a bold title and the breadcrumb.
  - LM buttons: raised, with sheen and shade, a pressed state, the active tab's glow and underline, and
    normal / confirm / danger / dim / locked.
  - LM controls: info boxes and cards, fields, glossy bars, skin swatches, colour wheels and paged books.
  - Top-level keys `include`, `skin`, `look`, `keep`, `format` and `sheets`; screen keys `skin`, `look` and `background`.
  - LM rules: 8 px padding, the 80 px minimum, no "Hub", no bare "Back", no raw ids, Back placement, the right-border
    margin, click size *after* the fit shrink, and contrast against the skin.

  The header Javadoc of `GuiKit.java` documents every key.
