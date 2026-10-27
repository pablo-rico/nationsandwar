package org.papiricoh.nationsandwar.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.papiricoh.nationsandwar.NationsAndWar;

/** Server → one client: which town and nation the player belongs to ({@code town}/{@code nation} UUIDs, absent if none). Sent on login and when it changes. */
public record SelfPayload(CompoundTag self) implements CustomPacketPayload {
    public static final Type<SelfPayload> TYPE = new Type<>(NationsAndWar.id("self"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SelfPayload> STREAM_CODEC =
            ByteBufCodecs.TRUSTED_COMPOUND_TAG.map(SelfPayload::new, SelfPayload::self).cast();

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
