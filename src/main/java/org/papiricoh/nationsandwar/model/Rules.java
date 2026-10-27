package org.papiricoh.nationsandwar.model;

/**
 * The tunable numbers {@link Politics} applies (the server config's, or {@link #DEFAULTS} in tests). Costs are emeralds.
 *
 * @param claimScale     multiplier on {@link TownLevel#maxChunks}
 * @param claimCost      the second chunk's price; each further one costs {@code claimGrowth} more
 * @param buffer         chunks of wilderness kept between towns of different nations (Chebyshev distance)
 */
public record Rules(int townCost, int claimCost, int claimGrowth, int maxClaimCost, int outpostCost, int maxOutposts,
                    double claimScale, int buffer, int provinceCost, int nationCost, int allianceCost) {
    public static final Rules DEFAULTS = new Rules(64, 4, 1, 32, 48, 2, 1.0, 2, 32, 256, 64);

    /** The price of a town's next chunk when it already holds {@code held}. */
    public int nextClaimCost(int held) {
        return Math.min(maxClaimCost, claimCost + claimGrowth * Math.max(0, held - 1));
    }
}
