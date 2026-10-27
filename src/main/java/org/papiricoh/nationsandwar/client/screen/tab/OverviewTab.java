package org.papiricoh.nationsandwar.client.screen.tab;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.papiricoh.nationsandwar.client.gui.Draw;
import org.papiricoh.nationsandwar.client.gui.Ui;
import org.papiricoh.nationsandwar.client.screen.NationScreen;
import org.papiricoh.nationsandwar.client.screen.modal.GovernmentModal;
import org.papiricoh.nationsandwar.client.screen.modal.TextModal;
import org.papiricoh.nationsandwar.view.PoliticsView;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * My town, nation and alliance at a glance as three raised cards, the invitations waiting for me with buttons to accept
 * them, and my wars. The action bar founds a town (when I have none) or a nation (when my town has none), and takes me to
 * my town's spawn.
 */
public final class OverviewTab extends Tab {

    public OverviewTab(NationScreen screen) {
        super(screen);
    }

    @Override
    public String id() {
        return "overview";
    }

    @Override
    public Draw.Icon icon() {
        return Draw.Icon.OVERVIEW;
    }

    @Override
    public void render(GuiGraphics g, PoliticsView v, float partial) {
        int cw = (w - 16) / 3, ch = 84, cy = y + 6;
        townCard(g, v, x + 2, cy, cw, ch);
        nationCard(g, v, x + 2 + cw + 6, cy, cw, ch);
        allianceCard(g, v, x + 2 + 2 * (cw + 6), cy, cw, ch);
        int by = cy + ch + 12, half = (w - 12) / 2;
        invites(g, v, x + 2, by, half);
        wars(g, v, x + 10 + half, by, w - half - 12);
        actionBar(g, acts(v));
    }

    private List<Act> acts(PoliticsView v) {
        List<Act> acts = new ArrayList<>();
        if (v.town() == null) {
            acts.add(Act.modal(tr("found_town"), Ui.Style.GOLD, null, () -> new TextModal(tr("found_town"), Draw.Icon.TOWN,
                    tr("found_town_hint", v.prices().town()), "town.found", tr("town_name"), tr("found_cost", v.prices().town()), Ui.Style.GOLD,
                    view -> view.town() != null ? tr("leave_first") : null, name -> act("town.found", "name", name))));
            return acts;
        }
        if (v.nation() == null)
            acts.add(Act.modal(tr("found_nation"), Ui.Style.GOLD, leads(v.town().roles(), v.me()) ? null : tr("mayor_only"), () -> new GovernmentModal(true)));
        acts.add(Act.now(tr("go_spawn"), Ui.Style.NORMAL, null, () -> act("town.spawn")));
        acts.add(Act.now(tr("open_atlas"), Ui.Style.NORMAL, null, () -> screen.select("atlas")));
        return acts;
    }

    private void townCard(GuiGraphics g, PoliticsView v, int cx, int cy, int cw, int ch) {
        PoliticsView.TownView t = v.town();
        int by = card(g, cx, cy, cw, ch, t == null ? 0x666666 : t.color(), Draw.Icon.TOWN, t == null ? tr("no_town") : Component.literal(t.name()));
        if (t == null) {
            g.drawWordWrap(font, tr("no_town_hint"), cx + 6, by, cw - 12, Draw.MUTED);
            return;
        }
        int online = 0;
        for (PoliticsView.Member m : t.residents()) if (m.online()) online++;
        int sw = cw - 12;
        by = stat(g, Component.translatable("nationsandwar.info.level"), Component.translatable("nationsandwar.level." + t.level().toLowerCase()), cx + 6, by, sw, Draw.TEXT);
        by = stat(g, Component.translatable("nationsandwar.info.residents"), tr("online_of", t.residents().size(), online), cx + 6, by, sw, Draw.TEXT);
        by = stat(g, Component.translatable("nationsandwar.info.claims"), Component.literal(t.claims() + " / " + t.maxClaims()), cx + 6, by, sw, Draw.TEXT);
        stat(g, Component.translatable("nationsandwar.info.bank"), money(t.bank()), cx + 6, by, sw, Draw.GOOD);
        ui.zone(cx, cy, cw, ch, () -> screen.select("town"));
    }

    private void nationCard(GuiGraphics g, PoliticsView v, int cx, int cy, int cw, int ch) {
        PoliticsView.NationView n = v.nation();
        int by = card(g, cx, cy, cw, ch, n == null ? 0x666666 : n.color(), Draw.Icon.NATION, n == null ? tr("no_nation") : Component.literal(n.name()));
        if (n == null) {
            g.drawWordWrap(font, tr(v.town() == null ? "no_nation_town" : "no_nation_hint"), cx + 6, by, cw - 12, Draw.MUTED);
            return;
        }
        int sw = cw - 12;
        by = stat(g, tr("government"), government(n.id(), n.government()), cx + 6, by, sw, Draw.TEXT);
        by = stat(g, Component.translatable("nationsandwar.info.capital"), Component.literal(townName(v, n.capital())), cx + 6, by, sw, Draw.TEXT);
        by = stat(g, Component.translatable("nationsandwar.info.towns"), Component.literal(n.towns().size() + " · " + n.citizens().size()), cx + 6, by, sw, Draw.TEXT);
        stat(g, Component.translatable("nationsandwar.info.bank"), money(n.bank()), cx + 6, by, sw, Draw.GOOD);
        ui.zone(cx, cy, cw, ch, () -> screen.select("nation"));
    }

