package org.papiricoh.nationsandwar.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.papiricoh.nationsandwar.NationsAndWar;

/**
 * Server → client: open a screen. {@code screen}: "nation" (the main GUI; {@code args.tab} optional) or "envoy" (talking to
 * an NPC nation's envoy; {@code args.nation} = its id, {@code args.entity} = the envoy's entity id).
 */
public record OpenScreenPayload(String screen, CompoundTag args) implements CustomPacketPayload {
    public static final Type<OpenScreenPayload> TYPE = new Type<>(NationsAndWar.id("open_screen"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenScreenPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(32), OpenScreenPayload::screen, ByteBufCodecs.TRUSTED_COMPOUND_TAG, OpenScreenPayload::args, OpenScreenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
