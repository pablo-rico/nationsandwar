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
 * The claim highlights both Xaero maps draw: per-pixel colours of a chunk (16×16, Xaero's BGRA ints), the region hash
 * that tells Xaero when to redraw, and the tooltip. Shared by {@link WorldMapClaims} and {@link MinimapClaims}.
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
        int h = 1;
        for (var e : ClientClaims.claims(dim).entrySet()) {
            long c = e.getKey();
            if (ChunkPos.getX(c) >> 5 != rx || ChunkPos.getZ(c) >> 5 != rz) continue;
            Briefs.TownBrief t = ClientClaims.town(e.getValue());
            int color = t == null ? 0 : MapStyle.fill(dim, t, c);
            h = 31 * h + Long.hashCode(c) * 7 + e.getValue().hashCode() + color + MapStyle.state(dim, t == null ? new Briefs.TownBrief(e.getValue(),
                    "", 0, null, null, 0, false, "") : t, c).ordinal();
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
        boolean n = MapStyle.edge(claims, id, cx, cz, 0, -1), s = MapStyle.edge(claims, id, cx, cz, 0, 1),
                w = MapStyle.edge(claims, id, cx, cz, -1, 0), e = MapStyle.edge(claims, id, cx, cz, 1, 0);
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                boolean border = n && z == 0 || s && z == 15 || w && x == 0 || e && x == 15;
                boolean stripe = state != MapStyle.State.NORMAL && ((x + z) & 3) < 2;
                int a = border ? edgeA : stripe ? Math.min(255, fillA + 70) : fillA;
                int c = stripe && state == MapStyle.State.OCCUPIED ? 0x601010 : rgb;
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
        Component c = Component.literal(MapStyle.label(town)).withStyle(st -> st.withColor(ClientClaims.mapColor(town)));
        if (siege != null) c = c.copy().append(Component.translatable("nationsandwar.map.besieged", siege.besieger(),
                Math.round((siege.control() + 1) * 50)).withStyle(net.minecraft.ChatFormatting.RED));
        return c;
    }

    /** Asks the world map to redraw every highlight (claims or sieges changed). */
    public static void invalidate() {
        try {
            if (net.neoforged.fml.ModList.get().isLoaded("xaeroworldmap")) WorldMapClaims.invalidate();
        } catch (Throwable ignored) {
        }
    }
}
