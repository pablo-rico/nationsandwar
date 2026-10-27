package org.papiricoh.nationsandwar.model;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A town's or a nation's hierarchy of roles: a tree under one {@link Role.Kind#LEADER} root (id 0). Each player holds at
 * most one role per tree; members without one are plain residents/citizens. A holder may only edit, assign or remove roles
 * strictly below their own (the leader edits everything), so nobody can grant themselves more than they already have.
 */
public final class RoleTree {
    public static final int ROOT = 0, MAX_ROLES = 48, MAX_NAME = 24;

    private final Map<Integer, Role> roles = new LinkedHashMap<>();
    private int nextId = 1;

    private RoleTree() {
    }

    /** A tree with only its leader role. */
    public static RoleTree of(String leaderName, int leaderColor, UUID leader) {
        RoleTree t = new RoleTree();
        Role root = new Role(ROOT, Role.Kind.LEADER, leaderName, leaderColor, -1, EnumSet.allOf(Perm.class));
        if (leader != null) root.holders.add(leader);
        t.roles.put(ROOT, root);
        return t;
    }

    public Role root() {
        return roles.get(ROOT);
    }

    public Role get(int id) {
        return roles.get(id);
    }

    public Collection<Role> all() {
        return roles.values();
    }

    public Role governorRole() {
        for (Role r : roles.values()) if (r.kind == Role.Kind.GOVERNOR) return r;
        return null;
    }

    /** The role {@code player} holds, or null. */
    public Role roleOf(UUID player) {
        for (Role r : roles.values()) if (r.kind != Role.Kind.GOVERNOR && r.holders.contains(player)) return r;
        return null;
    }

    public boolean isLeader(UUID player) {
        return root().holders.contains(player);
    }

    public UUID leader() {
        return root().holders.isEmpty() ? null : root().holders.iterator().next();
    }

    public void setLeader(UUID player) {
        unassign(player);
        root().holders.clear();
        if (player != null) root().holders.add(player);
    }

    /** Whether {@code ancestor} lies strictly above {@code id}. */
    public boolean isAbove(int ancestor, int id) {
        Role r = roles.get(id);
        int guard = 0;
        while (r != null && r.parent >= 0 && guard++ < MAX_ROLES + 2) {
            if (r.parent == ancestor) return true;
            r = roles.get(r.parent);
        }
        return false;
    }

    /** Whether {@code actor} may edit role {@code id}: the leader always, otherwise holders of a role above it with MANAGE_ROLES. */
    public boolean canManage(UUID actor, int id) {
        if (isLeader(actor)) return true;
        Role mine = roleOf(actor);
        return mine != null && mine.has(Perm.MANAGE_ROLES) && isAbove(mine.id, id);
    }

    public List<Role> children(int id) {
        List<Role> out = new ArrayList<>();
        for (Role r : roles.values()) if (r.parent == id) out.add(r);
        return out;
    }

    // --- edits (callers check canManage) -----------------------------------------------------------------------------------

    public Result add(String name, int color, int parent, Set<Perm> perms) {
        if (roles.size() >= MAX_ROLES) return Result.fail("role.too_many");
        if (!roles.containsKey(parent)) return Result.fail("role.unknown");
        Result n = checkName(name);
        if (!n.ok()) return n;
        Role r = new Role(nextId++, Role.Kind.NORMAL, name.trim(), color, parent, perms);
        roles.put(r.id, r);
        return Result.ok("role.created", r.id);
    }

    /** Adds a built-in role (the defaults and the Governor role); no name checks. */
    public Role addBuiltIn(Role.Kind kind, String name, int color, int parent, Set<Perm> perms) {
        Role r = new Role(nextId++, kind, name, color, parent, perms);
        roles.put(r.id, r);
        return r;
    }

    public Result rename(int id, String name) {
        Role r = roles.get(id);
        if (r == null) return Result.fail("role.unknown");
        Result n = checkName(name);
        if (!n.ok()) return n;
        r.name = name.trim();
        return Result.ok("role.renamed");
    }

    public Result recolor(int id, int color) {
        Role r = roles.get(id);
        if (r == null) return Result.fail("role.unknown");
        r.color = color & 0xFFFFFF;
        return Result.ok("role.recolored");
    }

    /** Moves a role (and its subtree) under {@code parent}; refuses the root and any move that would make a cycle. */
    public Result move(int id, int parent) {
        Role r = roles.get(id);
        if (r == null || !roles.containsKey(parent)) return Result.fail("role.unknown");
        if (r.kind == Role.Kind.LEADER) return Result.fail("role.root_fixed");
        if (id == parent || isAbove(id, parent)) return Result.fail("role.cycle");
        r.parent = parent;
        return Result.ok("role.moved");
    }

    public Result setPerms(int id, Set<Perm> perms) {
        Role r = roles.get(id);
        if (r == null) return Result.fail("role.unknown");
        if (r.kind == Role.Kind.LEADER) return Result.fail("role.root_fixed");
        r.perms.clear();
        r.perms.addAll(perms);
        return Result.ok("role.perms_set");
    }

    /** Deletes a role: its children move up to its parent and its holders become plain members. */
    public Result remove(int id) {
        Role r = roles.get(id);
        if (r == null) return Result.fail("role.unknown");
        if (r.kind != Role.Kind.NORMAL) return Result.fail("role.builtin");
        for (Role c : children(id)) c.parent = r.parent;
        roles.remove(id);
        return Result.ok("role.deleted");
    }

    public Result assign(UUID player, int id) {
        Role r = roles.get(id);
        if (r == null) return Result.fail("role.unknown");
        if (r.kind != Role.Kind.NORMAL) return Result.fail("role.builtin");
        unassign(player);
        r.holders.add(player);
        return Result.ok("role.assigned");
    }

    public void unassign(UUID player) {
        for (Role r : roles.values()) if (r.kind == Role.Kind.NORMAL) r.holders.remove(player);
    }

    /** The perms {@code player} holds through this tree (leader: all). Governor holders are not counted here. */
    public EnumSet<Perm> permsOf(UUID player) {
        if (isLeader(player)) return EnumSet.allOf(Perm.class);
        Role r = roleOf(player);
        return r == null ? EnumSet.noneOf(Perm.class) : EnumSet.copyOf(r.perms.isEmpty() ? EnumSet.noneOf(Perm.class) : r.perms);
    }

    public static Result checkName(String name) {
        String s = name == null ? "" : name.trim();
        if (s.isEmpty() || s.length() > MAX_NAME) return Result.fail("role.bad_name");
        if (s.startsWith("@")) return Result.fail("role.bad_name");
        return Result.ok("ok");
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("next", nextId);
        ListTag l = new ListTag();
        for (Role r : roles.values()) l.add(r.save());
        t.put("roles", l);
        return t;
    }

    public static RoleTree load(CompoundTag t) {
        RoleTree tree = new RoleTree();
        tree.nextId = Math.max(1, t.getInt("next"));
        for (Tag e : t.getList("roles", Tag.TAG_COMPOUND)) {
            Role r = Role.load((CompoundTag) e);
            tree.roles.put(r.id, r);
            tree.nextId = Math.max(tree.nextId, r.id + 1);
        }
        if (!tree.roles.containsKey(ROOT)) tree.roles.put(ROOT, new Role(ROOT, Role.Kind.LEADER, "@nationsandwar.role.leader", 0xFFD700, -1, EnumSet.allOf(Perm.class)));
        return tree;
    }
}
