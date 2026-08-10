package net.minecraft.world.entity.monster.hoglin;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.AnimalMakeLove;
import net.minecraft.world.entity.ai.behavior.BabyFollowAdult;
import net.minecraft.world.entity.ai.behavior.BecomePassiveIfMemoryPresent;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.DoNothing;
import net.minecraft.world.entity.ai.behavior.EraseMemoryIf;
import net.minecraft.world.entity.ai.behavior.LookAtTargetSink;
import net.minecraft.world.entity.ai.behavior.MeleeAttack;
import net.minecraft.world.entity.ai.behavior.MoveToTargetSink;
import net.minecraft.world.entity.ai.behavior.RandomStroll;
import net.minecraft.world.entity.ai.behavior.RunOne;
import net.minecraft.world.entity.ai.behavior.SetEntityLookTargetSometimes;
import net.minecraft.world.entity.ai.behavior.SetWalkTargetAwayFrom;
import net.minecraft.world.entity.ai.behavior.SetWalkTargetFromAttackTargetIfTargetOutOfReach;
import net.minecraft.world.entity.ai.behavior.SetWalkTargetFromLookTarget;
import net.minecraft.world.entity.ai.behavior.StartAttacking;
import net.minecraft.world.entity.ai.behavior.StopAttackingIfTargetInvalid;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.schedule.Activity;

public class HoglinAi {
    public static final int REPELLENT_DETECTION_RANGE_HORIZONTAL = 8;
    public static final int REPELLENT_DETECTION_RANGE_VERTICAL = 4;
    private static final UniformInt RETREAT_DURATION = TimeUtil.rangeOfSeconds(5, 20);
    private static final int ATTACK_DURATION = 200;
    private static final int DESIRED_DISTANCE_FROM_PIGLIN_WHEN_IDLING = 8;
    private static final int DESIRED_DISTANCE_FROM_PIGLIN_WHEN_RETREATING = 15;
    private static final int ATTACK_INTERVAL = 40;
    private static final int BABY_ATTACK_INTERVAL = 15;
    private static final int REPELLENT_PACIFY_TIME = 200;
    private static final UniformInt ADULT_FOLLOW_RANGE = UniformInt.of(5, 16);
    private static final float SPEED_MULTIPLIER_WHEN_AVOIDING_REPELLENT = 1.0F;
    private static final float SPEED_MULTIPLIER_WHEN_RETREATING = 1.3F;
    private static final float SPEED_MULTIPLIER_WHEN_MAKING_LOVE = 0.6F;
    private static final float SPEED_MULTIPLIER_WHEN_IDLING = 0.4F;
    private static final float SPEED_MULTIPLIER_WHEN_FOLLOWING_ADULT = 0.6F;

    protected static Brain<?> makeBrain(Brain<Hoglin> brain) {
        initCoreActivity(brain);
        initIdleActivity(brain);
        initFightActivity(brain);
        initRetreatActivity(brain);
        brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.useDefaultActivity();
        return brain;
    }

    private static void initCoreActivity(Brain<Hoglin> brain) {
        brain.addActivity(Activity.CORE, 0, ImmutableList.of(new LookAtTargetSink(45, 90), new MoveToTargetSink()));
    }

    private static void initIdleActivity(Brain<Hoglin> brain) {
        brain.addActivity(
            Activity.IDLE,
            10,
            ImmutableList.of(
                BecomePassiveIfMemoryPresent.create(MemoryModuleType.NEAREST_REPELLENT, 200),
                new AnimalMakeLove(EntityType.HOGLIN, 0.6F, 2),
                SetWalkTargetAwayFrom.pos(MemoryModuleType.NEAREST_REPELLENT, 1.0F, 8, true),
                StartAttacking.create(HoglinAi::findNearestValidAttackTarget),
                BehaviorBuilder.triggerIf(Hoglin::isAdult, SetWalkTargetAwayFrom.entity(MemoryModuleType.NEAREST_VISIBLE_ADULT_PIGLIN, 0.4F, 8, false)),
                SetEntityLookTargetSometimes.create(8.0F, UniformInt.of(30, 60)),
                BabyFollowAdult.create(ADULT_FOLLOW_RANGE, 0.6F),
                createIdleMovementBehaviors()
            )
        );
    }

