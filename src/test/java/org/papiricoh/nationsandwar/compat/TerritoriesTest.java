package org.papiricoh.nationsandwar.compat;

import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TerritoriesTest {
    private final UUID a = UUID.randomUUID(), b = UUID.randomUUID(), nation = UUID.randomUUID();

    private static void rect(Map<Long, UUID> m, UUID town, int x0, int z0, int x1, int z1) {
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) m.put(ChunkPos.asLong(x, z), town);
    }

    @Test
    void townsOfOneGroupShareOneLabelAlongTheLongSide() {
        Map<Long, UUID> m = new HashMap<>();
        rect(m, a, 0, 0, 5, 2);
        rect(m, b, 6, 0, 11, 2);
        List<Territories.Label> ls = Territories.labels(m, t -> nation);
        assertEquals(1, ls.size(), "two touching towns of one nation make one region");
        Territories.Label l = ls.getFirst();
        assertEquals(nation, l.group());
        assertEquals(6.0, l.x(), 1e-9);
        assertEquals(1.5, l.z(), 1e-9);
        assertEquals(0.0, l.angle(), 1e-9);
        assertEquals(12.0, l.length(), 1e-9);
        assertEquals(3.0, l.thickness(), 1e-9);
    }

    @Test
    void splitTerritoryLabelsEachBigPieceAndTheLargestAlways() {
        Map<Long, UUID> m = new HashMap<>();
        rect(m, a, 0, 0, 4, 4);
        rect(m, b, 20, 0, 22, 1);
        rect(m, b, 40, 0, 40, 0);
        List<Territories.Label> ls = Territories.labels(m, t -> nation);
        assertEquals(2, ls.size(), "the 1-chunk outpost gets no name");
        assertTrue(ls.getFirst().largest());
        assertEquals(25, ls.getFirst().chunks());
        assertFalse(ls.get(1).largest());
        assertEquals(1, Territories.labels(m, t -> t).stream().filter(l -> l.group().equals(b)).count(), "by town: b's largest piece only");
    }

    @Test
    void diagonalStripTiltsButNoMoreThanTheLimit() {
        Map<Long, UUID> m = new HashMap<>();
        for (int i = 0; i < 12; i++) {
            m.put(ChunkPos.asLong(i, i), a);
            m.put(ChunkPos.asLong(i + 1, i), a);
        }
        Territories.Label l = Territories.labels(m, t -> t).getFirst();
        assertTrue(l.angle() > 0.5 && l.angle() <= Territories.MAX_TILT + 1e-9, "down-right strip tilts clockwise, clamped: " + l.angle());
        Map<Long, UUID> v = new HashMap<>();
        rect(v, a, 0, 0, 1, 15);
        assertEquals(Territories.MAX_TILT, Math.abs(Territories.labels(v, t -> t).getFirst().angle()), 1e-9, "a north-south strip still reads sideways");
    }

    @Test
    void labelOfAnLShapeSitsOnItsLand() {
        Map<Long, UUID> m = new HashMap<>();
        rect(m, a, 0, 0, 9, 1);
        rect(m, a, 0, 2, 1, 9);
        Territories.Label l = Territories.labels(m, t -> t).getFirst();
        assertTrue(m.containsKey(ChunkPos.asLong((int) Math.floor(l.x()), (int) Math.floor(l.z()))));
    }
}
