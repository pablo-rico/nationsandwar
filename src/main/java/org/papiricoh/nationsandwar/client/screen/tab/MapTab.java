package org.papiricoh.nationsandwar.client.screen.tab;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import org.papiricoh.nationsandwar.client.ClientClaims;
import org.papiricoh.nationsandwar.client.gui.Draw;
import org.papiricoh.nationsandwar.client.gui.Ui;
import org.papiricoh.nationsandwar.client.screen.NationScreen;
import org.papiricoh.nationsandwar.compat.MapStyle;
import org.papiricoh.nationsandwar.model.Perm;
import org.papiricoh.nationsandwar.view.Briefs;
import org.papiricoh.nationsandwar.view.PoliticsView;
import org.papiricoh.nationsandwar.view.SiegeBrief;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The mod's own claim map (the fallback when JourneyMap and Xaero's are absent): a chunk grid around the player over the
 * terrain's map colours, claims in their {@link MapStyle} colours with borders, besieged and occupied chunks hatched, a
 * hover tooltip, and click-or-drag to claim free chunks (or unclaim my own) with the outpost toggle. Right-drag pans,
 * the wheel zooms.
 */
public final class MapTab extends Tab {
    private static final int MAX_SAMPLES_PER_FRAME = 96;
    private final Map<Long, Integer> terrain = new HashMap<>();
    private final Set<Long> painted = new HashSet<>();
    private float panX, panZ;
    private int cell = 8;
    private boolean outpost, panning;
    /** 1 claiming, −1 unclaiming, 0 idle while the left button is down. */
    private int paint;
    private int mapX, mapY, mapW, mapH;
    private float centreX, centreZ;
    private String terrainDim = "";

    public MapTab(NationScreen screen) {
        super(screen);
    }

    @Override
    public String id() {
        return "map";
    }

    @Override
    public Draw.Icon icon() {
        return Draw.Icon.MAP;
    }

    private static String dim() {
        var level = Minecraft.getInstance().level;
        return level == null ? "" : level.dimension().location().toString();
    }

