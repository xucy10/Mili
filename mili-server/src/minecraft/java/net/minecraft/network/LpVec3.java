package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class LpVec3 {
    private static final int DATA_BITS = 15;
    private static final int DATA_BITS_MASK = 32767;
    private static final double MAX_QUANTIZED_VALUE = 32766.0;
    private static final int SCALE_BITS = 2;
    private static final int SCALE_BITS_MASK = 3;
    private static final int CONTINUATION_FLAG = 4;
    private static final int X_OFFSET = 3;
    private static final int Y_OFFSET = 18;
    private static final int Z_OFFSET = 33;
    public static final double ABS_MAX_VALUE = 1.7179869183E10;
    public static final double ABS_MIN_VALUE = 3.051944088384301E-5;

    public static boolean hasContinuationBit(int bytes) {
        return (bytes & 4) == 4;
    }

    public static Vec3 read(ByteBuf buffer) {
        int unsignedByte = buffer.readUnsignedByte();
        if (unsignedByte == 0) {
            return Vec3.ZERO;
        } else {
            int unsignedByte1 = buffer.readUnsignedByte();
            long unsignedInt = buffer.readUnsignedInt();
            long l = unsignedInt << 16 | unsignedByte1 << 8 | unsignedByte;
            long l1 = unsignedByte & 3;
            if (hasContinuationBit(unsignedByte)) {
                l1 |= (VarInt.read(buffer) & 4294967295L) << 2;
            }

            return new Vec3(unpack(l >> 3) * l1, unpack(l >> 18) * l1, unpack(l >> 33) * l1);
        }
    }

    public static void write(ByteBuf buffer, Vec3 vector) {
        double d = sanitize(vector.x);
        double d1 = sanitize(vector.y);
        double d2 = sanitize(vector.z);
        double max = Mth.absMax(d, Mth.absMax(d1, d2));
        if (max < 3.051944088384301E-5) {
            buffer.writeByte(0);
        } else {
            long l = Mth.ceilLong(max);
            boolean flag = (l & 3L) != l;
            long l1 = flag ? l & 3L | 4L : l;
            long l2 = pack(d / l) << 3;
            long l3 = pack(d1 / l) << 18;
            long l4 = pack(d2 / l) << 33;
            long l5 = l1 | l2 | l3 | l4;
            buffer.writeByte((byte)l5);
            buffer.writeByte((byte)(l5 >> 8));
            buffer.writeInt((int)(l5 >> 16));
            if (flag) {
                VarInt.write(buffer, (int)(l >> 2));
            }
        }
    }

    private static double sanitize(double value) {
        return Double.isNaN(value) ? 0.0 : Math.clamp(value, -1.7179869183E10, 1.7179869183E10);
    }

    private static long pack(double unpacked) {
        return Math.round((unpacked * 0.5 + 0.5) * 32766.0);
    }

    private static double unpack(long packed) {
        return Math.min((double)(packed & 32767L), 32766.0) * 2.0 / 32766.0 - 1.0;
    }
}
