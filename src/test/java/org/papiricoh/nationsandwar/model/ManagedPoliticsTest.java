package org.papiricoh.nationsandwar.model;

import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import org.papiricoh.nationsandwar.npc.FactionBrain;
import org.papiricoh.nationsandwar.war.War;
import org.papiricoh.nationsandwar.war.WarRules;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** The API's pure side: nations other mods run, reserved land, and the observer that turns changes into events. */
class ManagedPoliticsTest {
    private static final String OW = "minecraft:overworld";
    private static final String KEY = "testmod:pit";
    private final UUID mayor = UUID.randomUUID(), bob = UUID.randomUUID();

    private static long c(int x, int z) {
        return ChunkPos.asLong(x, z);
    }

    private Nation managed(Politics p) {
        Nation n = p.foundManagedNation(KEY, "The Pit", "testmod.gov.pit", 0xAA0000, 0);
        Town seat = p.foundManagedTown(KEY, "The Seat", OW, c(100, 100), 0, List.of(c(101, 100), c(100, 101)), 0);
        p.attachTown(n, seat);
        return n;
    }

    private Nation playerNation(Politics p, UUID who, String name, int x) {
        assertTrue(p.foundTown(who, name, OW, c(x, 0), 0, 0).ok());
        p.townOf(who).bank = 10_000;
        assertTrue(p.foundNation(who, name + " Realm", GovernmentType.KINGDOM, 0).ok());
        Nation n = p.nationOfPlayer(who);
        n.bank = 10_000;
        return n;
    }

    @Test
    void managedNationsTakeTheirFirstTownAsCapitalAndLookTheirOwn() {
        Politics p = new Politics();
        Nation pit = managed(p);
        Town seat = p.towns.get(pit.capital);
        assertNotNull(seat);
        assertTrue(pit.managed() && seat.managed() && pit.alwaysDefended());
        assertEquals(3, seat.claims.size());
        assertEquals("testmod.gov.pit", pit.titleKey());
        assertNull(pit.leader(), "nobody leads a managed nation here");
        Politics back = Politics.load(p.save());
        Nation again = back.nations.get(pit.id);
        assertEquals(KEY, again.managedBy);
        assertEquals("testmod.gov.pit", again.titles);
        assertEquals(KEY, back.towns.get(seat.id).managedBy);
    }

    @Test
    void nobodyJoinsOrAlliesWithAManagedNation() {
        Politics p = new Politics();
        Nation pit = managed(p);
        Town seat = p.towns.get(pit.capital);
        Nation mine = playerNation(p, mayor, "Alderford", 0);
        Town alder = p.townOf(mayor);
        assertTrue(p.foundTown(bob, "Belholm", OW, c(40, 0), 0, 0).ok());
        assertEquals("managed", p.join(UUID.randomUUID(), seat).key());
        assertEquals("managed", p.nationInvite(mayor, mine, seat).key());
        pit.invites.add(alder.id);
        p.townOf(bob).bank = 10_000;
        assertEquals("managed", p.nationJoin(bob, pit).key(), "even an old invitation does not let a town in");
        assertTrue(p.createAlliance(mayor, mine, "Northern Pact").ok());
        Alliance a = p.alliances.get(mine.alliance);
        assertEquals("managed", p.allianceInvite(mayor, a, pit).key());
        a.invites.add(pit.id);
        assertEquals("managed", p.allianceJoin(null, pit, a).key());
        assertEquals("managed", p.createAlliance(null, pit, "Infernal Pact").key());
    }

    @Test
    void reservedLandCannotBeClaimed() {
        Politics p = new Politics();
        p.claimVeto = (dim, chunk) -> ChunkPos.getX(chunk) >= 50;
        assertEquals("claim_reserved", p.foundTown(mayor, "Alderford", OW, c(60, 0), 0, 0).key());
        assertTrue(p.foundTown(mayor, "Alderford", OW, c(48, 0), 0, 0).ok());
        Town t = p.townOf(mayor);
        t.bank = 10_000;
        assertTrue(p.claim(mayor, t, OW, c(49, 0), false).ok());
        assertEquals("claim_reserved", p.claim(mayor, t, OW, c(50, 0), false).key());
        Town npc = p.foundNpcTown("Gloam", OW, c(52, 10), 0, 1, 0);
        assertTrue(npc.claims.isEmpty(), "an NPC town skips reserved chunks");
        Nation pit = p.foundManagedNation(KEY, "The Pit", null, 0, 0);
        Town seat = p.foundManagedTown(KEY, "The Seat", OW, c(70, 70), 0, List.of(c(71, 70)), 0);
        p.attachTown(pit, seat);
        assertEquals(2, seat.claims.size(), "the provider's own towns ignore the veto");
    }

