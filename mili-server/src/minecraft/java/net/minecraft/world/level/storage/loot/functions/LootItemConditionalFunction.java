package net.minecraft.world.level.storage.loot.functions;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.Products.P1;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import com.mojang.serialization.codecs.RecordCodecBuilder.Mu;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.predicates.ConditionUserBuilder;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public abstract class LootItemConditionalFunction implements LootItemFunction {
    protected final List<LootItemCondition> predicates;
    private final Predicate<LootContext> compositePredicates;

    protected LootItemConditionalFunction(List<LootItemCondition> predicates) {
        this.predicates = predicates;
        this.compositePredicates = Util.allOf(predicates);
    }

    @Override
    public abstract LootItemFunctionType<? extends LootItemConditionalFunction> getType();

    protected static <T extends LootItemConditionalFunction> P1<Mu<T>, List<LootItemCondition>> commonFields(Instance<T> instance) {
        return instance.group(
            LootItemCondition.DIRECT_CODEC
                .listOf()
                .optionalFieldOf("conditions", List.of())
                .forGetter(lootItemConditionalFunction -> lootItemConditionalFunction.predicates)
        );
    }

    @Override
    public final ItemStack apply(ItemStack stack, LootContext context) {
        return this.compositePredicates.test(context) ? this.run(stack, context) : stack;
    }

    protected abstract ItemStack run(ItemStack stack, LootContext context);

    @Override
    public void validate(ValidationContext context) {
        LootItemFunction.super.validate(context);

        for (int i = 0; i < this.predicates.size(); i++) {
            this.predicates.get(i).validate(context.forChild(new ProblemReporter.IndexedFieldPathElement("conditions", i)));
        }
    }

    protected static LootItemConditionalFunction.Builder<?> simpleBuilder(Function<List<LootItemCondition>, LootItemFunction> constructor) {
        return new LootItemConditionalFunction.DummyBuilder(constructor);
    }

    public abstract static class Builder<T extends LootItemConditionalFunction.Builder<T>> implements LootItemFunction.Builder, ConditionUserBuilder<T> {
        private final ImmutableList.Builder<LootItemCondition> conditions = ImmutableList.builder();

        @Override
        public T when(LootItemCondition.Builder conditionBuilder) {
            this.conditions.add(conditionBuilder.build());
            return this.getThis();
        }

        @Override
        public final T unwrap() {
            return this.getThis();
        }

        protected abstract T getThis();

        protected List<LootItemCondition> getConditions() {
            return this.conditions.build();
        }
    }

    static final class DummyBuilder extends LootItemConditionalFunction.Builder<LootItemConditionalFunction.DummyBuilder> {
        private final Function<List<LootItemCondition>, LootItemFunction> constructor;

        public DummyBuilder(Function<List<LootItemCondition>, LootItemFunction> constructor) {
            this.constructor = constructor;
        }

        @Override
        protected LootItemConditionalFunction.DummyBuilder getThis() {
            return this;
        }

        @Override
        public LootItemFunction build() {
            return this.constructor.apply(this.getConditions());
        }
    }
}
