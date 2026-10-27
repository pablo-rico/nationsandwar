package org.papiricoh.nationsandwar.client.screen.tab;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.papiricoh.nationsandwar.client.gui.Draw;
import org.papiricoh.nationsandwar.client.gui.Skins;
import org.papiricoh.nationsandwar.client.gui.Ui;
import org.papiricoh.nationsandwar.client.screen.NationScreen;
import org.papiricoh.nationsandwar.model.GovernmentType;
import org.papiricoh.nationsandwar.model.Perm;
import org.papiricoh.nationsandwar.view.PoliticsView;

import java.util.UUID;

/**
 * My nation: facts, bank, name and colour (left); the government, which only renames the nation and its ranks (middle);
 * its towns with capital and kick buttons plus the free towns to invite, and its citizens (right).
 */
public final class NationTab extends Tab {
    private EditBox amount, rename;
    private final Ui.Scroll towns = new Ui.Scroll(), citizens = new Ui.Scroll();
    private boolean confirmDelete;

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
    public void init() {
        amount = field("amount", 7, tr("amount"), true);
        rename = field("rename", 24, tr("new_name"), false);
    }

    @Override
    public void render(GuiGraphics g, PoliticsView v, float partial) {
        PoliticsView.NationView n = v.nation();
        int colW = (w - 16) / 3;
        info(g, v, n, x + 2, y + 2, colW);
        government(g, v, n, x + 8 + colW, y + 2, colW);
        members(g, v, n, x + 14 + 2 * colW, y + 2, w - 16 - 2 * colW);
    }

    private void info(GuiGraphics g, PoliticsView v, PoliticsView.NationView n, int cx, int cy, int cw) {
        boolean leader = leads(n.roles(), v.me());
        boolean mayor = v.town() != null && leads(v.town().roles(), v.me());
        heading(g, Component.literal(n.name()).withStyle(s -> s.withColor(Draw.mix(Draw.opaque(n.color()), 0xFFFFFFFF, 0.3f) & 0xFFFFFF)), cx, cy, cw);
        int sy = cy + 13;
        GovernmentType gt = GovernmentType.byId(n.government());
        sy = stat(g, tr("government"), Component.translatable(gt.key()), cx, sy, cw, Draw.TEXT);
        sy = stat(g, Component.translatable("nationsandwar.info.capital"), Component.literal(townName(v, n.capital())), cx, sy, cw, Draw.TEXT);
        sy = stat(g, Component.translatable("nationsandwar.info.towns"), Component.literal(String.valueOf(n.towns().size())), cx, sy, cw, Draw.TEXT);
        sy = stat(g, Component.translatable("nationsandwar.info.citizens"), Component.literal(String.valueOf(n.citizens().size())), cx, sy, cw, Draw.TEXT);
        if (n.overlord() != null) {
            long days = Math.max(0, (n.vassalUntil() - v.now()) / 24000);
            sy = stat(g, tr("overlord"), Component.literal(nationName(v, n.overlord()) + " (" + days + "d)"), cx, sy, cw, Draw.WARN);
        }
        sy = stat(g, Component.translatable("nationsandwar.info.bank"), money(n.bank()), cx, sy, cw, Draw.GOOD);
        bank(g, amount, cx, sy + 1, cw, need(n.myPerms(), Perm.BANK_WITHDRAW), "nation.deposit", "nation.withdraw");
        sy += 20;
        heading(g, tr("identity"), cx, sy, cw);
        sy += 13;
        place(rename, cx, sy, cw - 50);
        ui.button(g, cx + cw - 48, sy, 48, tr("rename"), !leader ? tr("leader_only") : rename.getValue().isBlank() ? tr("type_name") : null, () -> {
            act("nation.rename", "name", rename.getValue().trim());
            screen.clearField(rename);
        });
        sy += 17;
        sy += palette(g, cx, sy, cw, n.color(), leader ? c -> act("nation.color", "color", c) : null) + 4;
        if (leader) {
            ui.button(g, cx, sy, cw, 13, confirmDelete ? tr("confirm_delete") : tr("delete_nation"), Ui.Style.BAD, null, () -> {
                if (confirmDelete) act("nation.delete");
                confirmDelete = !confirmDelete;
            });
        } else {
            ui.button(g, cx, sy, cw, 13, tr("leave_nation"), Ui.Style.BAD, mayor ? null : tr("mayor_only"), () -> act("nation.leave"));
        }
    }

    private void government(GuiGraphics g, PoliticsView v, PoliticsView.NationView n, int cx, int cy, int cw) {
        boolean leader = leads(n.roles(), v.me());
        heading(g, tr("government"), cx, cy, cw);
        int sy = cy + 13;
        for (GovernmentType gt : GovernmentType.values()) {
            boolean cur = gt.id().equals(n.government());
            int rh = 24;
            boolean hover = ui.over(cx, sy, cw, rh);
            Draw.slab(g, cx, sy, cw - 3, rh - 3, 2, cur ? 0xFF3A3424 : hover && leader ? 0xFF2C3440 : 0xFF232A34, cur ? 0xFF2A2518 : 0xFF1A1F27);
            if (cur) Draw.frame(g, cx, sy, cw - 3, rh - 3, Draw.GOLD);
            g.drawString(font, Component.translatable(gt.key()), cx + 5, sy + 3, cur ? Draw.GOLD : Draw.TEXT, false);
            Component titles = Component.translatable(gt.key("leader")).append(" · ").append(Component.translatable(gt.key("governor")));
            Draw.text(g, font, Draw.fit(font, titles, (int) ((cw - 12) / 0.7f)), cx + 5, sy + 13, 0.7f, Draw.MUTED, false);
            if (!cur && leader) ui.zone(cx, sy, cw - 3, rh - 3, () -> act("nation.gov", "gov", gt.id()));
            else if (!cur) ui.tip(cx, sy, cw - 3, rh - 3, tr("leader_only"));
            sy += rh + 1;
        }
        Draw.text(g, font, Draw.fit(font, tr("gov_hint"), (int) (cw / 0.6f)), cx, sy + 1, 0.6f, 0xFF6A7384, false);
    }

