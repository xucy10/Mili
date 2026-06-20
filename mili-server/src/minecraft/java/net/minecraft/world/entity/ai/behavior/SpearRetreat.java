package net.minecraft.world.entity.ai.behavior;

import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class SpearRetreat extends Behavior<PathfinderMob> {
    public static final int MIN_COOLDOWN_DISTANCE = 9;
    public static final int MAX_COOLDOWN_DISTANCE = 11;
    public static final int MAX_FLEEING_TIME = 100;
    double speedModifierWhenRepositioning;

    public SpearRetreat(double speedModifierWhenRepositioning) {
        super(Map.of(MemoryModuleType.SPEAR_STATUS, MemoryStatus.VALUE_PRESENT), 100);
        this.speedModifierWhenRepositioning = speedModifierWhenRepositioning;
    }

    private @Nullable LivingEntity getTarget(PathfinderMob mob) {
        return mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
    }

    private boolean ableToAttack(PathfinderMob mob) {
        return this.getTarget(mob) != null && mob.getMainHandItem().has(DataComponents.KINETIC_WEAPON);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, PathfinderMob owner) {
        if (this.ableToAttack(owner) && !owner.isUsingItem()) {
            if (owner.getBrain().getMemory(MemoryModuleType.SPEAR_STATUS).orElse(SpearAttack.SpearStatus.APPROACH) != SpearAttack.SpearStatus.RETREAT) {
                return false;
            } else {
                LivingEntity target = this.getTarget(owner);
                double d = owner.distanceToSqr(target.getX(), target.getY(), target.getZ());
                int i = owner.isPassenger() ? 2 : 0;
                double squareRoot = Math.sqrt(d);
                Vec3 posAway = LandRandomPos.getPosAway(owner, Math.max(0.0, 9 + i - squareRoot), Math.max(1.0, 11 + i - squareRoot), 7, target.position());
                if (posAway == null) {
                    return false;
                } else {
                    owner.getBrain().setMemory(MemoryModuleType.SPEAR_FLEEING_POSITION, posAway);
                    return true;
                }
            }
        } else {
            return false;
        }
    }

    @Override
    protected void start(ServerLevel level, PathfinderMob entity, long gameTime) {
        entity.setAggressive(true);
        entity.getBrain().setMemory(MemoryModuleType.SPEAR_FLEEING_TIME, 0);
        super.start(level, entity, gameTime);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, PathfinderMob entity, long gameTime) {
        return entity.getBrain().getMemory(MemoryModuleType.SPEAR_FLEEING_TIME).orElse(100) < 100
            && entity.getBrain().getMemory(MemoryModuleType.SPEAR_FLEEING_POSITION).isPresent()
            && !entity.getNavigation().isDone()
            && this.ableToAttack(entity);
    }

    @Override
    protected void tick(ServerLevel level, PathfinderMob owner, long gameTime) {
        LivingEntity target = this.getTarget(owner);
        float f = owner.getRootVehicle() instanceof Mob mob ? mob.chargeSpeedModifier() : 1.0F;
        owner.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));
        owner.getBrain().setMemory(MemoryModuleType.SPEAR_FLEEING_TIME, owner.getBrain().getMemory(MemoryModuleType.SPEAR_FLEEING_TIME).orElse(0) + 1);
        owner.getBrain()
            .getMemory(MemoryModuleType.SPEAR_FLEEING_POSITION)
            .ifPresent(vec3 -> owner.getNavigation().moveTo(vec3.x, vec3.y, vec3.z, f * this.speedModifierWhenRepositioning));
    }

    @Override
    protected void stop(ServerLevel level, PathfinderMob entity, long gameTime) {
        entity.getNavigation().stop();
        entity.setAggressive(false);
        entity.stopUsingItem();
        entity.getBrain().eraseMemory(MemoryModuleType.SPEAR_FLEEING_TIME);
        entity.getBrain().eraseMemory(MemoryModuleType.SPEAR_FLEEING_POSITION);
        entity.getBrain().eraseMemory(MemoryModuleType.SPEAR_STATUS);
    }
}
