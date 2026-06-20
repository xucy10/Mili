package net.minecraft.world.attribute.modifier;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record FloatWithAlpha(float value, float alpha) {
    private static final Codec<FloatWithAlpha> FULL_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                Codec.FLOAT.fieldOf("value").forGetter(FloatWithAlpha::value),
                Codec.floatRange(0.0F, 1.0F).optionalFieldOf("alpha", 1.0F).forGetter(FloatWithAlpha::alpha)
            )
            .apply(instance, FloatWithAlpha::new)
    );
    public static final Codec<FloatWithAlpha> CODEC = Codec.either(Codec.FLOAT, FULL_CODEC)
        .xmap(
            either -> either.map(FloatWithAlpha::new, floatWithAlpha -> (FloatWithAlpha)floatWithAlpha),
            floatWithAlpha -> floatWithAlpha.alpha() == 1.0F ? Either.left(floatWithAlpha.value()) : Either.right(floatWithAlpha)
        );

    public FloatWithAlpha(float value) {
        this(value, 1.0F);
    }
}
