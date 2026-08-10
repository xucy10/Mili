package net.minecraft.world.level.storage.loot.entries;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class LootItem extends LootPoolSingletonContainer {
    public static final MapCodec<LootItem> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(Item.CODEC.fieldOf("name").forGetter(lootItem -> lootItem.item))
            .and(singletonFields(instance))
            .apply(instance, LootItem::new)
    );
    private final Holder<Item> item;

    private LootItem(Holder<Item> item, int weight, int quality, List<LootItemCondition> conditions, List<LootItemFunction> functions) {
        super(weight, quality, conditions, functions);
        this.item = item;
    }

    @Override
    public LootPoolEntryType getType() {
        return LootPoolEntries.ITEM;
    }

    @Override
    public void createItemStack(Consumer<ItemStack> stackConsumer, LootContext lootContext) {
        stackConsumer.accept(new ItemStack(this.item));
    }

    public static LootPoolSingletonContainer.Builder<?> lootTableItem(ItemLike item) {
        return simpleBuilder(
            (weight, quality, conditions, functions) -> new LootItem(item.asItem().builtInRegistryHolder(), weight, quality, conditions, functions)
        );
    }
}
