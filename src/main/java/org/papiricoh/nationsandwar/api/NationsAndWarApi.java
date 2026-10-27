package org.papiricoh.nationsandwar.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.papiricoh.nationsandwar.core.ApiEvents;
import org.papiricoh.nationsandwar.core.NW;
import org.papiricoh.nationsandwar.model.ChunkPerms;
import org.papiricoh.nationsandwar.model.Flag;
import org.papiricoh.nationsandwar.model.Nation;
import org.papiricoh.nationsandwar.model.Politics;
import org.papiricoh.nationsandwar.model.Town;
import org.papiricoh.nationsandwar.registry.NWRegistry;
import org.papiricoh.nationsandwar.war.WarService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The entry point for other mods (server side). Read the politics, run managed nations and towns, and hear about changes
 * through {@code api.event}. Wars are in {@link WarApi}, protection exceptions in {@link ProtectionBypass}, reserved land
 * in {@link ClaimVetoes}, the GUI of managed nations in {@code api.client.ManagedNationScreens}.
 *
 * <p>Records ({@link Nation}, {@link Town}…) are the live ones: read them freely, change them only through this class or
 * {@link Politics}' rules, and call {@link #changed} after changing anything yourself. {@code Nation.ext} /
 * {@code Town.ext} are yours to keep data in (one key per mod).
 */
public final class NationsAndWarApi {
    public static final String MODID = "nationsandwar";
    /** Bumped when the API changes in a way callers must know about. */
    public static final int VERSION = 1;

    private NationsAndWarApi() {
    }

    // --- reading -----------------------------------------------------------------------------------------------------------

    public static Politics politics(MinecraftServer server) {
        return NW.pol(server);
    }

    public static Town townOf(MinecraftServer server, UUID player) {
        return NW.pol(server).townOf(player);
    }

    public static Nation nationOf(MinecraftServer server, UUID player) {
        return NW.pol(server).nationOfPlayer(player);
    }

    public static Town townAt(Level level, BlockPos pos) {
        return NW.townAt(level, pos);
    }

    public static Town townAt(MinecraftServer server, String dimension, long chunk) {
        return NW.pol(server).townAt(dimension, chunk);
    }

    public static boolean allied(MinecraftServer server, UUID nationA, UUID nationB) {
        return NW.pol(server).allied(nationA, nationB);
    }

    /** Whether two nations fight on opposite sides of a war in preparation or active. */
    public static boolean atWar(MinecraftServer server, UUID nationA, UUID nationB) {
        return WarService.atWar(NW.data(server), nationA, nationB);
    }

    /** The dimension id string the API uses ("minecraft:overworld"). */
    public static String dimension(Level level) {
        return NW.dim(level);
    }

    // --- managed nations ---------------------------------------------------------------------------------------------------

    /** The first nation run by {@code key}, or null. */
    public static Nation managedNation(MinecraftServer server, String key) {
        for (Nation n : NW.pol(server).nations.values()) if (key.equals(n.managedBy)) return n;
        return null;
    }

    public static List<Nation> managedNations(MinecraftServer server, String key) {
        List<Nation> out = new ArrayList<>();
        for (Nation n : NW.pol(server).nations.values()) if (key.equals(n.managedBy)) out.add(n);
        return out;
    }

    public static List<Town> managedTowns(MinecraftServer server, String key) {
        List<Town> out = new ArrayList<>();
        for (Town t : NW.pol(server).towns.values()) if (key.equals(t.managedBy)) out.add(t);
        return out;
    }

    /**
     * A new nation run by {@code key} (register its {@link ManagedNationProvider} first). {@code titles}: a lang prefix for
     * its government's names ("modid.gov.pit" for the noun, ".leader"…), or null for a kingdom's. Its first town becomes
     * its capital.
     */
    public static Nation createManagedNation(MinecraftServer server, String key, String name, String titles, int color) {
        Nation n = NW.pol(server).foundManagedNation(key, name, titles, color, server.overworld().getGameTime());
        NW.changed(server);
        return n;
    }

    /**
     * A new town of {@code nation}, run by the nation's provider: its home chunk is {@code centre}'s, plus {@code chunks}
     * where free. {@code core}: the Town Core's position, placed as a block when {@code placeCore} (sieges break it); a
     * town without one falls only through {@link WarApi#townFallen}. {@code perms}/{@code flags} may be null (defaults).
     */
    public static Town createManagedTown(ServerLevel level, Nation nation, String name, BlockPos centre, Collection<Long> chunks, BlockPos core,
                                         boolean placeCore, ChunkPerms perms, Map<Flag, Boolean> flags) {
        MinecraftServer server = level.getServer();
        Politics pol = NW.pol(server);
        BlockPos heart = core == null ? centre : core;
        Town t = pol.foundManagedTown(nation.managedBy, name, NW.dim(level), NW.chunk(centre), heart.asLong(), chunks, level.getGameTime());
        t.color = nation.color;
        if (perms != null) t.perms = perms;
        if (flags != null) t.flags.putAll(flags);
        pol.attachTown(nation, t);
        if (placeCore) level.setBlockAndUpdate(heart, NWRegistry.TOWN_CORE.get().defaultBlockState());
        else t.core = Long.MIN_VALUE;
        NW.changed(server);
        return t;
    }

    /** Claims a free chunk for a managed town (no cost, buffer or adjacency). False if the chunk is taken. */
    public static boolean claimFor(MinecraftServer server, Town town, long chunk) {
        boolean ok = NW.pol(server).claimManaged(town, chunk);
        if (ok) NW.changed(server);
        return ok;
    }

    /** Gives a chunk of a managed town back to the wilderness (never its home chunk). */
    public static boolean unclaimFor(MinecraftServer server, Town town, long chunk) {
        boolean ok = NW.pol(server).unclaim(null, town, chunk).ok();
        if (ok) NW.changed(server);
        return ok;
    }

    /** Removes a town: claims back to the wilderness, its Core block gone ({@code TownEvent.Removed} follows). */
    public static void raze(MinecraftServer server, Town town) {
        if (town.core != Long.MIN_VALUE) {
            for (ServerLevel l : server.getAllLevels()) {
                if (!NW.dim(l).equals(town.dimension)) continue;
                BlockPos core = BlockPos.of(town.core);
                if (l.isLoaded(core) && l.getBlockState(core).is(NWRegistry.TOWN_CORE.get())) l.setBlockAndUpdate(core, Blocks.AIR.defaultBlockState());
            }
        }
        NW.pol(server).removeTown(town);
        NW.changed(server);
    }

    /** Every chunk key of a square of {@code radius} chunks around {@code centre}'s chunk. */
    public static List<Long> square(BlockPos centre, int radius) {
        List<Long> out = new ArrayList<>();
        int cx = centre.getX() >> 4, cz = centre.getZ() >> 4;
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) out.add(ChunkPos.asLong(cx + dx, cz + dz));
        return out;
    }

    // --- housekeeping ------------------------------------------------------------------------------------------------------

    /** After changing records yourself: saves and refreshes every client and map at the end of the tick. */
    public static void changed(MinecraftServer server) {
        NW.changed(server);
    }

    /** Delivers the queued events now instead of at the end of the tick (tests, a caller that needs them in order). */
    public static void flushEvents() {
        ApiEvents.flush();
    }
}
