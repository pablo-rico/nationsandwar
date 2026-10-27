package org.papiricoh.nationsandwar.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.papiricoh.nationsandwar.NationsAndWar;

/** Server → every client, each second while any siege runs: every siege's {@code view.SiegeBrief} (as NBT). */
public record SiegesPayload(CompoundTag sieges) implements CustomPacketPayload {
    public static final Type<SiegesPayload> TYPE = new Type<>(NationsAndWar.id("sieges"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SiegesPayload> STREAM_CODEC =
            ByteBufCodecs.TRUSTED_COMPOUND_TAG.map(SiegesPayload::new, SiegesPayload::sieges).cast();

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
