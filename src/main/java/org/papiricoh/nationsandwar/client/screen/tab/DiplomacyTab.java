package org.papiricoh.nationsandwar.client.screen.tab;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.papiricoh.nationsandwar.client.ClientClaims;
import org.papiricoh.nationsandwar.client.gui.Draw;
import org.papiricoh.nationsandwar.client.gui.Ui;
import org.papiricoh.nationsandwar.client.screen.NationScreen;
import org.papiricoh.nationsandwar.client.screen.modal.ConfirmModal;
import org.papiricoh.nationsandwar.client.screen.modal.PickModal;
import org.papiricoh.nationsandwar.client.screen.modal.TextModal;
import org.papiricoh.nationsandwar.model.Perm;
import org.papiricoh.nationsandwar.view.Briefs;
import org.papiricoh.nationsandwar.view.PoliticsView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Every nation with its relation to mine and, for NPC nations, their opinion of us (left). On the right, the picked
 * nation's card with what can be done with it — invite it to my alliance, expel it, declare war (after showing the cost),
 * see its ranks in the Atlas — or, with nothing picked, my alliance and the invitations waiting for us. A nation run by
 * another mod shows its {@link Tab#managedCard} and offers nothing. The action bar creates an alliance, invites a nation
 * and leaves the alliance (after asking).
 */
public final class DiplomacyTab extends Tab {
    private final Ui.Scroll list = new Ui.Scroll();
    private UUID picked;
    /** A managed nation's provider key to pick once its row shows up (the preview harness). */
    private String pickKey;

    public DiplomacyTab(NationScreen screen) {
        super(screen);
    }

    @Override
    public String id() {
        return "diplomacy";
    }

    @Override
    public Draw.Icon icon() {
        return Draw.Icon.DIPLOMACY;
    }

    public static int relationColor(String relation) {
        return switch (relation) {
            case "ally" -> Draw.GOOD;
            case "enemy" -> Draw.BAD;
            case "self" -> Draw.GOLD;
            default -> Draw.MUTED;
        };
    }

    /** An opinion meter, −100..100 around a centre line. */
    public static void opinion(GuiGraphics g, int ox, int oy, int ow, int value) {
        g.fill(ox, oy, ox + ow, oy + 5, 0xFF0A0C10);
        int mid = ox + ow / 2, len = Math.round((ow / 2f - 1) * Math.abs(value) / 100f);
        int c = value >= 40 ? Draw.GOOD : value >= 0 ? 0xFFB8C46A : value > -50 ? Draw.WARN : Draw.BAD;
        if (value >= 0) g.fill(mid, oy + 1, mid + len, oy + 4, c);
        else g.fill(mid - len, oy + 1, mid, oy + 4, c);
        g.fill(mid, oy - 1, mid + 1, oy + 6, 0xFF8C95A7);
    }

    /** Picks the first nation run by {@code key} as soon as it is known. */
    public void pickManaged(String key) {
        pickKey = key;
    }

    /** Picks a nation's row. */
    public void pick(UUID nation) {
        picked = nation;
    }

    @Override
    public void render(GuiGraphics g, PoliticsView v, float partial) {
        if (pickKey != null)
            for (Briefs.NationBrief b : ClientClaims.nations())
                if (pickKey.equals(b.managedBy())) {
                    picked = b.id();
                    pickKey = null;
                    break;
                }
        PoliticsView.NationSummary sel = picked == null ? null : v.nationSummary(picked);
        if (sel == null) picked = null;
        int rw = Math.min(170, w * 2 / 5), lw = w - rw - 8, ch = h - BAR - 4;
        nations(g, v, x + 2, y + 2, lw, ch);
        int rx = x + lw + 8;
        Briefs.NationBrief card = picked == null ? null : ClientClaims.nation(picked);
        if (card != null && card.managed()) {
            managedCard(g, card, rx, y + 5, rw - 3, Math.min(ch - 4, 200));
            ui.button(g, rx + rw - 18, y + 8, 12, 11, Component.literal("✕"), Ui.Style.NORMAL, null, () -> picked = null);
        } else if (sel != null) inspect(g, v, sel, rx, y + 2, rw, ch);
        else alliance(g, v, rx, y + 2, rw, ch);
        actionBar(g, acts(v));
    }

    private List<Act> acts(PoliticsView v) {
        PoliticsView.NationView mine = v.nation();
        int perms = mine == null ? 0 : mine.myPerms();
        PoliticsView.AllianceView a = v.alliance();
        List<Act> acts = new ArrayList<>();
        Component create = mine == null ? tr("no_nation") : a != null ? tr("in_alliance") : need(perms, Perm.DIPLOMACY);
        acts.add(Act.modal(tr("create_alliance"), Ui.Style.GOLD, create, () -> new TextModal(tr("create_alliance"), Draw.Icon.DIPLOMACY,
                tr("create_alliance_hint", v.prices().alliance()), "alliance.new", tr("alliance_name"), tr("create_cost", v.prices().alliance()), Ui.Style.GOLD,
                view -> view.nation() == null ? tr("no_nation") : view.alliance() != null ? tr("in_alliance") : need(view.nation().myPerms(), Perm.DIPLOMACY),
                name -> act("alliance.create", "name", name))));
        Component invite = a == null ? tr("no_alliance") : need(perms, Perm.DIPLOMACY);
        acts.add(Act.modal(tr("invite_nation"), invite, () -> new PickModal(tr("invite_nation"), Draw.Icon.DIPLOMACY, tr("invite_nation_hint"), view -> {
            List<PickModal.Row> rows = new ArrayList<>();
            PoliticsView.AllianceView al = view.alliance();
            if (al == null) return rows;
            for (PoliticsView.NationSummary n : view.nations()) {
                if (al.members().contains(n.id()) || ClientClaims.managed(n.id())) continue;
                Component why = al.invites().contains(n.id()) ? tr("invited") : "enemy".equals(n.relation()) ? tr("at_war") : null;
                rows.add(new PickModal.Row(n.id(), Component.literal(n.name()), government(n.id(), n.government()), n.color(), null, null, false, why));
            }
            return rows;
        }, tr("invite"), Ui.Style.GOOD, view -> view.alliance() == null ? tr("no_alliance") : view.nation() == null ? tr("no_nation")
                : need(view.nation().myPerms(), Perm.DIPLOMACY), (id, name) -> act("alliance.invite", "nation", id))));
        if (a != null) acts.add(Act.modal(tr("leave_alliance"), Ui.Style.BAD, need(perms, Perm.DIPLOMACY), () -> new ConfirmModal(tr("leave_alliance"),
                tr("confirm_leave_alliance", a.name()), tr("leave"), () -> act("alliance.leave"))));
        return acts;
    }

    private void nations(GuiGraphics g, PoliticsView v, int lx, int ly, int lw, int ch) {
        heading(g, Component.translatable("nationsandwar.info.nation_list").append(" " + v.nations().size()), lx, ly, lw);
        int top = ly + 13, lh = ly + ch - top;
        Draw.inset(g, lx, top, lw, lh);
        List<PoliticsView.NationSummary> all = new ArrayList<>(v.nations());
        all.sort(Comparator.comparing((PoliticsView.NationSummary n) -> !"self".equals(n.relation())).thenComparing(n -> !"ally".equals(n.relation()))
                .thenComparing(n -> !"enemy".equals(n.relation())).thenComparing(PoliticsView.NationSummary::name));
        list.content = all.size() * 27 + 2;
        ui.scrollZone(lx, top, lw, lh, list);
        ui.clip(g, lx + 1, top + 1, lw - 2, lh - 2);
        int ry = top + 2 - list.offset;
        for (PoliticsView.NationSummary n : all) {
            boolean self = "self".equals(n.relation()), managed = ClientClaims.managed(n.id());
            UUID id = n.id();
            colorRow(g, lx + 2, ry, lw - 4, 25, n.color(), id.equals(picked), () -> picked = id.equals(picked) ? null : id);
            if (self) g.fill(lx + 5, ry, lx + lw - 2, ry + 25, 0x18E3B556);
            Component name = Component.literal(n.name() + (n.npc() && !managed ? " ⚙" : ""));
            int textW = lw - 100;
            g.drawString(font, Draw.fit(font, name, textW), lx + 9, ry + 3, self ? Draw.GOLD : Draw.TEXT, false);
            Component sub = managed ? government(n.id(), n.government()).copy().append(" · ").append(tr("n_towns", n.towns()))
                    : government(n.id(), n.government()).copy().append(" · ").append(tr("towns_citizens", n.towns(), n.citizens()));
            Draw.text(g, font, Draw.fit(font, sub, (int) (textW / 0.7f)), lx + 9, ry + 15, 0.7f, Draw.MUTED, false);
            int cx = lx + lw - 86;
            Component rel = tr("relation." + n.relation());
            ui.chip(g, lx + lw - 8 - font.width(rel) - 6, ry + 3, rel, relationColor(n.relation()));
            if (managed) managedBadge(g, cx, ry + 14);
            else if (n.npc() && !self) {
                opinion(g, lx + lw - 50, ry + 17, 42, n.opinion());
                ui.tip(lx + lw - 50, ry + 15, 42, 9, tr("opinion", n.opinion()));
            }
            if (managed) ui.tip(lx + 2, ry, lw - 4, 25, Component.translatable("nationsandwar.managed.inspect"));
            ry += 27;
        }
        ui.unclip(g);
        ui.scrollbar(g, lx + lw - 3, top + 1, lh - 2, list);
    }

    /** The picked nation: its facts and what my nation can do about it. */
    private void inspect(GuiGraphics g, PoliticsView v, PoliticsView.NationSummary n, int ax, int ay, int aw, int ch) {
        PoliticsView.NationView mine = v.nation();
        int perms = mine == null ? 0 : mine.myPerms();
        boolean self = "self".equals(n.relation());
        int by = card(g, ax, ay + 3, aw - 3, ch - 3, n.color(), Draw.Icon.NATION, Component.literal(n.name()));
        ui.button(g, ax + aw - 19, ay + 7, 12, 11, Component.literal("✕"), Ui.Style.NORMAL, null, () -> picked = null);
        int sx = ax + 6, sw = aw - 15;
        ui.chip(g, sx, by - 2, tr("relation." + n.relation()), relationColor(n.relation()));
        by += 12;
        by = stat(g, tr("government"), government(n.id(), n.government()), sx, by, sw, Draw.TEXT);
        Component leaderTitle = Component.translatable(ClientClaims.titles(n.id(), n.government()) + ".leader");
        by = stat(g, leaderTitle, Component.literal(n.leader().isEmpty() ? "—" : n.leader()), sx, by, sw, Draw.TEXT);
        by = stat(g, Component.translatable("nationsandwar.info.towns"), Component.literal(String.valueOf(n.towns())), sx, by, sw, Draw.TEXT);
        by = stat(g, Component.translatable("nationsandwar.info.citizens"), Component.literal(String.valueOf(n.citizens())), sx, by, sw, Draw.TEXT);
        if (n.alliance() != null) {
            Briefs.AllianceBrief ab = ClientClaims.alliance(n.alliance());
            if (ab != null) by = stat(g, tr("alliance"), Component.literal(ab.name()), sx, by, sw, Draw.TEXT);
        }
        if (n.npc() && !self) {
            g.drawString(font, tr("their_opinion"), sx, by, Draw.MUTED, false);
            by += 10;
            opinion(g, sx, by + 1, sw, n.opinion());
            ui.tip(sx, by - 1, sw, 8, tr("opinion", n.opinion()));
            by += 10;
        }
        int bw = sw, bh = 14, bottom = ay + ch - 8;
        int bty = bottom - (self ? 1 : 4) * (bh + 3);
        ui.button(g, sx, bty, bw, bh, tr("see_ranks"), Ui.Style.NORMAL, null, () -> {
            screen.atlas().focus("nation", n.id());
            screen.select("atlas");
        });
        if (self || mine == null) return;
        bty += bh + 3;
        PoliticsView.AllianceView my = v.alliance();
        boolean member = my != null && my.members().contains(n.id());
        if (member) {
            ui.button(g, sx, bty, bw, bh, tr("expel_alliance"), Ui.Style.BAD, need(perms, Perm.DIPLOMACY), confirm(tr("expel_alliance"),
                    tr("confirm_expel_alliance", n.name(), my.name()), tr("kick"), () -> act("alliance.kick", "nation", n.id())));
        } else {
            boolean invited = my != null && my.invites().contains(n.id());
            Component why = my == null ? tr("no_alliance") : invited ? tr("invited") : "enemy".equals(n.relation()) ? tr("at_war") : need(perms, Perm.DIPLOMACY);
            ui.button(g, sx, bty, bw, bh, tr(invited ? "invited" : "ally_invite_long"), Ui.Style.GOOD, why, () -> act("alliance.invite", "nation", n.id()));
        }
        bty += bh + 3;
        Component warWhy = "enemy".equals(n.relation()) ? tr("at_war") : "ally".equals(n.relation()) ? tr("is_ally") : need(perms, Perm.DECLARE_WAR);
        ui.button(g, sx, bty, bw, bh, tr("declare_war"), Ui.Style.BAD, warWhy, confirm(tr("declare_war"),
                tr("confirm_declare_war", n.name(), v.prices().war()), tr("declare_war_cost_short", v.prices().war()), () -> act("war.declare", "nation", n.id())));
        bty += bh + 3;
        if (warWhy == null) Draw.text(g, font, Draw.fit(font, tr("declare_war_cost", v.prices().war()), (int) (bw / 0.65f)), sx, bty, 0.65f, 0xFF6A7384, false);
    }

    /** My alliance and the invitations waiting for us, while no nation is picked. */
    private void alliance(GuiGraphics g, PoliticsView v, int ax, int ay, int aw, int ch) {
        PoliticsView.NationView mine = v.nation();
        int perms = mine == null ? 0 : mine.myPerms();
        PoliticsView.AllianceView a = v.alliance();
        int cardH = a == null ? 46 : Math.min(ch / 2 + 10, 34 + a.members().size() * 11);
        int by = card(g, ax, ay + 3, aw - 3, cardH, a == null ? 0x666666 : a.color(), Draw.Icon.DIPLOMACY, a == null ? tr("no_alliance") : Component.literal(a.name()));
        if (a == null) g.drawWordWrap(font, tr("no_alliance_bar"), ax + 6, by - 2, aw - 15, Draw.MUTED);
        else for (UUID m : a.members()) {
            if (by > ay + cardH - 8) break;
            g.fill(ax + 6, by + 1, ax + 11, by + 6, Draw.opaque(nationColor(v, m)));
            g.drawString(font, Draw.fit(font, Component.literal(nationName(v, m) + (m.equals(a.leader()) ? " ★" : "")), aw - 26), ax + 14, by, Draw.TEXT, false);
            by += 11;
        }
        int iy = ay + cardH + 14;
        heading(g, tr("invites"), ax, iy, aw);
        iy += 13;
        for (UUID id : v.allianceInvites()) {
            for (PoliticsView.AllianceView al : v.alliances()) {
                if (!al.id().equals(id)) continue;
                g.fill(ax, iy, ax + aw, iy + 15, 0x18FFFFFF);
                g.fill(ax, iy, ax + 3, iy + 15, Draw.opaque(al.color()));
                g.drawString(font, Draw.fit(font, Component.literal(al.name()), aw - 56), ax + 6, iy + 4, Draw.TEXT, false);
                ui.button(g, ax + aw - 46, iy + 1, 44, 13, tr("accept"), Ui.Style.GOOD, need(perms, Perm.DIPLOMACY), () -> act("alliance.join", "alliance", id));
                iy += 17;
            }
        }
        if (v.allianceInvites().isEmpty()) g.drawString(font, tr("no_invites"), ax + 2, iy, Draw.MUTED, false);
        Draw.text(g, font, Draw.fit(font, tr("pick_nation_hint"), (int) (aw / 0.7f)), ax, ay + ch - 8, 0.7f, 0xFF6A7384, false);
    }
}