    @Override
    public void render(GuiGraphics g, PoliticsView v, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        String dim = dim();
        if (!dim.equals(terrainDim)) {
            terrain.clear();
            terrainDim = dim;
        }
        toolbar(g, v);
        mapX = x + 2;
        mapY = y + 18;
        mapW = w - 4;
        mapH = h - 32;
        centreX = (float) (mc.player.getX() / 16) + panX;
        centreZ = (float) (mc.player.getZ() / 16) + panZ;
        Draw.inset(g, mapX - 1, mapY - 1, mapW + 2, mapH + 2);
        ui.clip(g, mapX, mapY, mapW, mapH);
        Map<Long, UUID> claims = ClientClaims.claims(dim);
        UUID myTown = v.town() == null ? null : v.town().id();
        UUID myNation = v.nation() == null ? null : v.nation().id();
        int x0 = Mth.floor(centreX - mapW / 2f / cell) - 1, x1 = Mth.ceil(centreX + mapW / 2f / cell) + 1;
        int z0 = Mth.floor(centreZ - mapH / 2f / cell) - 1, z1 = Mth.ceil(centreZ + mapH / 2f / cell) + 1;
        int samples = 0, phase = (int) (mc.level.getGameTime() / 4 % 4);
        Long hovered = null;
        for (int cz = z0; cz <= z1; cz++) {
            for (int cx = x0; cx <= x1; cx++) {
                int sx = sx(cx), sy = sy(cz);
                long key = ChunkPos.asLong(cx, cz);
                Integer ground = terrain.get(key);
                if (ground == null && samples < MAX_SAMPLES_PER_FRAME && mc.level.hasChunk(cx, cz)) {
                    ground = sample(cx, cz);
                    terrain.put(key, ground);
                    samples++;
                }
                int base = ground == null ? 0xFF0E1116 : Draw.opaque(Draw.mix(Draw.shade(ground, 0.42f), 0x1A1F27, 0.35f));
                g.fill(sx, sy, sx + cell, sy + cell, base);
                UUID owner = claims.get(key);
                Briefs.TownBrief t = owner == null ? null : ClientClaims.town(owner);
                if (t != null) {
                    int fill = MapStyle.fill(dim, t, key);
                    g.fill(sx, sy, sx + cell, sy + cell, Draw.alpha(fill, (int) (255 * Math.max(0.6f, MapStyle.fillOpacity()))));
                    int edge = owner.equals(myTown) ? Draw.GOLD : ClientClaims.enemies(myNation, t.nation()) ? Draw.BAD
                            : ClientClaims.allied(myNation, t.nation()) ? Draw.GOOD : Draw.opaque(Draw.mix(ClientClaims.mapColor(t), 0xFFFFFF, 0.45f));
                    if (MapStyle.edge(claims, owner, cx, cz, 0, -1)) g.fill(sx, sy, sx + cell, sy + 1, edge);
                    if (MapStyle.edge(claims, owner, cx, cz, 0, 1)) g.fill(sx, sy + cell - 1, sx + cell, sy + cell, edge);
                    if (MapStyle.edge(claims, owner, cx, cz, -1, 0)) g.fill(sx, sy, sx + 1, sy + cell, edge);
                    if (MapStyle.edge(claims, owner, cx, cz, 1, 0)) g.fill(sx + cell - 1, sy, sx + cell, sy + cell, edge);
                    MapStyle.State state = MapStyle.state(dim, t, key);
                    if (state == MapStyle.State.BESIEGED) Draw.hatch(g, sx, sy, cell, 0xC0FF3030, phase);
                    else if (state == MapStyle.State.OCCUPIED) Draw.hatch(g, sx, sy, cell, 0xB0101010, 0);
                    if (owner.equals(myTown) && v.town().homeChunk() == key) {
                        int m = Math.max(2, cell / 3);
                        g.fill(sx + cell / 2 - m / 2, sy + cell / 2 - m / 2, sx + cell / 2 - m / 2 + m, sy + cell / 2 - m / 2 + m, Draw.GOLD);
                    }
                } else if (cell >= 6) {
                    g.fill(sx, sy, sx + 1, sy + cell, 0x14FFFFFF);
                    g.fill(sx, sy, sx + cell, sy + 1, 0x14FFFFFF);
                }
                if (ui.over(sx, sy, cell, cell)) hovered = key;
            }
        }
        if (hovered != null) {
            int hx = sx(ChunkPos.getX(hovered)), hy = sy(ChunkPos.getZ(hovered));
            Draw.frame(g, hx - 1, hy - 1, cell + 2, cell + 2, 0xFFFFFFFF);
        }
        playerMarker(g, mc);
        ui.unclip(g);
        if (hovered != null) hoverTip(v, dim, claims, hovered, myTown, myNation);
        legend(g, y + h - 11);
    }

    private int sx(int cx) {
        return mapX + mapW / 2 + Mth.floor((cx - centreX) * cell);
    }

    private int sy(int cz) {
        return mapY + mapH / 2 + Mth.floor((cz - centreZ) * cell);
    }

