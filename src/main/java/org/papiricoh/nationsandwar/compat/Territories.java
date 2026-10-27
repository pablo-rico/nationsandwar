package org.papiricoh.nationsandwar.compat;

import net.minecraft.world.level.ChunkPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Where the names go on a claim map, Paradox style: the claims are split into connected regions per group (a nation, a
 * province… see {@link MapLayer}), and each region big enough gets one label, tilted along its long axis when the region
 * is clearly elongated, sized to fill it.
 * Pure maths over chunk keys, shared by the mod's map, JourneyMap and Xaero's.
 */
public final class Territories {
    /** Regions smaller than this get no label, except the largest region of each group. */
    public static final int MIN_CHUNKS = 6;
    /** How far a label may tilt from the horizontal (radians), so names read left to right. */
    static final double MAX_TILT = Math.toRadians(40);
    /** A region must be this many times longer than thick (along its own axis) before its label tilts along it. */
    static final double TILT_RATIO = 1.6;

    /**
     * A label: its group, its centre ({@code x}, {@code z} in chunk units, where chunk (0, 0) spans 0..1), its tilt in radians
     * (clockwise on a north-up map), the region's extent along and across that tilt (chunks) and its size.
     */
    public record Label(UUID group, double x, double z, double angle, double length, double thickness, int chunks, boolean largest) {
    }

    private Territories() {
    }

    /** The labels of every group's regions; {@code groupOf} maps a town id to its group (null: leave the chunk out). */
    public static List<Label> labels(Map<Long, UUID> claims, Function<UUID, UUID> groupOf) {
        Map<Long, UUID> group = new HashMap<>();
        Map<UUID, UUID> memo = new HashMap<>();
        for (var e : claims.entrySet()) {
            UUID g = memo.computeIfAbsent(e.getValue(), groupOf::apply);
            if (g != null) group.put(e.getKey(), g);
        }
        Set<Long> seen = new HashSet<>();
        Map<UUID, List<List<Long>>> regions = new HashMap<>();
        for (long start : group.keySet()) {
            if (!seen.add(start)) continue;
            UUID g = group.get(start);
            List<Long> region = new ArrayList<>();
            ArrayDeque<Long> todo = new ArrayDeque<>();
            todo.add(start);
            while (!todo.isEmpty()) {
                long c = todo.poll();
                region.add(c);
                int cx = ChunkPos.getX(c), cz = ChunkPos.getZ(c);
                for (long n : new long[]{ChunkPos.asLong(cx + 1, cz), ChunkPos.asLong(cx - 1, cz), ChunkPos.asLong(cx, cz + 1), ChunkPos.asLong(cx, cz - 1)})
                    if (g.equals(group.get(n)) && seen.add(n)) todo.add(n);
            }
            regions.computeIfAbsent(g, k -> new ArrayList<>()).add(region);
        }
        List<Label> out = new ArrayList<>();
        regions.forEach((g, list) -> {
            list.sort(Comparator.comparingInt(r -> -r.size()));
            for (int i = 0; i < list.size(); i++)
                if (i == 0 || list.get(i).size() >= MIN_CHUNKS) out.add(label(g, list.get(i), i == 0));
        });
        out.sort(Comparator.comparingInt(l -> -l.chunks()));
        return out;
    }

    /** One region's label: centroid, principal axis (tilt clamped), extents along and across it. */
    static Label label(UUID group, List<Long> region, boolean largest) {
        int n = region.size();
        double mx = 0, mz = 0;
        for (long c : region) {
            mx += ChunkPos.getX(c) + 0.5;
            mz += ChunkPos.getZ(c) + 0.5;
        }
        mx /= n;
        mz /= n;
        double sxx = 0, szz = 0, sxz = 0;
        for (long c : region) {
            double dx = ChunkPos.getX(c) + 0.5 - mx, dz = ChunkPos.getZ(c) + 0.5 - mz;
            sxx += dx * dx;
            szz += dz * dz;
            sxz += dx * dz;
        }
        double angle = Math.abs(sxx - szz) < 1e-9 && Math.abs(sxz) < 1e-9 ? 0 : 0.5 * Math.atan2(2 * sxz, sxx - szz);
        if (angle > Math.PI / 2) angle -= Math.PI;
        if (angle < -Math.PI / 2) angle += Math.PI;
        double[] axis = extents(region, mx, mz, angle);
        angle = axis[0] < TILT_RATIO * axis[1] ? 0 : Math.max(-MAX_TILT, Math.min(MAX_TILT, angle));
        double[] ext = extents(region, mx, mz, angle);
        // A centroid outside the region (an L or a ring) moves to the region's chunk nearest to it.
        long at = ChunkPos.asLong((int) Math.floor(mx), (int) Math.floor(mz));
        if (!region.contains(at)) {
            double best = Double.MAX_VALUE;
            for (long c : region) {
                double dx = ChunkPos.getX(c) + 0.5 - mx, dz = ChunkPos.getZ(c) + 0.5 - mz, d = dx * dx + dz * dz;
                if (d < best) {
                    best = d;
                    at = c;
                }
            }
            mx = ChunkPos.getX(at) + 0.5;
            mz = ChunkPos.getZ(at) + 0.5;
        }
        return new Label(group, mx, mz, angle, ext[0], ext[1], n, largest);
    }

    /** A region's extent (chunks) along a direction and across it, measured from (mx, mz). */
    private static double[] extents(List<Long> region, double mx, double mz, double angle) {
        double ux = Math.cos(angle), uz = Math.sin(angle);
        double minU = Double.MAX_VALUE, maxU = -Double.MAX_VALUE, minV = Double.MAX_VALUE, maxV = -Double.MAX_VALUE;
        for (long c : region) {
            double dx = ChunkPos.getX(c) + 0.5 - mx, dz = ChunkPos.getZ(c) + 0.5 - mz;
            double u = dx * ux + dz * uz, v = -dx * uz + dz * ux;
            minU = Math.min(minU, u);
            maxU = Math.max(maxU, u);
            minV = Math.min(minV, v);
            maxV = Math.max(maxV, v);
        }
        return new double[]{maxU - minU + 1, maxV - minV + 1};
    }
}
