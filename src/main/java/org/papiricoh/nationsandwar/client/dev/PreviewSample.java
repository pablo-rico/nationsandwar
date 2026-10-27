package org.papiricoh.nationsandwar.client.dev;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.papiricoh.nationsandwar.api.NationsAndWarApi;
import org.papiricoh.nationsandwar.core.NW;
import org.papiricoh.nationsandwar.data.NWSavedData;
import org.papiricoh.nationsandwar.model.GovernmentType;
import org.papiricoh.nationsandwar.model.Nation;
import org.papiricoh.nationsandwar.model.Perm;
import org.papiricoh.nationsandwar.model.Politics;
import org.papiricoh.nationsandwar.model.Province;
import org.papiricoh.nationsandwar.model.Role;
import org.papiricoh.nationsandwar.model.RoleTree;
import org.papiricoh.nationsandwar.model.Town;
import org.papiricoh.nationsandwar.npc.NpcService;
import org.papiricoh.nationsandwar.registry.NWRegistry;
import org.papiricoh.nationsandwar.war.War;
import org.papiricoh.nationsandwar.war.WarService;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Sample politics for the preview (only once per world): the player's town Highmoor in the Kingdom of Valoria with ten
 * named residents and filled role trees, a second town in the province of North March under a governor, an allied NPC
 * nation, and an active war against another NPC nation with a siege next to the player. Apart from that (and also added to
 * worlds seeded before it), a nation run by another mod: "The Pit" with one town south-west of Highmoor.
 */
public final class PreviewSample {
    public static final List<String> NAMES = List.of("Notch", "jeb_", "Dinnerbone", "Grumm", "Steve", "Alex", "Herobrine", "Technoblade", "Dream",
            "Ph1LzA", "TommyInnit", "Grian");

    /** The provider key of the sample managed nation (its card's handler is registered by {@link DevPreview}). */
    public static final String PIT = "nationsandwar_preview:pit";

    private PreviewSample() {
    }

    private static UUID person(MinecraftServer server, String name) {
        UUID id = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (server.getProfileCache() != null) server.getProfileCache().add(new GameProfile(id, name));
        return id;
    }

