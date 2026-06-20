package net.minecraft.world.entity.projectile.hurtingprojectile;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class SmallFireball extends Fireball {
    public SmallFireball(EntityType<? extends SmallFireball> type, Level level) {
        super(type, level);
    }

    public SmallFireball(Level level, LivingEntity owner, Vec3 movement) {
        super(EntityType.SMALL_FIREBALL, owner, movement, level);
        // CraftBukkit start
        if (owner != null && owner instanceof Mob) { // Folia - region threading
            this.isIncendiary = (level instanceof ServerLevel serverLevel) && serverLevel.getGameRules().get(GameRules.MOB_GRIEFING);
        }
        // CraftBukkit end
    }

    public SmallFireball(Level level, double x, double y, double z, Vec3 movement) {
        super(EntityType.SMALL_FIREBALL, x, y, z, movement, level);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (this.level() instanceof ServerLevel serverLevel) {
            Entity var7 = result.getEntity();
            Entity owner = this.getOwner();
            int remainingFireTicks = var7.getRemainingFireTicks();
            // CraftBukkit start - Entity damage by entity event + combust event
            org.bukkit.event.entity.EntityCombustByEntityEvent event = new org.bukkit.event.entity.EntityCombustByEntityEvent(this.getBukkitEntity(), var7.getBukkitEntity(), 5.0F);
            var7.level().getCraftServer().getPluginManager().callEvent(event);

            if (!event.isCancelled()) {
                var7.igniteForSeconds(event.getDuration(), false);
            }
            // CraftBukkit end
            DamageSource damageSource = this.damageSources().fireball(this, owner);
            if (!var7.hurtServer(serverLevel, damageSource, 5.0F)) {
                var7.setRemainingFireTicks(remainingFireTicks);
            } else {
                EnchantmentHelper.doPostAttackEffects(serverLevel, var7, damageSource);
            }
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (this.level() instanceof ServerLevel serverLevel) {
            Entity owner = this.getOwner();
            if (this.isIncendiary) { // CraftBukkit
                BlockPos blockPos = result.getBlockPos().relative(result.getDirection());
                if (this.level().isEmptyBlock(blockPos) && !org.bukkit.craftbukkit.event.CraftEventFactory.callBlockIgniteEvent(this.level(), blockPos, this).isCancelled()) { // CraftBukkit
                    this.level().setBlockAndUpdate(blockPos, BaseFireBlock.getState(this.level(), blockPos));
                }
            }
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!this.level().isClientSide()) {
            this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.HIT); // CraftBukkit - add Bukkit remove cause
        }
    }
}
