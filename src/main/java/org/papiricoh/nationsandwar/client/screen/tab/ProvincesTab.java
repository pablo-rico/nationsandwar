package org.papiricoh.nationsandwar.client.screen.tab;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.papiricoh.nationsandwar.client.gui.Draw;
import org.papiricoh.nationsandwar.client.gui.Skins;
import org.papiricoh.nationsandwar.client.gui.Ui;
import org.papiricoh.nationsandwar.client.screen.NationScreen;
import org.papiricoh.nationsandwar.model.Perm;
import org.papiricoh.nationsandwar.view.PoliticsView;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * My nation's provinces: the list and the form to create one (left); the chosen province's towns with capital and
 * remove buttons, the nation's free towns to add, and its governor picked from the citizens (right).
 */
public final class ProvincesTab extends Tab {
    private EditBox name, rename;
    private UUID selected;
    private int capitalPick;
    private final Ui.Scroll list = new Ui.Scroll(), towns = new Ui.Scroll(), people = new Ui.Scroll();

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

    @Override
    public void init() {
        name = field("name", 24, tr("province_name"), false);
        rename = field("rename", 24, tr("new_name"), false);
    }

    private static PoliticsView.ProvinceView find(PoliticsView.NationView n, UUID id) {
        for (PoliticsView.ProvinceView p : n.provinces()) if (p.id().equals(id)) return p;
        return null;
    }

    @Override
    public void render(GuiGraphics g, PoliticsView v, float partial) {
        PoliticsView.NationView n = v.nation();
        if (find(n, selected) == null) selected = n.provinces().isEmpty() ? null : n.provinces().getFirst().id();
        int lw = Math.min(140, w / 3);
        Component denied = need(n.myPerms(), Perm.MANAGE_PROVINCES);
        provinceList(g, v, n, x + 2, y + 2, lw, denied);
        PoliticsView.ProvinceView p = find(n, selected);
        int dx = x + lw + 10, dw = w - lw - 12;
        if (p == null) {
            g.drawWordWrap(font, tr("no_provinces"), dx, y + 20, dw, Draw.MUTED);
            return;
        }
        detail(g, v, n, p, dx, y + 2, dw, denied);
    }

    private void provinceList(GuiGraphics g, PoliticsView v, PoliticsView.NationView n, int lx, int ly, int lw, Component denied) {
        heading(g, Component.translatable("nationsandwar.info.province_list").append(" " + n.provinces().size()), lx, ly, lw);
        int top = ly + 13, lh = h - 64;
        Draw.inset(g, lx, top, lw, lh);
        list.content = n.provinces().size() * 24 + 2;
        ui.scrollZone(lx, top, lw, lh, list);
        ui.clip(g, lx + 1, top + 1, lw - 2, lh - 2);
        int ry = top + 2 - list.offset;
        for (PoliticsView.ProvinceView p : n.provinces()) {
            ui.row(g, lx + 1, ry, lw - 2, 22, p.id().equals(selected), () -> selected = p.id());
            g.drawString(font, Draw.fit(font, Component.literal(p.name()), lw - 30), lx + 6, ry + 2, Draw.TEXT, false);
            Component gov = p.governor() == null ? tr("no_governor") : Component.literal(p.governorName());
            Draw.text(g, font, Draw.fit(font, gov, (int) ((lw - 12) / 0.75f)), lx + 6, ry + 12, 0.75f, p.governor() == null ? 0xFF6A7384 : Draw.INFO, false);
            Component count = Component.literal(String.valueOf(p.towns().size()));
            g.drawString(font, count, lx + lw - 6 - font.width(count), ry + 2, Draw.MUTED, false);
            ry += 24;
        }
        ui.unclip(g);
        ui.scrollbar(g, lx + lw - 3, top + 1, lh - 2, list);
        int fy = top + lh + 4;
        List<PoliticsView.TownSummary> free = freeTowns(v, n);
        place(name, lx, fy, lw);
        fy += 16;
        PoliticsView.TownSummary cap = free.isEmpty() ? null : free.get(Math.floorMod(capitalPick, free.size()));
        ui.button(g, lx, fy, 12, Component.literal("‹"), free.size() < 2 ? tr("no_choice") : null, () -> capitalPick--);
        Component capName = cap == null ? tr("no_free_towns") : Component.literal("★ " + cap.name());
        g.drawCenteredString(font, Draw.fit(font, capName, lw - 30), lx + lw / 2, fy + 3, cap == null ? Draw.MUTED : Draw.GOLD);
        ui.button(g, lx + lw - 12, fy, 12, Component.literal("›"), free.size() < 2 ? tr("no_choice") : null, () -> capitalPick++);
        ui.tip(lx + 14, fy, lw - 28, 13, tr("province_capital"));
        fy += 15;
        int cbw = lw;
        Component why = denied != null ? denied : cap == null ? tr("no_free_towns") : name.getValue().isBlank() ? tr("type_name") : null;
        ui.button(g, lx + lw - cbw, fy, cbw, 13, tr("create_cost", v.prices().province()), Ui.Style.GOLD, why, () -> {
            act("province.create", "name", name.getValue().trim(), "town", cap.id());
            screen.clearField(name);
        });
    }

    /** The nation's towns that are in no province. */
    private static List<PoliticsView.TownSummary> freeTowns(PoliticsView v, PoliticsView.NationView n) {
        List<PoliticsView.TownSummary> out = new ArrayList<>();
        for (UUID t : n.towns()) {
            PoliticsView.TownSummary s = v.townSummary(t);
            if (s != null && s.province() == null) out.add(s);
        }
        return out;
    }

