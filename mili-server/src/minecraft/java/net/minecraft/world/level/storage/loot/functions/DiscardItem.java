package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class DiscardItem extends LootItemConditionalFunction {
    public static final MapCodec<DiscardItem> CODEC = RecordCodecBuilder.mapCodec(instance -> commonFields(instance).apply(instance, DiscardItem::new));

    protected DiscardItem(List<LootItemCondition> predicates) {
        super(predicates);
    }

    @Override
    public LootItemFunctionType<DiscardItem> getType() {
        return LootItemFunctions.DISCARD;
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        return ItemStack.EMPTY;
    }

    public static LootItemConditionalFunction.Builder<?> discardItem() {
        return simpleBuilder(DiscardItem::new);
    }
}
