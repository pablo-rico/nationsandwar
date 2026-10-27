package org.papiricoh.nationsandwar.api.event;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.Event;

/**
 * Base of every Nations &amp; War event, posted on {@code NeoForge.EVENT_BUS}. Apart from {@link WarEvent.Declare} (posted
 * before the war exists, so it can be cancelled) they are queued while the change happens and delivered at the end of the
 * server tick, so a listener may change politics or wars without upsetting the change that raised the event.
 */
public abstract class NWEvent extends Event {
    private final MinecraftServer server;

    protected NWEvent(MinecraftServer server) {
        this.server = server;
    }

    public MinecraftServer server() {
        return server;
    }
}
