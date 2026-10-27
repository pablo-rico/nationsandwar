package org.papiricoh.nationsandwar.compat;

import net.minecraft.world.level.ChunkPos;
import org.papiricoh.nationsandwar.NWClientConfig;
import org.papiricoh.nationsandwar.client.ClientClaims;
import org.papiricoh.nationsandwar.view.Briefs;
import org.papiricoh.nationsandwar.view.SiegeBrief;

import java.util.Map;
import java.util.UUID;

/**
 * How claims look on any map (JourneyMap, Xaero's, the mod's own): the fill colour of a chunk, whether it is besieged or
 * occupied, and its label. Colours are 0xRRGGBB. Land of a nation run by another mod ({@link #managed}) is darker, with a
 * border and sparse stripes in its own colour ({@link #accent}, {@link #hatch}).
 */
public final class MapStyle {
    public enum State {NORMAL, BESIEGED, OCCUPIED}

    private MapStyle() {
    }

    public static State state(String dim, Briefs.TownBrief town, long chunk) {
        if (ClientClaims.siegeAt(dim, chunk) != null) return State.BESIEGED;
        return ClientClaims.occupied(town.id(), chunk) ? State.OCCUPIED : State.NORMAL;
    }

    /** The fill: the nation's colour (or the town's), darkened for managed land; a siege shows the besieger's colour. */
    public static int fill(String dim, Briefs.TownBrief town, long chunk) {
        SiegeBrief s = ClientClaims.siegeAt(dim, chunk);
        return s != null ? s.besiegerColor() : base(town);
    }

    /** The fill away from sieges: the nation's colour (or the town's), darkened for managed land. */
    public static int base(Briefs.TownBrief town) {
        return managed(town) ? mix(ClientClaims.mapColor(town), 0x000000, 0.6f) : ClientClaims.mapColor(town);
    }

    public static float fillOpacity() {
        return (float) NWClientConfig.get(NWClientConfig.MAP_FILL_OPACITY);
    }

    /** The fill opacity of managed land (denser, as its fill is darkened). */
    public static float managedOpacity() {
        return Math.min(1f, fillOpacity() + 0.2f);
    }

    /** Whether a town is run by another mod, or belongs to a nation that is. */
    public static boolean managed(Briefs.TownBrief town) {
        return town.managed() || ClientClaims.managed(town.nation());
    }

    /** The border and stripe colour of managed land: the nation's colour, lightened. */
    public static int accent(Briefs.TownBrief town) {
        return mix(ClientClaims.mapColor(town), 0xFFFFFF, 0.3f);
    }

    /** Whether pixel (x, z) of a chunk (0..15) lies on the stripes of managed land (sparse, the other way to a siege's). */
    public static boolean hatch(int x, int z) {
        return ((x - z) & 7) == 0;
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round((a >> 16 & 255) * (1 - t) + (b >> 16 & 255) * t), g = Math.round((a >> 8 & 255) * (1 - t) + (b >> 8 & 255) * t),
                bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }

    public static String label(Briefs.TownBrief town) {
        Briefs.NationBrief n = ClientClaims.nation(town.nation());
        return n == null ? town.name() : town.name() + " (" + n.name() + ")";
    }

    /** Whether the chunk's neighbour in a direction belongs to another owner (draw a border there). */
    public static boolean edge(Map<Long, UUID> claims, UUID town, int cx, int cz, int dx, int dz) {
        return !town.equals(claims.get(ChunkPos.asLong(cx + dx, cz + dz)));
    }
}
