package org.papiricoh.nationsandwar.model;

/** A town-wide switch that a single chunk may override. */
public enum Flag {
    PVP(false), MOBS(false), FIRE(false), EXPLOSIONS(false);

    public final boolean defaultOn;

    Flag(boolean defaultOn) {
        this.defaultOn = defaultOn;
    }

    public String langKey() {
        return "nationsandwar.flag." + name().toLowerCase();
    }

    public static Flag byId(String id) {
        for (Flag f : values()) if (f.name().equalsIgnoreCase(id)) return f;
        return null;
    }
}
