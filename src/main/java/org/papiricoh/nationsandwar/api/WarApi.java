package org.papiricoh.nationsandwar.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import org.papiricoh.nationsandwar.core.NW;
import org.papiricoh.nationsandwar.model.Nation;
import org.papiricoh.nationsandwar.model.Result;
import org.papiricoh.nationsandwar.registry.NWRegistry;
import org.papiricoh.nationsandwar.war.War;
import org.papiricoh.nationsandwar.war.WarService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Wars for other mods: declaring between any two nations (managed ones included), scoring from outside, deciding a managed
 * town's heart, planting banners for NPC fighters, and telling the sieges which nation an entity fights for. Server side.
 * The life of every war is reported by {@code api.event.WarEvent}.
 */
public final class WarApi {
    /**
     * How a war is declared. {@code -1} (or "") keeps the config's value.
     *
     * @param pay            take the war's price from the attacker's bank
     * @param preparation    ticks before it turns active
     * @param duration       ticks it stays active at most
     * @param targetScore    score that wins it
     * @param reasonKey      a lang key naming why (shown in the war's notices and the GUI)
     * @param ignoreCooldown declare even if the pair fought recently
     */
    public record WarOptions(boolean pay, long preparation, long duration, int targetScore, String reasonKey, boolean ignoreCooldown) {
        /** A player's or NPC nation's declaration. */
        public static WarOptions ordinary(boolean pay) {
            return new WarOptions(pay, -1, -1, -1, "", false);
        }

        /** Free, with the config's times and target. */
        public static WarOptions free() {
            return ordinary(false);
        }

        public WarOptions times(long preparation, long duration) {
            return new WarOptions(pay, preparation, duration, targetScore, reasonKey, ignoreCooldown);
        }

        public WarOptions target(int score) {
            return new WarOptions(pay, preparation, duration, score, reasonKey, ignoreCooldown);
        }

        public WarOptions reason(String key) {
            return new WarOptions(pay, preparation, duration, targetScore, key, ignoreCooldown);
        }

        public WarOptions noCooldown() {
            return new WarOptions(pay, preparation, duration, targetScore, reasonKey, true);
        }
    }

    private static final List<Function<LivingEntity, UUID>> COMBATANTS = new CopyOnWriteArrayList<>();

    private WarApi() {
    }

    /**
     * Declares as a provider would: a managed nation's locks do not stop it (its provider is the one declaring), but the
     * ordinary rules do (allies, vassals, one war per pair, one attack at a time, the cooldown unless ignored) and
     * {@code WarEvent.Declare} may still veto it.
     */
    public static Result declare(MinecraftServer server, UUID attacker, UUID defender, WarOptions options) {
        Nation a = NW.pol(server).nations.get(attacker), d = NW.pol(server).nations.get(defender);
        if (a == null || d == null) return Result.fail("unknown_nation");
        return WarService.declare(server, a, d, options, true);
    }

    /** Every war that is not over (preparation, active, treaty). */
    public static List<War> wars(MinecraftServer server) {
        List<War> out = new ArrayList<>();
        for (War w : NW.data(server).wars.values()) if (w.phase != War.Phase.ENDED) out.add(w);
        return out;
    }

    public static War war(MinecraftServer server, UUID id) {
        return NW.data(server).wars.get(id);
    }

    /** The active war between two nations (on opposite sides), or null. */
    public static War activeWar(MinecraftServer server, UUID a, UUID b) {
        return WarService.activeWar(NW.data(server), a, b);
    }

    /** Points for {@code nation}'s side (a skirmish won, a champion slain…). Ignored unless the war is active. */
    public static void addScore(MinecraftServer server, War war, UUID nation, int points) {
        Boolean side = war.sideOf(nation);
        if (side == null || war.phase != War.Phase.ACTIVE || points == 0) return;
        war.score(side, points);
        NW.changed(server);
    }

    /** Ends the fighting now with {@code outcome} (then the treaty, or peace). */
    public static void end(MinecraftServer server, War war, War.Outcome outcome) {
        if (war.live()) WarService.end(server, war, outcome);
    }

    /**
     * The heart of {@code town} fell to {@code byNation}: for towns with no Core block, once their home chunk was taken
     * ({@code WarEvent.CoreOpened}). Scores the Core's points. False if the town was not open to it.
     */
    public static boolean townFallen(MinecraftServer server, War war, UUID town, UUID byNation) {
        return WarService.townFallen(server, war, town, byNation);
    }

    /**
     * Plants a Siege Banner at {@code pos} for {@code nation} (an NPC war party, a provider's officers) and starts the siege.
     * {@code requireDefendersOnline}: refuse, as for players, when too few defenders are online. The block is removed again
     * when refused.
     */
    public static Result plantBannerAs(ServerLevel level, BlockPos pos, UUID nation, boolean requireDefendersOnline) {
        var before = level.getBlockState(pos);
        level.setBlockAndUpdate(pos, NWRegistry.SIEGE_BANNER.get().defaultBlockState());
        Result r = WarService.plantBanner(level, pos, nation, !requireDefendersOnline);
        if (!r.ok()) level.setBlockAndUpdate(pos, before);
        else WarService.logPlanted(level, pos, before);
        return r;
    }

    /**
     * Tells sieges, war zones and kill scores which nation an entity fights for: return its nation's id, or null to pass.
     * Asked for every living entity that is not a player of a nation in the war and not one of this mod's guards (a
     * provider's officers; a player who serves a managed nation from outside its wars).
     */
    public static void registerCombatant(Function<LivingEntity, UUID> resolver) {
        COMBATANTS.add(resolver);
    }

    /** The nation {@code e} fights for according to the registered resolvers (the first that answers), or null. */
    public static UUID combatantNation(LivingEntity e) {
        for (Function<LivingEntity, UUID> f : COMBATANTS) {
            UUID n = f.apply(e);
            if (n != null) return n;
        }
        return null;
    }
}
