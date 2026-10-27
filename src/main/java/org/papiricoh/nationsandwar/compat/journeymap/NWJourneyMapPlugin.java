package org.papiricoh.nationsandwar.compat.journeymap;

import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.client.display.Context;
import journeymap.api.v2.client.display.PolygonOverlay;
import journeymap.api.v2.client.util.UIState;
import journeymap.api.v2.client.model.MapPolygonWithHoles;
import journeymap.api.v2.client.model.ShapeProperties;
import journeymap.api.v2.client.model.TextProperties;
import journeymap.api.v2.client.util.PolygonHelper;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.papiricoh.nationsandwar.NWClientConfig;
import org.papiricoh.nationsandwar.NationsAndWar;
import org.papiricoh.nationsandwar.client.ClientClaims;
import org.papiricoh.nationsandwar.client.notice.NoticeHud;
import org.papiricoh.nationsandwar.compat.MapLayer;
import org.papiricoh.nationsandwar.compat.MapStyle;
import org.papiricoh.nationsandwar.compat.Territories;
import org.papiricoh.nationsandwar.view.Briefs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Claims on JourneyMap, by the chosen {@link MapLayer}: one filled polygon per connected region of each group (nation,
 * province, town or alliance) with a thick border and the group's name sized to the region (Paradox style; JourneyMap
 * draws labels level, at the polygon's centre), thin outlines with small names for the towns inside when zoomed in, and
 * besieged or occupied chunks on top in the besieger's colour. A managed nation's land is darker with a thick border in
 * its colour. Rebuilt at most once a second after the claims, briefs, sieges or layer change. JourneyMap finds this class
 * by its annotation, so nothing loads it without JourneyMap.
 */
@JourneyMapPlugin(apiVersion = "2.0.0")
public final class NWJourneyMapPlugin implements IClientPlugin {
    private IClientAPI api;
    private boolean dirty = true;
    private int ticks;

    @Override
    public String getModId() {
        return NationsAndWar.MODID;
    }

