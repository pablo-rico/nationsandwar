package org.papiricoh.nationsandwar.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Small stream codecs vanilla lacks. */
public final class NeoCodecs {
    private NeoCodecs() {
    }

    public static <E extends Enum<E>> StreamCodec<ByteBuf, E> enumCodec(Class<E> type) {
        E[] values = type.getEnumConstants();
        return ByteBufCodecs.VAR_INT.map(i -> values[Math.max(0, Math.min(values.length - 1, i))], Enum::ordinal);
    }
}
