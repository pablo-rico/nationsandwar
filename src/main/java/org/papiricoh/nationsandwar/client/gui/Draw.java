package org.papiricoh.nationsandwar.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.papiricoh.nationsandwar.NationsAndWar;

/**
 * Drawing primitives for the mod's screens: the theme colours, the icon strip, free quads and lines (for the 2.5D cards
 * and connectors), slabs with thickness and shadow, bars, hatching and colour maths. Colours are 0xAARRGGBB.
 */
public final class Draw {
    public static final int DIM = 0xB0080A0E, PANEL = 0xFF1A1F28, PANEL_HI = 0xFF232A35, INSET = 0xFF12161C, EDGE = 0xFF343E4F,
            EDGE_HI = 0xFF56647D, TEXT = 0xFFEAE4D4, MUTED = 0xFF8C95A7, GOLD = 0xFFE3B556, GOOD = 0xFF6FD08A, BAD = 0xFFE5646A,
            WARN = 0xFFE8A93C, INFO = 0xFF7FB2F0, MANAGED = 0xFFB48CE6;
    public static final ResourceLocation ICONS = NationsAndWar.id("textures/gui/icons.png");

    /** The cells of {@code textures/gui/icons.png}, in order (see {@code tools/artgen/guiart.py}). */
    public enum Icon {OVERVIEW, TOWN, ROLES, PROVINCES, NATION, NATION_ROLES, DIPLOMACY, WAR, MAP, CROWN, SCROLL}

    private Draw() {
    }

    public static void icon(GuiGraphics g, Icon icon, int x, int y) {
        RenderSystem.enableBlend();
        g.blit(ICONS, x, y, icon.ordinal() * 16, 0, 16, 16, 256, 16);
        RenderSystem.disableBlend();
    }

