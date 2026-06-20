package net.minecraft.network;

import io.netty.buffer.ByteBuf;

public class VarLong {
    private static final int MAX_VARLONG_SIZE = 10;
    private static final int DATA_BITS_MASK = 127;
    private static final int CONTINUATION_BIT_MASK = 128;
    private static final int DATA_BITS_PER_BYTE = 7;

    public static int getByteSize(long data) {
        for (int i = 1; i < MAX_VARLONG_SIZE; i++) {
            if ((data & -1L << i * 7) == 0L) {
                return i;
            }
        }

        return MAX_VARLONG_SIZE;
    }

    public static boolean hasContinuationBit(byte data) {
        return (data & 128) == 128;
    }

    public static long read(ByteBuf buffer) {
        long l = 0L;
        int i = 0;

        byte _byte;
        do {
            _byte = buffer.readByte();
            l |= (long)(_byte & 127) << i++ * 7;
            if (i > 10) {
                throw new RuntimeException("VarLong too big");
            }
        } while (hasContinuationBit(_byte));

        return l;
    }

    public static ByteBuf write(ByteBuf buffer, long value) {
        while ((value & -128L) != 0L) {
            buffer.writeByte((int)(value & 127L) | 128);
            value >>>= 7;
        }

        buffer.writeByte((int)value);
        return buffer;
    }
}
