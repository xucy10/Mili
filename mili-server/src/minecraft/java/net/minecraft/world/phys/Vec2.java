package net.minecraft.world.phys;

import com.mojang.serialization.Codec;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

public class Vec2 {
    public static final Vec2 ZERO = new Vec2(0.0F, 0.0F);
    public static final Vec2 ONE = new Vec2(1.0F, 1.0F);
    public static final Vec2 UNIT_X = new Vec2(1.0F, 0.0F);
    public static final Vec2 NEG_UNIT_X = new Vec2(-1.0F, 0.0F);
    public static final Vec2 UNIT_Y = new Vec2(0.0F, 1.0F);
    public static final Vec2 NEG_UNIT_Y = new Vec2(0.0F, -1.0F);
    public static final Vec2 MAX = new Vec2(Float.MAX_VALUE, Float.MAX_VALUE);
    public static final Vec2 MIN = new Vec2(Float.MIN_VALUE, Float.MIN_VALUE);
    public static final Codec<Vec2> CODEC = Codec.FLOAT
        .listOf()
        .comapFlatMap(list -> Util.fixedSize((List<Float>)list, 2).map(list1 -> new Vec2(list1.get(0), list1.get(1))), vec2 -> List.of(vec2.x, vec2.y));
    public final float x;
    public final float y;

    public Vec2(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public Vec2 scale(float factor) {
        return new Vec2(this.x * factor, this.y * factor);
    }

    public float dot(Vec2 other) {
        return this.x * other.x + this.y * other.y;
    }

    public Vec2 add(Vec2 other) {
        return new Vec2(this.x + other.x, this.y + other.y);
    }

    public Vec2 add(float value) {
        return new Vec2(this.x + value, this.y + value);
    }

    public boolean equals(Vec2 other) {
        return this.x == other.x && this.y == other.y;
    }

    public Vec2 normalized() {
        float squareRoot = Mth.sqrt(this.x * this.x + this.y * this.y);
        return squareRoot < 1.0E-4F ? ZERO : new Vec2(this.x / squareRoot, this.y / squareRoot);
    }

    public float length() {
        return Mth.sqrt(this.x * this.x + this.y * this.y);
    }

    public float lengthSquared() {
        return this.x * this.x + this.y * this.y;
    }

    public float distanceToSqr(Vec2 other) {
        float f = other.x - this.x;
        float f1 = other.y - this.y;
        return f * f + f1 * f1;
    }

    public Vec2 negated() {
        return new Vec2(-this.x, -this.y);
    }
}
