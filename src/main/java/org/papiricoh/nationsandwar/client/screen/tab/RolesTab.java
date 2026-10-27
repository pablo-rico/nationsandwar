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
 * The hierarchy of ranks of my town or my nation: the 2.5D {@link RoleTree} on the left and an inspector for the chosen
 * card on the right, with three pages — the rank itself (rename, recolour, add a sub-rank, delete), its permissions
 * (only those I hold can be granted; a town tree hides the nation-only ones) and its people (give or take the rank). A
 * province card shows its governor and the citizens to choose a new one from.
 */
public final class RolesTab extends Tab implements RoleTree.Host {
    private final boolean nation;
    private RoleTree tree;
    private EditBox rename, child;
    private int page;
    private boolean confirmDelete;
    private final Ui.Scroll scroll = new Ui.Scroll();
    private PoliticsView built;

    public RolesTab(NationScreen screen, boolean nation) {
        super(screen);
        this.nation = nation;
    }

    @Override
    public String id() {
        return nation ? "nationroles" : "roles";
    }

    @Override
    public Draw.Icon icon() {
        return nation ? Draw.Icon.NATION_ROLES : Draw.Icon.ROLES;
    }

    @Override
    public Component unavailable(PoliticsView v) {
        return nation ? v.nation() == null ? tr("no_nation") : null : v.town() == null ? tr("no_town") : null;
    }

    @Override
    public void init() {
        if (tree == null) tree = new RoleTree(this, ui, font);
        rename = field("rename", 24, tr("new_name"), false);
        child = field("child", 24, tr("rank_name"), false);
    }

    /** Opens a page of the inspector: 0 rank, 1 permissions, 2 people. */
    public void page(int page) {
        this.page = page;
    }

    private String scope() {
        return nation ? "nation" : "town";
    }

    private List<PoliticsView.RoleView> roles(PoliticsView v) {
        return nation ? v.nation().roles() : v.town().roles();
    }

    private List<PoliticsView.Member> members(PoliticsView v) {
        return nation ? v.nation().citizens() : v.town().residents();
    }

    private int myPerms(PoliticsView v) {
        return nation ? v.nation().myPerms() : v.town().myPerms();
    }

    @Override
    public void render(GuiGraphics g, PoliticsView v, float partial) {
        if (built != v) {
            tree.build(roles(v), members(v), nation ? v.nation().provinces() : null);
            built = v;
            if (tree.selected() == null || tree.node(tree.selected()) == null) tree.select("r0");
        }
        int iw = Math.min(136, w / 3);
        tree.render(g, x, y, w - iw - 6, h);
        inspector(g, v, x + w - iw, y, iw);
    }

    // --- permissions -------------------------------------------------------------------------------------------------------

    private PoliticsView viewNow() {
        return org.papiricoh.nationsandwar.client.ClientPolitics.view();
    }

    private boolean leader(PoliticsView v) {
        return leads(roles(v), v.me());
    }

    /** Whether the viewer may manage a role: the leader any, else MANAGE_ROLES and the role strictly below their own. */
    private boolean manages(PoliticsView v, PoliticsView.RoleView role) {
        if (leader(v)) return true;
        if (!has(myPerms(v), Perm.MANAGE_ROLES)) return false;
        PoliticsView.RoleView mine = null;
        for (PoliticsView.RoleView r : roles(v)) if (r.holders().contains(v.me())) mine = r;
        if (mine == null || mine.id() == role.id()) return false;
        for (PoliticsView.RoleView r = parentOf(v, role); r != null; r = parentOf(v, r)) if (r.id() == mine.id()) return true;
        return false;
    }

    private PoliticsView.RoleView parentOf(PoliticsView v, PoliticsView.RoleView role) {
        for (PoliticsView.RoleView r : roles(v)) if (r.id() == role.parent()) return r;
        return null;
    }

    private Component manageDenied(PoliticsView v, PoliticsView.RoleView role) {
        return manages(v, role) ? null : tr("cannot_manage");
    }

    @Override
    public boolean canMove(PoliticsView.RoleView role) {
        PoliticsView v = viewNow();
        return v != null && role.parent() >= 0 && manages(v, role);
    }

