package net.minecraft.world.attribute.modifier;

import com.mojang.serialization.Codec;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.LerpFunction;

public interface FloatModifier<Argument> extends AttributeModifier<Float, Argument> {
    FloatModifier<FloatWithAlpha> ALPHA_BLEND = new FloatModifier<FloatWithAlpha>() {
        @Override
        public Float apply(Float subject, FloatWithAlpha argument) {
            return Mth.lerp(argument.alpha(), subject, argument.value());
        }

        @Override
        public Codec<FloatWithAlpha> argumentCodec(EnvironmentAttribute<Float> attribute) {
            return FloatWithAlpha.CODEC;
        }

        @Override
        public LerpFunction<FloatWithAlpha> argumentKeyframeLerp(EnvironmentAttribute<Float> attribute) {
            return (delta, start, end) -> new FloatWithAlpha(Mth.lerp(delta, start.value(), end.value()), Mth.lerp(delta, start.alpha(), end.alpha()));
        }
    };
    FloatModifier<Float> ADD = (FloatModifier.Simple) Float::sum;
    FloatModifier<Float> SUBTRACT = (FloatModifier.Simple)(_float, _float1) -> _float - _float1;
    FloatModifier<Float> MULTIPLY = (FloatModifier.Simple)(_float, _float1) -> _float * _float1;
    FloatModifier<Float> MINIMUM = (FloatModifier.Simple) Math::min;
    FloatModifier<Float> MAXIMUM = (FloatModifier.Simple) Math::max;

    @FunctionalInterface
    public interface Simple extends FloatModifier<Float> {
        @Override
        default Codec<Float> argumentCodec(EnvironmentAttribute<Float> attribute) {
            return Codec.FLOAT;
        }

        @Override
        default LerpFunction<Float> argumentKeyframeLerp(EnvironmentAttribute<Float> attribute) {
            return LerpFunction.ofFloat();
        }
    }
}
