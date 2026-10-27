package org.papiricoh.nationsandwar.war;

import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import org.papiricoh.nationsandwar.model.GovernmentType;
import org.papiricoh.nationsandwar.model.Nation;
import org.papiricoh.nationsandwar.model.Politics;
import org.papiricoh.nationsandwar.model.Town;

import java.util.LinkedHashSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WarPlannerTest {
    private static long c(int x, int z) {
        return ChunkPos.asLong(x, z);
    }

    private static void occupy(War w, Town t, long chunk) {
        w.occupied.computeIfAbsent(t.id, k -> new LinkedHashSet<>()).add(chunk);
    }

    @Test
    void siegesWalkTowardTheHomeChunk() {
        Politics p = new Politics();
        Town t = p.foundNpcTown("Belholm", "o", c(0, 0), 0, 2, 0);
        War w = new War(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        var dist = WarPlanner.distances(t);
        long first = WarPlanner.nextChunk(w, t);
        assertEquals(2, dist.get(first), "starts at the edge closest to the centre, not a corner");
        occupy(w, t, first);
        long second = WarPlanner.nextChunk(w, t);
        assertEquals(1, dist.get(second), "then one step in");
        occupy(w, t, second);
        assertEquals(t.homeChunk, WarPlanner.nextChunk(w, t), "then the home chunk");
        w.newSiege(t.id, "o", t.homeChunk, 0, true, 0);
        assertNotEquals(t.homeChunk, WarPlanner.nextChunk(w, t), "a besieged chunk is not chosen twice");
    }

    @Test
    void liberationFreesTheHomeChunkFirst() {
        Politics p = new Politics();
        Town t = p.foundNpcTown("Belholm", "o", c(0, 0), 0, 2, 0);
        War w = new War(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        assertNull(WarPlanner.liberationTarget(w, t));
        occupy(w, t, c(2, 2));
        occupy(w, t, c(2, 0));
        assertEquals(c(2, 0), WarPlanner.liberationTarget(w, t), "the occupied chunk nearest the centre");
        occupy(w, t, t.homeChunk);
        assertEquals(t.homeChunk, WarPlanner.liberationTarget(w, t), "the home chunk before anything else");
    }

    @Test
    void capitalsAreTakenLastAndUnreachableTownsSkipped() {
        Politics p = new Politics();
        Nation me = p.foundNpcNation("Valoria", GovernmentType.KINGDOM, p.foundNpcTown("Corwick", "o", c(20, 0), 0, 1, 0), 0);
        Town capital = p.foundNpcTown("Belholm", "o", c(0, 0), 0, 1, 0);
        Town other = p.foundNpcTown("Talmere", "o", c(40, 0), 0, 1, 0);
        Nation enemy = p.foundNpcNation("Ostmark", GovernmentType.KINGDOM, capital, 0);
        p.attachTown(enemy, other);
        War w = new War(UUID.randomUUID(), me.id, enemy.id);
        assertEquals(other, WarPlanner.targetTown(p, w, me, t -> true), "the capital waits");
        assertEquals(capital, WarPlanner.targetTown(p, w, me, t -> t != other), "unless the other town cannot be reached");
        w.fallen.add(other.id);
        assertEquals(capital, WarPlanner.targetTown(p, w, me, t -> true), "fallen towns are done");
    }

    @Test
    void frontsPartiesAndRetreatFollowStrength() {
        assertEquals(1, WarPlanner.fronts(10, 10, 3));
        assertEquals(2, WarPlanner.fronts(15, 10, 3));
        assertEquals(3, WarPlanner.fronts(40, 10, 3), "capped");
        assertEquals(1, WarPlanner.fronts(2, 10, 3));
        assertEquals(4, WarPlanner.party(4, 10, 10));
        assertEquals(8, WarPlanner.party(4, 50, 10), "at most double");
        assertEquals(3, WarPlanner.party(4, 1, 10), "at least three quarters");
        assertEquals(8, WarPlanner.reserve(2));
        assertTrue(WarPlanner.shouldRetreat(6, 2));
        assertFalse(WarPlanner.shouldRetreat(5, 2));
    }
}