    private static void initFightActivity(Brain<Hoglin> brain) {
        brain.addActivityAndRemoveMemoryWhenStopped(
            Activity.FIGHT,
            10,
            ImmutableList.of(
                BecomePassiveIfMemoryPresent.create(MemoryModuleType.NEAREST_REPELLENT, 200),
                new AnimalMakeLove(EntityType.HOGLIN, 0.6F, 2),
                SetWalkTargetFromAttackTargetIfTargetOutOfReach.create(1.0F),
                BehaviorBuilder.triggerIf(Hoglin::isAdult, MeleeAttack.create(40)),
                BehaviorBuilder.triggerIf(AgeableMob::isBaby, MeleeAttack.create(15)),
                StopAttackingIfTargetInvalid.create(),
                EraseMemoryIf.create(HoglinAi::isBreeding, MemoryModuleType.ATTACK_TARGET)
            ),
            MemoryModuleType.ATTACK_TARGET
        );
    }

    private static void initRetreatActivity(Brain<Hoglin> brain) {
        brain.addActivityAndRemoveMemoryWhenStopped(
            Activity.AVOID,
            10,
            ImmutableList.of(
                SetWalkTargetAwayFrom.entity(MemoryModuleType.AVOID_TARGET, 1.3F, 15, false),
                createIdleMovementBehaviors(),
                SetEntityLookTargetSometimes.create(8.0F, UniformInt.of(30, 60)),
                EraseMemoryIf.create(HoglinAi::wantsToStopFleeing, MemoryModuleType.AVOID_TARGET)
            ),
            MemoryModuleType.AVOID_TARGET
        );
    }

    private static RunOne<Hoglin> createIdleMovementBehaviors() {
        return new RunOne<>(
            ImmutableList.of(Pair.of(RandomStroll.stroll(0.4F), 2), Pair.of(SetWalkTargetFromLookTarget.create(0.4F, 3), 2), Pair.of(new DoNothing(30, 60), 1))
        );
    }

