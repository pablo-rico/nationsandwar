package org.papiricoh.nationsandwar.client.screen.tab;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.papiricoh.nationsandwar.client.gui.Draw;
import org.papiricoh.nationsandwar.client.gui.Skins;
import org.papiricoh.nationsandwar.client.gui.Ui;
import org.papiricoh.nationsandwar.client.screen.NationScreen;
import org.papiricoh.nationsandwar.client.screen.modal.BankModal;
import org.papiricoh.nationsandwar.client.screen.modal.ConfirmModal;
import org.papiricoh.nationsandwar.client.screen.modal.GovernmentModal;
import org.papiricoh.nationsandwar.client.screen.modal.NameColorModal;
import org.papiricoh.nationsandwar.client.screen.modal.PickModal;
import org.papiricoh.nationsandwar.model.Perm;
import org.papiricoh.nationsandwar.view.PoliticsView;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * My nation: its card with the facts and the bank (left), its towns with capital and kick buttons (middle) and its
 * citizens with their ranks (right). The action bar renames and recolours it, changes its government, opens the bank and
 * invites a free town; leaving or deleting the nation asks first.
 */
public final class NationTab extends Tab {
    private final Ui.Scroll towns = new Ui.Scroll(), citizens = new Ui.Scroll();

    public NationTab(NationScreen screen) {
        super(screen);
    }

    @Override
    public String id() {
        return "nation";
    }

    @Override
    public Draw.Icon icon() {
        return Draw.Icon.NATION;
    }

    @Override
    public Component unavailable(PoliticsView v) {
        return v.nation() == null ? tr("no_nation") : null;
    }

    @Override
    public void render(GuiGraphics g, PoliticsView v, float partial) {
        PoliticsView.NationView n = v.nation();
        int colW = (w - 16) / 3, ch = h - BAR - 4;
        info(g, v, n, x + 2, y + 2, colW, ch);
        townList(g, v, n, x + 8 + colW, y + 2, colW, ch);
        citizenList(g, n, x + 14 + 2 * colW, y + 2, w - 16 - 2 * colW, ch);
        actionBar(g, acts(v, n));
    }

    private List<Act> acts(PoliticsView v, PoliticsView.NationView n) {
        boolean leader = leads(n.roles(), v.me()), mayor = v.town() != null && leads(v.town().roles(), v.me());
        Component leaderOnly = leader ? null : tr("leader_only");
        List<Act> acts = new ArrayList<>();
        acts.add(Act.modal(tr("identity"), leaderOnly, () -> new NameColorModal(tr("identity"), Draw.Icon.NATION, "nation.name." + n.id(), n.name(),
                n.color(), tr("save"), null, (name, color) -> {
            if (!name.equals(n.name())) act("nation.rename", "name", name);
            if (color != (n.color() & 0xFFFFFF)) act("nation.color", "color", color);
        })));
        acts.add(Act.modal(tr("government"), null, () -> new GovernmentModal(false)));
        acts.add(Act.modal(tr("bank"), null, () -> new BankModal(true)));
        acts.add(Act.modal(tr("invite_town"), need(n.myPerms(), Perm.MANAGE_TOWNS), () -> new PickModal(tr("invite_town"), Draw.Icon.TOWN,
                tr("invite_town_hint"), view -> {
            List<PickModal.Row> rows = new ArrayList<>();
            PoliticsView.NationView mine = view.nation();
            if (mine == null) return rows;
            for (PoliticsView.TownSummary t : view.towns()) {
                if (t.nation() != null || t.npc()) continue;
                boolean invited = mine.invitedTowns().contains(t.id());
                rows.add(new PickModal.Row(t.id(), Component.literal(t.name()), tr("mayor_is", t.mayor()), t.color(), null, null, false,
                        invited ? tr("invited") : null));
            }
            return rows;
        }, tr("invite"), Ui.Style.GOOD, view -> view.nation() == null ? tr("no_nation") : need(view.nation().myPerms(), Perm.MANAGE_TOWNS),
                (town, name) -> act("nation.invite", "town", town))));
        if (leader) acts.add(Act.modal(tr("delete_nation"), Ui.Style.BAD, null, () -> new ConfirmModal(tr("delete_nation"),
                tr("confirm_delete_nation", n.name()), tr("delete"), () -> act("nation.delete"))));
        else acts.add(Act.modal(tr("leave_nation"), Ui.Style.BAD, mayor ? null : tr("mayor_only"), () -> new ConfirmModal(tr("leave_nation"),
                tr("confirm_leave_nation", n.name()), tr("leave"), () -> act("nation.leave"))));
        return acts;
    }

