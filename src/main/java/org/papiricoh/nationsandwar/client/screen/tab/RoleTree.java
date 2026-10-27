package org.papiricoh.nationsandwar.client.screen.tab;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.papiricoh.nationsandwar.client.gui.Draw;
import org.papiricoh.nationsandwar.client.gui.Skins;
import org.papiricoh.nationsandwar.client.gui.Ui;
import org.papiricoh.nationsandwar.view.PoliticsView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A town's or nation's hierarchy drawn as a 2.5D org chart on a dotted war-table: every rank is a raised card with
 * thickness and a drop shadow, wearing its colour and the 3D busts of the people who hold it, joined to its parent by
 * shadowed elbow connectors. The nation's Governor rank branches into one card per province with its governor.
 *
 * <p>Drag the table to pan, scroll to zoom toward the mouse (the same pan/zoom maths as the Hunter's Book roadmap), click
 * a card to select it, drag a card onto another to move it there, and drag a person's bust onto another rank to give it
 * to them (or onto a province to make them its governor). Cards glide to their new places when the tree changes.
 */
public final class RoleTree {
    /** Card size and the gaps between cards, in table units (one unit is one GUI pixel at zoom 1). */
    static final float CW = 108, CH = 64, HG = 14, VG = 30, DEPTH = 6;
    private static final float MIN_ZOOM = 0.3f, MAX_ZOOM = 2.4f, BUST = 16, BUST_STEP = 20;

    /** What the tree asks of its tab: permissions and the actions its gestures trigger. */
    public interface Host {
        boolean canMove(PoliticsView.RoleView role);

        boolean canAssignFrom(PoliticsView.RoleView role);

        boolean canAssignTo(PoliticsView.RoleView role);

        boolean canGovern();

        void select(Node node);

        void move(PoliticsView.RoleView role, PoliticsView.RoleView parent);

        void assign(UUID player, PoliticsView.RoleView role);

        void governor(UUID player, PoliticsView.ProvinceView province);
    }

    /** A card: a rank, or (under the nation's Governor rank) a province. */
    public static final class Node {
        public final String key;
        public final PoliticsView.RoleView role;
        public final PoliticsView.ProvinceView province;
        final List<Node> kids = new ArrayList<>();
        Node parent;
        float tx, ty, width;

        Node(PoliticsView.RoleView role, PoliticsView.ProvinceView province) {
            this.role = role;
            this.province = province;
            this.key = role != null ? "r" + role.id() : "p" + province.id();
        }

        boolean under(Node other) {
            for (Node n = this; n != null; n = n.parent) if (n == other) return true;
            return false;
        }

        int color() {
            return role != null ? role.color() : 0x5C6BC0;
        }
    }

    private final Host host;
    private final Ui ui;
    private final Font font;
    private final List<Node> nodes = new ArrayList<>();
    private final Map<String, float[]> shown = new HashMap<>();
    private final Map<UUID, PoliticsView.Member> people = new HashMap<>();
    private float panX, panY, zoom = 1, minX, maxX, maxY;
    private boolean fitted, panning, dragging;
    private int x0, y0, x1, y1;
    private long lastFrame;
    private String selected;
    private Node press, hovered, drop;
    private UUID pressPerson;
    private double pressX, pressY;

    public RoleTree(Host host, Ui ui, Font font) {
        this.host = host;
        this.ui = ui;
        this.font = font;
    }

    public void select(String key) {
        selected = key;
    }

    public String selected() {
        return selected;
    }

    public Node node(String key) {
        for (Node n : nodes) if (n.key.equals(key)) return n;
        return null;
    }

    // --- layout ------------------------------------------------------------------------------------------------------------

    /** Rebuilds the cards from the roles (and, for a nation, its provinces) and lays them out as a tidy top-down tree. */
    public void build(List<PoliticsView.RoleView> roles, List<PoliticsView.Member> members, List<PoliticsView.ProvinceView> provinces) {
        nodes.clear();
        people.clear();
        for (PoliticsView.Member m : members) people.put(m.id(), m);
        Map<Integer, Node> byId = new HashMap<>();
        Node root = null;
        List<PoliticsView.RoleView> sorted = new ArrayList<>(roles);
        sorted.sort(Comparator.comparingInt(PoliticsView.RoleView::id));
        for (PoliticsView.RoleView r : sorted) {
            Node n = new Node(r, null);
            byId.put(r.id(), n);
            nodes.add(n);
        }
        for (Node n : nodes) {
            Node p = byId.get(n.role.parent());
            if (n.role.parent() < 0 || p == null) {
                if (root == null || n.role.parent() < 0) root = n;
                continue;
            }
            n.parent = p;
            p.kids.add(n);
        }
        if (provinces != null) {
            for (Node n : List.copyOf(nodes)) {
                if (!"GOVERNOR".equals(n.role.kind())) continue;
                List<PoliticsView.ProvinceView> ps = new ArrayList<>(provinces);
                ps.sort(Comparator.comparing(PoliticsView.ProvinceView::name));
                for (PoliticsView.ProvinceView p : ps) {
                    Node pn = new Node(null, p);
                    pn.parent = n;
                    n.kids.add(pn);
                    nodes.add(pn);
                }
            }
        }
        if (root == null) return;
        measure(root);
        place(root, 0, 0);
        minX = Float.MAX_VALUE;
        maxX = -Float.MAX_VALUE;
        maxY = 0;
        for (Node n : nodes) {
            minX = Math.min(minX, n.tx);
            maxX = Math.max(maxX, n.tx + CW);
            maxY = Math.max(maxY, n.ty + CH);
        }
        for (Node n : nodes) shown.putIfAbsent(n.key, new float[]{n.parent == null ? n.tx : n.parent.tx, n.parent == null ? n.ty : n.parent.ty});
    }

    private static float measure(Node n) {
        float sum = 0;
        for (Node k : n.kids) sum += measure(k);
        sum += HG * Math.max(0, n.kids.size() - 1);
        n.width = Math.max(CW, sum);
        return n.width;
    }

    private static void place(Node n, float left, int depth) {
        n.tx = left + (n.width - CW) / 2;
        n.ty = depth * (CH + VG);
        float sum = -HG;
        for (Node k : n.kids) sum += k.width + HG;
        float at = left + (n.width - Math.max(0, sum)) / 2;
        for (Node k : n.kids) {
            place(k, at, depth + 1);
            at += k.width + HG;
        }
    }

    private void fit() {
        float bw = maxX - minX + 24, bh = maxY + 24 + DEPTH;
        zoom = Mth.clamp(Math.min((x1 - x0) / bw, (y1 - y0) / bh), MIN_ZOOM, 1.6f);
        panX = (minX + maxX) / 2;
        panY = (maxY - DEPTH) / 2;
    }

    public void refit() {
        fitted = false;
    }

    private float worldX(double sx) {
        return (float) (panX + (sx - (x0 + x1) / 2.0) / zoom);
    }

    private float worldY(double sy) {
        return (float) (panY + (sy - (y0 + y1) / 2.0) / zoom);
    }

    private void zoomAt(double mx, double my, float to) {
        to = Mth.clamp(to, MIN_ZOOM, MAX_ZOOM);
        float wx = worldX(mx), wy = worldY(my);
        zoom = to;
        panX = (float) (wx - (mx - (x0 + x1) / 2.0) / zoom);
        panY = (float) (wy - (my - (y0 + y1) / 2.0) / zoom);
        clampPan();
    }

    private void clampPan() {
        panX = Mth.clamp(panX, minX, maxX);
        panY = Mth.clamp(panY, 0, Math.max(0, maxY));
    }

    // --- drawing -----------------------------------------------------------------------------------------------------------

    public void render(GuiGraphics g, int rx, int ry, int rw, int rh) {
        x0 = rx;
        y0 = ry;
        x1 = rx + rw;
        y1 = ry + rh;
        if (!fitted && !nodes.isEmpty()) {
            fit();
            fitted = true;
        }
        glide();
        g.fillGradient(rx, ry, x1, y1, 0xFF1B2230, 0xFF10141B);
        ui.clip(g, rx, ry, rw, rh);
        var pose = g.pose();
        pose.pushPose();
        pose.translate((x0 + x1) / 2f, (y0 + y1) / 2f, 0);
        pose.scale(zoom, zoom, 1);
        pose.translate(-panX, -panY, 0);
        dots(g);
        float wmx = worldX(ui.mx), wmy = worldY(ui.my);
        boolean inside = ui.mx >= x0 && ui.mx < x1 && ui.my >= y0 && ui.my < y1;
        hovered = inside && !panning ? hit(wmx, wmy) : null;
        drop = dragging ? dropTarget(hovered) : null;
        for (Node n : nodes) if (n.parent != null) connector(g, n.parent, n);
        for (Node n : nodes) if (!(dragging && pressPerson == null && n == press)) card(g, n, at(n)[0], at(n)[1], false);
        if (dragging && press != null) {
            float dx = (float) ((ui.mx - pressX) / zoom), dy = (float) ((ui.my - pressY) / zoom);
            pose.pushPose();
            pose.translate(0, 0, 150);
            if (pressPerson == null) card(g, press, at(press)[0] + dx, at(press)[1] + dy, true);
            else {
                PoliticsView.Member m = people.get(pressPerson);
                Draw.shadow(g, wmx - 8, wmy - 4, 16, 20, 6);
                Skins.bust(g, pressPerson, m == null ? "" : m.name(), wmx, wmy - 2, BUST * 1.3f, -28, 60);
            }
            pose.popPose();
        }
        pose.popPose();
        g.fillGradient(rx, ry, x1, ry + 10, 0x60000000, 0x00000000);
        g.fillGradient(rx, y1 - 14, x1, y1, 0x00000000, 0x70000000);
        ui.unclip(g);
        overlay(g);
    }

    private float[] at(Node n) {
        float[] p = shown.computeIfAbsent(n.key, k -> new float[]{n.tx, n.ty});
        return p;
    }

    private void glide() {
        long now = Util.getMillis();
        float dt = lastFrame == 0 ? 1 : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        float k = 1 - (float) Math.exp(-dt * 10);
        for (Node n : nodes) {
            float[] p = at(n);
            p[0] += (n.tx - p[0]) * k;
            p[1] += (n.ty - p[1]) * k;
        }
    }

    /** The table's dotted grid, in table space so it pans and zooms with the cards. */
    private void dots(GuiGraphics g) {
        float step = 24, wx0 = worldX(x0), wx1 = worldX(x1), wy0 = worldY(y0), wy1 = worldY(y1), r = 0.8f / Math.min(1, zoom);
        for (float gy = (float) Math.floor(wy0 / step) * step; gy <= wy1; gy += step)
            for (float gx = (float) Math.floor(wx0 / step) * step; gx <= wx1; gx += step)
                Draw.rect(g, gx - r / 2, gy - r / 2, r, r, 0x30A0B0C8);
    }

    private void connector(GuiGraphics g, Node parent, Node child) {
        float[] p = at(parent), c = at(child);
        float px = p[0] + CW / 2, py = p[1] + CH, cx = c[0] + CW / 2, cy = c[1] - DEPTH / 2, mid = py + (cy - py) * 0.5f;
        int color = Draw.opaque(Draw.mix(Draw.mix(parent.color(), 0x7C8698, 0.45f), 0x000000, 0.1f));
        for (int pass = 0; pass < 2; pass++) {
            float o = pass == 0 ? 2.2f : 0;
            int col = pass == 0 ? 0x55000000 : color;
            Draw.line(g, px - o, py + o, px - o, mid + o + 1, 2, col);
            Draw.line(g, Math.min(px, cx) - 1 - o, mid + o, Math.max(px, cx) + 1 - o, mid + o, 2, col);
            Draw.line(g, cx - o, mid + o - 1, cx - o, cy + o, 2, col);
        }
        Draw.rect(g, px - 2.5f, mid - 2.5f, 5, 5, Draw.mix(color, 0xFFFFFFFF, 0.25f));
        Draw.rect(g, cx - 1.5f, cy - 1.5f, 3, 3, color);
    }

    private void card(GuiGraphics g, Node n, float x, float y, boolean ghost) {
        boolean sel = n.key.equals(selected), hov = n == hovered && !dragging, target = n == drop;
        float lift = ghost ? 6 : sel || hov ? 2 : 0;
        y -= lift;
        int rc = Draw.opaque(n.color());
        Draw.shadow(g, x, y + lift, CW, CH, 7 + lift * 2);
        int top = n.province != null ? Draw.mix(0xFF3A3428, rc, 0.18f) : Draw.mix(0xFF2B323E, rc, 0.30f);
        int bottom = n.province != null ? 0xFF221E17 : Draw.mix(0xFF161A21, rc, 0.12f);
        Draw.slab(g, x, y, CW, CH, DEPTH, top, bottom);
        Draw.rect(g, x, y, CW, 13, Draw.mix(rc, 0xFFFFFFFF, 0.12f), Draw.shade(rc, 0.72f));
        Draw.rect(g, x, y + 13, CW, 1, 0x60000000);
        Component name = n.role != null ? Tab.roleName(n.role.name()) : Component.literal(n.province.name());
        boolean dark = Draw.luma(rc) > 0.62f;
        int nx = n.role != null && !"NORMAL".equals(n.role.kind()) || n.province != null ? 15 : 4;
        g.drawString(font, Draw.fit(font, name, (int) CW - nx - 4), (int) x + nx, (int) y + 3, dark ? 0xFF1A1A1A : 0xFFFFFFFF, !dark);
        if (n.role != null && "LEADER".equals(n.role.kind())) Draw.icon(g, Draw.Icon.CROWN, x - 1, y - 3, 15);
        else if (n.role != null && "GOVERNOR".equals(n.role.kind()) || n.province != null) Draw.icon(g, Draw.Icon.SCROLL, x, y - 1, 14);
        List<UUID> holders = holders(n);
        if (holders.isEmpty()) vacant(g, x, y);
        else busts(g, n, holders, x, y, ghost);
        footer(g, n, x, y);
        if (sel) {
            Draw.frame(g, (int) x - 1, (int) y - 1, (int) CW + 2, (int) CH + 2, Draw.GOLD);
            Draw.line(g, x + CW + 1, y - 1, x + CW + DEPTH + 1, y - DEPTH - 1, 1, Draw.GOLD);
        }
        if (target) Draw.frame(g, (int) x - 2, (int) y - 2, (int) CW + 4, (int) CH + 4, Draw.GOOD);
        else if (dragging && n == hovered && !ghost) Draw.frame(g, (int) x - 2, (int) y - 2, (int) CW + 4, (int) CH + 4, Draw.BAD);
    }

    private List<UUID> holders(Node n) {
        if (n.role != null) return n.role.holders();
        return n.province.governor() == null ? List.of() : List.of(n.province.governor());
    }

    private String name(UUID id, Node n) {
        PoliticsView.Member m = people.get(id);
        if (m != null) return m.name();
        return n.province != null ? n.province.governorName() : "";
    }

    private void vacant(GuiGraphics g, float x, float y) {
        float cx = x + CW / 2, cy = y + 20;
        int c = 0x30FFFFFF;
        Draw.rect(g, cx - 4, cy, 8, 8, c);
        Draw.rect(g, cx - 7, cy + 9, 14, 9, c);
        Component v = Component.translatable("nationsandwar.gui.vacant");
        Draw.text(g, font, v, cx - font.width(v) * 0.3f, y + 43, 0.6f, 0xFF7A8396, false);
    }

    private void busts(GuiGraphics g, Node n, List<UUID> holders, float x, float y, boolean ghost) {
        int max = holders.size() > 5 ? 4 : holders.size();
        float total = max * BUST_STEP + (holders.size() > max ? 14 : 0), left = x + (CW - total) / 2 + BUST_STEP / 2;
        for (int i = 0; i < max; i++) {
            UUID id = holders.get(i);
            float bx = left + i * BUST_STEP, neck = y + 26;
            if (!(dragging && id.equals(pressPerson))) {
                Draw.rect(g, bx - 8, y + 39, 16, 2, 0x40000000);
                Skins.bust(g, id, name(id, n), bx, neck, BUST, -28, ghost ? 20 : 12);
            }
            PoliticsView.Member m = people.get(id);
            if (m != null && m.online()) Draw.rect(g, bx + 6, y + 16, 3, 3, Draw.GOOD);
            float scale = max == 1 ? 0.7f : 0.5f;
            Component label = Draw.fit(font, Component.literal(name(id, n)), (int) ((max == 1 ? CW - 10 : BUST_STEP) / scale));
            Draw.text(g, font, label, bx - font.width(label) * scale / 2, y + 44, scale, Draw.TEXT, false);
            if (!ghost && !dragging) {
                float wmx = worldX(ui.mx), wmy = worldY(ui.my);
                if (wmx >= bx - BUST_STEP / 2 && wmx < bx + BUST_STEP / 2 && wmy >= y + 14 && wmy < y + 50 && ui.mx >= x0 && ui.mx < x1 && ui.my >= y0 && ui.my < y1)
                    ui.tip(ui.mx, ui.my, 1, 1, Component.literal(name(id, n)),
                            Component.translatable("nationsandwar.gui." + (m != null && m.online() ? "online" : "offline")).withStyle(s -> s.withColor(0x8C95A7)),
                            Component.translatable("nationsandwar.gui.drag_person").withStyle(s -> s.withColor(0x6A7384)));
            }
        }
        if (holders.size() > max) {
            Component more = Component.literal("+" + (holders.size() - max));
            g.drawString(font, more, (int) (left + max * BUST_STEP - BUST_STEP / 2 + 2), (int) y + 24, Draw.MUTED, false);
        }
    }

    private void footer(GuiGraphics g, Node n, float x, float y) {
        Component info;
        if (n.province != null) info = Component.translatable("nationsandwar.gui.n_towns", n.province.towns().size());
        else if ("LEADER".equals(n.role.kind())) info = Component.translatable("nationsandwar.gui.all_perms");
        else info = Component.translatable("nationsandwar.gui.n_perms", Integer.bitCount(n.role.perms()));
        Draw.rect(g, x, y + CH - 9, CW, 9, 0x40000000);
        Draw.text(g, font, info, x + 4, y + CH - 7, 0.55f, 0xFF9AA3B5, false);
        if (n.role != null) {
            Component c = Component.literal(String.valueOf(n.role.holders().size()));
            Draw.text(g, font, c, x + CW - 4 - font.width(c) * 0.55f, y + CH - 7, 0.55f, 0xFF9AA3B5, false);
        }
    }

    private void overlay(GuiGraphics g) {
        int bx = x1 - 4;
        bx -= ui.button(g, bx - 14, y0 + 4, 14, 13, Component.literal("+"), Ui.Style.NORMAL, null, () -> zoomAt((x0 + x1) / 2.0, (y0 + y1) / 2.0, zoom * 1.25f)) + 2;
        bx -= ui.button(g, bx - 14, y0 + 4, 14, 13, Component.literal("−"), Ui.Style.NORMAL, null, () -> zoomAt((x0 + x1) / 2.0, (y0 + y1) / 2.0, zoom / 1.25f)) + 2;
        ui.button(g, bx - 28, y0 + 4, 28, 13, Component.translatable("nationsandwar.gui.fit"), Ui.Style.NORMAL, null, this::fit);
        Component hint = Component.translatable("nationsandwar.gui.tree_hint");
        Draw.text(g, font, Draw.fit(font, hint, (int) ((x1 - x0 - 8) / 0.6f)), x0 + 4, y1 - 8, 0.6f, 0xFF7A8396, false);
    }

    // --- input -------------------------------------------------------------------------------------------------------------

    private Node hit(float wx, float wy) {
        for (int i = nodes.size() - 1; i >= 0; i--) {
            Node n = nodes.get(i);
            float[] p = at(n);
            if (wx >= p[0] && wx < p[0] + CW + DEPTH && wy >= p[1] - DEPTH && wy < p[1] + CH) return n;
        }
        return null;
    }

    /** The holder whose bust is under a table point on a card, or null. */
    private UUID personAt(Node n, float wx, float wy) {
        List<UUID> holders = holders(n);
        int max = holders.size() > 5 ? 4 : holders.size();
        float[] p = at(n);
        float total = max * BUST_STEP + (holders.size() > max ? 14 : 0), left = p[0] + (CW - total) / 2;
        if (wy < p[1] + 14 || wy >= p[1] + 50) return null;
        int i = (int) Math.floor((wx - left) / BUST_STEP);
        return i >= 0 && i < max ? holders.get(i) : null;
    }

    private Node dropTarget(Node over) {
        if (over == null || press == null || over == press) return null;
        if (pressPerson != null) {
            if (over.province != null) return host.canGovern() ? over : null;
            return host.canAssignTo(over.role) && !over.role.holders().contains(pressPerson) ? over : null;
        }
        if (over.province != null || over.under(press)) return null;
        return press.parent == over ? null : over;
    }

    public boolean inside(double mx, double my) {
        return mx >= x0 && mx < x1 && my >= y0 && my < y1;
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (!inside(mx, my) || button != 0) return false;
        float wx = worldX(mx), wy = worldY(my);
        press = hit(wx, wy);
        pressPerson = press == null ? null : personAt(press, wx, wy);
        pressX = mx;
        pressY = my;
        dragging = false;
        panning = press == null;
        return true;
    }

    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button != 0) return false;
        if (panning) {
            panX -= (float) (dx / zoom);
            panY -= (float) (dy / zoom);
            clampPan();
            return true;
        }
        if (press == null) return false;
        if (!dragging && Math.hypot(mx - pressX, my - pressY) > 4) {
            boolean may = pressPerson != null ? press.province == null && host.canAssignFrom(press.role)
                    : press.role != null && host.canMove(press.role);
            if (may) dragging = true;
        }
        return true;
    }

    public boolean mouseReleased(double mx, double my, int button) {
        if (button != 0) return false;
        boolean handled = panning || press != null;
        if (dragging && drop != null) {
            if (pressPerson != null && drop.province != null) host.governor(pressPerson, drop.province);
            else if (pressPerson != null) host.assign(pressPerson, drop.role);
            else host.move(press.role, drop.role);
        } else if (!dragging && press != null) {
            selected = press.key;
            host.select(press);
        }
        press = null;
        pressPerson = null;
        dragging = panning = false;
        return handled;
    }

    public boolean mouseScrolled(double mx, double my, double delta) {
        if (!inside(mx, my)) return false;
        zoomAt(mx, my, zoom * (delta > 0 ? 1.15f : 1 / 1.15f));
        return true;
    }
}
