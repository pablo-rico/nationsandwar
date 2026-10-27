package org.papiricoh.nationsandwar.client.screen.tab;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.papiricoh.nationsandwar.client.ClientClaims;
import org.papiricoh.nationsandwar.client.ClientPolitics;
import org.papiricoh.nationsandwar.client.gui.Draw;
import org.papiricoh.nationsandwar.client.gui.Ui;
import org.papiricoh.nationsandwar.client.screen.NationScreen;
import org.papiricoh.nationsandwar.client.screen.modal.ConfirmModal;
import org.papiricoh.nationsandwar.client.screen.modal.NameColorModal;
import org.papiricoh.nationsandwar.client.screen.modal.PeopleModal;
import org.papiricoh.nationsandwar.client.screen.modal.PermsModal;
import org.papiricoh.nationsandwar.client.screen.modal.PickModal;
import org.papiricoh.nationsandwar.model.Perm;
import org.papiricoh.nationsandwar.view.Briefs;
import org.papiricoh.nationsandwar.view.PoliticsView;
import org.papiricoh.nationsandwar.view.TreeView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Every town and nation of the world, and their ranks. On the left a collapsible, searchable navigation tree — Alliance ›
 * Nation › Province › Town, then the independent nations and the free towns — built from the public briefs. On the right
 * the picked entity's header card (colour, government or level, leader or mayor, counts, relation to mine) above its 2.5D
 * {@link RoleTree} (asked for with {@link ClientPolitics#requestTree}): editable by drag when it is my town or nation,
 * read-only otherwise; a nation run by another mod shows its {@link #managedCard} instead. The action bar works on the
 * picked card through modals: edit the rank, its permissions, its people, a new sub-rank, delete it; a province card's
 * governor, name and colour.
 */
public final class AtlasTab extends Tab implements RoleTree.Host {
    /** An entity to show: scope "town" or "nation". */
    public record Target(String scope, UUID id) {
    }

    /**
     * What the viewer may do on a tree: the leader manages any rank; anyone else with MANAGE_ROLES the ranks strictly below
     * their own. Shared with the rank modals.
     */
    public record Rights(TreeView tree, PoliticsView v) {
        public int myPerms() {
            if (tree == null || !tree.mine()) return 0;
            if (tree.nation()) return v.nation() != null && v.nation().id().equals(tree.id()) ? v.nation().myPerms() : 0;
            return v.town() != null && v.town().id().equals(tree.id()) ? v.town().myPerms() : 0;
        }

        public boolean leader() {
            return tree != null && tree.mine() && leads(tree.roles(), v.me());
        }

        public boolean manages(PoliticsView.RoleView role) {
            if (tree == null || !tree.mine()) return false;
            if (leader()) return true;
            if (!has(myPerms(), Perm.MANAGE_ROLES)) return false;
            PoliticsView.RoleView mine = tree.roleOf(v.me());
            if (mine == null || mine.id() == role.id()) return false;
            int guard = 0;
            for (PoliticsView.RoleView r = parentOf(role); r != null && guard++ < 64; r = parentOf(r)) if (r.id() == mine.id()) return true;
            return false;
        }

        /** Whether the viewer may add a rank under this one: managing it, or holding it with MANAGE_ROLES. */
        public boolean canAdd(PoliticsView.RoleView role) {
            return leader() || has(myPerms(), Perm.MANAGE_ROLES) && (manages(role) || role.holders().contains(v.me()));
        }

        public boolean governs() {
            return tree != null && tree.nation() && tree.mine() && has(myPerms(), Perm.MANAGE_PROVINCES);
        }

        private PoliticsView.RoleView parentOf(PoliticsView.RoleView role) {
            for (PoliticsView.RoleView r : tree.roles()) if (r.id() == role.parent()) return r;
            return null;
        }
    }

    /** A row of the navigation tree. */
    private static final class Nav {
        static final int SECTION = 0, ALLIANCE = 1, NATION = 2, PROVINCE = 3, TOWN = 4;
        final int kind;
        final String key;
        final UUID id, nation;
        final String name;
        final int color;
        final List<Nav> kids = new ArrayList<>();
        boolean managed, mine, npc, capital;

        Nav(int kind, String key, UUID id, UUID nation, String name, int color) {
            this.kind = kind;
            this.key = key;
            this.id = id;
            this.nation = nation;
            this.name = name;
            this.color = color;
        }

        boolean matches(String q) {
            if (kind != SECTION && name.toLowerCase(Locale.ROOT).contains(q)) return true;
            for (Nav k : kids) if (k.matches(q)) return true;
            return false;
        }
    }

    private static final int ROW = 13, HEADER = 44;
    private final Set<String> expanded = new HashSet<>();
    private final Ui.Scroll navScroll = new Ui.Scroll();
    private EditBox search;
    private boolean navOpen = true, treeShown;
    private Target picked;
    private Function<PoliticsView, Target> pending;
    private String pendingCard;
    private RoleTree tree;
    private TreeView built;

    public AtlasTab(NationScreen screen) {
        super(screen);
    }

    @Override
    public String id() {
        return "atlas";
    }

    @Override
    public Draw.Icon icon() {
        return Draw.Icon.NATION_ROLES;
    }

    @Override
    public void init() {
        search = field("search", 24, tr("search"), false);
    }

    // --- focus -------------------------------------------------------------------------------------------------------------

    /** Shows a town ({@code scope} "town") or a nation ("nation"), opening the navigation tree down to it. */
    public void focus(String scope, UUID id) {
        if (id == null) return;
        pending = null;
        if (picked != null && picked.scope().equals(scope) && picked.id().equals(id)) return;
        picked = new Target(scope, id);
        tree = null;
        built = null;
        expandTo(scope, id);
        ClientPolitics.requestTree(scope, id);
    }

    /** Focuses what {@code pick} chooses as soon as it chooses something (null: not yet) from a view. */
    public void focusLater(Function<PoliticsView, Target> pick) {
        pending = pick;
    }

    private void focusProvince(UUID nation, UUID province) {
        focus("nation", nation);
        pendingCard = "p" + province;
        if (tree != null && tree.node(pendingCard) != null) {
            tree.select(pendingCard);
            pendingCard = null;
        }
        expanded.add("p" + province);
    }

    private void expandTo(String scope, UUID id) {
        UUID nation = id;
        if (scope.equals("town")) {
            Briefs.TownBrief t = ClientClaims.town(id);
            if (t == null) return;
            if (t.province() != null) expanded.add("p" + t.province());
            nation = t.nation();
        }
        if (nation == null) return;
        expanded.add("n" + nation);
        Briefs.NationBrief n = ClientClaims.nation(nation);
        if (n != null && n.alliance() != null) expanded.add("a" + n.alliance());
    }

    private Target defaultTarget(PoliticsView v) {
        if (v.town() != null) return new Target("town", v.town().id());
        if (v.nation() != null) return new Target("nation", v.nation().id());
        Briefs.NationBrief first = null;
        for (Briefs.NationBrief n : ClientClaims.nations()) if (first == null || n.name().compareToIgnoreCase(first.name()) < 0) first = n;
        return first == null ? null : new Target("nation", first.id());
    }

    // --- drawing -----------------------------------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, PoliticsView v, float partial) {
        if (pending != null) {
            Target t = pending.apply(v);
            if (t != null) focus(t.scope(), t.id());
        }
        if (picked == null) {
            Target t = defaultTarget(v);
            if (t != null) focus(t.scope(), t.id());
        }
        if (picked != null) ClientPolitics.requestTree(picked.scope(), picked.id());
        int navW = navOpen ? Math.min(150, w / 3) : 16;
        nav(g, v, x, y, navW);
        int rx = x + navW + 6, rw = w - navW - 6;
        treeShown = false;
        if (picked == null) {
            g.drawCenteredString(font, tr("atlas_empty"), rx + rw / 2, y + h / 2 - 4, Draw.MUTED);
            return;
        }
        header(g, v, rx, y + 4, rw);
        int by = y + HEADER + 8, barY = y + h - 18, bh = barY - by - 3;
        List<Act> acts = body(g, v, rx, by, rw, bh);
        actionBar(g, rx, barY, rw, acts);
        TreeView t = ClientPolitics.tree();
        if (t != null && t.exists() && !t.mine() && acts.size() < 3) {
            Component ro = tr("read_only");
            ui.chip(g, rx + rw - font.width(ro) - 10, barY + 4, ro, Draw.MUTED);
        }
    }

    /** The picked entity's tree (or managed card, or a placeholder); returns the action bar's buttons. */
    private List<Act> body(GuiGraphics g, PoliticsView v, int bx, int by, int bw, int bh) {
        Briefs.NationBrief managed = managedNation();
        if (managed != null) {
            g.fillGradient(bx, by, bx + bw, by + bh, 0xFF1B2230, 0xFF10141B);
            int cw = Math.min(240, bw - 20), ch = Math.min(bh - 12, 200);
            managedCard(g, managed, bx + (bw - cw) / 2, by + 6, cw, ch);
            return List.of();
        }
        TreeView t = ClientPolitics.tree();
        if (t == null || !t.exists()) {
            g.fillGradient(bx, by, bx + bw, by + bh, 0xFF1B2230, 0xFF10141B);
            g.drawCenteredString(font, t == null ? Component.translatable("nationsandwar.gui.loading") : tr("gone"), bx + bw / 2, by + bh / 2 - 4, Draw.MUTED);
            return List.of();
        }
        if (tree == null) tree = new RoleTree(this, ui, font);
        if (built != t) {
            built = t;
            tree.tint(t.color());
            tree.build(t.roles(), t.members(), t.nation() ? t.provinces() : null);
            tree.readOnly(!t.mine());
            if (pendingCard != null && tree.node(pendingCard) != null) {
                tree.select(pendingCard);
                pendingCard = null;
            }
            if (tree.selected() == null || tree.node(tree.selected()) == null)
                for (PoliticsView.RoleView r : t.roles()) if (r.parent() < 0) tree.select("r" + r.id());
        }
        tree.render(g, bx, by, bw, bh);
        treeShown = true;
        return acts(v, t);
    }

    /** The nation run by another mod behind the picked entity, or null. */
    private Briefs.NationBrief managedNation() {
        UUID nation = picked.id();
        if (picked.scope().equals("town")) {
            Briefs.TownBrief tb = ClientClaims.town(picked.id());
            if (tb == null || !tb.managed()) return null;
            nation = tb.nation();
        }
        Briefs.NationBrief n = nation == null ? null : ClientClaims.nation(nation);
        return n != null && n.managed() ? n : null;
    }

    private List<Act> acts(PoliticsView v, TreeView t) {
        RoleTree.Node n = tree.node(tree.selected());
        List<Act> acts = new ArrayList<>();
        if (n == null) return acts;
        Rights rights = new Rights(t, v);
        String scope = t.scope();
        if (n.province != null) {
            PoliticsView.ProvinceView p = n.province;
            if (!t.mine()) return acts;
            Component denied = rights.governs() ? null : need(rights.myPerms(), Perm.MANAGE_PROVINCES);
            acts.add(Act.modal(tr("governor"), denied, () -> governorModal(p)));
            acts.add(Act.modal(tr("identity"), denied, () -> new NameColorModal(tr("province_identity", p.name()), Draw.Icon.SCROLL,
                    "province.name." + p.id(), p.name(), p.color(), tr("save"), null, (name, color) -> {
                if (!name.equals(p.name())) act("province.rename", "province", p.id(), "name", name);
                if (color != (p.color() & 0xFFFFFF)) act("province.color", "province", p.id(), "color", color);
            })));
            acts.add(Act.now(tr("open_provinces"), Ui.Style.NORMAL, null, () -> screen.select("provinces")));
            return acts;
        }
        PoliticsView.RoleView r = n.role;
        String display = roleName(r.name()).getString();
        if (!t.mine()) {
            acts.add(Act.modal(tr("people"), null, () -> new PeopleModal(roleName(r.name()), r.id())));
            return acts;
        }
        Component manage = rights.manages(r) ? null : tr("cannot_manage");
        acts.add(Act.modal(tr("edit_rank"), manage, () -> new NameColorModal(tr("edit_rank_of", display), Draw.Icon.ROLES,
                "rank." + scope + "." + t.id() + "." + r.id(), display, r.color(), tr("save"), null, (name, color) -> {
            if (!name.equals(display)) act("role.rename", "scope", scope, "id", r.id(), "name", name);
            if (color != (r.color() & 0xFFFFFF)) act("role.color", "scope", scope, "id", r.id(), "color", color);
        })));
        if (!t.permsHidden()) acts.add(Act.modal(tr("page.perms"), null, () -> new PermsModal(roleName(r.name()), r.id())));
        acts.add(Act.modal(tr("people"), null, () -> new PeopleModal(roleName(r.name()), r.id())));
        acts.add(Act.modal(tr("new_rank"), rights.canAdd(r) ? null : tr("cannot_manage"), () -> new NameColorModal(tr("new_rank_under", display),
                Draw.Icon.ROLES, "rank.new." + scope + "." + t.id(), "", PALETTE[(t.roles().size() * 5 + 3) % PALETTE.length], tr("create"), null,
                (name, color) -> act("role.add", "scope", scope, "name", name, "color", color, "parent", r.id()))));
        if ("NORMAL".equals(r.kind()))
            acts.add(Act.modal(tr("delete_rank"), Ui.Style.BAD, manage, () -> new ConfirmModal(tr("delete_rank"),
                    tr("confirm_delete_rank", display), tr("delete"), () -> act("role.remove", "scope", scope, "id", r.id()))));
        return acts;
    }

    private PickModal governorModal(PoliticsView.ProvinceView p) {
        return new PickModal(tr("governor_of", p.name()), Draw.Icon.SCROLL, tr("pick_governor_hint"), v -> {
            TreeView t = ClientPolitics.tree();
            List<PickModal.Row> rows = new ArrayList<>();
            UUID current = p.governor();
            if (t != null) for (PoliticsView.ProvinceView q : t.provinces()) if (q.id().equals(p.id())) current = q.governor();
            rows.add(PickModal.Row.of(PickModal.NONE, tr("no_governor"), null, 0x3A3F48));
            if (t != null) for (PoliticsView.Member m : t.members())
                rows.add(PickModal.Row.person(m.id(), m.name(), tr(m.online() ? "online" : "offline"), m.id().equals(current)));
            return rows;
        }, tr("appoint"), Ui.Style.GOLD, v -> new Rights(ClientPolitics.tree(), v).governs() ? null : tr("needs", Component.translatable(Perm.MANAGE_PROVINCES.langKey())),
                (id, name) -> act("province.governor", "province", p.id(), "player", PickModal.NONE.equals(id) ? "" : id.toString()));
    }

    /** The picked entity's card: emblem, name, government or level, relation and the facts that matter. */
    private void header(GuiGraphics g, PoliticsView v, int hx, int hy, int hw) {
        boolean nation = picked.scope().equals("nation");
        Briefs.NationBrief nb = nation ? ClientClaims.nation(picked.id()) : null;
        Briefs.TownBrief tb = nation ? null : ClientClaims.town(picked.id());
        TreeView t = ClientPolitics.tree();
        String name = nb != null ? nb.name() : tb != null ? tb.name() : t != null && t.exists() ? t.name() : "?";
        int color = Draw.opaque(nb != null ? nb.color() : tb != null ? tb.color() : t != null ? t.color() : 0x777777);
        int cw = hw - 4;
        Draw.shadow(g, hx, hy, cw, HEADER - 4, 6);
        Draw.slab(g, hx, hy, cw, HEADER - 4, 3, Draw.mix(0xFF2B323E, color, 0.22f), Draw.mix(0xFF161A21, color, 0.08f));
        g.fill(hx, hy, hx + 3, hy + HEADER - 4, color);
        int ex = hx + 8, ey = hy + 5;
        g.fillGradient(ex, ey, ex + 30, ey + 30, Draw.mix(color, 0xFFFFFFFF, 0.18f), Draw.shade(color, 0.6f));
        Draw.frame(g, ex, ey, 30, 30, Draw.shade(color, 0.4f));
        g.fill(ex + 1, ey + 1, ex + 29, ey + 2, 0x50FFFFFF);
        Draw.icon(g, nation ? Draw.Icon.NATION : Draw.Icon.TOWN, ex + 7, ey + 7);
        int tx = ex + 37;
        g.drawString(font, Draw.fit(font, Component.literal(name), hw / 2 - 30), tx, hy + 7, Draw.mix(color, 0xFFFFFFFF, 0.55f), true);
        Component sub;
        UUID linkNation = null;
        if (nation) {
            sub = government(picked.id(), nb != null ? nb.government() : t != null ? t.government() : "kingdom").copy();
            if (nb != null && nb.alliance() != null) {
                Briefs.AllianceBrief ab = ClientClaims.alliance(nb.alliance());
                if (ab != null) sub = sub.copy().append(" · ").append(Component.literal(ab.name()));
            }
        } else {
            PoliticsView.TownSummary ts = v.townSummary(picked.id());
            String level = ts != null ? ts.level() : t != null ? t.level() : "";
            sub = level.isEmpty() ? Component.empty() : Component.translatable("nationsandwar.level." + level.toLowerCase(Locale.ROOT));
            if (tb != null && tb.nation() != null) linkNation = tb.nation();
        }
        Draw.text(g, font, Draw.fit(font, sub, (int) ((hw / 2f - 30) / 0.75f)), tx, hy + 19, 0.75f, Draw.MUTED, false);
        if (linkNation != null) {
            Briefs.NationBrief ln = ClientClaims.nation(linkNation);
            Briefs.ProvinceBrief pb = tb.province() == null ? null : ClientClaims.province(tb.province());
            Component link = Component.literal("› " + (ln == null ? "?" : ln.name()) + (pb == null ? "" : " · " + pb.name()));
            float lx = tx + (sub.getString().isEmpty() ? 0 : font.width(sub) * 0.75f + 5);
            int lw = (int) (font.width(link) * 0.75f);
            boolean hover = ui.over((int) lx, hy + 18, lw, 8);
            Draw.text(g, font, link, lx, hy + 19, 0.75f, hover ? Draw.GOLD : Draw.INFO, false);
            UUID go = linkNation;
            ui.zone((int) lx, hy + 18, lw, 8, () -> focus("nation", go));
            ui.tip((int) lx, hy + 18, lw, 8, tr("open_nation"));
        }
        relationChips(g, v, nb, tb, tx, hy + 28);
        stats(g, v, nation, nb, tb, t, hx + cw - 8, hy + 7);
    }

    private void relationChips(GuiGraphics g, PoliticsView v, Briefs.NationBrief nb, Briefs.TownBrief tb, int cx, int cy) {
        UUID nation = nb != null ? nb.id() : tb != null ? tb.nation() : null;
        String rel;
        if (tb != null && v.town() != null && tb.id().equals(v.town().id())) rel = "self";
        else {
            PoliticsView.NationSummary s = nation == null ? null : v.nationSummary(nation);
            rel = s == null ? "neutral" : s.relation();
        }
        cx += ui.chip(g, cx, cy, tr("relation." + rel), DiplomacyTab.relationColor(rel)) + 3;
        if (nation != null && ClientClaims.managed(nation)) cx += managedBadge(g, cx, cy) + 3;
        boolean npc = nb != null ? nb.npc() : tb != null && tb.npc();
        if (npc) ui.chip(g, cx, cy, tr("npc"), Draw.INFO);
    }

    /** Right-aligned facts: the leader or mayor, and the counts. */
    private void stats(GuiGraphics g, PoliticsView v, boolean nation, Briefs.NationBrief nb, Briefs.TownBrief tb, TreeView t, int right, int sy) {
        Component who, counts;
        if (nation) {
            PoliticsView.NationSummary s = v.nationSummary(picked.id());
            String leader = s != null ? s.leader() : nb != null ? nb.leader() : "";
            String title = ClientClaims.titles(picked.id(), nb != null ? nb.government() : "kingdom") + ".leader";
            who = Component.translatable(title).append(": ").append(leader.isEmpty() ? tr("nobody") : Component.literal(leader));
            int towns = s != null ? s.towns() : 0, citizens = s != null ? s.citizens() : t != null ? t.members().size() : 0;
            counts = tr("towns_citizens", towns, citizens);
        } else {
            PoliticsView.TownSummary s = v.townSummary(picked.id());
            String mayor = s != null ? s.mayor() : tb != null ? tb.mayor() : "";
            who = Component.translatable("nationsandwar.info.mayor").append(": ").append(mayor.isEmpty() ? tr("nobody") : Component.literal(mayor));
            int residents = s != null ? s.residents() : t != null ? t.members().size() : 0, claims = s != null ? s.claims() : 0;
            counts = tr("residents_claims", residents, claims);
        }
        who = Draw.fit(font, who, 170);
        g.drawString(font, who, right - font.width(who), sy, Draw.TEXT, false);
        Draw.text(g, font, counts, right - font.width(counts) * 0.75f, sy + 12, 0.75f, Draw.MUTED, false);
        if (t != null && t.exists()) {
            Component ranks = tr("n_ranks", t.roles().size());
            Draw.text(g, font, ranks, right - font.width(ranks) * 0.75f, sy + 22, 0.75f, 0xFF6A7384, false);
        }
    }

    // --- navigation tree ---------------------------------------------------------------------------------------------------

    private void nav(GuiGraphics g, PoliticsView v, int nx, int ny, int nw) {
        Draw.inset(g, nx, ny, nw, h);
        if (!navOpen) {
            ui.button(g, nx + 2, ny + 2, 12, 13, Component.literal("»"), Ui.Style.NORMAL, null, () -> navOpen = true);
            ui.tip(nx + 2, ny + 2, 12, 13, tr("show_list"));
            Draw.text(g, font, tr("tab.atlas"), nx + 5, ny + 22, 0.75f, Draw.MUTED, false);
            return;
        }
        place(search, nx + 2, ny + 2, nw - 18);
        ui.button(g, nx + nw - 15, ny + 2, 13, 13, Component.literal("«"), Ui.Style.NORMAL, null, () -> navOpen = false);
        ui.tip(nx + nw - 15, ny + 2, 13, 13, tr("hide_list"));
        String q = search.getValue().trim().toLowerCase(Locale.ROOT);
        List<Nav> roots = build(v);
        List<Nav> rows = new ArrayList<>();
        List<Integer> depths = new ArrayList<>();
        for (Nav r : roots) flatten(r, 0, q, rows, depths);
        int top = ny + 18, lh = h - 19;
        navScroll.content = rows.size() * ROW + 4;
        ui.scrollZone(nx, top, nw, lh, navScroll);
        ui.clip(g, nx + 1, top, nw - 2, lh - 1);
        int ry = top + 1 - navScroll.offset;
        for (int i = 0; i < rows.size(); i++) {
            if (ry > top - ROW && ry < top + lh) navRow(g, rows.get(i), depths.get(i), nx + 1, ry, nw - 5, !q.isEmpty());
            ry += ROW;
        }
        if (rows.isEmpty()) g.drawCenteredString(font, tr("no_match"), nx + nw / 2, top + 10, Draw.MUTED);
        ui.unclip(g);
        ui.scrollbar(g, nx + nw - 3, top, lh - 1, navScroll);
    }

    private void flatten(Nav n, int depth, String q, List<Nav> out, List<Integer> depths) {
        if (!q.isEmpty() && !n.matches(q)) return;
        out.add(n);
        depths.add(depth);
        boolean open = n.kind == Nav.SECTION || !q.isEmpty() || expanded.contains(n.key);
        if (open) for (Nav k : n.kids) flatten(k, n.kind == Nav.SECTION ? depth : depth + 1, q, out, depths);
    }

    private void navRow(GuiGraphics g, Nav n, int depth, int rx, int ry, int rw, boolean searching) {
        if (n.kind == Nav.SECTION) {
            Component label = Component.literal(n.name.toUpperCase(Locale.ROOT));
            Draw.text(g, font, label, rx + 4, ry + 4, 0.65f, Draw.GOLD, false);
            int lw = (int) (font.width(label) * 0.65f) + 8;
            g.fill(rx + lw, ry + 7, rx + rw, ry + 8, 0x30E3B556);
            return;
        }
        boolean sel = picked != null && (n.kind == Nav.NATION && picked.scope().equals("nation") || n.kind == Nav.TOWN && picked.scope().equals("town"))
                && n.id.equals(picked.id());
        Runnable click = switch (n.kind) {
            case Nav.ALLIANCE -> () -> toggle(n.key);
            case Nav.PROVINCE -> () -> focusProvince(n.nation, n.id);
            case Nav.NATION -> () -> focus("nation", n.id);
            default -> () -> focus("town", n.id);
        };
        ui.row(g, rx, ry, rw, ROW, sel, click);
        int ix = rx + 3 + depth * 8;
        if (!n.kids.isEmpty()) {
            boolean open = searching || expanded.contains(n.key);
            boolean hover = ui.over(ix - 2, ry, 10, ROW);
            Draw.chevron(g, ix + 3, ry + 6.5f, 5, open, hover ? Draw.GOLD : Draw.MUTED);
            if (!searching) ui.zone(ix - 2, ry, 10, ROW, () -> toggle(n.key));
        }
        ix += 9;
        int c = Draw.opaque(n.color);
        if (n.kind == Nav.ALLIANCE) {
            Draw.quad(g, ix + 3.5f, ry + 2, ix + 7, ry + 6.5f, ix + 3.5f, ry + 11, ix, ry + 6.5f, c);
        } else if (n.kind == Nav.PROVINCE) {
            Draw.frame(g, ix + 1, ry + 3, 6, 7, c);
        } else {
            int s = n.kind == Nav.NATION ? 7 : 5, o = (7 - s) / 2;
            g.fillGradient(ix + o, ry + 3 + o, ix + o + s, ry + 3 + o + s, Draw.mix(c, 0xFFFFFFFF, 0.2f), Draw.shade(c, 0.7f));
        }
        ix += 10;
        float right = rx + rw - 2;
        if (n.managed) right -= ui.chip(g, right - (font.width(Component.translatable("nationsandwar.managed.badge")) + 4) * 0.6f, ry + 3.5f,
                Component.translatable("nationsandwar.managed.badge"), Draw.MANAGED, 0.6f) + 2;
        int tc = n.mine ? Draw.GOLD : n.kind == Nav.ALLIANCE ? Draw.mix(c, 0xFFFFFFFF, 0.5f) : n.kind == Nav.PROVINCE ? 0xFFB8BFCC : Draw.TEXT;
        String label = (n.capital ? "★ " : "") + n.name + (n.npc && !n.managed ? " ⚙" : "");
        boolean small = n.kind == Nav.TOWN || n.kind == Nav.PROVINCE;
        float scale = small ? 0.85f : 1;
        Component text = Draw.fit(font, Component.literal(label), (int) ((right - ix - 2) / scale));
        if (small) Draw.text(g, font, text, ix, ry + 3, scale, tc, false);
        else g.drawString(font, text, ix, ry + 2, tc, false);
        if (font.width(label) * scale > right - ix - 2) ui.tip(rx, ry, rw, ROW, Component.literal(n.name));
    }

    private void toggle(String key) {
        if (!expanded.remove(key)) expanded.add(key);
    }

    /** The navigation tree from the public briefs: alliances, then independent nations, then free towns. */
    private List<Nav> build(PoliticsView v) {
        UUID myTown = v.town() == null ? null : v.town().id(), myNation = v.nation() == null ? null : v.nation().id();
        UUID myAlliance = v.alliance() == null ? null : v.alliance().id();
        Map<UUID, List<Briefs.TownBrief>> townsOf = new HashMap<>();
        List<Briefs.TownBrief> free = new ArrayList<>();
        for (Briefs.TownBrief t : ClientClaims.towns()) {
            if (t.nation() != null) townsOf.computeIfAbsent(t.nation(), k -> new ArrayList<>()).add(t);
            else free.add(t);
        }
        Map<UUID, List<Briefs.NationBrief>> byAlliance = new LinkedHashMap<>();
        List<Briefs.NationBrief> loose = new ArrayList<>();
        List<Briefs.NationBrief> nations = new ArrayList<>(ClientClaims.nations());
        nations.sort(Comparator.comparing((Briefs.NationBrief n) -> !n.id().equals(myNation)).thenComparing(Briefs.NationBrief::name, String.CASE_INSENSITIVE_ORDER));
        for (Briefs.NationBrief n : nations) {
            if (n.alliance() != null) byAlliance.computeIfAbsent(n.alliance(), k -> new ArrayList<>()).add(n);
            else loose.add(n);
        }
        List<Nav> roots = new ArrayList<>();
        if (!byAlliance.isEmpty()) {
            Nav sec = new Nav(Nav.SECTION, "s.alliances", null, null, tr("alliances").getString(), 0);
            List<UUID> ids = new ArrayList<>(byAlliance.keySet());
            ids.sort(Comparator.comparing((UUID id) -> !id.equals(myAlliance)).thenComparing(id -> allianceName(v, id), String.CASE_INSENSITIVE_ORDER));
            for (UUID id : ids) {
                Briefs.AllianceBrief ab = ClientClaims.alliance(id);
                Nav a = new Nav(Nav.ALLIANCE, "a" + id, id, null, allianceName(v, id), ab != null ? ab.color() : 0x888888);
                a.mine = id.equals(myAlliance);
                for (Briefs.NationBrief n : byAlliance.get(id)) a.kids.add(nationNav(n, townsOf, myNation, myTown));
                sec.kids.add(a);
            }
            roots.add(sec);
        }
        if (!loose.isEmpty()) {
            Nav sec = new Nav(Nav.SECTION, "s.nations", null, null, tr("independent_nations").getString(), 0);
            for (Briefs.NationBrief n : loose) sec.kids.add(nationNav(n, townsOf, myNation, myTown));
            roots.add(sec);
        }
        if (!free.isEmpty()) {
            Nav sec = new Nav(Nav.SECTION, "s.towns", null, null, tr("free_towns").getString(), 0);
            free.sort(Comparator.comparing((Briefs.TownBrief t) -> !t.id().equals(myTown)).thenComparing(Briefs.TownBrief::name, String.CASE_INSENSITIVE_ORDER));
            for (Briefs.TownBrief t : free) sec.kids.add(townNav(t, null, myTown));
            roots.add(sec);
        }
        return roots;
    }

    private static String allianceName(PoliticsView v, UUID id) {
        Briefs.AllianceBrief ab = ClientClaims.alliance(id);
        if (ab != null) return ab.name();
        for (PoliticsView.AllianceView a : v.alliances()) if (a.id().equals(id)) return a.name();
        return "?";
    }

    private Nav nationNav(Briefs.NationBrief n, Map<UUID, List<Briefs.TownBrief>> townsOf, UUID myNation, UUID myTown) {
        Nav nav = new Nav(Nav.NATION, "n" + n.id(), n.id(), n.id(), n.name(), n.color());
        nav.managed = n.managed();
        nav.npc = n.npc();
        nav.mine = n.id().equals(myNation);
        List<Briefs.TownBrief> towns = new ArrayList<>(townsOf.getOrDefault(n.id(), List.of()));
        towns.sort(Comparator.comparing((Briefs.TownBrief t) -> !t.id().equals(n.capital())).thenComparing(Briefs.TownBrief::name, String.CASE_INSENSITIVE_ORDER));
        Map<UUID, Nav> provinces = new LinkedHashMap<>();
        List<Nav> loose = new ArrayList<>();
        for (Briefs.TownBrief t : towns) {
            Nav tn = townNav(t, n, myTown);
            if (t.province() == null) {
                loose.add(tn);
                continue;
            }
            Nav p = provinces.computeIfAbsent(t.province(), id -> {
                Briefs.ProvinceBrief pb = ClientClaims.province(id);
                return new Nav(Nav.PROVINCE, "p" + id, id, n.id(), pb == null ? "?" : pb.name(), pb == null ? n.color() : pb.color());
            });
            p.kids.add(tn);
        }
        List<Nav> ps = new ArrayList<>(provinces.values());
        ps.sort(Comparator.comparing(p -> p.name, String.CASE_INSENSITIVE_ORDER));
        nav.kids.addAll(ps);
        nav.kids.addAll(loose);
        return nav;
    }

    private static Nav townNav(Briefs.TownBrief t, Briefs.NationBrief n, UUID myTown) {
        Nav nav = new Nav(Nav.TOWN, "t" + t.id(), t.id(), t.nation(), t.name(), t.color());
        nav.managed = t.managed();
        nav.npc = t.npc();
        nav.mine = t.id().equals(myTown);
        nav.capital = n != null && t.id().equals(n.capital());
        return nav;
    }

    // --- the tree's host ---------------------------------------------------------------------------------------------------

    private Rights rights() {
        PoliticsView v = ClientPolitics.view();
        return v == null ? new Rights(null, null) : new Rights(ClientPolitics.tree(), v);
    }

    @Override
    public boolean canMove(PoliticsView.RoleView role) {
        return role.parent() >= 0 && rights().manages(role);
    }

    @Override
    public boolean canAssignFrom(PoliticsView.RoleView role) {
        return "NORMAL".equals(role.kind()) && rights().manages(role);
    }

    @Override
    public boolean canAssignTo(PoliticsView.RoleView role) {
        return "NORMAL".equals(role.kind()) && rights().manages(role);
    }

    @Override
    public boolean canGovern() {
        return rights().governs();
    }

    @Override
    public void select(RoleTree.Node node) {
    }

    @Override
    public void move(PoliticsView.RoleView role, PoliticsView.RoleView parent) {
        TreeView t = ClientPolitics.tree();
        if (t != null && t.mine()) act("role.move", "scope", t.scope(), "id", role.id(), "parent", parent.id());
    }

    @Override
    public void assign(UUID player, PoliticsView.RoleView role) {
        TreeView t = ClientPolitics.tree();
        if (t != null && t.mine()) act("role.assign", "scope", t.scope(), "id", role.id(), "player", player);
    }

    @Override
    public void governor(UUID player, PoliticsView.ProvinceView province) {
        act("province.governor", "province", province.id(), "player", player);
    }

    // --- input -------------------------------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return treeShown && tree != null && tree.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return treeShown && tree != null && tree.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        return tree != null && tree.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        return treeShown && tree != null && tree.mouseScrolled(mx, my, delta);
    }
}
