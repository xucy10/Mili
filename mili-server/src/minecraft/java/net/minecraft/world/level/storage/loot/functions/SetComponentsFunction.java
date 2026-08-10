package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class SetComponentsFunction extends LootItemConditionalFunction {
    public static final MapCodec<SetComponentsFunction> CODEC = RecordCodecBuilder.mapCodec(
        instance -> commonFields(instance)
            .and(DataComponentPatch.CODEC.fieldOf("components").forGetter(setComponentsFunction -> setComponentsFunction.components))
            .apply(instance, SetComponentsFunction::new)
    );
    private final DataComponentPatch components;

    private SetComponentsFunction(List<LootItemCondition> predicates, DataComponentPatch components) {
        super(predicates);
        this.components = components;
    }

    @Override
    public LootItemFunctionType<SetComponentsFunction> getType() {
        return LootItemFunctions.SET_COMPONENTS;
    }

    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        stack.applyComponentsAndValidate(this.components);
        return stack;
    }

    public static <T> LootItemConditionalFunction.Builder<?> setComponent(DataComponentType<T> component, T value) {
        return simpleBuilder(list -> new SetComponentsFunction(list, DataComponentPatch.builder().set(component, value).build()));
    }
}
