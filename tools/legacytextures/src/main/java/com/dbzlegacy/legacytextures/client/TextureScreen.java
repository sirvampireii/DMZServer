package com.dbzlegacy.legacytextures.client;

import com.dbzlegacy.legacytextures.TextureMode;
import com.dbzlegacy.legacytextures.resolve.TextureSet;
import java.util.List;
import java.util.function.UnaryOperator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The texture menu (/legacytextures). Three one-tap presets, one switch per category and the HD set
 * picker. Every change is sent to the server, which stores it and sends it back; the menu redraws
 * from that answer. Players and NB Armors switch at once; the world switch reloads textures once.
 */
final class TextureScreen extends Screen {
    private static final int GAP = 4;
    private static final int ROW = 20;
    private static final int PRESET_W = 104;
    private static final int WIDE_W = 3 * PRESET_W + 2 * GAP;
    private final Screen parent;
    private int seenRevision = -1;

    TextureScreen(Screen parent) {
        super(Component.m_237113_("Legacy Textures"));
        this.parent = parent;
    }

    @Override
    protected void m_7856_() {
        seenRevision = ClientState.revision();
        TextureMode mode = ClientState.mode();
        int left = this.f_96543_ / 2 - WIDE_W / 2;
        int y = 52;

        TextureMode.Preset current = mode.preset();
        int x = left;
        for (TextureMode.Preset p : TextureMode.Preset.values()) {
            Button b = Button.m_253074_(Component.m_237113_(p.label), btn -> pick(m -> m.withPreset(p)))
                    .m_252987_(x, y, PRESET_W, ROW)
                    .m_257505_(Tooltip.m_257550_(Component.m_237113_(presetHelp(p, mode))))
                    .m_253136_();
            b.f_93623_ = p != current;
            this.m_142416_(b);
            x += PRESET_W + GAP;
        }

        y += ROW + 14;
        this.m_142416_(Button.m_253074_(label("Players and DragonMineZ armor", mode.players),
                        btn -> pick(m -> m.withPlayers(!m.players)))
                .m_252987_(left, y, WIDE_W, ROW)
                .m_257505_(Tooltip.m_257550_(Component.m_237113_(
                        "Bodies, faces, hair, tattoos, forms and DragonMineZ armor on every player. Switches at once.")))
                .m_253136_());
        y += ROW + GAP;
        this.m_142416_(Button.m_253074_(label("NB Armors outfits", mode.nbArmor),
                        btn -> pick(m -> m.withNbArmor(!m.nbArmor)))
                .m_252987_(left, y, WIDE_W, ROW)
                .m_257505_(Tooltip.m_257550_(Component.m_237113_(
                        "Gis, armors and vanity outfits from NB Armors. Switches at once.")))
                .m_253136_());
        y += ROW + GAP;
        this.m_142416_(Button.m_253074_(label("World, items and menus", mode.world),
                        btn -> pick(m -> m.withWorld(!m.world)))
                .m_252987_(left, y, WIDE_W, ROW)
                .m_257505_(Tooltip.m_257550_(Component.m_237113_(
                        "Blocks, items and menu art. Clicking this reloads your textures once; the game pauses for "
                                + "a few seconds while it loads.")))
                .m_253136_());

        y += ROW + 18;
        List<TextureSet> sets = ClientState.registry().hdSets();
        TextureSet active = ClientState.registry().hdOrFallback(mode.set);
        Button setButton = Button.m_253074_(Component.m_237113_("HD set: " + active.name),
                        btn -> pick(m -> m.withSet(nextSet(sets, m.set))))
                .m_252987_(left, y, WIDE_W, ROW)
                .m_257505_(Tooltip.m_257550_(Component.m_237113_(sets.size() > 1
                        ? "Click to use the next HD set. Every switch set to HD uses this set."
                        : "Only one HD set is installed. Add more in config/legacytextures/texture_sets.")))
                .m_253136_();
        setButton.f_93623_ = sets.size() > 1;
        this.m_142416_(setButton);

        this.m_142416_(Button.m_253074_(Component.m_237113_("Done"), btn -> this.m_7379_())
                .m_252987_(this.f_96543_ / 2 - PRESET_W / 2, this.f_96544_ - 32, PRESET_W, ROW)
                .m_253136_());
    }

    private static Component label(String what, boolean hd) {
        return Component.m_237113_(what + ": " + (hd ? "HD" : "Standard"));
    }

    private static String presetHelp(TextureMode.Preset p, TextureMode mode) {
        String what = switch (p) {
            case ALL_HD -> "HD for players, NB Armors and the world.";
            case PLAYERS_HD_ONLY -> "HD for players and NB Armors; standard world.";
            case ALL_VANILLA -> "The standard look everywhere.";
        };
        boolean reload = p.world != mode.world;
        return what + (reload ? " Reloads your textures once (a few seconds)." : " Switches at once.");
    }

    private static String nextSet(List<TextureSet> sets, String current) {
        if (sets.isEmpty()) {
            return current;
        }
        for (int i = 0; i < sets.size(); i++) {
            if (sets.get(i).id.equals(current)) {
                return sets.get((i + 1) % sets.size()).id;
            }
        }
        return sets.get(0).id;
    }

    private void pick(UnaryOperator<TextureMode> change) {
        ClientState.request(change.apply(ClientState.mode()));
    }

    @Override
    public void m_86600_() {
        if (seenRevision != ClientState.revision()) {
            this.m_232761_();
        }
    }

    @Override
    public void m_88315_(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.m_280273_(g);
        g.m_280653_(this.f_96547_, this.f_96539_, this.f_96543_ / 2, 16, 0xFFFFFF);
        g.m_280653_(this.f_96547_, Component.m_237113_("Pick what your own game draws. Other players are not affected."),
                this.f_96543_ / 2, 30, 0xAAAAAA);
        g.m_280653_(this.f_96547_, Component.m_237113_("Changing the world look reloads textures once (a few seconds)."),
                this.f_96543_ / 2, 52 + ROW + 14 + 3 * (ROW + GAP) + 2, 0xAAAAAA);
        super.m_88315_(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void m_7379_() {
        this.f_96541_.m_91152_(parent);
    }

    @Override
    public boolean m_7043_() {
        return false;
    }
}
