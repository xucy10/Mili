package net.minecraft.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.state.BlockState;

public class BlastFurnaceBlockEntity extends AbstractFurnaceBlockEntity {
    private static final Component DEFAULT_NAME = Component.translatable("container.blast_furnace");

    public BlastFurnaceBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityType.BLAST_FURNACE, pos, blockState, RecipeType.BLASTING);
    }

    @Override
    protected Component getDefaultName() {
        return DEFAULT_NAME;
    }

    @Override
    protected int getBurnDuration(FuelValues fuelValues, ItemStack stack) {
        return super.getBurnDuration(fuelValues, stack) / 2;
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory player) {
        return new BlastFurnaceMenu(id, player, this, this.dataAccess);
    }
}
