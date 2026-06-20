package net.minecraft.world.entity.ai.behavior;

import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import org.jspecify.annotations.Nullable;

public class SpearApproach extends Behavior<PathfinderMob> {
    double speedModifierWhenRepositioning;
    float approachDistanceSq;

    public SpearApproach(double speedModifierWhenRepositioning, float approachDistance) {
        super(Map.of(MemoryModuleType.SPEAR_STATUS, MemoryStatus.VALUE_ABSENT));
        this.speedModifierWhenRepositioning = speedModifierWhenRepositioning;
        this.approachDistanceSq = approachDistance * approachDistance;
    }

    private boolean ableToAttack(PathfinderMob mob) {
        return this.getTarget(mob) != null && mob.getMainHandItem().has(DataComponents.KINETIC_WEAPON);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, PathfinderMob owner) {
        return this.ableToAttack(owner) && !owner.isUsingItem();
    }

    @Override
    protected void start(ServerLevel level, PathfinderMob entity, long gameTime) {
        entity.setAggressive(true);
        entity.getBrain().setMemory(MemoryModuleType.SPEAR_STATUS, SpearAttack.SpearStatus.APPROACH);
        super.start(level, entity, gameTime);
    }

    private @Nullable LivingEntity getTarget(PathfinderMob mob) {
        return mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, PathfinderMob entity, long gameTime) {
        return this.ableToAttack(entity) && this.farEnough(entity);
    }

    private boolean farEnough(PathfinderMob mob) {
        LivingEntity target = this.getTarget(mob);
        double d = mob.distanceToSqr(target.getX(), target.getY(), target.getZ());
        return d > this.approachDistanceSq;
    }

    @Override
    protected void tick(ServerLevel level, PathfinderMob owner, long gameTime) {
        LivingEntity target = this.getTarget(owner);
        Entity rootVehicle = owner.getRootVehicle();
        float f = 1.0F;
        if (rootVehicle instanceof Mob mob) {
            f = mob.chargeSpeedModifier();
        }

        owner.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));
        owner.getNavigation().moveTo(target, f * this.speedModifierWhenRepositioning);
    }

    @Override
    protected void stop(ServerLevel level, PathfinderMob entity, long gameTime) {
        entity.getNavigation().stop();
        entity.getBrain().setMemory(MemoryModuleType.SPEAR_STATUS, SpearAttack.SpearStatus.CHARGING);
    }

    @Override
    protected boolean timedOut(long gameTime) {
        return false;
    }
}
