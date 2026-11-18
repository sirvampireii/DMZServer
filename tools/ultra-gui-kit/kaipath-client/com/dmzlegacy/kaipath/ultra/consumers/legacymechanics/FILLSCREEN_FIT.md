# fillScreen fit: merge guide for KaiPath (JLDK)

Plug-and-play fixes for `fillScreen` screens in `LmDynamicScreen`. They apply to every partner channel.
LegacyMechanics 4.7.52-4.7.54 sent every menu this way (a 1920x1080 clamp request, `fillScreen`, and a
426x240 design box). Since 4.7.55 only the staff config editor does; player menus are centered panels
sized to their page (see `docs/KAIPATH_GUI_PROTOCOL.md`, "Centered menus"). KaiPath 1.5.6 already
ships these fixes; fix 3 (the scroll bar) is what a tall centered menu uses at GUI scale 3 and 4.

**No wire change.** Protocol stays `"1"`, there are no new layout keys and nothing new to advertise. LM
already works on 1.5.3 as shipped; these are polish fixes.

## What to copy

1. [`UltraBridgeFit.java`](UltraBridgeFit.java). It is pure Java with no Minecraft types, so drop it in
   as is, next to `UltraBridgeFooter`.
2. Optional: [`test/.../UltraBridgeFitTest.java`](../../../../../../../test/com/dmzlegacy/kaipath/ultra/consumers/legacymechanics/UltraBridgeFitTest.java).
   It is an offline `main()` that prints `UltraBridgeFitTest OK`.

Then make the three edits below in 1.5.3's `LmDynamicScreen`. Field names are 1.5.3's own, read from the
1.5.3 class: `panelX/Y/W/H`, `contentTop`, `contentBottom`, `contentScroll`, `maxContentScroll`,
`scrollWidgets`, and `ClippedWidget.originalY`. The kit's own
[`LmDynamicScreen.java`](LmDynamicScreen.java) shows fixes 1 and 2 in its `placePanel` and title
paint. It is an older screen with no content band, so fix 3 is described here only.

## 1. One scale for x and y (no stretching)

**Problem:** in `init` (`m_7856_`), the `fillScreen` branch sets `panelW = width - 2*margin` and
`panelH = height - 2*margin`, then scales x by `panelW / designW` and y by `panelH / designH`. On any
window whose shape differs from the box (16:10, 4:3, 21:9, a resized window), that stretches buttons and
the model rectangle. The model's `min(w / 0.6, h / 1.8)` fit can also flip axis, which cuts off a giant
form that the server sized to fit.

**Fix:** replace that branch's panel and scale lines:

```java
UltraBridgeFit.Placement fit = UltraBridgeFit.fit(this.f_96543_, this.f_96544_, margin, dw, dh);
panelW = fit.w();
panelH = fit.h();
float sx = fit.scale();
float sy = fit.scale();
center = true; // the box is centered on the leftover axis (letterbox), not pinned at margin
```

Leave the `scaledLayout(layout, sx, sy)` and `scaledControls(..., sx, sy)` calls exactly as they are.
With `center = true`, the existing `panelX`/`panelY` lines (`max(margin, (width - panelW) / 2)`) place
the box at `fit.x()` / `fit.y()`. Keep `fit.scale()` in a field (for example `fitScale`, defaulting to
`1f`) for fix 2.

**Result:** on a 16:9 window nothing changes. Every other window gets the box at its own shape, centered.

## 2. Scale the title with the header

**Problem:** `headerBottom` is scaled, but the title is drawn at size 1. On a big GUI (scale 2.25 at
960x540) the title is small and sits low across the ruler line of a band 2.25x taller.

**Fix:** where `render` (`m_88315_`) draws the title, when `fillScreen` is on:

```java
float ts = UltraBridgeFit.titleScale(fitScale, titleW, 9, bandH, panelW - 16);
if (ts > 1f) {
    int scaledW = Math.round(titleW * ts);
    int x = center ? panelX + Math.max(8, (panelW - scaledW) / 2) : panelX + 8;
    int y = panelY + Math.max(2, Math.round((bandH - 9 * ts) / 2f));
    g.m_280168_().m_85836_();                 // pose().pushPose()
    g.m_280168_().m_252880_(x, y, 0f);        // translate
    g.m_280168_().m_85841_(ts, ts, 1f);       // scale
    g.m_280488_(this.f_96547_, title, 0, 0, color);
    g.m_280168_().m_85849_();                 // popPose()
} else {
    // the existing title draw
}
```