    @Override
    public void initialize(IClientAPI api) {
        this.api = api;
        ClientClaims.listen(c -> dirty = true);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> {
            if (++ticks % 20 == 0 && dirty) rebuild();
            if (ticks % 10 == 0) avoidMinimap();
        });
    }

    /**
     * Keeps the notices below JourneyMap's minimap, which sits in the top-right by default. JourneyMap's reported display
     * bounds are not reliable, so this reserves the default minimap's share of the screen height while it is active
     * ({@code notices.topOffset} in the client config overrides it).
     */
    private void avoidMinimap() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        UIState state = api.getUIState(Context.UI.Minimap);
        int manual = NWClientConfig.get(NWClientConfig.NOTICE_TOP_OFFSET);
        if (manual >= 0) NoticeHud.reserveTop(manual);
        else NoticeHud.reserveTop(state != null && state.active && mc.getWindow() != null ? (int) (mc.getWindow().getGuiScaledHeight() * 0.38) : 0);
    }

    /** JourneyMap zoom from which the towns inside a group get their outline names. */
    private static final int TOWN_NAMES_ZOOM = 2;

    private void rebuild() {
        dirty = false;
        api.removeAll(getModId());
        if (!NWClientConfig.get(NWClientConfig.MAP_OVERLAYS)) return;
        boolean labels = NWClientConfig.get(NWClientConfig.MAP_LABELS), byTown = MapStyle.layer() == MapLayer.TOWNS;
        for (String dim : ClientClaims.dimensions()) {
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dim));
            Map<UUID, List<ChunkPos>> towns = new HashMap<>(), groups = new HashMap<>();
            Map<UUID, Briefs.TownBrief> groupTown = new HashMap<>();
            List<ChunkPos> war = new ArrayList<>();
            for (var e : ClientClaims.claims(dim).entrySet()) {
                Briefs.TownBrief town = ClientClaims.town(e.getValue());
                if (town == null) continue;
                ChunkPos c = new ChunkPos(e.getKey());
                towns.computeIfAbsent(town.id(), k -> new ArrayList<>()).add(c);
                UUID g = MapStyle.group(town).id();
                groups.computeIfAbsent(g, k -> new ArrayList<>()).add(c);
                groupTown.putIfAbsent(g, town);
                if (MapStyle.state(dim, town, e.getKey()) != MapStyle.State.NORMAL) war.add(c);
            }
            for (var e : groups.entrySet()) {
                Briefs.TownBrief town = groupTown.get(e.getKey());
                MapStyle.Group group = MapStyle.group(town);
                boolean managed = MapStyle.managed(town);
                int color = MapStyle.base(town);
                ShapeProperties shape = managed
                        ? new ShapeProperties().setFillColor(color).setFillOpacity(MapStyle.managedOpacity()).setStrokeColor(MapStyle.accent(town))
                        .setStrokeOpacity(1f).setStrokeWidth(3f)
                        : new ShapeProperties().setFillColor(color).setFillOpacity(MapStyle.fillOpacity()).setStrokeColor(MapStyle.mix(color, 0xFFFFFF, 0.35f))
                        .setStrokeOpacity(0.95f).setStrokeWidth(byTown ? 2f : 3f);
                List<MapPolygonWithHoles> polys = PolygonHelper.createChunksPolygon(e.getValue(), 70);
                double biggest = 0;
                for (MapPolygonWithHoles poly : polys) biggest = Math.max(biggest, area(poly));
                for (MapPolygonWithHoles poly : polys) {
                    PolygonOverlay o = new PolygonOverlay(getModId(), key, shape, poly);
                    o.setOverlayGroupName("territories").setTitle(group.name());
                    double area = area(poly);
                    int chunks = (int) Math.round(area / 256);
                    if (labels && (chunks >= Territories.MIN_CHUNKS || area == biggest)) {
                        float scale = (float) Math.max(1, Math.min(group.kind() == MapLayer.TOWNS ? 2 : 4, Math.sqrt(chunks) / 2.5));
                        o.setLabel(group.name().toUpperCase(java.util.Locale.ROOT)).setTextProperties(new TextProperties()
                                .setColor(MapStyle.mix(group.color(), 0xFFFFFF, 0.65f)).setBackgroundColor(MapStyle.mix(group.color(), 0x000000, 0.7f))
                                .setBackgroundOpacity(0.35f).setFontShadow(true).setScale(scale));
                    }
                    show(o);
                }
            }
            if (!byTown) {
                ShapeProperties line = new ShapeProperties().setFillOpacity(0f).setStrokeColor(0xFFFFFF).setStrokeOpacity(0.35f).setStrokeWidth(1f);
                for (var e : towns.entrySet()) {
                    Briefs.TownBrief town = ClientClaims.town(e.getKey());
                    boolean alone = MapStyle.group(town).kind() == MapLayer.TOWNS;
                    for (MapPolygonWithHoles poly : PolygonHelper.createChunksPolygon(e.getValue(), 70)) {
                        PolygonOverlay o = new PolygonOverlay(getModId(), key, line, poly);
                        o.setOverlayGroupName("towns").setDisplayOrder(5).setTitle(MapStyle.hierarchy(town));
                        // Below the group's name, which JourneyMap also centres on the polygon.
                        if (labels && !alone) o.setLabel(town.name()).setTextProperties(new TextProperties().setColor(0xFFFFFF).setBackgroundColor(0x000000)
                                .setBackgroundOpacity(0.4f).setFontShadow(true).setScale(1f).setOffsetY(14).setMinZoom(TOWN_NAMES_ZOOM));
                        show(o);
                    }
                }
            }
            for (ChunkPos c : war) {
                Briefs.TownBrief town = ClientClaims.townAt(dim, c.x, c.z);
                if (town == null) continue;
                boolean besieged = MapStyle.state(dim, town, c.toLong()) == MapStyle.State.BESIEGED;
                ShapeProperties shape = new ShapeProperties().setFillColor(besieged ? MapStyle.fill(dim, town, c.toLong()) : 0x601010)
                        .setFillOpacity(0.6f).setStrokeColor(0xFF2020).setStrokeOpacity(1f).setStrokeWidth(besieged ? 3f : 1.5f);
                for (MapPolygonWithHoles poly : PolygonHelper.createChunksPolygon(List.of(c), 70)) {
                    PolygonOverlay o = new PolygonOverlay(getModId(), key, shape, poly);
                    o.setOverlayGroupName("war").setDisplayOrder(10).setTitle(MapStyle.hierarchy(town));
                    show(o);
                }
            }
        }
    }

    /** A polygon's area in blocks (its outline, minus its holes). */
    private static double area(MapPolygonWithHoles poly) {
        double a = Math.abs(shoelace(poly.hull.getPoints()));
        if (poly.holes != null) for (var h : poly.holes) a -= Math.abs(shoelace(h.getPoints()));
        return a;
    }

    private static double shoelace(List<net.minecraft.core.BlockPos> pts) {
        double s = 0;
        for (int i = 0; i < pts.size(); i++) {
            var p = pts.get(i);
            var q = pts.get((i + 1) % pts.size());
            s += (double) p.getX() * q.getZ() - (double) q.getX() * p.getZ();
        }
        return s / 2;
    }

    private void show(PolygonOverlay o) {
        try {
            api.show(o);
        } catch (Exception e) {
            NationsAndWar.LOGGER.debug("[journeymap] overlay refused", e);
        }
    }
}
