package net.minecraft.world.level.storage.loot.entries;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public abstract class CompositeEntryBase extends LootPoolEntryContainer {
    public static final ProblemReporter.Problem NO_CHILDREN_PROBLEM = new ProblemReporter.Problem() {
        @Override
        public String description() {
            return "Empty children list";
        }
    };
    protected final List<LootPoolEntryContainer> children;
    private final ComposableEntryContainer composedChildren;

    protected CompositeEntryBase(List<LootPoolEntryContainer> children, List<LootItemCondition> conditions) {
        super(conditions);
        this.children = children;
        this.composedChildren = this.compose(children);
    }

    @Override
    public void validate(ValidationContext validationContext) {
        super.validate(validationContext);
        if (this.children.isEmpty()) {
            validationContext.reportProblem(NO_CHILDREN_PROBLEM);
        }

        for (int i = 0; i < this.children.size(); i++) {
            this.children.get(i).validate(validationContext.forChild(new ProblemReporter.IndexedFieldPathElement("children", i)));
        }
    }

    protected abstract ComposableEntryContainer compose(List<? extends ComposableEntryContainer> children);

    @Override
    public final boolean expand(LootContext lootContext, Consumer<LootPoolEntry> entryConsumer) {
        return this.canRun(lootContext) && this.composedChildren.expand(lootContext, entryConsumer);
    }

    public static <T extends CompositeEntryBase> MapCodec<T> createCodec(CompositeEntryBase.CompositeEntryConstructor<T> factory) {
        return RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    LootPoolEntries.CODEC.listOf().optionalFieldOf("children", List.of()).forGetter(compositeEntryBase -> compositeEntryBase.children)
                )
                .and(commonFields(instance).t1())
                .apply(instance, factory::create)
        );
    }

    @FunctionalInterface
    public interface CompositeEntryConstructor<T extends CompositeEntryBase> {
        T create(List<LootPoolEntryContainer> children, List<LootItemCondition> conditions);
    }
}
