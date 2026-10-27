package org.papiricoh.nationsandwar.war;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.papiricoh.nationsandwar.NWConfig;
import org.papiricoh.nationsandwar.NationsAndWar;
import org.papiricoh.nationsandwar.core.NW;
import org.papiricoh.nationsandwar.core.Notices;
import org.papiricoh.nationsandwar.data.NWSavedData;
import org.papiricoh.nationsandwar.model.Nation;
import org.papiricoh.nationsandwar.model.Perm;
import org.papiricoh.nationsandwar.model.Politics;
import org.papiricoh.nationsandwar.model.Result;
import org.papiricoh.nationsandwar.model.Town;
import org.papiricoh.nationsandwar.network.NoticePayload;
import org.papiricoh.nationsandwar.network.SiegesPayload;
import org.papiricoh.nationsandwar.npc.GuardEntity;
import org.papiricoh.nationsandwar.npc.NpcService;
import org.papiricoh.nationsandwar.registry.NWRegistry;
import org.papiricoh.nationsandwar.view.SiegeBrief;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Wars in the world: declaring, the phases, sieges and their control bars, Town Cores, scoring, the end (every changed block
 * restored) and the treaty. State lives in {@link NWSavedData#wars}; pure rules in {@link WarRules} and {@link SiegeControl}.
 */
@EventBusSubscriber(modid = NationsAndWar.MODID)
public final class WarService {
    private static boolean siegesShown;

    private WarService() {
    }

    // --- queries -----------------------------------------------------------------------------------------------------------

    public static boolean atWar(NWSavedData data, UUID a, UUID b) {
        for (War w : data.wars.values()) if (w.live() && w.enemies(a, b)) return true;
        return false;
    }

    public static War activeWar(NWSavedData data, UUID a, UUID b) {
        for (War w : data.wars.values()) if (w.phase == War.Phase.ACTIVE && w.enemies(a, b)) return w;
        return null;
    }

    public static boolean nationAtWar(MinecraftServer server, UUID nation) {
        for (War w : NW.data(server).wars.values()) if (w.live() && w.involves(nation)) return true;
        return false;
    }

    public static boolean townAtWar(MinecraftServer server, Town t) {
        return t.nation != null && nationAtWar(server, t.nation);
    }

    /** The live war, if any, in which chunk {@code chunk} of {@code town} is a war zone (besieged or occupied). */
    public static War warZone(NWSavedData data, Town town, long chunk) {
        for (War w : data.wars.values()) {
            if (w.phase != War.Phase.ACTIVE) continue;
            if (w.isOccupied(town.id, chunk)) return w;
            for (Siege s : w.sieges) if (s.town.equals(town.id) && s.chunk == chunk) return w;
        }
        return null;
    }

    /** Whether a player stands in a war zone (no teleporting out of a fight). */
    public static boolean inCombat(MinecraftServer server, ServerPlayer p) {
        Town t = NW.townAt(p.level(), p.blockPosition());
        return t != null && warZone(NW.data(server), t, NW.chunk(p.blockPosition())) != null;
    }

    /** Whether {@code player} may build and break here because of the war: both sides may, in the war zone. */
    public static War fightsHere(MinecraftServer server, UUID player, Town town, long chunk) {
        NWSavedData data = NW.data(server);
        War w = warZone(data, town, chunk);
        if (w == null) return null;
        Nation n = data.politics.nationOfPlayer(player);
        return n != null && w.involves(n.id) ? w : null;
    }

    // --- actions -----------------------------------------------------------------------------------------------------------

    public static Result action(ServerPlayer p, String sub, CompoundTag a) {
        MinecraftServer server = p.getServer();
        NWSavedData data = NW.data(server);
        Politics pol = data.politics;
        Nation mine = pol.nationOfPlayer(p.getUUID());
        if (mine == null) return Result.fail("no_nation");
        switch (sub) {
            case "declare": {
                if (!pol.canNation(p.getUUID(), mine, Perm.DECLARE_WAR)) return Result.fail("no_perm");
                Nation target = pol.nations.get(UUID.fromString(a.getString("nation")));
                if (target == null) return Result.fail("unknown_nation");
                return declare(server, mine, target, true);
            }
            case "peace": {
                if (!pol.canNation(p.getUUID(), mine, Perm.SIGN_TREATY)) return Result.fail("no_perm");
                War w = primaryWar(data, mine.id, a);
                if (w == null) return Result.fail("war.none");
                boolean side = w.attacker.equals(mine.id);
                if (w.peaceOfferedByAttackers != null && w.peaceOfferedByAttackers != side) {
                    end(server, w, War.Outcome.WHITE_PEACE);
                    return Result.ok("war.white_peace");
                }
                w.peaceOfferedByAttackers = side;
                Nation other = pol.nations.get(side ? w.defender : w.attacker);
                if (other != null && other.npc) NpcService.peaceOffered(server, other, mine, w);
                else if (other != null) Notices.toNation(server, other, NoticePayload.Kind.WAR, Component.translatable("nationsandwar.notice.peace_offered"),
                        Component.literal(mine.name), "handshake");
                return Result.ok("war.peace_offered");
            }
            case "surrender": {
                if (!pol.canNation(p.getUUID(), mine, Perm.SIGN_TREATY)) return Result.fail("no_perm");
                War w = primaryWar(data, mine.id, a);
                if (w == null) return Result.fail("war.none");
                end(server, w, w.attacker.equals(mine.id) ? War.Outcome.DEFENDERS : War.Outcome.ATTACKERS);
                return Result.ok("war.surrendered");
            }
            case "terms": {
                War w = treatyWar(data, mine);
                if (w == null) return Result.fail("war.no_treaty");
                if (!pol.canNation(p.getUUID(), mine, Perm.SIGN_TREATY)) return Result.fail("no_perm");
                Treaty t = new Treaty();
                for (Tag e : a.getList("annex", Tag.TAG_STRING)) t.annex.add(UUID.fromString(e.getAsString()));
                t.tribute = a.getInt("tribute");
                t.vassalDays = a.getInt("vassal");
                Nation loser = pol.nations.get(w.loser());
                if (loser == null || !Treaty.allowed(w, t, loser.bank, loser.capital, NWConfig.get(NWConfig.MAX_VASSAL_DAYS)))
                    return Result.fail("war.bad_terms");
                w.terms = t;
                return Result.ok("war.terms_set");
            }
            case "confirm": {
                War w = treatyWar(data, mine);
                if (w == null) return Result.fail("war.no_treaty");
                if (!pol.canNation(p.getUUID(), mine, Perm.SIGN_TREATY)) return Result.fail("no_perm");
                applyTreaty(server, w);
                return Result.ok("war.treaty_signed");
            }
            default:
                return Result.fail("unknown_action");
        }
    }

    private static War primaryWar(NWSavedData data, UUID nation, CompoundTag a) {
        for (War w : data.wars.values()) {
            if (!w.live() || !(w.attacker.equals(nation) || w.defender.equals(nation))) continue;
            if (a.contains("war") && !w.id.toString().equals(a.getString("war"))) continue;
            return w;
        }
        return null;
    }

    private static War treatyWar(NWSavedData data, Nation winner) {
        for (War w : data.wars.values()) if (w.phase == War.Phase.TREATY && winner.id.equals(w.winner())) return w;
        return null;
    }

    /** Declares war; {@code pay} takes the price from the attacker's bank (NPC nations declare for free). */
    public static Result declare(MinecraftServer server, Nation attacker, Nation defender, boolean pay) {
        NWSavedData data = NW.data(server);
        long now = server.overworld().getGameTime();
        Result ok = WarRules.canDeclare(data.politics, data.wars.values(), data.lastWar, attacker, defender, now, NWConfig.minutes(NWConfig.WAR_COOLDOWN_MINUTES));
        if (!ok.ok()) return ok;
        int cost = NWConfig.get(NWConfig.WAR_COST);
        if (pay && attacker.bank < cost) return Result.fail("nation_poor", cost);
        if (pay) attacker.bank -= cost;
        if (defender.npc) defender.addOpinion(attacker.id, -40);
        War w = new War(UUID.randomUUID(), attacker.id, defender.id);
        WarRules.gatherSides(data.politics, w);
        w.declared = now;
        w.activeAt = now + NWConfig.minutes(NWConfig.PREPARATION_MINUTES);
        w.deadline = w.activeAt + NWConfig.minutes(NWConfig.DURATION_MINUTES);
        if (w.activeAt <= now) w.phase = War.Phase.ACTIVE;
        data.wars.put(w.id, w);
        NW.changed(server);
        Notices.toAll(server, NoticePayload.Kind.WAR, Component.translatable("nationsandwar.notice.war_declared"),
                Component.translatable("nationsandwar.notice.vs", attacker.name, defender.name), attacker.color, "war");
        return Result.ok("war.declared", defender.name);
    }

    // --- sieges ------------------------------------------------------------------------------------------------------------

    /**
     * A Siege Banner was placed by {@code placer} (a player's or a guard's nation) at {@code pos}. Returns why it may not
     * stand, or ok after starting the siege.
     */
    public static Result plantBanner(ServerLevel level, BlockPos pos, UUID placerNation, boolean npcPlacer) {
        MinecraftServer server = level.getServer();
        NWSavedData data = NW.data(server);
        Politics pol = data.politics;
        long chunk = NW.chunk(pos);
        Town town = pol.townAt(NW.dim(level), chunk);
        if (town == null || town.nation == null || placerNation == null) return Result.fail("siege.not_enemy");
        War w = activeWar(data, placerNation, town.nation);
        if (w == null) return Result.fail("siege.no_war");
        if (w.isOccupied(town.id, chunk)) return Result.fail("siege.occupied");
        if (w.siegeAt(NW.dim(level), chunk) != null) return Result.fail("siege.already");
        boolean side = w.sideOf(placerNation);
        long running = w.sieges.stream().filter(s -> s.byAttackers == side).count();
        if (running >= NWConfig.get(NWConfig.MAX_SIEGES)) return Result.fail("siege.too_many", NWConfig.get(NWConfig.MAX_SIEGES));
        if (!onFront(pol, w, town, chunk)) return Result.fail("siege.not_front");
        Nation defender = pol.nations.get(town.nation);
        if (!npcPlacer && !defender.npc) {
            int online = 0;
            for (UUID n : side ? w.defenders : w.attackers) {
                Nation dn = pol.nations.get(n);
                if (dn == null) continue;
                for (UUID c : pol.citizens(dn)) if (NW.online(server, c)) online++;
            }
            int min = NWConfig.get(NWConfig.MIN_DEFENDERS_ONLINE);
            if (online < min) return Result.fail("siege.defenders_offline", min);
        }
        Siege s = w.newSiege(town.id, NW.dim(level), chunk, pos.asLong(), side, level.getGameTime());
        NW.changed(server);
        Nation besieger = pol.nations.get(placerNation);
        Component sub = Component.literal(town.name + " ← " + (besieger == null ? "?" : besieger.name));
        notifySides(server, w, Component.translatable("nationsandwar.notice.siege_started"), sub, besieger == null ? 0xFF5555 : besieger.color, "siege");
        NpcService.siegeStarted(server, w, s);
        return Result.ok("siege.started", town.name);
    }

    /** A chunk may be besieged if it touches land that is not the town's, or a chunk of it already occupied. */
    private static boolean onFront(Politics pol, War w, Town town, long chunk) {
        int x = ChunkPos.getX(chunk), z = ChunkPos.getZ(chunk);
        long[] around = {ChunkPos.asLong(x + 1, z), ChunkPos.asLong(x - 1, z), ChunkPos.asLong(x, z + 1), ChunkPos.asLong(x, z - 1)};
        for (long c : around) if (!town.claims.contains(c) || w.isOccupied(town.id, c)) return true;
        return false;
    }

    /** A Siege Banner was broken: its siege fails (whoever broke it). */
    public static void bannerBroken(ServerLevel level, BlockPos pos) {
        NWSavedData data = NW.data(level.getServer());
        for (War w : data.wars.values()) {
            Siege s = w.siegeAt(NW.dim(level), NW.chunk(pos));
            if (s != null && s.banner == pos.asLong()) {
                w.sieges.remove(s);
                Town t = data.politics.towns.get(s.town);
                notifySides(level.getServer(), w, Component.translatable("nationsandwar.notice.siege_broken"),
                        Component.literal(t == null ? "?" : t.name), 0x55FF55, "shield");
                NW.changed(level.getServer());
                return;
            }
        }
    }

    /**
     * An enemy broke a Town Core. Returns true if the hit counted (the block must stay until the last hit). Outside a fallen
     * home chunk the Core is simply protected.
     */
    public static boolean hitCore(ServerLevel level, BlockPos pos, ServerPlayer player) {
        MinecraftServer server = level.getServer();
        NWSavedData data = NW.data(server);
        Town town = NW.townAt(level, pos);
        Nation mine = data.politics.nationOfPlayer(player.getUUID());
        if (town == null || town.core != pos.asLong() || mine == null || town.nation == null) return false;
        War w = activeWar(data, mine.id, town.nation);
        if (w == null || !w.coreOpen.contains(town.id) || w.fallen.contains(town.id)) return false;
        int hits = w.coreDamage.merge(town.id, 1, Integer::sum);
        int need = NWConfig.get(NWConfig.CORE_HITS);
        level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 1f, 0.6f + 0.4f * hits / need);
        level.sendParticles(ParticleTypes.LAVA, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0);
        if (hits >= need) {
            w.fallen.add(town.id);
            w.coreOpen.remove(town.id);
            w.score(w.sideOf(mine.id), NWConfig.get(NWConfig.CORE_POINTS));
            notifySides(server, w, Component.translatable("nationsandwar.notice.town_fell"), Component.literal(town.name), mine.color, "skull");
        } else {
            player.displayClientMessage(Component.translatable("nationsandwar.msg.core_hit", hits, need), true);
        }
        NW.changed(server);
        return true;
    }

    // --- ticking -----------------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 0) return;
        NWSavedData data = NW.data(server);
        long now = server.overworld().getGameTime();
        int target = NWConfig.get(NWConfig.TARGET_SCORE);
        boolean any = false;
        for (War w : new ArrayList<>(data.wars.values())) {
            switch (w.phase) {
                case PREPARATION -> {
                    if (now >= w.activeAt) {
                        w.phase = War.Phase.ACTIVE;
                        NW.changed(server);
                        notifySides(server, w, Component.translatable("nationsandwar.notice.war_active"), sidesName(data.politics, w), 0xFF5555, "war");
                    }
                }
                case ACTIVE -> {
                    for (Siege s : new ArrayList<>(w.sieges)) tickSiege(server, data, w, s);
                    any |= !w.sieges.isEmpty();
                    War.Outcome o = WarRules.check(w, target, now);
                    if (o != War.Outcome.NONE) end(server, w, o);
                }
                case TREATY -> {
                    if (now >= w.treatyUntil) applyTreaty(server, w);
                }
                case ENDED -> data.wars.remove(w.id);
            }
        }
        for (Nation n : data.politics.nations.values()) {
            if (n.overlord != null && now >= n.vassalUntil) {
                n.overlord = null;
                NW.changed(server);
            }
        }
        if (any || siegesShown) {
            PacketDistributor.sendToAllPlayers(new SiegesPayload(SiegeBrief.writeAll(allSieges(server))));
            siegesShown = any;
        }
    }

    private static void tickSiege(MinecraftServer server, NWSavedData data, War w, Siege s) {
        ServerLevel level = null;
        for (ServerLevel l : server.getAllLevels()) if (NW.dim(l).equals(s.dimension)) level = l;
        if (level == null) return;
        BlockPos banner = BlockPos.of(s.banner);
        if (!level.isLoaded(banner)) return;
        if (!level.getBlockState(banner).is(NWRegistry.SIEGE_BANNER.get())) {
            bannerBroken(level, banner);
            return;
        }
        Set<UUID> besiegers = s.byAttackers ? w.attackers : w.defenders, holders = s.byAttackers ? w.defenders : w.attackers;
        int r = NWConfig.get(NWConfig.SIEGE_RADIUS), a = 0, d = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(banner).inflate(r), LivingEntity::isAlive)) {
            UUID nation = null;
            if (e instanceof Player pl && !pl.isSpectator()) {
                Nation n = data.politics.nationOfPlayer(pl.getUUID());
                nation = n == null ? null : n.id;
            } else if (e instanceof GuardEntity g) {
                nation = g.nation();
            }
            if (nation == null) continue;
            if (besiegers.contains(nation)) a++;
            else if (holders.contains(nation)) d++;
        }
        float before = s.control;
        s.control = SiegeControl.step(s.control, a, d, (float) NWConfig.get(NWConfig.CONTROL_RATE), (float) NWConfig.get(NWConfig.CONTROL_DECAY));
        if (before != s.control) data.setDirty();
        Town town = data.politics.towns.get(s.town);
        if (s.control >= 1) {
            w.sieges.remove(s);
            level.setBlockAndUpdate(banner, Blocks.AIR.defaultBlockState());
            if (town != null) {
                w.occupied.computeIfAbsent(town.id, k -> new java.util.LinkedHashSet<>()).add(s.chunk);
                w.score(s.byAttackers, NWConfig.get(NWConfig.CHUNK_POINTS));
                boolean home = s.chunk == town.homeChunk;
                if (home) w.coreOpen.add(town.id);
                notifySides(server, w, Component.translatable(home ? "nationsandwar.notice.core_exposed" : "nationsandwar.notice.chunk_taken"),
                        Component.literal(town.name), 0xFFAA00, home ? "skull" : "flag");
            }
            NW.changed(server);
        } else if (s.control <= -1) {
            w.sieges.remove(s);
            level.setBlockAndUpdate(banner, Blocks.AIR.defaultBlockState());
            notifySides(server, w, Component.translatable("nationsandwar.notice.siege_failed"), Component.literal(town == null ? "?" : town.name),
                    0x55FF55, "shield");
            NW.changed(server);
        }
    }

    @SubscribeEvent
    public static void death(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim) || !(event.getSource().getEntity() instanceof ServerPlayer killer)) return;
        NWSavedData data = NW.data(victim.getServer());
        Nation a = data.politics.nationOfPlayer(killer.getUUID()), b = data.politics.nationOfPlayer(victim.getUUID());
        if (a == null || b == null) return;
        War w = activeWar(data, a.id, b.id);
        if (w == null) return;
        w.score(w.sideOf(a.id), NWConfig.get(NWConfig.KILL_POINTS));
        NW.changed(victim.getServer());
    }

    // --- the end -----------------------------------------------------------------------------------------------------------

    /** Ends the fighting: every war-zone block back as it was, banners down, then the treaty window (or peace). */
    public static void end(MinecraftServer server, War w, War.Outcome outcome) {
        NWSavedData data = NW.data(server);
        Politics pol = data.politics;
        for (Siege s : w.sieges) {
            for (ServerLevel l : server.getAllLevels()) {
                BlockPos b = BlockPos.of(s.banner);
                if (NW.dim(l).equals(s.dimension) && l.getBlockState(b).is(NWRegistry.SIEGE_BANNER.get())) l.setBlockAndUpdate(b, Blocks.AIR.defaultBlockState());
            }
        }
        w.sieges.clear();
        RestoreLog log = data.restore.remove(w.id);
        if (log != null) log.restore(server);
        w.outcome = outcome;
        long now = server.overworld().getGameTime();
        data.lastWar.put(WarRules.pair(w.attacker, w.defender), now);
        Nation att = pol.nations.get(w.attacker), def = pol.nations.get(w.defender);
        if (outcome == War.Outcome.WHITE_PEACE || w.winner() == null || pol.nations.get(w.loser()) == null) {
            w.phase = War.Phase.ENDED;
            notifySides(server, w, Component.translatable("nationsandwar.notice.white_peace"), sidesName(pol, w), 0xFFFFFF, "handshake");
        } else {
            Nation loser = pol.nations.get(w.loser());
            w.terms = Treaty.defaults(w, loser.bank, NWConfig.get(NWConfig.TRIBUTE_SHARE), loser.capital, NWConfig.get(NWConfig.VASSAL_DAYS));
            w.phase = War.Phase.TREATY;
            w.treatyUntil = now + NWConfig.minutes(NWConfig.TREATY_MINUTES);
            Nation winner = pol.nations.get(w.winner());
            notifySides(server, w, Component.translatable("nationsandwar.notice.war_won"), Component.literal(winner == null ? "?" : winner.name),
                    winner == null ? 0xFFD700 : winner.color, "crown");
            if (winner != null && winner.npc) applyTreaty(server, w);
        }
        NW.changed(server);
    }

    /** Applies the treaty's terms (annexation, tribute, vassalage) and closes the war. */
    public static void applyTreaty(MinecraftServer server, War w) {
        Politics pol = NW.pol(server);
        Nation winner = pol.nations.get(w.winner()), loser = pol.nations.get(w.loser());
        Treaty t = w.terms;
        if (winner != null && loser != null && t != null) {
            for (UUID id : t.annex) {
                Town town = pol.towns.get(id);
                if (town == null || !loser.towns.contains(id) || id.equals(loser.capital)) continue;
                pol.detachTown(loser, town);
                pol.attachTown(winner, town);
            }
            int tribute = Math.min(t.tribute, loser.bank);
            loser.bank -= tribute;
            winner.bank += tribute;
            if (t.vassalDays > 0) {
                loser.overlord = winner.id;
                loser.vassalUntil = server.overworld().getGameTime() + t.vassalDays * 24000L;
            }
            notifySides(server, w, Component.translatable("nationsandwar.notice.treaty_signed"),
                    Component.translatable("nationsandwar.notice.treaty_terms", t.annex.size(), tribute, t.vassalDays), winner.color, "scroll");
        }
        w.phase = War.Phase.ENDED;
        NW.changed(server);
    }

    // --- helpers -----------------------------------------------------------------------------------------------------------

    public static void notifySides(MinecraftServer server, War w, Component title, Component sub, int color, String icon) {
        Politics pol = NW.pol(server);
        Set<UUID> players = new java.util.HashSet<>();
        for (UUID n : w.attackers) if (pol.nations.get(n) != null) players.addAll(pol.citizens(pol.nations.get(n)));
        for (UUID n : w.defenders) if (pol.nations.get(n) != null) players.addAll(pol.citizens(pol.nations.get(n)));
        Notices.toPlayers(server, players, NoticePayload.Kind.WAR, title, sub, color, icon);
    }

    private static Component sidesName(Politics pol, War w) {
        Nation a = pol.nations.get(w.attacker), d = pol.nations.get(w.defender);
        return Component.translatable("nationsandwar.notice.vs", a == null ? "?" : a.name, d == null ? "?" : d.name);
    }

    public static List<SiegeBrief> siegeBriefs(MinecraftServer server, War w) {
        Politics pol = NW.pol(server);
        List<SiegeBrief> out = new ArrayList<>();
        for (Siege s : w.sieges) {
            Nation b = pol.nations.get(s.byAttackers ? w.attacker : w.defender), d = pol.nations.get(s.byAttackers ? w.defender : w.attacker);
            out.add(new SiegeBrief(w.id, s.id, s.town, s.dimension, s.chunk, s.banner, s.control, b == null ? 0xFF5555 : b.color,
                    d == null ? 0x5555FF : d.color, b == null ? "?" : b.name, d == null ? "?" : d.name));
        }
        return out;
    }

    public static List<SiegeBrief> allSieges(MinecraftServer server) {
        List<SiegeBrief> out = new ArrayList<>();
        for (War w : NW.data(server).wars.values()) if (w.phase == War.Phase.ACTIVE) out.addAll(siegeBriefs(server, w));
        return out;
    }
}
