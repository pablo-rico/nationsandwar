package org.papiricoh.nationsandwar.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.papiricoh.nationsandwar.NationsAndWar;

/** Server → client: the political picture for one player ({@code view.PoliticsView} written as NBT). */
public record ViewPayload(CompoundTag view) implements CustomPacketPayload {
    public static final Type<ViewPayload> TYPE = new Type<>(NationsAndWar.id("view"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ViewPayload> STREAM_CODEC =
            ByteBufCodecs.TRUSTED_COMPOUND_TAG.map(ViewPayload::new, ViewPayload::view).cast();

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
