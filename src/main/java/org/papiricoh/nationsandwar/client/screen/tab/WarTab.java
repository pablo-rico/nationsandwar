package org.papiricoh.nationsandwar.client.screen.tab;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.papiricoh.nationsandwar.client.ClientClaims;
import org.papiricoh.nationsandwar.client.gui.Draw;
import org.papiricoh.nationsandwar.client.gui.Ui;
import org.papiricoh.nationsandwar.client.screen.NationScreen;
import org.papiricoh.nationsandwar.client.screen.modal.ConfirmModal;
import org.papiricoh.nationsandwar.client.screen.modal.TreatyModal;
import org.papiricoh.nationsandwar.model.Perm;
import org.papiricoh.nationsandwar.view.PoliticsView;
import org.papiricoh.nationsandwar.view.SiegeBrief;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The wars my nation is in: the two sides, each side's score against the target, the phase with its countdown, the
 * sieges with their control bars and the fallen towns. The action bar offers or accepts white peace and surrenders (both
 * after asking); during the treaty window it opens the winner's terms editor and signs at once.
 */
public final class WarTab extends Tab {
    private UUID selected;

    public WarTab(NationScreen screen) {
        super(screen);
    }

    @Override
    public String id() {
        return "war";
    }

    @Override
    public Draw.Icon icon() {
        return Draw.Icon.WAR;
    }

    @Override
    public Component unavailable(PoliticsView v) {
        return v.wars().isEmpty() ? tr("at_peace") : null;
    }

    public static int phaseColor(String phase) {
        return switch (phase) {
            case "PREPARATION" -> Draw.WARN;
            case "ACTIVE" -> Draw.BAD;
            case "TREATY" -> Draw.GOLD;
            default -> Draw.MUTED;
        };
    }

    /** Game ticks as "h:mm:ss" / "m:ss". */
    public static String clock(long ticks) {
        long s = Math.max(0, ticks / 20), hh = s / 3600, mm = s / 60 % 60, ss = s % 60;
        return hh > 0 ? String.format("%d:%02d:%02d", hh, mm, ss) : String.format("%d:%02d", mm, ss);
    }

    @Override
    public void render(GuiGraphics g, PoliticsView v, float partial) {
        PoliticsView.WarView war = null;
        for (PoliticsView.WarView wv : v.wars()) if (wv.id().equals(selected)) war = wv;
        if (war == null) {
            war = v.wars().getFirst();
            selected = war.id();
        }
        int ty = y + 2;
        if (v.wars().size() > 1) {
            int cx = x + 2;
            for (PoliticsView.WarView wv : v.wars()) {
                Component label = Component.literal(nationName(v, wv.attacker()) + " ⚔ " + nationName(v, wv.defender()));
                int bw = font.width(label) + 10;
                PoliticsView.WarView pick = wv;
                ui.button(g, cx, ty, bw, 12, label, wv == war ? Ui.Style.GOLD : Ui.Style.NORMAL, null, () -> selected = pick.id());
                cx += bw + 3;
            }
            ty += 16;
        }
        ty = banner(g, v, war, ty);
        ty = scores(g, v, war, ty + 4);
        PoliticsView.NationView mine = v.nation();
        UUID winner = "ATTACKERS".equals(war.outcome()) ? war.attacker() : "DEFENDERS".equals(war.outcome()) ? war.defender() : null;
        int bottom = y + h - BAR - 2;
        if ("TREATY".equals(war.phase())) treaty(g, v, war, winner, ty + 4, bottom);
        else details(g, v, war, ty + 4, bottom - 4);
        actionBar(g, acts(v, war, mine, winner));
    }

    private int banner(GuiGraphics g, PoliticsView v, PoliticsView.WarView war, int by) {
        int bh = 34, ac = nationColor(v, war.attacker()), dc = nationColor(v, war.defender());
        Draw.shadow(g, x + 2, by + 3, w - 6, bh, 6);
        Draw.slab(g, x + 2, by + 3, w - 7, bh, 3, 0xFF2A2F3A, 0xFF1A1E26);
        g.fillGradient(x + 2, by + 3, x + 2 + (w - 7) / 3, by + 3 + bh, Draw.alpha(ac, 0x70), Draw.alpha(ac, 0x20));
        g.fillGradient(x + 2 + (w - 7) * 2 / 3, by + 3, x + w - 5, by + 3 + bh, Draw.alpha(dc, 0x70), Draw.alpha(dc, 0x20));
        g.drawString(font, Draw.fit(font, Component.literal(nationName(v, war.attacker())), w / 3 - 12), x + 8, by + 8, Draw.TEXT, true);
        Draw.text(g, font, tr("attackers"), x + 8, by + 20, 0.7f, Draw.MUTED, false);
        Component dn = Draw.fit(font, Component.literal(nationName(v, war.defender())), w / 3 - 12);
        g.drawString(font, dn, x + w - 10 - font.width(dn), by + 8, Draw.TEXT, true);
        Component dl = tr("defenders");
        Draw.text(g, font, dl, x + w - 10 - font.width(dl) * 0.7f, by + 20, 0.7f, Draw.MUTED, false);
        Draw.icon(g, Draw.Icon.WAR, x + w / 2 - 9, by + 4);
        Component phase = Component.translatable(K + "phase." + war.phase().toLowerCase());
        long until = switch (war.phase()) {
            case "PREPARATION" -> war.activeAt();
            case "ACTIVE" -> war.deadline();
            case "TREATY" -> war.treatyUntil();
            default -> v.now();
        };
        Component left = Component.literal(clock(until - v.now()));
        int pw = font.width(phase) + 6 + font.width(left) + 4;
        int px = x + w / 2 - pw / 2;
        ui.chip(g, px, by + 22, phase, phaseColor(war.phase()));
        g.drawString(font, left, px + font.width(phase) + 10, by + 23, Draw.TEXT, false);
        return by + bh + 4;
    }

