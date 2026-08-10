package net.minecraft.world.level.storage.loot.predicates;

import com.google.common.collect.Sets;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.storage.loot.IntRange;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.NumberProviders;

public record ValueCheckCondition(NumberProvider provider, IntRange range) implements LootItemCondition {
    public static final MapCodec<ValueCheckCondition> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                NumberProviders.CODEC.fieldOf("value").forGetter(ValueCheckCondition::provider),
                IntRange.CODEC.fieldOf("range").forGetter(ValueCheckCondition::range)
            )
            .apply(instance, ValueCheckCondition::new)
    );

    @Override
    public LootItemConditionType getType() {
        return LootItemConditions.VALUE_CHECK;
    }

    @Override
    public Set<ContextKey<?>> getReferencedContextParams() {
        return Sets.union(this.provider.getReferencedContextParams(), this.range.getReferencedContextParams());
    }

    @Override
    public boolean test(LootContext context) {
        return this.range.test(context, this.provider.getInt(context));
    }

    public static LootItemCondition.Builder hasValue(NumberProvider provider, IntRange range) {
        return () -> new ValueCheckCondition(provider, range);
    }
}
