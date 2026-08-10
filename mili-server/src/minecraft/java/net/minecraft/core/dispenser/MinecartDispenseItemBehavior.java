package net.minecraft.core.dispenser;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;

public class MinecartDispenseItemBehavior extends DefaultDispenseItemBehavior {
    private final DefaultDispenseItemBehavior defaultDispenseItemBehavior = new DefaultDispenseItemBehavior();
    private final EntityType<? extends AbstractMinecart> entityType;

    public MinecartDispenseItemBehavior(EntityType<? extends AbstractMinecart> entityType) {
        this.entityType = entityType;
    }

    @Override
    public ItemStack execute(BlockSource blockSource, ItemStack item) {
        Direction direction = blockSource.state().getValue(DispenserBlock.FACING);
        ServerLevel serverLevel = blockSource.level();
        Vec3 vec3 = blockSource.center();
        double d = vec3.x() + direction.getStepX() * 1.125;
        double d1 = Math.floor(vec3.y()) + direction.getStepY();
        double d2 = vec3.z() + direction.getStepZ() * 1.125;
        BlockPos blockPos = blockSource.pos().relative(direction);
        BlockState blockState = serverLevel.getBlockState(blockPos);
        double d3;
        if (blockState.is(BlockTags.RAILS)) {
            if (getRailShape(blockState).isSlope()) {
                d3 = 0.6;
            } else {
                d3 = 0.1;
            }
        } else {
            if (!blockState.isAir()) {
                return this.defaultDispenseItemBehavior.dispense(blockSource, item);
            }

            BlockState blockState1 = serverLevel.getBlockState(blockPos.below());
            if (!blockState1.is(BlockTags.RAILS)) {
                return this.defaultDispenseItemBehavior.dispense(blockSource, item);
            }

            if (direction != Direction.DOWN && getRailShape(blockState1).isSlope()) {
                d3 = -0.4;
            } else {
                d3 = -0.9;
            }
        }

        Vec3 vec31 = new Vec3(d, d1 + d3, d2);
        ItemStack itemstack1 = item.copyWithCount(1); // Paper - shrink below and single item in event
        org.bukkit.block.Block block2 = org.bukkit.craftbukkit.block.CraftBlock.at(serverLevel, blockSource.pos());
        org.bukkit.craftbukkit.inventory.CraftItemStack craftItem = org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(itemstack1);

        org.bukkit.event.block.BlockDispenseEvent event = new org.bukkit.event.block.BlockDispenseEvent(block2, craftItem.clone(), org.bukkit.craftbukkit.util.CraftVector.toBukkit(vec31));
        serverLevel.getCraftServer().getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            return item;
        }

        boolean shrink = true;
        if (!event.getItem().equals(craftItem)) {
            shrink = false;
            // Chain to handler for new item
            ItemStack eventStack = org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(event.getItem());
            DispenseItemBehavior dispenseItemBehavior = DispenserBlock.getDispenseBehavior(blockSource, eventStack);
            if (dispenseItemBehavior != DispenseItemBehavior.NOOP && dispenseItemBehavior != this) {
                dispenseItemBehavior.dispense(blockSource, eventStack);
                return item;
            }
        }

        itemstack1 = org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(event.getItem());
        AbstractMinecart abstractMinecart = AbstractMinecart.createMinecart(serverLevel, event.getVelocity().getX(), event.getVelocity().getY(), event.getVelocity().getZ(), this.entityType, EntitySpawnReason.DISPENSER, itemstack1, null);

        if (abstractMinecart != null) {
            if (serverLevel.addFreshEntity(abstractMinecart) && shrink) item.shrink(1); // Paper - if entity add was successful and supposed to shrink
            // CraftBukkit end
        }

        return item;
    }

    private static RailShape getRailShape(BlockState state) {
        return state.getBlock() instanceof BaseRailBlock baseRailBlock ? state.getValue(baseRailBlock.getShapeProperty()) : RailShape.NORTH_SOUTH;
    }

    @Override
    protected void playSound(BlockSource blockSource) {
        blockSource.level().levelEvent(LevelEvent.SOUND_DISPENSER_DISPENSE, blockSource.pos(), 0);
    }
}
