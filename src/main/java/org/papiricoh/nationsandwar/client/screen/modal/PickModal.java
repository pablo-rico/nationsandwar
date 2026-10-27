package org.papiricoh.nationsandwar.client.screen.modal;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.papiricoh.nationsandwar.client.gui.Draw;
import org.papiricoh.nationsandwar.client.gui.Skins;
import org.papiricoh.nationsandwar.client.gui.Ui;
import org.papiricoh.nationsandwar.view.PoliticsView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Pick one entry of a searchable list, then press the button: add a town to a province, appoint a governor, invite a town
 * or a nation. Rows are rebuilt from every fresh view. With {@link #withName} a name field comes first (a new province:
 * its name and its capital). The {@link #NONE} row stands for "nobody" (clearing a governor).
 */
public final class PickModal extends Modal {
    public static final UUID NONE = new UUID(0, 0);

    /**
     * A row: a colour bar, or a face when {@code face} is set; {@code current} wears a "current" chip; {@code denied}
     * non-null greys it out.
     */
    public record Row(UUID id, Component label, Component sub, int color, UUID face, String faceName, boolean current, Component denied) {
        public static Row of(UUID id, Component label, Component sub, int color) {
            return new Row(id, label, sub, color, null, null, false, null);
        }

        public static Row person(UUID id, String name, Component sub, boolean current) {
            return new Row(id, Component.literal(name), sub, 0x5C6370, id, name, current, null);
        }
    }

    private static final int WIDTH = 260;
    private final Component text, button;
    private final Ui.Style style;
    private final Function<PoliticsView, List<Row>> rows;
    private final Function<PoliticsView, Component> denied;
    private final BiConsumer<UUID, String> apply;
    private final Ui.Scroll scroll = new Ui.Scroll();
    private String nameKey;
    private Component nameHint;
    private EditBox name, search;
    private UUID picked;

    /**
     * @param denied the reason the viewer may not do it at all (null: allowed)
     * @param apply  the picked row's id and the name field's text ("" without one)
     */
    public PickModal(Component title, Draw.Icon icon, Component text, Function<PoliticsView, List<Row>> rows, Component button, Ui.Style style,
                     Function<PoliticsView, Component> denied, BiConsumer<UUID, String> apply) {
        super(title, icon, WIDTH, 236);
        this.text = text;
        this.rows = rows;
        this.button = button;
        this.style = style;
        this.denied = denied;
        this.apply = apply;
    }

    /** Adds a name field above the list. */
    public PickModal withName(String key, Component hint) {
        this.nameKey = key;
        this.nameHint = hint;
        return this;
    }

    @Override
    protected void init() {
        if (nameKey != null) name = field(nameKey, 24, nameHint, false, "");
        search = field("pick.search", 24, tr("search"), false, "");
        search.setValue("");
    }

    @Override
    public void render(GuiGraphics g, PoliticsView v, float partial) {
        int ty = y + 1;
        if (text != null) ty = text(g, text, x, ty, w, Draw.MUTED) + 4;
        if (name != null) {
            label(g, tr("name"), x, ty);
            place(name, x, ty + 8, w);
            ty += 26;
        }
        place(search, x, ty, w);
        ty += 17;
        List<Row> all = rows.apply(v), shown = new ArrayList<>();
        String q = search.getValue().trim().toLowerCase(Locale.ROOT);
        for (Row r : all) if (q.isEmpty() || r.label().getString().toLowerCase(Locale.ROOT).contains(q)) shown.add(r);
        boolean present = false;
        for (Row r : all) present |= r.id().equals(picked);
        if (!present) picked = null;
        int lh = y + h - ty - 10;
        Draw.inset(g, x, ty, w, lh);
        scroll.content = shown.size() * 16 + 2;
        ui.scrollZone(x, ty, w, lh, scroll);
        ui.clip(g, x + 1, ty + 1, w - 2, lh - 2);
        int ry = ty + 2 - scroll.offset;
        for (Row r : shown) {
            boolean sel = r.id().equals(picked), off = r.denied() != null;
            ui.row(g, x + 1, ry, w - 2, 15, sel, off ? null : () -> picked = r.id());
            if (r.face() != null) Skins.face(g, r.face(), r.faceName(), x + 5, ry + 3, 9);
            else {
                g.fillGradient(x + 5, ry + 3, x + 14, ry + 12, Draw.opaque(Draw.mix(r.color(), 0xFFFFFF, 0.2f)), Draw.opaque(Draw.shade(r.color(), 0.7f)));
                Draw.frame(g, x + 5, ry + 3, 9, 9, 0xFF0B0E12);
            }
            int right = x + w - 6;
            if (r.current()) right -= ui.chip(g, right - font.width(tr("current")) - 6, ry + 3, tr("current"), Draw.GOLD) + 3;
            int tw = right - (x + 18);
            int labelW = Math.min(font.width(r.label()), r.sub() == null ? tw : tw * 2 / 3);
            g.drawString(font, Draw.fit(font, r.label(), labelW + 1), x + 18, ry + 4, off ? 0xFF5C6370 : Draw.TEXT, false);
            if (r.sub() != null) {
                float sx = x + 22 + labelW;
                Component sub = Draw.fit(font, r.sub(), (int) ((right - sx) / 0.7f));
                Draw.text(g, font, sub, sx, ry + 5.5f, 0.7f, Draw.MUTED, false);
            }
            if (off) ui.tip(x + 1, ry, w - 2, 15, r.denied());
            ry += 16;
        }
        if (shown.isEmpty()) g.drawCenteredString(font, tr(all.isEmpty() ? "nothing_to_pick" : "no_match"), x + w / 2, ty + lh / 2 - 4, Draw.MUTED);
        ui.unclip(g);
        ui.scrollbar(g, x + w - 3, ty + 1, lh - 2, scroll);
        Component why = denied.apply(v);
        if (why != null) Draw.text(g, font, Draw.fit(font, why, (int) (w / 0.75f)), x, y + h - 7, 0.75f, Draw.BAD, false);
    }

    @Override
    public List<Action> actions(PoliticsView v) {
        Component why = denied.apply(v);
        if (why == null && name != null && name.getValue().isBlank()) why = tr("type_name");
        if (why == null && picked == null) why = tr("pick_one");
        UUID id = picked;
        return List.of(action(button, style, why, () -> apply.accept(id, name == null ? "" : name.getValue().trim())));
    }
}
