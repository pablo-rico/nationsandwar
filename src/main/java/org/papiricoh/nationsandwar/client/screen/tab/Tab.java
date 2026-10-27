package org.papiricoh.nationsandwar.client.screen.tab;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.papiricoh.nationsandwar.api.client.ManagedNationScreens;
import org.papiricoh.nationsandwar.client.ClientClaims;
import org.papiricoh.nationsandwar.client.ClientPolitics;
import org.papiricoh.nationsandwar.client.gui.Draw;
import org.papiricoh.nationsandwar.client.gui.Ui;
import org.papiricoh.nationsandwar.client.screen.NationScreen;
import org.papiricoh.nationsandwar.client.screen.modal.ConfirmModal;
import org.papiricoh.nationsandwar.client.screen.modal.Modal;
import org.papiricoh.nationsandwar.model.Perm;
import org.papiricoh.nationsandwar.view.Briefs;
import org.papiricoh.nationsandwar.view.PoliticsView;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * One page of the management GUI. Each frame {@link #render} draws it from the latest view inside the content rectangle
 * ({@link #x}, {@link #y}, {@link #w}, {@link #h}) and registers its click zones with {@link #ui}; text fields are made
 * once in {@link #init} and placed every frame with {@link #place} (a field not placed in a frame is hidden).
 */
public abstract class Tab {
    protected static final String K = "nationsandwar.gui.";
    protected final NationScreen screen;
    protected Ui ui;
    protected Font font;
    protected int x, y, w, h;

    protected Tab(NationScreen screen) {
        this.screen = screen;
    }

    public abstract String id();

    public abstract Draw.Icon icon();

    /** Null when the tab can be used, else why not (shown greyed out in the rail). */
    public Component unavailable(PoliticsView v) {
        return null;
    }

    public void bind(Ui ui, Font font, int x, int y, int w, int h) {
        this.ui = ui;
        this.font = font;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public void init() {
    }

    public abstract void render(GuiGraphics g, PoliticsView v, float partial);

    /** A fresh view arrived (drop selections that no longer exist). */
    public void viewChanged(PoliticsView v) {
    }

    public boolean mouseClicked(double mx, double my, int button) {
        return false;
    }

    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return false;
    }

    public boolean mouseReleased(double mx, double my, int button) {
        return false;
    }

    public boolean mouseScrolled(double mx, double my, double delta) {
        return false;
    }

    // --- helpers -----------------------------------------------------------------------------------------------------------

    protected EditBox field(String key, int maxLength, Component hint, boolean digits) {
        return screen.field(id() + "." + key, maxLength, hint, digits);
    }

    protected void place(EditBox box, int fx, int fy, int fw) {
        box.setX(fx);
        box.setY(fy);
        box.setWidth(fw);
        box.visible = true;
    }

    protected static int number(EditBox box) {
        try {
            return Integer.parseInt(box.getValue().trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static MutableComponent tr(String key, Object... args) {
        return Component.translatable(K + key, args);
    }

    /** A role's name: "@key" names are lang keys. */
    public static Component roleName(String name) {
        return name.startsWith("@") ? Component.translatable(name.substring(1)) : Component.literal(name);
    }

    public static boolean has(int mask, Perm p) {
        return (mask & 1 << p.ordinal()) != 0;
    }

    /** Null when allowed, else the "needs …" reason for a greyed-out control. */
    public static Component need(int mask, Perm p) {
        return has(mask, p) ? null : Component.translatable(K + "needs", Component.translatable(p.langKey()));
    }

    public static Component needIf(boolean ok, String reasonKey) {
        return ok ? null : Component.translatable(K + reasonKey);
    }

    /** Whether a player holds the root (leader) role of a tree. */
    public static boolean leads(List<PoliticsView.RoleView> roles, UUID me) {
        for (PoliticsView.RoleView r : roles) if (r.parent() < 0) return r.holders().contains(me);
        return false;
    }

    /** Builds action arguments from key/value pairs (String, Integer, Boolean, Byte, UUID or Tag values). */
    public static CompoundTag args(Object... kv) {
        CompoundTag t = new CompoundTag();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            String k = (String) kv[i];
            Object v = kv[i + 1];
            if (v instanceof Integer n) t.putInt(k, n);
            else if (v instanceof Boolean b) t.putBoolean(k, b);
            else if (v instanceof Byte b) t.putByte(k, b);
            else if (v instanceof Tag tag) t.put(k, tag);
            else if (v != null) t.putString(k, v.toString());
        }
        return t;
    }

    public static void act(String action, Object... kv) {
        ClientPolitics.act(action, args(kv));
    }

    /** The colours offered for towns, nations and roles. */
    public static final int[] PALETTE = {0xC62828, 0xE57373, 0xEF6C00, 0xFFB300, 0xFFD54F, 0x9CCC65, 0x43A047, 0x26A69A, 0x4FC3F7, 0x1E88E5,
            0x5C6BC0, 0x8E24AA, 0xEC407A, 0x8D6E63, 0x9E9E9E, 0x424242};

    /** A raised 2.5D card with a colour stripe, an icon and a title; returns the y where its body starts. */
    protected int card(GuiGraphics g, int cx, int cy, int cw, int ch, int color, Draw.Icon icon, Component title) {
        Draw.shadow(g, cx, cy, cw, ch, 6);
        Draw.slab(g, cx, cy, cw, ch, 3, 0xFF29313E, 0xFF1C222B);
        g.fill(cx, cy, cx + cw, cy + 2, Draw.opaque(color));
        Draw.icon(g, icon, cx + 4, cy + 5);
        g.drawString(font, Draw.fit(font, title, cw - 26), cx + 23, cy + 9, Draw.mix(Draw.opaque(color), 0xFFFFFFFF, 0.35f), true);
        return cy + 25;
    }

    /** Two rows of colour swatches fitted to a width; {@code pick} is null when the viewer may not change it. Returns the height. */
    protected int palette(GuiGraphics g, int sx, int sy, int width, int current, java.util.function.IntConsumer pick) {
        return palette(ui, g, sx, sy, width, 11, current, pick);
    }

    /** {@link #palette} for any {@link Ui} (modals), with swatches up to {@code maxSize} across. */
    public static int palette(Ui ui, GuiGraphics g, int sx, int sy, int width, int maxSize, int current, java.util.function.IntConsumer pick) {
        int size = Math.max(5, Math.min(maxSize, width / 8 - 1));
        for (int i = 0; i < PALETTE.length; i++) {
            int c = PALETTE[i];
            ui.swatch(g, sx + i % 8 * (size + 1), sy + i / 8 * (size + 1), size, c, (current & 0xFFFFFF) == c, pick != null, pick == null ? null : () -> pick.accept(c));
        }
        return 2 * (size + 1);
    }

    /** An amount field with Deposit and Withdraw buttons on one row. */
    protected void bank(GuiGraphics g, EditBox amount, int bx, int by, int bw, Component withdrawDenied, String deposit, String withdraw) {
        int btn = 26, fw = bw - 2 * btn - 4;
        place(amount, bx, by, fw);
        int n = number(amount);
        Component none = n <= 0 ? tr("type_amount") : null;
        ui.button(g, bx + fw + 2, by, btn, 13, Component.literal("+◆"), Ui.Style.GOOD, none, () -> act(deposit, "amount", n));
        ui.tip(bx + fw + 2, by, btn, 13, tr("deposit"));
        Component out = none != null ? none : withdrawDenied;
        ui.button(g, bx + bw - btn, by, btn, 13, Component.literal("−◆"), Ui.Style.GOLD, out, () -> act(withdraw, "amount", n));
        if (out == null) ui.tip(bx + bw - btn, by, btn, 13, tr("withdraw"));
    }

    /** A section heading with a hairline rule. */
    protected void heading(GuiGraphics g, Component text, int hx, int hy, int hw) {
        g.drawString(font, text, hx, hy, Draw.GOLD, false);
        int tw = font.width(text) + 4;
        if (hw > tw) g.fill(hx + tw, hy + 4, hx + hw, hy + 5, 0x40E3B556);
    }

    // --- action bars and modals --------------------------------------------------------------------------------------------

    /**
     * A button of a page's action bar: it opens a modal ({@code modal}) or acts at once ({@code run}). {@code denied}
     * non-null greys it out with the reason as its tooltip. {@link Ui.Style#BAD} buttons sit at the right end of the bar.
     */
    public record Act(Component label, Ui.Style style, Component denied, Supplier<Modal> modal, Runnable run) {
        public static Act modal(Component label, Component denied, Supplier<Modal> modal) {
            return new Act(label, Ui.Style.NORMAL, denied, modal, null);
        }

        public static Act modal(Component label, Ui.Style style, Component denied, Supplier<Modal> modal) {
            return new Act(label, style, denied, modal, null);
        }

        public static Act now(Component label, Ui.Style style, Component denied, Runnable run) {
            return new Act(label, style, denied, null, run);
        }
    }

    /** The height an action bar takes at the bottom of a page. */
    protected static final int BAR = 20;
    private List<Act> lastActs = List.of();

    /** The page's action bar along the bottom of the content; returns the y where it starts (the page ends above it). */
    protected int actionBar(GuiGraphics g, List<Act> acts) {
        return actionBar(g, x, y + h - BAR + 2, w, acts);
    }

    /** An action bar in a given strip (18 high). */
    protected int actionBar(GuiGraphics g, int bx, int by, int bw, List<Act> acts) {
        lastActs = acts;
        g.fillGradient(bx, by, bx + bw, by + 18, 0xFF1D232D, 0xFF161A21);
        g.fill(bx, by, bx + bw, by + 1, 0x50E3B556);
        g.fill(bx, by + 17, bx + bw, by + 18, 0xFF0B0E12);
        int total = 0, n = 0;
        for (Act a : acts) {
            total += font.width(a.label()) + 14;
            n++;
        }
        float squeeze = n == 0 ? 1 : Math.min(1, (bw - 8 - 3f * n) / Math.max(1, total));
        int left = bx + 4, right = bx + bw - 4;
        for (Act a : acts) {
            int aw = Math.max(20, (int) ((font.width(a.label()) + 14) * squeeze));
            int ax;
            if (a.style() == Ui.Style.BAD) {
                right -= aw;
                ax = right;
                right -= 3;
            } else {
                ax = left;
                left += aw + 3;
            }
            Runnable run = a.modal() != null ? () -> screen.open(a.modal().get()) : a.run();
            ui.button(g, ax, by + 2, aw, 14, a.label(), a.style(), a.denied(), run);
            if (a.modal() != null && a.denied() == null) g.fill(ax + aw - 4, by + 4, ax + aw - 2, by + 5, Draw.alpha(Draw.GOLD, 0x90));
        }
        return by;
    }

    /** For the preview harness: the modal behind the {@code index}-th modal button of the last action bar, or null. */
    public Modal previewModal(int index) {
        int i = 0;
        for (Act a : lastActs) {
            if (a.modal() == null) continue;
            if (i++ != index) continue;
            try {
                return a.modal().get();
            } catch (RuntimeException e) {
                return null; // a denied button whose modal has nothing to show
            }
        }
        return null;
    }

    /** Opens a modal over the screen. */
    protected void open(Modal m) {
        screen.open(m);
    }

    /** A destructive one-click action behind a {@link ConfirmModal}. */
    protected Runnable confirm(Component title, Component text, Component button, Runnable action) {
        return () -> screen.open(new ConfirmModal(title, text, button, action));
    }

    /** A clickable panel row with a colour bar on its left. Returns whether it is hovered. */
    protected boolean colorRow(GuiGraphics g, int rx, int ry, int rw, int rh, int color, boolean selected, Runnable action) {
        boolean hover = ui.over(rx, ry, rw, rh);
        g.fill(rx, ry, rx + rw, ry + rh, selected ? 0x40E3B556 : hover && action != null ? 0x26FFFFFF : 0x14FFFFFF);
        g.fillGradient(rx, ry, rx + 3, ry + rh, Draw.opaque(Draw.mix(color, 0xFFFFFF, 0.2f)), Draw.opaque(Draw.shade(color, 0.7f)));
        if (selected) Draw.frame(g, rx, ry, rw, rh, Draw.alpha(Draw.GOLD, 0xA0));
        if (action != null) ui.zone(rx, ry, rw, rh, action);
        return hover;
    }

    /** "label  value" on one line; returns the next line's y. */
    protected int stat(GuiGraphics g, Component label, Component value, int sx, int sy, int sw, int color) {
        g.drawString(font, label, sx, sy, Draw.MUTED, false);
        g.drawString(font, value, sx + sw - font.width(value), sy, color, false);
        return sy + 11;
    }

    public static Component money(int amount) {
        return Component.literal(amount + " ◆");
    }

    public static String nationName(PoliticsView v, UUID id) {
        PoliticsView.NationSummary n = id == null ? null : v.nationSummary(id);
        Briefs.NationBrief b = n == null && id != null ? ClientClaims.nation(id) : null;
        return n != null ? n.name() : b != null ? b.name() : "?";
    }

    public static int nationColor(PoliticsView v, UUID id) {
        PoliticsView.NationSummary n = id == null ? null : v.nationSummary(id);
        Briefs.NationBrief b = n == null && id != null ? ClientClaims.nation(id) : null;
        return n != null ? n.color() : b != null ? b.color() : 0x888888;
    }

    /** A nation's government noun ("Kingdom"): the managing mod's titles for a managed nation. */
    public static Component government(UUID nation, String government) {
        return Component.translatable(ClientClaims.titles(nation, government));
    }

    /** The "Managed" pill of a nation run by another mod; returns its width. */
    protected int managedBadge(GuiGraphics g, int bx, int by) {
        return ui.chip(g, bx, by, Component.translatable("nationsandwar.managed.badge"), Draw.MANAGED);
    }

    /**
     * The card of a nation run by another mod, shown instead of its details: its colour and emblem, the handler's title,
     * text and the badge, and one button (enabled when the handler's {@code canOpen}) that hands the viewer over to that mod.
     * With no handler: the generic text and no button.
     */
    protected void managedCard(GuiGraphics g, Briefs.NationBrief n, int cx, int cy, int cw, int ch) {
        ManagedNationScreens.Handler handler = ManagedNationScreens.handler(n.managedBy());
        int color = Draw.opaque(n.color()), light = Draw.mix(color, 0xFFFFFFFF, 0.35f), mid = cx + cw / 2;
        Draw.shadow(g, cx, cy, cw, ch, 6);
        Draw.slab(g, cx, cy, cw, ch, 3, 0xFF29313E, 0xFF1C222B);
        g.fill(cx, cy, cx + cw, cy + 2, color);
        int ew = 28, eh = 30, ex = mid - ew / 2, ey = cy + 10;
        Draw.shadow(g, ex, ey, ew, eh + 8, 4);
        g.fillGradient(ex, ey, ex + ew, ey + eh, Draw.mix(color, 0xFFFFFFFF, 0.15f), Draw.shade(color, 0.6f));
        Draw.quad(g, ex, ey + eh, ex + ew, ey + eh, mid, ey + eh + 9, mid, ey + eh + 9, Draw.shade(color, 0.6f));
        Draw.hatch(g, ex, ey, ew, 0x30000000, 0);
        Draw.frame(g, ex, ey, ew, eh, Draw.shade(color, 0.4f));
        Draw.icon(g, Draw.Icon.NATION, ex + 6, ey + 7);
        int ty = ey + eh + 15;
        Component title = handler == null ? Component.literal(n.name()) : handler.title(n);
        g.drawCenteredString(font, Draw.fit(font, title, cw - 10), mid, ty, light);
        ty += 11;
        if (!n.titles().isEmpty()) {
            Component noun = Draw.fit(font, Component.translatable(n.titles()), (int) ((cw - 10) / 0.75f));
            Draw.text(g, font, noun, mid - font.width(noun) * 0.75f / 2, ty, 0.75f, Draw.MUTED, false);
            ty += 10;
        }
        Component badge = Component.translatable("nationsandwar.managed.badge");
        ui.chip(g, mid - (font.width(badge) + 6) / 2, ty, badge, Draw.MANAGED);
        ty += 16;
        int buttonY = cy + ch - 19, bottom = handler == null ? cy + ch - 6 : buttonY - 4;
        Component text = handler == null ? Component.translatable("nationsandwar.managed.description") : handler.description(n);
        for (var line : font.split(text, cw - 12)) {
            if (ty + 9 > bottom) break;
            g.drawString(font, line, mid - font.width(line) / 2, ty, Draw.MUTED, false);
            ty += 10;
        }
        if (handler == null) return;
        var player = net.minecraft.client.Minecraft.getInstance().player;
        Component denied = player != null && handler.canOpen(player, n) ? null : handler.cannotOpen(n);
        ui.button(g, cx + 6, buttonY, cw - 12, 13, handler.button(n), Ui.Style.GOLD, denied, () -> handler.open(player, n));
    }

    public static String townName(PoliticsView v, UUID id) {
        PoliticsView.TownSummary t = id == null ? null : v.townSummary(id);
        return t == null ? "?" : t.name();
    }
}
