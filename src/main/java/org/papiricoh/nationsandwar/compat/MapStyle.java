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
 * occupied, and its label. Colours are 0xRRGGBB.
 */
public final class MapStyle {
    public enum State {NORMAL, BESIEGED, OCCUPIED}

    private MapStyle() {
    }

    public static State state(String dim, Briefs.TownBrief town, long chunk) {
        if (ClientClaims.siegeAt(dim, chunk) != null) return State.BESIEGED;
        return ClientClaims.occupied(town.id(), chunk) ? State.OCCUPIED : State.NORMAL;
    }

    /** The fill: the nation's colour (or the town's); a siege shows the besieger's colour. */
    public static int fill(String dim, Briefs.TownBrief town, long chunk) {
        SiegeBrief s = ClientClaims.siegeAt(dim, chunk);
        return s != null ? s.besiegerColor() : ClientClaims.mapColor(town);
    }

    public static float fillOpacity() {
        return (float) NWClientConfig.get(NWClientConfig.MAP_FILL_OPACITY);
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
