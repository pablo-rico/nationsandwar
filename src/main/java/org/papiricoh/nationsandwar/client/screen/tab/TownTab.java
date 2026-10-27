package org.papiricoh.nationsandwar.client.screen.tab;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.papiricoh.nationsandwar.client.gui.Draw;
import org.papiricoh.nationsandwar.client.gui.Skins;
import org.papiricoh.nationsandwar.client.gui.Ui;
import org.papiricoh.nationsandwar.client.screen.NationScreen;
import org.papiricoh.nationsandwar.client.screen.modal.BankModal;
import org.papiricoh.nationsandwar.client.screen.modal.ConfirmModal;
import org.papiricoh.nationsandwar.client.screen.modal.NameColorModal;
import org.papiricoh.nationsandwar.client.screen.modal.SpawnModal;
import org.papiricoh.nationsandwar.client.screen.modal.TextModal;
import org.papiricoh.nationsandwar.client.screen.modal.TownSettingsModal;
import org.papiricoh.nationsandwar.model.ChunkPerms;
import org.papiricoh.nationsandwar.model.Flag;
import org.papiricoh.nationsandwar.model.Perm;
import org.papiricoh.nationsandwar.view.PoliticsView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * My town: its facts and bank (left), its settings at a glance — flags, open or closed, who may do what (middle) — and
 * the residents with their online state and rank, each with a kick button (right). The action bar opens the modals: name
 * and colour, bank, spawn, settings, invite; leaving or deleting the town asks first.
 */
public final class TownTab extends Tab {
    private final Ui.Scroll residents = new Ui.Scroll();

    public TownTab(NationScreen screen) {
        super(screen);
    }

    @Override
    public String id() {
        return "town";
    }

    @Override
    public Draw.Icon icon() {
        return Draw.Icon.TOWN;
    }

    @Override
    public Component unavailable(PoliticsView v) {
        return v.town() == null ? tr("no_town") : null;
    }

    @Override
    public void render(GuiGraphics g, PoliticsView v, float partial) {
        PoliticsView.TownView t = v.town();
        int colW = (w - 16) / 3, ch = h - BAR - 4;
        info(g, v, t, x + 2, y + 2, colW, ch);
        settings(g, t, x + 8 + colW, y + 2, colW, ch);
        people(g, v, t, x + 14 + 2 * colW, y + 2, w - 16 - 2 * colW, ch);
        actionBar(g, acts(v, t));
    }

    private List<Act> acts(PoliticsView v, PoliticsView.TownView t) {
        boolean mayor = leads(t.roles(), v.me());
        int perms = t.myPerms();
        List<Act> acts = new ArrayList<>();
        acts.add(Act.modal(tr("identity"), mayor ? null : tr("mayor_only"), () -> new NameColorModal(tr("identity"), Draw.Icon.TOWN,
                "town.name." + t.id(), t.name(), t.color(), tr("save"), null, (name, color) -> {
            if (!name.equals(t.name())) act("town.rename", "name", name);
            if (color != (t.color() & 0xFFFFFF)) act("town.color", "color", color);
        })));
        acts.add(Act.modal(tr("bank"), null, () -> new BankModal(false)));
        acts.add(Act.modal(tr("spawn"), null, SpawnModal::new));
        acts.add(Act.modal(tr("settings"), null, TownSettingsModal::new));
        acts.add(Act.modal(tr("invite"), need(perms, Perm.INVITE), () -> new TextModal(tr("invite_player"), Draw.Icon.TOWN, tr("invite_player_hint"),
                "player.town", tr("player_name"), tr("invite"), Ui.Style.GOOD, view -> view.town() == null ? tr("no_town") : need(view.town().myPerms(), Perm.INVITE),
                name -> act("town.invite", "player", name))));
        if (mayor) acts.add(Act.modal(tr("delete_town"), Ui.Style.BAD, null, () -> new ConfirmModal(tr("delete_town"),
                tr("confirm_delete_town", t.name()), tr("delete"), () -> act("town.delete"))));
        else acts.add(Act.modal(tr("leave_town"), Ui.Style.BAD, null, () -> new ConfirmModal(tr("leave_town"),
                tr("confirm_leave_town", t.name()), tr("leave"), () -> act("town.leave"))));
        return acts;
    }

