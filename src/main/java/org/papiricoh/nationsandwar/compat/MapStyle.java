package org.papiricoh.nationsandwar.compat;

import net.minecraft.world.level.ChunkPos;
import org.papiricoh.nationsandwar.NWClientConfig;
import org.papiricoh.nationsandwar.client.ClientClaims;
import org.papiricoh.nationsandwar.view.Briefs;
import org.papiricoh.nationsandwar.view.SiegeBrief;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * How claims look on any map (JourneyMap, Xaero's, the mod's own): the {@link MapLayer} in use and the {@link Group} each
 * town falls in under it, the fill colour of a chunk, whether it is besieged or occupied, the borders between groups and
 * the names over them ({@link #labels}). Colours are 0xRRGGBB. Land of a nation run by another mod ({@link #managed}) is
 * darker, with a border and sparse stripes in its own colour ({@link #accent}, {@link #hatch}).
 */
public final class MapStyle {
    public enum State {NORMAL, BESIEGED, OCCUPIED}

    /** What a town shows as under a layer: a nation, a province, an alliance or the town itself ({@code kind}). */
    public record Group(UUID id, String name, int color, MapLayer kind) {
    }

    /** Per layer: town id → its group, and group id → group. */
    private static final Map<MapLayer, Map<UUID, Group>> GROUPS = new EnumMap<>(MapLayer.class), BY_ID = new EnumMap<>(MapLayer.class);
    private static final Map<String, List<Territories.Label>> LABELS = new HashMap<>();

    static {
        ClientClaims.listen(c -> {
            if (c == ClientClaims.Change.SIEGES) return;
            if (c != ClientClaims.Change.CLAIMS) {
                GROUPS.clear();
                BY_ID.clear();
            }
            LABELS.clear();
        });
    }

    private MapStyle() {
    }

    // --- layers and groups -------------------------------------------------------------------------------------------------

    public static MapLayer layer() {
        return NWClientConfig.get(NWClientConfig.MAP_LAYER);
    }

    /** Switches the layer on every map (saved in the client config). */
    public static void setLayer(MapLayer layer) {
        if (layer == layer()) return;
        NWClientConfig.set(NWClientConfig.MAP_LAYER, layer);
        LABELS.clear();
        ClientClaims.layerChanged();
    }

    /** The group a town shows as under the current layer. */
    public static Group group(Briefs.TownBrief town) {
        return group(layer(), town);
    }

    public static Group group(MapLayer layer, Briefs.TownBrief town) {
        Map<UUID, Group> byTown = GROUPS.computeIfAbsent(layer, k -> new HashMap<>());
        Group g = byTown.get(town.id());
        if (g == null) {
            g = resolve(layer, town);
            byTown.put(town.id(), g);
            BY_ID.computeIfAbsent(layer, k -> new HashMap<>()).put(g.id(), g);
        }
        return g;
    }

    private static Group resolve(MapLayer layer, Briefs.TownBrief town) {
        Briefs.NationBrief n = ClientClaims.nation(town.nation());
        if (layer == MapLayer.ALLIANCES && n != null && n.alliance() != null) {
            Briefs.AllianceBrief a = ClientClaims.alliance(n.alliance());
            if (a != null) return new Group(a.id(), a.name(), a.color() & 0xFFFFFF, MapLayer.ALLIANCES);
        }
        if (layer == MapLayer.PROVINCES && town.province() != null) {
            Briefs.ProvinceBrief p = ClientClaims.province(town.province());
            if (p != null) return new Group(p.id(), p.name(), p.color() & 0xFFFFFF, MapLayer.PROVINCES);
        }
        if (layer != MapLayer.TOWNS && n != null) return new Group(n.id(), n.name(), n.color() & 0xFFFFFF, MapLayer.NATIONS);
        return new Group(town.id(), town.name(), town.color() & 0xFFFFFF, MapLayer.TOWNS);
    }

    /** The group id of a town under the current layer (null for an unknown town). */
    public static UUID groupId(UUID town) {
        Briefs.TownBrief t = ClientClaims.town(town);
        return t == null ? null : group(t).id();
    }

    /** Whether the chunk's neighbour in a direction lies outside the town's group (draw the group's border there). */
    public static boolean groupEdge(Map<Long, UUID> claims, UUID town, int cx, int cz, int dx, int dz) {
        UUID other = claims.get(ChunkPos.asLong(cx + dx, cz + dz));
        if (other == null) return true;
        if (other.equals(town)) return false;
        UUID a = groupId(town), b = groupId(other);
        return a == null || !a.equals(b);
    }

    /** The names to draw over a dimension's claims under the current layer (cached until claims, briefs or layer change). */
    public static List<Territories.Label> labels(String dim) {
        return LABELS.computeIfAbsent(dim, d -> Territories.labels(ClientClaims.claims(d), MapStyle::groupId));
    }

    /** The group with this id among the current layer's (null if no town shows as it). */
    public static Group groupById(UUID group) {
        Map<UUID, Group> m = BY_ID.get(layer());
        return m == null ? null : m.get(group);
    }

    // --- chunks ------------------------------------------------------------------------------------------------------------

    public static State state(String dim, Briefs.TownBrief town, long chunk) {
        if (ClientClaims.siegeAt(dim, chunk) != null) return State.BESIEGED;
        return ClientClaims.occupied(town.id(), chunk) ? State.OCCUPIED : State.NORMAL;
    }

    /** The fill: the group's colour, darkened for managed land; a siege shows the besieger's colour. */
    public static int fill(String dim, Briefs.TownBrief town, long chunk) {
        SiegeBrief s = ClientClaims.siegeAt(dim, chunk);
        return s != null ? s.besiegerColor() : base(town);
    }

    /** The fill away from sieges: the group's colour, darkened for managed land. */
    public static int base(Briefs.TownBrief town) {
        int c = group(town).color();
        return managed(town) ? mix(c, 0x000000, 0.6f) : c;
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

    /** The border and stripe colour of managed land: the group's colour, lightened. */
    public static int accent(Briefs.TownBrief town) {
        return mix(group(town).color(), 0xFFFFFF, 0.3f);
    }

    /** Whether pixel (x, z) of a chunk (0..15) lies on the stripes of managed land (sparse, the other way to a siege's). */
    public static boolean hatch(int x, int z) {
        return ((x - z) & 7) == 0;
    }

    public static int mix(int a, int b, float t) {
        int r = Math.round((a >> 16 & 255) * (1 - t) + (b >> 16 & 255) * t), g = Math.round((a >> 8 & 255) * (1 - t) + (b >> 8 & 255) * t),
                bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }

    public static String label(Briefs.TownBrief town) {
        Briefs.NationBrief n = ClientClaims.nation(town.nation());
        return n == null ? town.name() : town.name() + " (" + n.name() + ")";
    }

    /** The town's whole standing, outermost last: "Highmoor · North March · Valoria · Iron Pact". */
    public static String hierarchy(Briefs.TownBrief town) {
        StringBuilder b = new StringBuilder(town.name());
        Briefs.ProvinceBrief p = ClientClaims.province(town.province());
        if (p != null) b.append(" · ").append(p.name());
        Briefs.NationBrief n = ClientClaims.nation(town.nation());
        if (n != null) {
            b.append(" · ").append(n.name());
            Briefs.AllianceBrief a = ClientClaims.alliance(n.alliance());
            if (a != null) b.append(" · ").append(a.name());
        }
        return b.toString();
    }

    /** Whether the chunk's neighbour in a direction belongs to another owner (draw a border there). */
    public static boolean edge(Map<Long, UUID> claims, UUID town, int cx, int cz, int dx, int dz) {
        return !town.equals(claims.get(ChunkPos.asLong(cx + dx, cz + dz)));
    }
}
