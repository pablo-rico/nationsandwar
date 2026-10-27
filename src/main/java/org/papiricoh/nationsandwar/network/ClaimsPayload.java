package org.papiricoh.nationsandwar.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.papiricoh.nationsandwar.NationsAndWar;

import java.util.UUID;

/**
 * Server → client: claimed chunks of one dimension. {@code full} replaces the client's whole map of that dimension
 * (login, dimension change); otherwise each entry is a change, with a null town meaning back to wilderness.
 */
public record ClaimsPayload(String dimension, boolean full, long[] chunks, UUID[] towns) implements CustomPacketPayload {
    public static final Type<ClaimsPayload> TYPE = new Type<>(NationsAndWar.id("claims"));
    private static final UUID NONE = new UUID(0, 0);
    public static final StreamCodec<RegistryFriendlyByteBuf, ClaimsPayload> STREAM_CODEC = StreamCodec.of((b, p) -> {
        b.writeUtf(p.dimension, 128);
        b.writeBoolean(p.full);
        b.writeVarInt(p.chunks.length);
        for (int i = 0; i < p.chunks.length; i++) {
            b.writeLong(p.chunks[i]);
            b.writeUUID(p.towns[i] == null ? NONE : p.towns[i]);
        }
    }, b -> {
        String dim = b.readUtf(128);
        boolean full = b.readBoolean();
        int n = b.readVarInt();
        long[] chunks = new long[n];
        UUID[] towns = new UUID[n];
        for (int i = 0; i < n; i++) {
            chunks[i] = b.readLong();
            UUID u = b.readUUID();
            towns[i] = NONE.equals(u) ? null : u;
        }
        return new ClaimsPayload(dim, full, chunks, towns);
    });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
