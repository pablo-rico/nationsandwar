package org.papiricoh.nationsandwar.core;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.papiricoh.nationsandwar.NationsAndWar;
import org.papiricoh.nationsandwar.api.event.AllianceEvent;
import org.papiricoh.nationsandwar.api.event.NWEvent;
import org.papiricoh.nationsandwar.api.event.NationEvent;
import org.papiricoh.nationsandwar.api.event.TownEvent;
import org.papiricoh.nationsandwar.model.Alliance;
import org.papiricoh.nationsandwar.model.Nation;
import org.papiricoh.nationsandwar.model.PoliticsObserver;
import org.papiricoh.nationsandwar.model.Town;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/**
 * Turns the changes {@link org.papiricoh.nationsandwar.model.Politics} reports into the API's events and delivers them at
 * the end of the server tick (after this mod's own ticking), in the order they happened. Listeners may change politics:
 * what they raise is delivered in the same flush.
 */
@EventBusSubscriber(modid = NationsAndWar.MODID)
public final class ApiEvents {
    private static final Deque<NWEvent> QUEUE = new ArrayDeque<>();
    /** Events delivered per flush at most (a listener that keeps raising events cannot hang the tick). */
    private static final int FLUSH_LIMIT = 4096;
    private static Observer observer;

    private ApiEvents() {
    }

    /** The observer for {@code server}'s politics. */
    public static PoliticsObserver observer(MinecraftServer server) {
        if (observer == null || observer.server != server) observer = new Observer(server);
        return observer;
    }

    public static void queue(NWEvent event) {
        QUEUE.add(event);
    }

    /** Delivers everything queued (also called by {@code NationsAndWarApi.flushEvents}). */
    public static void flush() {
        for (int i = 0; i < FLUSH_LIMIT && !QUEUE.isEmpty(); i++) NeoForge.EVENT_BUS.post(QUEUE.poll());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(ServerTickEvent.Post event) {
        if (!QUEUE.isEmpty()) flush();
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        QUEUE.clear();
        observer = null;
    }

    private record Observer(MinecraftServer server) implements PoliticsObserver {
        @Override
        public void townFounded(Town town) {
            queue(new TownEvent.Founded(server, town));
        }

        @Override
        public void townRemoved(Town town) {
            queue(new TownEvent.Removed(server, town));
        }

        @Override
        public void residentJoined(Town town, UUID player) {
            queue(new TownEvent.ResidentJoined(server, town, player));
        }

        @Override
        public void residentLeft(Town town, UUID player) {
            queue(new TownEvent.ResidentLeft(server, town, player));
        }

        @Override
        public void claimed(Town town, long chunk) {
            queue(new TownEvent.Claimed(server, town, chunk));
        }

        @Override
        public void unclaimed(Town town, long chunk) {
            queue(new TownEvent.Unclaimed(server, town, chunk));
        }

        @Override
        public void chunkTransferred(Town from, Town to, long chunk) {
            queue(new TownEvent.ChunkTransferred(server, from, to, chunk));
        }

        @Override
        public void nationFounded(Nation nation) {
            queue(new NationEvent.Founded(server, nation));
        }

        @Override
        public void nationRemoved(Nation nation) {
            queue(new NationEvent.Removed(server, nation));
        }

        @Override
        public void townJoinedNation(Nation nation, Town town) {
            queue(new NationEvent.TownJoined(server, nation, town));
        }

        @Override
        public void townLeftNation(Nation nation, Town town) {
            queue(new NationEvent.TownLeft(server, nation, town));
        }

        @Override
        public void leaderChanged(Nation nation, UUID before, UUID after) {
            queue(new NationEvent.LeaderChanged(server, nation, before, after));
        }

        @Override
        public void renamed(Object owner, String before) {
            if (owner instanceof Town t) queue(new TownEvent.Renamed(server, t, before));
            else if (owner instanceof Nation n) queue(new NationEvent.Renamed(server, n, before));
        }

        @Override
        public void allianceJoined(Alliance alliance, Nation nation) {
            queue(new AllianceEvent.Joined(server, alliance, nation));
        }

        @Override
        public void allianceLeft(Alliance alliance, Nation nation) {
            queue(new AllianceEvent.Left(server, alliance, nation));
        }
    }
}
