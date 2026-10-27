package org.papiricoh.nationsandwar.client.screen.tab;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.papiricoh.nationsandwar.client.gui.Draw;
import org.papiricoh.nationsandwar.client.gui.Skins;
import org.papiricoh.nationsandwar.client.gui.Ui;
import org.papiricoh.nationsandwar.client.screen.NationScreen;
import org.papiricoh.nationsandwar.model.ChunkPerms;
import org.papiricoh.nationsandwar.model.Flag;
import org.papiricoh.nationsandwar.model.Perm;
import org.papiricoh.nationsandwar.view.PoliticsView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * My town: facts and bank, name, colour and spawn (left); flags, open/closed and the chunk permission matrix, group ×
 * action (middle); and the residents with their online state and rank, invite and kick (right).
 */
public final class TownTab extends Tab {
    private static final String[] ACTIONS = {"build", "destroy", "switch", "container", "item_use"};
    private EditBox amount, rename, invite;
    private final Ui.Scroll residents = new Ui.Scroll();
    private boolean confirmDelete;

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
    public void init() {
        amount = field("amount", 7, tr("amount"), true);
        rename = field("rename", 24, tr("new_name"), false);
        invite = field("invite", 16, tr("player_name"), false);
    }

    @Override
    public void render(GuiGraphics g, PoliticsView v, float partial) {
        PoliticsView.TownView t = v.town();
        int colW = (w - 16) / 3;
        info(g, v, t, x + 2, y + 2, colW);
        settings(g, t, x + 8 + colW, y + 2, colW);
        people(g, v, t, x + 14 + 2 * colW, y + 2, w - 16 - 2 * colW);
    }

    private void info(GuiGraphics g, PoliticsView v, PoliticsView.TownView t, int cx, int cy, int cw) {
        int perms = t.myPerms();
        boolean mayor = leads(t.roles(), v.me());
        heading(g, Component.literal(t.name()).withStyle(s -> s.withColor(Draw.mix(Draw.opaque(t.color()), 0xFFFFFFFF, 0.3f) & 0xFFFFFF)), cx, cy, cw);
        int sy = cy + 13;
        sy = stat(g, Component.translatable("nationsandwar.info.level"), Component.translatable("nationsandwar.level." + t.level().toLowerCase()), cx, sy, cw, Draw.TEXT);
        g.drawString(font, Component.translatable("nationsandwar.info.claims"), cx, sy, Draw.MUTED, false);
        Draw.bar(g, cx + 44, sy + 1, cw - 90, 6, t.maxClaims() == 0 ? 0 : t.claims() / (float) t.maxClaims(), Draw.INFO);
        Component claims = Component.literal(t.claims() + "/" + t.maxClaims());
        g.drawString(font, claims, cx + cw - font.width(claims), sy, Draw.TEXT, false);
        sy += 11;
        sy = stat(g, tr("outposts"), Component.literal(t.outposts() + "/" + t.maxOutposts()), cx, sy, cw, Draw.TEXT);
        sy = stat(g, tr("next_claim"), money(t.nextClaimCost()), cx, sy, cw, Draw.TEXT);
        sy = stat(g, Component.translatable("nationsandwar.info.bank"), money(t.bank()), cx, sy, cw, Draw.GOOD);
        bank(g, amount, cx, sy + 1, cw, need(perms, Perm.BANK_WITHDRAW), "town.deposit", "town.withdraw");
        sy += 20;
        heading(g, tr("identity"), cx, sy, cw);
        sy += 13;
        place(rename, cx, sy, cw - 50);
        ui.button(g, cx + cw - 48, sy, 48, tr("rename"), !mayor ? tr("mayor_only") : rename.getValue().isBlank() ? tr("type_name") : null, () -> {
            act("town.rename", "name", rename.getValue().trim());
            screen.clearField(rename);
        });
        sy += 17;
        sy += palette(g, cx, sy, cw, t.color(), mayor ? c -> act("town.color", "color", c) : null) + 4;
        int bw = (cw - 3) / 2;
        ui.button(g, cx, sy, bw, tr("set_spawn"), need(perms, Perm.SET_SPAWN), () -> act("town.setspawn"));
        ui.button(g, cx + bw + 3, sy, cw - bw - 3, tr("go_spawn"), null, () -> act("town.spawn"));
        sy += 16;
        if (mayor) {
            ui.button(g, cx, sy, cw, 13, confirmDelete ? tr("confirm_delete") : tr("delete_town"), Ui.Style.BAD, null, () -> {
                if (confirmDelete) act("town.delete");
                confirmDelete = !confirmDelete;
            });
        } else {
            ui.button(g, cx, sy, cw, 13, tr("leave_town"), Ui.Style.BAD, null, () -> act("town.leave"));
        }
    }

