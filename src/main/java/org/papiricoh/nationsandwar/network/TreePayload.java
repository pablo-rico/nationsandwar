package org.papiricoh.nationsandwar.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.papiricoh.nationsandwar.NationsAndWar;

/** Server → client: the rank tree of one town or nation ({@code view.TreeView} written as NBT), answering {@code inspect}. */
public record TreePayload(CompoundTag tree) implements CustomPacketPayload {
    public static final Type<TreePayload> TYPE = new Type<>(NationsAndWar.id("tree"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TreePayload> STREAM_CODEC =
            ByteBufCodecs.TRUSTED_COMPOUND_TAG.map(TreePayload::new, TreePayload::tree).cast();

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
