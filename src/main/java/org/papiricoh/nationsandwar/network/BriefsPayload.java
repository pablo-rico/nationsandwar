package org.papiricoh.nationsandwar.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.papiricoh.nationsandwar.NationsAndWar;

/**
 * Server → every client: the public facts about all towns, provinces, nations, alliances and wars ({@code view.Briefs} as
 * NBT), for the maps, the territory notices and the HUD. Sent on login and whenever politics change (batched per tick).
 */
public record BriefsPayload(CompoundTag briefs) implements CustomPacketPayload {
    public static final Type<BriefsPayload> TYPE = new Type<>(NationsAndWar.id("briefs"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BriefsPayload> STREAM_CODEC =
            ByteBufCodecs.TRUSTED_COMPOUND_TAG.map(BriefsPayload::new, BriefsPayload::briefs).cast();

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
