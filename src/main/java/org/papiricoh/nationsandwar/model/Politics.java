package org.papiricoh.nationsandwar.model;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Every town, province, nation and alliance, the claim index, and the rules of politics (pure: no world, no players online;
 * callers pass UUIDs, chunk keys and the game time, and turn the {@link Result} into chat, sync and notices). Emeralds move
 * between banks here; taking them from or giving them to a player's inventory is the caller's job.
 */
public final class Politics {
    public static final Pattern NAME = Pattern.compile("[\\p{L}\\p{N} _'\\-]{3,24}");

    public final Map<UUID, Town> towns = new LinkedHashMap<>();
    public final Map<UUID, Province> provinces = new LinkedHashMap<>();
    public final Map<UUID, Nation> nations = new LinkedHashMap<>();
    public final Map<UUID, Alliance> alliances = new LinkedHashMap<>();
    /** dimension → chunk → town. */
    private final Map<String, Map<Long, UUID>> claimIndex = new HashMap<>();
    /** player → town (residents). */
    private final Map<UUID, UUID> residence = new HashMap<>();

    public Rules rules = Rules.DEFAULTS;
    /** Hears every change (see {@link PoliticsObserver}); {@code core} installs the one that posts the API's events. */
    public PoliticsObserver observer = PoliticsObserver.NONE;
    /** Chunks no town may claim (dimension id, chunk key): other mods reserve them through {@code api.ClaimVetoes}. */
    public java.util.function.BiPredicate<String, Long> claimVeto = (dim, chunk) -> false;

    // --- queries -----------------------------------------------------------------------------------------------------------

    public Town townAt(String dim, long chunk) {
        Map<Long, UUID> m = claimIndex.get(dim);
        UUID id = m == null ? null : m.get(chunk);
        return id == null ? null : towns.get(id);
    }

    public Map<Long, UUID> claimsIn(String dim) {
        return claimIndex.getOrDefault(dim, Map.of());
    }

    public Town townOf(UUID player) {
        UUID id = residence.get(player);
        return id == null ? null : towns.get(id);
    }

    public Nation nationOf(Town town) {
        return town == null || town.nation == null ? null : nations.get(town.nation);
    }

    public Nation nationOfPlayer(UUID player) {
        return nationOf(townOf(player));
    }

    public Province provinceOf(Town town) {
        return town == null || town.province == null ? null : provinces.get(town.province);
    }

    public Set<UUID> citizens(Nation nation) {
        Set<UUID> out = new LinkedHashSet<>();
        for (UUID t : nation.towns) {
            Town town = towns.get(t);
            if (town != null) out.addAll(town.residents);
        }
        return out;
    }

    public boolean allied(UUID nationA, UUID nationB) {
        if (nationA == null || nationB == null) return false;
        if (nationA.equals(nationB)) return true;
        Nation a = nations.get(nationA), b = nations.get(nationB);
        return a != null && b != null && a.alliance != null && a.alliance.equals(b.alliance);
    }

    /** How {@code player} stands towards {@code town}'s chunks. */
    public ChunkPerms.Group group(UUID player, Town town) {
        Town mine = townOf(player);
        if (mine == town) return ChunkPerms.Group.RESIDENT;
        if (mine == null || mine.nation == null || town.nation == null) return ChunkPerms.Group.OUTSIDER;
        if (mine.nation.equals(town.nation)) return ChunkPerms.Group.NATION;
        return allied(mine.nation, town.nation) ? ChunkPerms.Group.ALLY : ChunkPerms.Group.OUTSIDER;
    }

    /**
     * Whether {@code player} may act with {@code perm} for {@code town}: through the town's roles (as a resident), the
     * nation's roles (any town of the nation), or the Governor role (towns of the province they govern).
     */
    public boolean can(UUID player, Town town, Perm perm) {
        if (town.residents.contains(player) && town.roles.permsOf(player).contains(perm)) return true;
        Nation n = nationOf(town);
        if (n == null || !citizens(n).contains(player)) return false;
        if (n.roles.permsOf(player).contains(perm)) return true;
        Province p = provinceOf(town);
        Role gov = n.roles.governorRole();
        return p != null && player.equals(p.governor) && gov != null && gov.perms.contains(perm);
    }

    public boolean canNation(UUID player, Nation nation, Perm perm) {
        return citizens(nation).contains(player) && nation.roles.permsOf(player).contains(perm);
    }

    public int maxClaims(Town town) {
        return TownLevel.maxChunks(town.residents.size(), rules.claimScale());
    }

    // --- towns -------------------------------------------------------------------------------------------------------------

    /** Founds a town around {@code chunk}; the caller has already taken {@link Rules#townCost()} from the founder. */
    public Result foundTown(UUID founder, String name, String dim, long chunk, long core, long now) {
        if (townOf(founder) != null) return Result.fail("already_in_town");
        Result n = checkName(name, towns.values().stream().map(t -> t.name).toList());
        if (!n.ok()) return n;
        if (townAt(dim, chunk) != null) return Result.fail("chunk_taken");
        if (claimVeto.test(dim, chunk)) return Result.fail("claim_reserved");
        if (tooClose(dim, chunk, null)) return Result.fail("too_close");
        Town t = new Town(UUID.randomUUID(), name.trim(), dim, chunk);
        t.color = colorFor(t.id);
        t.roles = GovernmentType.defaultTownTree(founder);
        t.residents.add(founder);
        t.spawn = core;
        t.core = core;
        t.founded = now;
        towns.put(t.id, t);
        residence.put(founder, t.id);
        index(t, chunk);
        observer.townFounded(t);
        return Result.ok("town_founded", t.name);
    }

