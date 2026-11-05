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
 * The texture menu (/legacytextures or the menu key). Three one-tap presets, one switch per category
 * and the HD set picker. Every change is sent to the server, which stores it and sends it back; the
 * menu redraws from that answer. Players and NB Armors switch at once; the world switch reloads
 * textures once. All text comes from assets/legacytextures/lang.
 */
final class TextureScreen extends Screen {
    private static final int GAP = 4;
    private static final int ROW = 20;
    private static final int PRESET_W = 104;
    private static final int WIDE_W = 3 * PRESET_W + 2 * GAP;
    private final Screen parent;
    private int seenRevision = -1;

    TextureScreen(Screen parent) {
        super(Component.m_237115_("legacytextures.menu.title"));
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
            Button b = Button.m_253074_(Component.m_237115_(p.key), btn -> pick(m -> m.withPreset(p)))
                    .m_252987_(x, y, PRESET_W, ROW)
                    .m_257505_(Tooltip.m_257550_(presetHelp(p, mode)))
                    .m_253136_();
            b.f_93623_ = p != current;
            this.m_142416_(b);
            x += PRESET_W + GAP;
        }

        y += ROW + 14;
        y = toggle("players", mode.players, m -> m.withPlayers(!m.players), left, y);
        y = toggle("nbarmor", mode.nbArmor, m -> m.withNbArmor(!m.nbArmor), left, y);
        toggle("world", mode.world, m -> m.withWorld(!m.world), left, y);

        y += ROW + 18;
        List<TextureSet> sets = ClientState.registry().hdSets();
        TextureSet active = ClientState.registry().hdOrFallback(mode.set);
        Button setButton = Button.m_253074_(Component.m_237110_("legacytextures.set.button", new Object[] {active.name}),
                        btn -> pick(m -> m.withSet(nextSet(sets, m.set))))
                .m_252987_(left, y, WIDE_W, ROW)
                .m_257505_(Tooltip.m_257550_(Component.m_237115_(
                        sets.size() > 1 ? "legacytextures.set.help.many" : "legacytextures.set.help.one")))
                .m_253136_();
        setButton.f_93623_ = sets.size() > 1;
        this.m_142416_(setButton);

        this.m_142416_(Button.m_253074_(Component.m_237115_("legacytextures.menu.done"), btn -> this.m_7379_())
                .m_252987_(this.f_96543_ / 2 - PRESET_W / 2, this.f_96544_ - 32, PRESET_W, ROW)
                .m_253136_());
    }

    /** One switch row; returns the next row's y. */
    private int toggle(String what, boolean hd, UnaryOperator<TextureMode> change, int left, int y) {
        Component label = Component.m_237110_("legacytextures.switch", new Object[] {
                Component.m_237115_("legacytextures.switch." + what),
                Component.m_237115_(hd ? "legacytextures.state.hd" : "legacytextures.state.standard")});
        this.m_142416_(Button.m_253074_(label, btn -> pick(change))
                .m_252987_(left, y, WIDE_W, ROW)
                .m_257505_(Tooltip.m_257550_(Component.m_237115_("legacytextures.switch." + what + ".help")))
                .m_253136_());
        return y + ROW + GAP;
    }

    private static Component presetHelp(TextureMode.Preset p, TextureMode mode) {
        String when = p.world != mode.world ? "legacytextures.preset.reload" : "legacytextures.preset.instant";
        return Component.m_237110_(when, new Object[] {Component.m_237115_(p.key + ".help")});
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
        g.m_280653_(this.f_96547_, Component.m_237115_("legacytextures.menu.subtitle"), this.f_96543_ / 2, 30, 0xAAAAAA);
        g.m_280653_(this.f_96547_, Component.m_237115_("legacytextures.menu.world_note"),
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