    private void info(GuiGraphics g, PoliticsView v, PoliticsView.TownView t, int cx, int cy, int cw, int ch) {
        int by = card(g, cx, cy + 3, cw - 3, ch - 3, t.color(), Draw.Icon.TOWN, Component.literal(t.name()));
        int sx = cx + 6, sw = cw - 15;
        int sy = stat(g, Component.translatable("nationsandwar.info.level"), Component.translatable("nationsandwar.level." + t.level().toLowerCase()), sx, by, sw, Draw.TEXT);
        g.drawString(font, Component.translatable("nationsandwar.info.claims"), sx, sy, Draw.MUTED, false);
        Component claims = Component.literal(t.claims() + "/" + t.maxClaims());
        g.drawString(font, claims, sx + sw - font.width(claims), sy, Draw.TEXT, false);
        sy += 10;
        Draw.bar(g, sx, sy, sw, 5, t.maxClaims() == 0 ? 0 : t.claims() / (float) t.maxClaims(), Draw.INFO);
        sy += 8;
        sy = stat(g, tr("outposts"), Component.literal(t.outposts() + "/" + t.maxOutposts()), sx, sy, sw, Draw.TEXT);
        sy = stat(g, tr("next_claim"), money(t.nextClaimCost()), sx, sy, sw, Draw.TEXT);
        sy = stat(g, Component.translatable("nationsandwar.info.residents"), Component.literal(String.valueOf(t.residents().size())), sx, sy, sw, Draw.TEXT);
        if (t.nation() != null) sy = stat(g, Component.translatable("nationsandwar.gui.tab.nation"), Component.literal(nationName(v, t.nation())), sx, sy, sw, Draw.TEXT);
        sy += 4;
        int py = Math.min(sy, cy + ch - 34);
        Draw.slab(g, sx, py, sw, 24, 2, 0xFF3A3424, 0xFF262014);
        Draw.frame(g, sx, py, sw, 24, Draw.alpha(Draw.GOLD, 0x60));
        Draw.text(g, font, Component.translatable("nationsandwar.info.bank"), sx + 5, py + 4, 0.75f, Draw.MUTED, false);
        Component bank = money(t.bank());
        Draw.text(g, font, bank, sx + sw - 5 - font.width(bank) * 1.25f, py + 8, 1.25f, Draw.GOLD, true);
    }

    private void settings(GuiGraphics g, PoliticsView.TownView t, int cx, int cy, int cw, int ch) {
        heading(g, tr("flags"), cx, cy, cw);
        int sy = cy + 13;
        Flag[] flags = Flag.values();
        int fw = (cw - 3) / 2;
        for (int i = 0; i < flags.length; i++) {
            Flag f = flags[i];
            boolean on = (t.flags() & 1 << f.ordinal()) != 0;
            int fx = cx + (i % 2) * (fw + 3), fy = sy + (i / 2) * 15;
            g.fill(fx, fy, fx + fw, fy + 13, on ? 0x2A6FD08A : 0x18FFFFFF);
            g.fill(fx, fy, fx + 2, fy + 13, on ? Draw.GOOD : 0xFF4A505C);
            g.drawString(font, Draw.fit(font, Component.translatable(f.langKey()), fw - 24), fx + 6, fy + 3, on ? Draw.TEXT : Draw.MUTED, false);
            Component state = tr(on ? "on" : "off");
            Draw.text(g, font, state, fx + fw - 4 - font.width(state) * 0.7f, fy + 4, 0.7f, on ? Draw.GOOD : 0xFF6A7384, false);
        }
        sy += 33;
        g.fill(cx, sy, cx + cw, sy + 15, t.open() ? 0x2A7FB2F0 : 0x18FFFFFF);
        g.fill(cx, sy, cx + 2, sy + 15, t.open() ? Draw.INFO : 0xFF4A505C);
        g.drawString(font, Draw.fit(font, tr(t.open() ? "open" : "closed"), cw - 10), cx + 6, sy + 4, Draw.TEXT, false);
        sy += 22;
        heading(g, tr("chunk_perms"), cx, sy, cw);
        sy += 13;
        ChunkPerms cp = new ChunkPerms(t.chunkPerms());
        String[] actions = TownSettingsModal.ACTIONS;
        int labelW = Math.min(52, cw / 3), cell = (cw - labelW) / actions.length;
        for (int a = 0; a < actions.length; a++) {
            Component name = tr("action." + actions[a]);
            int ax = cx + labelW + a * cell;
            Component fit = Draw.fit(font, name, (int) ((cell - 1) / 0.55f));
            Draw.text(g, font, fit, ax + (cell - font.width(fit) * 0.55f) / 2, sy, 0.55f, Draw.MUTED, false);
            ui.tip(ax, sy - 1, cell, 7, name);
        }
        sy += 7;
        boolean odd = false;
        for (ChunkPerms.Group grp : ChunkPerms.Group.values()) {
            if (odd) g.fill(cx, sy, cx + cw, sy + 12, 0x10FFFFFF);
            odd = !odd;
            Draw.text(g, font, Draw.fit(font, tr("group." + grp.name().toLowerCase()), (int) ((labelW - 2) / 0.75f)), cx + 2, sy + 3, 0.75f, Draw.TEXT, false);
            for (int a = 0; a < actions.length; a++) {
                boolean on = cp.allows(grp, ChunkPerms.Action.values()[a]);
                int dx = cx + labelW + a * cell + cell / 2 - 3;
                g.fill(dx, sy + 3, dx + 6, sy + 9, on ? Draw.GOOD : 0xFF3A2328);
                Draw.frame(g, dx, sy + 3, 6, 6, on ? Draw.shade(Draw.GOOD, 0.6f) : 0xFF5A3238);
            }
            sy += 12;
        }
        Draw.text(g, font, Draw.fit(font, tr("settings_hint"), (int) (cw / 0.65f)), cx, sy + 4, 0.65f, 0xFF6A7384, false);
    }

