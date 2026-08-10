package net.minecraft.world.level.storage.loot;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.NumberProviders;
import org.jspecify.annotations.Nullable;

public class IntRange {
    private static final Codec<IntRange> RECORD_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                NumberProviders.CODEC.optionalFieldOf("min").forGetter(intRange -> Optional.ofNullable(intRange.min)),
                NumberProviders.CODEC.optionalFieldOf("max").forGetter(intRange -> Optional.ofNullable(intRange.max))
            )
            .apply(instance, IntRange::new)
    );
    public static final Codec<IntRange> CODEC = Codec.either(Codec.INT, RECORD_CODEC)
        .xmap(either -> either.map(IntRange::exact, Function.identity()), intRange -> {
            OptionalInt optionalInt = intRange.unpackExact();
            return optionalInt.isPresent() ? Either.left(optionalInt.getAsInt()) : Either.right(intRange);
        });
    private final @Nullable NumberProvider min;
    private final @Nullable NumberProvider max;
    private final IntRange.IntLimiter limiter;
    private final IntRange.IntChecker predicate;

    public Set<ContextKey<?>> getReferencedContextParams() {
        Builder<ContextKey<?>> builder = ImmutableSet.builder();
        if (this.min != null) {
            builder.addAll(this.min.getReferencedContextParams());
        }

        if (this.max != null) {
            builder.addAll(this.max.getReferencedContextParams());
        }

        return builder.build();
    }

    private IntRange(Optional<NumberProvider> min, Optional<NumberProvider> max) {
        this(min.orElse(null), max.orElse(null));
    }

    private IntRange(@Nullable NumberProvider min, @Nullable NumberProvider max) {
        this.min = min;
        this.max = max;
        if (min == null) {
            if (max == null) {
                this.limiter = (lootContext, value) -> value;
                this.predicate = (lootContext, value) -> true;
            } else {
                this.limiter = (lootContext, value) -> Math.min(max.getInt(lootContext), value);
                this.predicate = (lootContext, value) -> value <= max.getInt(lootContext);
            }
        } else if (max == null) {
            this.limiter = (lootContext, value) -> Math.max(min.getInt(lootContext), value);
            this.predicate = (lootContext, value) -> value >= min.getInt(lootContext);
        } else {
            this.limiter = (lootContext, value) -> Mth.clamp(value, min.getInt(lootContext), max.getInt(lootContext));
            this.predicate = (lootContext, value) -> value >= min.getInt(lootContext) && value <= max.getInt(lootContext);
        }
    }

    public static IntRange exact(int exactValue) {
        ConstantValue constantValue = ConstantValue.exactly(exactValue);
        return new IntRange(Optional.of(constantValue), Optional.of(constantValue));
    }

    public static IntRange range(int min, int max) {
        return new IntRange(Optional.of(ConstantValue.exactly(min)), Optional.of(ConstantValue.exactly(max)));
    }

    public static IntRange lowerBound(int min) {
        return new IntRange(Optional.of(ConstantValue.exactly(min)), Optional.empty());
    }

    public static IntRange upperBound(int max) {
        return new IntRange(Optional.empty(), Optional.of(ConstantValue.exactly(max)));
    }

    public int clamp(LootContext lootContext, int value) {
        return this.limiter.apply(lootContext, value);
    }

    public boolean test(LootContext lootContext, int value) {
        return this.predicate.test(lootContext, value);
    }

    private OptionalInt unpackExact() {
        return Objects.equals(this.min, this.max)
                && this.min instanceof ConstantValue constantValue
                && Math.floor(constantValue.value()) == constantValue.value()
            ? OptionalInt.of((int)constantValue.value())
            : OptionalInt.empty();
    }

    @FunctionalInterface
    interface IntChecker {
        boolean test(LootContext lootContext, int value);
    }

    @FunctionalInterface
    interface IntLimiter {
        int apply(LootContext lootContext, int value);
    }
}
