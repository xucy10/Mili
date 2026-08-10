package net.minecraft.world.inventory;

import java.util.List;
import net.minecraft.world.item.ItemStack;

public interface ContainerSynchronizer {
    void sendInitialData(AbstractContainerMenu container, List<ItemStack> items, ItemStack carried, int[] remoteDataSlots);

    void sendSlotChange(AbstractContainerMenu container, int slot, ItemStack stack);

    void sendCarriedChange(AbstractContainerMenu containerMenu, ItemStack stack);

    void sendDataChange(AbstractContainerMenu container, int id, int value);

    RemoteSlot createSlot();

    default void sendOffHandSlotChange() {} // Paper - Sync offhand slot in menus
}
