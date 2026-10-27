package org.papiricoh.nationsandwar.core;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.papiricoh.nationsandwar.model.Alliance;
import org.papiricoh.nationsandwar.model.ChunkPerms;
import org.papiricoh.nationsandwar.model.Flag;
import org.papiricoh.nationsandwar.model.GovernmentType;
import org.papiricoh.nationsandwar.model.Nation;
import org.papiricoh.nationsandwar.model.Perm;
import org.papiricoh.nationsandwar.model.Politics;
import org.papiricoh.nationsandwar.model.Province;
import org.papiricoh.nationsandwar.model.Result;
import org.papiricoh.nationsandwar.model.Role;
import org.papiricoh.nationsandwar.model.Town;
import org.papiricoh.nationsandwar.network.ActionPayload;
import org.papiricoh.nationsandwar.network.NoticePayload;
import org.papiricoh.nationsandwar.network.RateLimiter;
import org.papiricoh.nationsandwar.npc.NpcService;
import org.papiricoh.nationsandwar.registry.NWRegistry;
import org.papiricoh.nationsandwar.war.WarService;

import java.util.UUID;

/**
 * Every political action, for the commands and the GUI alike: {@code run(player, "town.claim", args)}. Arguments are NBT;
 * ids are UUID strings, players a UUID or a name, chunks {@code x}/{@code z} (chunk coordinates; the player's chunk if
 * absent). The full list is in docs/CONTRACT.md.
 */
public final class Actions {
    private Actions() {
    }

