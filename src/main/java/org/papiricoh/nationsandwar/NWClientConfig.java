package org.papiricoh.nationsandwar;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client config: how the map overlays look, and which top-right notices ({@code client.notice}) show and how. */
public final class NWClientConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue MAP_OVERLAYS, MAP_LABELS;
    public static final ModConfigSpec.DoubleValue MAP_FILL_OPACITY;
    public static final ModConfigSpec.BooleanValue NOTICES, NOTICE_TERRITORY, NOTICE_WAR, NOTICE_POLITICS, NOTICE_PERMISSION, NOTICE_SIEGE_PANEL,
            NOTICE_SOUNDS;
    public static final ModConfigSpec.DoubleValue NOTICE_VOLUME, NOTICE_SCALE;
    public static final ModConfigSpec.IntValue NOTICE_TOP_OFFSET;
    public static final ModConfigSpec.BooleanValue NOTICE_TOP_CENTER;

    static {
        B.push("map");
        MAP_OVERLAYS = B.comment("Draw towns, nations and sieges on JourneyMap / Xaero's maps.").define("overlays", true);
        MAP_LABELS = B.comment("Town names on the map overlays.").define("labels", true);
        MAP_FILL_OPACITY = B.defineInRange("fillOpacity", 0.3, 0.0, 1.0);
        B.pop();
        B.push("notices");
        NOTICES = B.comment("Show the top-right notices at all.").define("enabled", true);
        NOTICE_TERRITORY = B.comment("Entering a town, province, nation or the wilderness.").define("territory", true);
        NOTICE_WAR = B.comment("Wars and sieges.").define("war", true);
        NOTICE_POLITICS = B.comment("Towns, nations, alliances, offices and invitations.").define("politics", true);
        NOTICE_PERMISSION = B.comment("The short line when you may not build or use something.").define("permission", true);
        NOTICE_SIEGE_PANEL = B.comment("The siege control panel while you stand near a besieged chunk.").define("siegePanel", true);
        NOTICE_SOUNDS = B.comment("A soft sound when a notice appears.").define("sounds", true);
        NOTICE_VOLUME = B.defineInRange("volume", 0.6, 0.0, 1.0);
        NOTICE_SCALE = B.comment("Size of the notices (1 = GUI scale).").defineInRange("scale", 0.5, 0.25, 1.5);
        NOTICE_TOP_CENTER = B.comment("Show the notices at the top centre (under boss bars) instead of the top-right corner (under toasts and minimaps).")
                .define("topCenter", true);
        NOTICE_TOP_OFFSET = B.comment("GUI pixels kept free above the notices (for a top-right minimap). -1 = automatic (below JourneyMap's minimap).")
                .defineInRange("topOffset", -1, -1, 1000);
        B.pop();
    }

    public static final ModConfigSpec SPEC = B.build();

    private NWClientConfig() {
    }

    public static boolean get(ModConfigSpec.BooleanValue v) {
        return SPEC.isLoaded() ? v.get() : v.getDefault();
    }

    public static int get(ModConfigSpec.IntValue v) {
        return SPEC.isLoaded() ? v.get() : v.getDefault();
    }

    public static double get(ModConfigSpec.DoubleValue v) {
        return SPEC.isLoaded() ? v.get() : v.getDefault();
    }
}
