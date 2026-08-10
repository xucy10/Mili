package net.minecraft.world.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class FurnaceFuelSlot extends Slot {
    private final AbstractFurnaceMenu menu;

    public FurnaceFuelSlot(AbstractFurnaceMenu furnaceMenu, Container furnaceContainer, int slot, int x, int y) {
        super(furnaceContainer, slot, x, y);
        this.menu = furnaceMenu;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return this.menu.isFuel(stack) || isBucket(stack);
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return isBucket(stack) ? 1 : super.getMaxStackSize(stack);
    }

    public static boolean isBucket(ItemStack stack) {
        return stack.is(Items.BUCKET);
    }
}
