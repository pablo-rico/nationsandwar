package org.papiricoh.nationsandwar.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;
import org.papiricoh.nationsandwar.NationsAndWar;
import org.papiricoh.nationsandwar.client.notice.NoticeHud;
import org.papiricoh.nationsandwar.client.render.EnvoyRenderer;
import org.papiricoh.nationsandwar.client.render.GuardRenderer;
import org.papiricoh.nationsandwar.client.screen.NationScreen;
import org.papiricoh.nationsandwar.compat.MapLayer;
import org.papiricoh.nationsandwar.compat.MapStyle;
import org.papiricoh.nationsandwar.network.NoticePayload;
import org.papiricoh.nationsandwar.registry.NWRegistry;

/** Client wiring: renderers, the GUI key (N) and the map layer key, the notices layer, map compat, and forgetting the server on logout. */
public final class NWClient {
    public static final KeyMapping OPEN_GUI = new KeyMapping("key.nationsandwar.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N,
            "key.categories.nationsandwar");
    /** Cycles the map layer ({@code compat.MapLayer}) on every map; unbound until the player picks a key. */
    public static final KeyMapping MAP_LAYER = new KeyMapping("key.nationsandwar.map_layer", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(),
            "key.categories.nationsandwar");

    private NWClient() {
    }

    @EventBusSubscriber(modid = NationsAndWar.MODID, value = Dist.CLIENT)
    public static final class ModEvents {
        @SubscribeEvent
        public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(NWRegistry.GUARD.get(), GuardRenderer::new);
            event.registerEntityRenderer(NWRegistry.ENVOY.get(), EnvoyRenderer::new);
        }

        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent event) {
            event.register(OPEN_GUI);
            event.register(MAP_LAYER);
        }

        @SubscribeEvent
        public static void layers(RegisterGuiLayersEvent event) {
            event.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, NationsAndWar.id("notices"), NoticeHud::render);
        }

        @SubscribeEvent
        public static void setup(FMLClientSetupEvent event) {
            if (ModList.get().isLoaded("xaeroworldmap"))
                ClientClaims.listen(c -> org.papiricoh.nationsandwar.compat.xaero.XaeroHighlights.invalidate());
        }
    }

    @EventBusSubscriber(modid = NationsAndWar.MODID, value = Dist.CLIENT)
    public static final class GameEvents {
        @SubscribeEvent
        public static void tick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            while (OPEN_GUI.consumeClick()) if (mc.screen == null && mc.player != null) mc.setScreen(new NationScreen(null));
            while (MAP_LAYER.consumeClick()) {
                MapLayer layer = MapStyle.layer().next();
                MapStyle.setLayer(layer);
                NoticeHud.push(NoticePayload.Kind.TERRITORY, Component.translatable("nationsandwar.map.layer_title"),
                        Component.translatable(layer.langKey()), 0xD9A43A, "flag");
            }
        }

        @SubscribeEvent
        public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientClaims.clear();
            ClientPolitics.clear();
        }
    }
}
