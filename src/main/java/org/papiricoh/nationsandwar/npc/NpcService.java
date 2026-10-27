package org.papiricoh.nationsandwar.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.papiricoh.nationsandwar.NWConfig;
import org.papiricoh.nationsandwar.NationsAndWar;
import org.papiricoh.nationsandwar.core.Emeralds;
import org.papiricoh.nationsandwar.core.NW;
import org.papiricoh.nationsandwar.core.Notices;
import org.papiricoh.nationsandwar.data.NWSavedData;
import org.papiricoh.nationsandwar.model.Alliance;
import org.papiricoh.nationsandwar.model.GovernmentType;
import org.papiricoh.nationsandwar.model.Nation;
import org.papiricoh.nationsandwar.model.Perm;
import org.papiricoh.nationsandwar.model.Politics;
import org.papiricoh.nationsandwar.model.Result;
import org.papiricoh.nationsandwar.model.Town;
import org.papiricoh.nationsandwar.network.NoticePayload;
import org.papiricoh.nationsandwar.registry.NWRegistry;
import org.papiricoh.nationsandwar.war.Siege;
import org.papiricoh.nationsandwar.war.War;
import org.papiricoh.nationsandwar.war.WarPlanner;
import org.papiricoh.nationsandwar.war.WarService;
import net.minecraft.world.phys.AABB;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;

/**
 * NPC nations in the world: generated villages adopted as towns (grouped into nations), their guards and envoys, the
 * diplomacy players do with them, their brain's decisions, war parties and siege reinforcements.
 */
@EventBusSubscriber(modid = NationsAndWar.MODID)
public final class NpcService {
    private record Pending(String dim, int chunkX, int chunkZ, String key) {
    }

    private static final Deque<Pending> QUEUE = new ArrayDeque<>();

    private NpcService() {
    }

