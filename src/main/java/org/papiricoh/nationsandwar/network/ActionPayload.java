package org.papiricoh.nationsandwar.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.papiricoh.nationsandwar.NationsAndWar;

/**
 * Client → server: one political action from the GUI, the same ones the commands run ({@code core.Actions}; the list of
 * actions and their arguments is in docs/CONTRACT.md). The server checks every right, answers in chat or with a notice, and
 * sends a fresh {@link ViewPayload}.
 */
public record ActionPayload(String action, CompoundTag args) implements CustomPacketPayload {
    public static final Type<ActionPayload> TYPE = new Type<>(NationsAndWar.id("action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(48), ActionPayload::action, ByteBufCodecs.TRUSTED_COMPOUND_TAG, ActionPayload::args, ActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
