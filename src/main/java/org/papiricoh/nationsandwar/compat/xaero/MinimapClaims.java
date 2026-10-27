package org.papiricoh.nationsandwar.compat.xaero;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.common.minimap.highlight.ChunkHighlighter;
import xaero.hud.minimap.info.render.compile.InfoDisplayCompiler;

/**
 * Towns on Xaero's Minimap, when the World Map is not installed (with it, the minimap shows the World Map's highlights).
 * Registered by {@code mixin.xaero.MinimapRegistryMixin}.
 */
public final class MinimapClaims extends ChunkHighlighter {
    public MinimapClaims() {
        super(true);
    }

    @Override
    public boolean regionHasHighlights(ResourceKey<Level> dim, int rx, int rz) {
        return XaeroHighlights.regionHas(dim, rx, rz);
    }

    @Override
    public boolean chunkIsHighlit(ResourceKey<Level> dim, int cx, int cz) {
        return XaeroHighlights.chunkHas(dim, cx, cz);
    }

    @Override
    protected int[] getColors(ResourceKey<Level> dim, int cx, int cz) {
        return XaeroHighlights.colors(dim, cx, cz, resultStore) ? resultStore : null;
    }

    @Override
    public void addChunkHighlightTooltips(InfoDisplayCompiler compiler, ResourceKey<Level> dim, int x, int z, int width) {
    }
}
