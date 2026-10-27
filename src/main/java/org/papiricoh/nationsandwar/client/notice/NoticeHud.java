package org.papiricoh.nationsandwar.client.notice;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.fml.util.ObfuscationReflectionHelper;
import org.papiricoh.nationsandwar.NWClientConfig;
import org.papiricoh.nationsandwar.NationsAndWar;
import org.papiricoh.nationsandwar.client.gui.Draw;
import org.papiricoh.nationsandwar.network.NoticePayload;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * The top-right notices: a stack of cards that slide in from the screen edge and fade out, each with a hanging banner in
 * the entity's colour, an icon, a title, a subtitle and optional {@link Badge}s. War outranks politics, politics
 * territory: at most {@link #MAX_CARDS} cards of one shared width show (highest priority on top) and a waiting card of higher priority
 * pushes the weakest one out; permission notices are a single compact line under them. Only the newest territory notice
 * is kept, and a repeated notice refreshes the card already showing. The stack sits below vanilla toasts, hides with F1
 * and the debug screen, and the live {@link SiegePanel} is drawn under it.
 */
public final class NoticeHud {
    /** A small tag on the right of a card's title ("AT WAR", "ALLY", "PvP"); {@code color} is 0xRRGGBB. */
    public record Badge(Component text, int color) {
    }

    private static final class Card {
        final NoticePayload.Kind kind;
        final Component title, subtitle;
        final int color;
        final ResourceLocation icon;
        final List<Badge> badges;
        final SoundEvent sound;
        long shownAt, closeAt;
        int width;
        float y = Float.NaN;

        Card(NoticePayload.Kind kind, Component title, Component subtitle, int color, String icon, List<Badge> badges, SoundEvent sound) {
            this.kind = kind;
            this.title = title;
            this.subtitle = subtitle;
            this.color = Draw.opaque(color);
            this.icon = icon(icon);
            this.badges = List.copyOf(badges);
            this.sound = sound;
        }

        boolean compact() {
            return kind == NoticePayload.Kind.PERMISSION;
        }

        boolean same(Card o) {
            return kind == o.kind && title.equals(o.title) && subtitle.equals(o.subtitle);
        }

        void close(long now) {
            closeAt = Math.min(closeAt, now);
        }
    }

    /** Sprites under {@code textures/gui/notice/} (made by {@code tools/artgen/noticeart.py}); unknown names show a flag. */
    private static final Set<String> ICONS = Set.of("war", "siege", "crown", "scroll", "shield", "skull", "handshake", "flag", "pvp",
            "home", "wild", "town", "province", "nation", "ally", "enemy");
    private static final int MAX_CARDS = 3, MARGIN = 6, GAP = 5, CARD_H = 28, LINE_H = 15, TEXT_X = 37, TOAST_SLOT = 32;
    private static final long IN_MS = 280, OUT_MS = 420;
    private static final List<Card> SHOWN = new ArrayList<>(), WAITING = new ArrayList<>();
    private static long lastFrame;
    private static float toastY, stackWidth;
    private static Field toastSlots;
    private static boolean toastSlotsLooked;

    private NoticeHud() {
    }

    public static void push(NoticePayload.Kind kind, Component title, Component subtitle, int color, String icon) {
        push(kind, title, subtitle, color, icon, List.of(), null);
    }

    /** Queues a notice; {@code sound} null plays the kind's own. */
    public static void push(NoticePayload.Kind kind, Component title, Component subtitle, int color, String icon, List<Badge> badges, SoundEvent sound) {
        if (!enabled(kind)) return;
        Card card = new Card(kind, title, subtitle == null ? Component.empty() : subtitle, color, icon, badges, sound);
        long now = Util.getMillis();
        for (Card c : SHOWN)
            if (c.closeAt > now && c.same(card)) {
                c.closeAt = now + duration(kind);
                return;
            }
        if (kind == NoticePayload.Kind.TERRITORY) {
            WAITING.removeIf(c -> c.kind == NoticePayload.Kind.TERRITORY);
            for (Card c : SHOWN) if (c.kind == NoticePayload.Kind.TERRITORY) c.close(now);
        }
        WAITING.removeIf(c -> c.same(card));
        WAITING.add(card);
        WAITING.sort(Comparator.comparingInt((Card c) -> -priority(c.kind)));
    }

    public static void clear() {
        SHOWN.clear();
        WAITING.clear();
    }

    private static boolean enabled(NoticePayload.Kind kind) {
        if (!NWClientConfig.get(NWClientConfig.NOTICES)) return false;
        return NWClientConfig.get(switch (kind) {
            case TERRITORY -> NWClientConfig.NOTICE_TERRITORY;
            case WAR -> NWClientConfig.NOTICE_WAR;
            case POLITICS -> NWClientConfig.NOTICE_POLITICS;
            case PERMISSION -> NWClientConfig.NOTICE_PERMISSION;
        });
    }

    private static int priority(NoticePayload.Kind kind) {
        return switch (kind) {
            case WAR -> 3;
            case POLITICS -> 2;
            case TERRITORY -> 1;
            case PERMISSION -> 0;
        };
    }

    private static long duration(NoticePayload.Kind kind) {
        return switch (kind) {
            case WAR -> 7000;
            case POLITICS -> 6000;
            case TERRITORY -> 4500;
            case PERMISSION -> 2500;
        };
    }

    static ResourceLocation icon(String name) {
        return NationsAndWar.id("textures/gui/notice/" + (name != null && ICONS.contains(name) ? name : "flag") + ".png");
    }

    // --- queue -------------------------------------------------------------------------------------------------------------

    /** Drops finished cards and moves waiting ones in, pushing out a weaker card when the stack is full. */
    private static void update(long now) {
        SHOWN.removeIf(c -> now >= c.closeAt + OUT_MS);
        boolean bumped = false;
        for (Iterator<Card> it = WAITING.iterator(); it.hasNext(); ) {
            Card w = it.next();
            int used = 0;
            boolean leaving = false;
            Card weakest = null;
            for (Card c : SHOWN) {
                if (c.compact() != w.compact()) continue;
                used++;
                if (c.closeAt <= now) leaving = true;
                else if (priority(c.kind) < priority(w.kind) && (weakest == null || priority(c.kind) < priority(weakest.kind))) weakest = c;
            }
            if (used < (w.compact() ? 1 : MAX_CARDS)) {
                it.remove();
                w.shownAt = now;
                w.closeAt = now + duration(w.kind);
                SHOWN.add(w);
                sound(w);
            } else if (w.compact() && !leaving) {
                for (Card c : SHOWN) if (c.compact()) c.close(now);
            } else if (!w.compact() && !leaving && !bumped && weakest != null) {
                weakest.close(now);
                bumped = true;
            }
        }
    }

    private static void sound(Card c) {
        if (!NWClientConfig.get(NWClientConfig.NOTICE_SOUNDS)) return;
        float volume = (float) NWClientConfig.get(NWClientConfig.NOTICE_VOLUME);
        if (volume <= 0) return;
        SoundEvent s = c.sound;
        float pitch = 1f, gain = 1f;
        if (s == null) switch (c.kind) {
            case WAR -> {
                s = SoundEvents.BELL_BLOCK;
                pitch = 0.75f;
                gain = 0.7f;
            }
            case POLITICS -> {
                s = SoundEvents.NOTE_BLOCK_CHIME.value();
                pitch = 1.2f;
                gain = 0.5f;
            }
            case TERRITORY -> s = SoundEvents.AMETHYST_BLOCK_CHIME;
            case PERMISSION -> {
                s = SoundEvents.NOTE_BLOCK_HAT.value();
                pitch = 0.7f;
                gain = 0.35f;
            }
        }
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, volume * gain));
    }

    /** How far down the vanilla toasts reach (their occupied slots are private, so read by reflection; 0 if that fails). */
    /** Whether this frame lays the stack out at the top centre ({@code notices.topCenter}). */
    private static boolean centred;
    private static Field bossEvents;
    private static boolean bossEventsLooked;

    /** Where the boss bars end (vanilla draws them from y 12, 19 apart), so centred notices sit under them. */
    private static int bossBottom(Minecraft mc) {
        if (!bossEventsLooked) {
            bossEventsLooked = true;
            try {
                bossEvents = ObfuscationReflectionHelper.findField(net.minecraft.client.gui.components.BossHealthOverlay.class, "events");
            } catch (RuntimeException e) {
                NationsAndWar.LOGGER.warn("Notices cannot see the boss bars; they may overlap", e);
            }
        }
        if (bossEvents == null) return 0;
        try {
            int n = ((java.util.Map<?, ?>) bossEvents.get(mc.gui.getBossOverlay())).size();
            return n == 0 ? 0 : 12 + 19 * n;
        } catch (IllegalAccessException | RuntimeException e) {
            return 0;
        }
    }

    /** GUI height kept free at the top-right by a map's minimap (set by the map compat; 0 = none). */
    private static int reservedTop;

    public static void reserveTop(int guiY) {
        reservedTop = Math.max(0, guiY);
    }

    private static int toastBottom(Minecraft mc) {
        return Math.max(reservedTop, toastsBottom(mc));
    }

    private static int toastsBottom(Minecraft mc) {
        if (!toastSlotsLooked) {
            toastSlotsLooked = true;
            try {
                toastSlots = ObfuscationReflectionHelper.findField(ToastComponent.class, "occupiedSlots");
            } catch (RuntimeException e) {
                NationsAndWar.LOGGER.warn("Notices cannot see the toasts; they may overlap", e);
            }
        }
        if (toastSlots == null) return 0;
        try {
            return ((BitSet) toastSlots.get(mc.getToasts())).length() * TOAST_SLOT;
        } catch (IllegalAccessException | RuntimeException e) {
            toastSlots = null;
            return 0;
        }
    }

    // --- drawing -----------------------------------------------------------------------------------------------------------

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        long now = Util.getMillis();
        float dt = Math.min(100, now - lastFrame);
        lastFrame = now;
        update(now);
        if (mc.options.hideGui || mc.getDebugOverlay().showDebugScreen() || mc.player == null) return;
        float scale = (float) NWClientConfig.get(NWClientConfig.NOTICE_SCALE), ease = 1 - (float) Math.exp(-dt / 70f);
        boolean centre = NWClientConfig.get(NWClientConfig.NOTICE_TOP_CENTER);
        // Centred cards still drop below the toasts when they are wide enough to reach the toasts' column (160 wide).
        boolean underToasts = !centre || (g.guiWidth() / scale + Math.max(stackWidth, SiegePanel.W)) / 2 > (g.guiWidth() - 160) / scale - 4;
        toastY += ((centre ? Math.max(bossBottom(mc), underToasts ? toastsBottom(mc) : 0) : toastBottom(mc)) - toastY) * ease;
        var pose = g.pose();
        pose.pushPose();
        pose.scale(scale, scale, 1);
        float screenW = g.guiWidth() / scale, right = screenW - MARGIN, y = toastY / scale + MARGIN;
        int widest = 0;
        for (Card c : SHOWN) {
            if (c.width == 0) c.width = width(mc.font, c);
            if (!c.compact()) widest = Math.max(widest, c.width);
        }
        stackWidth = stackWidth == 0 ? widest : stackWidth + (widest - stackWidth) * ease;
        centred = centre;
        if (centre) right = (screenW + Math.max(stackWidth, SiegePanel.W)) / 2;
        List<Card> order = new ArrayList<>(SHOWN);
        order.sort(Comparator.comparing(Card::compact).thenComparingInt(c -> -priority(c.kind)).thenComparingLong(c -> c.shownAt));
        for (Card c : order) {
            c.y = Float.isNaN(c.y) ? y : c.y + (y - c.y) * ease;
            y += ((c.compact() ? LINE_H : CARD_H) + GAP) * card(g, mc.font, c, right, now);
        }
        SiegePanel.render(g, mc, centred ? screenW / 2 + SiegePanel.W / 2f : right, y, dt, now);
        pose.popPose();
    }

    /** Draws one card and returns how much of its slot it still holds (shrinks to 0 while it fades out). */
    private static float card(GuiGraphics g, Font font, Card c, float right, long now) {
        float in = ease(Math.min(1, (now - c.shownAt) / (float) IN_MS));
        float out = now > c.closeAt ? Math.min(1, (now - c.closeAt) / (float) OUT_MS) : 0;
        float alpha = in * (1 - out), slot = 1 - out * out * (3 - 2 * out);
        if (alpha < 0.03f) return slot;
        int w = c.compact() ? c.width : Math.max(c.width, Math.round(stackWidth));
        float x, y = c.y;
        if (centred) {
            x = right - Math.max(stackWidth, SiegePanel.W) / 2 - w / 2f;
            y -= (1 - in) * 14 + out * 10;
        } else {
            x = right - w + (1 - in) * (w + MARGIN + 12) + out * 28;
        }
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        float left = 1 - Math.min(1, (now - c.shownAt) / (float) Math.max(1, c.closeAt - c.shownAt));
        if (c.compact()) compact(g, font, c, w, alpha);
        else full(g, font, c, w, alpha, left, now);
        pose.popPose();
        return slot;
    }

    private static void full(GuiGraphics g, Font font, Card c, int w, float a, float left, long now) {
        int h = CARD_H, col = c.color;
        g.fill(-1, 2, w + 1, h + 3, fade(0x50000000, a));
        g.fillGradient(0, 0, w, h, fade(Draw.alpha(Draw.PANEL_HI, 0xF0), a), fade(Draw.alpha(Draw.PANEL, 0xF0), a));
        Draw.frame(g, 0, 0, w, h, fade(0xFF0B0E12, a));
        g.fill(1, 1, w - 1, 2, fade(Draw.mix(Draw.PANEL_HI, 0xFFFFFFFF, 0.12f), a));
        if (c.kind == NoticePayload.Kind.WAR) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(now / 200.0);
            Draw.frame(g, 1, 1, w - 2, h - 2, fade(Draw.alpha(Draw.BAD, (int) (70 + 90 * pulse)), a));
        }
        banner(g, 4, h, col, a);
        int ix = 14, iy = (h - 20) / 2;
        g.fill(ix, iy, ix + 20, iy + 20, fade(Draw.mix(Draw.INSET, col, 0.22f), a));
        Draw.frame(g, ix, iy, 20, 20, fade(Draw.mix(col, 0xFF000000, 0.45f), a));
        icon(g, c.icon, ix + 2, iy + 2, 16, a);
        int avail = w - TEXT_X - 6, bx = w - 5;
        for (int i = c.badges.size() - 1; i >= 0; i--) bx -= badge(g, font, c.badges.get(i), bx, 4, a) + 2;
        int titleColor = c.kind == NoticePayload.Kind.WAR ? Draw.GOLD : Draw.TEXT;
        g.drawString(font, Draw.fit(font, c.title, bx - TEXT_X - 2), TEXT_X, 5, fade(titleColor, a), false);
        g.drawString(font, Draw.fit(font, c.subtitle, avail), TEXT_X, 16, fade(Draw.MUTED, a), false);
        g.fill(TEXT_X, h - 2, TEXT_X + Math.round((w - TEXT_X - 2) * left), h - 1, fade(Draw.alpha(col, 0x90), a));
    }

    /** The hanging banner on a card's left edge: a gold rod, a cloth in the entity's colour and a swallowtail. */
    private static void banner(GuiGraphics g, int x, int h, int col, float a) {
        int lo = fade(Draw.shade(col, 0.72f), a);
        Draw.quad(g, x, h, x + 3, h, x + 3, h + 1.5f, x, h + 4, lo);
        Draw.quad(g, x + 3, h, x + 6, h, x + 6, h + 4, x + 3, h + 1.5f, lo);
        g.fillGradient(x, -1, x + 6, h, fade(Draw.mix(col, 0xFFFFFFFF, 0.3f), a), lo);
        g.fill(x + 1, -1, x + 2, h, fade(0x30FFFFFF, a));
        g.fill(x - 1, -3, x + 7, -1, fade(Draw.shade(Draw.GOLD, 0.85f), a));
    }

    private static void compact(GuiGraphics g, Font font, Card c, int w, float a) {
        int h = LINE_H;
        g.fill(0, 0, w, h, fade(0xC00E1116, a));
        Draw.frame(g, 0, 0, w, h, fade(0x90000000, a));
        g.fill(0, 0, 2, h, fade(c.color, a));
        icon(g, c.icon, 5, 2, 11, a);
        Component text = Component.empty().append(c.title).append(Component.literal("  ")).append(c.subtitle.copy().withColor(Draw.MUTED & 0xFFFFFF));
        g.drawString(font, Draw.fit(font, text, w - 25), 20, 4, fade(0xFFD6D0C2, a), false);
    }

    /** A badge right-aligned at {@code right}; returns its width. */
    private static int badge(GuiGraphics g, Font font, Badge b, int right, int y, float a) {
        int col = Draw.opaque(b.color()), w = font.width(b.text()) + 6, x = right - w;
        g.fill(x, y, right, y + 11, fade(Draw.alpha(col, 0x38), a));
        Draw.frame(g, x, y, w, 11, fade(Draw.alpha(col, 0xC0), a));
        g.drawString(font, b.text(), x + 3, y + 2, fade(Draw.mix(col, 0xFFFFFFFF, 0.35f), a), false);
        return w;
    }

    private static int width(Font font, Card c) {
        if (c.compact()) return Math.clamp(25 + font.width(c.title) + font.width("  ") + font.width(c.subtitle), 80, 240);
        int badges = 0;
        for (Badge b : c.badges) badges += font.width(b.text()) + 8;
        int text = Math.max(font.width(c.title) + badges + 4, font.width(c.subtitle));
        return Math.clamp(TEXT_X + text + 8, 150, 240);
    }

    /** A notice sprite at a size, faded. */
    static void icon(GuiGraphics g, ResourceLocation icon, float x, float y, float size, float a) {
        g.flush();
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(size / 16f, size / 16f, 1);
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1, 1, 1, a);
        g.blit(icon, 0, 0, 0, 0, 16, 16, 16, 16);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
        pose.popPose();
    }

    /** A colour with its alpha multiplied by {@code a} (kept above the font's "no alpha means opaque" threshold). */
    static int fade(int color, float a) {
        return Math.max(5, Math.round((color >>> 24) * a)) << 24 | (color & 0xFFFFFF);
    }

    static float ease(float t) {
        float u = 1 - t;
        return 1 - u * u * u;
    }
}
