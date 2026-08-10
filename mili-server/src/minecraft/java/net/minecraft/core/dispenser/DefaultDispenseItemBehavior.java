package net.minecraft.core.dispenser;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.LevelEvent;

public class DefaultDispenseItemBehavior implements DispenseItemBehavior {
    private static final int DEFAULT_ACCURACY = 6;
    private Direction direction; // Paper - cache facing direction

    @Override
    public final ItemStack dispense(BlockSource blockSource, ItemStack item) {
        this.direction = blockSource.state().getValue(DispenserBlock.FACING); // Paper - cache facing direction
        ItemStack itemStack = this.execute(blockSource, item);
        this.playSound(blockSource);
        this.playAnimation(blockSource, this.direction); // Paper - cache facing direction
        return itemStack;
    }

    protected ItemStack execute(BlockSource blockSource, ItemStack item) {
        // Paper - cache facing direction
        Position dispensePosition = DispenserBlock.getDispensePosition(blockSource);
        ItemStack itemStack = item.split(1);
        // CraftBukkit start
        if (!DefaultDispenseItemBehavior.spawnItem(blockSource.level(), itemStack, 6, this.direction, dispensePosition, blockSource)) {
            item.grow(1);
        }
        // CraftBukkit end
        return item;
    }

    public static void spawnItem(Level level, ItemStack stack, int speed, Direction facing, Position position) {
        // CraftBukkit start
        ItemEntity itemEntity = prepareItem(level, stack, speed, facing, position);
        level.addFreshEntity(itemEntity);
    }

    private static ItemEntity prepareItem(Level level, ItemStack stack, int speed, Direction facing, Position position) {
        // CraftBukkit end
        double d = position.x();
        double d1 = position.y();
        double d2 = position.z();
        if (facing.getAxis() == Direction.Axis.Y) {
            d1 -= 0.125;
        } else {
            d1 -= 0.15625;
        }

        ItemEntity itemEntity = new ItemEntity(level, d, d1, d2, stack);
        double d3 = level.random.nextDouble() * 0.1 + 0.2;
        itemEntity.setDeltaMovement(
            level.random.triangle(facing.getStepX() * d3, 0.0172275 * speed),
            level.random.triangle(0.2, 0.0172275 * speed),
            level.random.triangle(facing.getStepZ() * d3, 0.0172275 * speed)
        );
        return itemEntity; // CraftBukkit
    }

    // CraftBukkit start - void -> boolean return
    public static boolean spawnItem(Level level, ItemStack stack, int speed, Direction facing, Position position, BlockSource blockSource) {
        if (stack.isEmpty()) return true;
        ItemEntity itemEntity = DefaultDispenseItemBehavior.prepareItem(level, stack, speed, facing, position);

        org.bukkit.block.Block block = org.bukkit.craftbukkit.block.CraftBlock.at(level, blockSource.pos());
        org.bukkit.craftbukkit.inventory.CraftItemStack craftItem = org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(stack);

        org.bukkit.event.block.BlockDispenseEvent event = new org.bukkit.event.block.BlockDispenseEvent(block, craftItem.clone(), org.bukkit.craftbukkit.util.CraftVector.toBukkit(itemEntity.getDeltaMovement()));
        level.getCraftServer().getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            return false;
        }

        itemEntity.setItem(org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(event.getItem()));
        itemEntity.setDeltaMovement(org.bukkit.craftbukkit.util.CraftVector.toVec3(event.getVelocity()));

        if (blockSource.state().is(net.minecraft.world.level.block.Blocks.DISPENSER) && !event.getItem().getType().equals(craftItem.getType())) {
            // Chain to handler for new item
            ItemStack eventStack = org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(event.getItem());
            DispenseItemBehavior dispenseBehavior = DispenserBlock.getDispenseBehavior(blockSource, eventStack);
            if (dispenseBehavior != DispenseItemBehavior.NOOP && dispenseBehavior.getClass() != DefaultDispenseItemBehavior.class) {
                dispenseBehavior.dispense(blockSource, eventStack);
            } else {
                level.addFreshEntity(itemEntity);
            }
            return false;
        }

        level.addFreshEntity(itemEntity);

        return true;
        // CraftBukkit end
    }

    protected void playSound(BlockSource blockSource) {
        playDefaultSound(blockSource);
    }

    protected void playAnimation(BlockSource blockSource, Direction direction) {
        playDefaultAnimation(blockSource, direction);
    }

    private static void playDefaultSound(BlockSource blockSource) {
        blockSource.level().levelEvent(LevelEvent.SOUND_DISPENSER_DISPENSE, blockSource.pos(), 0);
    }

    private static void playDefaultAnimation(BlockSource blockSource, Direction direction) {
        blockSource.level().levelEvent(LevelEvent.PARTICLES_SHOOT_SMOKE, blockSource.pos(), direction.get3DDataValue());
    }

    protected ItemStack consumeWithRemainder(BlockSource blockSource, ItemStack stack, ItemStack remainder) {
        stack.shrink(1);
        if (stack.isEmpty()) {
            return remainder;
        } else {
            this.addToInventoryOrDispense(blockSource, remainder);
            return stack;
        }
    }

    private void addToInventoryOrDispense(BlockSource blockSource, ItemStack remainder) {
        ItemStack itemStack = blockSource.blockEntity().insertItem(remainder);
        if (!itemStack.isEmpty()) {
            Direction direction = blockSource.state().getValue(DispenserBlock.FACING);
            spawnItem(blockSource.level(), itemStack, 6, direction, DispenserBlock.getDispensePosition(blockSource));
            playDefaultSound(blockSource);
            playDefaultAnimation(blockSource, direction);
        }
    }
}
