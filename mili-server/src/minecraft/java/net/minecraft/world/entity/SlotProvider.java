package net.minecraft.world.entity;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import java.util.Objects;
import net.minecraft.world.item.slot.SlotCollection;
import org.jspecify.annotations.Nullable;

public interface SlotProvider {
    @Nullable SlotAccess getSlot(int slotIndex);

    default SlotCollection getSlotsFromRange(IntList slots) {
        List<SlotAccess> list = slots.intStream().mapToObj(this::getSlot).filter(Objects::nonNull).toList();
        return SlotCollection.of(list);
    }
}