    @Test
    void theObserverHearsEveryChange() {
        Politics p = new Politics();
        List<String> heard = new ArrayList<>();
        p.observer = new PoliticsObserver() {
            @Override
            public void townFounded(Town town) {
                heard.add("town+" + town.name);
            }

            @Override
            public void residentJoined(Town town, UUID player) {
                heard.add("join");
            }

            @Override
            public void residentLeft(Town town, UUID player) {
                heard.add("left");
            }

            @Override
            public void claimed(Town town, long chunk) {
                heard.add("claim");
            }

            @Override
            public void nationFounded(Nation nation) {
                heard.add("nation+");
            }

            @Override
            public void townJoinedNation(Nation nation, Town town) {
                heard.add("joinNation");
            }

            @Override
            public void townLeftNation(Nation nation, Town town) {
                heard.add("leftNation");
            }

            @Override
            public void leaderChanged(Nation nation, UUID before, UUID after) {
                heard.add("leader");
            }

            @Override
            public void nationRemoved(Nation nation) {
                heard.add("nation-");
            }

            @Override
            public void allianceJoined(Alliance alliance, Nation nation) {
                heard.add("ally+");
            }

            @Override
            public void allianceLeft(Alliance alliance, Nation nation) {
                heard.add("ally-");
            }

            @Override
            public void townRemoved(Town town) {
                heard.add("town-");
            }
        };
        Nation n = playerNation(p, mayor, "Alderford", 0);
        Town t = p.townOf(mayor);
        assertEquals(List.of("town+Alderford", "joinNation", "nation+"), heard);
        heard.clear();
        t.open = true;
        assertTrue(p.join(bob, t).ok());
        assertTrue(p.claim(mayor, t, OW, c(1, 0), false).ok());
        assertTrue(p.setLeader(mayor, n, bob).ok());
        assertTrue(p.leave(bob).ok());
        assertTrue(p.createAlliance(null, n, "Northern Pact").ok());
        assertEquals(List.of("join", "claim", "leader", "left", "ally+"), heard);
        heard.clear();
        p.removeNation(n);
        assertEquals(List.of("leftNation", "ally-", "nation-"), heard);
        heard.clear();
        p.removeTown(t);
        assertEquals(List.of("town-"), heard);
    }

    @Test
    void npcNationsLeaveManagedNationsAlone() {
        Politics p = new Politics();
        Nation pit = managed(p);
        Nation big = p.foundNpcNation("Valoria", GovernmentType.KINGDOM, p.foundNpcTown("Valoria Town", OW, c(0, 0), 0, 0, 0), 0);
        for (int i = 1; i < 4; i++) p.attachTown(big, p.foundNpcTown("Valoria " + i, OW, c(i * 3, 50), 0, 0, 0));
        big.opinion.put(pit.id, -100);
        assertTrue(FactionBrain.decide(p, List.of(), true, x -> true).stream().noneMatch(d -> pit.id.equals(d.other())));
        FactionBrain.drift(p, List.of(), 3);
        assertEquals(-100, big.opinionOf(pit.id), "no opinion of a managed nation drifts");
        assertTrue(pit.opinion.isEmpty(), "a managed nation has no brain");
    }

    @Test
    void aWarsOwnTargetWins() {
        War w = new War(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        w.phase = War.Phase.ACTIVE;
        w.deadline = 1000;
        w.scoreAttackers = 12;
        assertEquals(War.Outcome.NONE, WarRules.check(w, 20, 10));
        w.targetScore = 10;
        assertEquals(War.Outcome.ATTACKERS, WarRules.check(w, 20, 10));
        War back = War.load(w.save());
        assertEquals(10, back.targetScore);
    }
}
