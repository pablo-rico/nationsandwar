package org.papiricoh.nationsandwar.client.notice;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.papiricoh.nationsandwar.NationsAndWar;
import org.papiricoh.nationsandwar.client.ClientClaims;
import org.papiricoh.nationsandwar.client.gui.Draw;
import org.papiricoh.nationsandwar.model.Flag;
import org.papiricoh.nationsandwar.network.NoticePayload;
import org.papiricoh.nationsandwar.view.Briefs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Watches which territory the player stands in (from {@link ClientClaims#townAt}) and announces changes as TERRITORY
 * notices, naming only the level that changed: wilderness, a town, a new province or a new nation, or "Welcome home" for
 * the player's own town. Cards carry the town's PvP state and an "at war" or "ally" badge. Debounced: a new place must
 * hold for two samples ({@link #SAMPLE} ticks apart), and coming back to a place left less than {@link #REPEAT_MS} ago
 * changes nothing on screen, so walking along a border stays quiet.
 */
@EventBusSubscriber(modid = NationsAndWar.MODID, value = Dist.CLIENT)
public final class TerritoryWatcher {
    /** Where the player stands; all null in the wilderness. */
    private record Place(UUID town, UUID province, UUID nation) {
        static final Place WILD = new Place(null, null, null);
    }

    private static final int SAMPLE = 5, WILD_COLOR = 0x7C8A6A;
    private static final long REPEAT_MS = 3000;
    /** Place → when the player was last in it. */
    private static final Map<Place, Long> SEEN = new HashMap<>();
    private static Place current, candidate;
    private static int clock, stable;

    private TerritoryWatcher() {
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            current = null;
            return;
        }
        if (++clock % SAMPLE != 0) return;
        long now = Util.getMillis();
        Briefs.TownBrief t = ClientClaims.townAt(mc.level.dimension().location().toString(), mc.player.chunkPosition().x, mc.player.chunkPosition().z);
        Place here = t == null ? Place.WILD : new Place(t.id(), t.province(), t.nation());
        if (current == null) current = Place.WILD;
        SEEN.put(current, now);
        if (here.equals(current)) {
            candidate = null;
            return;
        }
        if (!here.equals(candidate)) {
            candidate = here;
            stable = 0;
        }
        if (++stable < 2) return;
        Place from = current;
        current = here;
        candidate = null;
        Long seen = SEEN.get(here);
        if (SEEN.size() > 64) SEEN.values().removeIf(v -> now - v > REPEAT_MS);
        if (seen != null && now - seen < REPEAT_MS) return;
        announce(from, here);
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        current = candidate = null;
        SEEN.clear();
        NoticeHud.clear();
    }

    private static void announce(Place from, Place to) {
        Briefs.TownBrief t = ClientClaims.town(to.town());
        if (t == null) {
            Briefs.TownBrief old = ClientClaims.town(from.town());
            push(tr("wilderness"), old == null ? Component.empty() : tr("left", old.name()), WILD_COLOR, "wild", List.of(), null);
            return;
        }
        Briefs.NationBrief n = ClientClaims.nation(t.nation());
        Briefs.ProvinceBrief p = ClientClaims.province(t.province());
        UUID mine = ClientClaims.myNation();
        boolean enemy = ClientClaims.enemies(mine, t.nation()), ally = !enemy && mine != null && !mine.equals(t.nation()) && ClientClaims.allied(mine, t.nation());
        boolean pvp = enemy || (t.flags() & 1 << Flag.PVP.ordinal()) != 0;
        List<NoticeHud.Badge> badges = new ArrayList<>();
        if (enemy) badges.add(new NoticeHud.Badge(tr("badge.war"), Draw.BAD & 0xFFFFFF));
        else if (ally) badges.add(new NoticeHud.Badge(tr("badge.ally"), Draw.GOOD & 0xFFFFFF));
        if (n != null && n.managed()) badges.add(new NoticeHud.Badge(tr("badge.managed"), Draw.MANAGED & 0xFFFFFF));
        badges.add(pvp ? new NoticeHud.Badge(tr("badge.pvp"), Draw.WARN & 0xFFFFFF) : new NoticeHud.Badge(tr("badge.safe"), Draw.MUTED & 0xFFFFFF));
        SoundEvent sound = enemy ? SoundEvents.NOTE_BLOCK_BASS.value() : null;
        String townIcon = enemy ? "enemy" : ally ? "ally" : "town";
        if (t.id().equals(ClientClaims.myTown())) {
            push(tr("welcome_home"), join(Component.literal(t.name()), p == null ? null : Component.literal(p.name())), t.color(), "home", badges, null);
        } else if (from.town() != null && !Objects.equals(from.nation(), to.nation()) && n != null) {
            push(nationName(n), join(Component.literal(t.name()), p == null ? null : Component.literal(p.name())), n.color(), enemy ? "enemy" : "nation", badges, sound);
        } else if (from.town() != null && Objects.equals(from.nation(), to.nation()) && !Objects.equals(from.province(), to.province()) && p != null) {
            Component of = n == null ? null : tr("province_of", n.name());
            push(Component.literal(p.name()), join(of, Component.literal(t.name())), n != null ? n.color() : t.color(), "province", badges, sound);
        } else {
            Component sub = n == null ? tr("independent") : join(nationName(n), p == null ? null : Component.literal(p.name()));
            push(Component.literal(t.name()), sub, t.color(), townIcon, badges, sound);
        }
    }

    private static void push(Component title, Component subtitle, int color, String icon, List<NoticeHud.Badge> badges, SoundEvent sound) {
        NoticeHud.push(NoticePayload.Kind.TERRITORY, title, subtitle, color, icon, badges, sound);
    }

    /** "Kingdom of Valoria". */
    private static Component nationName(Briefs.NationBrief n) {
        return tr("nation_of", Component.translatable(ClientClaims.titles(n.id(), n.government())), n.name());
    }

    /** "a · b", or whichever is present. */
    private static Component join(Component a, Component b) {
        if (a == null) return b == null ? Component.empty() : b;
        if (b == null) return a;
        return Component.empty().append(a).append(" · ").append(b);
    }

    private static MutableComponent tr(String key, Object... args) {
        return Component.translatable("nationsandwar.notice." + key, args);
    }
}
