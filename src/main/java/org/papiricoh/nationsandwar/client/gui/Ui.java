package org.papiricoh.nationsandwar.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * Immediate-mode controls for the mod's screens: every frame the screen draws its buttons, checkboxes and rows from the
 * latest view and registers their click zones here, so a fresh view from the server shows up at once with nothing to
 * rebuild. Clicks go to the zones of the last frame (topmost first); one tooltip per frame is drawn at the end. Clip
 * rectangles (scrolling lists) also bound the zones inside them.
 */
public final class Ui {
    public enum Style {NORMAL, GOLD, GOOD, BAD}

    private record Zone(int x0, int y0, int x1, int y1, Runnable action) {
    }

    private record ScrollZone(int x0, int y0, int x1, int y1, Scroll scroll) {
    }

    /** A scroll offset for a list; the list sets {@link #content} every frame. */
    public static final class Scroll {
        public int offset, content;

        public void clamp(int visible) {
            offset = Math.max(0, Math.min(offset, Math.max(0, content - visible)));
        }
    }

    public final Font font;
    public int mx, my;
    private final List<Zone> zones = new ArrayList<>();
    private final List<ScrollZone> scrolls = new ArrayList<>();
    private final Deque<int[]> clips = new ArrayDeque<>();
    private List<Component> tip;

    public Ui(Font font) {
        this.font = font;
    }

    /** Starts a frame: the zones drawn from now on replace the last frame's. */
    public void begin(int mouseX, int mouseY) {
        mx = mouseX;
        my = mouseY;
        zones.clear();
        scrolls.clear();
        clips.clear();
        tip = null;
    }

    /**
     * Starts a new layer on top of what was drawn so far this frame: the zones, scroll zones and tooltip registered until now
     * are dropped, so the page under a modal is inert. The mouse moves to (x, y) for the new layer.
     */
    public void layer(int mouseX, int mouseY) {
        zones.clear();
        scrolls.clear();
        tip = null;
        mx = mouseX;
        my = mouseY;
    }

    /** {@link #layer(int, int)} keeping the mouse where it is. */
    public void layer() {
        layer(mx, my);
    }

    // --- clipping ----------------------------------------------------------------------------------------------------------

    public void clip(GuiGraphics g, int x, int y, int w, int h) {
        int[] c = {x, y, x + w, y + h};
        int[] top = clips.peek();
        if (top != null) c = new int[]{Math.max(c[0], top[0]), Math.max(c[1], top[1]), Math.min(c[2], top[2]), Math.min(c[3], top[3])};
        clips.push(c);
        g.enableScissor(c[0], c[1], Math.max(c[0], c[2]), Math.max(c[1], c[3]));
    }

    public void unclip(GuiGraphics g) {
        clips.pop();
        g.disableScissor();
        int[] top = clips.peek();
        if (top != null) g.enableScissor(top[0], top[1], top[2], top[3]);
    }

    /** Whether the mouse is over a rectangle (and inside the current clip). */
    public boolean over(int x, int y, int w, int h) {
        int[] c = clips.peek();
        if (c != null && (mx < c[0] || mx >= c[2] || my < c[1] || my >= c[3])) return false;
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Registers a click zone (clipped to the current clip). */
    public void zone(int x, int y, int w, int h, Runnable action) {
        int x0 = x, y0 = y, x1 = x + w, y1 = y + h;
        int[] c = clips.peek();
        if (c != null) {
            x0 = Math.max(x0, c[0]);
            y0 = Math.max(y0, c[1]);
            x1 = Math.min(x1, c[2]);
            y1 = Math.min(y1, c[3]);
        }
        if (x1 > x0 && y1 > y0) zones.add(new Zone(x0, y0, x1, y1, action));
    }

    public void scrollZone(int x, int y, int w, int h, Scroll s) {
        s.clamp(h);
        scrolls.add(new ScrollZone(x, y, x + w, y + h, s));
    }

    public void tip(int x, int y, int w, int h, Component... lines) {
        if (over(x, y, w, h)) tip = List.of(lines);
    }

    public void tip(int x, int y, int w, int h, List<Component> lines) {
        if (over(x, y, w, h)) tip = lines;
    }

    // --- controls ----------------------------------------------------------------------------------------------------------

    /**
     * A button. {@code denied} non-null greys it out and becomes its tooltip (why the viewer may not). Returns its width.
     */
    public int button(GuiGraphics g, int x, int y, int w, int h, Component label, Style style, Component denied, Runnable action) {
        if (w <= 0) w = font.width(label) + 10;
        boolean enabled = denied == null && action != null, hover = over(x, y, w, h);
        int accent = switch (style) {
            case GOLD -> Draw.GOLD;
            case GOOD -> Draw.GOOD;
            case BAD -> Draw.BAD;
            default -> Draw.EDGE_HI;
        };
        int top = enabled ? Draw.mix(0xFF2C3543, accent, hover ? 0.32f : 0.12f) : 0xFF1C2028;
        int bottom = enabled ? Draw.mix(0xFF1D232D, accent, hover ? 0.18f : 0.04f) : 0xFF16191F;
        g.fillGradient(x, y, x + w, y + h, top, bottom);
        Draw.frame(g, x, y, w, h, enabled ? Draw.mix(0xFF0B0E12, accent, hover ? 0.9f : 0.45f) : 0xFF262B33);
        if (enabled) g.fill(x + 1, y + 1, x + w - 1, y + 2, Draw.alpha(0xFFFFFF, hover ? 0x50 : 0x28));
        int tc = enabled ? (style == Style.NORMAL ? Draw.TEXT : Draw.mix(Draw.TEXT, accent, 0.55f)) : 0xFF5C6370;
        Component shown = Draw.fit(font, label, w - 4);
        g.drawString(font, shown, x + (w - font.width(shown)) / 2, y + (h - 8) / 2, tc, enabled);
        if (denied != null) tip(x, y, w, h, Component.literal("").append(label), denied.copy().withStyle(s -> s.withColor(Draw.BAD & 0xFFFFFF)));
        if (enabled) zone(x, y, w, h, action);
        return w;
    }

    public int button(GuiGraphics g, int x, int y, int w, Component label, Component denied, Runnable action) {
        return button(g, x, y, w, 13, label, Style.NORMAL, denied, action);
    }

    /** A checkbox with a label; returns the total width. */
    public int check(GuiGraphics g, int x, int y, Component label, boolean on, Component denied, Runnable toggle) {
        boolean enabled = denied == null, hover = over(x, y, 11 + font.width(label) + 3, 11);
        g.fill(x, y, x + 11, y + 11, enabled ? (hover ? 0xFF3A4556 : 0xFF0C0F14) : 0xFF14171C);
        Draw.frame(g, x, y, 11, 11, enabled ? (hover ? Draw.GOLD : Draw.EDGE_HI) : 0xFF2A2F38);
        if (on) {
            int c = enabled ? Draw.GOOD : 0xFF4D6B57;
            Draw.line(g, x + 2.5f, y + 5.5f, x + 4.5f, y + 8f, 1.6f, c);
            Draw.line(g, x + 4.5f, y + 8f, x + 8.5f, y + 2.5f, 1.6f, c);
        }
        int w = 11;
        if (label != null && !label.getString().isEmpty()) {
            g.drawString(font, label, x + 14, y + 2, enabled ? Draw.TEXT : 0xFF5C6370, false);
            w += 3 + font.width(label);
        }
        if (denied != null) tip(x, y, w, 11, denied);
        else zone(x, y, w, 11, toggle);
        return w;
    }

    /** A small colour square; a ring shows the selected one. */
    public void swatch(GuiGraphics g, int x, int y, int size, int rgb, boolean selected, boolean enabled, Runnable pick) {
        boolean hover = enabled && over(x, y, size, size);
        g.fill(x, y, x + size, y + size, selected ? Draw.GOLD : hover ? 0xFFFFFFFF : 0xFF0B0E12);
        g.fillGradient(x + 1, y + 1, x + size - 1, y + size - 1, Draw.opaque(Draw.mix(rgb, 0xFFFFFF, 0.2f)), Draw.opaque(Draw.shade(rgb, 0.8f)));
        if (!enabled) g.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0x90101010);
        if (enabled) zone(x, y, size, size, pick);
    }