`bandH` is the header band height the title already centers in (`headerBottom - 2`). The scale is never
below 1 and never wider than the panel or taller than the band.

## 3. Scroll bar for the content band

**Problem:** when content runs past `contentBottom`, 1.5.3 scrolls it with the wheel, but nothing shows
that it scrolls. Before LM 4.7.52, the Body Parts shop at 1080p auto scale showed none of its 19 buttons.
Any partner screen that overflows has the same problem.

**Fix, in three parts:**

1. **Paint the bar** at the end of `render`, after the widgets:

   ```java
   UltraBridgeFit.Bar bar = UltraBridgeFit.scrollBar(
           panelX + panelW, contentTop, contentBottom, contentScroll, maxContentScroll);
   if (bar != null) {
       g.m_280509_(bar.x(), bar.trackTop(), bar.x() + UltraBridgeFit.BAR_W,
               bar.trackTop() + bar.trackH(), UltraBridgeFit.TRACK_ARGB);
       g.m_280509_(bar.x(), bar.thumbTop(), bar.x() + UltraBridgeFit.BAR_W,
               bar.thumbTop() + bar.thumbH(), UltraBridgeFit.THUMB_ARGB);
   }
   ```

2. **Move the scroll into one method.** Take the loop at the end of `mouseScrolled` (`m_6050_`), which
   sets every `ClippedWidget`'s y to `originalY - contentScroll`, and put it in a method both handlers call:

   ```java
   private void applyContentScroll(int value) {
       contentScroll = Math.max(0, Math.min(maxContentScroll, value));
       for (ClippedWidget w : scrollWidgets) {
           w.m_253211_(w.originalY - contentScroll);   // same call the wheel loop makes today
       }
   }
   ```

   `mouseScrolled` then becomes
   `applyContentScroll(UltraBridgeFit.wheel(contentScroll, delta, 24, maxContentScroll))`. That is the same
   24 px per notch it uses now.

3. **Drag the thumb, or click the track:**

   ```java
   private boolean draggingBar;
   private double barGrab;

   @Override
   public boolean m_6375_(double mx, double my, int button) {          // mouseClicked
       UltraBridgeFit.Bar bar = UltraBridgeFit.scrollBar(
               panelX + panelW, contentTop, contentBottom, contentScroll, maxContentScroll);
       if (button == 0 && bar != null && bar.hits(mx, my)) {
           barGrab = bar.hitsThumb(my) ? my - bar.thumbTop() : bar.thumbH() / 2.0;
           draggingBar = true;
           applyContentScroll(UltraBridgeFit.scrollForThumb(bar, my, barGrab, maxContentScroll));
           return true;
       }
       return super.m_6375_(mx, my, button);
   }

   @Override
   public boolean m_7979_(double mx, double my, int button, double dx, double dy) {   // mouseDragged
       if (draggingBar) {
           UltraBridgeFit.Bar bar = UltraBridgeFit.scrollBar(
                   panelX + panelW, contentTop, contentBottom, contentScroll, maxContentScroll);
           applyContentScroll(UltraBridgeFit.scrollForThumb(bar, my, barGrab, maxContentScroll));
           return true;
       }
       return super.m_7979_(mx, my, button, dx, dy);
   }

   @Override
   public boolean m_6348_(double mx, double my, int button) {         // mouseReleased
       draggingBar = false;
       return super.m_6348_(mx, my, button);
   }
   ```

   The bar is 4 px wide and sits 2 px inside the panel's right edge, so it does not cover buttons laid out
   inside a partner's design box. LM keeps content 10 design px from the edge.

## Check it

- Run `UltraBridgeFitTest`; it prints `UltraBridgeFitTest OK`.
- In game, open the LM hub with `/lm` and a long page (Character Services, then Body parts):
  - **GUI scale 2 on 1080p:** the title is drawn at the header's size and sits centered in its band.
  - **A resized window** (for example 1280x1024): the menu is letterboxed with its 16:9 shape, the
    buttons are not stretched, and the model is whole.
  - **A page that overflows** (any partner screen taller than its box): a bar shows at the right of the
    content band. Dragging it, clicking the track and the wheel all scroll the same content.
- Screens without `fillScreen` are untouched: `fitScale` stays `1f`, and they have the same bar only when
  they overflow.
