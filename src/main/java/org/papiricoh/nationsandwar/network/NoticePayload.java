package org.papiricoh.nationsandwar.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.papiricoh.nationsandwar.NationsAndWar;

/**
 * Server → client: a notice for the top-right corner. {@code kind} is one of {@link Kind}; {@code icon} names a sprite
 * under {@code textures/gui/notice/} ("war", "siege", "crown", "scroll", "shield", "skull", "handshake", "flag", "pvp").
 */
public record NoticePayload(Kind kind, Component title, Component subtitle, int color, String icon) implements CustomPacketPayload {
    public enum Kind {TERRITORY, WAR, POLITICS, PERMISSION}

    public static final Type<NoticePayload> TYPE = new Type<>(NationsAndWar.id("notice"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NoticePayload> STREAM_CODEC = StreamCodec.composite(
            NeoCodecs.enumCodec(Kind.class), NoticePayload::kind,
            ComponentSerialization.TRUSTED_STREAM_CODEC, NoticePayload::title,
            ComponentSerialization.TRUSTED_STREAM_CODEC, NoticePayload::subtitle,
            ByteBufCodecs.INT, NoticePayload::color,
            ByteBufCodecs.stringUtf8(32), NoticePayload::icon,
            NoticePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
