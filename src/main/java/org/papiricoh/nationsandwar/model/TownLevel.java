package org.papiricoh.nationsandwar.model;

/**
 * A town's size class by residents: more residents, more chunks and a grander title ({@code nationsandwar.level.<id>}).
 * The claim limit is the level's base plus {@link #perResident} for each resident, both scaled by the server config.
 */
public enum TownLevel {
    HAMLET(1, 16), VILLAGE(3, 32), CITY(8, 64), METROPOLIS(16, 128);

    public static final int PER_RESIDENT = 8;

    public final int minResidents, baseChunks;

    TownLevel(int minResidents, int baseChunks) {
        this.minResidents = minResidents;
        this.baseChunks = baseChunks;
    }

    public String langKey() {
        return "nationsandwar.level." + name().toLowerCase();
    }

    public static TownLevel of(int residents) {
        TownLevel best = HAMLET;
        for (TownLevel l : values()) if (residents >= l.minResidents) best = l;
        return best;
    }

    /** The most chunks a town of {@code residents} may hold, with {@code scale} from the config. */
    public static int maxChunks(int residents, double scale) {
        return (int) Math.round((of(residents).baseChunks + PER_RESIDENT * Math.max(0, residents - 1)) * scale);
    }
}
