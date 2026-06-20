package net.minecraft.world.attribute.modifier;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ARGB;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.LerpFunction;

public interface ColorModifier<Argument> extends AttributeModifier<Integer, Argument> {
    ColorModifier<Integer> ALPHA_BLEND = new ColorModifier<Integer>() {
        @Override
        public Integer apply(Integer subject, Integer argument) {
            return ARGB.alphaBlend(subject, argument);
        }

        @Override
        public Codec<Integer> argumentCodec(EnvironmentAttribute<Integer> attribute) {
            return ExtraCodecs.STRING_ARGB_COLOR;
        }

        @Override
        public LerpFunction<Integer> argumentKeyframeLerp(EnvironmentAttribute<Integer> attribute) {
            return LerpFunction.ofColor();
        }
    };
    ColorModifier<Integer> ADD = (RgbModifier) ARGB::addRgb;
    ColorModifier<Integer> SUBTRACT = (RgbModifier) ARGB::subtractRgb;
    ColorModifier<Integer> MULTIPLY_RGB = (RgbModifier) ARGB::multiply;
    ColorModifier<Integer> MULTIPLY_ARGB = (ArgbModifier) ARGB::multiply;
    ColorModifier<ColorModifier.BlendToGray> BLEND_TO_GRAY = new ColorModifier<ColorModifier.BlendToGray>() {
        @Override
        public Integer apply(Integer subject, ColorModifier.BlendToGray argument) {
            int i = ARGB.scaleRGB(ARGB.greyscale(subject), argument.brightness);
            return ARGB.srgbLerp(argument.factor, subject, i);
        }

        @Override
        public Codec<ColorModifier.BlendToGray> argumentCodec(EnvironmentAttribute<Integer> attribute) {
            return ColorModifier.BlendToGray.CODEC;
        }

        @Override
        public LerpFunction<ColorModifier.BlendToGray> argumentKeyframeLerp(EnvironmentAttribute<Integer> attribute) {
            return (delta, start, end) -> new ColorModifier.BlendToGray(
                Mth.lerp(delta, start.brightness, end.brightness), Mth.lerp(delta, start.factor, end.factor)
            );
        }
    };

    @FunctionalInterface
    public interface ArgbModifier extends ColorModifier<Integer> {
        @Override
        default Codec<Integer> argumentCodec(EnvironmentAttribute<Integer> attribute) {
            return Codec.either(ExtraCodecs.STRING_ARGB_COLOR, ExtraCodecs.RGB_COLOR_CODEC)
                .xmap(Either::unwrap, integer -> ARGB.alpha(integer) == 255 ? Either.right(integer) : Either.left(integer));
        }

        @Override
        default LerpFunction<Integer> argumentKeyframeLerp(EnvironmentAttribute<Integer> attribute) {
            return LerpFunction.ofColor();
        }
    }

    public record BlendToGray(float brightness, float factor) {
        public static final Codec<ColorModifier.BlendToGray> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.floatRange(0.0F, 1.0F).fieldOf("brightness").forGetter(ColorModifier.BlendToGray::brightness),
                    Codec.floatRange(0.0F, 1.0F).fieldOf("factor").forGetter(ColorModifier.BlendToGray::factor)
                )
                .apply(instance, ColorModifier.BlendToGray::new)
        );
    }

    @FunctionalInterface
    public interface RgbModifier extends ColorModifier<Integer> {
        @Override
        default Codec<Integer> argumentCodec(EnvironmentAttribute<Integer> attribute) {
            return ExtraCodecs.STRING_RGB_COLOR;
        }

        @Override
        default LerpFunction<Integer> argumentKeyframeLerp(EnvironmentAttribute<Integer> attribute) {
            return LerpFunction.ofColor();
        }
    }
}
