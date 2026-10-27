package org.papiricoh.nationsandwar.network;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * How often one player may ask the server for something: a token bucket per player and kind of request, refilled
 * with game time. A modified client spamming a request is answered at the rate a real one needs, never more. Server thread.
 */
public final class RateLimiter {

    /** Tokens and when they were last refilled, per player and kind. */
    private static final Map<UUID, Map<String, double[]>> BUCKETS = new HashMap<>();

    private RateLimiter() {
    }

    /**
     * Takes one token for {@code kind} from {@code player}'s bucket.
     *
     * @param perSecond tokens refilled each second
     * @param burst     the most kept at once
     * @return whether the request may go ahead
     */
    public static boolean allow(ServerPlayer player, String kind, double perSecond, int burst) {
        return allow(player.getUUID(), kind, perSecond, burst, player.serverLevel().getGameTime());
    }

    /** {@link #allow(ServerPlayer, String, double, int)} at game time {@code now} (pure, for tests). */
    public static boolean allow(UUID player, String kind, double perSecond, int burst, long now) {
        double[] b = BUCKETS.computeIfAbsent(player, k -> new HashMap<>()).computeIfAbsent(kind, k -> new double[]{burst, now});
        b[0] = Math.min(burst, b[0] + Math.max(0, now - (long) b[1]) * perSecond / 20.0);
        b[1] = now;
        if (b[0] < 1) return false;
        b[0] -= 1;
        return true;
    }

    public static void forget(UUID player) {
        BUCKETS.remove(player);
    }
}
