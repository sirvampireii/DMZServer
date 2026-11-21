package com.dmzlegacy.kaipath.ultra.consumers.legacymechanics;

/** Offline check of the preview facing contract (no Minecraft runtime). Run from GuiOpenPacketRoundTripTest. */
public final class PreviewFacingTest {
    private PreviewFacingTest() {}

    public static void main(String[] args) {
        // Stopped at any angle (here the back, 180): the next still packet is front-facing.
        check(PreviewFacing.afterPacket(180f, false) == 0f, "rotate=false resets the turntable");
        check(PreviewFacing.lookX(false, 180f) == 0f && PreviewFacing.lookY(false) == 0f,
                "a still preview looks straight at the viewer");
        check(PreviewFacing.afterPacket(97f, true) == 97f, "a spinning preview keeps its angle across packets");
        check(PreviewFacing.lookX(true, 90f) == PreviewFacing.SWING, "spin swings the look");
        System.out.println("PreviewFacingTest OK");
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            throw new AssertionError("PreviewFacingTest: " + what);
        }
    }
}