    /** A clickable row background that highlights on hover; returns whether it is hovered. */
    public boolean row(GuiGraphics g, int x, int y, int w, int h, boolean selected, Runnable action) {
        boolean hover = over(x, y, w, h);
        if (selected) g.fill(x, y, x + w, y + h, 0x40E3B556);
        else if (hover && action != null) g.fill(x, y, x + w, y + h, 0x22FFFFFF);
        if (selected) g.fill(x, y, x + 2, y + h, Draw.GOLD);
        if (action != null) zone(x, y, w, h, action);
        return hover;
    }

    /** A pill-shaped label (relation, phase, rank). Returns its width. */
    public int chip(GuiGraphics g, int x, int y, Component label, int color) {
        int w = font.width(label) + 6;
        g.fill(x, y, x + w, y + 10, Draw.alpha(color, 0x40));
        Draw.frame(g, x, y, w, 10, Draw.alpha(color, 0xC0));
        g.drawString(font, label, x + 3, y + 1, Draw.mix(Draw.opaque(color), 0xFFFFFFFF, 0.35f), false);
        return w;
    }

    /** A small pill drawn at a scale (dense rows such as the Atlas tree). Returns its width. */
    public float chip(GuiGraphics g, float x, float y, Component label, int color, float scale) {
        float w = (font.width(label) + 4) * scale, h = 9 * scale;
        Draw.rect(g, x, y, w, h, Draw.alpha(color, 0x40));
        Draw.rect(g, x, y, w, scale, Draw.alpha(color, 0xC0));
        Draw.rect(g, x, y + h - scale, w, scale, Draw.alpha(color, 0xC0));
        Draw.text(g, font, label, x + 2 * scale, y + 1.2f * scale, scale, Draw.mix(Draw.opaque(color), 0xFFFFFFFF, 0.35f), false);
        return w;
    }

    /** A thin vertical scrollbar for a clipped list. */
    public void scrollbar(GuiGraphics g, int x, int y, int h, Scroll s) {
        if (s.content <= h) return;
        g.fill(x, y, x + 2, y + h, 0x40000000);
        int th = Math.max(8, h * h / s.content), ty = y + (h - th) * s.offset / Math.max(1, s.content - h);
        g.fill(x, ty, x + 2, ty + th, Draw.EDGE_HI);
    }

    // --- events ------------------------------------------------------------------------------------------------------------

    public boolean click(double x, double y) {
        for (int i = zones.size() - 1; i >= 0; i--) {
            Zone z = zones.get(i);
            if (x >= z.x0 && x < z.x1 && y >= z.y0 && y < z.y1) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                z.action.run();
                return true;
            }
        }
        return false;
    }

    public boolean scroll(double x, double y, double delta) {
        for (ScrollZone z : scrolls) {
            if (x >= z.x0 && x < z.x1 && y >= z.y0 && y < z.y1) {
                z.scroll.offset -= (int) (delta * 12);
                z.scroll.clamp(z.y1 - z.y0);
                return true;
            }
        }
        return false;
    }

    public void tooltip(GuiGraphics g) {
        if (tip != null && !tip.isEmpty()) g.renderTooltip(font, tip, Optional.empty(), mx, my);
    }
}
