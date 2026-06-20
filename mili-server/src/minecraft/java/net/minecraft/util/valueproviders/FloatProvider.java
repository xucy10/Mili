package net.minecraft.util.valueproviders;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.core.registries.BuiltInRegistries;

public abstract class FloatProvider implements SampledFloat {
    private static final Codec<Either<Float, FloatProvider>> CONSTANT_OR_DISPATCH_CODEC = Codec.either(
        Codec.FLOAT, BuiltInRegistries.FLOAT_PROVIDER_TYPE.byNameCodec().dispatch(FloatProvider::getType, FloatProviderType::codec)
    );
    public static final Codec<FloatProvider> CODEC = CONSTANT_OR_DISPATCH_CODEC.xmap(
        either -> either.map(ConstantFloat::of, floatProvider -> (FloatProvider)floatProvider),
        floatProvider -> floatProvider.getType() == FloatProviderType.CONSTANT
            ? Either.left(((ConstantFloat)floatProvider).getValue())
            : Either.right(floatProvider)
    );

    public static Codec<FloatProvider> codec(float minInclusive, float maxInclusive) {
        return CODEC.validate(
            floatProvider -> {
                if (floatProvider.getMinValue() < minInclusive) {
                    return DataResult.error(
                        () -> "Value provider too low: " + minInclusive + " [" + floatProvider.getMinValue() + "-" + floatProvider.getMaxValue() + "]"
                    );
                } else {
                    return floatProvider.getMaxValue() > maxInclusive
                        ? DataResult.error(
                            () -> "Value provider too high: " + maxInclusive + " [" + floatProvider.getMinValue() + "-" + floatProvider.getMaxValue() + "]"
                        )
                        : DataResult.success(floatProvider);
                }
            }
        );
    }

    public abstract float getMinValue();

    public abstract float getMaxValue();

    public abstract FloatProviderType<?> getType();
}
