package org.papiricoh.nationsandwar.war;

import java.util.Random;

/**
 * Sieges nobody watches (pure): a war between NPC or managed nations whose banner lies in an unloaded chunk runs on the
 * fighters last counted there. The bar moves as {@link SiegeControl} says; every {@link #ATTRITION_SECONDS} the fighters
 * clash and someone may fall (the weaker side more often), so no siege stays even forever.
 */
public final class WarSim {
    public static final int ATTRITION_SECONDS = 30;

    private WarSim() {
    }

    /** Whether this second is a clash for siege {@code siegeId} (each siege on its own beat). */
    public static boolean clash(long second, int siegeId) {
        return Math.floorMod(second + siegeId * 7L, ATTRITION_SECONDS) == 0;
    }

    /**
     * One clash between {@code a} besiegers and {@code d} holders: returns their numbers after it. Each side may lose one
     * fighter: the outnumbered side with 0.5 + 0.1 per fighter it lacks (at most 0.9), the other with 0.25; an even fight
     * 0.35 each.
     */
    public static int[] clash(int a, int d, Random r) {
        if (a <= 0 || d <= 0) return new int[]{Math.max(0, a), Math.max(0, d)};
        double pa, pd;
        if (a == d) {
            pa = pd = 0.35;
        } else {
            double weak = Math.min(0.9, 0.5 + 0.1 * Math.abs(a - d));
            pa = a < d ? weak : 0.25;
            pd = d < a ? weak : 0.25;
        }
        int na = r.nextDouble() < pa ? a - 1 : a;
        int nd = r.nextDouble() < pd ? d - 1 : d;
        return new int[]{na, nd};
    }

    /** The seeded dice for one clash. */
    public static Random dice(java.util.UUID war, int siegeId, long second) {
        return new Random(war.getMostSignificantBits() ^ war.getLeastSignificantBits() * 31 ^ siegeId * 0x9E3779B97F4A7C15L ^ second);
    }
}
