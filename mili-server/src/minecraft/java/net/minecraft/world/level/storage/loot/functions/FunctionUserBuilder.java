package net.minecraft.world.level.storage.loot.functions;

import java.util.Arrays;
import java.util.function.Function;

public interface FunctionUserBuilder<T extends FunctionUserBuilder<T>> {
    T apply(LootItemFunction.Builder functionBuilder);

    default <E> T apply(Iterable<E> builderSources, Function<E, LootItemFunction.Builder> toBuilderFunction) {
        T functionUserBuilder = this.unwrap();

        for (E object : builderSources) {
            functionUserBuilder = functionUserBuilder.apply(toBuilderFunction.apply(object));
        }

        return functionUserBuilder;
    }

    default <E> T apply(E[] builderSources, Function<E, LootItemFunction.Builder> toBuilderFunction) {
        return this.apply(Arrays.asList(builderSources), toBuilderFunction);
    }

    T unwrap();
}