    public static void seed(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        NWSavedData data = NW.data(server);
        Politics pol = data.politics;
        if (pol.townOf(player.getUUID()) != null) {
            seedManaged(player);
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos at = player.blockPosition();
        ChunkPos home = new ChunkPos(at);
        pol.foundTown(player.getUUID(), "Highmoor", NW.dim(level), home.toLong(), at.asLong(), level.getGameTime());
        Town t = pol.townOf(player.getUUID());
        t.bank = 1240;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) pol.index(t, ChunkPos.asLong(home.x + dx, home.z + dz));
        UUID[] people = new UUID[NAMES.size()];
        for (int i = 0; i < people.length; i++) people[i] = person(server, NAMES.get(i));
        for (int i = 0; i < 9; i++) {
            t.invites.add(people[i]);
            pol.join(people[i], t);
        }
        List<Role> townRoles = List.copyOf(t.roles.all());
        int assistant = townRoles.get(1).id, builder = townRoles.get(2).id;
        int guard = t.roles.add("Watch Captain", 0x9E9E9E, assistant, EnumSet.of(Perm.KICK)).intArg();
        t.roles.add("Harbourmaster", 0x26A69A, RoleTree.ROOT, EnumSet.of(Perm.BANK_DEPOSIT));
        t.roles.assign(people[0], assistant);
        t.roles.assign(people[1], assistant);
        t.roles.assign(people[2], builder);
        t.roles.assign(people[3], guard);

        pol.foundNation(player.getUUID(), "Valoria", GovernmentType.KINGDOM, 0);
        Nation n = pol.nationOf(t);
        n.bank = 5320;
        BlockPos far = at.offset(16 * 10, 0, 0);
        pol.foundTown(people[9], "Ashdale", NW.dim(level), new ChunkPos(far).toLong(), far.asLong(), 0);
        Town t2 = pol.townOf(people[9]);
        t2.invites.add(people[10]);
        pol.join(people[10], t2);
        pol.attachTown(n, t2);
        pol.createProvince(null, n, "North March", t2);
        Province pr = pol.provinceOf(t2);
        pol.setGovernor(player.getUUID(), pr, people[10]);
        List<Role> nr = List.copyOf(n.roles.all());
        n.roles.assign(people[0], nr.get(1).id);
        n.roles.assign(people[4], nr.get(2).id);
        n.roles.assign(people[5], nr.get(3).id);
        n.roles.add("Spymaster", 0x424242, nr.get(1).id, EnumSet.of(Perm.DIPLOMACY));

        Town ally = NpcService.create(level, at.offset(-16 * 12, 0, 0), "Calmere");
        Town foe = NpcService.create(level, at.offset(16 * 14, 0, 16 * 12), "Dravenholt");
        if (ally != null && ally.nation != null) {
            Nation an = pol.nations.get(ally.nation);
            an.opinion.put(n.id, 70);
            pol.createAlliance(null, n, "Iron Pact");
            pol.alliances.get(n.alliance).members.add(an.id);
            an.alliance = n.alliance;
        }
        if (foe != null && foe.nation != null) {
            Nation fn = pol.nations.get(foe.nation);
            WarService.declare(server, fn, n, false);
            for (War w : data.wars.values()) {
                if (!w.enemies(fn.id, n.id)) continue;
                w.phase = War.Phase.ACTIVE;
                w.deadline = level.getGameTime() + 20 * 60 * 90;
                w.scoreAttackers = 14;
                w.scoreDefenders = 23;
                BlockPos banner = at.offset(18, 0, 3);
                banner = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, banner);
                level.setBlockAndUpdate(banner, NWRegistry.SIEGE_BANNER.get().defaultBlockState());
                var s = w.newSiege(t.id, NW.dim(level), NW.chunk(banner), banner.asLong(), true, level.getGameTime());
                s.control = 0.35f;
            }
        }
        seedManaged(player);
        NW.changed(server);
    }

    /** "The Pit", run by {@link #PIT}, with the town Brimstone Gate 6 chunks west and 8 south of Highmoor (once per world). */
    private static void seedManaged(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (NationsAndWarApi.managedNation(server, PIT) != null) return;
        Town home = NW.pol(server).townOf(player.getUUID());
        BlockPos centre = (home == null ? player.blockPosition() : BlockPos.of(home.spawn)).offset(-16 * 6, 0, 16 * 8);
        Nation pit = NationsAndWarApi.createManagedNation(server, PIT, "The Pit", "nationsandwar.gov.theocracy", 0x9C1F1F);
        NationsAndWarApi.createManagedTown(player.serverLevel(), pit, "Brimstone Gate", centre, NationsAndWarApi.square(centre, 2), null, false, null, null);
    }

    /** Stands the player between Highmoor and the Pit (2 chunks west, 4 south of Highmoor) so the map shows both. */
    public static void toManagedView(ServerPlayer player) {
        Town t = NW.pol(player.getServer()).townOf(player.getUUID());
        if (t != null) stand(player, BlockPos.of(t.spawn).offset(-32, 0, 64));
    }

    /** A guard, a soldier, an envoy, a Town Core and a Siege Banner lined up in front of the player (facing south). */
    public static void showcase(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Politics pol = NW.pol(player.getServer());
        Nation n = pol.nationOfPlayer(player.getUUID());
        Town t = pol.townOf(player.getUUID());
        player.teleportTo(level, player.getX(), player.getY(), player.getZ(), 0, 10);
        BlockPos base = player.blockPosition().offset(0, 0, 5);
        var g = NpcService.spawnGuard(level, t, n, base.offset(-2, 0, 0), 1, false);
        var s = NpcService.spawnGuard(level, null, n, base.offset(2, 0, 0), 1, true);
        for (var e : new net.minecraft.world.entity.Mob[]{g, s}) {
            if (e == null) continue;
            e.setNoAi(true);
            e.moveTo(e.getX(), e.getY(), e.getZ(), 180, 0);
            e.setYHeadRot(180);
            e.yBodyRot = 180;
        }
        g.moveTo(base.getX() - 1.5, base.getY(), base.getZ() + 0.5, 180, 0);
        s.moveTo(base.getX() + 2.5, base.getY(), base.getZ() + 0.5, 180, 0);
        var envoy = NWRegistry.ENVOY.get().create(level);
        if (envoy != null && n != null) {
            envoy.moveTo(base.getX() + 0.5, base.getY(), base.getZ() + 1.5, 180, 0);
            envoy.setNoAi(true);
            envoy.setYHeadRot(180);
            envoy.yBodyRot = 180;
            envoy.assign(n, base);
            level.addFreshEntity(envoy);
        }
        level.setBlockAndUpdate(base.offset(-1, 0, -2), NWRegistry.TOWN_CORE.get().defaultBlockState());
        level.setBlockAndUpdate(base.offset(1, 0, -2), NWRegistry.SIEGE_BANNER.get().defaultBlockState());
        org.papiricoh.nationsandwar.NationsAndWar.LOGGER.info("[preview] showcase at {} core={} banner={}", base,
                level.getBlockState(base.offset(-1, 0, -2)), level.getBlockState(base.offset(1, 0, -2)));
    }

    /** An envoy of the NPC nation at war with the player's (else any NPC nation) in front of the player, talked to at once. */
    public static void envoy(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        NWSavedData data = NW.data(player.getServer());
        Nation mine = data.politics.nationOfPlayer(player.getUUID());
        Nation npc = null;
        for (Nation n : data.politics.nations.values()) {
            if (!n.npc) continue;
            if (npc == null || mine != null && WarService.atWar(data, n.id, mine.id)) npc = n;
        }
        var envoy = NWRegistry.ENVOY.get().create(level);
        if (envoy == null || npc == null) return;
        player.teleportTo(level, player.getX(), player.getY(), player.getZ(), 0, 10);
        BlockPos at = player.blockPosition().offset(0, 0, 3);
        envoy.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 180, 0);
        envoy.setNoAi(true);
        envoy.setYHeadRot(180);
        envoy.yBodyRot = 180;
        envoy.assign(npc, at);
        level.addFreshEntity(envoy);
        net.minecraft.nbt.CompoundTag args = new net.minecraft.nbt.CompoundTag();
        args.putString("nation", npc.id.toString());
        args.putInt("entity", envoy.getId());
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new org.papiricoh.nationsandwar.network.OpenScreenPayload("envoy", args));
    }

    /** Ends the sample war with the player's nation as the winner and the enemy's capital fallen, opening the treaty window. */
    public static void treaty(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        NWSavedData data = NW.data(server);
        Nation mine = data.politics.nationOfPlayer(player.getUUID());
        if (mine == null) return;
        for (War w : List.copyOf(data.wars.values())) {
            if (!w.live() || !w.involves(mine.id)) continue;
            Nation foe = data.politics.nations.get(w.attacker.equals(mine.id) ? w.defender : w.attacker);
            if (foe != null && foe.capital != null) w.fallen.add(foe.capital);
            WarService.end(server, w, w.attacker.equals(mine.id) ? War.Outcome.ATTACKERS : War.Outcome.DEFENDERS);
        }
        NW.changed(server);
    }

    /** Stands the player in the second town, Ashdale (province North March), for the territory notice. */
    public static void toAshdale(ServerPlayer player) {
        for (Town t : NW.pol(player.getServer()).towns.values()) if (t.name.equals("Ashdale")) stand(player, BlockPos.of(t.spawn));
    }

    /** Stands the player a few blocks west of the sample siege's banner, facing it. */
    public static void toSiege(ServerPlayer player) {
        for (War w : NW.data(player.getServer()).wars.values())
            for (var s : w.sieges) {
                stand(player, BlockPos.of(s.banner).offset(-4, 0, 0));
                return;
            }
    }

    private static void stand(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        BlockPos top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos);
        player.teleportTo(level, top.getX() + 0.5, top.getY(), top.getZ() + 0.5, -90, 10);
    }
}
