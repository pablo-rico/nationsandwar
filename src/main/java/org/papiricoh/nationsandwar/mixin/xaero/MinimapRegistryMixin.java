package org.papiricoh.nationsandwar.mixin.xaero;

import org.papiricoh.nationsandwar.compat.xaero.MinimapClaims;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.common.minimap.highlight.HighlighterRegistry;

/** The minimap's own highlighter list (only used without the World Map: see {@code XaeroMixinPlugin}). */
@Mixin(value = HighlighterRegistry.class, remap = false)
public abstract class MinimapRegistryMixin {
    @Inject(method = "end", at = @At("HEAD"))
    private void nationsandwar$register(CallbackInfo ci) {
        ((HighlighterRegistry) (Object) this).register(new MinimapClaims());
    }
}
