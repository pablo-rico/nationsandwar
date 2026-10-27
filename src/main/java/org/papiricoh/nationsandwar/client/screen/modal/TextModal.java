package org.papiricoh.nationsandwar.client.screen.modal;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.papiricoh.nationsandwar.client.gui.Draw;
import org.papiricoh.nationsandwar.client.gui.Ui;
import org.papiricoh.nationsandwar.view.PoliticsView;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * One text field under a short explanation, and one button that sends it: found a town, create an alliance, invite a
 * player by name.
 */
public final class TextModal extends Modal {
    private static final int WIDTH = 240;
    private final String key;
    private final Component text, hint, button;
    private final Ui.Style style;
    private final Function<PoliticsView, Component> denied;
    private final Consumer<String> send;
    private EditBox field;

    /** @param denied the reason the viewer may not, from the latest view (null: allowed) */
    public TextModal(Component title, Draw.Icon icon, Component text, String key, Component hint, Component button, Ui.Style style,
                     Function<PoliticsView, Component> denied, Consumer<String> send) {
        super(title, icon, WIDTH, TITLE + 6 + lines(text) * 10 + 32 + FOOTER);
        this.key = key;
        this.text = text;
        this.hint = hint;
        this.button = button;
        this.style = style;
        this.denied = denied;
        this.send = send;
    }

    private static int lines(Component text) {
        return text == null ? 0 : Minecraft.getInstance().font.split(text, WIDTH - 16).size();
    }

    @Override
    protected void init() {
        field = field(key, key.startsWith("player") ? 16 : 24, hint, false, "");
    }

    @Override
    public void render(GuiGraphics g, PoliticsView v, float partial) {
        int ty = text == null ? y + 2 : text(g, text, x, y + 2, w, Draw.MUTED) + 6;
        place(field, x, ty, w);
        Component why = denied.apply(v);
        if (why != null) Draw.text(g, font, Draw.fit(font, why, (int) (w / 0.75f)), x, ty + 17, 0.75f, Draw.BAD, false);
    }

    @Override
    public List<Action> actions(PoliticsView v) {
        Component why = denied.apply(v);
        if (why == null && field.getValue().isBlank()) why = tr("type_name");
        return List.of(action(button, style, why, () -> send.accept(field.getValue().trim())));
    }
}
