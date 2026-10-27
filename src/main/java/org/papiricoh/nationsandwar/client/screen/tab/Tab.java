package org.papiricoh.nationsandwar.client.screen.tab;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.papiricoh.nationsandwar.client.ClientPolitics;
import org.papiricoh.nationsandwar.client.gui.Draw;
import org.papiricoh.nationsandwar.client.gui.Ui;
import org.papiricoh.nationsandwar.client.screen.NationScreen;
import org.papiricoh.nationsandwar.model.Perm;
import org.papiricoh.nationsandwar.view.PoliticsView;

import java.util.List;
import java.util.UUID;

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

    protected static MutableComponent tr(String key, Object... args) {
        return Component.translatable(K + key, args);
    }

    /** A role's name: "@key" names are lang keys. */
    public static Component roleName(String name) {
        return name.startsWith("@") ? Component.translatable(name.substring(1)) : Component.literal(name);
    }

    protected static boolean has(int mask, Perm p) {
        return (mask & 1 << p.ordinal()) != 0;
    }

    /** Null when allowed, else the "needs …" reason for a greyed-out control. */
    protected static Component need(int mask, Perm p) {
        return has(mask, p) ? null : Component.translatable(K + "needs", Component.translatable(p.langKey()));
    }

    protected static Component needIf(boolean ok, String reasonKey) {
        return ok ? null : Component.translatable(K + reasonKey);
    }

    /** Whether a player holds the root (leader) role of a tree. */
    public static boolean leads(List<PoliticsView.RoleView> roles, UUID me) {
        for (PoliticsView.RoleView r : roles) if (r.parent() < 0) return r.holders().contains(me);
        return false;
    }

    /** Builds action arguments from key/value pairs (String, Integer, Boolean, Byte, UUID or Tag values). */
    protected static CompoundTag args(Object... kv) {
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

    protected static void act(String action, Object... kv) {
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
        int size = Math.max(5, Math.min(11, width / 8 - 1));
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

    /** "label  value" on one line; returns the next line's y. */
    protected int stat(GuiGraphics g, Component label, Component value, int sx, int sy, int sw, int color) {
        g.drawString(font, label, sx, sy, Draw.MUTED, false);
        g.drawString(font, value, sx + sw - font.width(value), sy, color, false);
        return sy + 11;
    }

    protected static Component money(int amount) {
        return Component.literal(amount + " ◆");
    }

    protected static String nationName(PoliticsView v, UUID id) {
        PoliticsView.NationSummary n = id == null ? null : v.nationSummary(id);
        return n == null ? "?" : n.name();
    }

    protected static int nationColor(PoliticsView v, UUID id) {
        PoliticsView.NationSummary n = id == null ? null : v.nationSummary(id);
        return n == null ? 0x888888 : n.color();
    }

    protected static String townName(PoliticsView v, UUID id) {
        PoliticsView.TownSummary t = id == null ? null : v.townSummary(id);
        return t == null ? "?" : t.name();
    }
}
