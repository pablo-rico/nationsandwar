package org.papiricoh.nationsandwar.client.screen.tab;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.papiricoh.nationsandwar.client.gui.Draw;
import org.papiricoh.nationsandwar.client.gui.Skins;
import org.papiricoh.nationsandwar.client.gui.Ui;
import org.papiricoh.nationsandwar.client.screen.NationScreen;
import org.papiricoh.nationsandwar.client.screen.modal.ConfirmModal;
import org.papiricoh.nationsandwar.client.screen.modal.NameColorModal;
import org.papiricoh.nationsandwar.client.screen.modal.PickModal;
import org.papiricoh.nationsandwar.model.Perm;
import org.papiricoh.nationsandwar.view.PoliticsView;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * My nation's provinces: the list in their map colours with their governors (left); the chosen province's card — colour,
 * capital, governor — and its towns, with buttons to make one the capital or take it out (right). The action bar creates
 * a province, renames and recolours it, adds a town, appoints its governor and deletes it (after asking).
 */
public final class ProvincesTab extends Tab {
    private UUID selected;
    private final Ui.Scroll list = new Ui.Scroll(), towns = new Ui.Scroll();

    public ProvincesTab(NationScreen screen) {
        super(screen);
    }

    @Override
    public String id() {
        return "provinces";
    }

    @Override
    public Draw.Icon icon() {
        return Draw.Icon.PROVINCES;
    }

    @Override
    public Component unavailable(PoliticsView v) {
        return v.nation() == null ? tr("no_nation") : null;
    }

    private static PoliticsView.ProvinceView find(PoliticsView.NationView n, UUID id) {
        for (PoliticsView.ProvinceView p : n.provinces()) if (p.id().equals(id)) return p;
        return null;
    }

    /** Selects a province (the Atlas's province cards link here). */
    public void select(UUID province) {
        selected = province;
    }

    @Override
    public void render(GuiGraphics g, PoliticsView v, float partial) {
        PoliticsView.NationView n = v.nation();
        if (find(n, selected) == null) selected = n.provinces().isEmpty() ? null : n.provinces().getFirst().id();
        int lw = Math.min(150, w / 3), ch = h - BAR - 4;
        provinceList(g, n, x + 2, y + 2, lw, ch);
        PoliticsView.ProvinceView p = find(n, selected);
        int dx = x + lw + 10, dw = w - lw - 12;
        if (p == null) {
            Draw.inset(g, dx, y + 2, dw, ch);
            g.drawWordWrap(font, tr("no_provinces"), dx + 10, y + ch / 2 - 8, dw - 20, Draw.MUTED);
        } else detail(g, v, n, p, dx, y + 2, dw, ch);
        actionBar(g, acts(v, n, p));
    }

