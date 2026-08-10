package net.minecraft.world.level.storage.loot.entries;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.MapCodec;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class AlternativesEntry extends CompositeEntryBase {
    public static final MapCodec<AlternativesEntry> CODEC = createCodec(AlternativesEntry::new);
    public static final ProblemReporter.Problem UNREACHABLE_PROBLEM = new ProblemReporter.Problem() {
        @Override
        public String description() {
            return "Unreachable entry!";
        }
    };

    AlternativesEntry(List<LootPoolEntryContainer> children, List<LootItemCondition> conditions) {
        super(children, conditions);
    }

    @Override
    public LootPoolEntryType getType() {
        return LootPoolEntries.ALTERNATIVES;
    }

    @Override
    protected ComposableEntryContainer compose(List<? extends ComposableEntryContainer> children) {
        return switch (children.size()) {
            case 0 -> ALWAYS_FALSE;
            case 1 -> (ComposableEntryContainer)children.get(0);
            case 2 -> children.get(0).or(children.get(1));
            default -> (lootContext, entryConsumer) -> {
                for (ComposableEntryContainer composableEntryContainer : children) {
                    if (composableEntryContainer.expand(lootContext, entryConsumer)) {
                        return true;
                    }
                }

                return false;
            };
        };
    }

    @Override
    public void validate(ValidationContext validationContext) {
        super.validate(validationContext);

        for (int i = 0; i < this.children.size() - 1; i++) {
            if (this.children.get(i).conditions.isEmpty()) {
                validationContext.reportProblem(UNREACHABLE_PROBLEM);
            }
        }
    }

    public static AlternativesEntry.Builder alternatives(LootPoolEntryContainer.Builder<?>... children) {
        return new AlternativesEntry.Builder(children);
    }

    public static <E> AlternativesEntry.Builder alternatives(Collection<E> childrenSources, Function<E, LootPoolEntryContainer.Builder<?>> toChildrenFunction) {
        return new AlternativesEntry.Builder(childrenSources.stream().map(toChildrenFunction::apply).toArray(LootPoolEntryContainer.Builder[]::new));
    }

    public static class Builder extends LootPoolEntryContainer.Builder<AlternativesEntry.Builder> {
        private final ImmutableList.Builder<LootPoolEntryContainer> entries = ImmutableList.builder();

        public Builder(LootPoolEntryContainer.Builder<?>... children) {
            for (LootPoolEntryContainer.Builder<?> builder : children) {
                this.entries.add(builder.build());
            }
        }

        @Override
        protected AlternativesEntry.Builder getThis() {
            return this;
        }

        @Override
        public AlternativesEntry.Builder otherwise(LootPoolEntryContainer.Builder<?> childBuilder) {
            this.entries.add(childBuilder.build());
            return this;
        }

        @Override
        public LootPoolEntryContainer build() {
            return new AlternativesEntry(this.entries.build(), this.getConditions());
        }
    }
}
