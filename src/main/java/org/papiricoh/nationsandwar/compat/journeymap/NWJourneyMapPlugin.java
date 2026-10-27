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
import org.papiricoh.nationsandwar.compat.MapStyle;
import org.papiricoh.nationsandwar.view.Briefs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Towns on JourneyMap: one outlined polygon per town (its nation's colour, its name as label), and besieged or occupied
 * chunks on top in the besieger's colour. Rebuilt at most once a second after the claims, briefs or sieges change.
 * JourneyMap finds this class by its annotation, so nothing loads it without JourneyMap.
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

    private void rebuild() {
        dirty = false;
        api.removeAll(getModId());
        if (!NWClientConfig.get(NWClientConfig.MAP_OVERLAYS)) return;
        for (String dim : ClientClaims.dimensions()) {
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dim));
            Map<UUID, List<ChunkPos>> byTown = new HashMap<>();
            List<ChunkPos> war = new ArrayList<>();
            for (var e : ClientClaims.claims(dim).entrySet()) {
                ChunkPos c = new ChunkPos(e.getKey());
                byTown.computeIfAbsent(e.getValue(), k -> new ArrayList<>()).add(c);
            }
            for (var e : byTown.entrySet()) {
                Briefs.TownBrief town = ClientClaims.town(e.getKey());
                if (town == null) continue;
                int color = ClientClaims.mapColor(town);
                ShapeProperties shape = new ShapeProperties().setFillColor(color).setFillOpacity(MapStyle.fillOpacity())
                        .setStrokeColor(color).setStrokeOpacity(0.9f).setStrokeWidth(2f);
                for (MapPolygonWithHoles poly : PolygonHelper.createChunksPolygon(e.getValue(), 70)) {
                    PolygonOverlay o = new PolygonOverlay(getModId(), key, shape, poly);
                    o.setOverlayGroupName("towns").setTitle(MapStyle.label(town));
                    if (NWClientConfig.get(NWClientConfig.MAP_LABELS)) {
                        o.setLabel(town.name()).setTextProperties(new TextProperties().setColor(0xFFFFFF).setBackgroundColor(color)
                                .setBackgroundOpacity(0.5f).setFontShadow(true).setMinZoom(1));
                    }
                    show(o);
                }
                for (ChunkPos c : e.getValue()) if (MapStyle.state(dim, town, c.toLong()) != MapStyle.State.NORMAL) war.add(c);
            }
            for (ChunkPos c : war) {
                Briefs.TownBrief town = ClientClaims.townAt(dim, c.x, c.z);
                if (town == null) continue;
                boolean besieged = MapStyle.state(dim, town, c.toLong()) == MapStyle.State.BESIEGED;
                ShapeProperties shape = new ShapeProperties().setFillColor(besieged ? MapStyle.fill(dim, town, c.toLong()) : 0x601010)
                        .setFillOpacity(0.6f).setStrokeColor(0xFF2020).setStrokeOpacity(1f).setStrokeWidth(besieged ? 3f : 1.5f);
                for (MapPolygonWithHoles poly : PolygonHelper.createChunksPolygon(List.of(c), 70)) {
                    PolygonOverlay o = new PolygonOverlay(getModId(), key, shape, poly);
                    o.setOverlayGroupName("war").setDisplayOrder(10).setTitle(MapStyle.label(town));
                    show(o);
                }
            }
        }
    }

    private void show(PolygonOverlay o) {
        try {
            api.show(o);
        } catch (Exception e) {
            NationsAndWar.LOGGER.debug("[journeymap] overlay refused", e);
        }
    }
}