    private void members(GuiGraphics g, PoliticsView v, PoliticsView.NationView n, int cx, int cy, int cw) {
        boolean leader = leads(n.roles(), v.me());
        Component manage = need(n.myPerms(), Perm.MANAGE_TOWNS);
        heading(g, Component.translatable("nationsandwar.info.towns"), cx, cy, cw);
        int top = cy + 12, lh = (h - 30) / 2;
        Draw.inset(g, cx, top, cw, lh);
        int free = 0;
        for (PoliticsView.TownSummary t : v.towns()) if (t.nation() == null && !t.npc()) free++;
        towns.content = (n.towns().size() + free) * 13 + (free > 0 ? 12 : 0) + 2;
        ui.scrollZone(cx, top, cw, lh, towns);
        ui.clip(g, cx + 1, top + 1, cw - 2, lh - 2);
        int ry = top + 2 - towns.offset;
        for (UUID t : n.towns()) {
            boolean capital = t.equals(n.capital());
            PoliticsView.TownSummary s = v.townSummary(t);
            ui.row(g, cx + 1, ry, cw - 2, 12, false, null);
            if (s != null) g.fill(cx + 3, ry + 3, cx + 7, ry + 7, Draw.opaque(s.color()));
            g.drawString(font, Draw.fit(font, Component.literal((capital ? "★ " : "") + townName(v, t)), cw - 40), cx + 10, ry + 2, capital ? Draw.GOLD : Draw.TEXT, false);
            if (!capital) {
                ui.button(g, cx + cw - 28, ry + 1, 12, 10, Component.literal("★"), Ui.Style.GOLD, leader ? null : tr("leader_only"), () -> act("nation.capital", "town", t));
                ui.button(g, cx + cw - 15, ry + 1, 12, 10, Component.literal("✕"), Ui.Style.BAD, manage, () -> act("nation.kick", "town", t));
            }
            ry += 13;
        }
        if (free > 0) {
            Draw.text(g, font, tr("invite_towns"), cx + 4, ry + 3, 0.75f, Draw.MUTED, false);
            ry += 12;
        }
        for (PoliticsView.TownSummary t : v.towns()) {
            if (t.nation() != null || t.npc()) continue;
            boolean invited = n.invitedTowns().contains(t.id());
            g.drawString(font, Draw.fit(font, Component.literal(t.name()), cw - 50), cx + 4, ry + 2, Draw.MUTED, false);
            ui.button(g, cx + cw - 42, ry + 1, 39, 10, tr(invited ? "invited" : "invite"), Ui.Style.GOOD, invited ? tr("invited") : manage,
                    () -> act("nation.invite", "town", t.id()));
            ry += 13;
        }
        ui.unclip(g);
        ui.scrollbar(g, cx + cw - 3, top + 1, lh - 2, towns);

        int cy2 = top + lh + 4;
        heading(g, Component.translatable("nationsandwar.info.citizens").append(" · " + n.citizens().size()), cx, cy2, cw);
        int top2 = cy2 + 12, lh2 = y + h - top2 - 2;
        Draw.inset(g, cx, top2, cw, lh2);
        citizens.content = n.citizens().size() * 12 + 2;
        ui.scrollZone(cx, top2, cw, lh2, citizens);
        ui.clip(g, cx + 1, top2 + 1, cw - 2, lh2 - 2);
        ry = top2 + 2 - citizens.offset;
        for (PoliticsView.Member m : n.citizens()) {
            Skins.face(g, m.id(), m.name(), cx + 3, ry + 2, 8);
            g.fill(cx + 13, ry + 8, cx + 16, ry + 11, m.online() ? Draw.GOOD : 0xFF555B66);
            g.drawString(font, Draw.fit(font, Component.literal(m.name()), cw / 2), cx + 19, ry + 2, m.online() ? Draw.TEXT : Draw.MUTED, false);
            for (PoliticsView.RoleView r : n.roles()) {
                if (r.id() != m.role()) continue;
                Component rn = Draw.fit(font, roleName(r.name()), (int) ((cw / 2 - 8) / 0.75f));
                Draw.text(g, font, rn, cx + cw - 5 - font.width(rn) * 0.75f, ry + 3, 0.75f, Draw.mix(Draw.opaque(r.color()), 0xFFFFFFFF, 0.3f), false);
            }
            ry += 12;
        }
        ui.unclip(g);
        ui.scrollbar(g, cx + cw - 3, top2 + 1, lh2 - 2, citizens);
    }
}
