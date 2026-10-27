package org.papiricoh.nationsandwar.api.event;

import net.minecraft.server.MinecraftServer;
import org.papiricoh.nationsandwar.model.Town;

import java.util.UUID;

/** Something happened to a town. The {@link Town} is the live record (or the last one, once it is removed). */
public abstract class TownEvent extends NWEvent {
    private final Town town;

    protected TownEvent(MinecraftServer server, Town town) {
        super(server);
        this.town = town;
    }

    public Town town() {
        return town;
    }

    public static final class Founded extends TownEvent {
        public Founded(MinecraftServer server, Town town) {
            super(server, town);
        }
    }

    /** Deleted, razed, or emptied: its claims are free and its residents homeless. */
    public static final class Removed extends TownEvent {
        public Removed(MinecraftServer server, Town town) {
            super(server, town);
        }
    }

    public static final class Renamed extends TownEvent {
        private final String before;

        public Renamed(MinecraftServer server, Town town, String before) {
            super(server, town);
            this.before = before;
        }

        public String before() {
            return before;
        }
    }

    public static final class ResidentJoined extends TownEvent {
        private final UUID player;

        public ResidentJoined(MinecraftServer server, Town town, UUID player) {
            super(server, town);
            this.player = player;
        }

        public UUID player() {
            return player;
        }
    }

    /** Left or kicked (a removed town raises {@link Removed} instead). */
    public static final class ResidentLeft extends TownEvent {
        private final UUID player;

        public ResidentLeft(MinecraftServer server, Town town, UUID player) {
            super(server, town);
            this.player = player;
        }

        public UUID player() {
            return player;
        }
    }

    public static final class Claimed extends TownEvent {
        private final long chunk;

        public Claimed(MinecraftServer server, Town town, long chunk) {
            super(server, town);
            this.chunk = chunk;
        }

        /** {@code ChunkPos.asLong} key, in {@code town().dimension}. */
        public long chunk() {
            return chunk;
        }
    }

    public static final class Unclaimed extends TownEvent {
        private final long chunk;

        public Unclaimed(MinecraftServer server, Town town, long chunk) {
            super(server, town);
            this.chunk = chunk;
        }

        public long chunk() {
            return chunk;
        }
    }

    /** A chunk of {@code from} now belongs to {@link #town()} (conquest, annexation). */
    public static final class ChunkTransferred extends TownEvent {
        private final Town from;
        private final long chunk;

        public ChunkTransferred(MinecraftServer server, Town from, Town to, long chunk) {
            super(server, to);
            this.from = from;
            this.chunk = chunk;
        }

        public Town from() {
            return from;
        }

        public long chunk() {
            return chunk;
        }
    }
}
