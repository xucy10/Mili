package net.minecraft.core.dispenser;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.phys.Vec3;

public class BoatDispenseItemBehavior extends DefaultDispenseItemBehavior {
    private final DefaultDispenseItemBehavior defaultDispenseItemBehavior = new DefaultDispenseItemBehavior();
    private final EntityType<? extends AbstractBoat> type;

    public BoatDispenseItemBehavior(EntityType<? extends AbstractBoat> type) {
        this.type = type;
    }

    @Override
    public ItemStack execute(BlockSource blockSource, ItemStack item) {
        Direction direction = blockSource.state().getValue(DispenserBlock.FACING);
        ServerLevel serverLevel = blockSource.level();
        Vec3 vec3 = blockSource.center();
        double d = 0.5625 + this.type.getWidth() / 2.0;
        double d1 = vec3.x() + direction.getStepX() * d;
        double d2 = vec3.y() + direction.getStepY() * 1.125F;
        double d3 = vec3.z() + direction.getStepZ() * d;
        BlockPos blockPos = blockSource.pos().relative(direction);
        double d4;
        if (serverLevel.getFluidState(blockPos).is(FluidTags.WATER)) {
            d4 = 1.0;
        } else {
            if (!serverLevel.getBlockState(blockPos).isAir() || !serverLevel.getFluidState(blockPos.below()).is(FluidTags.WATER)) {
                return this.defaultDispenseItemBehavior.dispense(blockSource, item);
            }

            d4 = 0.0;
        }

        // CraftBukkit start
        ItemStack singleItemStack = item.copyWithCount(1);
        org.bukkit.block.Block block = org.bukkit.craftbukkit.block.CraftBlock.at(serverLevel, blockSource.pos());
        org.bukkit.craftbukkit.inventory.CraftItemStack craftItem = org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(singleItemStack);

        org.bukkit.event.block.BlockDispenseEvent event = new org.bukkit.event.block.BlockDispenseEvent(block, craftItem.clone(), new org.bukkit.util.Vector(d1, d2 + d4, d3));
        serverLevel.getCraftServer().getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            return item;
        }

        boolean shrink = true;
        if (!event.getItem().equals(craftItem)) {
            shrink = false;
            // Chain to handler for new item
            ItemStack eventStack = org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(event.getItem());
            DispenseItemBehavior dispenseBehavior = DispenserBlock.getDispenseBehavior(blockSource, eventStack);
            if (dispenseBehavior != DispenseItemBehavior.NOOP && dispenseBehavior != this) {
                dispenseBehavior.dispense(blockSource, eventStack);
                return item;
            }
        }
        // CraftBukkit end
        AbstractBoat abstractBoat = this.type.create(serverLevel, EntitySpawnReason.DISPENSER);
        if (abstractBoat != null) {
            abstractBoat.setInitialPos(event.getVelocity().getX(), event.getVelocity().getY(), event.getVelocity().getZ()); // CraftBukkit
            EntityType.<AbstractBoat>createDefaultStackConfig(serverLevel, item, null).accept(abstractBoat);
            abstractBoat.setYRot(direction.toYRot());
            if (serverLevel.addFreshEntity(abstractBoat) && shrink) item.shrink(1); // Paper - if entity add was successful and supposed to shrink
        }

        return item;
    }

    @Override
    protected void playSound(BlockSource blockSource) {
        blockSource.level().levelEvent(LevelEvent.SOUND_DISPENSER_DISPENSE, blockSource.pos(), 0);
    }
}
