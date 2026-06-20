package net.minecraft.world.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.crafting.RecipePropertySet;
import net.minecraft.world.item.crafting.RecipeType;

public class BlastFurnaceMenu extends AbstractFurnaceMenu {
    public BlastFurnaceMenu(int containerId, Inventory playerInventory) {
        super(MenuType.BLAST_FURNACE, RecipeType.BLASTING, RecipePropertySet.BLAST_FURNACE_INPUT, RecipeBookType.BLAST_FURNACE, containerId, playerInventory);
    }

    public BlastFurnaceMenu(int containerId, Inventory playerInventory, Container blastFurnaceContainer, ContainerData blastFurnaceData) {
        super(
            MenuType.BLAST_FURNACE,
            RecipeType.BLASTING,
            RecipePropertySet.BLAST_FURNACE_INPUT,
            RecipeBookType.BLAST_FURNACE,
            containerId,
            playerInventory,
            blastFurnaceContainer,
            blastFurnaceData
        );
    }
}