    private List<Act> acts(PoliticsView v, PoliticsView.NationView n, PoliticsView.ProvinceView p) {
        Component denied = need(n.myPerms(), Perm.MANAGE_PROVINCES), none = p == null ? tr("no_province_picked") : null;
        Component onP = none != null ? none : denied;
        List<Act> acts = new ArrayList<>();
        acts.add(Act.modal(tr("create_province"), Ui.Style.GOLD, denied, () -> new PickModal(tr("create_province"), Draw.Icon.PROVINCES,
                tr("create_province_hint", v.prices().province()), view -> freeRows(view), tr("create_cost", v.prices().province()), Ui.Style.GOLD,
                view -> view.nation() == null ? tr("no_nation") : need(view.nation().myPerms(), Perm.MANAGE_PROVINCES),
                (town, name) -> act("province.create", "name", name, "town", town)).withName("province.new", tr("province_name"))));
        acts.add(Act.modal(tr("identity"), onP, () -> new NameColorModal(tr("province_identity", p.name()), Draw.Icon.PROVINCES,
                "province.name." + p.id(), p.name(), p.color(), tr("save"), null, (name, color) -> {
            if (!name.equals(p.name())) act("province.rename", "province", p.id(), "name", name);
            if (color != (p.color() & 0xFFFFFF) || !p.picked()) act("province.color", "province", p.id(), "color", color);
        })));
        acts.add(Act.modal(tr("add_town"), onP, () -> new PickModal(tr("add_town_to", p.name()), Draw.Icon.TOWN, tr("add_town_hint"),
                ProvincesTab::freeRows, tr("add"), Ui.Style.GOOD, view -> view.nation() == null ? tr("no_nation") : need(view.nation().myPerms(), Perm.MANAGE_PROVINCES),
                (town, name) -> act("province.add", "province", p.id(), "town", town))));
        acts.add(Act.modal(tr("governor"), onP, () -> new PickModal(tr("governor_of", p.name()), Draw.Icon.SCROLL, tr("pick_governor_hint"), view -> {
            List<PickModal.Row> rows = new ArrayList<>();
            if (view.nation() == null) return rows;
            PoliticsView.ProvinceView now = find(view.nation(), p.id());
            UUID current = now == null ? null : now.governor();
            rows.add(PickModal.Row.of(PickModal.NONE, tr("no_governor"), null, 0x3A3F48));
            for (PoliticsView.Member m : view.nation().citizens())
                rows.add(PickModal.Row.person(m.id(), m.name(), tr(m.online() ? "online" : "offline"), m.id().equals(current)));
            return rows;
        }, tr("appoint"), Ui.Style.GOLD, view -> view.nation() == null ? tr("no_nation") : need(view.nation().myPerms(), Perm.MANAGE_PROVINCES),
                (id, name) -> act("province.governor", "province", p.id(), "player", PickModal.NONE.equals(id) ? "" : id.toString()))));
        acts.add(Act.modal(tr("delete_province"), Ui.Style.BAD, onP, () -> new ConfirmModal(tr("delete_province"),
                tr("confirm_delete_province", p.name()), tr("delete"), () -> act("province.delete", "province", p.id()))));
        return acts;
    }

    /** The nation's towns that are in no province, as rows to pick from. */
    private static List<PickModal.Row> freeRows(PoliticsView v) {
        List<PickModal.Row> rows = new ArrayList<>();
        if (v.nation() == null) return rows;
        for (UUID t : v.nation().towns()) {
            PoliticsView.TownSummary s = v.townSummary(t);
            if (s != null && s.province() == null)
                rows.add(PickModal.Row.of(s.id(), Component.literal(s.name()), tr("n_residents", s.residents()), s.color()));
        }
        return rows;
    }

    private void provinceList(GuiGraphics g, PoliticsView.NationView n, int lx, int ly, int lw, int ch) {
        heading(g, Component.translatable("nationsandwar.info.province_list").append(" " + n.provinces().size()), lx, ly, lw);
        int top = ly + 13, lh = ly + ch - top;
        Draw.inset(g, lx, top, lw, lh);
        list.content = n.provinces().size() * 26 + 2;
        ui.scrollZone(lx, top, lw, lh, list);
        ui.clip(g, lx + 1, top + 1, lw - 2, lh - 2);
        int ry = top + 2 - list.offset;
        for (PoliticsView.ProvinceView p : n.provinces()) {
            colorRow(g, lx + 2, ry, lw - 4, 24, p.color(), p.id().equals(selected), () -> selected = p.id());
            g.drawString(font, Draw.fit(font, Component.literal(p.name()), lw - 34), lx + 8, ry + 3, Draw.TEXT, false);
            Component gov = p.governor() == null ? tr("no_governor") : Component.literal(p.governorName());
            Draw.text(g, font, Draw.fit(font, gov, (int) ((lw - 16) / 0.75f)), lx + 8, ry + 14, 0.75f, p.governor() == null ? 0xFF6A7384 : Draw.INFO, false);
            Component count = Component.literal(String.valueOf(p.towns().size()));
            g.drawString(font, count, lx + lw - 8 - font.width(count), ry + 3, Draw.MUTED, false);
            ry += 26;
        }
        ui.unclip(g);
        ui.scrollbar(g, lx + lw - 3, top + 1, lh - 2, list);
    }

