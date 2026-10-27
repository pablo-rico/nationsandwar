package org.papiricoh.nationsandwar.war;

import org.junit.jupiter.api.Test;
import org.papiricoh.nationsandwar.model.GovernmentType;
import org.papiricoh.nationsandwar.model.Nation;
import org.papiricoh.nationsandwar.model.Politics;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WarRulesTest {
    @Test
    void controlBarMovesWithTheEdgeAndDecays() {
        assertEquals(0.03f, SiegeControl.step(0, 5, 2, 0.01f, 0.002f), 1e-6, "edge of 3");
        assertEquals(0.03f, SiegeControl.step(0, 9, 0, 0.01f, 0.002f), 1e-6, "capped at 3");
        assertEquals(-0.01f, SiegeControl.step(0, 1, 2, 0.01f, 0.002f), 1e-6);
        assertEquals(0.498f, SiegeControl.step(0.5f, 0, 0, 0.01f, 0.002f), 1e-6, "empty field decays");
        assertEquals(0f, SiegeControl.step(0.001f, 0, 0, 0.01f, 0.002f), 1e-6);
        assertEquals(1f, SiegeControl.step(0.99f, 3, 0, 0.01f, 0.002f), 1e-6, "clamped");
        assertEquals(100, SiegeControl.secondsAlone(0.01f));
    }

    @Test
    void warsEndByTargetOrDeadline() {
        War w = new War(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        w.phase = War.Phase.ACTIVE;
        w.deadline = 1000;
        assertEquals(War.Outcome.NONE, WarRules.check(w, 60, 10));
        w.scoreDefenders = 60;
        assertEquals(War.Outcome.DEFENDERS, WarRules.check(w, 60, 10));
        w.scoreDefenders = 5;
        w.scoreAttackers = 5;
        assertEquals(War.Outcome.WHITE_PEACE, WarRules.check(w, 60, 1000));
        w.scoreAttackers = 6;
        assertEquals(War.Outcome.ATTACKERS, WarRules.check(w, 60, 1000));
    }

    @Test
    void declarationsAreChecked() {
        Politics p = new Politics();
        p.foundTown(UUID.randomUUID(), "Alderford", "o", 0, 0, 0);
        Nation a = p.foundNpcNation("Valoria", GovernmentType.KINGDOM, p.towns.values().iterator().next(), 0);
        Nation b = p.foundNpcNation("Ostmark", GovernmentType.KINGDOM, p.foundNpcTown("Belholm", "o", 1 << 20, 0, 0, 0), 0);
        var last = new HashMap<String, Long>();
        assertTrue(WarRules.canDeclare(p, List.of(), last, a, b, 0, 100).ok());
        assertEquals("war.self", WarRules.canDeclare(p, List.of(), last, a, a, 0, 100).key());
        War w = new War(UUID.randomUUID(), a.id, b.id);
        assertEquals("war.already", WarRules.canDeclare(p, List.of(w), last, b, a, 0, 100).key());
        last.put(WarRules.pair(a.id, b.id), 50L);
        assertEquals("war.cooldown", WarRules.canDeclare(p, List.of(), last, a, b, 100, 100).key());
        b.overlord = a.id;
        assertEquals("war.vassal", WarRules.canDeclare(p, List.of(), new HashMap<>(), b, a, 0, 100).key());
    }

    @Test
    void treatyDefaultsAndLimits() {
        UUID capital = UUID.randomUUID(), other = UUID.randomUUID();
        War w = new War(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        w.fallen.add(capital);
        w.fallen.add(other);
        Treaty t = Treaty.defaults(w, 400, 0.25, capital, 7);
        assertEquals(java.util.Set.of(other), t.annex, "never the capital");
        assertEquals(100, t.tribute);
        assertEquals(7, t.vassalDays, "the capital fell: vassalage");
        assertTrue(Treaty.allowed(w, t, 400, capital, 14));
        t.tribute = 500;
        assertFalse(Treaty.allowed(w, t, 400, capital, 14));
        t.tribute = 0;
        t.annex.add(capital);
        assertFalse(Treaty.allowed(w, t, 400, capital, 14));
    }

    @Test
    void warSavesAndLoads() {
        War w = new War(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        UUID town = UUID.randomUUID();
        w.phase = War.Phase.ACTIVE;
        w.scoreAttackers = 7;
        w.occupied.computeIfAbsent(town, k -> new java.util.LinkedHashSet<>()).add(42L);
        w.coreDamage.put(town, 3);
        Siege s = w.newSiege(town, "o", 9, 99, true, 5);
        s.control = 0.5f;
        War back = War.load(w.save());
        assertEquals(7, back.scoreAttackers);
        assertTrue(back.isOccupied(town, 42));
        assertEquals(3, back.coreDamage.get(town));
        assertEquals(0.5f, back.siegeAt("o", 9).control);
        assertEquals(2, back.newSiege(town, "o", 10, 1, true, 0).id, "siege ids continue");
    }
}
