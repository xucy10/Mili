package net.minecraft.world.level.storage.loot;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntries;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntry;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.functions.FunctionUserBuilder;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctions;
import net.minecraft.world.level.storage.loot.predicates.ConditionUserBuilder;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.NumberProviders;
import org.apache.commons.lang3.mutable.MutableInt;

public class LootPool {
    public static final Codec<LootPool> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                LootPoolEntries.CODEC.listOf().fieldOf("entries").forGetter(lootPool -> lootPool.entries),
                LootItemCondition.DIRECT_CODEC.listOf().optionalFieldOf("conditions", List.of()).forGetter(lootPool -> lootPool.conditions),
                LootItemFunctions.ROOT_CODEC.listOf().optionalFieldOf("functions", List.of()).forGetter(lootPool -> lootPool.functions),
                NumberProviders.CODEC.fieldOf("rolls").forGetter(lootPool -> lootPool.rolls),
                NumberProviders.CODEC.fieldOf("bonus_rolls").orElse(ConstantValue.exactly(0.0F)).forGetter(lootPool -> lootPool.bonusRolls)
            )
            .apply(instance, LootPool::new)
    );
    private final List<LootPoolEntryContainer> entries;
    private final List<LootItemCondition> conditions;
    private final Predicate<LootContext> compositeCondition;
    private final List<LootItemFunction> functions;
    private final BiFunction<ItemStack, LootContext, ItemStack> compositeFunction;
    private final NumberProvider rolls;
    private final NumberProvider bonusRolls;

    LootPool(
        List<LootPoolEntryContainer> entries,
        List<LootItemCondition> conditions,
        List<LootItemFunction> functions,
        NumberProvider rolls,
        NumberProvider bonusRolls
    ) {
        this.entries = entries;
        this.conditions = conditions;
        this.compositeCondition = Util.allOf(conditions);
        this.functions = functions;
        this.compositeFunction = LootItemFunctions.compose(functions);
        this.rolls = rolls;
        this.bonusRolls = bonusRolls;
    }

    private void addRandomItem(Consumer<ItemStack> stackConsumer, LootContext context) {
        RandomSource random = context.getRandom();
        List<LootPoolEntry> list = Lists.newArrayList();
        MutableInt mutableInt = new MutableInt();

        for (LootPoolEntryContainer lootPoolEntryContainer : this.entries) {
            lootPoolEntryContainer.expand(context, lootPoolEntry1 -> {
                int weight = lootPoolEntry1.getWeight(context.getLuck());
                if (weight > 0) {
                    list.add(lootPoolEntry1);
                    mutableInt.add(weight);
                }
            });
        }

        int size = list.size();
        if (mutableInt.intValue() != 0 && size != 0) {
            if (size == 1) {
                list.get(0).createItemStack(stackConsumer, context);
            } else {
                int randomInt = random.nextInt(mutableInt.intValue());

                for (LootPoolEntry lootPoolEntry : list) {
                    randomInt -= lootPoolEntry.getWeight(context.getLuck());
                    if (randomInt < 0) {
                        lootPoolEntry.createItemStack(stackConsumer, context);
                        return;
                    }
                }
            }
        }
    }

    public void addRandomItems(Consumer<ItemStack> stackConsumer, LootContext lootContext) {
        if (this.compositeCondition.test(lootContext)) {
            Consumer<ItemStack> consumer = LootItemFunction.decorate(this.compositeFunction, stackConsumer, lootContext);
            int i = this.rolls.getInt(lootContext) + Mth.floor(this.bonusRolls.getFloat(lootContext) * lootContext.getLuck());

            for (int i1 = 0; i1 < i; i1++) {
                this.addRandomItem(consumer, lootContext);
            }
        }
    }

    public void validate(ValidationContext context) {
        for (int i = 0; i < this.conditions.size(); i++) {
            this.conditions.get(i).validate(context.forChild(new ProblemReporter.IndexedFieldPathElement("conditions", i)));
        }

        for (int i = 0; i < this.functions.size(); i++) {
            this.functions.get(i).validate(context.forChild(new ProblemReporter.IndexedFieldPathElement("functions", i)));
        }

        for (int i = 0; i < this.entries.size(); i++) {
            this.entries.get(i).validate(context.forChild(new ProblemReporter.IndexedFieldPathElement("entries", i)));
        }

        this.rolls.validate(context.forChild(new ProblemReporter.FieldPathElement("rolls")));
        this.bonusRolls.validate(context.forChild(new ProblemReporter.FieldPathElement("bonus_rolls")));
    }

    public static LootPool.Builder lootPool() {
        return new LootPool.Builder();
    }

    public static class Builder implements FunctionUserBuilder<LootPool.Builder>, ConditionUserBuilder<LootPool.Builder> {
        private final ImmutableList.Builder<LootPoolEntryContainer> entries = ImmutableList.builder();
        private final ImmutableList.Builder<LootItemCondition> conditions = ImmutableList.builder();
        private final ImmutableList.Builder<LootItemFunction> functions = ImmutableList.builder();
        private NumberProvider rolls = ConstantValue.exactly(1.0F);
        private NumberProvider bonusRolls = ConstantValue.exactly(0.0F);

        public LootPool.Builder setRolls(NumberProvider rolls) {
            this.rolls = rolls;
            return this;
        }

        @Override
        public LootPool.Builder unwrap() {
            return this;
        }

        public LootPool.Builder setBonusRolls(NumberProvider bonusRolls) {
            this.bonusRolls = bonusRolls;
            return this;
        }

        public LootPool.Builder add(LootPoolEntryContainer.Builder<?> entriesBuilder) {
            this.entries.add(entriesBuilder.build());
            return this;
        }

        @Override
        public LootPool.Builder when(LootItemCondition.Builder conditionBuilder) {
            this.conditions.add(conditionBuilder.build());
            return this;
        }

        @Override
        public LootPool.Builder apply(LootItemFunction.Builder functionBuilder) {
            this.functions.add(functionBuilder.build());
            return this;
        }

        public LootPool build() {
            return new LootPool(this.entries.build(), this.conditions.build(), this.functions.build(), this.rolls, this.bonusRolls);
        }
    }
}
