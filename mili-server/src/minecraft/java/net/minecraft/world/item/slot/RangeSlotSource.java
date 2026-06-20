package net.minecraft.world.item.slot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.SlotProvider;
import net.minecraft.world.inventory.SlotRange;
import net.minecraft.world.inventory.SlotRanges;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootContextArg;

public class RangeSlotSource implements SlotSource {
    public static final MapCodec<RangeSlotSource> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                LootContextArg.ENTITY_OR_BLOCK.fieldOf("source").forGetter(rangeSlotSource -> rangeSlotSource.source),
                SlotRanges.CODEC.fieldOf("slots").forGetter(rangeSlotSource -> rangeSlotSource.slotRange)
            )
            .apply(instance, RangeSlotSource::new)
    );
    private final LootContextArg<Object> source;
    private final SlotRange slotRange;

    private RangeSlotSource(LootContextArg<Object> source, SlotRange slotRange) {
        this.source = source;
        this.slotRange = slotRange;
    }

    @Override
    public MapCodec<RangeSlotSource> codec() {
        return MAP_CODEC;
    }

    @Override
    public Set<ContextKey<?>> getReferencedContextParams() {
        return Set.of(this.source.contextParam());
    }

    @Override
    public final SlotCollection provide(LootContext context) {
        return this.source.get(context) instanceof SlotProvider slotProvider ? slotProvider.getSlotsFromRange(this.slotRange.slots()) : SlotCollection.EMPTY;
    }
}
