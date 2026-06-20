package net.minecraft.world.item.slot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.Function;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.ValidationContext;

public abstract class CompositeSlotSource implements SlotSource {
    protected final List<SlotSource> terms;
    private final Function<LootContext, SlotCollection> compositeSlotSource;

    protected CompositeSlotSource(List<SlotSource> terms) {
        this.terms = terms;
        this.compositeSlotSource = SlotSources.group(terms);
    }

    protected static <T extends CompositeSlotSource> MapCodec<T> createCodec(Function<List<SlotSource>, T> mapper) {
        return RecordCodecBuilder.mapCodec(
            instance -> instance.group(SlotSources.CODEC.listOf().fieldOf("terms").forGetter(compositeSlotSource -> compositeSlotSource.terms))
                .apply(instance, mapper)
        );
    }

    protected static <T extends CompositeSlotSource> Codec<T> createInlineCodec(Function<List<SlotSource>, T> mapper) {
        return SlotSources.CODEC.listOf().xmap(mapper, compositeSlotSource -> compositeSlotSource.terms);
    }

    @Override
    public abstract MapCodec<? extends CompositeSlotSource> codec();

    @Override
    public SlotCollection provide(LootContext context) {
        return this.compositeSlotSource.apply(context);
    }

    @Override
    public void validate(ValidationContext context) {
        SlotSource.super.validate(context);

        for (int i = 0; i < this.terms.size(); i++) {
            this.terms.get(i).validate(context.forChild(new ProblemReporter.IndexedFieldPathElement("terms", i)));
        }
    }
}
