package net.minecraft.world.item;

import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ItemSteerable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class FoodOnAStickItem<T extends Entity & ItemSteerable> extends Item {
    private final EntityType<T> canInteractWith;
    private final int consumeItemDamage;

    public FoodOnAStickItem(EntityType<T> canInteractWith, int consumeItemDamage, Item.Properties properties) {
        super(properties);
        this.canInteractWith = canInteractWith;
        this.consumeItemDamage = consumeItemDamage;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResult.PASS;
        } else {
            Entity controlledVehicle = player.getControlledVehicle();
            if (player.isPassenger()
                && controlledVehicle instanceof ItemSteerable itemSteerable
                && controlledVehicle.getType() == this.canInteractWith
                && itemSteerable.boost()) {
                EquipmentSlot equipmentSlot = hand.asEquipmentSlot();
                ItemStack itemStack = itemInHand.hurtAndConvertOnBreak(this.consumeItemDamage, Items.FISHING_ROD, player, equipmentSlot);
                return InteractionResult.SUCCESS_SERVER.heldItemTransformedTo(itemStack);
            } else {
                player.awardStat(Stats.ITEM_USED.get(this));
                return InteractionResult.PASS;
            }
        }
    }
}
