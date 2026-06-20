package net.minecraft.world.level.storage.loot.entries;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class SequentialEntry extends CompositeEntryBase {
    public static final MapCodec<SequentialEntry> CODEC = createCodec(SequentialEntry::new);

    SequentialEntry(List<LootPoolEntryContainer> children, List<LootItemCondition> conditions) {
        super(children, conditions);
    }

    @Override
    public LootPoolEntryType getType() {
        return LootPoolEntries.SEQUENCE;
    }

    @Override
    protected ComposableEntryContainer compose(List<? extends ComposableEntryContainer> children) {
        return switch (children.size()) {
            case 0 -> ALWAYS_TRUE;
            case 1 -> (ComposableEntryContainer)children.get(0);
            case 2 -> children.get(0).and(children.get(1));
            default -> (lootContext, entryConsumer) -> {
                for (ComposableEntryContainer composableEntryContainer : children) {
                    if (!composableEntryContainer.expand(lootContext, entryConsumer)) {
                        return false;
                    }
                }

                return true;
            };
        };
    }

    public static SequentialEntry.Builder sequential(LootPoolEntryContainer.Builder<?>... children) {
        return new SequentialEntry.Builder(children);
    }

    public static class Builder extends LootPoolEntryContainer.Builder<SequentialEntry.Builder> {
        private final ImmutableList.Builder<LootPoolEntryContainer> entries = ImmutableList.builder();

        public Builder(LootPoolEntryContainer.Builder<?>... children) {
            for (LootPoolEntryContainer.Builder<?> builder : children) {
                this.entries.add(builder.build());
            }
        }

        @Override
        protected SequentialEntry.Builder getThis() {
            return this;
        }

        @Override
        public SequentialEntry.Builder then(LootPoolEntryContainer.Builder<?> childBuilder) {
            this.entries.add(childBuilder.build());
            return this;
        }

        @Override
        public LootPoolEntryContainer build() {
            return new SequentialEntry(this.entries.build(), this.getConditions());
        }
    }
}
