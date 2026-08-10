package net.minecraft.world.entity.animal.frog;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.pathfinder.Path;

public class ShootTongue extends Behavior<Frog> {
    public static final int TIME_OUT_DURATION = 100;
    public static final int CATCH_ANIMATION_DURATION = 6;
    public static final int TONGUE_ANIMATION_DURATION = 10;
    private static final float EATING_DISTANCE = 1.75F;
    private static final float EATING_MOVEMENT_FACTOR = 0.75F;
    public static final int UNREACHABLE_TONGUE_TARGETS_COOLDOWN_DURATION = 100;
    public static final int MAX_UNREACHBLE_TONGUE_TARGETS_IN_MEMORY = 5;
    private int eatAnimationTimer;
    private int calculatePathCounter;
    private final SoundEvent tongueSound;
    private final SoundEvent eatSound;
    private ShootTongue.State state = ShootTongue.State.DONE;

    public ShootTongue(SoundEvent tongueSound, SoundEvent eatSound) {
        super(
            ImmutableMap.of(
                MemoryModuleType.WALK_TARGET,
                MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.LOOK_TARGET,
                MemoryStatus.REGISTERED,
                MemoryModuleType.ATTACK_TARGET,
                MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.IS_PANICKING,
                MemoryStatus.VALUE_ABSENT
            ),
            100
        );
        this.tongueSound = tongueSound;
        this.eatSound = eatSound;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Frog owner) {
        LivingEntity livingEntity = owner.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).get();
        boolean canPathfindToTarget = this.canPathfindToTarget(owner, livingEntity);
        if (!canPathfindToTarget) {
            owner.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            this.addUnreachableTargetToMemory(owner, livingEntity);
        }

        return canPathfindToTarget && owner.getPose() != Pose.CROAKING && Frog.canEat(livingEntity);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Frog entity, long gameTime) {
        return entity.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
            && this.state != ShootTongue.State.DONE
            && !entity.getBrain().hasMemoryValue(MemoryModuleType.IS_PANICKING);
    }

    @Override
    protected void start(ServerLevel level, Frog entity, long gameTime) {
        LivingEntity livingEntity = entity.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).get();
        BehaviorUtils.lookAtEntity(entity, livingEntity);
        entity.setTongueTarget(livingEntity);
        entity.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(livingEntity.position(), 2.0F, 0));
        this.calculatePathCounter = 10;
        this.state = ShootTongue.State.MOVE_TO_TARGET;
    }

    @Override
    protected void stop(ServerLevel level, Frog entity, long gameTime) {
        entity.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        entity.eraseTongueTarget();
        entity.setPose(Pose.STANDING);
    }

    private void eatEntity(ServerLevel level, Frog frog) {
        level.playSound(null, frog, this.eatSound, SoundSource.NEUTRAL, 2.0F, 1.0F);
        Optional<Entity> tongueTarget = frog.getTongueTarget();
        if (tongueTarget.isPresent()) {
            Entity entity = tongueTarget.get();
            if (entity.isAlive()) {
                frog.doHurtTarget(level, entity);
                if (!entity.isAlive()) {
                    entity.remove(Entity.RemovalReason.KILLED, org.bukkit.event.entity.EntityRemoveEvent.Cause.DEATH); // CraftBukkit - add Bukkit remove cause
                }
            }
        }
    }

    @Override
    protected void tick(ServerLevel level, Frog owner, long gameTime) {
        LivingEntity livingEntity = owner.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).get();
        owner.setTongueTarget(livingEntity);
        switch (this.state) {
            case MOVE_TO_TARGET:
                if (livingEntity.distanceTo(owner) < 1.75F) {
                    level.playSound(null, owner, this.tongueSound, SoundSource.NEUTRAL, 2.0F, 1.0F);
                    owner.setPose(Pose.USING_TONGUE);
                    livingEntity.setDeltaMovement(livingEntity.position().vectorTo(owner.position()).normalize().scale(0.75));
                    this.eatAnimationTimer = 0;
                    this.state = ShootTongue.State.CATCH_ANIMATION;
                } else if (this.calculatePathCounter <= 0) {
                    owner.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(livingEntity.position(), 2.0F, 0));
                    this.calculatePathCounter = 10;
                } else {
                    this.calculatePathCounter--;
                }
                break;
            case CATCH_ANIMATION:
                if (this.eatAnimationTimer++ >= 6) {
                    this.state = ShootTongue.State.EAT_ANIMATION;
                    this.eatEntity(level, owner);
                }
                break;
            case EAT_ANIMATION:
                if (this.eatAnimationTimer >= 10) {
                    this.state = ShootTongue.State.DONE;
                } else {
                    this.eatAnimationTimer++;
                }
            case DONE:
        }
    }

    private boolean canPathfindToTarget(Frog frog, LivingEntity target) {
        Path path = frog.getNavigation().createPath(target, 0);
        return path != null && path.getDistToTarget() < 1.75F;
    }

    private void addUnreachableTargetToMemory(Frog frog, LivingEntity target) {
        List<UUID> list = frog.getBrain().getMemory(MemoryModuleType.UNREACHABLE_TONGUE_TARGETS).orElseGet(ArrayList::new);
        boolean flag = !list.contains(target.getUUID());
        if (list.size() == 5 && flag) {
            list.remove(0);
        }

        if (flag) {
            list.add(target.getUUID());
        }

        frog.getBrain().setMemoryWithExpiry(MemoryModuleType.UNREACHABLE_TONGUE_TARGETS, list, 100L);
    }

    static enum State {
        MOVE_TO_TARGET,
        CATCH_ANIMATION,
        EAT_ANIMATION,
        DONE;
    }
}
