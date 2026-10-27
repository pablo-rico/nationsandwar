package org.papiricoh.nationsandwar.npc;

import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import org.papiricoh.nationsandwar.model.GovernmentType;
import org.papiricoh.nationsandwar.model.Nation;
import org.papiricoh.nationsandwar.model.Persona;
import org.papiricoh.nationsandwar.model.Politics;
import org.papiricoh.nationsandwar.model.Province;
import org.papiricoh.nationsandwar.model.Role;
import org.papiricoh.nationsandwar.model.Town;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NpcCourtTest {
    private Nation npc(Politics p, String name, int towns) {
        Nation n = p.foundNpcNation(name, GovernmentType.KINGDOM, p.foundNpcTown(name + " Town", "o", ChunkPos.asLong(0, 0), 0, 0, 0), 0);
        for (int i = 1; i < towns; i++) p.attachTown(n, p.foundNpcTown(name + " " + i, "o", ChunkPos.asLong(i * 5, 0), 0, 0, 0));
        return n;
    }

    private static void staffAll(Politics p, Nation n, Random r) {
        NpcCourt.staff(p, n, r, 1);
        for (UUID t : n.towns) NpcCourt.staff(p, p.towns.get(t), r, 1);
    }

    @Test
    void staffFillsEveryEmptyRoleAndNeverAPlayersSeat() {
        Politics p = new Politics();
        Nation n = npc(p, "Galheim", 2);
        UUID player = UUID.randomUUID();
        Role treasurer = n.roles.all().stream().filter(r -> r.name.endsWith(".treasurer")).findFirst().orElseThrow();
        treasurer.holders.add(player);
        staffAll(p, n, new Random(1));

        for (Role r : n.roles.all()) {
            if (r.kind == Role.Kind.GOVERNOR) continue;
            assertFalse(r.holders.isEmpty(), "nation role " + r.name);
        }
        assertEquals(List.of(player), List.copyOf(treasurer.holders), "the player's seat is untouched");
        assertFalse(n.court.containsKey(player));
        assertNotNull(p.persona(n.leader()));
        for (UUID t : n.towns) {
            Town town = p.towns.get(t);
            for (Role r : town.roles.all()) assertFalse(r.holders.isEmpty(), "town role " + r.name);
            assertEquals(town.court.get(town.mayor()), p.persona(town.mayor()));
        }
        // two towns: one province with a character for governor
        assertEquals(1, n.provinces.size());
        Province prov = p.provinces.get(n.provinces.iterator().next());
        assertEquals(2, prov.towns.size());
        assertNotNull(p.persona(prov.governor));
        assertTrue(n.roles.governorRole().holders.contains(prov.governor));

        // staffing again changes nothing
        String before = n.save().toString();
        NpcCourt.staff(p, n, new Random(2), 2);
        assertEquals(before, n.save().toString());
    }

    @Test
    void successionPromotesTheSecond() {
        Politics p = new Politics();
        Nation n = npc(p, "Galheim", 1);
        staffAll(p, n, new Random(3));
        UUID king = n.leader();
        Role second = NpcCourt.second(n.roles);
        UUID chancellor = second.holders.iterator().next();

        List<NpcCourt.Change> changes = NpcCourt.succeed(p, n, new Random(4), 10);
        assertEquals(chancellor, n.leader());
        assertFalse(n.court.containsKey(king), "the old leader leaves the court");
        assertNull(p.persona(king));
        assertEquals(1, second.holders.size());
        assertNotEquals(chancellor, second.holders.iterator().next(), "a new second");
        assertTrue(changes.stream().anyMatch(c -> c.who().id().equals(chancellor) && c.role().endsWith(".leader")));
        assertTrue(changes.stream().anyMatch(c -> c.role().endsWith(".second")));
    }

    @Test
    void reshuffleKeepsEverySeatFilled() {
        Politics p = new Politics();
        Nation n = npc(p, "Galheim", 3);
        Random r = new Random(5);
        staffAll(p, n, r);
        for (int i = 0; i < 40; i++) {
            NpcCourt.reshuffle(p, n, r, i);
            for (Role role : n.roles.all()) assertFalse(role.holders.isEmpty(), "nation role " + role.name);
            for (UUID t : n.towns) for (Role role : p.towns.get(t).roles.all()) assertFalse(role.holders.isEmpty());
            for (UUID id : n.court.keySet()) assertNotNull(p.persona(id));
        }
    }

    @Test
    void cleanupRemovesRolelessCharacters() {
        Politics p = new Politics();
        Nation n = npc(p, "Galheim", 1);
        staffAll(p, n, new Random(6));
        Persona idle = NpcCourt.hire(n.court, n.name, new Random(7), 0);
        assertNotNull(p.persona(idle.id()));
        NpcCourt.cleanup(p, n);
        assertNull(p.persona(idle.id()));
        assertNotNull(p.persona(n.leader()), "seated characters stay");
    }

    @Test
    void managedAndPlayerNationsAreUntouched() {
        Politics p = new Politics();
        Nation managed = p.foundManagedNation("othermod:x", "Pit", null, 0x112233, 0);
        Town town = p.foundManagedTown("othermod:x", "Pit Town", "o", 0, 0, List.of(0L), 0);
        p.attachTown(managed, town);
        managed.npc = true;
        town.npc = true;
        NpcCourt.staff(p, managed, new Random(8), 0);
        NpcCourt.staff(p, town, new Random(8), 0);
        assertNull(managed.leader());
        assertTrue(managed.court.isEmpty());
        assertTrue(town.court.isEmpty());
        assertTrue(NpcCourt.succeed(p, managed, new Random(8), 0).isEmpty());

        Politics q = new Politics();
        Nation npc = npc(q, "Galheim", 1);
        npc.npc = false;
        NpcCourt.staff(q, npc, new Random(9), 0);
        assertTrue(npc.court.isEmpty());
    }

    @Test
    void courtIsSaved() {
        Politics p = new Politics();
        Nation n = npc(p, "Galheim", 2);
        staffAll(p, n, new Random(10));
        Politics back = Politics.load(p.save());
        Nation m = back.nations.get(n.id);
        assertEquals(n.court, m.court);
        assertEquals(n.court.get(n.leader()).name(), back.persona(m.leader()).name());
        Town cap = back.towns.get(m.capital);
        assertNotNull(back.persona(cap.mayor()));
    }

    @Test
    void personNamesTakeTheNationsSyllables() {
        String a = NameGen.person(1, "Galheim");
        assertEquals(a, NameGen.person(1, "Galheim"));
        assertTrue(a.contains(" "));
        boolean stem = false;
        for (long s = 0; s < 20; s++) stem |= NameGen.person(s, "Galheim").split(" ")[1].startsWith("Gal");
        assertTrue(stem);
    }
}
