package net.minecraft.core.dispenser;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;

public class ShearsDispenseItemBehavior extends OptionalDispenseItemBehavior {
    @Override
    protected ItemStack execute(BlockSource blockSource, ItemStack item) {
        ServerLevel serverLevel = blockSource.level();
        // CraftBukkit start
        org.bukkit.block.Block bukkitBlock = org.bukkit.craftbukkit.block.CraftBlock.at(serverLevel, blockSource.pos());
        org.bukkit.craftbukkit.inventory.CraftItemStack craftItem = org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(item); // Paper - ignore stack size on damageable items
        org.bukkit.event.block.BlockDispenseEvent event = new org.bukkit.event.block.BlockDispenseEvent(bukkitBlock, craftItem.clone(), new org.bukkit.util.Vector(0, 0, 0));
        serverLevel.getCraftServer().getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            this.setSuccess(false);
            return item;
        }

        if (!event.getItem().equals(craftItem)) {
            // Chain to handler for new item
            ItemStack eventStack = org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(event.getItem());
            DispenseItemBehavior dispenseBehavior = DispenserBlock.getDispenseBehavior(blockSource, eventStack);
            if (dispenseBehavior != DispenseItemBehavior.NOOP && dispenseBehavior != this) {
                dispenseBehavior.dispense(blockSource, eventStack);
                return item;
            }
        }
        // CraftBukkit end
        if (!serverLevel.isClientSide()) {
            BlockPos blockPos = blockSource.pos().relative(blockSource.state().getValue(DispenserBlock.FACING));
            this.setSuccess(tryShearBeehive(serverLevel, item, blockPos) || tryShearEntity(serverLevel, blockPos, item, bukkitBlock, craftItem)); // CraftBukkit
            if (this.isSuccess()) {
                item.hurtAndBreak(1, serverLevel, null, item1 -> {});
            }
        }

        return item;
    }

    private static boolean tryShearBeehive(ServerLevel level, ItemStack stack, BlockPos pos) {
        BlockState blockState = level.getBlockState(pos);
        if (blockState.is(
            BlockTags.BEEHIVES, blockStateBase -> blockStateBase.hasProperty(BeehiveBlock.HONEY_LEVEL) && blockStateBase.getBlock() instanceof BeehiveBlock
        )) {
            int honeyLevelValue = blockState.getValue(BeehiveBlock.HONEY_LEVEL);
            if (honeyLevelValue >= 5) {
                level.playSound(null, pos, SoundEvents.BEEHIVE_SHEAR, SoundSource.BLOCKS, 1.0F, 1.0F);
                BeehiveBlock.dropHoneycomb(level, stack, blockState, level.getBlockEntity(pos), null, pos);
                ((BeehiveBlock)blockState.getBlock())
                    .releaseBeesAndResetHoneyLevel(level, blockState, pos, null, BeehiveBlockEntity.BeeReleaseStatus.BEE_RELEASED);
                level.gameEvent(null, GameEvent.SHEAR, pos);
                return true;
            }
        }

        return false;
    }

    private static boolean tryShearEntity(ServerLevel level, BlockPos pos, ItemStack stack, org.bukkit.block.Block bukkitBlock, org.bukkit.craftbukkit.inventory.CraftItemStack craftItem) { // CraftBukkit - add args
        for (Entity entity : level.getEntitiesOfClass(Entity.class, new AABB(pos), EntitySelector.NO_SPECTATORS)) {
            if (entity.shearOffAllLeashConnections(null)) {
                return true;
            }

            if (entity instanceof Shearable shearable && shearable.readyForShearing()) {
                // CraftBukkit start
                // Paper start - Add drops to shear events
                org.bukkit.event.block.BlockShearEntityEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callBlockShearEntityEvent(entity, bukkitBlock, craftItem, shearable.generateDefaultDrops(level, stack));
                if (event.isCancelled()) {
                    // Paper end - Add drops to shear events
                    continue;
                }
                // CraftBukkit end
                shearable.shear(level, SoundSource.BLOCKS, stack, org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(event.getDrops())); // Paper - Add drops to shear events
                level.gameEvent(null, GameEvent.SHEAR, pos);
                return true;
            }
        }

        return false;
    }
}
