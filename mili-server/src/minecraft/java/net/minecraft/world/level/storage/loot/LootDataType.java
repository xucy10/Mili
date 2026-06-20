package net.minecraft.world.level.storage.loot;

import com.mojang.serialization.Codec;
import java.util.stream.Stream;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctions;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public record LootDataType<T>(ResourceKey<Registry<T>> registryKey, Codec<T> codec, LootDataType.Validator<T> validator) {
    public static final LootDataType<LootItemCondition> PREDICATE = new LootDataType<>(
        Registries.PREDICATE, LootItemCondition.DIRECT_CODEC, createSimpleValidator()
    );
    public static final LootDataType<LootItemFunction> MODIFIER = new LootDataType<>(
        Registries.ITEM_MODIFIER, LootItemFunctions.ROOT_CODEC, createSimpleValidator()
    );
    public static final LootDataType<LootTable> TABLE = new LootDataType<>(Registries.LOOT_TABLE, LootTable.DIRECT_CODEC, createLootTableValidator());

    public void runValidation(ValidationContext context, ResourceKey<T> key, T value) {
        this.validator.run(context, key, value);
    }

    public static Stream<LootDataType<?>> values() {
        return Stream.of(PREDICATE, MODIFIER, TABLE);
    }

    private static <T extends LootContextUser> LootDataType.Validator<T> createSimpleValidator() {
        return (context, key, value) -> value.validate(context.enterElement(new ProblemReporter.RootElementPathElement(key), key));
    }

    private static LootDataType.Validator<LootTable> createLootTableValidator() {
        // CraftBukkit start
        return (context, key, value) -> {
            value.validate(
                context.setContextKeySet(value.getParamSet()).enterElement(new ProblemReporter.RootElementPathElement(key), key)
            );
            value.craftLootTable = new org.bukkit.craftbukkit.CraftLootTable(org.bukkit.craftbukkit.util.CraftNamespacedKey.fromMinecraft(key.identifier()), value);
            // CraftBukkit end
        };
    }

    @FunctionalInterface
    public interface Validator<T> {
        void run(ValidationContext context, ResourceKey<T> key, T value);
    }
}