    @Override
    public boolean canAssignFrom(PoliticsView.RoleView role) {
        PoliticsView v = viewNow();
        return v != null && "NORMAL".equals(role.kind()) && manages(v, role);
    }

    @Override
    public boolean canAssignTo(PoliticsView.RoleView role) {
        PoliticsView v = viewNow();
        return v != null && "NORMAL".equals(role.kind()) && manages(v, role);
    }

    @Override
    public boolean canGovern() {
        PoliticsView v = viewNow();
        return nation && v != null && v.nation() != null && has(v.nation().myPerms(), Perm.MANAGE_PROVINCES);
    }

    @Override
    public void select(RoleTree.Node node) {
        page = node.province != null ? 0 : page;
        confirmDelete = false;
        scroll.offset = 0;
    }

    @Override
    public void move(PoliticsView.RoleView role, PoliticsView.RoleView parent) {
        act("role.move", "scope", scope(), "id", role.id(), "parent", parent.id());
    }

    @Override
    public void assign(UUID player, PoliticsView.RoleView role) {
        act("role.assign", "scope", scope(), "id", role.id(), "player", player);
    }

    @Override
    public void governor(UUID player, PoliticsView.ProvinceView province) {
        act("province.governor", "province", province.id(), "player", player);
    }

    // --- inspector ---------------------------------------------------------------------------------------------------------

    private void inspector(GuiGraphics g, PoliticsView v, int ix, int iy, int iw) {
        Draw.panel(g, ix, iy, iw, h);
        RoleTree.Node n = tree.node(tree.selected());
        if (n == null) {
            g.drawWordWrap(font, tr("pick_card"), ix + 6, iy + 8, iw - 12, Draw.MUTED);
            return;
        }
        if (n.province != null) {
            province(g, v, n.province, ix + 5, iy + 5, iw - 10);
            return;
        }
        PoliticsView.RoleView r = n.role;
        int rc = Draw.opaque(r.color());
        g.fillGradient(ix + 1, iy + 1, ix + iw - 1, iy + 22, Draw.alpha(rc, 0xA0), Draw.alpha(rc, 0x30));
        g.drawString(font, Draw.fit(font, roleName(r.name()), iw - 12), ix + 6, iy + 4, 0xFFFFFFFF, true);
        String kind = switch (r.kind()) {
            case "LEADER" -> "kind.leader";
            case "GOVERNOR" -> "kind.governor";
            default -> "kind.rank";
        };
        Draw.text(g, font, tr(kind), ix + 6, iy + 14, 0.65f, 0xFFD8DCE4, false);
        String[] pages = {"page.rank", "page.perms", "page.people"};
        int tw = (iw - 10) / 3;
        for (int i = 0; i < 3; i++) {
            int p = i;
            ui.button(g, ix + 5 + i * tw, iy + 25, tw, 13, tr(pages[i]), page == i ? Ui.Style.GOLD : Ui.Style.NORMAL, null, () -> {
                page = p;
                scroll.offset = 0;
            });
        }
        int by = iy + 42, bw = iw - 10, bx = ix + 5;
        switch (page) {
            case 1 -> perms(g, v, r, bx, by, bw);
            case 2 -> people(g, v, r, bx, by, bw);
            default -> rank(g, v, r, bx, by, bw);
        }
    }

