package net.minecraft.world.entity.ai.behavior;

import java.util.Map;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class SpearAttack extends Behavior<PathfinderMob> {
    public static final int MIN_REPOSITION_DISTANCE = 6;
    public static final int MAX_REPOSITION_DISTANCE = 7;
    double speedModifierWhenCharging;
    double speedModifierWhenRepositioning;
    float approachDistanceSq;
    float targetInRangeRadiusSq;

    public SpearAttack(double speedModifierWhenCharging, double speedModifierWhenRepositioning, float approachDistance, float targetInRangeRadius) {
        super(Map.of(MemoryModuleType.SPEAR_STATUS, MemoryStatus.VALUE_PRESENT));
        this.speedModifierWhenCharging = speedModifierWhenCharging;
        this.speedModifierWhenRepositioning = speedModifierWhenRepositioning;
        this.approachDistanceSq = approachDistance * approachDistance;
        this.targetInRangeRadiusSq = targetInRangeRadius * targetInRangeRadius;
    }

    private @Nullable LivingEntity getTarget(PathfinderMob mob) {
        return mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
    }

    private boolean ableToAttack(PathfinderMob mob) {
        return this.getTarget(mob) != null && mob.getMainHandItem().has(DataComponents.KINETIC_WEAPON);
    }

    private int getKineticWeaponUseDuration(PathfinderMob mob) {
        return Optional.ofNullable(mob.getMainHandItem().get(DataComponents.KINETIC_WEAPON)).map(KineticWeapon::computeDamageUseDuration).orElse(0);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, PathfinderMob owner) {
        return owner.getBrain().getMemory(MemoryModuleType.SPEAR_STATUS).orElse(SpearAttack.SpearStatus.APPROACH) == SpearAttack.SpearStatus.CHARGING
            && this.ableToAttack(owner)
            && !owner.isUsingItem();
    }

    @Override
    protected void start(ServerLevel level, PathfinderMob entity, long gameTime) {
        entity.setAggressive(true);
        entity.getBrain().setMemory(MemoryModuleType.SPEAR_ENGAGE_TIME, this.getKineticWeaponUseDuration(entity));
        entity.getBrain().eraseMemory(MemoryModuleType.SPEAR_CHARGE_POSITION);
        entity.startUsingItem(InteractionHand.MAIN_HAND);
        super.start(level, entity, gameTime);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, PathfinderMob entity, long gameTime) {
        return entity.getBrain().getMemory(MemoryModuleType.SPEAR_ENGAGE_TIME).orElse(0) > 0 && this.ableToAttack(entity);
    }

    @Override
    protected void tick(ServerLevel level, PathfinderMob owner, long gameTime) {
        LivingEntity target = this.getTarget(owner);
        double d = owner.distanceToSqr(target.getX(), target.getY(), target.getZ());
        Entity rootVehicle = owner.getRootVehicle();
        float f = 1.0F;
        if (rootVehicle instanceof Mob mob) {
            f = mob.chargeSpeedModifier();
        }

        int i = owner.isPassenger() ? 2 : 0;
        owner.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));
        owner.getBrain().setMemory(MemoryModuleType.SPEAR_ENGAGE_TIME, owner.getBrain().getMemory(MemoryModuleType.SPEAR_ENGAGE_TIME).orElse(0) - 1);
        Vec3 vec3 = owner.getBrain().getMemory(MemoryModuleType.SPEAR_CHARGE_POSITION).orElse(null);
        if (vec3 != null) {
            owner.getNavigation().moveTo(vec3.x, vec3.y, vec3.z, f * this.speedModifierWhenRepositioning);
            if (owner.getNavigation().isDone()) {
                owner.getBrain().eraseMemory(MemoryModuleType.SPEAR_CHARGE_POSITION);
            }
        } else {
            owner.getNavigation().moveTo(target, f * this.speedModifierWhenCharging);
            if (d < this.targetInRangeRadiusSq || owner.getNavigation().isDone()) {
                double squareRoot = Math.sqrt(d);
                Vec3 posAway = LandRandomPos.getPosAway(owner, 6 + i - squareRoot, 7 + i - squareRoot, 7, target.position());
                owner.getBrain().setMemory(MemoryModuleType.SPEAR_CHARGE_POSITION, posAway);
            }
        }
    }

    @Override
    protected void stop(ServerLevel level, PathfinderMob entity, long gameTime) {
        entity.getNavigation().stop();
        entity.stopUsingItem();
        entity.getBrain().eraseMemory(MemoryModuleType.SPEAR_CHARGE_POSITION);
        entity.getBrain().eraseMemory(MemoryModuleType.SPEAR_ENGAGE_TIME);
        entity.getBrain().setMemory(MemoryModuleType.SPEAR_STATUS, SpearAttack.SpearStatus.RETREAT);
    }

    @Override
    protected boolean timedOut(long gameTime) {
        return false;
    }

    public static enum SpearStatus {
        APPROACH,
        CHARGING,
        RETREAT;
    }
}
