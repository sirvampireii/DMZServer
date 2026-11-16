package com.dmzlegacy.kaipath.ultra.consumers.legacymechanics;

/** Offline checks for {@link UltraBridgeFit}. No Minecraft runtime. */
public final class UltraBridgeFitTest {
    private UltraBridgeFitTest() {}

    public static void main(String[] args) {
        // LM menus: 426x240 design box, margin 0, on the GUI sizes LM 4.7.52 was checked at.
        UltraBridgeFit.Placement big = UltraBridgeFit.fit(960, 540, 0, 426, 240);
        check(Math.abs(big.scale() - 2.25f) < 1e-4, "960x540: scale 2.25 (height-bound), got " + big.scale());
        check(big.h() == 540 && big.w() == 959 && big.x() == 0 && big.y() == 0,
                "960x540: box 959x540 at 0,0, got " + big);
        UltraBridgeFit.Placement small = UltraBridgeFit.fit(427, 240, 0, 426, 240);
        check(small.scale() == 1f && small.w() == 426 && small.h() == 240 && small.x() == 0,
                "427x240: scale 1, box 426x240, got " + small);
        UltraBridgeFit.Placement auto1080 = UltraBridgeFit.fit(480, 270, 0, 426, 240);
        check(Math.abs(auto1080.scale() - 1.125f) < 1e-4 && auto1080.h() == 270,
                "480x270: scale 1.125, got " + auto1080);

        // A 5:4 window: one scale for both axes, box centered vertically (letterbox).
        UltraBridgeFit.Placement square = UltraBridgeFit.fit(640, 512, 0, 426, 240);
        float shape = square.w() / (float) square.h();
        check(Math.abs(shape - 426f / 240f) < 0.01f, "5:4 window keeps the box's 16:9 shape, got " + square);
        check(square.x() == 0 && square.y() == (512 - square.h()) / 2, "5:4 window: centered vertically, got " + square);
        check(square.px(86) == Math.round(86 * square.scale()) && square.px(120) == Math.round(120 * square.scale()),
                "px() uses the one scale on both axes");

        // A margin is honored on both sides.
        UltraBridgeFit.Placement inset = UltraBridgeFit.fit(500, 300, 10, 426, 240);
        check(inset.x() >= 10 && inset.y() >= 10 && inset.x() + inset.w() <= 490 && inset.y() + inset.h() <= 290,
                "margin keeps the box inside, got " + inset);

        // Title: scales with the box, never below 1, never past the band or the panel width.
        check(Math.abs(UltraBridgeFit.titleScale(2.25f, 100, 9, 63, 942) - 2.25f) < 1e-4, "title follows the box scale");
        check(UltraBridgeFit.titleScale(1f, 100, 9, 28, 410) == 1f, "title at scale 1 stays vanilla size");
        check(UltraBridgeFit.titleScale(0.5f, 100, 9, 14, 200) == 1f, "title never shrinks below 1");
        check(UltraBridgeFit.titleScale(4f, 100, 9, 20, 2000) <= (20 - 2) / 9f + 1e-4, "title fits the header band");
        check(UltraBridgeFit.titleScale(4f, 300, 9, 200, 600) <= 2f + 1e-4, "title fits the panel width");

        // Scroll bar: none when nothing scrolls; thumb proportional; ends land on the track ends.
        check(UltraBridgeFit.scrollBar(400, 50, 200, 0, 0) == null, "no bar when nothing scrolls");
        UltraBridgeFit.Bar top = UltraBridgeFit.scrollBar(400, 50, 200, 0, 150);
        check(top != null && top.thumbTop() == 50 && top.thumbH() == 75 && top.x() == 400 - 2 - 4,
                "half the content visible: thumb is half the track, at the top, got " + top);
        UltraBridgeFit.Bar end = UltraBridgeFit.scrollBar(400, 50, 200, 150, 150);
        check(end.thumbTop() + end.thumbH() == 200, "scrolled to the end: thumb at the bottom, got " + end);
        UltraBridgeFit.Bar tiny = UltraBridgeFit.scrollBar(400, 50, 200, 0, 100000);
        check(tiny.thumbH() == UltraBridgeFit.MIN_THUMB_H, "long content: thumb keeps its minimum, got " + tiny);
        check(top.hits(top.x() + 1, 100) && !top.hits(top.x() - 20, 100), "bar hit test");

        // Dragging the thumb maps back to the scroll offset.
        check(UltraBridgeFit.scrollForThumb(top, 50 + 10, 10, 150) == 0, "thumb at the track top: scroll 0");
        check(UltraBridgeFit.scrollForThumb(top, 200 - 75 + 10, 10, 150) == 150, "thumb at the track end: max");
        check(UltraBridgeFit.scrollForThumb(top, 9999, 0, 150) == 150, "drag past the end clamps");
        check(UltraBridgeFit.scrollForThumb(top, -9999, 0, 150) == 0, "drag past the top clamps");
        for (int s = 0; s <= 150; s += 15) {
            UltraBridgeFit.Bar b = UltraBridgeFit.scrollBar(400, 50, 200, s, 150);
            int back = UltraBridgeFit.scrollForThumb(b, b.thumbTop(), 0, 150);
            check(Math.abs(back - s) <= 3, "thumb position round-trips scroll " + s + ", got " + back);
        }

        // Wheel: one notch moves one step, clamped to the content.
        check(UltraBridgeFit.wheel(0, -1, 20, 150) == 20, "wheel down scrolls one step");
        check(UltraBridgeFit.wheel(10, 1, 20, 150) == 0, "wheel up clamps at 0");
        check(UltraBridgeFit.wheel(140, -1, 20, 150) == 150, "wheel down clamps at the end");

        System.out.println("UltraBridgeFitTest OK");
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            throw new AssertionError(what);
        }
    }
}
