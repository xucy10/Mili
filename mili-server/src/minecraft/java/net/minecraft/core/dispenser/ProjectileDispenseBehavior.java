package net.minecraft.core.dispenser;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.level.block.DispenserBlock;

public class ProjectileDispenseBehavior extends DefaultDispenseItemBehavior {
    private final ProjectileItem projectileItem;
    private final ProjectileItem.DispenseConfig dispenseConfig;

    public ProjectileDispenseBehavior(Item projectile) {
        if (projectile instanceof ProjectileItem projectileItem) {
            this.projectileItem = projectileItem;
            this.dispenseConfig = projectileItem.createDispenseConfig();
        } else {
            throw new IllegalArgumentException(projectile + " not instance of " + ProjectileItem.class.getSimpleName());
        }
    }

    @Override
    public ItemStack execute(BlockSource blockSource, ItemStack item) {
        ServerLevel serverLevel = blockSource.level();
        Direction direction = blockSource.state().getValue(DispenserBlock.FACING);
        Position dispensePosition = this.dispenseConfig.positionFunction().getDispensePosition(blockSource, direction);
        ItemStack singleItemStack = item.copyWithCount(1); // Paper - shrink below and single item in event
        org.bukkit.block.Block block = org.bukkit.craftbukkit.block.CraftBlock.at(serverLevel, blockSource.pos());
        org.bukkit.craftbukkit.inventory.CraftItemStack craftItem = org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(singleItemStack);

        org.bukkit.event.block.BlockDispenseEvent event = new org.bukkit.event.block.BlockDispenseEvent(block, craftItem.clone(), new org.bukkit.util.Vector(direction.getStepX(), direction.getStepY(), direction.getStepZ()));
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

        // SPIGOT-7923: Avoid create projectiles with empty item
        if (!singleItemStack.isEmpty()) {
            Projectile projectile = Projectile.spawnProjectileUsingShoot(this.projectileItem.asProjectile(serverLevel, dispensePosition, org.bukkit.craftbukkit.inventory.CraftItemStack.unwrap(event.getItem()), direction), serverLevel, singleItemStack, event.getVelocity().getX(), event.getVelocity().getY(), event.getVelocity().getZ(), this.dispenseConfig.power(), this.dispenseConfig.uncertainty()); // Paper - track changed items in the dispense event; unwrap is safe here because all uses of the stack make their own copies
            projectile.projectileSource = new org.bukkit.craftbukkit.projectiles.CraftBlockProjectileSource(blockSource.blockEntity());
        }
        if (shrink) item.shrink(1);
        // CraftBukkit end
        return item;
    }

    @Override
    protected void playSound(BlockSource blockSource) {
        blockSource.level().levelEvent(this.dispenseConfig.overrideDispenseEvent().orElse(1002), blockSource.pos(), 0);
    }
}
