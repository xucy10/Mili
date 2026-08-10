package net.minecraft.world.attribute;

import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

public interface LerpFunction<T> {
    static LerpFunction<Float> ofFloat() {
        return Mth::lerp;
    }

    static LerpFunction<Float> ofDegrees(float maxDelta) {
        return (delta, start, end) -> {
            float f = Mth.wrapDegrees(end - start);
            return Math.abs(f) >= maxDelta ? end : start + delta * f;
        };
    }

    static <T> LerpFunction<T> ofConstant() {
        return (delta, start, end) -> start;
    }

    static <T> LerpFunction<T> ofStep(float threshold) {
        return (delta, start, end) -> delta >= threshold ? end : start;
    }

    static LerpFunction<Integer> ofColor() {
        return ARGB::srgbLerp;
    }

    T apply(float delta, T start, T end);
}
