package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;

public class CrossbowAttack<E extends Mob & CrossbowAttackMob, T extends LivingEntity> extends Behavior<E> {
    private static final int TIMEOUT = 1200;
    private int attackDelay;
    private CrossbowAttack.CrossbowState crossbowState = CrossbowAttack.CrossbowState.UNCHARGED;

    public CrossbowAttack() {
        super(ImmutableMap.of(MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED, MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT), 1200);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, E owner) {
        LivingEntity attackTarget = getAttackTarget(owner);
        return owner.isHolding(Items.CROSSBOW) && BehaviorUtils.canSee(owner, attackTarget) && BehaviorUtils.isWithinAttackRange(owner, attackTarget, 0);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, E entity, long gameTime) {
        return entity.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET) && this.checkExtraStartConditions(level, entity);
    }

    @Override
    protected void tick(ServerLevel level, E owner, long gameTime) {
        LivingEntity attackTarget = getAttackTarget(owner);
        this.lookAtTarget(owner, attackTarget);
        this.crossbowAttack(owner, attackTarget);
    }

    @Override
    protected void stop(ServerLevel level, E entity, long gameTime) {
        if (entity.isUsingItem()) {
            entity.stopUsingItem();
        }

        if (entity.isHolding(Items.CROSSBOW)) {
            entity.setChargingCrossbow(false);
            entity.getUseItem().set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
        }
    }

    private void crossbowAttack(E shooter, LivingEntity target) {
        if (this.crossbowState == CrossbowAttack.CrossbowState.UNCHARGED) {
            shooter.startUsingItem(ProjectileUtil.getWeaponHoldingHand(shooter, Items.CROSSBOW));
            this.crossbowState = CrossbowAttack.CrossbowState.CHARGING;
            shooter.setChargingCrossbow(true);
        } else if (this.crossbowState == CrossbowAttack.CrossbowState.CHARGING) {
            if (!shooter.isUsingItem()) {
                this.crossbowState = CrossbowAttack.CrossbowState.UNCHARGED;
            }

            int ticksUsingItem = shooter.getTicksUsingItem();
            ItemStack useItem = shooter.getUseItem();
            if (ticksUsingItem >= CrossbowItem.getChargeDuration(useItem, shooter)) {
                shooter.releaseUsingItem();
                this.crossbowState = CrossbowAttack.CrossbowState.CHARGED;
                this.attackDelay = 20 + shooter.getRandom().nextInt(20);
                shooter.setChargingCrossbow(false);
            }
        } else if (this.crossbowState == CrossbowAttack.CrossbowState.CHARGED) {
            this.attackDelay--;
            if (this.attackDelay == 0) {
                this.crossbowState = CrossbowAttack.CrossbowState.READY_TO_ATTACK;
            }
        } else if (this.crossbowState == CrossbowAttack.CrossbowState.READY_TO_ATTACK) {
            shooter.performRangedAttack(target, 1.0F);
            this.crossbowState = CrossbowAttack.CrossbowState.UNCHARGED;
        }
    }

    private void lookAtTarget(Mob shooter, LivingEntity target) {
        shooter.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));
    }

    private static LivingEntity getAttackTarget(LivingEntity shooter) {
        return shooter.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).get();
    }

    static enum CrossbowState {
        UNCHARGED,
        CHARGING,
        CHARGED,
        READY_TO_ATTACK;
    }
}
