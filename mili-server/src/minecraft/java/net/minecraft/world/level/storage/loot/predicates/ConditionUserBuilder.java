package net.minecraft.world.level.storage.loot.predicates;

import java.util.function.Function;

public interface ConditionUserBuilder<T extends ConditionUserBuilder<T>> {
    T when(LootItemCondition.Builder conditionBuilder);

    default <E> T when(Iterable<E> builderSources, Function<E, LootItemCondition.Builder> toBuilderFunction) {
        T conditionUserBuilder = this.unwrap();

        for (E object : builderSources) {
            conditionUserBuilder = conditionUserBuilder.when(toBuilderFunction.apply(object));
        }

        return conditionUserBuilder;
    }

    T unwrap();
}