    private void allianceCard(GuiGraphics g, PoliticsView v, int cx, int cy, int cw, int ch) {
        PoliticsView.AllianceView a = v.alliance();
        int by = card(g, cx, cy, cw, ch, a == null ? 0x666666 : a.color(), Draw.Icon.DIPLOMACY, a == null ? tr("no_alliance") : Component.literal(a.name()));
        if (a == null) {
            g.drawWordWrap(font, tr("no_alliance_hint"), cx + 6, by, cw - 12, Draw.MUTED);
            ui.zone(cx, cy, cw, ch, () -> screen.select("diplomacy"));
            return;
        }
        for (UUID m : a.members()) {
            if (by > cy + ch - 10) break;
            g.fill(cx + 6, by + 2, cx + 10, by + 6, Draw.opaque(nationColor(v, m)));
            Component name = Component.literal(nationName(v, m) + (m.equals(a.leader()) ? " ★" : ""));
            g.drawString(font, Draw.fit(font, name, cw - 20), cx + 13, by, Draw.TEXT, false);
            by += 10;
        }
        ui.zone(cx, cy, cw, ch, () -> screen.select("diplomacy"));
    }

    private void invites(GuiGraphics g, PoliticsView v, int ix, int iy, int iw) {
        heading(g, tr("invites"), ix, iy, iw);
        int ry = iy + 14;
        boolean mayor = v.town() != null && leads(v.town().roles(), v.me());
        int dip = v.nation() == null ? 0 : v.nation().myPerms();
        for (UUID t : v.townInvites()) ry = invite(g, ix, ry, iw, tr("invite_town", townName(v, t)), v.town() == null ? null : tr("leave_first"),
                () -> act("town.join", "town", t));
        for (UUID n : v.nationInvites()) ry = invite(g, ix, ry, iw, tr("invite_nation", nationName(v, n)), mayor ? null : tr("mayor_only"),
                () -> act("nation.join", "nation", n));
        for (UUID a : v.allianceInvites()) {
            String name = "?";
            for (PoliticsView.AllianceView al : v.alliances()) if (al.id().equals(a)) name = al.name();
            ry = invite(g, ix, ry, iw, tr("invite_alliance", name), need(dip, org.papiricoh.nationsandwar.model.Perm.DIPLOMACY),
                    () -> act("alliance.join", "alliance", a));
        }
        if (ry == iy + 14) g.drawString(font, tr("no_invites"), ix + 2, ry + 2, Draw.MUTED, false);
    }

    private int invite(GuiGraphics g, int ix, int ry, int iw, Component text, Component denied, Runnable accept) {
        g.fill(ix, ry, ix + iw, ry + 17, 0x30FFFFFF);
        g.fill(ix, ry, ix + 2, ry + 17, Draw.INFO);
        g.drawString(font, Draw.fit(font, text, iw - 58), ix + 6, ry + 5, Draw.TEXT, false);
        ui.button(g, ix + iw - 48, ry + 2, 46, 13, tr("accept"), Ui.Style.GOOD, denied, accept);
        return ry + 19;
    }

    private void wars(GuiGraphics g, PoliticsView v, int fx, int fy, int fw) {
        heading(g, tr("wars_title"), fx, fy, fw);
        int ry = fy + 14;
        if (v.town() == null || v.nation() == null) {
            g.drawWordWrap(font, tr(v.town() == null ? "no_town_bar" : "no_nation_bar"), fx + 2, ry + 2, fw - 4, Draw.MUTED);
            return;
        }
        for (PoliticsView.WarView war : v.wars()) {
            if (ry > y + h - BAR - 14) break;
            boolean att = war.attackers().contains(v.nation().id());
            UUID foe = att ? war.defender() : war.attacker();
            int color = nationColor(v, foe);
            ui.row(g, fx, ry, fw, 14, false, () -> screen.select("war"));
            g.fill(fx + 3, ry + 4, fx + 8, ry + 9, Draw.opaque(color));
            boolean managed = org.papiricoh.nationsandwar.client.ClientClaims.managed(foe);
            g.drawString(font, Draw.fit(font, tr("versus", nationName(v, foe)), fw - (managed ? 132 : 80)), fx + 11, ry + 3, Draw.TEXT, false);
            if (managed) managedBadge(g, fx + fw - 126, ry + 2);
            ui.chip(g, fx + fw - 74, ry + 2, Component.translatable(K + "phase." + war.phase().toLowerCase()), WarTab.phaseColor(war.phase()));
            ry += 16;
        }
        if (v.wars().isEmpty()) g.drawString(font, tr("at_peace"), fx + 2, ry + 2, Draw.GOOD, false);
    }
}
