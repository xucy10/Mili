package net.minecraft.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class Containers {
    public static void dropContents(Level level, BlockPos pos, Container inventory) {
        dropContents(level, pos.getX(), pos.getY(), pos.getZ(), inventory);
    }

    public static void dropContents(Level level, Entity entityAt, Container inventory) {
        dropContents(level, entityAt.getX(), entityAt.getY(), entityAt.getZ(), inventory);
    }

    private static void dropContents(Level level, double x, double y, double z, Container inventory) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            dropItemStack(level, x, y, z, inventory.getItem(i));
        }
    }

    public static void dropContents(Level level, BlockPos pos, NonNullList<ItemStack> stackList) {
        stackList.forEach(stack -> dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack));
    }

    public static void dropItemStack(Level level, double x, double y, double z, ItemStack stack) {
        double d = EntityType.ITEM.getWidth();
        double d1 = 1.0 - d;
        double d2 = d / 2.0;
        double d3 = Math.floor(x) + level.random.nextDouble() * d1 + d2;
        double d4 = Math.floor(y) + level.random.nextDouble() * d1;
        double d5 = Math.floor(z) + level.random.nextDouble() * d1 + d2;

        while (!stack.isEmpty()) {
            ItemEntity itemEntity = new ItemEntity(level, d3, d4, d5, stack.split(level.random.nextInt(21) + 10));
            float f = 0.05F;
            itemEntity.setDeltaMovement(
                level.random.triangle(0.0, 0.11485000171139836),
                level.random.triangle(0.2, 0.11485000171139836),
                level.random.triangle(0.0, 0.11485000171139836)
            );
            level.addFreshEntity(itemEntity);
        }
    }

    public static void updateNeighboursAfterDestroy(BlockState state, Level level, BlockPos pos) {
        level.updateNeighbourForOutputSignal(pos, state.getBlock());
    }
}
