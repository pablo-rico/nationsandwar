package org.papiricoh.nationsandwar.model;

import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PoliticsTest {
    private static final String OW = "minecraft:overworld";
    private final UUID mayor = UUID.randomUUID(), bob = UUID.randomUUID(), eve = UUID.randomUUID();

    private static long c(int x, int z) {
        return ChunkPos.asLong(x, z);
    }

    private Town found(Politics p, UUID who, String name, int x, int z) {
        assertTrue(p.foundTown(who, name, OW, c(x, z), 0, 0).ok());
        Town t = p.townOf(who);
        t.bank = 10_000;
        return t;
    }

    @Test
    void claimsMustTouchAndCostMore() {
        Politics p = new Politics();
        Town t = found(p, mayor, "Alderford", 0, 0);
        assertEquals("not_adjacent", p.claim(mayor, t, OW, c(2, 0), false).key());
        Result r = p.claim(mayor, t, OW, c(1, 0), false);
        assertTrue(r.ok());
        assertEquals(Rules.DEFAULTS.claimCost(), r.intArg());
        assertEquals(Rules.DEFAULTS.claimCost() + Rules.DEFAULTS.claimGrowth(), p.claim(mayor, t, OW, c(2, 0), false).intArg());
        assertTrue(p.claim(mayor, t, OW, c(10, 10), true).ok(), "outposts may stand apart");
        assertEquals(t, p.townAt(OW, c(10, 10)));
        assertEquals("home_chunk", p.unclaim(mayor, t, c(0, 0)).key());
    }

    @Test
    void townsKeepTheirDistance() {
        Politics p = new Politics();
        found(p, mayor, "Alderford", 0, 0);
        assertEquals("too_close", p.foundTown(bob, "Belholm", OW, c(2, 0), 0, 0).key());
        assertTrue(p.foundTown(bob, "Belholm", OW, c(3, 0), 0, 0).ok());
    }

    @Test
    void claimLimitFollowsResidents() {
        Politics p = new Politics();
        Town t = found(p, mayor, "Alderford", 0, 0);
        int max = p.maxClaims(t);
        for (int x = 1; x < max; x++) assertTrue(p.claim(mayor, t, OW, c(x, 0), false).ok(), "claim " + x);
        assertEquals("claim_limit", p.claim(mayor, t, OW, c(max, 0), false).key());
        t.invites.add(bob);
        p.join(bob, t);
        assertTrue(p.maxClaims(t) > max);
    }

    @Test
    void chunkGroupsAndPerms() {
        Politics p = new Politics();
        Town a = found(p, mayor, "Alderford", 0, 0);
        Town b = found(p, bob, "Belholm", 20, 0);
        assertEquals(ChunkPerms.Group.RESIDENT, p.group(mayor, a));
        assertEquals(ChunkPerms.Group.OUTSIDER, p.group(bob, a));
        assertTrue(p.foundNation(mayor, "Valoria", GovernmentType.KINGDOM, 0).ok());
        Nation n = p.nationOf(a);
        assertTrue(p.nationInvite(mayor, n, b).ok());
        assertTrue(p.nationJoin(bob, n).ok());
        assertEquals(ChunkPerms.Group.NATION, p.group(bob, a));
        assertFalse(a.perms(c(0, 0)).allows(ChunkPerms.Group.NATION, ChunkPerms.Action.BUILD));
        assertTrue(p.setChunkPerm(mayor, a, ChunkPerms.Group.NATION, ChunkPerms.Action.BUILD, true, c(0, 0)).ok());
        assertTrue(a.perms(c(0, 0)).allows(ChunkPerms.Group.NATION, ChunkPerms.Action.BUILD));
        assertFalse(a.perms(c(1, 0)).allows(ChunkPerms.Group.NATION, ChunkPerms.Action.BUILD), "only that chunk");
    }

    @Test
    void nationRolesActInEveryTownAndGovernorsInTheirProvince() {
        Politics p = new Politics();
        Town a = found(p, mayor, "Alderford", 0, 0);
        Town b = found(p, bob, "Belholm", 20, 0);
        p.foundNation(mayor, "Valoria", GovernmentType.REPUBLIC, 0);
        Nation n = p.nationOf(a);
        n.bank = 1000;
        p.nationInvite(mayor, n, b);
        p.nationJoin(bob, n);
        a.invites.add(eve);
        p.join(eve, a);
        assertFalse(p.can(eve, b, Perm.CLAIM));
        assertTrue(p.createProvince(mayor, n, "North March", b).ok());
        Province pr = p.provinceOf(b);
        assertTrue(p.setGovernor(mayor, pr, eve).ok());
        assertTrue(n.roles.governorRole().holders.contains(eve), "governors show in the nation tree");
        assertTrue(p.can(eve, b, Perm.CLAIM), "governor perms in the province");
        assertFalse(p.can(eve, a, Perm.CLAIM), "not outside it");
        int marshal = n.roles.all().stream().filter(r -> r.name.endsWith(".marshal")).findFirst().orElseThrow().id;
        assertTrue(p.roleAssign(mayor, n, bob, marshal).ok());
        assertTrue(p.canNation(bob, n, Perm.DECLARE_WAR));
    }

    @Test
    void cannotGrantMoreThanYouHold() {
        Politics p = new Politics();
        Town a = found(p, mayor, "Alderford", 0, 0);
        a.invites.add(bob);
        p.join(bob, a);
        int assistant = a.roles.all().stream().filter(r -> r.name.endsWith(".assistant")).findFirst().orElseThrow().id;
        int builder = a.roles.all().stream().filter(r -> r.name.endsWith(".builder")).findFirst().orElseThrow().id;
        p.roleAssign(mayor, a, bob, assistant);
        assertEquals("cannot_grant", p.rolePerms(bob, a, builder, EnumSet.of(Perm.BANK_WITHDRAW)).key());
        assertTrue(p.rolePerms(bob, a, builder, EnumSet.of(Perm.CLAIM, Perm.INVITE)).ok());
        assertEquals("no_perm", p.rolePerms(bob, a, assistant, EnumSet.of(Perm.CLAIM)).key(), "not their own role");
    }

    @Test
    void governmentRenamesDefaultRolesOnly() {
        Politics p = new Politics();
        Town a = found(p, mayor, "Alderford", 0, 0);
        p.foundNation(mayor, "Valoria", GovernmentType.KINGDOM, 0);
        Nation n = p.nationOf(a);
        int custom = n.roles.add("Grand Admiral", 0, RoleTree.ROOT, EnumSet.noneOf(Perm.class)).intArg();
        p.setGovernment(mayor, n, GovernmentType.EMPIRE);
        assertEquals("@nationsandwar.gov.empire.leader", n.roles.root().name);
        assertEquals("Grand Admiral", n.roles.get(custom).name);
    }

    @Test
    void capitalLeavingDisbandsOrMoves() {
        Politics p = new Politics();
        Town a = found(p, mayor, "Alderford", 0, 0);
        p.foundNation(mayor, "Valoria", GovernmentType.KINGDOM, 0);
        Nation n = p.nationOf(a);
        assertEquals("capital_cannot_leave", p.nationLeave(mayor).key());
        p.removeTown(a);
        assertFalse(p.nations.containsKey(n.id), "a nation without towns is gone");
    }

    @Test
    void alliancesMakeAllies() {
        Politics p = new Politics();
        Town a = found(p, mayor, "Alderford", 0, 0);
        Town b = found(p, bob, "Belholm", 20, 0);
        p.foundNation(mayor, "Valoria", GovernmentType.KINGDOM, 0);
        p.foundNation(bob, "Ostmark", GovernmentType.TRIBE, 0);
        Nation x = p.nationOf(a), y = p.nationOf(b);
        x.bank = y.bank = 1000;
        assertTrue(p.createAlliance(mayor, x, "Iron Pact").ok());
        Alliance al = p.alliances.get(x.alliance);
        assertTrue(p.allianceInvite(mayor, al, y).ok());
        assertTrue(p.allianceJoin(bob, y, al).ok());
        assertTrue(p.allied(x.id, y.id));
        assertEquals(ChunkPerms.Group.ALLY, p.group(bob, a));
    }

    @Test
    void savesAndLoadsEverything() {
        Politics p = new Politics();
        Town a = found(p, mayor, "Alderford", 0, 0);
        p.claim(mayor, a, OW, c(1, 0), false);
        p.setFlag(mayor, a, Flag.PVP, true, c(1, 0));
        p.foundNation(mayor, "Valoria", GovernmentType.THEOCRACY, 0);
        Politics back = Politics.load(p.save());
        Town a2 = back.townOf(mayor);
        assertEquals("Alderford", a2.name);
        assertEquals(a2, back.townAt(OW, c(1, 0)));
        assertTrue(a2.flag(Flag.PVP, c(1, 0)));
        assertFalse(a2.flag(Flag.PVP, c(0, 0)));
        assertEquals(GovernmentType.THEOCRACY, back.nationOf(a2).government);
    }

    @Test
    void npcTownsClaimARadius() {
        Politics p = new Politics();
        Town t = p.foundNpcTown("Calmere", OW, c(0, 0), 0, 2, 0);
        assertEquals(25, t.claims.size());
        assertTrue(t.residents.isEmpty());
        Nation n = p.foundNpcNation("Eldoria", GovernmentType.KINGDOM, t, 0);
        assertTrue(n.npc);
        assertNull(n.leader());
    }

    @Test
    void levels() {
        assertEquals(TownLevel.HAMLET, TownLevel.of(1));
        assertEquals(TownLevel.VILLAGE, TownLevel.of(3));
        assertEquals(TownLevel.METROPOLIS, TownLevel.of(40));
        assertEquals(16, TownLevel.maxChunks(1, 1.0));
    }
}
