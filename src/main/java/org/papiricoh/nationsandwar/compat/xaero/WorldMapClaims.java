package org.papiricoh.nationsandwar.compat.xaero;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.map.WorldMapSession;
import xaero.map.highlight.ChunkHighlighter;

import java.util.List;

/** Towns on Xaero's World Map (registered by {@code mixin.xaero.WorldMapRegistryMixin}). */
public final class WorldMapClaims extends ChunkHighlighter {
    public WorldMapClaims() {
        super(true);
    }

    @Override
    public int calculateRegionHash(ResourceKey<Level> dim, int rx, int rz) {
        return XaeroHighlights.hash(dim, rx, rz);
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
    public Component getChunkHighlightSubtleTooltip(ResourceKey<Level> dim, int cx, int cz) {
        return XaeroHighlights.tooltip(dim, cx, cz);
    }

    @Override
    public Component getChunkHighlightBluntTooltip(ResourceKey<Level> dim, int cx, int cz) {
        return null;
    }

    @Override
    public void addMinimapBlockHighlightTooltips(List<Component> list, ResourceKey<Level> dim, int x, int z, int width) {
        Component c = XaeroHighlights.tooltip(dim, x >> 4, z >> 4);
        if (c != null) list.add(c);
    }

    static void invalidate() {
        WorldMapSession session = WorldMapSession.getCurrentSession();
        if (session != null && session.getMapProcessor() != null && session.getMapProcessor().getMapWorld() != null)
            session.getMapProcessor().getMapWorld().clearAllCachedHighlightHashes();
    }
}