    private void settings(GuiGraphics g, PoliticsView.TownView t, int cx, int cy, int cw) {
        int perms = t.myPerms();
        heading(g, tr("flags"), cx, cy, cw);
        int sy = cy + 13;
        Flag[] flags = Flag.values();
        for (int i = 0; i < flags.length; i++) {
            Flag f = flags[i];
            boolean on = (t.flags() & 1 << f.ordinal()) != 0;
            ui.check(g, cx + (i % 2) * (cw / 2), sy + (i / 2) * 13, Component.translatable(f.langKey()), on, need(perms, Perm.MANAGE_FLAGS),
                    () -> act("town.flag", "flag", f.name(), "value", (byte) (on ? 0 : 1)));
        }
        sy += 27;
        ui.check(g, cx, sy, tr(t.open() ? "open" : "closed"), t.open(), need(perms, Perm.INVITE), () -> act("town.open", "value", !t.open()));
        sy += 18;
        heading(g, tr("chunk_perms"), cx, sy, cw);
        sy += 13;
        ChunkPerms cp = new ChunkPerms(t.chunkPerms());
        int labelW = 40, cell = Math.max(12, Math.min(17, (cw - labelW) / ACTIONS.length));
        for (int a = 0; a < ACTIONS.length; a++) {
            Component name = tr("action." + ACTIONS[a]);
            int ax = cx + labelW + a * cell;
            Draw.text(g, font, Draw.fit(font, name, (int) ((cell - 1) / 0.5f)), ax, sy + 1, 0.5f, Draw.MUTED, false);
            ui.tip(ax, sy - 1, cell, 7, name);
        }
        sy += 7;
        Component denied = need(perms, Perm.MANAGE_PLOTS);
        for (ChunkPerms.Group grp : ChunkPerms.Group.values()) {
            Draw.text(g, font, Draw.fit(font, tr("group." + grp.name().toLowerCase()), (int) ((labelW - 2) / 0.75f)), cx, sy + 3, 0.75f, Draw.TEXT, false);
            for (int a = 0; a < ACTIONS.length; a++) {
                ChunkPerms.Action ac = ChunkPerms.Action.values()[a];
                boolean on = cp.allows(grp, ac);
                ui.check(g, cx + labelW + a * cell, sy, Component.empty(), on, denied,
                        () -> act("town.perm", "group", grp.name(), "perm", ac.name(), "value", !on));
            }
            sy += 13;
        }
        g.drawWordWrap(font, tr("chunk_perms_hint"), cx, sy + 3, cw, 0xFF6A7384);
    }

    private void people(GuiGraphics g, PoliticsView v, PoliticsView.TownView t, int cx, int cy, int cw) {
        int perms = t.myPerms();
        heading(g, Component.translatable("nationsandwar.info.residents").append(" · " + t.residents().size()), cx, cy, cw);
        int top = cy + 13, listH = h - 34 - (top - y);
        Draw.inset(g, cx, top, cw, listH);
        List<PoliticsView.Member> list = new ArrayList<>(t.residents());
        list.sort(Comparator.comparing((PoliticsView.Member m) -> !m.online()).thenComparing(PoliticsView.Member::name, String.CASE_INSENSITIVE_ORDER));
        residents.content = list.size() * 12 + 2;
        ui.scrollZone(cx, top, cw, listH, residents);
        ui.clip(g, cx + 1, top + 1, cw - 2, listH - 2);
        int ry = top + 2 - residents.offset;
        for (PoliticsView.Member m : list) {
            ui.row(g, cx + 1, ry, cw - 2, 12, m.id().equals(v.me()), null);
            Skins.face(g, m.id(), m.name(), cx + 3, ry + 2, 8);
            g.fill(cx + 13, ry + 8, cx + 16, ry + 11, m.online() ? Draw.GOOD : 0xFF555B66);
            PoliticsView.RoleView role = null;
            for (PoliticsView.RoleView r : t.roles()) if (r.id() == m.role()) role = r;
            int rx = cx + cw - 14;
            if (!m.id().equals(v.me()) && has(perms, Perm.KICK) && (role == null || role.parent() >= 0))
                ui.button(g, rx, ry + 1, 11, 10, Component.literal("✕"), Ui.Style.BAD, null, () -> act("town.kick", "player", m.id()));
            if (role != null) {
                Component rn = Draw.fit(font, roleName(role.name()), 52);
                int rw = font.width(rn);
                Draw.text(g, font, rn, rx - 2 - rw * 0.75f, ry + 3, 0.75f, Draw.mix(Draw.opaque(role.color()), 0xFFFFFFFF, 0.3f), false);
            }
            g.drawString(font, Draw.fit(font, Component.literal(m.name()), cw - 80), cx + 19, ry + 2, m.online() ? Draw.TEXT : Draw.MUTED, false);
            ui.tip(cx + 1, ry, cw - 30, 12, Component.literal(m.name()), tr(m.online() ? "online" : "offline"));
            ry += 12;
        }
        ui.unclip(g);
        ui.scrollbar(g, cx + cw - 3, top + 1, listH - 2, residents);
        int iy = top + listH + 4;
        place(invite, cx, iy, cw - 50);
        Component denied = need(perms, Perm.INVITE);
        ui.button(g, cx + cw - 48, iy, 48, tr("invite"), denied != null ? denied : invite.getValue().isBlank() ? tr("type_name") : null, () -> {
            act("town.invite", "player", invite.getValue().trim());
            screen.clearField(invite);
        });
    }
}
