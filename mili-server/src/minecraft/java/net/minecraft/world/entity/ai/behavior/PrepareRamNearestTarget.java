package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;

public class PrepareRamNearestTarget<E extends PathfinderMob> extends Behavior<E> {
    public static final int TIME_OUT_DURATION = 160;
    private final ToIntFunction<E> getCooldownOnFail;
    private final int minRamDistance;
    private final int maxRamDistance;
    private final float walkSpeed;
    private final TargetingConditions ramTargeting;
    private final int ramPrepareTime;
    private final Function<E, SoundEvent> getPrepareRamSound;
    private Optional<Long> reachedRamPositionTimestamp = Optional.empty();
    private Optional<PrepareRamNearestTarget.RamCandidate> ramCandidate = Optional.empty();

    public PrepareRamNearestTarget(
        ToIntFunction<E> getCooldownOnFail,
        int minRamDistance,
        int maxRamDistance,
        float walkSpeed,
        TargetingConditions ramTargeting,
        int ramPrepareTime,
        Function<E, SoundEvent> getPrepareRamSound
    ) {
        super(
            ImmutableMap.of(
                MemoryModuleType.LOOK_TARGET,
                MemoryStatus.REGISTERED,
                MemoryModuleType.RAM_COOLDOWN_TICKS,
                MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
                MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.RAM_TARGET,
                MemoryStatus.VALUE_ABSENT
            ),
            160
        );
        this.getCooldownOnFail = getCooldownOnFail;
        this.minRamDistance = minRamDistance;
        this.maxRamDistance = maxRamDistance;
        this.walkSpeed = walkSpeed;
        this.ramTargeting = ramTargeting;
        this.ramPrepareTime = ramPrepareTime;
        this.getPrepareRamSound = getPrepareRamSound;
    }