    // --- village adoption --------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void chunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !event.isNewChunk() || !(event.getChunk() instanceof LevelChunk chunk)) return;
        if (!NWConfig.get(NWConfig.NPC_VILLAGES)) return;
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (Map.Entry<Structure, StructureStart> e : chunk.getAllStarts().entrySet()) {
            if (!e.getValue().isValid() || !registry.wrapAsHolder(e.getKey()).is(StructureTags.VILLAGE)) continue;
            ChunkPos at = chunk.getPos();
            QUEUE.add(new Pending(NW.dim(level), at.x, at.z, NW.dim(level) + "|" + at.toLong()));
        }
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        NWSavedData data = NW.data(server);
        if (!QUEUE.isEmpty()) {
            Pending p = QUEUE.poll();
            if (data.villages.add(p.key())) {
                data.setDirty();
                ServerLevel level = level(server, p.dim());
                long seed = server.getWorldData().worldGenOptions().seed() ^ p.key().hashCode();
                if (level != null && new java.util.Random(seed).nextDouble() < NWConfig.get(NWConfig.VILLAGE_CHANCE)) {
                    adopt(level, new ChunkPos(p.chunkX(), p.chunkZ()), NameGen.town(seed), seed);
                }
            }
        }
        long now = server.overworld().getGameTime();
        if (now - data.lastBrain >= NWConfig.get(NWConfig.BRAIN_INTERVAL)) {
            data.lastBrain = now;
            think(server, data, now / 24000);
        }
        if (server.getTickCount() % NWConfig.get(NWConfig.PLAN_TICKS) == 0) campaigns(server, data);
        if (server.getTickCount() % 6000 == 3000) garrison(server, data);
    }

    /** Makes an NPC town of the village whose start lies in {@code at}; joins a near NPC nation or founds one. */
    public static Town adopt(ServerLevel level, ChunkPos at, String name, long seed) {
        MinecraftServer server = level.getServer();
        NWSavedData data = NW.data(server);
        Politics pol = data.politics;
        String dim = NW.dim(level);
        if (pol.townAt(dim, at.toLong()) != null || pol.claimVeto.test(dim, at.toLong())) return null;
        BlockPos core = corePos(level, at);
        int radius = NWConfig.get(NWConfig.VILLAGE_RADIUS);
        Town town = pol.foundNpcTown(name, dim, at.toLong(), core.asLong(), radius, level.getGameTime());
        level.setBlockAndUpdate(core, NWRegistry.TOWN_CORE.get().defaultBlockState());
        Nation nation = null;
        int best = Integer.MAX_VALUE, range = NWConfig.get(NWConfig.GROUP_DISTANCE);
        for (Nation n : pol.nations.values()) {
            if (!n.npc || n.towns.size() >= NWConfig.get(NWConfig.MAX_NPC_TOWNS)) continue;
            Town cap = pol.towns.get(n.capital);
            if (cap == null || !cap.dimension.equals(dim)) continue;
            int d = Math.max(Math.abs(ChunkPos.getX(cap.homeChunk) - at.x), Math.abs(ChunkPos.getZ(cap.homeChunk) - at.z));
            if (d <= range && d < best) {
                best = d;
                nation = n;
            }
        }
        boolean founded = nation == null;
        if (founded) {
            GovernmentType[] govs = GovernmentType.values();
            nation = pol.foundNpcNation(NameGen.nation(seed), govs[(int) Math.floorMod(seed, govs.length)], town, level.getGameTime());
        } else {
            pol.attachTown(nation, town);
        }
        java.util.Random court = new java.util.Random(seed);
        NpcCourt.staff(pol, town, court, level.getGameTime());
        NpcCourt.staff(pol, nation, court, level.getGameTime());
        for (int i = 0; i < NWConfig.get(NWConfig.GUARDS_PER_TOWN); i++) spawnGuard(level, town, nation, core, 24, false);
        if (founded) spawnEnvoy(level, nation, core);
        NW.changed(server);
        NationsAndWar.LOGGER.info("[npc] {} adopted into {} at chunk {}", town.name, nation.name, at);
        return town;
    }

    /** A dry, open spot near the chunk's centre for the Core. */
    private static BlockPos corePos(ServerLevel level, ChunkPos at) {
        BlockPos fallback = null;
        for (int r = 0; r <= 6; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int x = at.getMiddleBlockX() + dx, z = at.getMiddleBlockZ() + dz;
                    BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
                    BlockState below = level.getBlockState(top.below());
                    if (fallback == null) fallback = top;
                    if (below.isSolid() && below.getFluidState().isEmpty() && level.getBlockState(top).canBeReplaced()
                            && level.getBlockState(top).getFluidState().isEmpty()) return top;
                }
            }
        }
        return fallback;
    }

    public static GuardEntity spawnGuard(ServerLevel level, Town town, Nation nation, BlockPos post, int radius, boolean soldier) {
        GuardEntity g = NWRegistry.GUARD.get().create(level);
        if (g == null) return null;
        BlockPos at = surface(level, post.offset(level.random.nextInt(7) - 3, 0, level.random.nextInt(7) - 3));
        g.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360, 0);
        g.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
        g.assign(town, nation, post, radius, soldier);
        level.addFreshEntity(g);
        return g;
    }

    private static void spawnEnvoy(ServerLevel level, Nation nation, BlockPos post) {
        EnvoyEntity e = NWRegistry.ENVOY.get().create(level);
        if (e == null) return;
        BlockPos at = surface(level, post.offset(2, 0, 0));
        e.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        e.assign(nation, post);
        level.addFreshEntity(e);
    }

    private static BlockPos surface(ServerLevel level, BlockPos p) {
        return new BlockPos(p.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ()), p.getZ());
    }

    /** Tops up each loaded NPC town's guards (and its capital's envoy). */
    private static void garrison(MinecraftServer server, NWSavedData data) {
        for (Town t : data.politics.towns.values()) {
            if (!t.npc || t.nation == null) continue;
            ServerLevel level = level(server, t.dimension);
            BlockPos core = BlockPos.of(t.core);
            if (level == null || !level.isLoaded(core) || level.getNearestPlayer(core.getX(), core.getY(), core.getZ(), 96, false) == null) continue;
            Nation n = data.politics.nations.get(t.nation);
            var box = new net.minecraft.world.phys.AABB(core).inflate(48);
            long guards = level.getEntitiesOfClass(GuardEntity.class, box, g -> !g.soldier && t.id.equals(g.town())).size();
            for (long i = guards; i < NWConfig.get(NWConfig.GUARDS_PER_TOWN); i++) spawnGuard(level, t, n, core, 24, false);
            if (n != null && n.npc && t.id.equals(n.capital) && level.getEntitiesOfClass(EnvoyEntity.class, box, e -> n.id.equals(e.nation())).isEmpty())
                spawnEnvoy(level, n, core);
        }
    }

    // --- the brain ---------------------------------------------------------------------------------------------------------

    private static void think(MinecraftServer server, NWSavedData data, long day) {
        Politics pol = data.politics;
        FactionBrain.drift(pol, data.wars.values(), day);
        NpcCourt.tick(server, day);
        var decisions = FactionBrain.decide(pol, data.wars.values(), NWConfig.get(NWConfig.NPC_WARS), n -> n.npc
                || pol.citizens(n).stream().anyMatch(c -> NW.online(server, c)));
        for (FactionBrain.Decision d : decisions) {
            Nation n = pol.nations.get(d.nation()), other = pol.nations.get(d.other());
            if (n == null || other == null) continue;
            switch (d.kind()) {
                case DECLARE_WAR -> WarService.declare(server, n, other, false);
                case ALLY -> ally(pol, n, other);
                case OFFER_PEACE -> {
                    for (War w : data.wars.values()) {
                        if (!w.live() || !w.enemies(n.id, other.id)) continue;
                        if (other.npc && FactionBrain.acceptsPeace(other, n.id, w)) WarService.end(server, w, War.Outcome.WHITE_PEACE);
                        else {
                            w.peaceOfferedByAttackers = w.attacker.equals(n.id);
                            Notices.toNation(server, other, NoticePayload.Kind.WAR, Component.translatable("nationsandwar.notice.peace_offered"),
                                    Component.literal(n.name), "handshake");
                        }
                    }
                }
            }
        }
        NW.changed(server);
    }

    private static void ally(Politics pol, Nation a, Nation b) {
        if (b.alliance != null) {
            Alliance al = pol.alliances.get(b.alliance);
            if (al != null && a.alliance == null) pol.addToAlliance(al, a);
        } else if (a.alliance == null) {
            pol.createAlliance(null, a, NameGen.nation(a.id.getLeastSignificantBits()) + " Pact");
            Alliance al = pol.alliances.get(a.alliance);
            if (al != null) pol.addToAlliance(al, b);
        }
    }

    // --- war campaigns -----------------------------------------------------------------------------------------------------

    /** Base number of fighters an NPC side sends to defend a banner. */
    private static final int RESPONSE = 3;

    private static int strength(Politics pol, java.util.Set<UUID> side) {
        return WarPlanner.sideStrength(pol, side, n -> FactionBrain.strength(pol, n));
    }

    /**
     * Every plan, each NPC primary at war tops up its fighters at every banner of its war (while its reserve lasts), frees
     * its own occupied land first, then keeps as many sieges going as its strength allows, each one a step closer to
     * the target town's Core.
     */
    private static void campaigns(MinecraftServer server, NWSavedData data) {
        Politics pol = data.politics;
        for (War w : new java.util.ArrayList<>(data.wars.values())) {
            if (w.phase != War.Phase.ACTIVE) continue;
            for (boolean side : new boolean[]{true, false}) {
                Nation npc = pol.nations.get(side ? w.attacker : w.defender);
                if (npc == null || !npc.npc || w.phase != War.Phase.ACTIVE) continue;
                int mine = strength(pol, side ? w.attackers : w.defenders), theirs = strength(pol, side ? w.defenders : w.attackers);
                boolean spent = w.losses.getOrDefault(npc.id, 0) >= WarPlanner.reserve(FactionBrain.strength(pol, npc));
                for (Siege s : new java.util.ArrayList<>(w.sieges)) reinforce(server, w, npc, s, mine, theirs, spent);
                if (spent) continue;
                int budget = WarPlanner.fronts(mine, theirs, NWConfig.get(NWConfig.MAX_SIEGES))
                        - (int) w.sieges.stream().filter(s -> s.byAttackers == side).count();
                for (UUID tid : npc.towns) {
                    if (budget <= 0) break;
                    Town t = pol.towns.get(tid);
                    Long c = t == null ? null : WarPlanner.liberationTarget(w, t);
                    if (c != null && open(server, data, w, npc, t, c, mine, theirs)) budget--;
                }
                for (int tries = 0; budget > 0 && tries < 6; tries++) {
                    Town target = WarPlanner.targetTown(pol, w, npc, t -> reachable(server, t));
                    Long c = target == null ? null : WarPlanner.nextChunk(w, target);
                    if (c == null || !open(server, data, w, npc, target, c, mine, theirs)) break;
                    budget--;
                }
            }
        }
    }

    /** Runs one round of NPC war plans now (the GameTests' way in; the server runs one every {@code npc.planTicks}). */
    public static void planWars(MinecraftServer server) {
        campaigns(server, NW.data(server));
    }

    /** A town may be attacked now: an NPC or managed one always, a player town only with a player near it. */
    private static boolean reachable(MinecraftServer server, Town t) {
        Nation owner = t.nation == null ? null : NW.pol(server).nations.get(t.nation);
        if (owner != null && owner.alwaysDefended()) return true;
        ServerLevel level = level(server, t.dimension);
        BlockPos core = BlockPos.of(t.core);
        return level != null && level.getNearestPlayer(core.getX(), core.getY(), core.getZ(), 160, false) != null;
    }

    /** Plants {@code npc}'s banner in {@code chunk} of {@code town} (a deferred one if the chunk is not live) and sends its party. */
    private static boolean open(MinecraftServer server, NWSavedData data, War w, Nation npc, Town town, long chunk, int mine, int theirs) {
        ServerLevel level = level(server, town.dimension);
        if (level == null) return false;
        BlockPos centre = new ChunkPos(chunk).getMiddleBlockPosition(BlockPos.of(town.core).getY());
        if (!level.isPositionEntityTicking(centre)) {
            if (!WarService.openSiege(level, centre, npc.id, true, false).ok()) return false;
            Siege s = w.siegeAt(town.dimension, chunk);
            if (s != null) s.lastA = WarPlanner.party(NWConfig.get(NWConfig.WAR_PARTY_SIZE), mine, theirs);
            return true;
        }
        BlockPos banner = surface(level, centre);
        BlockState before = level.getBlockState(banner);
        level.setBlockAndUpdate(banner, NWRegistry.SIEGE_BANNER.get().defaultBlockState());
        if (!WarService.plantBanner(level, banner, npc.id, true).ok()) {
            level.setBlockAndUpdate(banner, before);
            return false;
        }
        data.restoreLog(w.id).record(level, banner, before);
        Siege s = w.siegeAt(town.dimension, chunk);
        if (s != null) reinforce(server, w, npc, s, mine, theirs, false);
        return true;
    }

    /**
     * Brings {@code npc}'s fighters at siege {@code s} up to its party: idle soldiers nearby walk over first, then fresh
     * ones come from its nearest land (none once its reserve is spent). Unwatched sieges top up their simulated numbers.
     */
    private static void reinforce(MinecraftServer server, War w, Nation npc, Siege s, int mine, int theirs, boolean spent) {
        Boolean side = w.sideOf(npc.id);
        if (side == null) return;
        boolean besieging = s.byAttackers == side;
        int want = WarPlanner.party(besieging ? NWConfig.get(NWConfig.WAR_PARTY_SIZE) : RESPONSE, mine, theirs);
        ServerLevel level = level(server, s.dimension);
        if (level == null) return;
        BlockPos banner = BlockPos.of(s.banner);
        if (!level.isPositionEntityTicking(banner)) {
            if (spent || !WarService.unattended(NW.pol(server), w)) return;
            if (besieging) s.lastA = Math.max(s.lastA, want);
            else s.lastD = Math.max(s.lastD, want);
            return;
        }
        if (!s.placed) return;
        int r = NWConfig.get(NWConfig.SIEGE_RADIUS);
        AABB near = new AABB(banner).inflate(r), around = new AABB(banner).inflate(96);
        int have = level.getEntitiesOfClass(GuardEntity.class, around, g -> npc.id.equals(g.nation())
                && (near.contains(g.position()) || banner.equals(g.post()))).size();
        for (GuardEntity g : level.getEntitiesOfClass(GuardEntity.class, around, g -> g.soldier && npc.id.equals(g.nation()) && idle(server, g))) {
            if (have >= want) return;
            g.garrison = false;
            g.repost(banner, 6);
            have++;
        }
        if (spent) return;
        BlockPos from = staging(level, npc, banner);
        for (; have < want; have++) {
            GuardEntity g = spawnGuard(level, null, npc, from, 6, true);
            if (g != null) g.repost(banner, 6);
        }
    }

    /** A soldier with nothing to do: its banner is gone, and it holds no chunk its side still occupies. */
    private static boolean idle(MinecraftServer server, GuardEntity g) {
        BlockPos post = g.post();
        if (post == null) return true;
        String dim = NW.dim(g.level());
        if (WarService.bannerActive(server, dim, post)) return false;
        if (!g.garrison) return true;
        Town t = NW.pol(server).townAt(dim, NW.chunk(post));
        return t == null || WarService.warZone(NW.data(server), t, NW.chunk(post)) == null;
    }

    /** Where fresh soldiers come from: the nation's nearest live claim within four chunks of the banner, else the banner. */
    private static BlockPos staging(ServerLevel level, Nation npc, BlockPos banner) {
        Politics pol = NW.pol(level.getServer());
        String dim = NW.dim(level);
        ChunkPos at = new ChunkPos(banner);
        long best = 0;
        int bestD = 5;
        for (UUID tid : npc.towns) {
            Town t = pol.towns.get(tid);
            if (t == null || !t.dimension.equals(dim)) continue;
            for (long c : t.claims) {
                int d = Math.max(Math.abs(ChunkPos.getX(c) - at.x), Math.abs(ChunkPos.getZ(c) - at.z));
                if (d < bestD && level.isPositionEntityTicking(new ChunkPos(c).getMiddleBlockPosition(banner.getY()))) {
                    best = c;
                    bestD = d;
                }
            }
        }
        return bestD < 5 ? surface(level, new ChunkPos(best).getMiddleBlockPosition(0)) : banner;
    }

    /** {@code s} won its chunk: some of the besiegers stay as its garrison; the rest wait for the next banner. */
    public static void chunkTaken(MinecraftServer server, War w, Siege s, ServerLevel level) {
        Nation npc = NW.pol(server).nations.get(s.byAttackers ? w.attacker : w.defender);
        if (npc == null || !npc.npc) return;
        BlockPos hold = surface(level, new ChunkPos(s.chunk).getMiddleBlockPosition(0));
        int left = NWConfig.get(NWConfig.GARRISON);
        AABB near = new AABB(BlockPos.of(s.banner)).inflate(NWConfig.get(NWConfig.SIEGE_RADIUS));
        for (GuardEntity g : level.getEntitiesOfClass(GuardEntity.class, near, g -> g.soldier && npc.id.equals(g.nation()))) {
            if (left-- <= 0) break;
            g.garrison = true;
            g.repost(hold, 8);
        }
    }

    /** A deferred siege's chunk went live: its simulated fighters step out around the banner. */
    public static void materialize(MinecraftServer server, War w, Siege s, ServerLevel level) {
        Politics pol = NW.pol(server);
        Town t = pol.towns.get(s.town);
        Nation besieger = pol.nations.get(s.byAttackers ? w.attacker : w.defender);
        Nation holder = pol.nations.get(s.liberation ? (s.byAttackers ? w.defender : w.attacker) : t == null ? null : t.nation);
        BlockPos banner = BlockPos.of(s.banner);
        if (besieger != null && besieger.npc) for (int i = 0; i < s.lastA; i++) spawnGuard(level, null, besieger, banner, 6, true);
        if (holder != null && holder.npc) for (int i = 0; i < s.lastD; i++) spawnGuard(level, s.liberation ? null : t, holder, banner, 6, true);
    }

    /**
     * A siege on an NPC side's land (or on a chunk it holds): the town's guards nearby rally to the banner and defenders
     * come out, more the stronger the besiegers. Unwatched, they are counted instead.
     */
    public static void siegeStarted(MinecraftServer server, War w, Siege s) {
        Politics pol = NW.pol(server);
        Town t = pol.towns.get(s.town);
        Nation n = pol.nations.get(s.liberation ? (s.byAttackers ? w.defender : w.attacker) : t == null ? null : t.nation);
        if (n == null || !n.npc) return;
        ServerLevel level = level(server, s.dimension);
        if (level == null) return;
        int mine = strength(pol, s.byAttackers ? w.defenders : w.attackers), theirs = strength(pol, s.byAttackers ? w.attackers : w.defenders);
        int want = WarPlanner.party(RESPONSE, mine, theirs);
        BlockPos banner = BlockPos.of(s.banner);
        if (!level.isPositionEntityTicking(banner)) {
            s.lastD = want + (s.liberation ? NWConfig.get(NWConfig.GARRISON) : NWConfig.get(NWConfig.GUARDS_PER_TOWN) / 2);
            return;
        }
        if (!s.liberation && t != null) {
            for (GuardEntity g : level.getEntitiesOfClass(GuardEntity.class, new AABB(banner).inflate(64),
                    g -> !g.soldier && !g.rallied() && t.id.equals(g.town()))) g.rally(banner, 6);
        }
        for (int i = 0; i < want; i++) spawnGuard(level, s.liberation ? null : t, n, banner, 8, true);
    }

    public static void guardKilled(ServerPlayer killer, GuardEntity guard) {
        Politics pol = NW.pol(killer.getServer());
        Nation n = guard.nation() == null ? null : pol.nations.get(guard.nation());
        Nation k = pol.nationOfPlayer(killer.getUUID());
        if (n != null && k != null && n.npc && !WarService.atWar(NW.data(killer.getServer()), n.id, k.id)) {
            n.addOpinion(k.id, -10);
            NW.changed(killer.getServer());
        }
    }

    // --- diplomacy with players ------------------------------------------------------------------------------------------

    public static Result action(ServerPlayer p, String sub, CompoundTag a) {
        MinecraftServer server = p.getServer();
        NWSavedData data = NW.data(server);
        Politics pol = data.politics;
        Nation npc = pol.nations.get(UUID.fromString(a.getString("nation")));
        if (npc == null || !npc.npc) return Result.fail("unknown_nation");
        Nation mine = pol.nationOfPlayer(p.getUUID());
        switch (sub) {
            case "gift": {
                if (mine == null) return Result.fail("no_nation");
                int n = a.getInt("amount");
                if (n <= 0) return Result.fail("bad_amount");
                if (!Emeralds.take(p, n)) return Result.fail("player_poor", n);
                npc.bank += n;
                npc.addOpinion(mine.id, FactionBrain.giftOpinion(n));
                return Result.ok("npc.gift_taken", npc.opinionOf(mine.id));
            }
            case "propose_alliance": {
                if (mine == null || !pol.canNation(p.getUUID(), mine, Perm.DIPLOMACY)) return Result.fail("no_perm");
                if (npc.alliance != null) return Result.fail("already_in_alliance");
                if (npc.opinionOf(mine.id) < FactionBrain.ACCEPT_ALLIANCE) return Result.fail("npc.refuses", npc.opinionOf(mine.id));
                if (mine.alliance == null) {
                    Result r = pol.createAlliance(null, mine, mine.name + " Pact");
                    if (!r.ok()) return r;
                }
                Alliance al = pol.alliances.get(mine.alliance);
                pol.addToAlliance(al, npc);
                return Result.ok("npc.alliance_accepted", npc.name);
            }
            case "ask_peace": {
                if (mine == null || !pol.canNation(p.getUUID(), mine, Perm.SIGN_TREATY)) return Result.fail("no_perm");
                for (War w : data.wars.values()) {
                    if (!w.live() || !w.enemies(npc.id, mine.id)) continue;
                    if (!FactionBrain.acceptsPeace(npc, mine.id, w)) return Result.fail("npc.refuses", npc.opinionOf(mine.id));
                    WarService.end(server, w, War.Outcome.WHITE_PEACE);
                    return Result.ok("war.white_peace");
                }
                return Result.fail("war.none");
            }
            default:
                return Result.fail("unknown_action");
        }
    }

    /** A player alliance invited an NPC nation: it answers at once. */
    public static void allianceInvite(MinecraftServer server, Nation npc, Nation inviter) {
        Politics pol = NW.pol(server);
        if (npc.opinionOf(inviter.id) < FactionBrain.ACCEPT_ALLIANCE || inviter.alliance == null) return;
        Alliance al = pol.alliances.get(inviter.alliance);
        if (al != null && pol.allianceJoin(null, npc, al).ok()) {
            Notices.toNation(server, inviter, NoticePayload.Kind.POLITICS, Component.translatable("nationsandwar.notice.alliance_joined"),
                    Component.literal(npc.name + " → " + al.name), "handshake");
        }
    }

    /** A white peace offered to an NPC nation. */
    public static void peaceOffered(MinecraftServer server, Nation npc, Nation from, War w) {
        if (FactionBrain.acceptsPeace(npc, from.id, w)) WarService.end(server, w, War.Outcome.WHITE_PEACE);
    }

    /** {@code /nwadmin npc}: an NPC town (and nation) at a player's chunk. */
    public static Town create(ServerLevel level, BlockPos at, String name) {
        long seed = level.random.nextLong();
        return adopt(level, new ChunkPos(at), name == null || name.isBlank() ? NameGen.town(seed) : name, seed);
    }

    private static ServerLevel level(MinecraftServer server, String dim) {
        for (ServerLevel l : server.getAllLevels()) if (NW.dim(l).equals(dim)) return l;
        return null;
    }
}
