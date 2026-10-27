package org.papiricoh.nationsandwar.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
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
import org.papiricoh.nationsandwar.registry.NWRegistry;

/** Client wiring: renderers, the GUI key (N), the notices layer, map compat, and forgetting the server on logout. */
public final class NWClient {
    public static final KeyMapping OPEN_GUI = new KeyMapping("key.nationsandwar.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N,
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
        }

        @SubscribeEvent
        public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientClaims.clear();
            ClientPolitics.clear();
        }
    }
}
