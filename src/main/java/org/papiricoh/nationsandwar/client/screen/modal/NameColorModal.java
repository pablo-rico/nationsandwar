package org.papiricoh.nationsandwar.client.screen.modal;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.papiricoh.nationsandwar.client.gui.Draw;
import org.papiricoh.nationsandwar.client.gui.Ui;
import org.papiricoh.nationsandwar.client.screen.tab.Tab;
import org.papiricoh.nationsandwar.view.PoliticsView;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * A name field and the palette, with a live preview plate, for a town, nation, province or rank — renaming and recolouring
 * one, or naming a new rank. {@code save} gets the trimmed name and the colour; callers send only what changed.
 */
public final class NameColorModal extends Modal {
    private final String key, name;
    private final Component button, denied;
    private final BiConsumer<String, Integer> save;
    private EditBox field;
    private int color;

    /**
     * @param key    the draft key (one per entity, e.g. "town.name.&lt;id&gt;")
     * @param denied non-null when the viewer may not change it (the reason)
     */
    public NameColorModal(Component title, Draw.Icon icon, String key, String name, int color, Component button, Component denied,
                          BiConsumer<String, Integer> save) {
        super(title, icon, 236, 166);
        this.key = key;
        this.name = name;
        this.color = color & 0xFFFFFF;
        this.button = button;
        this.denied = denied;
        this.save = save;
    }

    @Override
    protected void init() {
        field = field(key, 24, tr("new_name"), false, name);
        field.setEditable(denied == null);
    }

    @Override
    public void render(GuiGraphics g, PoliticsView v, float partial) {
        int c = Draw.opaque(color), py = y + 4;
        Draw.shadow(g, x, py, w - 4, 26, 5);
        Draw.slab(g, x, py, w - 4, 26, 3, Draw.mix(0xFF2B323E, c, 0.35f), Draw.mix(0xFF161A21, c, 0.15f));
        g.fill(x, py, x + 3, py + 26, c);
        g.fillGradient(x + 7, py + 4, x + 25, py + 22, Draw.mix(c, 0xFFFFFFFF, 0.2f), Draw.shade(c, 0.7f));
        Draw.frame(g, x + 7, py + 4, 18, 18, Draw.shade(c, 0.45f));
        Draw.icon(g, icon(), x + 8, py + 5);
        String shown = field.getValue().isBlank() ? name : field.getValue().trim();
        if (shown.isEmpty()) g.drawString(font, Draw.fit(font, tr("new_name"), w - 44), x + 30, py + 9, Draw.alpha(0xFFFFFF, 0x70), false);
        else g.drawString(font, Draw.fit(font, Component.literal(shown), w - 44), x + 30, py + 9, Draw.mix(c, 0xFFFFFFFF, 0.45f), true);
        int ly = py + 34;
        label(g, tr("name"), x, ly);
        place(field, x, ly + 8, w);
        ly += 28;
        label(g, tr("colour"), x, ly);
        Tab.palette(ui, g, x, ly + 9, w, 14, color, denied == null ? col -> color = col : null);
        if (denied != null) Draw.text(g, font, Draw.fit(font, denied, (int) (w / 0.75f)), x, y + h - 8, 0.75f, Draw.BAD, false);
    }

    @Override
    public List<Action> actions(PoliticsView v) {
        Component why = denied != null ? denied : field.getValue().isBlank() ? tr("type_name") : null;
        return List.of(action(button, Ui.Style.GOLD, why, () -> save.accept(field.getValue().trim(), color)));
    }
}
