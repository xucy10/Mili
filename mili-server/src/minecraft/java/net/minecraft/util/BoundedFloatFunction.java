package net.minecraft.util;

import it.unimi.dsi.fastutil.floats.Float2FloatFunction;
import java.util.function.Function;

public interface BoundedFloatFunction<C> {
    BoundedFloatFunction<Float> IDENTITY = createUnlimited(f -> f);

    float apply(C value);

    float minValue();

    float maxValue();

    static BoundedFloatFunction<Float> createUnlimited(final Float2FloatFunction operation) {
        return new BoundedFloatFunction<Float>() {
            @Override
            public float apply(Float value) {
                return operation.apply(value);
            }

            @Override
            public float minValue() {
                return Float.NEGATIVE_INFINITY;
            }

            @Override
            public float maxValue() {
                return Float.POSITIVE_INFINITY;
            }
        };
    }

    default <C2> BoundedFloatFunction<C2> comap(final Function<C2, C> mapper) {
        final BoundedFloatFunction<C> boundedFloatFunction = this;
        return new BoundedFloatFunction<C2>() {
            @Override
            public float apply(C2 value) {
                return boundedFloatFunction.apply(mapper.apply(value));
            }

            @Override
            public float minValue() {
                return boundedFloatFunction.minValue();
            }

            @Override
            public float maxValue() {
                return boundedFloatFunction.maxValue();
            }
        };
    }
}
