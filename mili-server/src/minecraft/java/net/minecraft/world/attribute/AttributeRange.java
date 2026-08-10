package net.minecraft.world.attribute;

import com.mojang.serialization.DataResult;
import net.minecraft.util.Mth;

public interface AttributeRange<Value> {
    AttributeRange<Float> UNIT_FLOAT = ofFloat(0.0F, 1.0F);
    AttributeRange<Float> NON_NEGATIVE_FLOAT = ofFloat(0.0F, Float.POSITIVE_INFINITY);

    static <Value> AttributeRange<Value> any() {
        return new AttributeRange<Value>() {
            @Override
            public DataResult<Value> validate(Value value) {
                return DataResult.success(value);
            }

            @Override
            public Value sanitize(Value value) {
                return value;
            }
        };
    }

    static AttributeRange<Float> ofFloat(final float min, final float max) {
        return new AttributeRange<Float>() {
            @Override
            public DataResult<Float> validate(Float value) {
                return value >= min && value <= max ? DataResult.success(value) : DataResult.error(() -> value + " is not in range [" + min + "; " + max + "]");
            }

            @Override
            public Float sanitize(Float value) {
                return value >= min && value <= max ? value : Mth.clamp(value, min, max);
            }
        };
    }

    DataResult<Value> validate(Value value);

    Value sanitize(Value value);
}
