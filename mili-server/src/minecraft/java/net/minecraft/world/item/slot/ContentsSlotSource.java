package net.minecraft.world.item.slot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.storage.loot.ContainerComponentManipulator;
import net.minecraft.world.level.storage.loot.ContainerComponentManipulators;

public class ContentsSlotSource extends TransformedSlotSource {
    public static final MapCodec<ContentsSlotSource> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance -> commonFields(instance)
            .and(ContainerComponentManipulators.CODEC.fieldOf("component").forGetter(contentsSlotSource -> contentsSlotSource.component))
            .apply(instance, ContentsSlotSource::new)
    );
    private final ContainerComponentManipulator<?> component;

    private ContentsSlotSource(SlotSource slotSource, ContainerComponentManipulator<?> component) {
        super(slotSource);
        this.component = component;
    }

    @Override
    public MapCodec<ContentsSlotSource> codec() {
        return MAP_CODEC;
    }

    @Override
    protected SlotCollection transform(SlotCollection slots) {
        return slots.flatMap(this.component::getSlots);
    }
}
