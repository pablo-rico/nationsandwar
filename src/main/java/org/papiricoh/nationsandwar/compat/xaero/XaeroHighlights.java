package org.papiricoh.nationsandwar.compat.xaero;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.papiricoh.nationsandwar.NWClientConfig;
import org.papiricoh.nationsandwar.client.ClientClaims;
import org.papiricoh.nationsandwar.compat.MapStyle;
import org.papiricoh.nationsandwar.view.Briefs;
import org.papiricoh.nationsandwar.view.SiegeBrief;

import java.util.Map;
import java.util.UUID;

/**
 * The claim highlights both Xaero maps draw: per-pixel colours of a chunk (16×16, Xaero's BGRA ints) by the chosen map
 * layer (a solid border where the group ends, a dotted one between its towns), the region hash that tells Xaero when to
 * redraw, and the tooltip. Shared by {@link WorldMapClaims} and {@link MinimapClaims}; the names over the World Map are
 * drawn by {@code mixin.xaero.WorldMapLabelsMixin}.
 */
public final class XaeroHighlights {
    private XaeroHighlights() {
    }

    static String dim(ResourceKey<Level> key) {
        return key.location().toString();
    }

    static boolean on() {
        return NWClientConfig.get(NWClientConfig.MAP_OVERLAYS);
    }

    static boolean regionHas(ResourceKey<Level> key, int rx, int rz) {
        if (!on()) return false;
        for (long c : ClientClaims.claims(dim(key)).keySet()) if (ChunkPos.getX(c) >> 5 == rx && ChunkPos.getZ(c) >> 5 == rz) return true;
        return false;
    }

    static boolean chunkHas(ResourceKey<Level> key, int cx, int cz) {
        return on() && ClientClaims.claims(dim(key)).containsKey(ChunkPos.asLong(cx, cz));
    }

    static int hash(ResourceKey<Level> key, int rx, int rz) {
        if (!on()) return 0;
        String dim = dim(key);
        int h = 31 + MapStyle.layer().ordinal();
        for (var e : ClientClaims.claims(dim).entrySet()) {
            long c = e.getKey();
            if (ChunkPos.getX(c) >> 5 != rx || ChunkPos.getZ(c) >> 5 != rz) continue;
            Briefs.TownBrief t = ClientClaims.town(e.getValue());
            int color = t == null ? 0 : MapStyle.fill(dim, t, c);
            UUID group = t == null ? null : MapStyle.group(t).id();
            h = 31 * h + Long.hashCode(c) * 7 + e.getValue().hashCode() + color + (group == null ? 0 : group.hashCode()) + MapStyle.state(dim,
                    t == null ? new Briefs.TownBrief(e.getValue(), "", 0, null, null, 0, false, "", "") : t, c).ordinal();
        }
        return h;
    }

    /** Fills {@code out} (256 BGRA pixels) for chunk (cx, cz); false if unclaimed. */
    static boolean colors(ResourceKey<Level> key, int cx, int cz, int[] out) {
        String dim = dim(key);
        Map<Long, UUID> claims = ClientClaims.claims(dim);
        long chunk = ChunkPos.asLong(cx, cz);
        UUID id = claims.get(chunk);
        Briefs.TownBrief town = ClientClaims.town(id);
        if (town == null) return false;
        MapStyle.State state = MapStyle.state(dim, town, chunk);
        int rgb = MapStyle.fill(dim, town, chunk);
        int fillA = Math.round(MapStyle.fillOpacity() * 255), edgeA = Math.min(255, fillA + 120);
        boolean n = MapStyle.groupEdge(claims, id, cx, cz, 0, -1), s = MapStyle.groupEdge(claims, id, cx, cz, 0, 1),
                w = MapStyle.groupEdge(claims, id, cx, cz, -1, 0), e = MapStyle.groupEdge(claims, id, cx, cz, 1, 0);
        boolean tn = !n && MapStyle.edge(claims, id, cx, cz, 0, -1), ts = !s && MapStyle.edge(claims, id, cx, cz, 0, 1),
                tw = !w && MapStyle.edge(claims, id, cx, cz, -1, 0), te = !e && MapStyle.edge(claims, id, cx, cz, 1, 0);
        boolean managed = state == MapStyle.State.NORMAL && MapStyle.managed(town);
        int accent = managed ? MapStyle.accent(town) : rgb;
        if (managed) fillA = Math.round(MapStyle.managedOpacity() * 255);
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                boolean border = n && z == 0 || s && z == 15 || w && x == 0 || e && x == 15;
                boolean seam = ((x + z) & 1) == 0 && (tn && z == 0 || ts && z == 15 || tw && x == 0 || te && x == 15);
                boolean stripe = state != MapStyle.State.NORMAL && ((x + z) & 3) < 2;
                if (managed && (border || MapStyle.hatch(x, z))) {
                    out[z << 4 | x] = bgra(accent, edgeA);
                    continue;
                }
                int a = border ? edgeA : seam ? Math.min(255, fillA + 60) : stripe ? Math.min(255, fillA + 70) : fillA;
                int c = stripe && state == MapStyle.State.OCCUPIED ? 0x601010 : border ? MapStyle.mix(rgb, 0xFFFFFF, 0.3f) : seam ? MapStyle.mix(rgb, 0xFFFFFF, 0.6f) : rgb;
                out[z << 4 | x] = bgra(c, a);
            }
        }
        return true;
    }

    static int bgra(int rgb, int alpha) {
        return (rgb & 255) << 24 | (rgb >> 8 & 255) << 16 | (rgb >> 16 & 255) << 8 | alpha & 255;
    }

    static Component tooltip(ResourceKey<Level> key, int cx, int cz) {
        String dim = dim(key);
        Briefs.TownBrief town = ClientClaims.townAt(dim, cx, cz);
        if (town == null) return null;
        SiegeBrief siege = ClientClaims.siegeAt(dim, ChunkPos.asLong(cx, cz));
        Component c = Component.literal(MapStyle.hierarchy(town)).withStyle(st -> st.withColor(MapStyle.mix(MapStyle.group(town).color(), 0xFFFFFF, 0.3f)));
        if (MapStyle.managed(town)) c = c.copy().append(Component.literal(" · ").append(Component.translatable("nationsandwar.managed.badge"))
                .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
        if (siege != null) c = c.copy().append(Component.translatable("nationsandwar.map.besieged", siege.besieger(),
                Math.round((siege.control() + 1) * 50)).withStyle(net.minecraft.ChatFormatting.RED));
        return c;
    }

    /**
     * The names over the World Map ({@code mixin.xaero.WorldMapLabelsMixin}): block ({@code cameraX}, {@code cameraZ}) sits at
     * the screen's centre and {@code guiPxPerBlock} GUI pixels make a block.
     */
    public static void drawLabels(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.gui.Font font, int width, int height, double cameraX,
                                  double cameraZ, double guiPxPerBlock) {
        if (!on()) return;
        String dim = WorldMapClaims.viewedDimension();
        if (dim == null) return;
        var pose = g.pose();
        pose.pushPose();
        pose.last().pose().identity();
        org.papiricoh.nationsandwar.client.map.MapLabels.draw(g, font, dim, width / 2.0, height / 2.0, cameraX / 16, cameraZ / 16, guiPxPerBlock * 16);
        pose.popPose();
    }

    /** Asks the world map to redraw every highlight (claims or sieges changed). */
    public static void invalidate() {
        try {
            if (net.neoforged.fml.ModList.get().isLoaded("xaeroworldmap")) WorldMapClaims.invalidate();
        } catch (Throwable ignored) {
        }
    }
}
