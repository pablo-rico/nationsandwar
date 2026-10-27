package org.papiricoh.nationsandwar.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.papiricoh.nationsandwar.NWConfig;
import org.papiricoh.nationsandwar.core.NW;
import org.papiricoh.nationsandwar.core.Notices;
import org.papiricoh.nationsandwar.data.NWSavedData;
import org.papiricoh.nationsandwar.model.Nation;
import org.papiricoh.nationsandwar.model.Persona;
import org.papiricoh.nationsandwar.model.Politics;
import org.papiricoh.nationsandwar.model.Province;
import org.papiricoh.nationsandwar.model.Result;
import org.papiricoh.nationsandwar.model.Role;
import org.papiricoh.nationsandwar.model.RoleTree;
import org.papiricoh.nationsandwar.model.Town;
import org.papiricoh.nationsandwar.network.NoticePayload;
import org.papiricoh.nationsandwar.war.War;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * The characters ({@link Persona}) who hold the roles of NPC nations and towns: staffing empty roles, succession after a
 * lost war or a fallen capital, and the court's changes by chance.
 *
 * <p>The rules ({@link #staff}, {@link #succeed}, {@link #reshuffle}, {@link #cleanup}) are pure: they take the politics
 * and a {@link Random}, and the server-facing methods add the notices and {@code NW.changed}. A seat held by a player
 * (a holder that is not in the court) is never touched, nor anything of a managed nation or town.
 */
public final class NpcCourt {
    /** How far from the capital's Core players hear of a court's changes. */
    private static final double NEAR = 128;

    /** A seat that changed hands: {@code role} is the role's name (an {@code @} lang key or literal), {@code owner} the nation's, town's or province's. */
    public record Change(String role, String owner, Persona who) {
    }

    private NpcCourt() {
    }

    /** Whether the court rules run for this nation / town: NPC and not managed by another mod. */
    public static boolean courtly(Nation n) {
        return n != null && n.npc && !n.managed();
    }

    public static boolean courtly(Town t) {
        return t != null && t.npc && !t.managed();
    }

    // --- staffing ----------------------------------------------------------------------------------------------------------

    /** Fills every empty leader/normal role of an NPC nation (never a managed one, never a player's seat). */
    public static void staff(Politics pol, Nation n, Random r, long now) {
        if (!courtly(n)) return;
        for (Role role : n.roles.all()) {
            if (role.kind == Role.Kind.GOVERNOR || !role.holders.isEmpty()) continue;
            int count = role.kind == Role.Kind.NORMAL && is(role, "marshal") && r.nextInt(3) == 0 ? 2 : 1;
            for (int i = 0; i < count; i++) role.holders.add(hire(n.court, n.name, r, now).id());
        }
        provinces(pol, n, r, now);
        cleanup(pol, n);
    }

    /** Fills every empty leader/normal role of an NPC town. */
    public static void staff(Politics pol, Town t, Random r, long now) {
        if (!courtly(t)) return;
        Nation n = pol.nationOf(t);
        String stock = n != null ? n.name : t.name;
        for (Role role : t.roles.all()) {
            if (role.kind == Role.Kind.GOVERNOR || !role.holders.isEmpty()) continue;
            role.holders.add(hire(t.court, stock, r, now).id());
        }
        cleanup(t);
    }

    /**
     * An NPC nation of two or more towns gets one province ("&lt;Nation&gt; Heartland") over every town in none; every
     * province of the nation without a governor gets a character.
     */
    private static void provinces(Politics pol, Nation n, Random r, long now) {
        if (n.towns.size() >= 2 && n.provinces.isEmpty()) {
            Town capital = n.capital == null ? null : pol.towns.get(n.capital);
            if (capital != null && capital.province == null) {
                Result made = pol.createProvince(null, n, n.name + " Heartland", capital);
                if (!made.ok()) pol.createProvince(null, n, "Heartland", capital);
            }
        }
        Province first = null;
        for (UUID pid : n.provinces) {
            first = pol.provinces.get(pid);
            if (first != null) break;
        }
        if (first == null) return;
        for (UUID tid : n.towns) {
            Town town = pol.towns.get(tid);
            if (town != null && town.province == null) pol.addToProvince(first, town);
        }
        for (UUID pid : List.copyOf(n.provinces)) {
            Province p = pol.provinces.get(pid);
            if (p != null && p.governor == null) pol.appointGovernor(p, hire(n.court, n.name, r, now).id());
        }
    }

    /** Removes the characters left without a role (governors count) from the nation's court. */
    public static void cleanup(Politics pol, Nation n) {
        Set<UUID> seated = new HashSet<>();
        for (Role role : n.roles.all()) seated.addAll(role.holders);
        for (UUID pid : n.provinces) {
            Province p = pol.provinces.get(pid);
            if (p != null && p.governor != null) seated.add(p.governor);
        }
        n.court.keySet().retainAll(seated);
    }

    /** Removes the characters left without a role from the town's court. */
    public static void cleanup(Town t) {
        Set<UUID> seated = new HashSet<>();
        for (Role role : t.roles.all()) seated.addAll(role.holders);
        t.court.keySet().retainAll(seated);
    }

    /** A new character for {@code court}, named from {@code stock}'s syllables (never the name of another in the court). */
    static Persona hire(Map<UUID, Persona> court, String stock, Random r, long now) {
        Set<String> taken = new HashSet<>();
        for (Persona p : court.values()) taken.add(p.name());
        String name = NameGen.person(r.nextLong(), stock);
        for (int i = 0; i < 8 && taken.contains(name); i++) name = NameGen.person(r.nextLong(), stock);
        UUID id = UUID.nameUUIDFromBytes(("nw-persona:" + r.nextLong() + ":" + now).getBytes(StandardCharsets.UTF_8));
        Persona p = new Persona(id, name, r.nextInt(GuardEntity.SKINS), now);
        court.put(id, p);
        return p;
    }

    // --- succession --------------------------------------------------------------------------------------------------------

    /**
     * The nation's leader (a character) falls: the {@code second} role's character steps up and the gaps are refilled.
     * Returns the seats that changed hands.
     */
    public static List<Change> succeed(Politics pol, Nation n, Random r, long now) {
        if (!courtly(n)) return List.of();
        Map<String, Set<UUID>> before = seats(pol, n);
        UUID old = n.roles.leader();
        if (old != null && !n.court.containsKey(old)) return List.of();
        if (old != null) {
            n.roles.root().holders.remove(old);
            n.court.remove(old);
        }
        promote(n.roles, n.court);
        return finish(pol, n, before, old, r, now);
    }

    /**
     * One seat of the nation's court (its tree, its provinces or its NPC towns' trees) changes hands by chance: a death or a
     * resignation (refilled with someone new), or a promotion of a character from the rank below. Returns the changes.
     */
    public static List<Change> reshuffle(Politics pol, Nation n, Random r, long now) {
        if (!courtly(n)) return List.of();
        record Seat(Object owner, Role role, Province province, UUID holder) {
        }
        List<Seat> pool = new ArrayList<>();
        for (Role role : n.roles.all())
            if (role.kind != Role.Kind.GOVERNOR) for (UUID h : role.holders) if (n.court.containsKey(h)) pool.add(new Seat(n, role, null, h));
        for (UUID pid : n.provinces) {
            Province p = pol.provinces.get(pid);
            if (p != null && p.governor != null && n.court.containsKey(p.governor)) pool.add(new Seat(n, null, p, p.governor));
        }
        for (UUID tid : n.towns) {
            Town t = pol.towns.get(tid);
            if (!courtly(t)) continue;
            for (Role role : t.roles.all()) for (UUID h : role.holders) if (t.court.containsKey(h)) pool.add(new Seat(t, role, null, h));
        }
        if (pool.isEmpty()) return List.of();
        Seat seat = pool.get(r.nextInt(pool.size()));
        if (seat.owner == n && seat.role != null && seat.role.kind == Role.Kind.LEADER) return succeed(pol, n, r, now);

        Map<String, Set<UUID>> before = seats(pol, n);
        UUID oldLeader = n.roles.leader();
        if (seat.province != null) {
            pol.appointGovernor(seat.province, null);
        } else {
            RoleTree tree = Politics.treeOf(seat.owner);
            Map<UUID, Persona> court = seat.owner instanceof Town t ? t.court : n.court;
            seat.role.holders.remove(seat.holder);
            court.remove(seat.holder);
            if (seat.role.kind == Role.Kind.LEADER) promote(tree, court);
            else if (r.nextBoolean()) {
                UUID deputy = deputy(tree, court, seat.role);
                if (deputy != null) {
                    tree.unassign(deputy);
                    seat.role.holders.add(deputy);
                }
            }
        }
        return finish(pol, n, before, oldLeader, r, now);
    }

    /** The {@code second} role's first character takes the empty root of {@code tree}; nothing when there is none. */
    private static void promote(RoleTree tree, Map<UUID, Persona> court) {
        if (!tree.root().holders.isEmpty()) return;
        Role second = second(tree);
        if (second == null) return;
        for (UUID h : second.holders) {
            if (court.containsKey(h)) {
                tree.setLeader(h);
                return;
            }
        }
    }

    /** A character holding a role right below {@code role}, or null. */
    private static UUID deputy(RoleTree tree, Map<UUID, Persona> court, Role role) {
        for (Role child : tree.children(role.id))
            if (child.kind == Role.Kind.NORMAL) for (UUID h : child.holders) if (court.containsKey(h)) return h;
        return null;
    }

    /** The leader's right hand: the default {@code second} role, else the first normal role under the root. */
    public static Role second(RoleTree tree) {
        for (Role r : tree.all()) if (r.kind == Role.Kind.NORMAL && is(r, "second")) return r;
        Role best = null;
        for (Role r : tree.children(RoleTree.ROOT)) if (r.kind == Role.Kind.NORMAL && (best == null || r.id < best.id)) best = r;
        return best;
    }

    /** Whether {@code role} still bears the default name {@code @…​.<rank>}. */
    private static boolean is(Role role, String rank) {
        return role.name.startsWith("@") && role.name.endsWith("." + rank);
    }

    /** Refills, tidies the courts, reports a new leader to the observer and lists the seats with a new holder. */
    private static List<Change> finish(Politics pol, Nation n, Map<String, Set<UUID>> before, UUID oldLeader, Random r, long now) {
        staff(pol, n, r, now);
        for (UUID tid : n.towns) {
            Town t = pol.towns.get(tid);
            if (courtly(t)) staff(pol, t, r, now);
        }
        UUID leader = n.roles.leader();
        if (leader != null && !leader.equals(oldLeader)) pol.observer.leaderChanged(n, oldLeader, leader);
        Map<String, Set<UUID>> after = seats(pol, n);
        List<Change> out = new ArrayList<>();
        Map<String, String[]> labels = labels(pol, n);
        after.forEach((key, holders) -> {
            Set<UUID> was = before.getOrDefault(key, Set.of());
            for (UUID h : holders) {
                if (was.contains(h)) continue;
                Persona p = pol.persona(h);
                String[] l = labels.get(key);
                if (p != null && l != null) out.add(new Change(l[0], l[1], p));
            }
        });
        return out;
    }

    /** Every seat of the nation's court (its tree, provinces and towns' trees) and who holds it. */
    private static Map<String, Set<UUID>> seats(Politics pol, Nation n) {
        Map<String, Set<UUID>> out = new LinkedHashMap<>();
        for (Role role : n.roles.all()) if (role.kind != Role.Kind.GOVERNOR) out.put(n.id + "/" + role.id, new LinkedHashSet<>(role.holders));
        for (UUID pid : n.provinces) {
            Province p = pol.provinces.get(pid);
            if (p != null) out.put(p.id + "/gov", p.governor == null ? new LinkedHashSet<>() : new LinkedHashSet<>(Set.of(p.governor)));
        }
        for (UUID tid : n.towns) {
            Town t = pol.towns.get(tid);
            if (t != null) for (Role role : t.roles.all()) out.put(t.id + "/" + role.id, new LinkedHashSet<>(role.holders));
        }
        return out;
    }

    /** Each seat key of {@link #seats}: {role name, owner name}. */
    private static Map<String, String[]> labels(Politics pol, Nation n) {
        Map<String, String[]> out = new LinkedHashMap<>();
        for (Role role : n.roles.all()) out.put(n.id + "/" + role.id, new String[]{role.name, n.name});
        Role gov = n.roles.governorRole();
        String govName = gov != null ? gov.name : "@" + n.government.key("governor");
        for (UUID pid : n.provinces) {
            Province p = pol.provinces.get(pid);
            if (p != null) out.put(p.id + "/gov", new String[]{govName, p.name});
        }
        for (UUID tid : n.towns) {
            Town t = pol.towns.get(tid);
            if (t != null) for (Role role : t.roles.all()) out.put(t.id + "/" + role.id, new String[]{role.name, t.name});
        }
        return out;
    }

    // --- the server --------------------------------------------------------------------------------------------------------

    /** The NPC nation lost a war: its leader falls, the second steps up. */
    public static void onWarLost(MinecraftServer server, Nation n) {
        fall(server, n);
    }

    /** The NPC nation's capital fell: its leader falls, the second steps up. */
    public static void onCapitalFell(MinecraftServer server, Nation n) {
        fall(server, n);
    }

    private static void fall(MinecraftServer server, Nation n) {
        Politics pol = NW.pol(server);
        if (!courtly(n) || pol.nations.get(n.id) != n) return;
        announce(server, n, succeed(pol, n, random(server), server.overworld().getGameTime()));
        NW.changed(server);
    }

    /** Once per NPC brain interval: staffs every NPC nation and town, and maybe changes a court by chance. */
    public static void tick(MinecraftServer server, long day) {
        Politics pol = NW.pol(server);
        Random r = random(server);
        long now = server.overworld().getGameTime();
        for (Nation n : List.copyOf(pol.nations.values())) staff(pol, n, r, now);
        for (Town t : List.copyOf(pol.towns.values())) staff(pol, t, r, now);
        int days = NWConfig.get(NWConfig.COURT_CHANGE_DAYS);
        if (days > 0) {
            double chance = Math.min(1, NWConfig.get(NWConfig.BRAIN_INTERVAL) / (24000.0 * days));
            for (Nation n : List.copyOf(pol.nations.values()))
                if (courtly(n) && r.nextDouble() < chance) announce(server, n, reshuffle(pol, n, r, now));
        }
        NW.changed(server);
    }

    private static Random random(MinecraftServer server) {
        return new Random(server.overworld().getRandom().nextLong());
    }

    /** "New King of Galheim: Aldric Galvor" to the players near the capital's Core, the nation's citizens and its enemies. */
    private static void announce(MinecraftServer server, Nation n, List<Change> changes) {
        if (changes.isEmpty()) return;
        Set<ServerPlayer> to = new LinkedHashSet<>();
        Politics pol = NW.pol(server);
        Town capital = n.capital == null ? null : pol.towns.get(n.capital);
        if (capital != null) {
            BlockPos core = BlockPos.of(capital.core);
            for (ServerPlayer p : server.getPlayerList().getPlayers())
                if (NW.dim(p.level()).equals(capital.dimension) && p.blockPosition().distSqr(core) <= NEAR * NEAR) to.add(p);
        }
        Set<UUID> people = new LinkedHashSet<>(pol.citizens(n));
        NWSavedData data = NW.data(server);
        for (War w : data.wars.values()) {
            if (!w.involves(n.id)) continue;
            for (UUID other : w.attackers.contains(n.id) ? w.defenders : w.attackers) {
                Nation o = pol.nations.get(other);
                if (o != null) people.addAll(pol.citizens(o));
            }
        }
        for (UUID u : people) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p != null) to.add(p);
        }
        for (Change c : changes) {
            Component role = c.role().startsWith("@") ? Component.translatable(c.role().substring(1)) : Component.literal(c.role());
            Component title = Component.translatable("nationsandwar.notice.court_new", role, c.owner());
            for (ServerPlayer p : to) Notices.send(p, NoticePayload.Kind.POLITICS, title, Component.literal(c.who().name()), n.color, "crown");
        }
    }
}
