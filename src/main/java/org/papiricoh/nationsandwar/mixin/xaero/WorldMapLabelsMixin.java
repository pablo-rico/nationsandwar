package org.papiricoh.nationsandwar.mixin.xaero;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.papiricoh.nationsandwar.compat.xaero.XaeroHighlights;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.map.gui.GuiMap;

/**
 * The territory names on Xaero's World Map, drawn over the map just before its waypoints and other elements. Xaero draws
 * block (x, z) at the screen's centre plus ({@code x - cameraX}) × {@code scale} real pixels, which is that divided by
 * {@code screenScale} in GUI pixels. If a Xaero update renames these, the mixin quietly does nothing (the colours stay).
 */
@Mixin(value = GuiMap.class, remap = false)
public abstract class WorldMapLabelsMixin {
    @Shadow
    private double cameraX;
    @Shadow
    private double cameraZ;
    @Shadow
    private double scale;
    @Shadow
    private double screenScale;

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lxaero/map/element/MapElementRenderHandler;render(Lxaero/map/gui/GuiMap;Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lxaero/map/graphics/renderer/multitexture/MultiTextureRenderTypeRendererProvider;DDIIDDDDDFZLxaero/map/element/HoveredMapElementHolder;Lnet/minecraft/client/Minecraft;F)Lxaero/map/element/HoveredMapElementHolder;"))
    private void nationsandwar$labels(GuiGraphics g, int mouseX, int mouseY, float partial, CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        XaeroHighlights.drawLabels(g, Minecraft.getInstance().font, screen.width, screen.height, cameraX, cameraZ, scale / Math.max(1e-6, screenScale));
    }
}
