package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Set;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.NumberProviders;

public class SetItemCountFunction extends LootItemConditionalFunction {
    public static final MapCodec<SetItemCountFunction> CODEC = RecordCodecBuilder.mapCodec(
        instance -> commonFields(instance)
            .and(
                instance.group(
                    NumberProviders.CODEC.fieldOf("count").forGetter(setItemCountFunction -> setItemCountFunction.value),
                    Codec.BOOL.fieldOf("add").orElse(false).forGetter(setItemCountFunction -> setItemCountFunction.add)
                )
            )
            .apply(instance, SetItemCountFunction::new)
    );
    private final NumberProvider value;
    private final boolean add;

    private SetItemCountFunction(List<LootItemCondition> predicates, NumberProvider value, boolean add) {
        super(predicates);
        this.value = value;
        this.add = add;
    }

    @Override
    public LootItemFunctionType<SetItemCountFunction> getType() {
        return LootItemFunctions.SET_COUNT;
    }

    @Override
    public Set<ContextKey<?>> getReferencedContextParams() {
        return this.value.getReferencedContextParams();
    }

    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        int i = this.add ? stack.getCount() : 0;
        stack.setCount(i + this.value.getInt(context));
        return stack;
    }

    public static LootItemConditionalFunction.Builder<?> setCount(NumberProvider countValue) {
        return simpleBuilder(list -> new SetItemCountFunction(list, countValue, false));
    }

    public static LootItemConditionalFunction.Builder<?> setCount(NumberProvider countValue, boolean add) {
        return simpleBuilder(list -> new SetItemCountFunction(list, countValue, add));
    }
}