    private void detail(GuiGraphics g, PoliticsView v, PoliticsView.NationView n, PoliticsView.ProvinceView p, int dx, int dy, int dw, int ch) {
        Component denied = need(n.myPerms(), Perm.MANAGE_PROVINCES);
        int c = Draw.opaque(p.color()), cardH = 46;
        int by = card(g, dx, dy + 3, dw - 3, cardH, p.color(), Draw.Icon.PROVINCES, Component.literal(p.name()));
        Component colour = tr(p.picked() ? "colour_picked" : "colour_default");
        Draw.text(g, font, colour, dx + dw - 10 - font.width(colour) * 0.7f, dy + 13, 0.7f, Draw.MUTED, false);
        g.fill(dx + dw - 20 - (int) (font.width(colour) * 0.7f), dy + 12, dx + dw - 14 - (int) (font.width(colour) * 0.7f), dy + 18, c);
        Component capital = Component.translatable("nationsandwar.info.capital").append(": ").append(Component.literal(townName(v, p.capital())));
        g.drawString(font, Draw.fit(font, capital, dw / 2 - 10), dx + 6, by - 2, Draw.MUTED, false);
        int gx = dx + dw / 2;
        if (p.governor() != null) {
            Skins.face(g, p.governor(), p.governorName(), gx, by - 3, 10);
            g.drawString(font, Draw.fit(font, tr("governor").append(": " + p.governorName()), dw / 2 - 20), gx + 13, by - 2, Draw.INFO, false);
        } else g.drawString(font, tr("no_governor"), gx, by - 2, 0xFF6A7384, false);

        int top = dy + cardH + 18;
        heading(g, Component.translatable("nationsandwar.info.towns").append(" · " + p.towns().size()), dx, top - 12, dw);
        int lh = dy + ch - top;
        Draw.inset(g, dx, top, dw, lh);
        towns.content = p.towns().size() * 15 + 2;
        ui.scrollZone(dx, top, dw, lh, towns);
        ui.clip(g, dx + 1, top + 1, dw - 2, lh - 2);
        int ry = top + 2 - towns.offset;
        for (UUID t : p.towns()) {
            boolean capitalTown = t.equals(p.capital());
            PoliticsView.TownSummary s = v.townSummary(t);
            ui.row(g, dx + 1, ry, dw - 2, 14, false, null);
            if (s != null) g.fill(dx + 5, ry + 4, dx + 11, ry + 10, Draw.opaque(s.color()));
            g.drawString(font, Draw.fit(font, Component.literal((capitalTown ? "★ " : "") + townName(v, t)), dw / 2), dx + 15, ry + 3, capitalTown ? Draw.GOLD : Draw.TEXT, false);
            if (s != null) {
                Component facts = tr("residents_claims", s.residents(), s.claims());
                Draw.text(g, font, facts, dx + dw / 2 + 10, ry + 4, 0.7f, Draw.MUTED, false);
            }
            if (!capitalTown) {
                ui.button(g, dx + dw - 30, ry + 2, 12, 10, Component.literal("★"), Ui.Style.GOLD, denied, () -> act("province.capital", "province", p.id(), "town", t));
                ui.tip(dx + dw - 30, ry + 2, 12, 10, tr("make_capital"));
                ui.button(g, dx + dw - 16, ry + 2, 12, 10, Component.literal("✕"), Ui.Style.BAD, denied, confirm(tr("remove_town"),
                        tr("confirm_remove_town", townName(v, t), p.name()), tr("remove"), () -> act("province.remove", "province", p.id(), "town", t)));
            }
            ry += 15;
        }
        ui.unclip(g);
        ui.scrollbar(g, dx + dw - 3, top + 1, lh - 2, towns);
    }
}
