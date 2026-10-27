package org.papiricoh.nationsandwar.client.screen.modal;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.papiricoh.nationsandwar.client.gui.Draw;
import org.papiricoh.nationsandwar.client.gui.Ui;
import org.papiricoh.nationsandwar.view.PoliticsView;

import java.util.List;

/**
 * "Are you sure?" for a destructive one-click action (delete, kick, leave, surrender, declare war): a warning emblem, the
 * text saying what will happen (and what it costs), and one red button that does it.
 */
public final class ConfirmModal extends Modal {
    private static final int WIDTH = 250, TEXT_W = WIDTH - 16 - 36;
    private final Component text, button;
    private final Runnable action;

    public ConfirmModal(Component title, Component text, Component button, Runnable action) {
        super(title, Draw.Icon.SCROLL, WIDTH, height(text));
        this.text = text;
        this.button = button;
        this.action = action;
    }

    private static int height(Component text) {
        int lines = Minecraft.getInstance().font.split(text, TEXT_W).size();
        return TITLE + 6 + Math.max(30, lines * 10 + 6) + 6 + FOOTER;
    }

    @Override
    public void render(GuiGraphics g, PoliticsView v, float partial) {
        int ex = x + 2, ey = y + 2;
        Draw.shadow(g, ex, ey, 24, 24, 4);
        Draw.slab(g, ex, ey, 24, 24, 2, Draw.mix(0xFF3A2328, Draw.BAD, 0.35f), 0xFF2A1619);
        Draw.frame(g, ex, ey, 24, 24, Draw.alpha(Draw.BAD, 0xA0));
        Draw.text(g, font, Component.literal("!"), ex + 9.5f, ey + 5, 2, Draw.mix(Draw.BAD, 0xFFFFFFFF, 0.4f), true);
        text(g, text, x + 36, y + 3, TEXT_W, Draw.TEXT);
    }

    @Override
    public List<Action> actions(PoliticsView v) {
        return List.of(action(button, Ui.Style.BAD, null, action));
    }
}
