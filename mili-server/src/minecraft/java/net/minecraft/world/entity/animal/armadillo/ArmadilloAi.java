package net.minecraft.world.entity.animal.armadillo;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.util.Pair;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.AnimalMakeLove;
import net.minecraft.world.entity.ai.behavior.AnimalPanic;
import net.minecraft.world.entity.ai.behavior.BabyFollowAdult;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.CountDownCooldownTicks;
import net.minecraft.world.entity.ai.behavior.DoNothing;
import net.minecraft.world.entity.ai.behavior.FollowTemptation;
import net.minecraft.world.entity.ai.behavior.LookAtTargetSink;
import net.minecraft.world.entity.ai.behavior.MoveToTargetSink;
import net.minecraft.world.entity.ai.behavior.OneShot;
import net.minecraft.world.entity.ai.behavior.RandomLookAround;
import net.minecraft.world.entity.ai.behavior.RandomStroll;
import net.minecraft.world.entity.ai.behavior.RunOne;
import net.minecraft.world.entity.ai.behavior.SetEntityLookTargetSometimes;
import net.minecraft.world.entity.ai.behavior.SetWalkTargetFromLookTarget;
import net.minecraft.world.entity.ai.behavior.Swim;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.schedule.Activity;

public class ArmadilloAi {
    private static final float SPEED_MULTIPLIER_WHEN_PANICKING = 2.0F;
    private static final float SPEED_MULTIPLIER_WHEN_IDLING = 1.0F;
    private static final float SPEED_MULTIPLIER_WHEN_TEMPTED = 1.25F;
    private static final float SPEED_MULTIPLIER_WHEN_FOLLOWING_ADULT = 1.25F;
    private static final float SPEED_MULTIPLIER_WHEN_MAKING_LOVE = 1.0F;
    private static final double DEFAULT_CLOSE_ENOUGH_DIST = 2.0;
    private static final double BABY_CLOSE_ENOUGH_DIST = 1.0;
    private static final UniformInt ADULT_FOLLOW_RANGE = UniformInt.of(5, 16);
    private static final ImmutableList<SensorType<? extends Sensor<? super Armadillo>>> SENSOR_TYPES = ImmutableList.of(
        SensorType.NEAREST_LIVING_ENTITIES, SensorType.HURT_BY, SensorType.FOOD_TEMPTATIONS, SensorType.NEAREST_ADULT, SensorType.ARMADILLO_SCARE_DETECTED
    );
    private static final ImmutableList<MemoryModuleType<?>> MEMORY_TYPES = ImmutableList.of(
        MemoryModuleType.IS_PANICKING,
        MemoryModuleType.HURT_BY,
        MemoryModuleType.HURT_BY_ENTITY,
        MemoryModuleType.WALK_TARGET,
        MemoryModuleType.LOOK_TARGET,
        MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE,
        MemoryModuleType.PATH,
        MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
        MemoryModuleType.TEMPTING_PLAYER,
        MemoryModuleType.TEMPTATION_COOLDOWN_TICKS,
        MemoryModuleType.GAZE_COOLDOWN_TICKS,
        MemoryModuleType.IS_TEMPTED,
        MemoryModuleType.BREED_TARGET,
        MemoryModuleType.NEAREST_VISIBLE_ADULT,
        MemoryModuleType.DANGER_DETECTED_RECENTLY
    );
    private static final OneShot<Armadillo> ARMADILLO_ROLLING_OUT = BehaviorBuilder.create(
        instance -> instance.group(instance.absent(MemoryModuleType.DANGER_DETECTED_RECENTLY)).apply(instance, memoryAccessor -> (level, entity, gameTime) -> {
            if (entity.isScared()) {
                entity.rollOut();
                return true;
            } else {
                return false;
            }
        })
    );

    public static Brain.Provider<Armadillo> brainProvider() {
        return Brain.provider(MEMORY_TYPES, SENSOR_TYPES);
    }

    protected static Brain<?> makeBrain(Brain<Armadillo> brain) {
        initCoreActivity(brain);
        initIdleActivity(brain);
        initScaredActivity(brain);
        brain.setCoreActivities(Set.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.useDefaultActivity();
        return brain;
    }

    private static void initCoreActivity(Brain<Armadillo> brain) {
        brain.addActivity(
            Activity.CORE,
            0,
            ImmutableList.of(
                new Swim<>(0.8F),
                new ArmadilloAi.ArmadilloPanic(2.0F),
                new LookAtTargetSink(45, 90),
                new MoveToTargetSink() {
                    @Override
                    protected boolean checkExtraStartConditions(ServerLevel level, Mob owner) {
                        return !(owner instanceof Armadillo armadillo && armadillo.isScared()) && super.checkExtraStartConditions(level, owner);
                    }
                },
                new CountDownCooldownTicks(MemoryModuleType.TEMPTATION_COOLDOWN_TICKS),
                new CountDownCooldownTicks(MemoryModuleType.GAZE_COOLDOWN_TICKS),
                ARMADILLO_ROLLING_OUT
            )
        );
    }

    private static void initIdleActivity(Brain<Armadillo> brain) {
        brain.addActivity(
            Activity.IDLE,
            ImmutableList.of(
                Pair.of(0, SetEntityLookTargetSometimes.create(EntityType.PLAYER, 6.0F, UniformInt.of(30, 60))),
                Pair.of(1, new AnimalMakeLove(EntityType.ARMADILLO, 1.0F, 1)),
                Pair.of(
                    2,
                    new RunOne<>(
                        ImmutableList.of(
                            Pair.of(new FollowTemptation(livingEntity -> 1.25F, livingEntity -> livingEntity.isBaby() ? 1.0 : 2.0), 1),
                            Pair.of(BabyFollowAdult.create(ADULT_FOLLOW_RANGE, 1.25F), 1)
                        )
                    )
                ),
                Pair.of(3, new RandomLookAround(UniformInt.of(150, 250), 30.0F, 0.0F, 0.0F)),
                Pair.of(
                    4,
                    new RunOne<>(
                        ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT),
                        ImmutableList.of(
                            Pair.of(RandomStroll.stroll(1.0F), 1), Pair.of(SetWalkTargetFromLookTarget.create(1.0F, 3), 1), Pair.of(new DoNothing(30, 60), 1)
                        )
                    )
                )
            )
        );
    }