    /** Each side's score as a bar growing toward the centre, where the target sits. */
    private int scores(GuiGraphics g, PoliticsView v, PoliticsView.WarView war, int sy) {
        int target = Math.max(1, war.target());
        Component t = tr("target", target);
        int gap = font.width(t) + 12, bw = (w - 4 - gap) / 2;
        scoreBar(g, x + 2, sy, bw, war.scoreAttackers(), target, nationColor(v, war.attacker()), false);
        scoreBar(g, x + w - 2 - bw, sy, bw, war.scoreDefenders(), target, nationColor(v, war.defender()), true);
        g.drawCenteredString(font, t, x + w / 2, sy + 1, Draw.MUTED);
        return sy + 12;
    }

    private void scoreBar(GuiGraphics g, int bx, int by, int bw, int score, int target, int color, boolean fromRight) {
        g.fill(bx, by, bx + bw, by + 10, 0xFF0A0C10);
        int fw = Math.round((bw - 2) * Math.min(1, score / (float) target)), c = Draw.opaque(color);
        int fx = fromRight ? bx + 1 : bx + bw - 1 - fw;
        if (fw > 0) g.fillGradient(fx, by + 1, fx + fw, by + 9, Draw.mix(c, 0xFFFFFFFF, 0.25f), Draw.shade(c, 0.75f));
        Component s = Component.literal(String.valueOf(score));
        g.drawString(font, s, fromRight ? bx + bw - 3 - font.width(s) : bx + 3, by + 1, Draw.TEXT, true);
        ui.tip(bx, by, bw, 10, tr("score_of", score, target));
    }

    private void details(GuiGraphics g, PoliticsView v, PoliticsView.WarView war, int dy, int bottom) {
        int cw = (w - 16) / 3;
        int cx = x + 2;
        heading(g, tr("sides"), cx, dy, cw);
        int ry = dy + 13;
        for (UUID a : war.attackers()) ry = side(g, v, a, cx, ry, cw, war.attacker().equals(a));
        ry += 3;
        g.fill(cx, ry, cx + cw, ry + 1, 0x30FFFFFF);
        ry += 4;
        for (UUID d : war.defenders()) ry = side(g, v, d, cx, ry, cw, war.defender().equals(d));

        cx += cw + 6;
        heading(g, tr("sieges"), cx, dy, cw);
        ry = dy + 13;
        for (SiegeBrief s : war.sieges()) {
            if (ry > bottom - 20) break;
            g.drawString(font, Draw.fit(font, Component.literal(townName(v, s.town())), cw - 30), cx, ry, Draw.TEXT, false);
            Component pct = Component.literal(Math.round((s.control() + 1) * 50) + "%");
            g.drawString(font, pct, cx + cw - font.width(pct), ry, Draw.MUTED, false);
            control(g, cx, ry + 10, cw, s);
            ui.tip(cx, ry, cw, 18, tr("siege_tip", s.besieger(), s.defender()));
            ry += 21;
        }
        if (war.sieges().isEmpty()) g.drawString(font, tr("no_sieges"), cx, ry, Draw.MUTED, false);

        cx += cw + 6;
        heading(g, tr("fallen"), cx, dy, w - 2 * cw - 14);
        ry = dy + 13;
        for (UUID t : war.coreOpen()) {
            g.drawString(font, Draw.fit(font, Component.literal("⚠ " + townName(v, t)), cw), cx, ry, Draw.WARN, false);
            ui.tip(cx, ry, cw, 10, tr("core_open"));
            ry += 11;
        }
        for (UUID t : war.fallen()) {
            g.drawString(font, Draw.fit(font, Component.literal("☠ " + townName(v, t)), cw), cx, ry, Draw.BAD, false);
            ry += 11;
        }
        if (war.coreOpen().isEmpty() && war.fallen().isEmpty()) g.drawString(font, tr("none"), cx, ry, Draw.MUTED, false);
    }

    /** One nation of a side; a managed one (no leader, no citizens) shows only its name, colour and badge. */
    private int side(GuiGraphics g, PoliticsView v, UUID n, int sx, int sy, int sw, boolean lead) {
        boolean managed = ClientClaims.managed(n);
        int bw = managed ? font.width(Component.translatable("nationsandwar.managed.badge")) + 8 : 0;
        g.fill(sx, sy + 1, sx + 5, sy + 7, Draw.opaque(nationColor(v, n)));
        g.drawString(font, Draw.fit(font, Component.literal(nationName(v, n) + (lead ? " ★" : "")), sw - 10 - bw), sx + 8, sy, lead ? Draw.TEXT : Draw.MUTED, false);
        if (managed) managedBadge(g, sx + sw - bw + 2, sy - 1);
        return sy + 11;
    }

