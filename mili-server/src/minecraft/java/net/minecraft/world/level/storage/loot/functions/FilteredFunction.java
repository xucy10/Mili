package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.advancements.criterion.ItemPredicate;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class FilteredFunction extends LootItemConditionalFunction {
    public static final MapCodec<FilteredFunction> CODEC = RecordCodecBuilder.mapCodec(
        instance -> commonFields(instance)
            .and(
                instance.group(
                    ItemPredicate.CODEC.fieldOf("item_filter").forGetter(filteredFunction -> filteredFunction.filter),
                    LootItemFunctions.ROOT_CODEC.optionalFieldOf("on_pass").forGetter(filteredFunction -> filteredFunction.onPass),
                    LootItemFunctions.ROOT_CODEC.optionalFieldOf("on_fail").forGetter(filteredFunction -> filteredFunction.onFail)
                )
            )
            .apply(instance, FilteredFunction::new)
    );
    private final ItemPredicate filter;
    private final Optional<LootItemFunction> onPass;
    private final Optional<LootItemFunction> onFail;

    FilteredFunction(List<LootItemCondition> predicates, ItemPredicate filter, Optional<LootItemFunction> onPass, Optional<LootItemFunction> onFail) {
        super(predicates);
        this.filter = filter;
        this.onPass = onPass;
        this.onFail = onFail;
    }

    @Override
    public LootItemFunctionType<FilteredFunction> getType() {
        return LootItemFunctions.FILTERED;
    }

    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        Optional<LootItemFunction> optional = this.filter.test(stack) ? this.onPass : this.onFail;
        return optional.isPresent() ? optional.get().apply(stack, context) : stack;
    }

    @Override
    public void validate(ValidationContext context) {
        super.validate(context);
        this.onPass.ifPresent(lootItemFunction -> lootItemFunction.validate(context.forChild(new ProblemReporter.FieldPathElement("on_pass"))));
        this.onFail.ifPresent(lootItemFunction -> lootItemFunction.validate(context.forChild(new ProblemReporter.FieldPathElement("on_fail"))));
    }

    public static FilteredFunction.Builder filtered(ItemPredicate itemPredicate) {
        return new FilteredFunction.Builder(itemPredicate);
    }

    public static class Builder extends LootItemConditionalFunction.Builder<FilteredFunction.Builder> {
        private final ItemPredicate itemPredicate;
        private Optional<LootItemFunction> onPass = Optional.empty();
        private Optional<LootItemFunction> onFail = Optional.empty();

        Builder(ItemPredicate itemPredicate) {
            this.itemPredicate = itemPredicate;
        }

        @Override
        protected FilteredFunction.Builder getThis() {
            return this;
        }

        public FilteredFunction.Builder onPass(Optional<LootItemFunction> onPass) {
            this.onPass = onPass;
            return this;
        }

        public FilteredFunction.Builder onFail(Optional<LootItemFunction> onFail) {
            this.onFail = onFail;
            return this;
        }

        @Override
        public LootItemFunction build() {
            return new FilteredFunction(this.getConditions(), this.itemPredicate, this.onPass, this.onFail);
        }
    }
}