    /** An NPC town: no residents, its tree empty of holders, claims of {@code radius} around the centre where free. */
    public Town foundNpcTown(String name, String dim, long centre, long core, int radius, long now) {
        Town t = new Town(UUID.randomUUID(), uniqueName(name, towns.values().stream().map(x -> x.name).toList()), dim, centre);
        t.color = colorFor(t.id);
        t.roles = GovernmentType.defaultTownTree(null);
        t.npc = true;
        t.spawn = core;
        t.core = core;
        t.founded = now;
        towns.put(t.id, t);
        int cx = ChunkPos.getX(centre), cz = ChunkPos.getZ(centre);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                long c = ChunkPos.asLong(cx + dx, cz + dz);
                if (townAt(dim, c) == null && !claimVeto.test(dim, c)) index(t, c);
            }
        }
        observer.townFounded(t);
        return t;
    }

    /**
     * A town another mod runs ({@code key} = its provider): no residents, an empty tree, the given chunks where free (no
     * buffer, no veto: the provider placed it). Its nation is attached by the caller.
     */
    public Town foundManagedTown(String key, String name, String dim, long centre, long core, Collection<Long> chunks, long now) {
        Town t = new Town(UUID.randomUUID(), uniqueName(name, towns.values().stream().map(x -> x.name).toList()), dim, centre);
        t.color = colorFor(t.id);
        t.roles = GovernmentType.defaultTownTree(null);
        t.managedBy = key;
        t.spawn = core;
        t.core = core;
        t.founded = now;
        towns.put(t.id, t);
        if (townAt(dim, centre) == null) index(t, centre);
        for (long c : chunks) if (townAt(dim, c) == null) index(t, c);
        observer.townFounded(t);
        return t;
    }

    /** Claims a free chunk for a managed town (no cost, no buffer, no adjacency). */
    public boolean claimManaged(Town town, long chunk) {
        if (townAt(town.dimension, chunk) != null) return false;
        index(town, chunk);
        observer.claimed(town, chunk);
        return true;
    }

    /**
     * Claims {@code chunk} for {@code town}, paying from the town bank.
     *
     * @return ok with the price paid as argument
     */
    public Result claim(UUID actor, Town town, String dim, long chunk, boolean outpost) {
        if (actor != null && !can(actor, town, Perm.CLAIM)) return Result.fail("no_perm");
        if (!town.dimension.equals(dim)) return Result.fail("wrong_dimension");
        if (townAt(dim, chunk) != null) return Result.fail("chunk_taken");
        if (claimVeto.test(dim, chunk)) return Result.fail("claim_reserved");
        if (town.claims.size() >= maxClaims(town)) return Result.fail("claim_limit", maxClaims(town));
        boolean adjacent = adjacentTo(town, chunk);
        if (!adjacent && !outpost) return Result.fail("not_adjacent");
        if (outpost && adjacent) outpost = false;
        if (outpost && town.outposts.size() >= rules.maxOutposts()) return Result.fail("outpost_limit", rules.maxOutposts());
        if (tooClose(dim, chunk, town)) return Result.fail("too_close");
        int cost = outpost ? rules.outpostCost() : rules.nextClaimCost(town.claims.size());
        if (town.bank < cost) return Result.fail("town_poor", cost);
        town.bank -= cost;
        index(town, chunk);
        if (outpost) town.outposts.add(chunk);
        observer.claimed(town, chunk);
        return Result.ok(outpost ? "outpost_claimed" : "claimed", cost);
    }

    public Result unclaim(UUID actor, Town town, long chunk) {
        if (actor != null && !can(actor, town, Perm.UNCLAIM)) return Result.fail("no_perm");
        if (!town.claims.contains(chunk)) return Result.fail("not_yours");
        if (chunk == town.homeChunk) return Result.fail("home_chunk");
        unindex(town, chunk);
        observer.unclaimed(town, chunk);
        return Result.ok("unclaimed");
    }

    public Result invite(UUID actor, Town town, UUID target) {
        if (town.managed()) return Result.fail("managed");
        if (!can(actor, town, Perm.INVITE)) return Result.fail("no_perm");
        if (townOf(target) != null) return Result.fail("target_in_town");
        town.invites.add(target);
        return Result.ok("invited");
    }

    public Result join(UUID player, Town town) {
        if (townOf(player) != null) return Result.fail("already_in_town");
        if (town.managed()) return Result.fail("managed");
        if (!town.open && !town.invites.remove(player)) return Result.fail("not_invited");
        town.residents.add(player);
        residence.put(player, town.id);
        observer.residentJoined(town, player);
        return Result.ok("joined", town.name);
    }

    public Result leave(UUID player) {
        Town t = townOf(player);
        if (t == null) return Result.fail("no_town");
        if (t.roles.isLeader(player)) return Result.fail("mayor_cannot_leave");
        removeResident(t, player);
        return Result.ok("left", t.name);
    }

    public Result kick(UUID actor, Town town, UUID target) {
        if (!can(actor, town, Perm.KICK)) return Result.fail("no_perm");
        if (!town.residents.contains(target)) return Result.fail("not_resident");
        if (town.roles.isLeader(target) || actor.equals(target)) return Result.fail("cannot_kick");
        Role r = town.roles.roleOf(target);
        if (r != null && !town.roles.canManage(actor, r.id)) return Result.fail("cannot_kick");
        removeResident(town, target);
        return Result.ok("kicked");
    }

    private void removeResident(Town t, UUID player) {
        t.residents.remove(player);
        t.roles.unassign(player);
        residence.remove(player);
        Nation n = nationOf(t);
        if (n != null) {
            n.roles.unassign(player);
            for (UUID pid : n.provinces) {
                Province p = provinces.get(pid);
                if (p != null && player.equals(p.governor)) setGovernorRaw(n, p, null);
            }
        }
        observer.residentLeft(t, player);
    }

    public Result setMayor(UUID actor, Town town, UUID target) {
        if (!town.roles.isLeader(actor)) return Result.fail("leader_only");
        if (!town.residents.contains(target)) return Result.fail("not_resident");
        town.roles.setLeader(target);
        Nation n = nationOf(town);
        if (n != null && town.id.equals(n.capital) && actor.equals(n.leader())) {
            n.roles.setLeader(target);
            observer.leaderChanged(n, actor, target);
        }
        return Result.ok("mayor_set");
    }

    public Result deposit(UUID actor, Town town, int amount) {
        if (amount <= 0) return Result.fail("bad_amount");
        if (!town.residents.contains(actor) && !can(actor, town, Perm.BANK_DEPOSIT)) return Result.fail("no_perm");
        town.bank += amount;
        return Result.ok("deposited", amount);
    }

    public Result withdraw(UUID actor, Town town, int amount) {
        if (amount <= 0) return Result.fail("bad_amount");
        if (!can(actor, town, Perm.BANK_WITHDRAW)) return Result.fail("no_perm");
        if (town.bank < amount) return Result.fail("town_poor", amount);
        town.bank -= amount;
        return Result.ok("withdrew", amount);
    }

    /** Sets a flag town-wide ({@code chunk} null) or for one chunk ({@code value} null clears the chunk's override). */
    public Result setFlag(UUID actor, Town town, Flag flag, Boolean value, Long chunk) {
        if (!can(actor, town, Perm.MANAGE_FLAGS)) return Result.fail("no_perm");
        if (chunk == null) {
            town.flags.put(flag, value == null ? flag.defaultOn : value);
        } else {
            if (!town.claims.contains(chunk)) return Result.fail("not_yours");
            if (value == null) town.settings(chunk).flags.remove(flag);
            else town.settings(chunk).flags.put(flag, value);
            town.tidy(chunk);
        }
        return Result.ok("flag_set");
    }

    public Result setChunkPerm(UUID actor, Town town, ChunkPerms.Group group, ChunkPerms.Action action, boolean value, Long chunk) {
        if (!can(actor, town, Perm.MANAGE_PLOTS)) return Result.fail("no_perm");
        if (chunk == null) {
            town.perms = town.perms.with(group, action, value);
        } else {
            if (!town.claims.contains(chunk)) return Result.fail("not_yours");
            Town.ChunkSettings s = town.settings(chunk);
            s.perms = (s.perms == null ? town.perms : s.perms).with(group, action, value);
        }
        return Result.ok("perm_set");
    }

    public Result resetChunk(UUID actor, Town town, long chunk) {
        if (!can(actor, town, Perm.MANAGE_PLOTS)) return Result.fail("no_perm");
        town.chunkSettings.remove(chunk);
        return Result.ok("chunk_reset");
    }

    public Result setSpawn(UUID actor, Town town, String dim, long chunk, long pos) {
        if (!can(actor, town, Perm.SET_SPAWN)) return Result.fail("no_perm");
        if (!town.dimension.equals(dim) || !town.claims.contains(chunk)) return Result.fail("not_yours");
        town.spawn = pos;
        return Result.ok("spawn_set");
    }

    public Result renameTown(UUID actor, Town town, String name) {
        if (!town.roles.isLeader(actor)) return Result.fail("leader_only");
        Result n = checkName(name, towns.values().stream().filter(t -> t != town).map(t -> t.name).toList());
        if (!n.ok()) return n;
        String before = town.name;
        town.name = name.trim();
        observer.renamed(town, before);
        return Result.ok("renamed");
    }

    public Result setTownColor(UUID actor, Town town, int color) {
        if (!town.roles.isLeader(actor)) return Result.fail("leader_only");
        town.color = color & 0xFFFFFF;
        return Result.ok("recolored");
    }

    public Result setOpen(UUID actor, Town town, boolean open) {
        if (!can(actor, town, Perm.INVITE)) return Result.fail("no_perm");
        town.open = open;
        return Result.ok(open ? "town_opened" : "town_closed");
    }

    /** Disbands a town: its claims go back to the wilderness; a capital's nation picks another capital or disbands too. */
    public Result deleteTown(UUID actor, Town town) {
        if (actor != null && !town.roles.isLeader(actor)) return Result.fail("leader_only");
        removeTown(town);
        return Result.ok("town_deleted", town.name);
    }

    public void removeTown(Town town) {
        Nation n = nationOf(town);
        if (n != null) detachTown(n, town);
        for (long c : new ArrayList<>(town.claims)) unindex(town, c);
        for (UUID r : town.residents) residence.remove(r);
        towns.remove(town.id);
        observer.townRemoved(town);
    }

    // --- roles (town or nation trees) --------------------------------------------------------------------------------------

    /** The members a tree's roles may go to: a town's residents or a nation's citizens. */
    public Set<UUID> members(Object owner) {
        return owner instanceof Town t ? t.residents : owner instanceof Nation n ? citizens(n) : Set.of();
    }

    public static RoleTree treeOf(Object owner) {
        return owner instanceof Town t ? t.roles : ((Nation) owner).roles;
    }

    public Result roleAdd(UUID actor, Object owner, String name, int color, int parent) {
        RoleTree tree = treeOf(owner);
        if (!tree.isLeader(actor) && !(canManageRoles(actor, owner) && tree.canManage(actor, parent) || isOwnRole(actor, tree, parent)))
            return Result.fail("no_perm");
        return tree.add(name, color, parent, EnumSet.noneOf(Perm.class));
    }

    public Result roleRename(UUID actor, Object owner, int id, String name) {
        RoleTree tree = treeOf(owner);
        if (!manage(actor, owner, id)) return Result.fail("no_perm");
        return tree.rename(id, name);
    }

    public Result roleRecolor(UUID actor, Object owner, int id, int color) {
        if (!manage(actor, owner, id)) return Result.fail("no_perm");
        return treeOf(owner).recolor(id, color);
    }

    public Result roleMove(UUID actor, Object owner, int id, int parent) {
        RoleTree tree = treeOf(owner);
        if (!manage(actor, owner, id)) return Result.fail("no_perm");
        if (!tree.isLeader(actor) && !(tree.canManage(actor, parent) || isOwnRole(actor, tree, parent))) return Result.fail("no_perm");
        return tree.move(id, parent);
    }

    /** Sets a role's perms; a non-leader may only grant perms they hold themselves. Town trees take no nation-only perms. */
    public Result rolePerms(UUID actor, Object owner, int id, Set<Perm> perms) {
        RoleTree tree = treeOf(owner);
        if (!manage(actor, owner, id)) return Result.fail("no_perm");
        EnumSet<Perm> wanted = perms.isEmpty() ? EnumSet.noneOf(Perm.class) : EnumSet.copyOf(perms);
        if (owner instanceof Town) wanted.removeAll(Perm.NATION_ONLY);
        if (!tree.isLeader(actor) && !tree.permsOf(actor).containsAll(wanted)) return Result.fail("cannot_grant");
        return tree.setPerms(id, wanted);
    }

    public Result roleRemove(UUID actor, Object owner, int id) {
        if (!manage(actor, owner, id)) return Result.fail("no_perm");
        return treeOf(owner).remove(id);
    }

    public Result roleAssign(UUID actor, Object owner, UUID target, int id) {
        RoleTree tree = treeOf(owner);
        if (!manage(actor, owner, id)) return Result.fail("no_perm");
        if (!members(owner).contains(target)) return Result.fail("not_member");
        if (tree.isLeader(target)) return Result.fail("cannot_assign_leader");
        Role current = tree.roleOf(target);
        if (current != null && !tree.canManage(actor, current.id)) return Result.fail("no_perm");
        return tree.assign(target, id);
    }

    public Result roleUnassign(UUID actor, Object owner, UUID target) {
        RoleTree tree = treeOf(owner);
        Role current = tree.roleOf(target);
        if (current == null || current.kind == Role.Kind.LEADER) return Result.fail("no_role");
        if (!manage(actor, owner, current.id)) return Result.fail("no_perm");
        tree.unassign(target);
        return Result.ok("role.unassigned");
    }

    private boolean canManageRoles(UUID actor, Object owner) {
        return owner instanceof Town t ? can(actor, t, Perm.MANAGE_ROLES) : canNation(actor, (Nation) owner, Perm.MANAGE_ROLES);
    }

    private boolean manage(UUID actor, Object owner, int id) {
        RoleTree tree = treeOf(owner);
        if (tree.isLeader(actor)) return true;
        return canManageRoles(actor, owner) && tree.canManage(actor, id);
    }

    private static boolean isOwnRole(UUID actor, RoleTree tree, int id) {
        Role mine = tree.roleOf(actor);
        return mine != null && mine.id == id && mine.has(Perm.MANAGE_ROLES);
    }

    // --- provinces ---------------------------------------------------------------------------------------------------------

    /** Creates a province around {@code capital} (a town of the nation in no province), paying from the nation bank. */
    public Result createProvince(UUID actor, Nation nation, String name, Town capital) {
        if (actor != null && !canNation(actor, nation, Perm.MANAGE_PROVINCES)) return Result.fail("no_perm");
        if (!nation.towns.contains(capital.id)) return Result.fail("not_nation_town");
        if (capital.province != null) return Result.fail("town_in_province");
        Result n = checkName(name, provinces.values().stream().filter(p -> p.nation.equals(nation.id)).map(p -> p.name).toList());
        if (!n.ok()) return n;
        if (actor != null && nation.bank < rules.provinceCost()) return Result.fail("nation_poor", rules.provinceCost());
        if (actor != null) nation.bank -= rules.provinceCost();
        Province p = new Province(UUID.randomUUID(), name.trim(), nation.id);
        p.capital = capital.id;
        p.towns.add(capital.id);
        capital.province = p.id;
        provinces.put(p.id, p);
        nation.provinces.add(p.id);
        return Result.ok("province_created", p.name);
    }

    public Result provinceAddTown(UUID actor, Province p, Town town) {
        Nation n = nations.get(p.nation);
        if (!canNation(actor, n, Perm.MANAGE_PROVINCES)) return Result.fail("no_perm");
        if (!n.towns.contains(town.id)) return Result.fail("not_nation_town");
        Province old = provinceOf(town);
        if (old == p) return Result.fail("town_in_province");
        if (old != null) {
            if (town.id.equals(old.capital)) return Result.fail("province_capital");
            old.towns.remove(town.id);
        }
        p.towns.add(town.id);
        town.province = p.id;
        return Result.ok("province_town_added");
    }

    public Result provinceRemoveTown(UUID actor, Province p, Town town) {
        Nation n = nations.get(p.nation);
        if (!canNation(actor, n, Perm.MANAGE_PROVINCES)) return Result.fail("no_perm");
        if (!p.towns.contains(town.id)) return Result.fail("not_in_province");
        if (town.id.equals(p.capital)) return Result.fail("province_capital");
        p.towns.remove(town.id);
        town.province = null;
        return Result.ok("province_town_removed");
    }

    public Result setProvinceCapital(UUID actor, Province p, Town town) {
        Nation n = nations.get(p.nation);
        if (!canNation(actor, n, Perm.MANAGE_PROVINCES)) return Result.fail("no_perm");
        if (!p.towns.contains(town.id)) return Result.fail("not_in_province");
        p.capital = town.id;
        return Result.ok("province_capital_set");
    }

    /** Names a governor (a citizen of the nation), or clears it with null. Governors show in the nation's Governor role. */
    public Result setGovernor(UUID actor, Province p, UUID governor) {
        Nation n = nations.get(p.nation);
        if (!canNation(actor, n, Perm.MANAGE_PROVINCES)) return Result.fail("no_perm");
        if (governor != null && !citizens(n).contains(governor)) return Result.fail("not_member");
        setGovernorRaw(n, p, governor);
        return Result.ok("governor_set");
    }

    private void setGovernorRaw(Nation n, Province p, UUID governor) {
        p.governor = governor;
        Role gov = n.roles.governorRole();
        if (gov == null) return;
        gov.holders.clear();
        for (UUID pid : n.provinces) {
            Province q = provinces.get(pid);
            if (q != null && q.governor != null) gov.holders.add(q.governor);
        }
    }

    public Result renameProvince(UUID actor, Province p, String name) {
        Nation n = nations.get(p.nation);
        if (!canNation(actor, n, Perm.MANAGE_PROVINCES)) return Result.fail("no_perm");
        Result r = checkName(name, provinces.values().stream().filter(q -> q != p && q.nation.equals(n.id)).map(q -> q.name).toList());
        if (!r.ok()) return r;
        p.name = name.trim();
        return Result.ok("renamed");
    }

    public Result deleteProvince(UUID actor, Province p) {
        Nation n = nations.get(p.nation);
        if (actor != null && !canNation(actor, n, Perm.MANAGE_PROVINCES)) return Result.fail("no_perm");
        removeProvince(n, p);
        return Result.ok("province_deleted", p.name);
    }

    private void removeProvince(Nation n, Province p) {
        for (UUID t : p.towns) {
            Town town = towns.get(t);
            if (town != null) town.province = null;
        }
        provinces.remove(p.id);
        n.provinces.remove(p.id);
        setGovernorRaw(n, p, null);
    }

    // --- nations -----------------------------------------------------------------------------------------------------------

    /** Founds a nation with the founder's town as capital, paying from that town's bank. */
    public Result foundNation(UUID founder, String name, GovernmentType gov, long now) {
        Town town = townOf(founder);
        if (town == null) return Result.fail("no_town");
        if (!town.roles.isLeader(founder)) return Result.fail("leader_only");
        if (town.nation != null) return Result.fail("already_in_nation");
        Result n = checkName(name, nations.values().stream().map(x -> x.name).toList());
        if (!n.ok()) return n;
        if (town.bank < rules.nationCost()) return Result.fail("town_poor", rules.nationCost());
        town.bank -= rules.nationCost();
        Nation nation = newNation(name.trim(), gov, founder, now);
        nation.capital = town.id;
        attachTown(nation, town);
        observer.nationFounded(nation);
        return Result.ok("nation_founded", nation.name);
    }

    public Nation foundNpcNation(String name, GovernmentType gov, Town capital, long now) {
        Nation nation = newNation(uniqueName(name, nations.values().stream().map(x -> x.name).toList()), gov, null, now);
        nation.npc = true;
        nation.capital = capital.id;
        attachTown(nation, capital);
        observer.nationFounded(nation);
        return nation;
    }

    /**
     * A nation another mod runs ({@code key} = its provider): no leader, no towns yet (the first one attached becomes its
     * capital), names from {@code titles} (a lang prefix) when not null.
     */
    public Nation foundManagedNation(String key, String name, String titles, int color, long now) {
        Nation nation = newNation(uniqueName(name, nations.values().stream().map(x -> x.name).toList()), GovernmentType.KINGDOM, null, now);
        nation.managedBy = key;
        nation.titles = titles;
        nation.color = color & 0xFFFFFF;
        observer.nationFounded(nation);
        return nation;
    }

    private Nation newNation(String name, GovernmentType gov, UUID leader, long now) {
        Nation nation = new Nation(UUID.randomUUID(), name, gov);
        nation.color = colorFor(nation.id);
        nation.roles = gov.defaultTree(leader);
        nation.founded = now;
        nations.put(nation.id, nation);
        return nation;
    }

    public void attachTown(Nation nation, Town town) {
        nation.towns.add(town.id);
        town.nation = nation.id;
        nation.invites.remove(town.id);
        if (nation.capital == null) nation.capital = town.id;
        observer.townJoinedNation(nation, town);
    }

    public Result nationInvite(UUID actor, Nation nation, Town town) {
        if (nation.managed() || town.managed()) return Result.fail("managed");
        if (!canNation(actor, nation, Perm.MANAGE_TOWNS)) return Result.fail("no_perm");
        if (town.nation != null) return Result.fail("already_in_nation");
        nation.invites.add(town.id);
        return Result.ok("invited");
    }

    public Result nationJoin(UUID mayor, Nation nation) {
        Town town = townOf(mayor);
        if (town == null || !town.roles.isLeader(mayor)) return Result.fail("leader_only");
        if (town.nation != null) return Result.fail("already_in_nation");
        if (nation.managed()) return Result.fail("managed");
        if (!nation.invites.contains(town.id)) return Result.fail("not_invited");
        attachTown(nation, town);
        return Result.ok("nation_joined", nation.name);
    }

    public Result nationLeave(UUID mayor) {
        Town town = townOf(mayor);
        if (town == null || !town.roles.isLeader(mayor)) return Result.fail("leader_only");
        Nation n = nationOf(town);
        if (n == null) return Result.fail("no_nation");
        if (town.id.equals(n.capital)) return Result.fail("capital_cannot_leave");
        detachTown(n, town);
        return Result.ok("nation_left", n.name);
    }

    public Result nationKick(UUID actor, Nation nation, Town town) {
        if (!canNation(actor, nation, Perm.MANAGE_TOWNS)) return Result.fail("no_perm");
        if (!nation.towns.contains(town.id)) return Result.fail("not_nation_town");
        if (town.id.equals(nation.capital)) return Result.fail("capital_cannot_leave");
        detachTown(nation, town);
        return Result.ok("kicked");
    }

    /** Takes a town out of its nation (province, roles of its residents); a capital leaving moves the capital or disbands. */
    public void detachTown(Nation n, Town town) {
        Province p = provinceOf(town);
        if (p != null) {
            p.towns.remove(town.id);
            if (p.towns.isEmpty()) removeProvince(n, p);
            else if (town.id.equals(p.capital)) p.capital = p.towns.iterator().next();
            for (UUID r : town.residents) if (r.equals(p.governor)) setGovernorRaw(n, p, null);
        }
        for (UUID r : town.residents) n.roles.unassign(r);
        n.towns.remove(town.id);
        town.nation = null;
        town.province = null;
        observer.townLeftNation(n, town);
        if (town.id.equals(n.capital)) {
            if (n.towns.isEmpty()) {
                removeNation(n);
            } else {
                Town next = towns.get(n.towns.iterator().next());
                n.capital = next.id;
                UUID before = n.roles.leader();
                if (before != null && town.residents.contains(before)) {
                    n.roles.setLeader(next.mayor());
                    observer.leaderChanged(n, before, next.mayor());
                }
            }
        }
    }

    public Result setCapital(UUID actor, Nation nation, Town town) {
        if (!nation.roles.isLeader(actor)) return Result.fail("leader_only");
        if (!nation.towns.contains(town.id)) return Result.fail("not_nation_town");
        nation.capital = town.id;
        return Result.ok("capital_set");
    }

    public Result setLeader(UUID actor, Nation nation, UUID target) {
        if (!nation.roles.isLeader(actor)) return Result.fail("leader_only");
        if (!citizens(nation).contains(target)) return Result.fail("not_member");
        nation.roles.setLeader(target);
        observer.leaderChanged(nation, actor, target);
        return Result.ok("leader_set");
    }

    /** Changes the government (names only). Default role names follow the new government; renamed roles keep their names. */
    public Result setGovernment(UUID actor, Nation nation, GovernmentType gov) {
        if (!nation.roles.isLeader(actor)) return Result.fail("leader_only");
        String old = "@" + nation.government.key() + ".";
        for (Role r : nation.roles.all()) {
            if (r.name.startsWith(old)) r.name = "@" + gov.key() + "." + r.name.substring(old.length());
        }
        nation.government = gov;
        return Result.ok("government_set");
    }

    public Result renameNation(UUID actor, Nation nation, String name) {
        if (!nation.roles.isLeader(actor)) return Result.fail("leader_only");
        Result n = checkName(name, nations.values().stream().filter(x -> x != nation).map(x -> x.name).toList());
        if (!n.ok()) return n;
        String before = nation.name;
        nation.name = name.trim();
        observer.renamed(nation, before);
        return Result.ok("renamed");
    }

    public Result setNationColor(UUID actor, Nation nation, int color) {
        if (!nation.roles.isLeader(actor)) return Result.fail("leader_only");
        nation.color = color & 0xFFFFFF;
        return Result.ok("recolored");
    }

    public Result nationDeposit(UUID actor, Nation nation, int amount) {
        if (amount <= 0) return Result.fail("bad_amount");
        if (!citizens(nation).contains(actor)) return Result.fail("no_perm");
        nation.bank += amount;
        return Result.ok("deposited", amount);
    }

    public Result nationWithdraw(UUID actor, Nation nation, int amount) {
        if (amount <= 0) return Result.fail("bad_amount");
        if (!canNation(actor, nation, Perm.BANK_WITHDRAW)) return Result.fail("no_perm");
        if (nation.bank < amount) return Result.fail("nation_poor", amount);
        nation.bank -= amount;
        return Result.ok("withdrew", amount);
    }

    public Result deleteNation(UUID actor, Nation nation) {
        if (actor != null && !nation.roles.isLeader(actor)) return Result.fail("leader_only");
        removeNation(nation);
        return Result.ok("nation_deleted", nation.name);
    }

    public void removeNation(Nation n) {
        for (UUID pid : new ArrayList<>(n.provinces)) {
            Province p = provinces.get(pid);
            if (p != null) removeProvince(n, p);
        }
        List<Town> freed = new ArrayList<>();
        for (UUID tid : n.towns) {
            Town t = towns.get(tid);
            if (t != null) {
                t.nation = null;
                t.province = null;
                freed.add(t);
            }
        }
        n.towns.clear();
        for (Town t : freed) observer.townLeftNation(n, t);
        if (n.alliance != null) {
            Alliance a = alliances.get(n.alliance);
            if (a != null) leaveAlliance(a, n);
        }
        for (Nation other : nations.values()) {
            other.opinion.remove(n.id);
            if (n.id.equals(other.overlord)) other.overlord = null;
        }
        nations.remove(n.id);
        observer.nationRemoved(n);
    }

    // --- alliances ---------------------------------------------------------------------------------------------------------

    public Result createAlliance(UUID actor, Nation nation, String name) {
        if (actor != null && !canNation(actor, nation, Perm.DIPLOMACY)) return Result.fail("no_perm");
        if (nation.managed()) return Result.fail("managed");
        if (nation.alliance != null) return Result.fail("already_in_alliance");
        Result n = checkName(name, alliances.values().stream().map(a -> a.name).toList());
        if (!n.ok()) return n;
        if (actor != null && nation.bank < rules.allianceCost()) return Result.fail("nation_poor", rules.allianceCost());
        if (actor != null) nation.bank -= rules.allianceCost();
        Alliance a = new Alliance(UUID.randomUUID(), name.trim(), nation.id);
        a.color = colorFor(a.id);
        alliances.put(a.id, a);
        nation.alliance = a.id;
        observer.allianceJoined(a, nation);
        return Result.ok("alliance_created", a.name);
    }

    public Result allianceInvite(UUID actor, Alliance a, Nation target) {
        Nation lead = nations.get(a.leader);
        if (actor != null && (lead == null || !canNation(actor, lead, Perm.DIPLOMACY))) return Result.fail("no_perm");
        if (target.managed() || lead != null && lead.managed()) return Result.fail("managed");
        if (target.alliance != null) return Result.fail("already_in_alliance");
        a.invites.add(target.id);
        return Result.ok("invited");
    }

    public Result allianceJoin(UUID actor, Nation nation, Alliance a) {
        if (actor != null && !canNation(actor, nation, Perm.DIPLOMACY)) return Result.fail("no_perm");
        if (nation.alliance != null) return Result.fail("already_in_alliance");
        if (nation.managed()) return Result.fail("managed");
        if (!a.invites.remove(nation.id)) return Result.fail("not_invited");
        addToAlliance(a, nation);
        return Result.ok("alliance_joined", a.name);
    }

    public Result allianceLeave(UUID actor, Nation nation) {
        if (actor != null && !canNation(actor, nation, Perm.DIPLOMACY)) return Result.fail("no_perm");
        Alliance a = nation.alliance == null ? null : alliances.get(nation.alliance);
        if (a == null) return Result.fail("no_alliance");
        leaveAlliance(a, nation);
        return Result.ok("alliance_left", a.name);
    }

    public Result allianceKick(UUID actor, Alliance a, Nation target) {
        Nation lead = nations.get(a.leader);
        if (lead == null || !canNation(actor, lead, Perm.DIPLOMACY)) return Result.fail("no_perm");
        if (!a.members.contains(target.id) || target == lead) return Result.fail("cannot_kick");
        leaveAlliance(a, target);
        return Result.ok("kicked");
    }

    /** Puts a nation in an alliance with no checks (NPC diplomacy; managed nations are refused by the callers). */
    public void addToAlliance(Alliance a, Nation nation) {
        a.members.add(nation.id);
        nation.alliance = a.id;
        observer.allianceJoined(a, nation);
    }

    private void leaveAlliance(Alliance a, Nation n) {
        a.members.remove(n.id);
        n.alliance = null;
        if (a.members.isEmpty()) {
            alliances.remove(a.id);
        } else if (a.leader.equals(n.id)) {
            a.leader = a.members.iterator().next();
        }
        observer.allianceLeft(a, n);
    }

    // --- claim index -------------------------------------------------------------------------------------------------------

    public void index(Town town, long chunk) {
        town.claims.add(chunk);
        claimIndex.computeIfAbsent(town.dimension, k -> new HashMap<>()).put(chunk, town.id);
    }

    public void unindex(Town town, long chunk) {
        town.claims.remove(chunk);
        town.outposts.remove(chunk);
        town.chunkSettings.remove(chunk);
        Map<Long, UUID> m = claimIndex.get(town.dimension);
        if (m != null && town.id.equals(m.get(chunk))) m.remove(chunk);
    }

    /** Hands a claimed chunk from one town to another (annexation, conquest). */
    public void transferChunk(Town from, Town to, long chunk) {
        unindex(from, chunk);
        index(to, chunk);
        observer.chunkTransferred(from, to, chunk);
    }

    private boolean adjacentTo(Town town, long chunk) {
        int x = ChunkPos.getX(chunk), z = ChunkPos.getZ(chunk);
        return town.claims.contains(ChunkPos.asLong(x + 1, z)) || town.claims.contains(ChunkPos.asLong(x - 1, z))
                || town.claims.contains(ChunkPos.asLong(x, z + 1)) || town.claims.contains(ChunkPos.asLong(x, z - 1));
    }

    /** Whether a chunk lies within the buffer of a town that is neither {@code self} nor of {@code self}'s nation. */
    private boolean tooClose(String dim, long chunk, Town self) {
        int b = rules.buffer();
        if (b <= 0) return false;
        int x = ChunkPos.getX(chunk), z = ChunkPos.getZ(chunk);
        Map<Long, UUID> m = claimIndex.getOrDefault(dim, Map.of());
        for (int dx = -b; dx <= b; dx++) {
            for (int dz = -b; dz <= b; dz++) {
                UUID other = m.get(ChunkPos.asLong(x + dx, z + dz));
                if (other == null || self != null && other.equals(self.id)) continue;
                Town t = towns.get(other);
                if (self != null && t != null && self.nation != null && self.nation.equals(t.nation)) continue;
                return true;
            }
        }
        return false;
    }

    // --- names, colours ----------------------------------------------------------------------------------------------------

    public static Result checkName(String name, Collection<String> taken) {
        String s = name == null ? "" : name.trim();
        if (!NAME.matcher(s).matches()) return Result.fail("bad_name");
        for (String t : taken) if (t.equalsIgnoreCase(s)) return Result.fail("name_taken");
        return Result.ok("ok");
    }

    private static String uniqueName(String base, Collection<String> taken) {
        String s = base;
        for (int i = 2; checkName(s, taken).key().equals("name_taken"); i++) s = base + " " + toRoman(i);
        return s;
    }

    private static String toRoman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n < r.length ? r[n] : Integer.toString(n);
    }

    /** A bright, stable colour from an id. */
    public static int colorFor(UUID id) {
        return hsv((Objects.hash(id) & 0xFFFF) / 65535f, 0.65f, 0.9f);
    }

    public static int hsv(float h, float s, float v) {
        float c = v * s, x = c * (1 - Math.abs((h * 6) % 2 - 1)), m = v - c;
        float[] rgb = switch ((int) (h * 6) % 6) {
            case 0 -> new float[]{c, x, 0};
            case 1 -> new float[]{x, c, 0};
            case 2 -> new float[]{0, c, x};
            case 3 -> new float[]{0, x, c};
            case 4 -> new float[]{x, 0, c};
            default -> new float[]{c, 0, x};
        };
        return (Math.round((rgb[0] + m) * 255) << 16) | (Math.round((rgb[1] + m) * 255) << 8) | Math.round((rgb[2] + m) * 255);
    }

    // --- persistence -------------------------------------------------------------------------------------------------------

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.put("towns", list(towns.values().stream().map(Town::save).toList()));
        t.put("provinces", list(provinces.values().stream().map(Province::save).toList()));
        t.put("nations", list(nations.values().stream().map(Nation::save).toList()));
        t.put("alliances", list(alliances.values().stream().map(Alliance::save).toList()));
        return t;
    }

    private static ListTag list(List<CompoundTag> tags) {
        ListTag l = new ListTag();
        l.addAll(tags);
        return l;
    }

    public static Politics load(CompoundTag t) {
        Politics p = new Politics();
        for (Tag e : t.getList("towns", Tag.TAG_COMPOUND)) {
            Town town = Town.load((CompoundTag) e);
            p.towns.put(town.id, town);
        }
        for (Tag e : t.getList("provinces", Tag.TAG_COMPOUND)) {
            Province pr = Province.load((CompoundTag) e);
            p.provinces.put(pr.id, pr);
        }
        for (Tag e : t.getList("nations", Tag.TAG_COMPOUND)) {
            Nation n = Nation.load((CompoundTag) e);
            p.nations.put(n.id, n);
        }
        for (Tag e : t.getList("alliances", Tag.TAG_COMPOUND)) {
            Alliance a = Alliance.load((CompoundTag) e);
            p.alliances.put(a.id, a);
        }
        p.reindex();
        return p;
    }

    /** Rebuilds the claim and residence indexes from the records. */
    public void reindex() {
        claimIndex.clear();
        residence.clear();
        for (Town town : towns.values()) {
            for (long c : town.claims) claimIndex.computeIfAbsent(town.dimension, k -> new HashMap<>()).put(c, town.id);
            for (UUID r : town.residents) residence.put(r, town.id);
        }
    }
}
