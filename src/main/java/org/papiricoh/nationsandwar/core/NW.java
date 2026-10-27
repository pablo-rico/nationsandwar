package org.papiricoh.nationsandwar.core;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.papiricoh.nationsandwar.data.NWSavedData;
import org.papiricoh.nationsandwar.model.Politics;
import org.papiricoh.nationsandwar.model.Result;
import org.papiricoh.nationsandwar.model.Town;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Server-side shortcuts: the saved data, dimension and chunk keys, names, replies, and "politics changed". */
public final class NW {
    /** Operators who toggled {@code /nwadmin bypass}: protection ignores them. */
    public static final Set<UUID> BYPASS = new HashSet<>();

    private NW() {
    }

    public static NWSavedData data(MinecraftServer server) {
        return NWSavedData.get(server);
    }

    public static Politics pol(MinecraftServer server) {
        return data(server).politics;
    }

    public static String dim(Level level) {
        return level.dimension().location().toString();
    }

    public static long chunk(BlockPos pos) {
        return ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
    }

    public static Town townAt(Level level, BlockPos pos) {
        if (level.getServer() == null) return null;
        return pol(level.getServer()).townAt(dim(level), chunk(pos));
    }

    /** A player's name from the profile cache (offline players too), or a short id. */
    public static String name(MinecraftServer server, UUID id) {
        if (id == null) return "";
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) return online.getGameProfile().getName();
        var cache = server.getProfileCache();
        if (cache != null) {
            var p = cache.get(id);
            if (p.isPresent()) return p.get().getName();
        }
        return id.toString().substring(0, 8);
    }

    /** A player by name or UUID string (online first, then the profile cache), or null. */
    public static UUID resolve(MinecraftServer server, String who) {
        if (who == null || who.isEmpty()) return null;
        try {
            return UUID.fromString(who);
        } catch (IllegalArgumentException ignored) {
        }
        ServerPlayer online = server.getPlayerList().getPlayerByName(who);
        if (online != null) return online.getUUID();
        var cache = server.getProfileCache();
        return cache == null ? null : cache.get(who).map(com.mojang.authlib.GameProfile::getId).orElse(null);
    }

    public static boolean online(MinecraftServer server, UUID id) {
        return server.getPlayerList().getPlayer(id) != null;
    }

    public static Component message(Result r) {
        Object[] args = new Object[r.args().length];
        for (int i = 0; i < args.length; i++) args[i] = r.args()[i] instanceof Component c ? c : String.valueOf(r.args()[i]);
        return Component.translatable(r.langKey(), args).withStyle(r.ok() ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    public static void reply(ServerPlayer player, Result r) {
        player.sendSystemMessage(message(r));
    }

    /** Marks the data to save and every client's briefs (and open GUIs) to refresh at the end of the tick. */
    public static void changed(MinecraftServer server) {
        data(server).setDirty();
        Sync.markDirty();
    }
}
