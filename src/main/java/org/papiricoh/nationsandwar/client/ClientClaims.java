package org.papiricoh.nationsandwar.client;

import net.minecraft.world.level.ChunkPos;
import org.papiricoh.nationsandwar.model.Nbt;
import org.papiricoh.nationsandwar.network.BriefsPayload;
import org.papiricoh.nationsandwar.network.ClaimsPayload;
import org.papiricoh.nationsandwar.network.SelfPayload;
import org.papiricoh.nationsandwar.network.SiegesPayload;
import org.papiricoh.nationsandwar.view.Briefs;
import org.papiricoh.nationsandwar.view.SiegeBrief;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The client's copy of the public political map: who owns each chunk (per dimension), every town's, province's, nation's
 * and alliance's brief, the live wars and sieges, and the local player's own town and nation. Read it from the render
 * thread (maps, notices, HUD); {@link #listen} to hear about changes.
 */
public final class ClientClaims {
    public enum Change {CLAIMS, BRIEFS, SIEGES, SELF}

    private static final Map<String, Map<Long, UUID>> CLAIMS = new HashMap<>();
    private static final Map<UUID, Briefs.TownBrief> TOWNS = new HashMap<>();
    private static final Map<UUID, Briefs.ProvinceBrief> PROVINCES = new HashMap<>();
    private static final Map<UUID, Briefs.NationBrief> NATIONS = new HashMap<>();
    private static final Map<UUID, Briefs.AllianceBrief> ALLIANCES = new HashMap<>();
    private static List<Briefs.WarBrief> wars = List.of();
    private static List<SiegeBrief> sieges = List.of();
    private static UUID myTown, myNation;
    private static final List<java.util.function.Consumer<Change>> LISTENERS = new CopyOnWriteArrayList<>();
    /** Chunk keys changed by the last claims packet, per dimension (for incremental map redraws). */
    private static final Map<String, Set<Long>> LAST_CHANGED = new HashMap<>();

    private ClientClaims() {
    }

    // --- packets -----------------------------------------------------------------------------------------------------------

    public static void claims(ClaimsPayload p) {
        Map<Long, UUID> m = CLAIMS.computeIfAbsent(p.dimension(), k -> new HashMap<>());
        Set<Long> changed = LAST_CHANGED.computeIfAbsent(p.dimension(), k -> new HashSet<>());
        changed.clear();
        if (p.full()) {
            changed.addAll(m.keySet());
            m.clear();
        }
        for (int i = 0; i < p.chunks().length; i++) {
            changed.add(p.chunks()[i]);
            if (p.towns()[i] == null) m.remove(p.chunks()[i]);
            else m.put(p.chunks()[i], p.towns()[i]);
        }
        fire(Change.CLAIMS);
    }

    public static void briefs(BriefsPayload p) {
        Briefs b = Briefs.read(p.briefs());
        TOWNS.clear();
        PROVINCES.clear();
        NATIONS.clear();
        ALLIANCES.clear();
        b.towns().forEach(t -> TOWNS.put(t.id(), t));
        b.provinces().forEach(t -> PROVINCES.put(t.id(), t));
        b.nations().forEach(t -> NATIONS.put(t.id(), t));
        b.alliances().forEach(t -> ALLIANCES.put(t.id(), t));
        wars = List.copyOf(b.wars());
        fire(Change.BRIEFS);
    }

    public static void sieges(SiegesPayload p) {
        sieges = List.copyOf(SiegeBrief.readAll(p.sieges()));
        fire(Change.SIEGES);
    }

    public static void self(SelfPayload p) {
        myTown = Nbt.getUuid(p.self(), "town");
        myNation = Nbt.getUuid(p.self(), "nation");
        fire(Change.SELF);
    }

    public static void clear() {
        CLAIMS.clear();
        TOWNS.clear();
        PROVINCES.clear();
        NATIONS.clear();
        ALLIANCES.clear();
        wars = List.of();
        sieges = List.of();
        myTown = myNation = null;
    }

    // --- queries -----------------------------------------------------------------------------------------------------------

    public static Briefs.TownBrief townAt(String dimension, int chunkX, int chunkZ) {
        Map<Long, UUID> m = CLAIMS.get(dimension);
        UUID id = m == null ? null : m.get(ChunkPos.asLong(chunkX, chunkZ));
        return id == null ? null : TOWNS.get(id);
    }

    /** chunk key → town id for a dimension (read-only). */
    public static Map<Long, UUID> claims(String dimension) {
        return java.util.Collections.unmodifiableMap(CLAIMS.getOrDefault(dimension, Map.of()));
    }

    public static Set<Long> lastChanged(String dimension) {
        return LAST_CHANGED.getOrDefault(dimension, Set.of());
    }

    public static Collection<String> dimensions() {
        return new ArrayList<>(CLAIMS.keySet());
    }

    public static Briefs.TownBrief town(UUID id) {
        return id == null ? null : TOWNS.get(id);
    }

    public static Collection<Briefs.TownBrief> towns() {
        return TOWNS.values();
    }

    public static Briefs.ProvinceBrief province(UUID id) {
        return id == null ? null : PROVINCES.get(id);
    }

    public static Briefs.NationBrief nation(UUID id) {
        return id == null ? null : NATIONS.get(id);
    }

    public static Collection<Briefs.NationBrief> nations() {
        return NATIONS.values();
    }

    public static Briefs.AllianceBrief alliance(UUID id) {
        return id == null ? null : ALLIANCES.get(id);
    }

    public static List<Briefs.WarBrief> wars() {
        return wars;
    }

    public static List<SiegeBrief> sieges() {
        return sieges;
    }

    public static SiegeBrief siegeAt(String dimension, long chunk) {
        for (SiegeBrief s : sieges) if (s.dimension().equals(dimension) && s.chunk() == chunk) return s;
        return null;
    }

    /** Whether a town's chunk has been taken in a live war. */
    public static boolean occupied(UUID town, long chunk) {
        for (Briefs.WarBrief w : wars) {
            int i = w.occupiedTowns().indexOf(town);
            if (i >= 0) for (long c : w.occupiedChunks().get(i)) if (c == chunk) return true;
        }
        return false;
    }

    /** Whether a town's nation is in a live war. */
    public static boolean atWar(UUID nation) {
        for (Briefs.WarBrief w : wars) if (w.attackers().contains(nation) || w.defenders().contains(nation)) return true;
        return false;
    }

    public static boolean enemies(UUID a, UUID b) {
        if (a == null || b == null) return false;
        for (Briefs.WarBrief w : wars)
            if (w.attackers().contains(a) && w.defenders().contains(b) || w.defenders().contains(a) && w.attackers().contains(b)) return true;
        return false;
    }

    public static boolean allied(UUID a, UUID b) {
        if (a == null || b == null) return false;
        if (a.equals(b)) return true;
        Briefs.NationBrief x = nation(a), y = nation(b);
        return x != null && y != null && x.alliance() != null && x.alliance().equals(y.alliance());
    }

    public static UUID myTown() {
        return myTown;
    }

    public static UUID myNation() {
        return myNation;
    }

    /** The colour a town shows on maps: its nation's, else its own. */
    public static int mapColor(Briefs.TownBrief t) {
        Briefs.NationBrief n = nation(t.nation());
        return n != null ? n.color() : t.color();
    }

    /** Whether a nation is run by another mod ({@code api.ManagedNations}). */
    public static boolean managed(UUID nation) {
        Briefs.NationBrief n = nation(nation);
        return n != null && n.managed();
    }

    /** The lang prefix of a nation's government names: the managing mod's titles, else {@code nationsandwar.gov.<id>}. */
    public static String titles(UUID nation, String government) {
        Briefs.NationBrief n = nation(nation);
        return n != null && !n.titles().isEmpty() ? n.titles() : "nationsandwar.gov." + government;
    }

    // --- listeners ---------------------------------------------------------------------------------------------------------

    public static void listen(java.util.function.Consumer<Change> listener) {
        LISTENERS.add(listener);
    }

    private static void fire(Change c) {
        for (var l : LISTENERS) l.accept(c);
    }
}