    /** The map colour of the surface block at a loaded chunk's centre. */
    private static int sample(int cx, int cz) {
        var level = Minecraft.getInstance().level;
        int bx = cx * 16 + 8, bz = cz * 16 + 8;
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz) - 1;
        BlockPos pos = new BlockPos(bx, top, bz);
        MapColor c = level.getBlockState(pos).getMapColor(level, pos);
        return c == MapColor.NONE ? 0x2A3A52 : c.col;
    }

    private void playerMarker(GuiGraphics g, Minecraft mc) {
        float px = mapX + mapW / 2f + (float) (mc.player.getX() / 16 - centreX) * cell;
        float py = mapY + mapH / 2f + (float) (mc.player.getZ() / 16 - centreZ) * cell;
        float yaw = (float) Math.toRadians(mc.player.getYRot());
        float fx = -Mth.sin(yaw), fz = Mth.cos(yaw), r = Math.max(4, cell * 0.7f);
        float lx = -fz, lz = fx;
        Draw.quad(g, px + fx * r, py + fz * r, px + lx * r * 0.6f - fx * r * 0.5f, py + lz * r * 0.6f - fz * r * 0.5f, px - fx * r * 0.2f, py - fz * r * 0.2f,
                px - lx * r * 0.6f - fx * r * 0.5f, py - lz * r * 0.6f - fz * r * 0.5f, 0xFFFFFFFF);
    }

    private void hoverTip(PoliticsView v, String dim, Map<Long, UUID> claims, long key, UUID myTown, UUID myNation) {
        int cx = ChunkPos.getX(key), cz = ChunkPos.getZ(key);
        List<Component> lines = new ArrayList<>();
        UUID owner = claims.get(key);
        Briefs.TownBrief t = owner == null ? null : ClientClaims.town(owner);
        if (t == null) lines.add(tr("wilderness").withStyle(s -> s.withColor(0x9AA3B5)));
        else {
            Component label = Component.literal(MapStyle.label(t)).withStyle(s -> s.withColor(ClientClaims.mapColor(t)));
            SiegeBrief siege = ClientClaims.siegeAt(dim, key);
            if (siege != null)
                label = label.copy().append(Component.translatable("nationsandwar.map.besieged", siege.besieger(), Math.round((siege.control() + 1) * 50))
                        .withStyle(s -> s.withColor(0xFF6060)));
            else if (MapStyle.state(dim, t, key) == MapStyle.State.OCCUPIED) label = label.copy().append(tr("occupied_suffix").withStyle(s -> s.withColor(0xB0B0B0)));
            lines.add(label);
            String rel = owner.equals(myTown) ? "self" : ClientClaims.enemies(myNation, t.nation()) ? "enemy" : ClientClaims.allied(myNation, t.nation()) ? "ally" : "neutral";
            lines.add(tr("relation." + rel).withStyle(s -> s.withColor(DiplomacyTab.relationColor(rel) & 0xFFFFFF)));
        }
        lines.add(Component.literal(cx + ", " + cz).withStyle(s -> s.withColor(0x707888)));
        if (v.town() != null) {
            if (t == null && has(v.town().myPerms(), Perm.CLAIM)) lines.add(tr(outpost ? "click_outpost" : "click_claim", v.town().nextClaimCost()).withStyle(s -> s.withColor(0x6FD08A)));
            else if (owner != null && owner.equals(myTown) && has(v.town().myPerms(), Perm.UNCLAIM)) lines.add(tr("click_unclaim").withStyle(s -> s.withColor(0xE5646A)));
        }
        ui.tip(ui.mx, ui.my, 1, 1, lines);
    }

    private void toolbar(GuiGraphics g, PoliticsView v) {
        int tx = x + 2, ty = y + 2;
        PoliticsView.TownView t = v.town();
        if (t != null) {
            Component claims = Component.translatable("nationsandwar.info.claims").append(": " + t.claims() + "/" + t.maxClaims());
            g.drawString(font, claims, tx, ty + 3, Draw.TEXT, false);
            tx += font.width(claims) + 10;
            Component label = tr("outpost_mode", t.outposts(), t.maxOutposts(), v.prices().outpost());
            tx += ui.check(g, tx, ty + 1, label, outpost, need(t.myPerms(), Perm.CLAIM), () -> outpost = !outpost) + 10;
        }
        int rx = x + w - 2;
        rx -= ui.button(g, rx - 46, ty, 46, 13, tr("centre"), Ui.Style.NORMAL, panX == 0 && panZ == 0 ? tr("centred") : null, () -> panX = panZ = 0) + 3;
        rx -= ui.button(g, rx - 14, ty, 14, 13, Component.literal("+"), Ui.Style.NORMAL, cell >= 16 ? tr("max_zoom") : null, () -> cell = Math.min(16, cell + 2)) + 2;
        ui.button(g, rx - 14, ty, 14, 13, Component.literal("−"), Ui.Style.NORMAL, cell <= 4 ? tr("max_zoom") : null, () -> cell = Math.max(4, cell - 2));
    }

    private void legend(GuiGraphics g, int ly) {
        int lx = x + 2;
        lx = key(g, lx, ly, Draw.GOLD, tr("my_town"));
        lx = key(g, lx, ly, Draw.GOOD, tr("relation.ally"));
        lx = key(g, lx, ly, Draw.BAD, tr("relation.enemy"));
        lx = key(g, lx, ly, 0xFFFF3030, tr("besieged"));
        key(g, lx, ly, 0xFF505050, tr("occupied"));
        Component hint = tr("map_hint");
        Draw.text(g, font, hint, x + w - 2 - font.width(hint) * 0.7f, ly + 2, 0.7f, 0xFF6A7384, false);
    }

    private int key(GuiGraphics g, int kx, int ky, int color, Component label) {
        g.fill(kx, ky + 1, kx + 7, ky + 8, color);
        Draw.text(g, font, label, kx + 9, ky + 2, 0.75f, Draw.MUTED, false);
        return kx + 9 + (int) (font.width(label) * 0.75f) + 8;
    }

    // --- input -------------------------------------------------------------------------------------------------------------

    private boolean inMap(double mx, double my) {
        return mx >= mapX && mx < mapX + mapW && my >= mapY && my < mapY + mapH;
    }

    private long chunkAt(double mx, double my) {
        int cx = Mth.floor(centreX + (mx - mapX - mapW / 2f) / cell), cz = Mth.floor(centreZ + (my - mapY - mapH / 2f) / cell);
        return ChunkPos.asLong(cx, cz);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!inMap(mx, my)) return false;
        if (button == 1) {
            panning = true;
            return true;
        }
        PoliticsView v = org.papiricoh.nationsandwar.client.ClientPolitics.view();
        if (button != 0 || v == null || v.town() == null) return false;
        long key = chunkAt(mx, my);
        UUID owner = ClientClaims.claims(dim()).get(key);
        painted.clear();
        if (owner == null && has(v.town().myPerms(), Perm.CLAIM)) paint = 1;
        else if (v.town().id().equals(owner) && has(v.town().myPerms(), Perm.UNCLAIM)) paint = -1;
        else paint = 0;
        paintAt(mx, my);
        return true;
    }

    private void paintAt(double mx, double my) {
        if (paint == 0 || !inMap(mx, my)) return;
        PoliticsView v = org.papiricoh.nationsandwar.client.ClientPolitics.view();
        if (v == null || v.town() == null) return;
        long key = chunkAt(mx, my);
        if (!painted.add(key)) return;
        UUID owner = ClientClaims.claims(dim()).get(key);
        int cx = ChunkPos.getX(key), cz = ChunkPos.getZ(key);
        if (paint > 0 && owner == null) act("town.claim", "x", cx, "z", cz, "outpost", outpost);
        else if (paint < 0 && v.town().id().equals(owner)) act("town.unclaim", "x", cx, "z", cz);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (panning && button == 1) {
            panX -= (float) dx / cell;
            panZ -= (float) dy / cell;
            return true;
        }
        if (paint != 0 && button == 0) {
            paintAt(mx, my);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean was = panning || paint != 0;
        panning = false;
        paint = 0;
        return was;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (!inMap(mx, my)) return false;
        int before = cell;
        cell = Mth.clamp(cell + (delta > 0 ? 2 : -2), 4, 16);
        if (cell != before) {
            float wx = centreX + (float) (mx - mapX - mapW / 2f) / before;
            float wz = centreZ + (float) (my - mapY - mapH / 2f) / before;
            panX += wx - (centreX + (float) (mx - mapX - mapW / 2f) / cell);
            panZ += wz - (centreZ + (float) (my - mapY - mapH / 2f) / cell);
        }
        return true;
    }
}
