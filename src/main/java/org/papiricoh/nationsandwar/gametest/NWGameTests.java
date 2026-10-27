package org.papiricoh.nationsandwar.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.papiricoh.nationsandwar.NWConfig;
import org.papiricoh.nationsandwar.NationsAndWar;
import org.papiricoh.nationsandwar.claim.ProtectionEvents;
import org.papiricoh.nationsandwar.core.Actions;
import org.papiricoh.nationsandwar.core.Emeralds;
import org.papiricoh.nationsandwar.core.NW;
import org.papiricoh.nationsandwar.data.NWSavedData;
import org.papiricoh.nationsandwar.model.ChunkPerms;
import org.papiricoh.nationsandwar.model.Flag;
import org.papiricoh.nationsandwar.model.GovernmentType;
import org.papiricoh.nationsandwar.model.Nation;
import org.papiricoh.nationsandwar.model.Politics;
import org.papiricoh.nationsandwar.model.Result;
import org.papiricoh.nationsandwar.model.Town;
import org.papiricoh.nationsandwar.npc.EnvoyEntity;
import org.papiricoh.nationsandwar.npc.GuardEntity;
import org.papiricoh.nationsandwar.npc.NpcService;
import org.papiricoh.nationsandwar.registry.NWRegistry;
import org.papiricoh.nationsandwar.war.War;
import org.papiricoh.nationsandwar.war.WarService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * End-to-end checks in a real server world. Each test has its own batch (towns keep a buffer of chunks between them) and
 * removes what it founded. Fake players are not "online" and not found by entity searches, so sieges are pushed by guards.
 */
@GameTestHolder(NationsAndWar.MODID)
@PrefixGameTestTemplate(false)
public final class NWGameTests {
    public static final String FIELD = "gametest/empty_48x24x48";

    private NWGameTests() {
    }

    /** Removes towns (and their wars) left by an earlier run that failed before cleaning up: they are all named "Gt …". */
    private static void purge(GameTestHelper h) {
        Politics pol = NW.pol(h.getLevel().getServer());
        cleanup(h, pol.towns.values().stream().filter(t -> t.name.startsWith("Gt ")).toArray(Town[]::new));
    }

