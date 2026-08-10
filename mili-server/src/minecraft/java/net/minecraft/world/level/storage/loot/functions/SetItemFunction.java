package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class SetItemFunction extends LootItemConditionalFunction {
    public static final MapCodec<SetItemFunction> CODEC = RecordCodecBuilder.mapCodec(
        instance -> commonFields(instance)
            .and(Item.CODEC.fieldOf("item").forGetter(setItemFunction -> setItemFunction.item))
            .apply(instance, SetItemFunction::new)
    );
    private final Holder<Item> item;

    private SetItemFunction(List<LootItemCondition> predicates, Holder<Item> item) {
        super(predicates);
        this.item = item;
    }

    @Override
    public LootItemFunctionType<SetItemFunction> getType() {
        return LootItemFunctions.SET_ITEM;
    }

    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        return stack.transmuteCopy(this.item.value());
    }
}
