package net.minecraft.world.level.storage.loot.entries;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class EmptyLootItem extends LootPoolSingletonContainer {
    public static final MapCodec<EmptyLootItem> CODEC = RecordCodecBuilder.mapCodec(instance -> singletonFields(instance).apply(instance, EmptyLootItem::new));

    private EmptyLootItem(int weight, int quality, List<LootItemCondition> conditions, List<LootItemFunction> functions) {
        super(weight, quality, conditions, functions);
    }

    @Override
    public LootPoolEntryType getType() {
        return LootPoolEntries.EMPTY;
    }

    @Override
    public void createItemStack(Consumer<ItemStack> stackConsumer, LootContext lootContext) {
    }

    public static LootPoolSingletonContainer.Builder<?> emptyItem() {
        return simpleBuilder(EmptyLootItem::new);
    }
}
