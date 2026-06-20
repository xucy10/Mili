package net.minecraft.world.level.storage.loot.functions;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.ContainerComponentManipulator;
import net.minecraft.world.level.storage.loot.ContainerComponentManipulators;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntries;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class SetContainerContents extends LootItemConditionalFunction {
    public static final MapCodec<SetContainerContents> CODEC = RecordCodecBuilder.mapCodec(
        instance -> commonFields(instance)
            .and(
                instance.group(
                    ContainerComponentManipulators.CODEC.fieldOf("component").forGetter(setContainerContents -> setContainerContents.component),
                    LootPoolEntries.CODEC.listOf().fieldOf("entries").forGetter(setContainerContents -> setContainerContents.entries)
                )
            )
            .apply(instance, SetContainerContents::new)
    );
    private final ContainerComponentManipulator<?> component;
    private final List<LootPoolEntryContainer> entries;

    SetContainerContents(List<LootItemCondition> predicates, ContainerComponentManipulator<?> component, List<LootPoolEntryContainer> entries) {
        super(predicates);
        this.component = component;
        this.entries = List.copyOf(entries);
    }

    @Override
    public LootItemFunctionType<SetContainerContents> getType() {
        return LootItemFunctions.SET_CONTENTS;
    }

    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        if (stack.isEmpty()) {
            return stack;
        } else {
            Stream.Builder<ItemStack> builder = Stream.builder();
            this.entries
                .forEach(
                    lootPoolEntryContainer -> lootPoolEntryContainer.expand(
                        context, lootPoolEntry -> lootPoolEntry.createItemStack(LootTable.createStackSplitter(context.getLevel(), builder::add), context)
                    )
                );
            this.component.setContents(stack, builder.build());
            return stack;
        }
    }

    @Override
    public void validate(ValidationContext context) {
        super.validate(context);

        for (int i = 0; i < this.entries.size(); i++) {
            this.entries.get(i).validate(context.forChild(new ProblemReporter.IndexedFieldPathElement("entries", i)));
        }
    }

    public static SetContainerContents.Builder setContents(ContainerComponentManipulator<?> component) {
        return new SetContainerContents.Builder(component);
    }

    public static class Builder extends LootItemConditionalFunction.Builder<SetContainerContents.Builder> {
        private final ImmutableList.Builder<LootPoolEntryContainer> entries = ImmutableList.builder();
        private final ContainerComponentManipulator<?> component;

        public Builder(ContainerComponentManipulator<?> component) {
            this.component = component;
        }

        @Override
        protected SetContainerContents.Builder getThis() {
            return this;
        }

        public SetContainerContents.Builder withEntry(LootPoolEntryContainer.Builder<?> lootEntryBuilder) {
            this.entries.add(lootEntryBuilder.build());
            return this;
        }

        @Override
        public LootItemFunction build() {
            return new SetContainerContents(this.getConditions(), this.component, this.entries.build());
        }
    }
}
