package net.minecraft.world.entity.projectile.throwableitemprojectile;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

public class ThrownLingeringPotion extends AbstractThrownPotion {
    public ThrownLingeringPotion(EntityType<? extends ThrownLingeringPotion> type, Level level) {
        super(type, level);
    }

    public ThrownLingeringPotion(Level level, LivingEntity owner, ItemStack item) {
        super(EntityType.LINGERING_POTION, level, owner, item);
    }

    public ThrownLingeringPotion(Level level, double x, double y, double z, ItemStack item) {
        super(EntityType.LINGERING_POTION, level, x, y, z, item);
    }

    @Override
    public Item getDefaultItem() {
        return Items.LINGERING_POTION;
    }

    @Override
    public boolean onHitAsPotion(ServerLevel level, ItemStack stack, HitResult hitResult) { // Paper - More projectile API
        AreaEffectCloud areaEffectCloud = new AreaEffectCloud(this.level(), this.getX(), this.getY(), this.getZ());
        if (this.getOwner() instanceof LivingEntity livingEntity) {
            areaEffectCloud.setOwner(livingEntity);
        }

        areaEffectCloud.setRadius(3.0F);
        areaEffectCloud.setRadiusOnUse(-0.5F);
        areaEffectCloud.setDuration(600);
        areaEffectCloud.setWaitTime(10);
        areaEffectCloud.setRadiusPerTick(-areaEffectCloud.getRadius() / areaEffectCloud.getDuration());
        areaEffectCloud.applyComponentsFromItemStack(stack);
        boolean noEffects = this.getItem().getOrDefault(net.minecraft.core.component.DataComponents.POTION_CONTENTS, net.minecraft.world.item.alchemy.PotionContents.EMPTY).hasEffects(); // Paper - Fix potions splash events
        // CraftBukkit start
        org.bukkit.event.entity.LingeringPotionSplashEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callLingeringPotionSplashEvent(this, hitResult, areaEffectCloud);
        if (!(event.isCancelled() || areaEffectCloud.isRemoved() || (!event.allowsEmptyCreation() && (noEffects && !areaEffectCloud.potionContents.hasEffects())))) { // Paper - don't spawn area effect cloud if the effects were empty and not changed during the event handling
        level.addFreshEntity(areaEffectCloud);
        } else {
            areaEffectCloud.discard(null);
        }
        // CraftBukkit end
        return !event.isCancelled(); // Paper - Fix potions splash events
    }
}
