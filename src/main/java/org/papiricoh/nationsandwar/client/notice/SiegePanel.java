package org.papiricoh.nationsandwar.client.notice;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import org.papiricoh.nationsandwar.NWClientConfig;
import org.papiricoh.nationsandwar.client.ClientClaims;
import org.papiricoh.nationsandwar.client.gui.Draw;
import org.papiricoh.nationsandwar.view.Briefs;
import org.papiricoh.nationsandwar.view.SiegeBrief;

/**
 * The live siege indicator under the notices: while the player stands within {@link #RANGE} chunks of a besieged chunk,
 * a compact panel shows the besieged town, the control bar (defender's colour from the left, besieger's from the right,
 * as in the war tab) with the capture percentage, and both nations' names. It fades in and out with the player's
 * distance and pulses red once the chunk is about to fall.
 */
final class SiegePanel {
    static final int RANGE = 2, W = 156, H = 35;
    private static final ResourceLocation ICON = NoticeHud.icon("siege");
    private static SiegeBrief last;
    private static float shown;

    private SiegePanel() {
    }

    static void render(GuiGraphics g, Minecraft mc, float right, float y, float dt, long now) {
        SiegeBrief s = NWClientConfig.get(NWClientConfig.NOTICE_SIEGE_PANEL) ? nearest(mc) : null;
        if (s != null) last = s;
        shown = Math.clamp(shown + (s != null ? dt : -dt) / 250f, 0, 1);
        if (shown <= 0.03f || last == null) return;
        float a = NoticeHud.ease(shown);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(right - W + (1 - a) * 24, y, 0);
        draw(g, mc, last, a, now);
        pose.popPose();
    }

    private static SiegeBrief nearest(Minecraft mc) {
        if (mc.level == null || mc.player == null) return null;
        String dim = mc.level.dimension().location().toString();
        ChunkPos at = mc.player.chunkPosition();
        SiegeBrief best = null;
        int bestDist = Integer.MAX_VALUE;
        for (SiegeBrief s : ClientClaims.sieges()) {
            if (!s.dimension().equals(dim)) continue;
            int d = Math.max(Math.abs(ChunkPos.getX(s.chunk()) - at.x), Math.abs(ChunkPos.getZ(s.chunk()) - at.z));
            if (d <= RANGE && d < bestDist) {
                best = s;
                bestDist = d;
            }
        }
        return best;
    }

    private static void draw(GuiGraphics g, Minecraft mc, SiegeBrief s, float a, long now) {
        var font = mc.font;
        int defender = Draw.opaque(s.defenderColor()), besieger = Draw.opaque(s.besiegerColor());
        g.fill(-1, 2, W + 1, H + 3, NoticeHud.fade(0x50000000, a));
        g.fillGradient(0, 0, W, H, NoticeHud.fade(Draw.alpha(Draw.PANEL_HI, 0xF0), a), NoticeHud.fade(Draw.alpha(Draw.PANEL, 0xF0), a));
        Draw.frame(g, 0, 0, W, H, NoticeHud.fade(0xFF0B0E12, a));
        g.fill(1, 0, W / 2, 2, NoticeHud.fade(defender, a));
        g.fill(W / 2, 0, W - 1, 2, NoticeHud.fade(besieger, a));
        if (s.control() > 0.5f) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(now / 160.0);
            Draw.frame(g, 1, 1, W - 2, H - 2, NoticeHud.fade(Draw.alpha(Draw.BAD, (int) (60 + 120 * pulse)), a));
        }
        NoticeHud.icon(g, ICON, 5, 4, 16, a);
        Briefs.TownBrief town = ClientClaims.town(s.town());
        int pct = Math.round((s.control() + 1) * 50);
        Component pctText = Component.literal(pct + "%");
        int pw = font.width(pctText), tx = 25, bw = W - tx - 6;
        Component title = Component.translatable("nationsandwar.notice.siege_of", town != null ? town.name() : s.defender());
        g.drawString(font, Draw.fit(font, title, bw - pw - 4), tx, 5, NoticeHud.fade(Draw.TEXT, a), false);
        g.drawString(font, pctText, W - 6 - pw, 5, NoticeHud.fade(Draw.mix(s.control() > 0 ? besieger : defender, 0xFFFFFFFF, 0.3f), a), false);
        int by = 16, mid = tx + bw / 2, at = mid + Math.round((bw / 2f - 1) * Math.clamp(s.control(), -1, 1));
        g.fill(tx, by, tx + bw, by + 6, NoticeHud.fade(0xFF0A0C10, a));
        g.fillGradient(tx + 1, by + 1, at, by + 5, NoticeHud.fade(Draw.mix(defender, 0xFFFFFFFF, 0.2f), a), NoticeHud.fade(Draw.shade(defender, 0.75f), a));
        g.fillGradient(at, by + 1, tx + bw - 1, by + 5, NoticeHud.fade(Draw.mix(besieger, 0xFFFFFFFF, 0.2f), a), NoticeHud.fade(Draw.shade(besieger, 0.75f), a));
        g.fill(mid, by - 1, mid + 1, by + 7, NoticeHud.fade(0x90FFFFFF, a));
        g.fill(at - 1, by - 2, at + 1, by + 8, NoticeHud.fade(Draw.GOLD, a));
        int half = bw / 2 - 3;
        Component def = Draw.fit(font, Component.literal(s.defender()), half), bes = Draw.fit(font, Component.literal(s.besieger()), half);
        g.drawString(font, def, tx, 25, NoticeHud.fade(Draw.mix(defender, 0xFFFFFFFF, 0.45f), a), false);
        g.drawString(font, bes, tx + bw - font.width(bes), 25, NoticeHud.fade(Draw.mix(besieger, 0xFFFFFFFF, 0.45f), a), false);
    }
}
