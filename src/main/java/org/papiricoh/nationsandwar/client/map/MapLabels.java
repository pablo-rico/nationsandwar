package org.papiricoh.nationsandwar.client.map;

import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import org.papiricoh.nationsandwar.NWClientConfig;
import org.papiricoh.nationsandwar.compat.MapLayer;
import org.papiricoh.nationsandwar.compat.MapStyle;
import org.papiricoh.nationsandwar.compat.Territories;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Paints the names over the territories of a top-down map, Paradox style: capitals with wide letter spacing, laid along
 * each region's long axis and scaled to fill it, light text with a dark outline in the group's colour. Names that would
 * be too small to read fade out (but a group's main region keeps its name at the normal font size at least); a name that
 * would overlap a bigger one is skipped. Used by the map tab and Xaero's
 * World Map; JourneyMap gets its labels through its own API.
 */
public final class MapLabels {
    /** On-screen text height (GUI px) below which a name is hidden, and the band over which it fades in. */
    private static final float MIN_PX = 5, FADE_PX = 3;
    private static final float GLYPH_H = 8;

    private MapLabels() {
    }

    /**
     * Draws the names of a dimension's territories under the current layer. The screen point ({@code originX},
     * {@code originY}) shows chunk coordinate ({@code chunkX}, {@code chunkZ}); {@code pxPerChunk} is GUI pixels per chunk.
     */
    public static void draw(GuiGraphics g, Font font, String dim, double originX, double originY, double chunkX, double chunkZ, double pxPerChunk) {
        if (!NWClientConfig.get(NWClientConfig.MAP_LABELS)) return;
        List<float[]> taken = new ArrayList<>();
        for (Territories.Label l : MapStyle.labels(dim)) {
            MapStyle.Group group = MapStyle.groupById(l.group());
            if (group == null) continue;
            float sx = (float) (originX + (l.x() - chunkX) * pxPerChunk), sy = (float) (originY + (l.z() - chunkZ) * pxPerChunk);
            if (sx < -2000 || sx > 6000 || sy < -2000 || sy > 6000) continue;
            String text = group.name().toUpperCase(Locale.ROOT);
            boolean town = group.kind() == MapLayer.TOWNS;
            float spacing = town ? 0.8f : 1.8f, width = width(font, text, spacing);
            if (width <= 0) continue;
            // Fill the region's length, but stay within its thickness and a third of its area (L shapes, rings).
            float scale = (float) Math.min(Math.min(0.8 * l.length() * pxPerChunk / width, 0.55 * l.thickness() * pxPerChunk / GLYPH_H),
                    Math.sqrt(0.33 * l.chunks() * pxPerChunk * pxPerChunk / (width * GLYPH_H)));
            scale = Math.min(scale, town ? 2.5f : 6f);
            // A group's main region always reads at least at the normal font size; other pieces fade in as they grow.
            if (l.largest()) scale = Math.max(scale, 1f);
            float px = scale * GLYPH_H;
            if (px < MIN_PX) continue;
            float half = (float) (Math.abs(Math.cos(l.angle())) * width * scale + Math.abs(Math.sin(l.angle())) * px) / 2,
                    halfH = (float) (Math.abs(Math.sin(l.angle())) * width * scale + Math.abs(Math.cos(l.angle())) * px) / 2;
            float[] box = {sx - half, sy - halfH, sx + half, sy + halfH};
            if (overlaps(taken, box)) continue;
            taken.add(box);
            int alpha = Math.max(0x18, Math.round(255 * Math.min(1, (px - MIN_PX) / FADE_PX)));
            int fill = MapStyle.mix(group.color(), 0xFFFFFF, 0.62f), outline = MapStyle.mix(group.color(), 0x000000, 0.78f);
            var pose = g.pose();
            pose.pushPose();
            pose.translate(sx, sy, 0);
            pose.mulPose(Axis.ZP.rotation((float) l.angle()));
            pose.scale(scale, scale, 1);
            // The outline: the text shifted about one GUI pixel around, then the fill above it.
            float o = Math.max(0.35f, Math.min(1f, 1f / scale));
            for (float[] d : AROUND) write(g, font, text, -width / 2 + d[0] * o, -GLYPH_H / 2 + d[1] * o, spacing, alpha << 24 | outline);
            pose.translate(0, 0, 0.5f);
            write(g, font, text, -width / 2, -GLYPH_H / 2, spacing, alpha << 24 | fill);
            pose.popPose();
        }
        g.flush();
    }

    private static final float[][] AROUND = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-1, -1}, {1, -1}, {-1, 1}, {1, 1}};

    /** The text letter by letter with extra spacing, at the pose's origin plus (x, y). */
    private static void write(GuiGraphics g, Font font, String text, float x, float y, float spacing, int color) {
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            font.drawInBatch(ch, x, y, color, false, g.pose().last().pose(), g.bufferSource(), Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            x += font.width(ch) + spacing;
            i += Character.charCount(cp);
        }
    }

    private static float width(Font font, String text, float spacing) {
        int n = text.codePointCount(0, text.length());
        return font.width(text) + spacing * Math.max(0, n - 1);
    }

    private static boolean overlaps(List<float[]> taken, float[] b) {
        for (float[] a : taken) if (a[0] < b[2] && b[0] < a[2] && a[1] < b[3] && b[1] < a[3]) return true;
        return false;
    }
}
