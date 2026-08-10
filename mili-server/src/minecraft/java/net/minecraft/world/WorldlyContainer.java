package net.minecraft.world;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public interface WorldlyContainer extends Container {
    int[] getSlotsForFace(Direction side);

    boolean canPlaceItemThroughFace(int index, ItemStack stack, @Nullable Direction direction);

    boolean canTakeItemThroughFace(int index, ItemStack stack, Direction direction);
}