    private static ServerPlayer fake(GameTestHelper h, String name, BlockPos rel) {
        if (name.startsWith("Mayor") || name.startsWith("Def")) purge(h);
        ServerPlayer p = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), name));
        p.setGameMode(GameType.SURVIVAL);
        BlockPos at = h.absolutePos(rel);
        p.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        return p;
    }

    /** A town for {@code mayor} on the chunk holding {@code rel}, with money in the bank, founded straight in the model. */
    private static Town town(GameTestHelper h, ServerPlayer mayor, String name, BlockPos rel) {
        ServerLevel level = h.getLevel();
        Politics pol = NW.pol(level.getServer());
        BlockPos at = h.absolutePos(rel);
        Result r = pol.foundTown(mayor.getUUID(), name, NW.dim(level), NW.chunk(at), at.asLong(), level.getGameTime());
        if (!r.ok()) throw new IllegalStateException("found " + name + ": " + r.key());
        Town t = pol.townOf(mayor.getUUID());
        t.bank = 1000;
        level.setBlockAndUpdate(at, NWRegistry.TOWN_CORE.get().defaultBlockState());
        NW.changed(level.getServer());
        return t;
    }

    /** An absolute spot in the same chunk as {@code coreRel} (chunk-local x/z), never the core itself. */
    private static BlockPos sameChunk(GameTestHelper h, BlockPos coreRel, int lx, int lz) {
        BlockPos abs = h.absolutePos(coreRel);
        if ((abs.getX() & 15) == lx && (abs.getZ() & 15) == lz) lx = (lx + 3) & 15;
        return new BlockPos((abs.getX() & ~15) + lx, abs.getY(), (abs.getZ() & ~15) + lz);
    }

    private static boolean is(GameTestHelper h, BlockPos abs, net.minecraft.world.level.block.Block b) {
        return h.getLevel().getBlockState(abs).is(b);
    }

    private static void cleanup(GameTestHelper h, Town... towns) {
        NWSavedData data = NW.data(h.getLevel().getServer());
        for (Town t : towns) {
            if (t == null) continue;
            for (War w : new java.util.ArrayList<>(data.wars.values())) if (t.nation != null && w.involves(t.nation)) data.wars.remove(w.id);
            if (data.politics.towns.containsKey(t.id)) data.politics.removeTown(t);
            Nation n = t.nation == null ? null : data.politics.nations.get(t.nation);
            if (n != null && n.towns.isEmpty()) data.politics.removeNation(n);
        }
        NW.changed(h.getLevel().getServer());
    }

    @GameTest(template = FIELD, batch = "nw_protection", timeoutTicks = 100)
    public static void protectionFollowsGroups(GameTestHelper h) {
        ServerPlayer mayor = fake(h, "Mayor1", new BlockPos(8, 2, 8)), stranger = fake(h, "Stranger1", new BlockPos(9, 2, 8));
        Town t = town(h, mayor, "Gt Protect", new BlockPos(8, 2, 8));
        BlockPos abs = sameChunk(h, new BlockPos(8, 2, 8), 2, 2);
        h.getLevel().setBlockAndUpdate(abs, Blocks.OAK_PLANKS.defaultBlockState());
        try {
            h.assertFalse(ProtectionEvents.allowed(stranger, h.getLevel(), abs, ChunkPerms.Action.DESTROY), "outsiders may not break");
            h.assertTrue(ProtectionEvents.allowed(mayor, h.getLevel(), abs, ChunkPerms.Action.DESTROY), "residents may break");
            h.assertFalse(stranger.gameMode.destroyBlock(abs), "the real break is refused");
            h.assertTrue(is(h, abs, Blocks.OAK_PLANKS), "still there");
            NW.pol(h.getLevel().getServer()).setChunkPerm(mayor.getUUID(), t, ChunkPerms.Group.OUTSIDER, ChunkPerms.Action.DESTROY, true, NW.chunk(abs));
            h.assertTrue(stranger.gameMode.destroyBlock(abs), "allowed once the chunk says so");
            h.assertFalse(is(h, abs, Blocks.OAK_PLANKS), "broken");
            h.assertFalse(mayor.gameMode.destroyBlock(BlockPos.of(t.core)), "the Core is never simply broken");
        } finally {
            cleanup(h, t);
        }
        h.succeed();
    }

    @GameTest(template = FIELD, batch = "nw_found", timeoutTicks = 100)
    public static void foundingAndClaimingCost(GameTestHelper h) {
        ServerPlayer mayor = fake(h, "Mayor2", new BlockPos(8, 2, 8));
        int cost = NWConfig.get(NWConfig.TOWN_COST);
        mayor.getInventory().add(new ItemStack(Items.EMERALD, cost));
        mayor.getInventory().add(new ItemStack(Items.EMERALD_BLOCK, 3));
        CompoundTag a = new CompoundTag();
        a.putString("name", "Gt Founders");
        Result r = Actions.run(mayor, "town.found", a);
        Town t = NW.pol(h.getLevel().getServer()).townOf(mayor.getUUID());
        try {
            h.assertTrue(r.ok(), "founded: " + r.key());
            h.assertTrue(Emeralds.count(mayor) == 27, "paid the founding price, has " + Emeralds.count(mayor));
            h.assertTrue(is(h, BlockPos.of(t.core), NWRegistry.TOWN_CORE.get()), "core placed at the founder's feet");
            CompoundTag d = new CompoundTag();
            d.putInt("amount", 20);
            h.assertTrue(Actions.run(mayor, "town.deposit", d).ok(), "deposit");
            h.assertTrue(t.bank == 20 && Emeralds.count(mayor) == 7, "bank " + t.bank + ", purse " + Emeralds.count(mayor));
            BlockPos core = BlockPos.of(t.core);
            CompoundTag c = new CompoundTag();
            c.putInt("x", (core.getX() >> 4) + 1);
            c.putInt("z", core.getZ() >> 4);
            Result claim = Actions.run(mayor, "town.claim", c);
            h.assertTrue(claim.ok(), "claim: " + claim.key());
            h.assertTrue(t.bank == 20 - NWConfig.get(NWConfig.CLAIM_COST), "claim paid from the bank");
            c.putInt("x", (core.getX() >> 4) + 5);
            h.assertTrue("not_adjacent".equals(Actions.run(mayor, "town.claim", c).key()), "no gaps");
        } finally {
            cleanup(h, t);
        }
        h.succeed();
    }

    @GameTest(template = FIELD, batch = "nw_explosions", timeoutTicks = 100)
    public static void explosionsFollowTheFlag(GameTestHelper h) {
        ServerPlayer mayor = fake(h, "Mayor3", new BlockPos(8, 2, 8));
        Town t = town(h, mayor, "Gt Blast", new BlockPos(8, 2, 8));
        BlockPos abs = sameChunk(h, new BlockPos(8, 2, 8), 12, 12);
        try {
            h.getLevel().setBlockAndUpdate(abs, Blocks.STONE_BRICKS.defaultBlockState());
            h.getLevel().explode(null, abs.getX() + 0.5, abs.getY() + 1.5, abs.getZ() + 0.5, 3f, Level.ExplosionInteraction.TNT);
            h.assertTrue(is(h, abs, Blocks.STONE_BRICKS), "protected from the blast");
            NW.pol(h.getLevel().getServer()).setFlag(mayor.getUUID(), t, Flag.EXPLOSIONS, true, null);
            h.getLevel().explode(null, abs.getX() + 0.5, abs.getY() + 1.5, abs.getZ() + 0.5, 3f, Level.ExplosionInteraction.TNT);
            h.assertFalse(is(h, abs, Blocks.STONE_BRICKS), "blown up once allowed");
        } finally {
            cleanup(h, t);
        }
        h.succeed();
    }

    /** Two player nations at war (attacker A, defender D); D's town is one chunk, so its home chunk is on the front. */
    private record Front(ServerPlayer attacker, ServerPlayer defender, Town a, Town d, Nation na, Nation nd, War war) {
    }

    private static Front front(GameTestHelper h, String tag) {
        ServerPlayer att = fake(h, "Att" + tag, new BlockPos(40, 2, 40)), def = fake(h, "Def" + tag, new BlockPos(8, 2, 8));
        Town d = town(h, def, "Gt Keep " + tag, new BlockPos(8, 2, 8));
        Politics pol = NW.pol(h.getLevel().getServer());
        BlockPos far = h.absolutePos(new BlockPos(8, 2, 8)).offset(16 * 8, 0, 0);
        Result r = pol.foundTown(att.getUUID(), "Gt Raiders " + tag, NW.dim(h.getLevel()), NW.chunk(far), far.asLong(), 0);
        if (!r.ok()) throw new IllegalStateException(r.key());
        Town a = pol.townOf(att.getUUID());
        a.bank = d.bank = 1000;
        pol.foundNation(att.getUUID(), "Gt North " + tag, GovernmentType.EMPIRE, 0);
        pol.foundNation(def.getUUID(), "Gt South " + tag, GovernmentType.REPUBLIC, 0);
        Nation na = pol.nationOf(a), nd = pol.nationOf(d);
        WarService.declare(h.getLevel().getServer(), na, nd, false);
        War w = WarService.activeWar(NW.data(h.getLevel().getServer()), na.id, nd.id);
        if (w == null) {
            w = NW.data(h.getLevel().getServer()).wars.values().stream().filter(x -> x.enemies(na.id, nd.id)).findFirst().orElseThrow();
            w.phase = War.Phase.ACTIVE;
        }
        w.deadline = Long.MAX_VALUE;
        return new Front(att, def, a, d, na, nd, w);
    }

    @GameTest(template = FIELD, batch = "nw_offline", timeoutTicks = 100)
    public static void noSiegeWithoutDefendersOnline(GameTestHelper h) {
        Front f = front(h, "O");
        int before = NWConfig.get(NWConfig.MIN_DEFENDERS_ONLINE);
        NWConfig.MIN_DEFENDERS_ONLINE.set(1);
        try {
            BlockPos banner = sameChunk(h, new BlockPos(8, 2, 8), 2, 2);
            Result r = WarService.plantBanner(h.getLevel(), banner, f.na.id, false);
            h.assertTrue("siege.defenders_offline".equals(r.key()), "refused: " + r.key());
        } finally {
            NWConfig.MIN_DEFENDERS_ONLINE.set(before);
            cleanup(h, f.a, f.d);
        }
        h.succeed();
    }

    @GameTest(template = FIELD, batch = "nw_siege", timeoutTicks = 600)
    public static void siegeTakesChunkCoreTownAndRestores(GameTestHelper h) {
        Front f = front(h, "S");
        int minDef = NWConfig.get(NWConfig.MIN_DEFENDERS_ONLINE), hits = NWConfig.get(NWConfig.CORE_HITS);
        double rate = NWConfig.get(NWConfig.CONTROL_RATE);
        NWConfig.MIN_DEFENDERS_ONLINE.set(0);
        NWConfig.CORE_HITS.set(2);
        NWConfig.CONTROL_RATE.set(0.2);
        ServerLevel level = h.getLevel();
        BlockPos wall = sameChunk(h, new BlockPos(8, 2, 8), 12, 3);
        level.setBlockAndUpdate(wall, Blocks.COBBLESTONE.defaultBlockState());
        BlockPos banner = sameChunk(h, new BlockPos(8, 2, 8), 2, 2);
        level.setBlockAndUpdate(banner, NWRegistry.SIEGE_BANNER.get().defaultBlockState());
        Result planted = WarService.plantBanner(level, banner, f.na.id, false);
        Runnable restore = () -> {
            NWConfig.MIN_DEFENDERS_ONLINE.set(minDef);
            NWConfig.CORE_HITS.set(hits);
            NWConfig.CONTROL_RATE.set(rate);
        };
        if (!planted.ok()) {
            restore.run();
            cleanup(h, f.a, f.d);
            h.fail("banner refused: " + planted.key());
            return;
        }
        h.assertTrue(f.attacker.gameMode.destroyBlock(wall), "fighters may break in the besieged chunk");
        h.assertFalse(is(h, wall, Blocks.COBBLESTONE), "wall broken");
        GuardEntity g = NpcService.spawnGuard(level, null, f.na, banner, 4, true);
        h.assertTrue(g != null, "soldier");
        g.setNoAi(true);
        g.moveTo(banner.getX() + 0.5, banner.getY(), banner.getZ() + 1.5);
        h.succeedWhen(() -> {
            var siege = f.war.sieges.isEmpty() ? null : f.war.sieges.getFirst();
            h.assertTrue(f.war.coreOpen.contains(f.d.id), "home chunk taken, core open (phase " + f.war.phase + ", control "
                    + (siege == null ? "-" : siege.control) + ", soldier alive " + g.isAlive() + " at " + g.blockPosition() + ")");
            h.assertTrue(f.war.isOccupied(f.d.id, f.d.homeChunk), "occupied");
            BlockPos core = BlockPos.of(f.d.core);
            f.attacker.gameMode.destroyBlock(core);
            f.attacker.gameMode.destroyBlock(core);
            h.assertTrue(f.war.fallen.contains(f.d.id), "the town fell after the core hits");
            h.assertTrue(is(h, core, NWRegistry.TOWN_CORE.get()), "the core block stays");
            WarService.end(level.getServer(), f.war, War.Outcome.ATTACKERS);
            h.assertTrue(is(h, wall, Blocks.COBBLESTONE), "restored after the war");
            h.assertTrue(f.war.phase == War.Phase.TREATY, "treaty window for the winner");
            if (g.isAlive()) g.discard();
            restore.run();
            cleanup(h, f.a, f.d);
        });
    }

    @GameTest(template = FIELD, batch = "nw_npc", timeoutTicks = 100)
    public static void npcTownsGetGuardsAndAnEnvoy(GameTestHelper h) {
        purge(h);
        BlockPos at = h.absolutePos(new BlockPos(24, 2, 24));
        Town t = NpcService.create(h.getLevel(), at, "Gt Calmere");
        h.assertTrue(t != null && t.npc, "adopted");
        try {
            h.assertTrue(t.claims.size() == (2 * NWConfig.get(NWConfig.VILLAGE_RADIUS) + 1) * (2 * NWConfig.get(NWConfig.VILLAGE_RADIUS) + 1), "radius claimed");
            h.assertTrue(t.nation != null && NW.pol(h.getLevel().getServer()).nations.get(t.nation).npc, "in an NPC nation");
            AABB box = new AABB(BlockPos.of(t.core)).inflate(16);
            int guards = h.getLevel().getEntitiesOfClass(GuardEntity.class, box).size();
            h.assertTrue(guards >= NWConfig.get(NWConfig.GUARDS_PER_TOWN), "guards: " + guards);
            h.assertTrue(!h.getLevel().getEntitiesOfClass(EnvoyEntity.class, box).isEmpty(), "an envoy at the capital");
            h.assertTrue(h.getLevel().getBlockState(BlockPos.of(t.core)).is(NWRegistry.TOWN_CORE.get()), "core placed");
            h.getLevel().getEntitiesOfClass(GuardEntity.class, box).forEach(e -> e.discard());
            h.getLevel().getEntitiesOfClass(EnvoyEntity.class, box).forEach(e -> e.discard());
        } finally {
            Actions.removeCore(h.getLevel().getServer(), t);
            cleanup(h, t);
        }
        h.succeed();
    }

    // --- the API (v0.2) ----------------------------------------------------------------------------------------------------

    private static final String TEST_KEY = "nationsandwar_test:power";
    private static boolean allowDeclare;
    private static final List<String> HEARD = new ArrayList<>();

    static {
        org.papiricoh.nationsandwar.api.ManagedNations.register(new org.papiricoh.nationsandwar.api.ManagedNationProvider() {
            @Override
            public String key() {
                return TEST_KEY;
            }

            @Override
            public boolean mayBeDeclaredOn(net.minecraft.server.MinecraftServer server, Nation self, Nation attacker) {
                return allowDeclare;
            }
        });
        org.papiricoh.nationsandwar.api.WarApi.registerCombatant(e -> {
            for (String tag : e.getTags()) if (tag.startsWith("nwtest_for_")) return UUID.fromString(tag.substring("nwtest_for_".length()));
            return null;
        });
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((org.papiricoh.nationsandwar.api.event.WarEvent.CoreOpened e) ->
                HEARD.add("core:" + e.town().name));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((org.papiricoh.nationsandwar.api.event.TownEvent.Removed e) ->
                HEARD.add("removed:" + e.town().name));
    }

    /** A managed nation with one one-chunk town (no Core block) on the chunk holding {@code rel}. */
    private static Town managedTown(GameTestHelper h, String name, BlockPos rel) {
        ServerLevel level = h.getLevel();
        Nation n = org.papiricoh.nationsandwar.api.NationsAndWarApi.createManagedNation(level.getServer(), TEST_KEY, name + " Power", "nationsandwar.gov.theocracy", 0x880000);
        BlockPos at = h.absolutePos(rel);
        return org.papiricoh.nationsandwar.api.NationsAndWarApi.createManagedTown(level, n, name, at, List.of(), at, false, null, null);
    }

    @GameTest(template = FIELD, batch = "nw_api_locks", timeoutTicks = 100)
    public static void managedNationsAreLocked(GameTestHelper h) {
        ServerPlayer mayor = fake(h, "Mayor7", new BlockPos(40, 2, 40));
        Town seat = managedTown(h, "Gt Seat", new BlockPos(8, 2, 8));
        var server = h.getLevel().getServer();
        Politics pol = NW.pol(server);
        BlockPos far = h.absolutePos(new BlockPos(8, 2, 8)).offset(16 * 8, 0, 0);
        pol.foundTown(mayor.getUUID(), "Gt Mortals", NW.dim(h.getLevel()), NW.chunk(far), far.asLong(), 0);
        Town mine = pol.townOf(mayor.getUUID());
        mine.bank = 1000;
        pol.foundNation(mayor.getUUID(), "Gt Mortal Realm", GovernmentType.KINGDOM, 0);
        Nation mn = pol.nationOf(mine), power = pol.nationOf(seat);
        try {
            h.assertTrue(power != null && power.managed() && seat.id.equals(power.capital), "managed nation with its seat as capital");
            allowDeclare = false;
            h.assertTrue("war.managed".equals(WarService.declare(server, mn, power, false).key()), "ordinary wars refused");
            allowDeclare = true;
            Result r = WarService.declare(server, mn, power, false);
            h.assertTrue(r.ok(), "declared once the provider allows it: " + r.key());
            h.assertTrue("managed".equals(pol.join(UUID.randomUUID(), seat).key()), "nobody joins it");
            var brief = org.papiricoh.nationsandwar.core.Sync.briefs(server).nations().stream().filter(b -> b.id().equals(power.id)).findFirst().orElseThrow();
            h.assertTrue(brief.managed() && TEST_KEY.equals(brief.managedBy()) && "nationsandwar.gov.theocracy".equals(brief.titles()), "brief carries it");
        } finally {
            allowDeclare = false;
            cleanup(h, mine, seat);
        }
        h.succeed();
    }

    @GameTest(template = FIELD, batch = "nw_api_war", timeoutTicks = 600)
    public static void combatantsBesiegeAndAHeartlessTownFalls(GameTestHelper h) {
        ServerPlayer mayor = fake(h, "Mayor8", new BlockPos(40, 2, 40));
        Town seat = managedTown(h, "Gt Hollow", new BlockPos(8, 2, 8));
        ServerLevel level = h.getLevel();
        var server = level.getServer();
        Politics pol = NW.pol(server);
        BlockPos far = h.absolutePos(new BlockPos(8, 2, 8)).offset(16 * 8, 0, 0);
        pol.foundTown(mayor.getUUID(), "Gt Crusaders", NW.dim(level), NW.chunk(far), far.asLong(), 0);
        Town mine = pol.townOf(mayor.getUUID());
        mine.bank = 1000;
        pol.foundNation(mayor.getUUID(), "Gt Crusade", GovernmentType.EMPIRE, 0);
        Nation mn = pol.nationOf(mine), power = pol.nationOf(seat);
        double rate = NWConfig.get(NWConfig.CONTROL_RATE);
        NWConfig.CONTROL_RATE.set(0.2);
        HEARD.clear();
        Runnable done = () -> {
            NWConfig.CONTROL_RATE.set(rate);
            cleanup(h, mine, seat);
        };
        Result r = org.papiricoh.nationsandwar.api.WarApi.declare(server, mn.id, power.id,
                org.papiricoh.nationsandwar.api.WarApi.WarOptions.free().times(0, 24000 * 10).noCooldown());
        if (!r.ok()) {
            done.run();
            h.fail("provider-style declaration refused: " + r.key());
            return;
        }
        War w = WarService.activeWar(NW.data(server), mn.id, power.id);
        BlockPos banner = sameChunk(h, new BlockPos(8, 2, 8), 3, 3);
        Result planted = org.papiricoh.nationsandwar.api.WarApi.plantBannerAs(level, banner, mn.id, false);
        if (w == null || !planted.ok()) {
            done.run();
            h.fail("war " + w + ", banner " + planted.key());
            return;
        }
        var soldier = new net.minecraft.world.entity.decoration.ArmorStand(level, banner.getX() + 0.5, banner.getY(), banner.getZ() + 1.5);
        soldier.addTag("nwtest_for_" + mn.id);
        level.addFreshEntity(soldier);
        h.succeedWhen(() -> {
            h.assertTrue(w.coreOpen.contains(seat.id), "the combatant took the home chunk");
            org.papiricoh.nationsandwar.api.NationsAndWarApi.flushEvents();
            h.assertTrue(HEARD.contains("core:Gt Hollow"), "CoreOpened heard: " + HEARD);
            h.assertTrue(org.papiricoh.nationsandwar.api.WarApi.townFallen(server, w, seat.id, mn.id), "the provider decides the heart");
            h.assertTrue(w.fallen.contains(seat.id), "fallen");
            WarService.end(server, w, War.Outcome.ATTACKERS);
            h.assertTrue(w.phase == War.Phase.ENDED, "a managed loser's treaty applies at once: " + w.phase);
            h.assertTrue(!pol.towns.containsKey(seat.id) && pol.townAt(seat.dimension, seat.homeChunk) == null, "razed, land free");
            org.papiricoh.nationsandwar.api.NationsAndWarApi.flushEvents();
            h.assertTrue(HEARD.contains("removed:Gt Hollow"), "TownEvent.Removed heard: " + HEARD);
            soldier.discard();
            done.run();
        });
    }

    @GameTest(template = FIELD, batch = "nw_api_bypass", timeoutTicks = 100)
    public static void bypassZonesLetAFightThrough(GameTestHelper h) {
        ServerPlayer mayor = fake(h, "Mayor9", new BlockPos(8, 2, 8)), stranger = fake(h, "Stranger9", new BlockPos(9, 2, 8));
        Town t = town(h, mayor, "Gt Arena", new BlockPos(8, 2, 8));
        ServerLevel level = h.getLevel();
        BlockPos abs = sameChunk(h, new BlockPos(8, 2, 8), 2, 2);
        level.setBlockAndUpdate(abs, Blocks.OAK_PLANKS.defaultBlockState());
        var zombie = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
        zombie.moveTo(abs.getX() + 0.5, abs.getY() + 1, abs.getZ() + 0.5);
        java.util.function.BooleanSupplier spawnCancelled = () -> {
            zombie.setSpawnCancelled(false); // the flag lives on the mob, not the event
            var ev = new net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent(zombie, level, zombie.getX(), zombie.getY(), zombie.getZ(),
                    level.getCurrentDifficultyAt(abs), net.minecraft.world.entity.MobSpawnType.NATURAL, null, null);
            ProtectionEvents.spawn(ev);
            return ev.isSpawnCancelled();
        };
        String key = "nationsandwar_test:arena";
        try {
            h.assertTrue(spawnCancelled.getAsBoolean(), "mobs off: the spawn is refused");
            h.assertFalse(stranger.gameMode.destroyBlock(abs), "outsiders may not break");
            org.papiricoh.nationsandwar.api.ProtectionBypass.openZone(key, NW.dim(level), new AABB(abs).inflate(8),
                    org.papiricoh.nationsandwar.api.ProtectionBypass.ALL);
            BlockPos feet = BlockPos.containing(zombie.getX(), zombie.getY(), zombie.getZ());
            h.assertTrue(org.papiricoh.nationsandwar.api.ProtectionBypass.bypassed(level, feet, org.papiricoh.nationsandwar.api.ProtectionBypass.Bypass.SPAWNS),
                    "the zone covers the mob");
            h.assertFalse(spawnCancelled.getAsBoolean(), "inside the zone the boss's minions spawn");
            h.assertTrue(stranger.gameMode.destroyBlock(abs), "inside the zone an outsider may break");
            level.setBlockAndUpdate(abs, Blocks.OAK_PLANKS.defaultBlockState());
            org.papiricoh.nationsandwar.api.ProtectionBypass.close(key);
            h.assertFalse(stranger.gameMode.destroyBlock(abs), "protected again once closed");
            h.assertTrue(spawnCancelled.getAsBoolean(), "and the spawn refused again");
        } finally {
            org.papiricoh.nationsandwar.api.ProtectionBypass.close(key);
            zombie.discard();
            cleanup(h, t);
        }
        h.succeed();
    }
}