    private void detail(GuiGraphics g, PoliticsView v, PoliticsView.NationView n, PoliticsView.ProvinceView p, int dx, int dy, int dw, Component denied) {
        g.drawString(font, Component.literal(p.name()), dx, dy, Draw.GOLD, true);
        place(rename, dx + dw - 160, dy - 2, 80);
        ui.button(g, dx + dw - 78, dy - 2, 40, tr("rename"), denied != null ? denied : rename.getValue().isBlank() ? tr("type_name") : null, () -> {
            act("province.rename", "province", p.id(), "name", rename.getValue().trim());
            screen.clearField(rename);
        });
        ui.button(g, dx + dw - 36, dy - 2, 36, 13, tr("delete"), Ui.Style.BAD, denied, () -> act("province.delete", "province", p.id()));
        int cw = (dw - 6) / 2, top = dy + 16;
        townsColumn(g, v, n, p, dx, top, cw, denied);
        governorColumn(g, n, p, dx + cw + 6, top, dw - cw - 6, denied);
    }

    private void townsColumn(GuiGraphics g, PoliticsView v, PoliticsView.NationView n, PoliticsView.ProvinceView p, int cx, int cy, int cw, Component denied) {
        heading(g, Component.translatable("nationsandwar.info.towns"), cx, cy, cw);
        int top = cy + 12, lh = y + h - top - 2;
        Draw.inset(g, cx, top, cw, lh);
        List<PoliticsView.TownSummary> free = freeTowns(v, n);
        towns.content = (p.towns().size() + free.size()) * 13 + 14;
        ui.scrollZone(cx, top, cw, lh, towns);
        ui.clip(g, cx + 1, top + 1, cw - 2, lh - 2);
        int ry = top + 2 - towns.offset;
        for (UUID t : p.towns()) {
            boolean capital = t.equals(p.capital());
            ui.row(g, cx + 1, ry, cw - 2, 12, false, null);
            g.drawString(font, Draw.fit(font, Component.literal((capital ? "★ " : "") + townName(v, t)), cw - 34), cx + 4, ry + 2, capital ? Draw.GOLD : Draw.TEXT, false);
            if (!capital) {
                ui.button(g, cx + cw - 28, ry + 1, 12, 10, Component.literal("★"), Ui.Style.GOLD, denied, () -> act("province.capital", "province", p.id(), "town", t));
                ui.button(g, cx + cw - 15, ry + 1, 12, 10, Component.literal("✕"), Ui.Style.BAD, denied, () -> act("province.remove", "province", p.id(), "town", t));
                ui.tip(cx + cw - 28, ry + 1, 12, 10, tr("make_capital"));
            }
            ry += 13;
        }
        if (!free.isEmpty()) {
            Draw.text(g, font, tr("add_towns"), cx + 4, ry + 3, 0.75f, Draw.MUTED, false);
            ry += 12;
        }
        for (PoliticsView.TownSummary t : free) {
            g.drawString(font, Draw.fit(font, Component.literal(t.name()), cw - 22), cx + 4, ry + 2, Draw.MUTED, false);
            ui.button(g, cx + cw - 15, ry + 1, 12, 10, Component.literal("+"), Ui.Style.GOOD, denied, () -> act("province.add", "province", p.id(), "town", t.id()));
            ry += 13;
        }
        ui.unclip(g);
        ui.scrollbar(g, cx + cw - 3, top + 1, lh - 2, towns);
    }

    private void governorColumn(GuiGraphics g, PoliticsView.NationView n, PoliticsView.ProvinceView p, int cx, int cy, int cw, Component denied) {
        heading(g, tr("governor"), cx, cy, cw);
        int gy = cy + 13;
        Draw.panel(g, cx, gy, cw, 26);
        if (p.governor() != null) {
            Skins.face(g, p.governor(), p.governorName(), cx + 4, gy + 5, 16);
            g.drawString(font, Draw.fit(font, Component.literal(p.governorName()), cw - 60), cx + 24, gy + 9, Draw.TEXT, false);
            int clw = font.width(tr("clear")) + 8;
            ui.button(g, cx + cw - clw - 4, gy + 6, clw, 13, tr("clear"), Ui.Style.BAD, denied, () -> act("province.governor", "province", p.id(), "player", ""));
        } else {
            g.drawString(font, tr("no_governor"), cx + 6, gy + 9, Draw.MUTED, false);
        }
        int top = gy + 30, lh = y + h - top - 2;
        Draw.text(g, font, tr("pick_governor"), cx + 1, top - 1, 0.75f, Draw.MUTED, false);
        top += 7;
        lh -= 7;
        Draw.inset(g, cx, top, cw, lh);
        people.content = n.citizens().size() * 12 + 2;
        ui.scrollZone(cx, top, cw, lh, people);
        ui.clip(g, cx + 1, top + 1, cw - 2, lh - 2);
        int ry = top + 2 - people.offset;
        for (PoliticsView.Member m : n.citizens()) {
            boolean is = m.id().equals(p.governor());
            ui.row(g, cx + 1, ry, cw - 2, 12, is, denied == null && !is ? () -> act("province.governor", "province", p.id(), "player", m.id()) : null);
            Skins.face(g, m.id(), m.name(), cx + 4, ry + 2, 8);
            g.drawString(font, Draw.fit(font, Component.literal(m.name()), cw - 20), cx + 15, ry + 2, m.online() ? Draw.TEXT : Draw.MUTED, false);
            ry += 12;
        }
        ui.unclip(g);
        ui.scrollbar(g, cx + cw - 3, top + 1, lh - 2, people);
    }
}