    public static void handle(ActionPayload payload, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer p)) return;
        if (!RateLimiter.allow(p, "action", 8, 16)) return;
        switch (payload.action()) {
            case "view" -> {
                Sync.watch(p, true);
                Sync.sendView(p);
            }
            case "close" -> Sync.watch(p, false);
            default -> {
                Result r = run(p, payload.action(), payload.args());
                NW.reply(p, r);
                Sync.sendView(p);
            }
        }
    }

    /** Runs an action and, when it went through, marks politics changed. The caller shows the result. */
    public static Result run(ServerPlayer p, String action, CompoundTag a) {
        Result r;
        try {
            r = dispatch(p, action, a);
        } catch (MissingOwner e) {
            r = Result.fail("no_membership");
        } catch (IllegalArgumentException e) {
            r = Result.fail("bad_args");
        }
        if (r.ok()) NW.changed(p.getServer());
        return r;
    }

    private static Result dispatch(ServerPlayer p, String action, CompoundTag a) {
        MinecraftServer server = p.getServer();
        Politics pol = NW.pol(server);
        UUID me = p.getUUID();
        Town town = pol.townOf(me);
        Nation nation = pol.nationOf(town);
        String group = action.contains(".") ? action.substring(0, action.indexOf('.')) : action;
        if (group.equals("war")) return WarService.action(p, action.substring(4), a);
        if (group.equals("npc")) return NpcService.action(p, action.substring(4), a);
        if (group.equals("role")) return role(p, pol, action.substring(5), a);

        switch (action) {
            // --- town ------------------------------------------------------------------------------------------------------
            case "town.found": {
                if (pol.townOf(me) != null) return Result.fail("already_in_town");
                ServerLevel level = p.serverLevel();
                BlockPos core = p.blockPosition();
                if (!level.getBlockState(core).canBeReplaced()) return Result.fail("core_blocked");
                int cost = pol.rules.townCost();
                if (!p.getAbilities().instabuild && Emeralds.count(p) < cost) return Result.fail("player_poor", cost);
                Result r = pol.foundTown(me, a.getString("name"), NW.dim(level), NW.chunk(core), core.asLong(), level.getGameTime());
                if (!r.ok()) return r;
                Emeralds.take(p, cost);
                level.setBlockAndUpdate(core, NWRegistry.TOWN_CORE.get().defaultBlockState());
                Notices.toAll(server, NoticePayload.Kind.POLITICS, Component.translatable("nationsandwar.notice.town_founded"),
                        Component.literal(a.getString("name").trim()), pol.townOf(me).color, "flag");
                return r;
            }
            case "town.claim": {
                Town t = need(town);
                return pol.claim(me, t, NW.dim(p.level()), chunk(p, a), a.getBoolean("outpost"));
            }
            case "town.unclaim":
                return pol.unclaim(me, need(town), chunk(p, a));
            case "town.invite": {
                UUID target = player(server, a);
                Result r = pol.invite(me, need(town), target);
                if (r.ok()) notify(server, target, NoticePayload.Kind.POLITICS, Component.translatable("nationsandwar.notice.town_invite"),
                        Component.literal(town.name), town.color, "scroll");
                return r;
            }
            case "town.join": {
                Town t = pol.towns.get(uuid(a, "town"));
                if (t == null) return Result.fail("unknown_town");
                Result r = pol.join(me, t);
                if (r.ok()) Notices.toTown(server, t, NoticePayload.Kind.POLITICS, Component.translatable("nationsandwar.notice.resident_joined"),
                        p.getName(), "flag");
                return r;
            }
            case "town.leave":
                return pol.leave(me);
            case "town.kick":
                return pol.kick(me, need(town), player(server, a));
            case "town.deposit": {
                Town t = need(town);
                int n = a.getInt("amount");
                if (n <= 0) return Result.fail("bad_amount");
                if (!p.getAbilities().instabuild && Emeralds.count(p) < n) return Result.fail("player_poor", n);
                Result r = pol.deposit(me, t, n);
                if (r.ok()) Emeralds.take(p, n);
                return r;
            }
            case "town.withdraw": {
                Result r = pol.withdraw(me, need(town), a.getInt("amount"));
                if (r.ok()) Emeralds.give(p, a.getInt("amount"));
                return r;
            }
            case "town.flag": {
                Flag f = Flag.byId(a.getString("flag"));
                if (f == null) return Result.fail("bad_args");
                byte v = a.getByte("value");
                Long chunk = a.contains("x") || a.getBoolean("here") ? chunk(p, a) : null;
                return pol.setFlag(me, need(town), f, v < 0 ? null : v > 0, chunk);
            }
            case "town.perm": {
                ChunkPerms.Group g = ChunkPerms.Group.valueOf(a.getString("group").toUpperCase());
                ChunkPerms.Action act = ChunkPerms.Action.valueOf(a.getString("perm").toUpperCase());
                Long chunk = a.contains("x") || a.getBoolean("here") ? chunk(p, a) : null;
                return pol.setChunkPerm(me, need(town), g, act, a.getBoolean("value"), chunk);
            }
            case "town.reset_chunk":
                return pol.resetChunk(me, need(town), chunk(p, a));
            case "town.setspawn":
                return pol.setSpawn(me, need(town), NW.dim(p.level()), NW.chunk(p.blockPosition()), p.blockPosition().asLong());
            case "town.spawn": {
                Town t = need(town);
                ServerLevel level = level(server, t.dimension);
                if (level == null) return Result.fail("wrong_dimension");
                if (WarService.inCombat(server, p)) return Result.fail("in_war_zone");
                BlockPos s = BlockPos.of(t.spawn);
                p.teleportTo(level, s.getX() + 0.5, s.getY() + 1, s.getZ() + 0.5, p.getYRot(), p.getXRot());
                return Result.ok("teleported");
            }
            case "town.rename":
                return pol.renameTown(me, need(town), a.getString("name"));
            case "town.color":
                return pol.setTownColor(me, need(town), a.getInt("color"));
            case "town.open":
                return pol.setOpen(me, need(town), a.getBoolean("value"));
            case "town.mayor":
                return pol.setMayor(me, need(town), player(server, a));
            case "town.delete": {
                Town t = need(town);
                if (WarService.townAtWar(server, t)) return Result.fail("at_war");
                Result r = pol.deleteTown(me, t);
                if (r.ok()) removeCore(server, t);
                return r;
            }

            // --- provinces -------------------------------------------------------------------------------------------------
            case "province.create": {
                Nation n = need(nation);
                Town capital = a.contains("town") ? pol.towns.get(uuid(a, "town")) : town;
                if (capital == null) return Result.fail("unknown_town");
                return pol.createProvince(me, n, a.getString("name"), capital);
            }
            case "province.add":
                return pol.provinceAddTown(me, province(pol, a), townArg(pol, a));
            case "province.remove":
                return pol.provinceRemoveTown(me, province(pol, a), townArg(pol, a));
            case "province.capital":
                return pol.setProvinceCapital(me, province(pol, a), townArg(pol, a));
            case "province.governor": {
                Province pr = province(pol, a);
                UUID gov = a.getString("player").isEmpty() ? null : player(server, a);
                Result r = pol.setGovernor(me, pr, gov);
                if (r.ok() && gov != null) notify(server, gov, NoticePayload.Kind.POLITICS, Component.translatable("nationsandwar.notice.governor"),
                        Component.literal(pr.name), pol.nations.get(pr.nation).color, "crown");
                return r;
            }
            case "province.rename":
                return pol.renameProvince(me, province(pol, a), a.getString("name"));
            case "province.delete":
                return pol.deleteProvince(me, province(pol, a));

            // --- nations ---------------------------------------------------------------------------------------------------
            case "nation.found": {
                Result r = pol.foundNation(me, a.getString("name"), GovernmentType.byId(a.getString("gov")), p.level().getGameTime());
                if (r.ok()) {
                    Nation n = pol.nationOfPlayer(me);
                    Notices.toAll(server, NoticePayload.Kind.POLITICS, Component.translatable("nationsandwar.notice.nation_founded"),
                            Component.literal(n.name), n.color, "crown");
                }
                return r;
            }
            case "nation.invite": {
                Town t = townArg(pol, a);
                Result r = pol.nationInvite(me, need(nation), t);
                if (r.ok()) notify(server, t.mayor(), NoticePayload.Kind.POLITICS, Component.translatable("nationsandwar.notice.nation_invite"),
                        Component.literal(nation.name), nation.color, "scroll");
                return r;
            }
            case "nation.join": {
                Nation n = pol.nations.get(uuid(a, "nation"));
                if (n == null) return Result.fail("unknown_nation");
                Result r = pol.nationJoin(me, n);
                if (r.ok()) Notices.toNation(server, n, NoticePayload.Kind.POLITICS, Component.translatable("nationsandwar.notice.town_joined_nation"),
                        Component.literal(town.name), "flag");
                return r;
            }
            case "nation.leave": {
                if (nation != null && WarService.nationAtWar(server, nation.id)) return Result.fail("at_war");
                return pol.nationLeave(me);
            }
            case "nation.kick":
                return pol.nationKick(me, need(nation), townArg(pol, a));
            case "nation.capital":
                return pol.setCapital(me, need(nation), townArg(pol, a));
            case "nation.leader":
                return pol.setLeader(me, need(nation), player(server, a));
            case "nation.gov":
                return pol.setGovernment(me, need(nation), GovernmentType.byId(a.getString("gov")));
            case "nation.rename":
                return pol.renameNation(me, need(nation), a.getString("name"));
            case "nation.color":
                return pol.setNationColor(me, need(nation), a.getInt("color"));
            case "nation.deposit": {
                Nation n = need(nation);
                int amount = a.getInt("amount");
                if (amount <= 0) return Result.fail("bad_amount");
                if (!p.getAbilities().instabuild && Emeralds.count(p) < amount) return Result.fail("player_poor", amount);
                Result r = pol.nationDeposit(me, n, amount);
                if (r.ok()) Emeralds.take(p, amount);
                return r;
            }
            case "nation.withdraw": {
                Result r = pol.nationWithdraw(me, need(nation), a.getInt("amount"));
                if (r.ok()) Emeralds.give(p, a.getInt("amount"));
                return r;
            }
            case "nation.delete": {
                Nation n = need(nation);
                if (WarService.nationAtWar(server, n.id)) return Result.fail("at_war");
                return pol.deleteNation(me, n);
            }

            // --- alliances -------------------------------------------------------------------------------------------------
            case "alliance.create":
                return pol.createAlliance(me, need(nation), a.getString("name"));
            case "alliance.invite": {
                Nation n = need(nation);
                Alliance al = n.alliance == null ? null : pol.alliances.get(n.alliance);
                if (al == null) return Result.fail("no_alliance");
                Nation target = pol.nations.get(uuid(a, "nation"));
                if (target == null) return Result.fail("unknown_nation");
                Result r = pol.allianceInvite(me, al, target);
                if (r.ok()) {
                    if (target.npc) NpcService.allianceInvite(server, target, n);
                    else notify(server, target.leader(), NoticePayload.Kind.POLITICS, Component.translatable("nationsandwar.notice.alliance_invite"),
                            Component.literal(al.name), al.color, "handshake");
                }
                return r;
            }
            case "alliance.join": {
                Alliance al = pol.alliances.get(uuid(a, "alliance"));
                if (al == null) return Result.fail("unknown_alliance");
                Result r = pol.allianceJoin(me, need(nation), al);
                if (r.ok()) for (UUID m : al.members) {
                    Nation mn = pol.nations.get(m);
                    if (mn != null) Notices.toNation(server, mn, NoticePayload.Kind.POLITICS, Component.translatable("nationsandwar.notice.alliance_joined"),
                            Component.literal(nation.name + " → " + al.name), "handshake");
                }
                return r;
            }
            case "alliance.leave":
                return pol.allianceLeave(me, need(nation));
            case "alliance.kick": {
                Nation n = need(nation);
                Alliance al = n.alliance == null ? null : pol.alliances.get(n.alliance);
                Nation target = pol.nations.get(uuid(a, "nation"));
                if (al == null || target == null) return Result.fail("no_alliance");
                return pol.allianceKick(me, al, target);
            }
            default:
                return Result.fail("unknown_action");
        }
    }

    private static Result role(ServerPlayer p, Politics pol, String sub, CompoundTag a) {
        UUID me = p.getUUID();
        Town town = pol.townOf(me);
        Object owner = "nation".equals(a.getString("scope")) ? pol.nationOf(town) : town;
        if (owner == null) return Result.fail(town == null ? "no_town" : "no_nation");
        MinecraftServer server = p.getServer();
        return switch (sub) {
            case "add" -> pol.roleAdd(me, owner, a.getString("name"), a.getInt("color"), a.contains("parent") ? a.getInt("parent") : 0);
            case "rename" -> pol.roleRename(me, owner, a.getInt("id"), a.getString("name"));
            case "color" -> pol.roleRecolor(me, owner, a.getInt("id"), a.getInt("color"));
            case "move" -> pol.roleMove(me, owner, a.getInt("id"), a.getInt("parent"));
            case "perms" -> pol.rolePerms(me, owner, a.getInt("id"), Perm.ofMask(a.getInt("perms")));
            case "remove" -> pol.roleRemove(me, owner, a.getInt("id"));
            case "assign" -> {
                UUID target = player(server, a);
                Result r = pol.roleAssign(me, owner, target, a.getInt("id"));
                if (r.ok()) {
                    Role role = Politics.treeOf(owner).get(a.getInt("id"));
                    Component name = role.name.startsWith("@") ? Component.translatable(role.name.substring(1)) : Component.literal(role.name);
                    notify(server, target, NoticePayload.Kind.POLITICS, Component.translatable("nationsandwar.notice.role_given"), name, role.color, "crown");
                }
                yield r;
            }
            case "unassign" -> pol.roleUnassign(me, owner, player(server, a));
            default -> Result.fail("unknown_action");
        };
    }

    // --- argument helpers --------------------------------------------------------------------------------------------------

    private static <T> T need(T value) {
        if (value == null) throw new MissingOwner();
        return value;
    }

    /** Thrown when the actor has no town or nation for a town/nation action; turned into a "no_town" reply. */
    private static final class MissingOwner extends IllegalArgumentException {
    }

    static long chunk(ServerPlayer p, CompoundTag a) {
        if (a.contains("x") && a.contains("z")) return ChunkPos.asLong(a.getInt("x"), a.getInt("z"));
        return NW.chunk(p.blockPosition());
    }

    static UUID uuid(CompoundTag a, String key) {
        if (a.contains(key, Tag.TAG_INT_ARRAY)) return a.getUUID(key);
        return UUID.fromString(a.getString(key));
    }

    static UUID player(MinecraftServer server, CompoundTag a) {
        UUID u = NW.resolve(server, a.getString("player"));
        if (u == null) throw new IllegalArgumentException("player");
        return u;
    }

    private static Town townArg(Politics pol, CompoundTag a) {
        Town t = pol.towns.get(uuid(a, "town"));
        if (t == null) throw new IllegalArgumentException("town");
        return t;
    }

    private static Province province(Politics pol, CompoundTag a) {
        Province p = pol.provinces.get(uuid(a, "province"));
        if (p == null) throw new IllegalArgumentException("province");
        return p;
    }

    static ServerLevel level(MinecraftServer server, String dim) {
        for (ServerLevel l : server.getAllLevels()) if (NW.dim(l).equals(dim)) return l;
        return null;
    }

    static void notify(MinecraftServer server, UUID player, NoticePayload.Kind kind, Component title, Component sub, int color, String icon) {
        if (player == null) return;
        ServerPlayer target = server.getPlayerList().getPlayer(player);
        if (target != null) Notices.send(target, kind, title, sub, color, icon);
    }

    public static void removeCore(MinecraftServer server, Town t) {
        ServerLevel level = level(server, t.dimension);
        if (level == null) return;
        BlockPos core = BlockPos.of(t.core);
        if (level.getBlockState(core).is(NWRegistry.TOWN_CORE.get())) level.setBlockAndUpdate(core, Blocks.AIR.defaultBlockState());
    }
}