    /** A siege's control: the besieger's colour grows from the right as control goes to +1. */
    private static void control(GuiGraphics g, int cx, int cy, int cw, SiegeBrief s) {
        g.fill(cx, cy, cx + cw, cy + 6, 0xFF0A0C10);
        int mid = cx + cw / 2, at = mid - Math.round((cw / 2f - 1) * s.control());
        g.fill(cx + 1, cy + 1, at, cy + 5, Draw.opaque(Draw.shade(s.defenderColor(), 0.9f)));
        g.fill(at, cy + 1, cx + cw - 1, cy + 5, Draw.opaque(s.besiegerColor()));
        g.fill(mid, cy - 1, mid + 1, cy + 7, 0xFFFFFFFF);
        g.fill(at - 1, cy - 1, at + 1, cy + 7, Draw.GOLD);
    }

    /** The treaty window: who won, the countdown and the terms set so far. */
    private void treaty(GuiGraphics g, PoliticsView v, PoliticsView.WarView war, UUID winner, int ty, int bottom) {
        Component outcome = winner == null ? tr("outcome.white_peace") : tr("outcome.won", nationName(v, winner));
        heading(g, tr("treaty").append(" — ").append(outcome), x + 2, ty, w - 4);
        int cw = (w - 10) / 2, cx = x + 2, ry = ty + 14;
        g.drawString(font, tr("annex"), cx, ry, Draw.MUTED, false);
        ry += 12;
        for (UUID t : war.fallen()) {
            if (ry > bottom - 10) break;
            boolean on = war.annex().contains(t);
            g.drawString(font, Component.literal((on ? "⚑ " : "· ") + townName(v, t)), cx + 4, ry, on ? Draw.GOLD : Draw.MUTED, false);
            ry += 11;
        }
        if (war.fallen().isEmpty()) g.drawString(font, tr("none"), cx + 4, ry, Draw.MUTED, false);
        int fx = cx + cw + 6, fy = ty + 14;
        fy = stat(g, tr("tribute"), money(war.tribute()), fx, fy, cw, Draw.GOLD);
        fy = stat(g, tr("vassal_days_max", v.prices().maxVassalDays()), Component.literal(String.valueOf(war.vassalDays())), fx, fy, cw, Draw.TEXT);
        fy = stat(g, tr("annexed_towns"), Component.literal(String.valueOf(war.annex().size())), fx, fy, cw, Draw.TEXT);
        g.drawWordWrap(font, tr("treaty_hint"), fx, fy + 6, cw, 0xFF6A7384);
    }

    private List<Act> acts(PoliticsView v, PoliticsView.WarView war, PoliticsView.NationView mine, UUID winner) {
        List<Act> acts = new ArrayList<>();
        if (mine == null) return acts;
        if ("TREATY".equals(war.phase())) {
            boolean editor = mine.id().equals(winner);
            Component denied = !editor ? tr("winner_only") : need(mine.myPerms(), Perm.SIGN_TREATY);
            acts.add(Act.modal(tr("edit_terms"), Ui.Style.GOLD, denied, () -> new TreatyModal(war)));
            acts.add(Act.modal(tr("sign_now"), Ui.Style.GOOD, denied, () -> new ConfirmModal(tr("sign_now"),
                    tr("confirm_sign", war.annex().size(), war.tribute(), war.vassalDays()), tr("sign_now"), () -> act("war.confirm"))));
            return acts;
        }
        if (!"PREPARATION".equals(war.phase()) && !"ACTIVE".equals(war.phase())) return acts;
        boolean attacker = war.attacker().equals(mine.id()), lead = attacker || war.defender().equals(mine.id());
        Component denied = !lead ? tr("war_leader_only") : need(mine.myPerms(), Perm.SIGN_TREATY);
        String mySide = attacker ? "attackers" : "defenders";
        boolean theyOffered = !war.peaceBy().isEmpty() && !war.peaceBy().equals(mySide), weOffered = war.peaceBy().equals(mySide);
        String foe = nationName(v, attacker ? war.defender() : war.attacker());
        Component peace = tr(theyOffered ? "accept_peace" : weOffered ? "peace_offered" : "offer_peace");
        acts.add(Act.modal(peace, Ui.Style.GOOD, weOffered ? tr("peace_offered") : denied, () -> new ConfirmModal(peace,
                tr(theyOffered ? "confirm_accept_peace" : "confirm_offer_peace", foe), peace, () -> act("war.peace", "war", war.id()))));
        acts.add(Act.modal(tr("surrender"), Ui.Style.BAD, denied, () -> new ConfirmModal(tr("surrender"),
                tr("confirm_surrender", foe), tr("surrender"), () -> act("war.surrender", "war", war.id()))));
        return acts;
    }
}
