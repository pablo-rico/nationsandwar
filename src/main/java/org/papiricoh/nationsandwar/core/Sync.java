package org.papiricoh.nationsandwar.core;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.papiricoh.nationsandwar.NationsAndWar;
import org.papiricoh.nationsandwar.data.NWSavedData;
import org.papiricoh.nationsandwar.model.Flag;
import org.papiricoh.nationsandwar.model.Nation;
import org.papiricoh.nationsandwar.model.Politics;
import org.papiricoh.nationsandwar.model.Town;
import org.papiricoh.nationsandwar.network.BriefsPayload;
import org.papiricoh.nationsandwar.network.ClaimsPayload;
import org.papiricoh.nationsandwar.network.RateLimiter;
import org.papiricoh.nationsandwar.network.ViewPayload;
import org.papiricoh.nationsandwar.view.Briefs;
import org.papiricoh.nationsandwar.war.War;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps clients up to date: the claim map (full on login, then differences), the public briefs, and the detailed view of
 * every player who has the management GUI open. Changes are gathered during a tick and sent once at its end.
 */
@EventBusSubscriber(modid = NationsAndWar.MODID)
public final class Sync {
    private static boolean dirty;
    /** The claim index as clients last saw it. */
    private static final Map<String, Map<Long, UUID>> SENT = new HashMap<>();
    /** Players with the GUI open (they asked for a view and have not closed it). */
    private static final Set<UUID> VIEWERS = new HashSet<>();
    /** The membership each online player was last told ("town|nation"). */
    private static final Map<UUID, String> SELF = new HashMap<>();

    private Sync() {
    }

    public static void markDirty() {
        dirty = true;
    }

    public static void watch(ServerPlayer p, boolean open) {
        if (open) VIEWERS.add(p.getUUID());
        else VIEWERS.remove(p.getUUID());
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        MinecraftServer server = p.getServer();
        Politics pol = NW.pol(server);
        if (SENT.isEmpty()) snapshot(pol);
        for (var level : server.getAllLevels()) {
            String dim = NW.dim(level);
            Map<Long, UUID> m = pol.claimsIn(dim);
            PacketDistributor.sendToPlayer(p, claims(dim, true, m));
        }
        PacketDistributor.sendToPlayer(p, new BriefsPayload(briefs(server).write()));
        SELF.remove(p.getUUID());
        sendSelf(p, pol);
    }

    private static void sendSelf(ServerPlayer p, Politics pol) {
        Town t = pol.townOf(p.getUUID());
        String key = (t == null ? "" : t.id) + "|" + (t == null || t.nation == null ? "" : t.nation);
        if (key.equals(SELF.get(p.getUUID()))) return;
        SELF.put(p.getUUID(), key);
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        if (t != null) tag.putUUID("town", t.id);
        if (t != null && t.nation != null) tag.putUUID("nation", t.nation);
        PacketDistributor.sendToPlayer(p, new org.papiricoh.nationsandwar.network.SelfPayload(tag));
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        VIEWERS.remove(event.getEntity().getUUID());
        SELF.remove(event.getEntity().getUUID());
        RateLimiter.forget(event.getEntity().getUUID());
        dirty = true;
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        SENT.clear();
        VIEWERS.clear();
        SELF.clear();
        dirty = false;
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (!dirty) return;
        dirty = false;
        MinecraftServer server = event.getServer();
        Politics pol = NW.pol(server);
        Set<String> dims = new HashSet<>(SENT.keySet());
        for (var level : server.getAllLevels()) dims.add(NW.dim(level));
        for (String dim : dims) {
            Map<Long, UUID> now = pol.claimsIn(dim), before = SENT.getOrDefault(dim, Map.of());
            Map<Long, UUID> delta = new HashMap<>();
            now.forEach((c, t) -> {
                if (!t.equals(before.get(c))) delta.put(c, t);
            });
            before.forEach((c, t) -> {
                if (!now.containsKey(c)) delta.put(c, null);
            });
            if (!delta.isEmpty()) PacketDistributor.sendToAllPlayers(claims(dim, false, delta));
        }
        snapshot(pol);
        PacketDistributor.sendToAllPlayers(new BriefsPayload(briefs(server).write()));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) sendSelf(p, pol);
        for (UUID v : new ArrayList<>(VIEWERS)) {
            ServerPlayer p = server.getPlayerList().getPlayer(v);
            if (p == null) VIEWERS.remove(v);
            else sendView(p);
        }
    }

    public static void sendView(ServerPlayer p) {
        PacketDistributor.sendToPlayer(p, new ViewPayload(ViewBuilder.build(p).write()));
    }

    private static void snapshot(Politics pol) {
        SENT.clear();
        for (Town t : pol.towns.values()) {
            Map<Long, UUID> m = SENT.computeIfAbsent(t.dimension, k -> new HashMap<>());
            for (long c : t.claims) m.put(c, t.id);
        }
    }

    private static ClaimsPayload claims(String dim, boolean full, Map<Long, UUID> m) {
        long[] chunks = new long[m.size()];
        UUID[] towns = new UUID[m.size()];
        int i = 0;
        for (var e : m.entrySet()) {
            chunks[i] = e.getKey();
            towns[i++] = e.getValue();
        }
        return new ClaimsPayload(dim, full, chunks, towns);
    }

    public static Briefs briefs(MinecraftServer server) {
        NWSavedData data = NW.data(server);
        Politics pol = data.politics;
        List<Briefs.TownBrief> towns = new ArrayList<>();
        for (Town t : pol.towns.values()) {
            int flags = 0;
            for (Flag f : Flag.values()) if (t.flags.getOrDefault(f, f.defaultOn)) flags |= 1 << f.ordinal();
            towns.add(new Briefs.TownBrief(t.id, t.name, t.color, t.nation, t.province, flags, t.npc, NW.name(server, t.mayor()),
                    t.managedBy == null ? "" : t.managedBy));
        }
        List<Briefs.ProvinceBrief> provinces = new ArrayList<>();
        pol.provinces.values().forEach(p -> provinces.add(new Briefs.ProvinceBrief(p.id, p.name, p.nation)));
        List<Briefs.NationBrief> nations = new ArrayList<>();
        for (Nation n : pol.nations.values()) {
            nations.add(new Briefs.NationBrief(n.id, n.name, n.color, n.government.id(), n.alliance, n.capital, n.npc, NW.name(server, n.leader()),
                    n.managedBy == null ? "" : n.managedBy, n.titleKey()));
        }
        List<Briefs.AllianceBrief> alliances = new ArrayList<>();
        pol.alliances.values().forEach(a -> alliances.add(new Briefs.AllianceBrief(a.id, a.name, a.color)));
        List<Briefs.WarBrief> wars = new ArrayList<>();
        for (War w : data.wars.values()) {
            if (!w.live()) continue;
            List<UUID> occTowns = new ArrayList<>();
            List<long[]> occChunks = new ArrayList<>();
            w.occupied.forEach((town, chunks) -> {
                occTowns.add(town);
                occChunks.add(chunks.stream().mapToLong(Long::longValue).toArray());
            });
            wars.add(new Briefs.WarBrief(w.id, List.copyOf(w.attackers), List.copyOf(w.defenders), w.phase.name(), occTowns, occChunks,
                    List.copyOf(w.coreOpen)));
        }
        return new Briefs(towns, provinces, nations, alliances, wars);
    }
}
