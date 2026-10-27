package org.papiricoh.nationsandwar;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.papiricoh.nationsandwar.model.Rules;

/** Server config ({@code serverconfig/nationsandwar-server.toml}). Reads fall back to the defaults before the config loads. */
public final class NWConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue TOWN_COST, CLAIM_COST, CLAIM_GROWTH, MAX_CLAIM_COST, OUTPOST_COST, MAX_OUTPOSTS, BUFFER,
            PROVINCE_COST, NATION_COST, ALLIANCE_COST;
    public static final ModConfigSpec.DoubleValue CLAIM_SCALE;

    public static final ModConfigSpec.IntValue WAR_COST, PREPARATION_MINUTES, DURATION_MINUTES, TARGET_SCORE, KILL_POINTS, CHUNK_POINTS,
            CORE_POINTS, MIN_DEFENDERS_ONLINE, SIEGE_RADIUS, MAX_SIEGES, CORE_HITS, TREATY_MINUTES, VASSAL_DAYS, MAX_VASSAL_DAYS,
            WAR_COOLDOWN_MINUTES;
    public static final ModConfigSpec.DoubleValue CONTROL_RATE, CONTROL_DECAY, TRIBUTE_SHARE;

    public static final ModConfigSpec.BooleanValue NPC_VILLAGES, NPC_WARS;
    public static final ModConfigSpec.DoubleValue VILLAGE_CHANCE;
    public static final ModConfigSpec.IntValue VILLAGE_RADIUS, GROUP_DISTANCE, MAX_NPC_TOWNS, GUARDS_PER_TOWN, BRAIN_INTERVAL, WAR_PARTY_SIZE;

    static {
        B.comment("Costs in emeralds, paid once (there is no upkeep). Town founding is paid by the founder; claims from the town bank;",
                "provinces, alliances and wars from the nation bank; nations from the founding town's bank.").push("economy");
        TOWN_COST = B.defineInRange("townCost", 64, 0, 100000);
        CLAIM_COST = B.comment("Price of a town's second chunk; each further chunk costs claimGrowth more, up to maxClaimCost.")
                .defineInRange("claimCost", 4, 0, 100000);
        CLAIM_GROWTH = B.defineInRange("claimGrowth", 1, 0, 10000);
        MAX_CLAIM_COST = B.defineInRange("maxClaimCost", 32, 0, 100000);
        OUTPOST_COST = B.defineInRange("outpostCost", 48, 0, 100000);
        MAX_OUTPOSTS = B.defineInRange("maxOutposts", 2, 0, 64);
        CLAIM_SCALE = B.comment("Multiplier on the chunks a town level allows.").defineInRange("claimScale", 1.0, 0.1, 100.0);
        BUFFER = B.comment("Chunks of wilderness kept between towns of different nations.").defineInRange("buffer", 2, 0, 16);
        PROVINCE_COST = B.defineInRange("provinceCost", 32, 0, 100000);
        NATION_COST = B.defineInRange("nationCost", 256, 0, 100000);
        ALLIANCE_COST = B.defineInRange("allianceCost", 64, 0, 100000);
        B.pop();

        B.push("war");
        WAR_COST = B.comment("Emeralds from the nation bank to declare a war.").defineInRange("warCost", 128, 0, 100000);
        PREPARATION_MINUTES = B.defineInRange("preparationMinutes", 10, 0, 10080);
        DURATION_MINUTES = B.comment("How long the active phase lasts before the score decides.").defineInRange("durationMinutes", 120, 1, 43200);
        TARGET_SCORE = B.comment("A side reaching this score wins at once.").defineInRange("targetScore", 60, 1, 100000);
        KILL_POINTS = B.defineInRange("killPoints", 1, 0, 1000);
        CHUNK_POINTS = B.defineInRange("chunkPoints", 3, 0, 1000);
        CORE_POINTS = B.defineInRange("corePoints", 15, 0, 1000);
        MIN_DEFENDERS_ONLINE = B.comment("Players of the besieged side who must be online to plant a Siege Banner (NPC nations always count as present).")
                .defineInRange("minDefendersOnline", 1, 0, 100);
        SIEGE_RADIUS = B.comment("Blocks around a Siege Banner where fighters push its control bar.").defineInRange("siegeRadius", 12, 2, 64);
        CONTROL_RATE = B.comment("Control gained per second per extra fighter (1.0 = full bar).").defineInRange("controlRate", 0.01, 0.0001, 1.0);
        CONTROL_DECAY = B.comment("Control lost per second toward zero when nobody is near.").defineInRange("controlDecay", 0.002, 0.0, 1.0);
        MAX_SIEGES = B.comment("Sieges a side may run at once in one war.").defineInRange("maxSieges", 3, 1, 32);
        CORE_HITS = B.comment("Times a Town Core must be broken, once its home chunk has fallen, to take the town.").defineInRange("coreHits", 8, 1, 1000);
        TREATY_MINUTES = B.comment("Minutes the winner has to dictate the treaty before its default terms apply.").defineInRange("treatyMinutes", 5, 0, 1440);
        TRIBUTE_SHARE = B.comment("Default tribute: this share of the loser's nation bank.").defineInRange("tributeShare", 0.25, 0.0, 1.0);
        VASSAL_DAYS = B.comment("Default days of vassalage when the loser's capital fell (in-game days).").defineInRange("vassalDays", 7, 0, 365);
        MAX_VASSAL_DAYS = B.defineInRange("maxVassalDays", 14, 0, 365);
        WAR_COOLDOWN_MINUTES = B.comment("Minutes after a war before the same two nations may fight again.").defineInRange("warCooldownMinutes", 60, 0, 43200);
        B.pop();

        B.push("npc");
        NPC_VILLAGES = B.comment("Turn some generated villages into NPC towns.").define("villages", true);
        VILLAGE_CHANCE = B.defineInRange("villageChance", 0.35, 0.0, 1.0);
        VILLAGE_RADIUS = B.comment("Chunks claimed around a village's centre (2 = 5x5).").defineInRange("villageRadius", 2, 0, 6);
        GROUP_DISTANCE = B.comment("An NPC town joins an NPC nation whose capital lies within this many chunks.").defineInRange("groupDistance", 24, 0, 256);
        MAX_NPC_TOWNS = B.defineInRange("maxTownsPerNation", 4, 1, 32);
        GUARDS_PER_TOWN = B.defineInRange("guardsPerTown", 3, 0, 16);
        BRAIN_INTERVAL = B.comment("Ticks between NPC nations' decisions (24000 = one day).").defineInRange("brainInterval", 24000, 1200, 240000);
        NPC_WARS = B.comment("NPC nations may declare wars (on each other and on players).").define("wars", true);
        WAR_PARTY_SIZE = B.defineInRange("warPartySize", 4, 1, 16);
        B.pop();
    }

    public static final ModConfigSpec SPEC = B.build();

    private NWConfig() {
    }

    public static int get(ModConfigSpec.IntValue v) {
        return SPEC.isLoaded() ? v.get() : v.getDefault();
    }

    public static double get(ModConfigSpec.DoubleValue v) {
        return SPEC.isLoaded() ? v.get() : v.getDefault();
    }

    public static boolean get(ModConfigSpec.BooleanValue v) {
        return SPEC.isLoaded() ? v.get() : v.getDefault();
    }

    public static Rules rules() {
        return new Rules(get(TOWN_COST), get(CLAIM_COST), get(CLAIM_GROWTH), get(MAX_CLAIM_COST), get(OUTPOST_COST), get(MAX_OUTPOSTS),
                get(CLAIM_SCALE), get(BUFFER), get(PROVINCE_COST), get(NATION_COST), get(ALLIANCE_COST));
    }

    public static long minutes(ModConfigSpec.IntValue v) {
        return get(v) * 1200L;
    }
}