    protected static void updateActivity(Hoglin hoglin) {
        Brain<Hoglin> brain = hoglin.getBrain();
        Activity activity = brain.getActiveNonCoreActivity().orElse(null);
        brain.setActiveActivityToFirstValid(ImmutableList.of(Activity.FIGHT, Activity.AVOID, Activity.IDLE));
        Activity activity1 = brain.getActiveNonCoreActivity().orElse(null);
        if (activity != activity1) {
            getSoundForCurrentActivity(hoglin).ifPresent(hoglin::makeSound);
        }

        hoglin.setAggressive(brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET));
    }

    protected static void onHitTarget(Hoglin hoglin, LivingEntity target) {
        if (!hoglin.isBaby()) {
            if (target.getType() == EntityType.PIGLIN && piglinsOutnumberHoglins(hoglin)) {
                setAvoidTarget(hoglin, target);
                broadcastRetreat(hoglin, target);
            } else {
                broadcastAttackTarget(hoglin, target);
            }
        }
    }

    private static void broadcastRetreat(Hoglin hoglin, LivingEntity target) {
        getVisibleAdultHoglins(hoglin).forEach(adults -> retreatFromNearestTarget(adults, target));
    }

    private static void retreatFromNearestTarget(Hoglin hoglin, LivingEntity target) {
        Brain<Hoglin> brain = hoglin.getBrain();
        LivingEntity livingEntity = BehaviorUtils.getNearestTarget(hoglin, brain.getMemory(MemoryModuleType.AVOID_TARGET), target);
        livingEntity = BehaviorUtils.getNearestTarget(hoglin, brain.getMemory(MemoryModuleType.ATTACK_TARGET), livingEntity);
        setAvoidTarget(hoglin, livingEntity);
    }

    private static void setAvoidTarget(Hoglin hoglin, LivingEntity target) {
        hoglin.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        hoglin.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        hoglin.getBrain().setMemoryWithExpiry(MemoryModuleType.AVOID_TARGET, target, RETREAT_DURATION.sample(hoglin.level().random));
    }

    private static Optional<? extends LivingEntity> findNearestValidAttackTarget(ServerLevel level, Hoglin hoglin) {
        return !isPacified(hoglin) && !isBreeding(hoglin) ? hoglin.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER) : Optional.empty();
    }

    static boolean isPosNearNearestRepellent(Hoglin hoglin, BlockPos pos) {
        Optional<BlockPos> memory = hoglin.getBrain().getMemory(MemoryModuleType.NEAREST_REPELLENT);
        return memory.isPresent() && memory.get().closerThan(pos, 8.0);
    }

    private static boolean wantsToStopFleeing(Hoglin hoglin) {
        return hoglin.isAdult() && !piglinsOutnumberHoglins(hoglin);
    }

    private static boolean piglinsOutnumberHoglins(Hoglin hoglin) {
        if (hoglin.isBaby()) {
            return false;
        } else {
            int i = hoglin.getBrain().getMemory(MemoryModuleType.VISIBLE_ADULT_PIGLIN_COUNT).orElse(0);
            int i1 = hoglin.getBrain().getMemory(MemoryModuleType.VISIBLE_ADULT_HOGLIN_COUNT).orElse(0) + 1;
            return i > i1;
        }
    }

    protected static void wasHurtBy(ServerLevel level, Hoglin hoglin, LivingEntity entity) {
        Brain<Hoglin> brain = hoglin.getBrain();
        brain.eraseMemory(MemoryModuleType.PACIFIED);
        brain.eraseMemory(MemoryModuleType.BREED_TARGET);
        if (hoglin.isBaby()) {
            retreatFromNearestTarget(hoglin, entity);
        } else {
            maybeRetaliate(level, hoglin, entity);
        }
    }

    private static void maybeRetaliate(ServerLevel level, Hoglin hoglin, LivingEntity entity) {
        if (!hoglin.getBrain().isActive(Activity.AVOID) || entity.getType() != EntityType.PIGLIN) {
            if (entity.getType() != EntityType.HOGLIN) {
                if (!BehaviorUtils.isOtherTargetMuchFurtherAwayThanCurrentAttackTarget(hoglin, entity, 4.0)) {
                    if (Sensor.isEntityAttackable(level, hoglin, entity)) {
                        setAttackTarget(hoglin, entity);
                        broadcastAttackTarget(hoglin, entity);
                    }
                }
            }
        }
    }

    private static void setAttackTarget(Hoglin hoglin, LivingEntity target) {
        Brain<Hoglin> brain = hoglin.getBrain();
        brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        brain.eraseMemory(MemoryModuleType.BREED_TARGET);
        brain.setMemoryWithExpiry(MemoryModuleType.ATTACK_TARGET, target, 200L);
    }

    private static void broadcastAttackTarget(Hoglin hoglin, LivingEntity target) {
        getVisibleAdultHoglins(hoglin).forEach(adults -> setAttackTargetIfCloserThanCurrent(adults, target));
    }

    private static void setAttackTargetIfCloserThanCurrent(Hoglin hoglin, LivingEntity target) {
        if (!isPacified(hoglin)) {
            Optional<LivingEntity> memory = hoglin.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET);
            LivingEntity nearestTarget = BehaviorUtils.getNearestTarget(hoglin, memory, target);
            setAttackTarget(hoglin, nearestTarget);
        }
    }

    public static Optional<SoundEvent> getSoundForCurrentActivity(Hoglin hoglin) {
        return hoglin.getBrain().getActiveNonCoreActivity().map(activity -> getSoundForActivity(hoglin, activity));
    }

    private static SoundEvent getSoundForActivity(Hoglin hoglin, Activity activity) {
        if (activity == Activity.AVOID || hoglin.isConverting()) {
            return SoundEvents.HOGLIN_RETREAT;
        } else if (activity == Activity.FIGHT) {
            return SoundEvents.HOGLIN_ANGRY;
        } else {
            return isNearRepellent(hoglin) ? SoundEvents.HOGLIN_RETREAT : SoundEvents.HOGLIN_AMBIENT;
        }
    }

    private static List<Hoglin> getVisibleAdultHoglins(Hoglin hoglin) {
        return hoglin.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_ADULT_HOGLINS).orElse(ImmutableList.of());
    }

    private static boolean isNearRepellent(Hoglin hoglin) {
        return hoglin.getBrain().hasMemoryValue(MemoryModuleType.NEAREST_REPELLENT);
    }

    private static boolean isBreeding(Hoglin hoglin) {
        return hoglin.getBrain().hasMemoryValue(MemoryModuleType.BREED_TARGET);
    }

    protected static boolean isPacified(Hoglin hoglin) {
        return hoglin.getBrain().hasMemoryValue(MemoryModuleType.PACIFIED);
    }
}
