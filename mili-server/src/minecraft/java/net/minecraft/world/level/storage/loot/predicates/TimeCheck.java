package net.minecraft.world.level.storage.loot.predicates;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.storage.loot.IntRange;
import net.minecraft.world.level.storage.loot.LootContext;

public record TimeCheck(Optional<Long> period, IntRange value) implements LootItemCondition {
    public static final MapCodec<TimeCheck> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                Codec.LONG.optionalFieldOf("period").forGetter(TimeCheck::period), IntRange.CODEC.fieldOf("value").forGetter(TimeCheck::value)
            )
            .apply(instance, TimeCheck::new)
    );

    @Override
    public LootItemConditionType getType() {
        return LootItemConditions.TIME_CHECK;
    }

    @Override
    public Set<ContextKey<?>> getReferencedContextParams() {
        return this.value.getReferencedContextParams();
    }

    @Override
    public boolean test(LootContext context) {
        ServerLevel level = context.getLevel();
        long dayTime = level.getDayTime();
        if (this.period.isPresent()) {
            dayTime %= this.period.get();
        }

        return this.value.test(context, (int)dayTime);
    }

    public static TimeCheck.Builder time(IntRange timeRange) {
        return new TimeCheck.Builder(timeRange);
    }

    public static class Builder implements LootItemCondition.Builder {
        private Optional<Long> period = Optional.empty();
        private final IntRange value;

        public Builder(IntRange value) {
            this.value = value;
        }

        public TimeCheck.Builder setPeriod(long period) {
            this.period = Optional.of(period);
            return this;
        }

        @Override
        public TimeCheck build() {
            return new TimeCheck(this.period, this.value);
        }
    }
}
