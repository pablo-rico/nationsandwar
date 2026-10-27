package org.papiricoh.nationsandwar.api.event;

import net.minecraft.server.MinecraftServer;
import org.papiricoh.nationsandwar.model.Alliance;
import org.papiricoh.nationsandwar.model.Nation;

/** A nation joined or left an alliance (founding one counts as joining it). */
public abstract class AllianceEvent extends NWEvent {
    private final Alliance alliance;
    private final Nation nation;

    protected AllianceEvent(MinecraftServer server, Alliance alliance, Nation nation) {
        super(server);
        this.alliance = alliance;
        this.nation = nation;
    }

    public Alliance alliance() {
        return alliance;
    }

    public Nation nation() {
        return nation;
    }

    public static final class Joined extends AllianceEvent {
        public Joined(MinecraftServer server, Alliance alliance, Nation nation) {
            super(server, alliance, nation);
        }
    }

    /** Left or kicked; the alliance may be dissolved already. */
    public static final class Left extends AllianceEvent {
        public Left(MinecraftServer server, Alliance alliance, Nation nation) {
            super(server, alliance, nation);
        }
    }
}
