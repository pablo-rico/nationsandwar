package org.papiricoh.nationsandwar.model;

import java.util.UUID;

/**
 * Hears every change {@link Politics} makes, whoever asked for it (a command, the GUI, an NPC nation, a treaty, another
 * mod). Pure: {@code core} installs one that posts the API's NeoForge events ({@code api.event}). Each call comes after the
 * change is done. The founding of a town or nation brings its first claims with it (no separate claim calls).
 */
public interface PoliticsObserver {
    PoliticsObserver NONE = new PoliticsObserver() {
    };

    default void townFounded(Town town) {
    }

    /** The town is gone (its claims already back to the wilderness, its residents homeless). */
    default void townRemoved(Town town) {
    }

    default void residentJoined(Town town, UUID player) {
    }

    /** Left, kicked (not when the whole town is removed: see {@link #townRemoved}). */
    default void residentLeft(Town town, UUID player) {
    }

    default void claimed(Town town, long chunk) {
    }

    default void unclaimed(Town town, long chunk) {
    }

    /** A chunk changed hands between two towns (conquest, annexation). */
    default void chunkTransferred(Town from, Town to, long chunk) {
    }

    default void nationFounded(Nation nation) {
    }

    /** The nation is gone (its towns free, out of its alliance). */
    default void nationRemoved(Nation nation) {
    }

    default void townJoinedNation(Nation nation, Town town) {
    }

    /** Left, kicked, annexed away, or its nation disbanded around it. */
    default void townLeftNation(Nation nation, Town town) {
    }

    /** The nation's leader changed ({@code before} or {@code after} may be null). */
    default void leaderChanged(Nation nation, UUID before, UUID after) {
    }

    /** A town or a nation was renamed. */
    default void renamed(Object owner, String before) {
    }

    default void allianceJoined(Alliance alliance, Nation nation) {
    }

    /** Left or kicked; {@code alliance} may already be dissolved. */
    default void allianceLeft(Alliance alliance, Nation nation) {
    }
}