    private void info(GuiGraphics g, PoliticsView v, PoliticsView.NationView n, int cx, int cy, int cw, int ch) {
        int by = card(g, cx, cy + 3, cw - 3, ch - 3, n.color(), Draw.Icon.NATION, Component.literal(n.name()));
        int sx = cx + 6, sw = cw - 15, sy = by;
        sy = stat(g, tr("government"), government(n.id(), n.government()), sx, sy, sw, Draw.TEXT);
        sy = stat(g, Component.translatable(org.papiricoh.nationsandwar.client.ClientClaims.titles(n.id(), n.government()) + ".leader"), Component.literal(leaderName(n)), sx, sy, sw, Draw.TEXT);
        sy = stat(g, Component.translatable("nationsandwar.info.capital"), Component.literal(townName(v, n.capital())), sx, sy, sw, Draw.TEXT);
        sy = stat(g, Component.translatable("nationsandwar.info.towns"), Component.literal(String.valueOf(n.towns().size())), sx, sy, sw, Draw.TEXT);
        sy = stat(g, Component.translatable("nationsandwar.info.citizens"), Component.literal(String.valueOf(n.citizens().size())), sx, sy, sw, Draw.TEXT);
        sy = stat(g, Component.translatable("nationsandwar.gui.tab.provinces"), Component.literal(String.valueOf(n.provinces().size())), sx, sy, sw, Draw.TEXT);
        if (n.overlord() != null) {
            long days = Math.max(0, (n.vassalUntil() - v.now()) / 24000);
            sy = stat(g, tr("overlord"), Component.literal(nationName(v, n.overlord()) + " (" + days + "d)"), sx, sy, sw, Draw.WARN);
        }
        if (v.alliance() != null) sy = stat(g, tr("alliance"), Component.literal(v.alliance().name()), sx, sy, sw, Draw.TEXT);
        int py = Math.max(sy + 4, cy + ch - 30);
        Draw.slab(g, sx, py, sw, 24, 2, 0xFF3A3424, 0xFF262014);
        Draw.frame(g, sx, py, sw, 24, Draw.alpha(Draw.GOLD, 0x60));
        Draw.text(g, font, Component.translatable("nationsandwar.info.bank"), sx + 5, py + 4, 0.75f, Draw.MUTED, false);
        Component bank = money(n.bank());
        Draw.text(g, font, bank, sx + sw - 5 - font.width(bank) * 1.25f, py + 8, 1.25f, Draw.GOLD, true);
    }

    private static String leaderName(PoliticsView.NationView n) {
        for (PoliticsView.RoleView r : n.roles())
            if (r.parent() < 0 && !r.holders().isEmpty())
                for (PoliticsView.Member m : n.citizens()) if (m.id().equals(r.holders().getFirst())) return m.name();
        return "—";
    }

    private void townList(GuiGraphics g, PoliticsView v, PoliticsView.NationView n, int cx, int cy, int cw, int ch) {
        boolean leader = leads(n.roles(), v.me());
        Component manage = need(n.myPerms(), Perm.MANAGE_TOWNS);
        heading(g, Component.translatable("nationsandwar.info.towns").append(" · " + n.towns().size()), cx, cy, cw);
        int top = cy + 13, lh = cy + ch - top;
        Draw.inset(g, cx, top, cw, lh);
        towns.content = n.towns().size() * 15 + 2;
        ui.scrollZone(cx, top, cw, lh, towns);
        ui.clip(g, cx + 1, top + 1, cw - 2, lh - 2);
        int ry = top + 2 - towns.offset;
        for (UUID t : n.towns()) {
            boolean capital = t.equals(n.capital());
            PoliticsView.TownSummary s = v.townSummary(t);
            ui.row(g, cx + 1, ry, cw - 2, 14, false, null);
            if (s != null) g.fill(cx + 4, ry + 4, cx + 10, ry + 10, Draw.opaque(s.color()));
            g.drawString(font, Draw.fit(font, Component.literal((capital ? "★ " : "") + townName(v, t)), cw - 44), cx + 14, ry + 3, capital ? Draw.GOLD : Draw.TEXT, false);
            if (!capital) {
                ui.button(g, cx + cw - 30, ry + 2, 12, 10, Component.literal("★"), Ui.Style.GOLD, leader ? null : tr("leader_only"), () -> act("nation.capital", "town", t));
                ui.tip(cx + cw - 30, ry + 2, 12, 10, tr("make_capital"));
                ui.button(g, cx + cw - 16, ry + 2, 12, 10, Component.literal("✕"), Ui.Style.BAD, manage, confirm(tr("kick_town"),
                        tr("confirm_kick_town", townName(v, t), n.name()), tr("kick"), () -> act("nation.kick", "town", t)));
            }
            ry += 15;
        }
        ui.unclip(g);
        ui.scrollbar(g, cx + cw - 3, top + 1, lh - 2, towns);
    }

    private void citizenList(GuiGraphics g, PoliticsView.NationView n, int cx, int cy, int cw, int ch) {
        heading(g, Component.translatable("nationsandwar.info.citizens").append(" · " + n.citizens().size()), cx, cy, cw);
        int top = cy + 13, lh = cy + ch - top;
        Draw.inset(g, cx, top, cw, lh);
        citizens.content = n.citizens().size() * 13 + 2;
        ui.scrollZone(cx, top, cw, lh, citizens);
        ui.clip(g, cx + 1, top + 1, cw - 2, lh - 2);
        int ry = top + 2 - citizens.offset;
        for (PoliticsView.Member m : n.citizens()) {
            Skins.face(g, m.id(), m.name(), cx + 3, ry + 2, 9);
            g.fill(cx + 10, ry + 8, cx + 13, ry + 11, m.online() ? Draw.GOOD : 0xFF555B66);
            g.drawString(font, Draw.fit(font, Component.literal(m.name()), cw / 2), cx + 16, ry + 3, m.online() ? Draw.TEXT : Draw.MUTED, false);
            for (PoliticsView.RoleView r : n.roles()) {
                if (r.id() != m.role()) continue;
                Component rn = Draw.fit(font, roleName(r.name()), (int) ((cw / 2 - 8) / 0.75f));
                Draw.text(g, font, rn, cx + cw - 5 - font.width(rn) * 0.75f, ry + 3.5f, 0.75f, Draw.mix(Draw.opaque(r.color()), 0xFFFFFFFF, 0.3f), false);
            }
            ry += 13;
        }
        ui.unclip(g);
        ui.scrollbar(g, cx + cw - 3, top + 1, lh - 2, citizens);
    }
}
