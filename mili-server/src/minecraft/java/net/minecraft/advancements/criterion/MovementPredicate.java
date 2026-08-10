package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Mth;

public record MovementPredicate(
    MinMaxBounds.Doubles x,
    MinMaxBounds.Doubles y,
    MinMaxBounds.Doubles z,
    MinMaxBounds.Doubles speed,
    MinMaxBounds.Doubles horizontalSpeed,
    MinMaxBounds.Doubles verticalSpeed,
    MinMaxBounds.Doubles fallDistance
) {
    public static final Codec<MovementPredicate> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                MinMaxBounds.Doubles.CODEC.optionalFieldOf("x", MinMaxBounds.Doubles.ANY).forGetter(MovementPredicate::x),
                MinMaxBounds.Doubles.CODEC.optionalFieldOf("y", MinMaxBounds.Doubles.ANY).forGetter(MovementPredicate::y),
                MinMaxBounds.Doubles.CODEC.optionalFieldOf("z", MinMaxBounds.Doubles.ANY).forGetter(MovementPredicate::z),
                MinMaxBounds.Doubles.CODEC.optionalFieldOf("speed", MinMaxBounds.Doubles.ANY).forGetter(MovementPredicate::speed),
                MinMaxBounds.Doubles.CODEC.optionalFieldOf("horizontal_speed", MinMaxBounds.Doubles.ANY).forGetter(MovementPredicate::horizontalSpeed),
                MinMaxBounds.Doubles.CODEC.optionalFieldOf("vertical_speed", MinMaxBounds.Doubles.ANY).forGetter(MovementPredicate::verticalSpeed),
                MinMaxBounds.Doubles.CODEC.optionalFieldOf("fall_distance", MinMaxBounds.Doubles.ANY).forGetter(MovementPredicate::fallDistance)
            )
            .apply(instance, MovementPredicate::new)
    );

    public static MovementPredicate speed(MinMaxBounds.Doubles speed) {
        return new MovementPredicate(
            MinMaxBounds.Doubles.ANY,
            MinMaxBounds.Doubles.ANY,
            MinMaxBounds.Doubles.ANY,
            speed,
            MinMaxBounds.Doubles.ANY,
            MinMaxBounds.Doubles.ANY,
            MinMaxBounds.Doubles.ANY
        );
    }

    public static MovementPredicate horizontalSpeed(MinMaxBounds.Doubles horizontalSpeed) {
        return new MovementPredicate(
            MinMaxBounds.Doubles.ANY,
            MinMaxBounds.Doubles.ANY,
            MinMaxBounds.Doubles.ANY,
            MinMaxBounds.Doubles.ANY,
            horizontalSpeed,
            MinMaxBounds.Doubles.ANY,
            MinMaxBounds.Doubles.ANY
        );
    }

    public static MovementPredicate verticalSpeed(MinMaxBounds.Doubles verticalSpeed) {
        return new MovementPredicate(
            MinMaxBounds.Doubles.ANY,
            MinMaxBounds.Doubles.ANY,
            MinMaxBounds.Doubles.ANY,
            MinMaxBounds.Doubles.ANY,
            MinMaxBounds.Doubles.ANY,
            verticalSpeed,
            MinMaxBounds.Doubles.ANY
        );
    }

    public static MovementPredicate fallDistance(MinMaxBounds.Doubles fallDistance) {
        return new MovementPredicate(
            MinMaxBounds.Doubles.ANY,
            MinMaxBounds.Doubles.ANY,
            MinMaxBounds.Doubles.ANY,
            MinMaxBounds.Doubles.ANY,
            MinMaxBounds.Doubles.ANY,
            MinMaxBounds.Doubles.ANY,
            fallDistance
        );
    }

    public boolean matches(double x, double y, double z, double fallDistance) {
        if (this.x.matches(x) && this.y.matches(y) && this.z.matches(z)) {
            double d = Mth.lengthSquared(x, y, z);
            if (!this.speed.matchesSqr(d)) {
                return false;
            } else {
                double d1 = Mth.lengthSquared(x, z);
                if (!this.horizontalSpeed.matchesSqr(d1)) {
                    return false;
                } else {
                    double abs = Math.abs(y);
                    return this.verticalSpeed.matches(abs) && this.fallDistance.matches(fallDistance);
                }
            }
        } else {
            return false;
        }
    }
}
