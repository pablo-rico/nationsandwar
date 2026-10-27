package org.papiricoh.nationsandwar.model;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RoleTreeTest {
    private final UUID leader = UUID.randomUUID(), alice = UUID.randomUUID(), bob = UUID.randomUUID();

    @Test
    void leaderHoldsEverythingAndRootIsFixed() {
        RoleTree t = RoleTree.of("King", 0xFFFFFF, leader);
        assertTrue(t.permsOf(leader).containsAll(EnumSet.allOf(Perm.class)));
        assertFalse(t.move(RoleTree.ROOT, RoleTree.ROOT).ok());
        assertFalse(t.remove(RoleTree.ROOT).ok());
    }

    @Test
    void movesRefuseCycles() {
        RoleTree t = RoleTree.of("King", 0, leader);
        int a = t.add("Duke", 0, RoleTree.ROOT, EnumSet.noneOf(Perm.class)).intArg();
        int b = t.add("Count", 0, a, EnumSet.noneOf(Perm.class)).intArg();
        int c = t.add("Baron", 0, b, EnumSet.noneOf(Perm.class)).intArg();
        assertFalse(t.move(a, c).ok(), "a parent under its own grandchild");
        assertFalse(t.move(a, a).ok());
        assertTrue(t.move(c, a).ok());
        assertTrue(t.isAbove(RoleTree.ROOT, c));
    }

    @Test
    void deletingARoleLiftsItsChildren() {
        RoleTree t = RoleTree.of("King", 0, leader);
        int a = t.add("Duke", 0, RoleTree.ROOT, EnumSet.noneOf(Perm.class)).intArg();
        int b = t.add("Count", 0, a, EnumSet.noneOf(Perm.class)).intArg();
        t.assign(alice, a);
        assertTrue(t.remove(a).ok());
        assertEquals(RoleTree.ROOT, t.get(b).parent);
        assertNull(t.roleOf(alice));
    }

    @Test
    void onlyRolesBelowCanBeManaged() {
        RoleTree t = RoleTree.of("King", 0, leader);
        int duke = t.add("Duke", 0, RoleTree.ROOT, EnumSet.of(Perm.MANAGE_ROLES)).intArg();
        int count = t.add("Count", 0, duke, EnumSet.noneOf(Perm.class)).intArg();
        int peer = t.add("Bishop", 0, RoleTree.ROOT, EnumSet.noneOf(Perm.class)).intArg();
        t.assign(alice, duke);
        assertTrue(t.canManage(alice, count));
        assertFalse(t.canManage(alice, duke), "not their own role");
        assertFalse(t.canManage(alice, peer), "not a sibling branch");
        assertTrue(t.canManage(leader, peer));
        assertFalse(t.canManage(bob, count));
    }

    @Test
    void namesAreChecked() {
        RoleTree t = RoleTree.of("King", 0, leader);
        assertFalse(t.add("", 0, 0, EnumSet.noneOf(Perm.class)).ok());
        assertFalse(t.add("@nationsandwar.hack", 0, 0, EnumSet.noneOf(Perm.class)).ok());
        assertFalse(t.add("x".repeat(25), 0, 0, EnumSet.noneOf(Perm.class)).ok());
    }

    @Test
    void savesAndLoads() {
        RoleTree t = RoleTree.of("King", 7, leader);
        int a = t.add("Duke", 5, RoleTree.ROOT, EnumSet.of(Perm.CLAIM, Perm.KICK)).intArg();
        t.assign(alice, a);
        RoleTree back = RoleTree.load(t.save());
        assertEquals("Duke", back.get(a).name);
        assertEquals(EnumSet.of(Perm.CLAIM, Perm.KICK), back.get(a).perms);
        assertEquals(a, back.roleOf(alice).id);
        assertTrue(back.isLeader(leader));
        assertTrue(back.add("Next", 0, 0, EnumSet.noneOf(Perm.class)).intArg() > a);
    }
}
