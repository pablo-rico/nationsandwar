package org.papiricoh.nationsandwar.core;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.papiricoh.nationsandwar.model.Nation;
import org.papiricoh.nationsandwar.model.Town;
import org.papiricoh.nationsandwar.network.NoticePayload;

import java.util.Collection;
import java.util.UUID;

/** Sends the top-right notices of war and politics (territory and permission notices are made on the client). */
public final class Notices {
    private Notices() {
    }

    public static void send(ServerPlayer p, NoticePayload.Kind kind, Component title, Component subtitle, int color, String icon) {
        PacketDistributor.sendToPlayer(p, new NoticePayload(kind, title, subtitle, color, icon));
    }

    public static void toPlayers(MinecraftServer server, Collection<UUID> players, NoticePayload.Kind kind, Component title, Component subtitle,
                                 int color, String icon) {
        NoticePayload n = new NoticePayload(kind, title, subtitle, color, icon);
        for (UUID u : players) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p != null) PacketDistributor.sendToPlayer(p, n);
        }
    }

    public static void toTown(MinecraftServer server, Town town, NoticePayload.Kind kind, Component title, Component subtitle, String icon) {
        toPlayers(server, town.residents, kind, title, subtitle, town.color, icon);
    }

    public static void toNation(MinecraftServer server, Nation nation, NoticePayload.Kind kind, Component title, Component subtitle, String icon) {
        toPlayers(server, NW.pol(server).citizens(nation), kind, title, subtitle, nation.color, icon);
    }

    public static void toAll(MinecraftServer server, NoticePayload.Kind kind, Component title, Component subtitle, int color, String icon) {
        PacketDistributor.sendToAllPlayers(new NoticePayload(kind, title, subtitle, color, icon));
    }
}
