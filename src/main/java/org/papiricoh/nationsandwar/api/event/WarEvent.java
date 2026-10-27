package org.papiricoh.nationsandwar.api.event;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.ICancellableEvent;
import org.papiricoh.nationsandwar.api.WarApi;
import org.papiricoh.nationsandwar.model.Nation;
import org.papiricoh.nationsandwar.model.Town;
import org.papiricoh.nationsandwar.war.Siege;
import org.papiricoh.nationsandwar.war.Treaty;
import org.papiricoh.nationsandwar.war.War;

import java.util.UUID;

/** The life of a war. All but {@link Declare} carry the {@link War} record and are delivered at the end of the tick. */
public abstract class WarEvent extends NWEvent {
    protected WarEvent(MinecraftServer server) {
        super(server);
    }

    /**
     * About to declare (every declaration: players, NPC nations, other mods). Posted at once; cancel it to forbid the war
     * (the declarer is told {@code nationsandwar.msg.war.vetoed}).
     */
    public static final class Declare extends WarEvent implements ICancellableEvent {
        private final Nation attacker, defender;
        private final WarApi.WarOptions options;

        public Declare(MinecraftServer server, Nation attacker, Nation defender, WarApi.WarOptions options) {
            super(server);
            this.attacker = attacker;
            this.defender = defender;
            this.options = options;
        }

        public Nation attacker() {
            return attacker;
        }

        public Nation defender() {
            return defender;
        }

        public WarApi.WarOptions options() {
            return options;
        }
    }

    /** Base of the events about a war that exists. */
    public abstract static class Of extends WarEvent {
        private final War war;

        protected Of(MinecraftServer server, War war) {
            super(server);
            this.war = war;
        }

        public War war() {
            return war;
        }
    }

    /** Declared: in preparation (or already active with no preparation). */
    public static final class Started extends Of {
        public Started(MinecraftServer server, War war) {
            super(server, war);
        }
    }

    public static final class PhaseChanged extends Of {
        private final War.Phase before;

        public PhaseChanged(MinecraftServer server, War war, War.Phase before) {
            super(server, war);
            this.before = before;
        }

        public War.Phase before() {
            return before;
        }
    }

    public static final class SiegeStarted extends Of {
        private final Siege siege;

        public SiegeStarted(MinecraftServer server, War war, Siege siege) {
            super(server, war);
            this.siege = siege;
        }

        public Siege siege() {
            return siege;
        }
    }

    /** A siege won its chunk ({@code byAttackers}: which side took it). */
    public static final class ChunkTaken extends Of {
        private final Town town;
        private final long chunk;
        private final boolean byAttackers;

        public ChunkTaken(MinecraftServer server, War war, Town town, long chunk, boolean byAttackers) {
            super(server, war);
            this.town = town;
            this.chunk = chunk;
            this.byAttackers = byAttackers;
        }

        public Town town() {
            return town;
        }

        public long chunk() {
            return chunk;
        }

        public boolean byAttackers() {
            return byAttackers;
        }
    }

    /**
     * The town's own side took back an occupied chunk ({@code byAttackers}: which side freed it). The occupiers lost the
     * chunk's points; a freed home chunk closes the Core again.
     */
    public static final class ChunkLiberated extends Of {
        private final Town town;
        private final long chunk;
        private final boolean byAttackers;

        public ChunkLiberated(MinecraftServer server, War war, Town town, long chunk, boolean byAttackers) {
            super(server, war);
            this.town = town;
            this.chunk = chunk;
            this.byAttackers = byAttackers;
        }

        public Town town() {
            return town;
        }

        public long chunk() {
            return chunk;
        }

        public boolean byAttackers() {
            return byAttackers;
        }
    }

    /**
     * A town's home chunk fell: its Core may be broken. A town with no Core block (a managed town whose provider resolves
     * the heart itself) waits for {@link WarApi#townFallen}.
     */
    public static final class CoreOpened extends Of {
        private final Town town;
        private final boolean byAttackers;

        public CoreOpened(MinecraftServer server, War war, Town town, boolean byAttackers) {
            super(server, war);
            this.town = town;
            this.byAttackers = byAttackers;
        }

        public Town town() {
            return town;
        }

        public boolean byAttackers() {
            return byAttackers;
        }
    }

    public static final class TownFallen extends Of {
        private final Town town;
        private final UUID byNation;

        public TownFallen(MinecraftServer server, War war, Town town, UUID byNation) {
            super(server, war);
            this.town = town;
            this.byNation = byNation;
        }

        public Town town() {
            return town;
        }

        public UUID byNation() {
            return byNation;
        }
    }

    /** The fighting is over ({@link War#outcome}); the treaty may still be pending. */
    public static final class Ended extends Of {
        public Ended(MinecraftServer server, War war) {
            super(server, war);
        }
    }

    public static final class TreatyApplied extends Of {
        private final Treaty terms;

        public TreatyApplied(MinecraftServer server, War war, Treaty terms) {
            super(server, war);
            this.terms = terms;
        }

        /** The terms applied (null for a war that ended without a treaty). */
        public Treaty terms() {
            return terms;
        }
    }
}
