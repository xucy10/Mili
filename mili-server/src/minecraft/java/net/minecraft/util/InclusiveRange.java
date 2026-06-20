package net.minecraft.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.function.Function;

public record InclusiveRange<T extends Comparable<T>>(T minInclusive, T maxInclusive) {
    public static final Codec<InclusiveRange<Integer>> INT = codec(Codec.INT);

    public InclusiveRange(T minInclusive, T maxInclusive) {
        if (minInclusive.compareTo(maxInclusive) > 0) {
            throw new IllegalArgumentException("min_inclusive must be less than or equal to max_inclusive");
        } else {
            this.minInclusive = minInclusive;
            this.maxInclusive = maxInclusive;
        }
    }

    public InclusiveRange(T value) {
        this(value, value);
    }

    public static <T extends Comparable<T>> Codec<InclusiveRange<T>> codec(Codec<T> codec) {
        return ExtraCodecs.intervalCodec(
            codec, "min_inclusive", "max_inclusive", InclusiveRange::create, InclusiveRange::minInclusive, InclusiveRange::maxInclusive
        );
    }

    public static <T extends Comparable<T>> Codec<InclusiveRange<T>> codec(Codec<T> codec, T min, T max) {
        return codec(codec)
            .validate(
                value -> {
                    if (value.minInclusive().compareTo(min) < 0) {
                        return DataResult.error(
                            () -> "Range limit too low, expected at least " + min + " [" + value.minInclusive() + "-" + value.maxInclusive() + "]"
                        );
                    } else {
                        return value.maxInclusive().compareTo(max) > 0
                            ? DataResult.error(
                                () -> "Range limit too high, expected at most " + max + " [" + value.minInclusive() + "-" + value.maxInclusive() + "]"
                            )
                            : DataResult.success(value);
                    }
                }
            );
    }

    public static <T extends Comparable<T>> DataResult<InclusiveRange<T>> create(T min, T max) {
        return min.compareTo(max) <= 0
            ? DataResult.success(new InclusiveRange<>(min, max))
            : DataResult.error(() -> "min_inclusive must be less than or equal to max_inclusive");
    }

    public <S extends Comparable<S>> InclusiveRange<S> map(Function<? super T, ? extends S> mapper) {
        return new InclusiveRange<>((S)mapper.apply(this.minInclusive), (S)mapper.apply(this.maxInclusive));
    }

    public boolean isValueInRange(T value) {
        return value.compareTo(this.minInclusive) >= 0 && value.compareTo(this.maxInclusive) <= 0;
    }

    public boolean contains(InclusiveRange<T> value) {
        return value.minInclusive().compareTo(this.minInclusive) >= 0 && value.maxInclusive.compareTo(this.maxInclusive) <= 0;
    }

    @Override
    public String toString() {
        return "[" + this.minInclusive + ", " + this.maxInclusive + "]";
    }
}
