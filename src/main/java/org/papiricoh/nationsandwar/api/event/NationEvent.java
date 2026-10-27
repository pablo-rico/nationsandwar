package org.papiricoh.nationsandwar.api.event;

import net.minecraft.server.MinecraftServer;
import org.papiricoh.nationsandwar.model.Nation;
import org.papiricoh.nationsandwar.model.Town;

import java.util.UUID;

/** Something happened to a nation. Citizenship follows towns: a town joining or leaving moves all its residents. */
public abstract class NationEvent extends NWEvent {
    private final Nation nation;

    protected NationEvent(MinecraftServer server, Nation nation) {
        super(server);
        this.nation = nation;
    }

    public Nation nation() {
        return nation;
    }

    public static final class Founded extends NationEvent {
        public Founded(MinecraftServer server, Nation nation) {
            super(server, nation);
        }
    }

    /** Disbanded: its towns are free and it left its alliance. */
    public static final class Removed extends NationEvent {
        public Removed(MinecraftServer server, Nation nation) {
            super(server, nation);
        }
    }

    public static final class Renamed extends NationEvent {
        private final String before;

        public Renamed(MinecraftServer server, Nation nation, String before) {
            super(server, nation);
            this.before = before;
        }

        public String before() {
            return before;
        }
    }

    /** The leader changed; either side may be null. */
    public static final class LeaderChanged extends NationEvent {
        private final UUID before, after;

        public LeaderChanged(MinecraftServer server, Nation nation, UUID before, UUID after) {
            super(server, nation);
            this.before = before;
            this.after = after;
        }

        public UUID before() {
            return before;
        }

        public UUID after() {
            return after;
        }
    }

    public static final class TownJoined extends NationEvent {
        private final Town town;

        public TownJoined(MinecraftServer server, Nation nation, Town town) {
            super(server, nation);
            this.town = town;
        }

        public Town town() {
            return town;
        }
    }

    /** Left, kicked, annexed away, or the nation disbanded around it. */
    public static final class TownLeft extends NationEvent {
        private final Town town;

        public TownLeft(MinecraftServer server, Nation nation, Town town) {
            super(server, nation);
            this.town = town;
        }

        public Town town() {
            return town;
        }
    }
}
