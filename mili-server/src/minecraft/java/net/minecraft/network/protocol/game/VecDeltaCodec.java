package net.minecraft.network.protocol.game;

import com.google.common.annotations.VisibleForTesting;
import net.minecraft.world.phys.Vec3;

public class VecDeltaCodec {
    private static final double TRUNCATION_STEPS = 4096.0;
    public Vec3 base = Vec3.ZERO; // Paper

    @VisibleForTesting
    static long encode(double value) {
        return Math.round(value * 4096.0);
    }

    @VisibleForTesting
    static double decode(long value) {
        return value / 4096.0;
    }

    public Vec3 decode(long x, long y, long z) {
        if (x == 0L && y == 0L && z == 0L) {
            return this.base;
        } else {
            double d = x == 0L ? this.base.x : decode(encode(this.base.x) + x);
            double d1 = y == 0L ? this.base.y : decode(encode(this.base.y) + y);
            double d2 = z == 0L ? this.base.z : decode(encode(this.base.z) + z);
            return new Vec3(d, d1, d2);
        }
    }

    public long encodeX(Vec3 value) {
        return encode(value.x) - encode(this.base.x);
    }

    public long encodeY(Vec3 value) {
        return encode(value.y) - encode(this.base.y);
    }

    public long encodeZ(Vec3 value) {
        return encode(value.z) - encode(this.base.z);
    }

    public Vec3 delta(Vec3 value) {
        return value.subtract(this.base);
    }

    public void setBase(Vec3 base) {
        this.base = base;
    }

    public Vec3 getBase() {
        return this.base;
    }
}
