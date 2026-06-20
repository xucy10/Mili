package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.Nameable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootContextArg;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class CopyNameFunction extends LootItemConditionalFunction {
    public static final MapCodec<CopyNameFunction> CODEC = RecordCodecBuilder.mapCodec(
        instance -> commonFields(instance)
            .and(LootContextArg.ENTITY_OR_BLOCK.fieldOf("source").forGetter(copyNameFunction -> copyNameFunction.source))
            .apply(instance, CopyNameFunction::new)
    );
    private final LootContextArg<Object> source;

    private CopyNameFunction(List<LootItemCondition> predicates, LootContextArg<?> source) {
        super(predicates);
        this.source = LootContextArg.cast((LootContextArg<? extends Object>)source);
    }

    @Override
    public LootItemFunctionType<CopyNameFunction> getType() {
        return LootItemFunctions.COPY_NAME;
    }

    @Override
    public Set<ContextKey<?>> getReferencedContextParams() {
        return Set.of(this.source.contextParam());
    }

    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        if (this.source.get(context) instanceof Nameable nameable) {
            stack.set(DataComponents.CUSTOM_NAME, nameable.getCustomName());
        }

        return stack;
    }

    public static LootItemConditionalFunction.Builder<?> copyName(LootContextArg<?> source) {
        return simpleBuilder(predicates -> new CopyNameFunction(predicates, source));
    }
}