    private void people(GuiGraphics g, PoliticsView v, PoliticsView.TownView t, int cx, int cy, int cw, int ch) {
        int perms = t.myPerms();
        heading(g, Component.translatable("nationsandwar.info.residents").append(" · " + t.residents().size()), cx, cy, cw);
        int top = cy + 13, listH = cy + ch - top;
        Draw.inset(g, cx, top, cw, listH);
        List<PoliticsView.Member> list = new ArrayList<>(t.residents());
        list.sort(Comparator.comparing((PoliticsView.Member m) -> !m.online()).thenComparing(PoliticsView.Member::name, String.CASE_INSENSITIVE_ORDER));
        residents.content = list.size() * 13 + 2;
        ui.scrollZone(cx, top, cw, listH, residents);
        ui.clip(g, cx + 1, top + 1, cw - 2, listH - 2);
        int ry = top + 2 - residents.offset;
        for (PoliticsView.Member m : list) {
            ui.row(g, cx + 1, ry, cw - 2, 13, m.id().equals(v.me()), null);
            Skins.face(g, m.id(), m.name(), cx + 3, ry + 2, 9);
            g.fill(cx + 10, ry + 8, cx + 13, ry + 11, m.online() ? Draw.GOOD : 0xFF555B66);
            PoliticsView.RoleView role = null;
            for (PoliticsView.RoleView r : t.roles()) if (r.id() == m.role()) role = r;
            int rx = cx + cw - 15;
            if (!m.id().equals(v.me()) && has(perms, Perm.KICK) && (role == null || role.parent() >= 0))
                ui.button(g, rx, ry + 2, 11, 10, Component.literal("✕"), Ui.Style.BAD, null, confirm(tr("kick_player"),
                        tr("confirm_kick_player", m.name(), t.name()), tr("kick"), () -> act("town.kick", "player", m.id())));
            if (role != null) {
                Component rn = Draw.fit(font, roleName(role.name()), 52);
                int rw = font.width(rn);
                Draw.text(g, font, rn, rx - 2 - rw * 0.75f, ry + 3.5f, 0.75f, Draw.mix(Draw.opaque(role.color()), 0xFFFFFFFF, 0.3f), false);
            }
            g.drawString(font, Draw.fit(font, Component.literal(m.name()), cw - 80), cx + 16, ry + 3, m.online() ? Draw.TEXT : Draw.MUTED, false);
            ui.tip(cx + 1, ry, cw - 30, 13, Component.literal(m.name()), tr(m.online() ? "online" : "offline"));
            ry += 13;
        }
        ui.unclip(g);
        ui.scrollbar(g, cx + cw - 3, top + 1, listH - 2, residents);
    }
}
