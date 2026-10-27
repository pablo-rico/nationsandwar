package org.papiricoh.nationsandwar.api;

import net.minecraft.server.MinecraftServer;
import org.papiricoh.nationsandwar.model.Nation;
import org.papiricoh.nationsandwar.model.Town;
import org.papiricoh.nationsandwar.war.Treaty;
import org.papiricoh.nationsandwar.war.War;

/**
 * Another mod's say over the nations it runs (see {@link ManagedNations}). A managed nation has no citizens: nobody joins
 * it, allies with it or edits it here, NPC nations ignore it, and its towns always count as defended. Every method has a
 * default, so a provider only overrides what it cares about. Server side only.
 */
public interface ManagedNationProvider {
    /** What a town of a managed nation becomes after it fell in a war its nation lost. */
    enum FallenTown {
        /** Removed: its claims go back to the wilderness ({@code TownEvent.Removed} follows). */
        RAZE,
        /** Handed to the winner (and run by the winner's provider, if the winner is managed too). */
        TRANSFER,
        /** Left as it is. */
        KEEP
    }

    /** The key the provider's nations and towns carry ({@code Nation.managedBy}), e.g. "supernaturalcraft:pit". */
    String key();

    /**
     * Whether {@code attacker} may declare war on {@code self} the ordinary way (a player nation's marshal, an NPC
     * nation's brain). The provider's own declarations ({@link WarApi#declare}) never ask. Default: nobody may.
     */
    default boolean mayBeDeclaredOn(MinecraftServer server, Nation self, Nation attacker) {
        return false;
    }

    /** Whether {@code besieger} may plant a Siege Banner in this chunk of {@code town}. Default: anywhere on the front. */
    default boolean mayBesiege(MinecraftServer server, Town town, long chunk, Nation besieger) {
        return true;
    }

    /**
     * The peace {@code self} dictates after winning against {@code loser}; null takes this mod's defaults without any
     * annexation (a managed nation never takes player towns unless the provider asks for them here). Applied at once.
     */
    default Treaty termsWhenWinning(MinecraftServer server, War war, Nation self, Nation loser) {
        return null;
    }

    /** What becomes of {@code town}, a town of {@code self} that fell in a war {@code self} lost to {@code winner}. */
    default FallenTown onFallen(MinecraftServer server, War war, Nation self, Town town, Nation winner) {
        return FallenTown.RAZE;
    }
}
