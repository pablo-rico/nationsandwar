package org.papiricoh.nationsandwar.client.screen.tab;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.papiricoh.nationsandwar.client.gui.Draw;
import org.papiricoh.nationsandwar.client.gui.Ui;
import org.papiricoh.nationsandwar.client.screen.NationScreen;
import org.papiricoh.nationsandwar.model.GovernmentType;
import org.papiricoh.nationsandwar.model.Perm;
import org.papiricoh.nationsandwar.view.PoliticsView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Every nation with its relation to mine and, for NPC nations, their opinion of us; alliance invite / kick and the
 * declare-war button per row (left). My alliance, or the form to create one and the invitations to join one (right).
 */
public final class DiplomacyTab extends Tab {
    private EditBox allianceName;
    private final Ui.Scroll list = new Ui.Scroll();
    private UUID armed;

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

    @Override
    public void init() {
        allianceName = field("alliance", 24, tr("alliance_name"), false);
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

    @Override
    public void render(GuiGraphics g, PoliticsView v, float partial) {
        int rw = Math.min(140, w / 3), lw = w - rw - 10;
        nations(g, v, x + 2, y + 2, lw);
        alliance(g, v, x + lw + 8, y + 2, rw);
    }

    private void nations(GuiGraphics g, PoliticsView v, int lx, int ly, int lw) {
        PoliticsView.NationView mine = v.nation();
        int perms = mine == null ? 0 : mine.myPerms();
        heading(g, Component.translatable("nationsandwar.info.nation_list").append(" " + v.nations().size()), lx, ly, lw);
        int top = ly + 13, lh = y + h - top - 2;
        Draw.inset(g, lx, top, lw, lh);
        List<PoliticsView.NationSummary> all = new ArrayList<>(v.nations());
        all.sort(Comparator.comparing((PoliticsView.NationSummary n) -> !"self".equals(n.relation())).thenComparing(n -> !"ally".equals(n.relation()))
                .thenComparing(n -> !"enemy".equals(n.relation())).thenComparing(PoliticsView.NationSummary::name));
        list.content = all.size() * 26 + 2;
        ui.scrollZone(lx, top, lw, lh, list);
        ui.clip(g, lx + 1, top + 1, lw - 2, lh - 2);
        int ry = top + 2 - list.offset;
        PoliticsView.AllianceView myAlliance = v.alliance();
        for (PoliticsView.NationSummary n : all) {
            boolean self = "self".equals(n.relation());
            g.fill(lx + 2, ry, lx + lw - 2, ry + 24, self ? 0x30E3B556 : 0x18FFFFFF);
            g.fillGradient(lx + 2, ry, lx + 6, ry + 24, Draw.opaque(Draw.mix(n.color(), 0xFFFFFF, 0.2f)), Draw.opaque(Draw.shade(n.color(), 0.7f)));
            Component name = Component.literal(n.name() + (n.npc() ? " ⚙" : ""));
            g.drawString(font, Draw.fit(font, name, lw - 150), lx + 10, ry + 3, Draw.TEXT, false);
            Component sub = Component.translatable(GovernmentType.byId(n.government()).key()).append(" · ")
                    .append(tr("towns_citizens", n.towns(), n.citizens())).append(" · " + n.leader());
            Draw.text(g, font, Draw.fit(font, sub, (int) ((lw - 150) / 0.7f)), lx + 10, ry + 14, 0.7f, Draw.MUTED, false);
            int cx = lx + lw - 140;
            ui.chip(g, cx, ry + 2, tr("relation." + n.relation()), relationColor(n.relation()));
            if (n.npc() && !self) {
                opinion(g, cx, ry + 16, 40, n.opinion());
                ui.tip(cx, ry + 14, 40, 9, tr("opinion", n.opinion()));
            }
            if (mine != null && !self) {
                int bx = lx + lw - 92;
                boolean member = myAlliance != null && myAlliance.members().contains(n.id());
                if (member) {
                    ui.button(g, bx, ry + 6, 44, 13, tr("kick"), Ui.Style.BAD, need(perms, Perm.DIPLOMACY), () -> act("alliance.kick", "nation", n.id()));
                } else {
                    Component why = myAlliance == null ? tr("no_alliance") : "enemy".equals(n.relation()) ? tr("at_war") : need(perms, Perm.DIPLOMACY);
                    boolean invited = myAlliance != null && myAlliance.invites().contains(n.id());
                    ui.button(g, bx, ry + 6, 44, 13, tr(invited ? "invited" : "ally_invite"), Ui.Style.GOOD, invited ? tr("invited") : why,
                            () -> act("alliance.invite", "nation", n.id()));
                }
                Component warWhy = "enemy".equals(n.relation()) ? tr("at_war") : "ally".equals(n.relation()) ? tr("is_ally") : need(perms, Perm.DECLARE_WAR);
                boolean sure = n.id().equals(armed);
                ui.button(g, bx + 46, ry + 6, 42, 13, sure ? tr("sure") : tr("war"), Ui.Style.BAD, warWhy, () -> {
                    if (sure) act("war.declare", "nation", n.id());
                    armed = sure ? null : n.id();
                });
                if (warWhy == null) ui.tip(bx + 46, ry + 6, 42, 13, tr("declare_war_cost", v.prices().war()));
            }
            ry += 26;
        }
        ui.unclip(g);
        ui.scrollbar(g, lx + lw - 3, top + 1, lh - 2, list);
    }

    private void alliance(GuiGraphics g, PoliticsView v, int ax, int ay, int aw) {
        PoliticsView.NationView mine = v.nation();
        int perms = mine == null ? 0 : mine.myPerms();
        PoliticsView.AllianceView a = v.alliance();
        if (a != null) {
            int by = card(g, ax, ay + 4, aw - 3, 110, a.color(), Draw.Icon.DIPLOMACY, Component.literal(a.name()));
            for (UUID m : a.members()) {
                if (by > ay + 100) break;
                g.fill(ax + 6, by + 2, ax + 10, by + 6, Draw.opaque(nationColor(v, m)));
                g.drawString(font, Draw.fit(font, Component.literal(nationName(v, m) + (m.equals(a.leader()) ? " ★" : "")), aw - 22), ax + 13, by, Draw.TEXT, false);
                by += 10;
            }
            ui.button(g, ax, ay + 122, aw - 3, 13, tr("leave_alliance"), Ui.Style.BAD, need(perms, Perm.DIPLOMACY), () -> act("alliance.leave"));
            return;
        }
        heading(g, tr("create_alliance"), ax, ay, aw);
        g.drawWordWrap(font, tr("create_alliance_hint", v.prices().alliance()), ax, ay + 13, aw, Draw.MUTED);
        place(allianceName, ax, ay + 50, aw);
        Component why = mine == null ? tr("no_nation") : need(perms, Perm.DIPLOMACY);
        if (why == null && allianceName.getValue().isBlank()) why = tr("type_name");
        ui.button(g, ax, ay + 67, aw, 13, tr("create"), Ui.Style.GOLD, why, () -> {
            act("alliance.create", "name", allianceName.getValue().trim());
            screen.clearField(allianceName);
        });
        int iy = ay + 90;
        heading(g, tr("invites"), ax, iy, aw);
        iy += 13;
        for (UUID id : v.allianceInvites()) {
            for (PoliticsView.AllianceView al : v.alliances()) {
                if (!al.id().equals(id)) continue;
                g.fill(ax, iy, ax + 3, iy + 13, Draw.opaque(al.color()));
                g.drawString(font, Draw.fit(font, Component.literal(al.name()), aw - 52), ax + 6, iy + 3, Draw.TEXT, false);
                ui.button(g, ax + aw - 44, iy, 44, 13, tr("accept"), Ui.Style.GOOD, need(perms, Perm.DIPLOMACY), () -> act("alliance.join", "alliance", id));
                iy += 15;
            }
        }
        if (v.allianceInvites().isEmpty()) g.drawString(font, tr("no_invites"), ax + 2, iy, Draw.MUTED, false);
    }
}