    private static void initScaredActivity(Brain<Armadillo> brain) {
        brain.addActivityWithConditions(
            Activity.PANIC,
            ImmutableList.of(Pair.of(0, new ArmadilloAi.ArmadilloBallUp())),
            Set.of(
                Pair.of(MemoryModuleType.DANGER_DETECTED_RECENTLY, MemoryStatus.VALUE_PRESENT),
                Pair.of(MemoryModuleType.IS_PANICKING, MemoryStatus.VALUE_ABSENT)
            )
        );
    }

    public static void updateActivity(Armadillo armadillo) {
        armadillo.getBrain().setActiveActivityToFirstValid(ImmutableList.of(Activity.PANIC, Activity.IDLE));
    }

    public static class ArmadilloBallUp extends Behavior<Armadillo> {
        static final int BALL_UP_STAY_IN_STATE = 5 * TimeUtil.SECONDS_PER_MINUTE * 20;
        static final int TICKS_DELAY_TO_DETERMINE_IF_DANGER_IS_STILL_AROUND = 5;
        static final int DANGER_DETECTED_RECENTLY_DANGER_THRESHOLD = 75;
        int nextPeekTimer = 0;
        boolean dangerWasAround;

        public ArmadilloBallUp() {
            super(Map.of(), BALL_UP_STAY_IN_STATE);
        }

        @Override
        protected void tick(ServerLevel level, Armadillo owner, long gameTime) {
            super.tick(level, owner, gameTime);
            if (this.nextPeekTimer > 0) {
                this.nextPeekTimer--;
            }

            if (owner.shouldSwitchToScaredState()) {
                owner.switchToState(Armadillo.ArmadilloState.SCARED);
                if (owner.onGround()) {
                    owner.playSound(SoundEvents.ARMADILLO_LAND);
                }
            } else {
                Armadillo.ArmadilloState state = owner.getState();
                long timeUntilExpiry = owner.getBrain().getTimeUntilExpiry(MemoryModuleType.DANGER_DETECTED_RECENTLY);
                boolean flag = timeUntilExpiry > 75L;
                if (flag != this.dangerWasAround) {
                    this.nextPeekTimer = this.pickNextPeekTimer(owner);
                }

                this.dangerWasAround = flag;
                if (state == Armadillo.ArmadilloState.SCARED) {
                    if (this.nextPeekTimer == 0 && owner.onGround() && flag) {
                        level.broadcastEntityEvent(owner, EntityEvent.ARMADILLO_PEEK);
                        this.nextPeekTimer = this.pickNextPeekTimer(owner);
                    }

                    if (timeUntilExpiry < Armadillo.ArmadilloState.UNROLLING.animationDuration()) {
                        owner.playSound(SoundEvents.ARMADILLO_UNROLL_START);
                        owner.switchToState(Armadillo.ArmadilloState.UNROLLING);
                    }
                } else if (state == Armadillo.ArmadilloState.UNROLLING && timeUntilExpiry > Armadillo.ArmadilloState.UNROLLING.animationDuration()) {
                    owner.switchToState(Armadillo.ArmadilloState.SCARED);
                }
            }
        }

        private int pickNextPeekTimer(Armadillo armadillo) {
            return Armadillo.ArmadilloState.SCARED.animationDuration() + armadillo.getRandom().nextIntBetweenInclusive(100, 400);
        }

        @Override
        protected boolean checkExtraStartConditions(ServerLevel level, Armadillo owner) {
            return owner.onGround();
        }

        @Override
        protected boolean canStillUse(ServerLevel level, Armadillo entity, long gameTime) {
            return entity.getState().isThreatened();
        }

        @Override
        protected void start(ServerLevel level, Armadillo entity, long gameTime) {
            entity.rollUp();
        }

        @Override
        protected void stop(ServerLevel level, Armadillo entity, long gameTime) {
            if (!entity.canStayRolledUp()) {
                entity.rollOut();
            }
        }
    }

    public static class ArmadilloPanic extends AnimalPanic<Armadillo> {
        public ArmadilloPanic(float speedMultiplier) {
            super(speedMultiplier, pathfinderMob -> DamageTypeTags.PANIC_ENVIRONMENTAL_CAUSES);
        }

        @Override
        protected void start(ServerLevel level, Armadillo entity, long gameTime) {
            entity.rollOut();
            super.start(level, entity, gameTime);
        }
    }
}
