package org.papiricoh.nationsandwar.api;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * Lets another mod's set pieces work inside towns: a boss arena opened over claimed land must spawn its minions, let
 * outsiders fight and build, burn and blow up what the fight needs, whatever the town's perms and flags say.
 *
 * <ul>
 *   <li>{@link #openZone}: a box in one dimension where the chosen protections are off, until {@link Zone#close()}. Zones
 *   live in memory only: the owner opens them again after a restart. Changes inside a zone are never logged for a war's
 *   restoration (the owner restores its own ground).</li>
 *   <li>{@link #exemptEntities}: entities (bosses, their minions) whose spawning, griefing and explosions are never
 *   blocked anywhere.</li>
 * </ul>
 * A Town Core stays protected even inside a zone.
 */
public final class ProtectionBypass {
    public enum Bypass {BUILD, DESTROY, SWITCH, CONTAINER, ITEM_USE, ATTACK, PVP, SPAWNS, MOB_GRIEFING, EXPLOSIONS, FIRE, PISTONS}

    public static final Set<Bypass> ALL = EnumSet.allOf(Bypass.class);

    /** An open zone; {@link #close()} puts protection back. */
    public static final class Zone {
        public final String key, dimension;
        public final AABB box;
        public final Set<Bypass> bypass;

        private Zone(String key, String dimension, AABB box, Set<Bypass> bypass) {
            this.key = key;
            this.dimension = dimension;
            this.box = box;
            this.bypass = bypass.isEmpty() ? EnumSet.noneOf(Bypass.class) : EnumSet.copyOf(bypass);
        }

        public void close() {
            ZONES.remove(this);
        }

        public boolean open() {
            return ZONES.contains(this);
        }
    }

    private static final List<Zone> ZONES = new CopyOnWriteArrayList<>();
    private static final List<Predicate<Entity>> EXEMPT = new CopyOnWriteArrayList<>();

    private ProtectionBypass() {
    }

    /**
     * Opens a zone. {@code key} names it for {@link #close(String)} (e.g. "supernaturalcraft:arena/12"); opening a key
     * again replaces the old zone.
     */
    public static Zone openZone(String key, String dimension, AABB box, Set<Bypass> bypass) {
        close(key);
        Zone z = new Zone(key, dimension, box, bypass);
        ZONES.add(z);
        return z;
    }

    public static void close(String key) {
        ZONES.removeIf(z -> z.key.equals(key));
    }

    /** Closes every zone whose key starts with {@code prefix} (a mod tidying up). */
    public static void closeAll(String prefix) {
        ZONES.removeIf(z -> z.key.startsWith(prefix));
    }

    public static List<Zone> zones() {
        return List.copyOf(ZONES);
    }

    public static void exemptEntities(Predicate<Entity> exempt) {
        EXEMPT.add(exempt);
    }

    /** Whether protection {@code b} is off at {@code pos} (inside an open zone of {@code level}'s dimension). */
    public static boolean bypassed(Level level, BlockPos pos, Bypass b) {
        if (ZONES.isEmpty()) return false;
        String dim = level.dimension().location().toString();
        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
        for (Zone zone : ZONES) if (zone.bypass.contains(b) && zone.dimension.equals(dim) && zone.box.contains(x, y, z)) return true;
        return false;
    }

    /** Whether {@code e} is exempt from spawn, griefing and explosion rules. */
    public static boolean exempt(Entity e) {
        if (e == null) return false;
        for (Predicate<Entity> p : EXEMPT) if (p.test(e)) return true;
        return false;
    }
}