    private void rank(GuiGraphics g, PoliticsView v, PoliticsView.RoleView r, int bx, int by, int bw) {
        Component denied = manageDenied(v, r);
        g.drawString(font, tr("name"), bx, by, Draw.MUTED, false);
        place(rename, bx, by + 10, bw - 42);
        ui.button(g, bx + bw - 40, by + 10, 40, tr("rename"), denied != null ? denied : rename.getValue().isBlank() ? tr("type_name") : null, () -> {
            act("role.rename", "scope", scope(), "id", r.id(), "name", rename.getValue().trim());
            screen.clearField(rename);
        });
        by += 28;
        g.drawString(font, tr("colour"), bx, by, Draw.MUTED, false);
        by += 10;
        int size = Math.max(6, (bw - 7) / 8 - 1);
        for (int i = 0; i < PALETTE.length; i++) {
            int c = PALETTE[i];
            ui.swatch(g, bx + i % 8 * (size + 1), by + i / 8 * (size + 1), size, c, (r.color() & 0xFFFFFF) == c, denied == null,
                    () -> act("role.color", "scope", scope(), "id", r.id(), "color", c));
        }
        by += 2 * (size + 1) + 6;
        g.drawString(font, tr("add_child"), bx, by, Draw.MUTED, false);
        place(child, bx, by + 10, bw - 42);
        boolean canAdd = leader(v) || has(myPerms(v), Perm.MANAGE_ROLES) && manages(v, r) || has(myPerms(v), Perm.MANAGE_ROLES) && r.holders().contains(v.me());
        Component addDenied = !canAdd ? tr("cannot_manage") : child.getValue().isBlank() ? tr("type_name") : null;
        ui.button(g, bx + bw - 40, by + 10, 40, 13, tr("add"), Ui.Style.GOOD, addDenied, () -> {
            int color = PALETTE[(roles(v).size() * 5 + 3) % PALETTE.length];
            act("role.add", "scope", scope(), "name", child.getValue().trim(), "color", color, "parent", r.id());
            screen.clearField(child);
        });
        by += 30;
        if ("NORMAL".equals(r.kind())) {
            ui.button(g, bx, by, bw, 13, confirmDelete ? tr("confirm_delete") : tr("delete_rank"), Ui.Style.BAD, denied, () -> {
                if (confirmDelete) act("role.remove", "scope", scope(), "id", r.id());
                confirmDelete = !confirmDelete;
            });
            by += 16;
        }
        g.drawWordWrap(font, tr(r.parent() < 0 ? "leader_hint" : "GOVERNOR".equals(r.kind()) ? "governor_hint" : "drag_hint"), bx, by + 2, bw, 0xFF6A7384);
    }

    private void perms(GuiGraphics g, PoliticsView v, PoliticsView.RoleView r, int bx, int by, int bw) {
        List<Perm> shown = new ArrayList<>();
        for (Perm p : Perm.values()) if (nation || !Perm.NATION_ONLY.contains(p)) shown.add(p);
        int top = by, lh = y + h - top - 4;
        scroll.content = shown.size() * 13 + 2;
        ui.scrollZone(bx, top, bw, lh, scroll);
        ui.clip(g, bx, top, bw, lh);
        int ry = top - scroll.offset;
        Component denied = r.parent() < 0 ? tr("leader_all") : manageDenied(v, r);
        int mine = myPerms(v);
        for (Perm p : shown) {
            boolean on = r.parent() < 0 || has(r.perms(), p);
            Component why = denied != null ? denied : !has(mine, p) && !leader(v) ? tr("not_yours") : null;
            ui.check(g, bx, ry, Draw.fit(font, Component.translatable(p.langKey()), bw - 16), on, why,
                    () -> act("role.perms", "scope", scope(), "id", r.id(), "perms", r.perms() ^ 1 << p.ordinal()));
            ry += 13;
        }
        ui.unclip(g);
        ui.scrollbar(g, bx + bw - 2, top, lh, scroll);
    }

