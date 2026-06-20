package net.minecraft.core.dispenser;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.AABB;

public class EquipmentDispenseItemBehavior extends DefaultDispenseItemBehavior {
    public static final EquipmentDispenseItemBehavior INSTANCE = new EquipmentDispenseItemBehavior();

    @Override
    protected ItemStack execute(BlockSource blockSource, ItemStack item) {
        return dispenseEquipment(blockSource, item, this) ? item : super.execute(blockSource, item); // Paper - fix possible StackOverflowError
    }

    @Deprecated @io.papermc.paper.annotation.DoNotUse // Paper
    public static boolean dispenseEquipment(BlockSource blockSource, ItemStack item) {
        // Paper start
        return dispenseEquipment(blockSource, item, null);
    }

    public static boolean dispenseEquipment(BlockSource blockSource, ItemStack item, @javax.annotation.Nullable DispenseItemBehavior currentBehavior) {
        // Paper end
        BlockPos blockPos = blockSource.pos().relative(blockSource.state().getValue(DispenserBlock.FACING));
        List<LivingEntity> entitiesOfClass = blockSource.level()
            .getEntitiesOfClass(LivingEntity.class, new AABB(blockPos), entity -> entity.canEquipWithDispenser(item));
        if (entitiesOfClass.isEmpty()) {
            return false;
        } else {
            LivingEntity livingEntity = entitiesOfClass.getFirst();
            EquipmentSlot equipmentSlotForItem = livingEntity.getEquipmentSlotForItem(item);
            ItemStack itemStack = item.copyWithCount(1); // Paper - shrink below and single item in event
            // CraftBukkit start
            net.minecraft.world.level.Level world = blockSource.level();
            org.bukkit.block.Block block = org.bukkit.craftbukkit.block.CraftBlock.at(world, blockSource.pos());
            org.bukkit.craftbukkit.inventory.CraftItemStack craftItem = org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(itemStack);

            org.bukkit.event.block.BlockDispenseArmorEvent event = new org.bukkit.event.block.BlockDispenseArmorEvent(block, craftItem.clone(), (org.bukkit.craftbukkit.entity.CraftLivingEntity) livingEntity.getBukkitEntity());
            world.getCraftServer().getPluginManager().callEvent(event);

            if (event.isCancelled()) {
                return false;
            }

            boolean shrink = true;
            if (!event.getItem().equals(craftItem)) {
                shrink = false;
                // Chain to handler for new item
                ItemStack eventStack = org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(event.getItem());
                DispenseItemBehavior dispenseItemBehavior = DispenserBlock.getDispenseBehavior(blockSource, eventStack);
                if (dispenseItemBehavior != DispenseItemBehavior.NOOP && (currentBehavior == null || dispenseItemBehavior != currentBehavior)) {
                    dispenseItemBehavior.dispense(blockSource, eventStack);
                    return true;
                }
            }

            livingEntity.setItemSlot(equipmentSlotForItem, org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(event.getItem()));
            // CraftBukkit end
            if (livingEntity instanceof Mob mob) {
                mob.setGuaranteedDrop(equipmentSlotForItem);
                mob.setPersistenceRequired();
            }

            if (shrink) item.shrink(1); // Paper - shrink here
            return true;
        }
    }
}