    /** An icon drawn at a size other than 16 (inside a scaled pose it stays crisp at the pose's scale). */
    public static void icon(GuiGraphics g, Icon icon, float x, float y, float size) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(size / 16f, size / 16f, 1);
        icon(g, icon, 0, 0);
        pose.popPose();
    }

    // --- shapes ------------------------------------------------------------------------------------------------------------

    /** A free quad; the corners may come in either winding. */
    public static void quad(GuiGraphics g, float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4, int c1, int c2, int c3, int c4) {
        float area = (x1 * y2 - x2 * y1) + (x2 * y3 - x3 * y2) + (x3 * y4 - x4 * y3) + (x4 * y1 - x1 * y4);
        Matrix4f m = g.pose().last().pose();
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        if (area > 0) {
            vc.addVertex(m, x4, y4, 0).setColor(c4);
            vc.addVertex(m, x3, y3, 0).setColor(c3);
            vc.addVertex(m, x2, y2, 0).setColor(c2);
            vc.addVertex(m, x1, y1, 0).setColor(c1);
        } else {
            vc.addVertex(m, x1, y1, 0).setColor(c1);
            vc.addVertex(m, x2, y2, 0).setColor(c2);
            vc.addVertex(m, x3, y3, 0).setColor(c3);
            vc.addVertex(m, x4, y4, 0).setColor(c4);
        }
    }

    public static void quad(GuiGraphics g, float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4, int color) {
        quad(g, x1, y1, x2, y2, x3, y3, x4, y4, color, color, color, color);
    }

    /** A rectangle in float coordinates with a vertical gradient. */
    public static void rect(GuiGraphics g, float x, float y, float w, float h, int top, int bottom) {
        quad(g, x, y, x, y + h, x + w, y + h, x + w, y, top, bottom, bottom, top);
    }

    public static void rect(GuiGraphics g, float x, float y, float w, float h, int color) {
        rect(g, x, y, w, h, color, color);
    }

    public static void line(GuiGraphics g, float x1, float y1, float x2, float y2, float width, int color) {
        float dx = x2 - x1, dy = y2 - y1, len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-3f) return;
        float nx = -dy / len * width / 2, ny = dx / len * width / 2;
        quad(g, x1 + nx, y1 + ny, x1 - nx, y1 - ny, x2 - nx, y2 - ny, x2 + nx, y2 + ny, color);
    }

    /** A 1-pixel frame inside (x, y, w, h). */
    public static void frame(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /** A panel: gradient body, a light top edge and a dark frame. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h, int top, int bottom) {
        g.fillGradient(x, y, x + w, y + h, top, bottom);
        frame(g, x, y, w, h, 0xFF0B0E12);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, mix(top, 0xFFFFFFFF, 0.12f));
    }

    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        panel(g, x, y, w, h, PANEL_HI, PANEL);
    }

    /** A sunken well for lists and maps. */
    public static void inset(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, INSET);
        g.fill(x, y, x + w, y + 1, 0xFF07090C);
        g.fill(x, y, x + 1, y + h, 0xFF07090C);
        g.fill(x, y + h - 1, x + w, y + h, 0xFF2A3240);
        g.fill(x + w - 1, y, x + w, y + h, 0xFF2A3240);
    }

    /** A soft drop shadow under a box, cast down and to the left (the light comes from the top right). */
    public static void shadow(GuiGraphics g, float x, float y, float w, float h, float spread) {
        for (int i = 0; i < 4; i++) {
            float o = spread * (i + 1) / 4f;
            rect(g, x - o * 0.4f, y + o * 0.8f, w + o * 0.3f, h + o * 0.4f, 0x16000000);
        }
    }

    /**
     * A box with thickness: the front face (vertical gradient of {@code top}→{@code bottom}) and a top and right face
     * receding up and to the right by {@code depth}, which gives cards their 2.5D look.
     */
    public static void slab(GuiGraphics g, float x, float y, float w, float h, float depth, int top, int bottom) {
        int lid = mix(top, 0xFFFFFFFF, 0.22f), side = shade(bottom, 0.55f);
        quad(g, x, y, x + w, y, x + w + depth, y - depth, x + depth, y - depth, lid, lid, shade(lid, 0.9f), shade(lid, 0.9f));
        quad(g, x + w, y, x + w, y + h, x + w + depth, y + h - depth, x + w + depth, y - depth, side, shade(side, 0.7f), shade(side, 0.7f), side);
        rect(g, x, y, w, h, top, bottom);
        line(g, x + depth, y - depth, x + w + depth, y - depth, 0.6f, 0x40FFFFFF);
    }

    /** A small solid triangle centred on (cx, cy): pointing down when {@code down}, else right (tree expanders). */
    public static void chevron(GuiGraphics g, float cx, float cy, float size, boolean down, int color) {
        float s = size / 2;
        if (down) quad(g, cx - s, cy - s / 2, cx + s, cy - s / 2, cx, cy + s * 0.7f, cx, cy + s * 0.7f, color);
        else quad(g, cx - s / 2, cy - s, cx + s * 0.7f, cy, cx + s * 0.7f, cy, cx - s / 2, cy + s, color);
    }

    /** A progress bar (0..1) in a dark trough. */
    public static void bar(GuiGraphics g, int x, int y, int w, int h, float frac, int color) {
        g.fill(x, y, x + w, y + h, 0xFF0A0C10);
        int fw = Math.round((w - 2) * Math.max(0, Math.min(1, frac)));
        if (fw > 0) {
            g.fillGradient(x + 1, y + 1, x + 1 + fw, y + h - 1, mix(color, 0xFFFFFFFF, 0.25f), shade(color, 0.75f));
        }
    }

    /** Diagonal stripes over a square cell (besieged / occupied chunks). */
    public static void hatch(GuiGraphics g, int x, int y, int size, int color, int phase) {
        for (int r = 0; r < size; r++)
            for (int c = 0; c < size; c++)
                if (((r + c + phase) & 3) == 0) g.fill(x + c, y + r, x + c + 1, y + r + 1, color);
    }

    // --- text --------------------------------------------------------------------------------------------------------------

    /** Text at a scale (0.5–1) so dense panels still fit. */
    public static void text(GuiGraphics g, Font font, Component s, float x, float y, float scale, int color, boolean shadow) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(scale, scale, 1);
        g.drawString(font, s, 0, 0, color, shadow);
        pose.popPose();
    }

    /** Cuts a component to a pixel width, adding "…". */
    public static Component fit(Font font, Component s, int width) {
        if (font.width(s) <= width) return s;
        String str = s.getString();
        while (!str.isEmpty() && font.width(str + "…") > width) str = str.substring(0, str.length() - 1);
        return Component.literal(str + "…").withStyle(s.getStyle());
    }

    // --- colour ------------------------------------------------------------------------------------------------------------

    public static int opaque(int rgb) {
        return 0xFF000000 | rgb;
    }

    public static int alpha(int c, int a) {
        return (a << 24) | (c & 0xFFFFFF);
    }

    public static int shade(int c, float f) {
        int r = Math.min(255, (int) ((c >> 16 & 255) * f)), gg = Math.min(255, (int) ((c >> 8 & 255) * f)), b = Math.min(255, (int) ((c & 255) * f));
        return (c & 0xFF000000) | r << 16 | gg << 8 | b;
    }

    public static int mix(int a, int b, float t) {
        int aa = a >>> 24, ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255;
        int ba = b >>> 24, br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
        return (int) (aa + (ba - aa) * t) << 24 | (int) (ar + (br - ar) * t) << 16 | (int) (ag + (bg - ag) * t) << 8 | (int) (ab + (bb - ab) * t);
    }

    /** Perceived brightness 0..1, to choose dark or light text over a colour. */
    public static float luma(int c) {
        return ((c >> 16 & 255) * 0.299f + (c >> 8 & 255) * 0.587f + (c & 255) * 0.114f) / 255f;
    }
}