    @Override
    protected void start(ServerLevel level, PathfinderMob entity, long gameTime) {
        Brain<?> brain = entity.getBrain();
        brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)
            .flatMap(
                nearestVisibleLivingEntities -> nearestVisibleLivingEntities.findClosest(livingEntity -> this.ramTargeting.test(level, entity, livingEntity))
            )
            // CraftBukkit start
            .map((livingEntity) -> {
                org.bukkit.event.entity.EntityTargetEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callEntityTargetLivingEvent(entity, livingEntity, (livingEntity instanceof net.minecraft.server.level.ServerPlayer) ? org.bukkit.event.entity.EntityTargetEvent.TargetReason.CLOSEST_PLAYER : org.bukkit.event.entity.EntityTargetEvent.TargetReason.CLOSEST_ENTITY);
                if (event.isCancelled() || event.getTarget() == null) {
                    return null;
                }
                livingEntity = ((org.bukkit.craftbukkit.entity.CraftLivingEntity) event.getTarget()).getHandle();
                return livingEntity;
            })
            // CraftBukkit end
            .ifPresent(entity1 -> this.chooseRamPosition(entity, entity1));
    }

    @Override
    protected void stop(ServerLevel level, E entity, long gameTime) {
        Brain<?> brain = entity.getBrain();
        if (!brain.hasMemoryValue(MemoryModuleType.RAM_TARGET)) {
            level.broadcastEntityEvent(entity, EntityEvent.END_RAM);
            brain.setMemory(MemoryModuleType.RAM_COOLDOWN_TICKS, this.getCooldownOnFail.applyAsInt(entity));
        }
    }

    @Override
    protected boolean canStillUse(ServerLevel level, PathfinderMob entity, long gameTime) {
        return this.ramCandidate.isPresent() && this.ramCandidate.get().getTarget().isAlive();
    }

    @Override
    protected void tick(ServerLevel level, E owner, long gameTime) {
        if (!this.ramCandidate.isEmpty()) {
            owner.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(this.ramCandidate.get().getStartPosition(), this.walkSpeed, 0));
            owner.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(this.ramCandidate.get().getTarget(), true));
            boolean flag = !this.ramCandidate.get().getTarget().blockPosition().equals(this.ramCandidate.get().getTargetPosition());
            if (flag) {
                level.broadcastEntityEvent(owner, EntityEvent.END_RAM);
                owner.getNavigation().stop();
                this.chooseRamPosition(owner, this.ramCandidate.get().target);
            } else {
                BlockPos blockPos = owner.blockPosition();
                if (blockPos.equals(this.ramCandidate.get().getStartPosition())) {
                    level.broadcastEntityEvent(owner, EntityEvent.START_RAM);
                    if (this.reachedRamPositionTimestamp.isEmpty()) {
                        this.reachedRamPositionTimestamp = Optional.of(gameTime);
                    }

                    if (gameTime - this.reachedRamPositionTimestamp.get() >= this.ramPrepareTime) {
                        owner.getBrain().setMemory(MemoryModuleType.RAM_TARGET, this.getEdgeOfBlock(blockPos, this.ramCandidate.get().getTargetPosition()));
                        level.playSound(null, owner, this.getPrepareRamSound.apply(owner), SoundSource.NEUTRAL, 1.0F, owner.getVoicePitch());
                        this.ramCandidate = Optional.empty();
                    }
                }
            }
        }
    }

    private Vec3 getEdgeOfBlock(BlockPos pos, BlockPos other) {
        double d = 0.5;
        double d1 = 0.5 * Mth.sign(other.getX() - pos.getX());
        double d2 = 0.5 * Mth.sign(other.getZ() - pos.getZ());
        return Vec3.atBottomCenterOf(other).add(d1, 0.0, d2);
    }

    private Optional<BlockPos> calculateRammingStartPosition(PathfinderMob pathfinder, LivingEntity entity) {
        BlockPos blockPos = entity.blockPosition();
        if (!this.isWalkableBlock(pathfinder, blockPos)) {
            return Optional.empty();
        } else {
            List<BlockPos> list = Lists.newArrayList();
            BlockPos.MutableBlockPos mutableBlockPos = blockPos.mutable();

            for (Direction direction : Direction.Plane.HORIZONTAL) {
                mutableBlockPos.set(blockPos);

                for (int i = 0; i < this.maxRamDistance; i++) {
                    if (!this.isWalkableBlock(pathfinder, mutableBlockPos.move(direction))) {
                        mutableBlockPos.move(direction.getOpposite());
                        break;
                    }
                }

                if (mutableBlockPos.distManhattan(blockPos) >= this.minRamDistance) {
                    list.add(mutableBlockPos.immutable());
                }
            }

            PathNavigation navigation = pathfinder.getNavigation();
            return list.stream().sorted(Comparator.comparingDouble(pathfinder.blockPosition()::distSqr)).filter(pos -> {
                Path path = navigation.createPath(pos, 0);
                return path != null && path.canReach();
            }).findFirst();
        }
    }

    private boolean isWalkableBlock(PathfinderMob pathfinder, BlockPos pos) {
        return pathfinder.getNavigation().isStableDestination(pos)
            && pathfinder.getPathfindingMalus(WalkNodeEvaluator.getPathTypeStatic(pathfinder, pos)) == 0.0F;
    }

    private void chooseRamPosition(PathfinderMob pathfinder, LivingEntity entity) {
        this.reachedRamPositionTimestamp = Optional.empty();
        this.ramCandidate = this.calculateRammingStartPosition(pathfinder, entity)
            .map(blockPos -> new PrepareRamNearestTarget.RamCandidate(blockPos, entity.blockPosition(), entity));
    }

    public static class RamCandidate {
        private final BlockPos startPosition;
        private final BlockPos targetPosition;
        final LivingEntity target;

        public RamCandidate(BlockPos startPosition, BlockPos targetPosition, LivingEntity target) {
            this.startPosition = startPosition;
            this.targetPosition = targetPosition;
            this.target = target;
        }

        public BlockPos getStartPosition() {
            return this.startPosition;
        }

        public BlockPos getTargetPosition() {
            return this.targetPosition;
        }

        public LivingEntity getTarget() {
            return this.target;
        }
    }
}
