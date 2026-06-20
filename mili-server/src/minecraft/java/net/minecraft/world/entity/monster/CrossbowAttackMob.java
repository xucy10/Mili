package net.minecraft.world.entity.monster;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

public interface CrossbowAttackMob extends RangedAttackMob {
    void setChargingCrossbow(boolean chargingCrossbow);

    @Nullable LivingEntity getTarget();

    void onCrossbowAttackPerformed();

    default void performCrossbowAttack(LivingEntity user, float velocity) {
        InteractionHand weaponHoldingHand = ProjectileUtil.getWeaponHoldingHand(user, Items.CROSSBOW);
        ItemStack itemInHand = user.getItemInHand(weaponHoldingHand);
        if (itemInHand.getItem() instanceof CrossbowItem crossbowItem) {
            crossbowItem.performShooting(
                user.level(), user, weaponHoldingHand, itemInHand, velocity, 14 - user.level().getDifficulty().getId() * 4, this.getTarget()
            );
        }

        this.onCrossbowAttackPerformed();
    }
}
