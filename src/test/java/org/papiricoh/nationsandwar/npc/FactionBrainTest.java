package org.papiricoh.nationsandwar.npc;

import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import org.papiricoh.nationsandwar.model.GovernmentType;
import org.papiricoh.nationsandwar.model.Nation;
import org.papiricoh.nationsandwar.model.Politics;
import org.papiricoh.nationsandwar.war.War;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FactionBrainTest {
    private Nation npc(Politics p, String name, int x, int towns) {
        Nation n = p.foundNpcNation(name, GovernmentType.KINGDOM, p.foundNpcTown(name + " Town", "o", ChunkPos.asLong(x, 0), 0, 0, 0), 0);
        for (int i = 1; i < towns; i++) p.attachTown(n, p.foundNpcTown(name + " " + i, "o", ChunkPos.asLong(x + i * 3, 50), 0, 0, 0));
        return n;
    }

    @Test
    void hatredAndStrengthMakeWar() {
        Politics p = new Politics();
        Nation big = npc(p, "Valoria", 0, 3), small = npc(p, "Ostmark", 100, 1);
        big.opinion.put(small.id, -80);
        var d = FactionBrain.decide(p, List.of(), true, n -> true);
        assertTrue(d.contains(new FactionBrain.Decision(FactionBrain.Kind.DECLARE_WAR, big.id, small.id)));
        assertTrue(FactionBrain.decide(p, List.of(), false, n -> true).stream().noneMatch(x -> x.kind() == FactionBrain.Kind.DECLARE_WAR),
                "wars off in the config");
        small.opinion.put(big.id, -80);
        assertTrue(FactionBrain.decide(p, List.of(), true, n -> true).stream().noneMatch(x -> x.nation().equals(small.id)
                && x.kind() == FactionBrain.Kind.DECLARE_WAR), "the weak do not attack");
    }

    @Test
    void friendsAlly() {
        Politics p = new Politics();
        Nation a = npc(p, "Valoria", 0, 1), b = npc(p, "Ostmark", 100, 1);
        a.opinion.put(b.id, 80);
        b.opinion.put(a.id, 70);
        assertTrue(FactionBrain.decide(p, List.of(), true, n -> true).contains(new FactionBrain.Decision(FactionBrain.Kind.ALLY, a.id, b.id)));
    }

    @Test
    void losersAskForPeace() {
        Politics p = new Politics();
        Nation a = npc(p, "Valoria", 0, 1), b = npc(p, "Ostmark", 100, 1);
        War w = new War(UUID.randomUUID(), a.id, b.id);
        w.phase = War.Phase.ACTIVE;
        w.scoreDefenders = 20;
        w.scoreAttackers = 2;
        assertTrue(FactionBrain.decide(p, List.of(w), true, n -> true).contains(new FactionBrain.Decision(FactionBrain.Kind.OFFER_PEACE, a.id, b.id)));
        b.opinion.put(a.id, -90);
        assertFalse(FactionBrain.acceptsPeace(b, a.id, w), "winning and hateful");
        assertTrue(FactionBrain.acceptsPeace(a, b.id, w), "losing");
    }

    @Test
    void neighboursDriftApart() {
        Politics p = new Politics();
        Nation a = npc(p, "Valoria", 0, 1), b = npc(p, "Ostmark", 5, 1);
        assertTrue(FactionBrain.borders(p, a, b));
        int total = 0;
        for (long day = 0; day < 30; day++) FactionBrain.drift(p, List.of(), day);
        total = a.opinionOf(b.id);
        assertTrue(total < 0, "neighbours sour: " + total);
    }

    @Test
    void namesAreStable() {
        assertEquals(NameGen.town(42), NameGen.town(42));
        assertNotEquals(NameGen.nation(1), NameGen.nation(2));
        assertTrue(Politics.NAME.matcher(NameGen.town(7)).matches());
    }
}
