package org.papiricoh.nationsandwar.compat;

import java.util.Locale;

/**
 * What the claim maps colour and name: each town shows as part of its nation, province, itself or its alliance. A town
 * outside the chosen kind of group falls back to the next one up (a province-less town shows its nation; a nation-less
 * town itself). Chosen in the client config ({@code map.layer}), on the map tab or with the layer key; JourneyMap and
 * Xaero's follow it.
 */
public enum MapLayer {
    NATIONS, PROVINCES, TOWNS, ALLIANCES;

    public MapLayer next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The layer's name: {@code nationsandwar.map.layer.<id>}. */
    public String langKey() {
        return "nationsandwar.map.layer." + id();
    }
}
