package net.minecraft.world.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.crafting.RecipePropertySet;
import net.minecraft.world.item.crafting.RecipeType;

public class FurnaceMenu extends AbstractFurnaceMenu {
    public FurnaceMenu(int containerId, Inventory playerInventory) {
        super(MenuType.FURNACE, RecipeType.SMELTING, RecipePropertySet.FURNACE_INPUT, RecipeBookType.FURNACE, containerId, playerInventory);
    }

    public FurnaceMenu(int containerId, Inventory playerInventory, Container furnaceContainer, ContainerData furnaceData) {
        super(
            MenuType.FURNACE,
            RecipeType.SMELTING,
            RecipePropertySet.FURNACE_INPUT,
            RecipeBookType.FURNACE,
            containerId,
            playerInventory,
            furnaceContainer,
            furnaceData
        );
    }
}