    private void people(GuiGraphics g, PoliticsView v, PoliticsView.RoleView r, int bx, int by, int bw) {
        int top = by, lh = y + h - top - 4;
        List<PoliticsView.Member> all = members(v);
        boolean normal = "NORMAL".equals(r.kind());
        int others = 0;
        for (PoliticsView.Member m : all) if (m.role() != r.id()) others++;
        scroll.content = (r.holders().size() + (normal ? others : 0)) * 13 + 30;
        ui.scrollZone(bx, top, bw, lh, scroll);
        ui.clip(g, bx, top, bw, lh);
        int ry = top - scroll.offset;
        Component denied = !normal ? tr(r.parent() < 0 ? "leader_fixed" : "governor_hint_short") : manageDenied(v, r);
        g.drawString(font, tr("holders", r.holders().size()), bx, ry, Draw.GOLD, false);
        ry += 11;
        for (UUID id : r.holders()) {
            String name = "?";
            boolean online = false;
            for (PoliticsView.Member m : all) {
                if (m.id().equals(id)) {
                    name = m.name();
                    online = m.online();
                }
            }
            Skins.face(g, id, name, bx + 1, ry + 2, 8);
            g.drawString(font, Draw.fit(font, Component.literal(name), bw - 28), bx + 12, ry + 2, online ? Draw.TEXT : Draw.MUTED, false);
            ui.button(g, bx + bw - 13, ry + 1, 11, 10, Component.literal("✕"), Ui.Style.BAD, denied,
                    () -> act("role.unassign", "scope", scope(), "player", id));
            ry += 13;
        }
        if (!normal) {
            ui.unclip(g);
            g.drawWordWrap(font, tr(r.parent() < 0 ? "leader_fixed" : "governor_hint"), bx, Math.max(ry + 4, top + 20), bw, 0xFF6A7384);
            return;
        }
        ry += 4;
        g.drawString(font, tr("give_rank"), bx, ry, Draw.GOLD, false);
        ry += 11;
        for (PoliticsView.Member m : all) {
            if (m.role() == r.id()) continue;
            Skins.face(g, m.id(), m.name(), bx + 1, ry + 2, 8);
            g.drawString(font, Draw.fit(font, Component.literal(m.name()), bw - 28), bx + 12, ry + 2, m.online() ? Draw.TEXT : Draw.MUTED, false);
            boolean isLeader = false;
            for (PoliticsView.RoleView rv : roles(v)) if (rv.parent() < 0 && rv.holders().contains(m.id())) isLeader = true;
            ui.button(g, bx + bw - 13, ry + 1, 11, 10, Component.literal("+"), Ui.Style.GOOD, isLeader ? tr("leader_fixed") : denied,
                    () -> act("role.assign", "scope", scope(), "id", r.id(), "player", m.id()));
            ry += 13;
        }
        ui.unclip(g);
        ui.scrollbar(g, bx + bw - 2, top, lh, scroll);
    }

    private void province(GuiGraphics g, PoliticsView v, PoliticsView.ProvinceView p, int bx, int by, int bw) {
        Draw.icon(g, Draw.Icon.SCROLL, bx, by);
        g.drawString(font, Draw.fit(font, Component.literal(p.name()), bw - 20), bx + 19, by + 4, Draw.GOLD, true);
        by += 20;
        Component capital = Component.translatable("nationsandwar.info.capital").append(": " + townName(v, p.capital()));
        g.drawString(font, Draw.fit(font, capital, bw), bx, by, Draw.MUTED, false);
        by += 11;
        g.drawString(font, tr("n_towns", p.towns().size()), bx, by, Draw.MUTED, false);
        by += 14;
        Component denied = canGovern() ? null : need(v.nation().myPerms(), Perm.MANAGE_PROVINCES);
        heading(g, tr("governor"), bx, by, bw);
        by += 12;
        if (p.governor() != null) {
            Skins.face(g, p.governor(), p.governorName(), bx, by, 12);
            g.drawString(font, Draw.fit(font, Component.literal(p.governorName()), bw - 50), bx + 15, by + 2, Draw.TEXT, false);
            ui.button(g, bx + bw - 30, by, 30, 13, tr("clear"), Ui.Style.BAD, denied, () -> act("province.governor", "province", p.id(), "player", ""));
        } else g.drawString(font, tr("no_governor"), bx, by + 2, Draw.MUTED, false);
        by += 18;
        g.drawString(font, tr("pick_governor"), bx, by, Draw.MUTED, false);
        by += 11;
        int lh = y + h - by - 4;
        scroll.content = v.nation().citizens().size() * 12;
        ui.scrollZone(bx, by, bw, lh, scroll);
        ui.clip(g, bx, by, bw, lh);
        int ry = by - scroll.offset;
        for (PoliticsView.Member m : v.nation().citizens()) {
            boolean is = m.id().equals(p.governor());
            ui.row(g, bx, ry, bw, 12, is, denied == null && !is ? () -> act("province.governor", "province", p.id(), "player", m.id()) : null);
            Skins.face(g, m.id(), m.name(), bx + 2, ry + 2, 8);
            g.drawString(font, Draw.fit(font, Component.literal(m.name()), bw - 16), bx + 13, ry + 2, m.online() ? Draw.TEXT : Draw.MUTED, false);
            ry += 12;
        }
        ui.unclip(g);
    }

    @Override
    public void viewChanged(PoliticsView v) {
        confirmDelete = false;
    }

    // --- input -------------------------------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return tree.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return tree.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        return tree.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        return tree.mouseScrolled(mx, my, delta);
    }
}
