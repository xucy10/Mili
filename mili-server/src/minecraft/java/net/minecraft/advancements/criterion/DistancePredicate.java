package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Mth;

public record DistancePredicate(
    MinMaxBounds.Doubles x, MinMaxBounds.Doubles y, MinMaxBounds.Doubles z, MinMaxBounds.Doubles horizontal, MinMaxBounds.Doubles absolute
) {
    public static final Codec<DistancePredicate> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                MinMaxBounds.Doubles.CODEC.optionalFieldOf("x", MinMaxBounds.Doubles.ANY).forGetter(DistancePredicate::x),
                MinMaxBounds.Doubles.CODEC.optionalFieldOf("y", MinMaxBounds.Doubles.ANY).forGetter(DistancePredicate::y),
                MinMaxBounds.Doubles.CODEC.optionalFieldOf("z", MinMaxBounds.Doubles.ANY).forGetter(DistancePredicate::z),
                MinMaxBounds.Doubles.CODEC.optionalFieldOf("horizontal", MinMaxBounds.Doubles.ANY).forGetter(DistancePredicate::horizontal),
                MinMaxBounds.Doubles.CODEC.optionalFieldOf("absolute", MinMaxBounds.Doubles.ANY).forGetter(DistancePredicate::absolute)
            )
            .apply(instance, DistancePredicate::new)
    );

    public static DistancePredicate horizontal(MinMaxBounds.Doubles horizontal) {
        return new DistancePredicate(MinMaxBounds.Doubles.ANY, MinMaxBounds.Doubles.ANY, MinMaxBounds.Doubles.ANY, horizontal, MinMaxBounds.Doubles.ANY);
    }

    public static DistancePredicate vertical(MinMaxBounds.Doubles vertical) {
        return new DistancePredicate(MinMaxBounds.Doubles.ANY, vertical, MinMaxBounds.Doubles.ANY, MinMaxBounds.Doubles.ANY, MinMaxBounds.Doubles.ANY);
    }

    public static DistancePredicate absolute(MinMaxBounds.Doubles absolute) {
        return new DistancePredicate(MinMaxBounds.Doubles.ANY, MinMaxBounds.Doubles.ANY, MinMaxBounds.Doubles.ANY, MinMaxBounds.Doubles.ANY, absolute);
    }

    public boolean matches(double x1, double y1, double z1, double x2, double y2, double z2) {
        float f = (float)(x1 - x2);
        float f1 = (float)(y1 - y2);
        float f2 = (float)(z1 - z2);
        return this.x.matches(Mth.abs(f))
            && this.y.matches(Mth.abs(f1))
            && this.z.matches(Mth.abs(f2))
            && this.horizontal.matchesSqr(f * f + f2 * f2)
            && this.absolute.matchesSqr(f * f + f1 * f1 + f2 * f2);
    }
}
