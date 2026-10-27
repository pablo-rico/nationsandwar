package org.papiricoh.nationsandwar.mixin.xaero;

import org.papiricoh.nationsandwar.compat.xaero.WorldMapClaims;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.map.highlight.HighlighterRegistry;

/** Xaero builds its highlighter list per session and closes it with {@code end()}: ours joins just before. */
@Mixin(value = HighlighterRegistry.class, remap = false)
public abstract class WorldMapRegistryMixin {
    @Inject(method = "end", at = @At("HEAD"))
    private void nationsandwar$register(CallbackInfo ci) {
        ((HighlighterRegistry) (Object) this).register(new WorldMapClaims());
    }
}
