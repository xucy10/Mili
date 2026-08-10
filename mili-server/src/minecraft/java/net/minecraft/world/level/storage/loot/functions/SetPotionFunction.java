package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class SetPotionFunction extends LootItemConditionalFunction {
    public static final MapCodec<SetPotionFunction> CODEC = RecordCodecBuilder.mapCodec(
        instance -> commonFields(instance)
            .and(Potion.CODEC.fieldOf("id").forGetter(setPotionFunction -> setPotionFunction.potion))
            .apply(instance, SetPotionFunction::new)
    );
    private final Holder<Potion> potion;

    private SetPotionFunction(List<LootItemCondition> predicates, Holder<Potion> potion) {
        super(predicates);
        this.potion = potion;
    }

    @Override
    public LootItemFunctionType<SetPotionFunction> getType() {
        return LootItemFunctions.SET_POTION;
    }

    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        stack.update(DataComponents.POTION_CONTENTS, PotionContents.EMPTY, this.potion, PotionContents::withPotion);
        return stack;
    }

    public static LootItemConditionalFunction.Builder<?> setPotion(Holder<Potion> potion) {
        return simpleBuilder(list -> new SetPotionFunction(list, potion));
    }
}
