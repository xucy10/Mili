package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.function.TriConsumer;
import org.jspecify.annotations.Nullable;

public class TransportItemsBetweenContainers extends Behavior<PathfinderMob> {
    public static final int TARGET_INTERACTION_TIME = 60;
    private static final int VISITED_POSITIONS_MEMORY_TIME = 6000;
    private static final int TRANSPORTED_ITEM_MAX_STACK_SIZE = 16;
    private static final int MAX_VISITED_POSITIONS = 10;
    private static final int MAX_UNREACHABLE_POSITIONS = 50;
    private static final int PASSENGER_MOB_TARGET_SEARCH_DISTANCE = 1;
    private static final int IDLE_COOLDOWN = 140;
    private static final double CLOSE_ENOUGH_TO_START_QUEUING_DISTANCE = 3.0;
    private static final double CLOSE_ENOUGH_TO_START_INTERACTING_WITH_TARGET_DISTANCE = 0.5;
    private static final double CLOSE_ENOUGH_TO_START_INTERACTING_WITH_TARGET_PATH_END_DISTANCE = 1.0;
    private static final double CLOSE_ENOUGH_TO_CONTINUE_INTERACTING_WITH_TARGET = 2.0;
    private final float speedModifier;
    private final int horizontalSearchDistance;
    private final int verticalSearchDistance;
    private final Predicate<BlockState> sourceBlockType;
    private final Predicate<BlockState> destinationBlockType;
    private final Predicate<TransportItemsBetweenContainers.TransportItemTarget> shouldQueueForTarget;
    private final Consumer<PathfinderMob> onStartTravelling;
    private final Map<TransportItemsBetweenContainers.ContainerInteractionState, TransportItemsBetweenContainers.OnTargetReachedInteraction> onTargetInteractionActions;
    private TransportItemsBetweenContainers.@Nullable TransportItemTarget target = null;
    private TransportItemsBetweenContainers.TransportItemState state;
    private TransportItemsBetweenContainers.@Nullable ContainerInteractionState interactionState;
    private int ticksSinceReachingTarget;

    public TransportItemsBetweenContainers(
        float speedModifier,
        Predicate<BlockState> sourceBlockType,
        Predicate<BlockState> destinationBlockType,
        int horizontalSearchDistance,
        int verticalSearchDistance,
        Map<TransportItemsBetweenContainers.ContainerInteractionState, TransportItemsBetweenContainers.OnTargetReachedInteraction> onTargetInteractionActions,
        Consumer<PathfinderMob> onStartTravelling,
        Predicate<TransportItemsBetweenContainers.TransportItemTarget> shouldQueueForTarget
    ) {
        super(
            ImmutableMap.of(
                MemoryModuleType.VISITED_BLOCK_POSITIONS,
                MemoryStatus.REGISTERED,
                MemoryModuleType.UNREACHABLE_TRANSPORT_BLOCK_POSITIONS,
                MemoryStatus.REGISTERED,
                MemoryModuleType.TRANSPORT_ITEMS_COOLDOWN_TICKS,
                MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.IS_PANICKING,
                MemoryStatus.VALUE_ABSENT
            )
        );
        this.speedModifier = speedModifier;
        this.sourceBlockType = sourceBlockType;
        this.destinationBlockType = destinationBlockType;
        this.horizontalSearchDistance = horizontalSearchDistance;
        this.verticalSearchDistance = verticalSearchDistance;
        this.onStartTravelling = onStartTravelling;
        this.shouldQueueForTarget = shouldQueueForTarget;
        this.onTargetInteractionActions = onTargetInteractionActions;
        this.state = TransportItemsBetweenContainers.TransportItemState.TRAVELLING;
    }

    @Override
    protected void start(ServerLevel level, PathfinderMob entity, long gameTime) {
        if (entity.getNavigation() instanceof GroundPathNavigation groundPathNavigation) {
            groundPathNavigation.setCanPathToTargetsBelowSurface(true);
        }
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, PathfinderMob owner) {
        return !owner.isLeashed();
    }

    @Override
    protected boolean canStillUse(ServerLevel level, PathfinderMob entity, long gameTime) {
        return entity.getBrain().getMemory(MemoryModuleType.TRANSPORT_ITEMS_COOLDOWN_TICKS).isEmpty() && !entity.isPanicking() && !entity.isLeashed();
    }

    @Override
    protected boolean timedOut(long gameTime) {
        return false;
    }

    @Override
    protected void tick(ServerLevel level, PathfinderMob owner, long gameTime) {
        boolean flag = this.updateInvalidTarget(level, owner);
        if (this.target == null) {
            this.stop(level, owner, gameTime);
        } else if (!flag) {
            if (this.state.equals(TransportItemsBetweenContainers.TransportItemState.QUEUING)) {
                this.onQueuingForTarget(this.target, level, owner);
            }

            if (this.state.equals(TransportItemsBetweenContainers.TransportItemState.TRAVELLING)) {
                this.onTravelToTarget(this.target, level, owner);
            }

            if (this.state.equals(TransportItemsBetweenContainers.TransportItemState.INTERACTING)) {
                this.onReachedTarget(this.target, level, owner);
            }
        }
    }

    private boolean updateInvalidTarget(ServerLevel level, PathfinderMob mob) {
        if (!this.hasValidTarget(level, mob)) {
            this.stopTargetingCurrentTarget(mob);
            Optional<TransportItemsBetweenContainers.TransportItemTarget> transportTarget = this.getTransportTarget(level, mob);
            if (transportTarget.isPresent()) {
                this.target = transportTarget.get();
                this.onStartTravelling(mob);
                this.setVisitedBlockPos(mob, level, this.target.pos);
                return true;
            } else {
                this.enterCooldownAfterNoMatchingTargetFound(mob);
                return true;
            }
        } else {
            return false;
        }
    }

    private void onQueuingForTarget(TransportItemsBetweenContainers.TransportItemTarget target, Level level, PathfinderMob mob) {
        if (!this.isAnotherMobInteractingWithTarget(target, level)) {
            this.resumeTravelling(mob);
        }
    }

    protected void onTravelToTarget(TransportItemsBetweenContainers.TransportItemTarget target, Level level, PathfinderMob mob) {
        if (this.isWithinTargetDistance(3.0, target, level, mob, this.getCenterPos(mob)) && this.isAnotherMobInteractingWithTarget(target, level)) {
            this.startQueuing(mob);
        } else if (this.isWithinTargetDistance(getInteractionRange(mob), target, level, mob, this.getCenterPos(mob))) {
            this.startOnReachedTargetInteraction(target, mob);
        } else {
            this.walkTowardsTarget(mob);
        }
    }

    private Vec3 getCenterPos(PathfinderMob mob) {
        return this.setMiddleYPosition(mob, mob.position());
    }

    protected void onReachedTarget(TransportItemsBetweenContainers.TransportItemTarget target, Level level, PathfinderMob mob) {
        if (!this.isWithinTargetDistance(2.0, target, level, mob, this.getCenterPos(mob))) {
            this.onStartTravelling(mob);
        } else {
            this.ticksSinceReachingTarget++;
            this.onTargetInteraction(target, mob);
            if (this.ticksSinceReachingTarget >= 60) {
                this.doReachedTargetInteraction(
                    mob,
                    target.container,
                    this::pickUpItems,
                    (pathfinderMob, container) -> this.stopTargetingCurrentTarget(mob),
                    this::putDownItem,
                    (pathfinderMob, container) -> this.stopTargetingCurrentTarget(mob)
                );
                this.onStartTravelling(mob);
            }
        }
    }

    private void startQueuing(PathfinderMob mob) {
        this.stopInPlace(mob);
        this.setTransportingState(TransportItemsBetweenContainers.TransportItemState.QUEUING);
    }

    private void resumeTravelling(PathfinderMob mob) {
        this.setTransportingState(TransportItemsBetweenContainers.TransportItemState.TRAVELLING);
        this.walkTowardsTarget(mob);
    }

    private void walkTowardsTarget(PathfinderMob mob) {
        if (this.target != null) {
            BehaviorUtils.setWalkAndLookTargetMemories(mob, this.target.pos, this.speedModifier, 0);
        }
    }

    private void startOnReachedTargetInteraction(TransportItemsBetweenContainers.TransportItemTarget target, PathfinderMob mob) {
        this.doReachedTargetInteraction(
            mob,
            target.container,
            this.onReachedInteraction(TransportItemsBetweenContainers.ContainerInteractionState.PICKUP_ITEM),
            this.onReachedInteraction(TransportItemsBetweenContainers.ContainerInteractionState.PICKUP_NO_ITEM),
            this.onReachedInteraction(TransportItemsBetweenContainers.ContainerInteractionState.PLACE_ITEM),
            this.onReachedInteraction(TransportItemsBetweenContainers.ContainerInteractionState.PLACE_NO_ITEM)
        );
        this.setTransportingState(TransportItemsBetweenContainers.TransportItemState.INTERACTING);
    }

    private void onStartTravelling(PathfinderMob mob) {
        this.onStartTravelling.accept(mob);
        this.setTransportingState(TransportItemsBetweenContainers.TransportItemState.TRAVELLING);
        this.interactionState = null;
        this.ticksSinceReachingTarget = 0;
    }

    private BiConsumer<PathfinderMob, Container> onReachedInteraction(TransportItemsBetweenContainers.ContainerInteractionState state) {
        return (pathfinderMob, container) -> this.setInteractionState(state);
    }

    private void setTransportingState(TransportItemsBetweenContainers.TransportItemState state) {
        this.state = state;
    }

    private void setInteractionState(TransportItemsBetweenContainers.ContainerInteractionState state) {
        this.interactionState = state;
    }

    private void onTargetInteraction(TransportItemsBetweenContainers.TransportItemTarget target, PathfinderMob mob) {
        mob.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(target.pos));
        this.stopInPlace(mob);
        if (this.interactionState != null) {
            Optional.ofNullable(this.onTargetInteractionActions.get(this.interactionState))
                .ifPresent(onTargetReachedInteraction -> onTargetReachedInteraction.accept(mob, target, this.ticksSinceReachingTarget));
        }
    }

    private void doReachedTargetInteraction(
        PathfinderMob mob,
        Container container,
        BiConsumer<PathfinderMob, Container> pickupItem,
        BiConsumer<PathfinderMob, Container> pickupNoItem,
        BiConsumer<PathfinderMob, Container> placeItem,
        BiConsumer<PathfinderMob, Container> placeNoItem
    ) {
        if (isPickingUpItems(mob)) {
            if (matchesGettingItemsRequirement(container)) {
                pickupItem.accept(mob, container);
            } else {
                pickupNoItem.accept(mob, container);
            }
        } else if (matchesLeavingItemsRequirement(mob, container)) {
            placeItem.accept(mob, container);
        } else {
            placeNoItem.accept(mob, container);
        }
    }

    private Optional<TransportItemsBetweenContainers.TransportItemTarget> getTransportTarget(ServerLevel level, PathfinderMob mob) {
        AABB targetSearchArea = this.getTargetSearchArea(mob);
        Set<GlobalPos> visitedPositions = getVisitedPositions(mob);
        Set<GlobalPos> unreachablePositions = getUnreachablePositions(mob);
        List<ChunkPos> list = ChunkPos.rangeClosed(new ChunkPos(mob.blockPosition()), Math.floorDiv(this.getHorizontalSearchDistance(mob), 16) + 1).toList();
        TransportItemsBetweenContainers.TransportItemTarget transportItemTarget = null;
        double d = Float.MAX_VALUE;

        for (ChunkPos chunkPos : list) {
            LevelChunk chunkNow = level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
            if (chunkNow != null) {
                for (BlockEntity blockEntity : chunkNow.getBlockEntities().values()) {
                    if (blockEntity instanceof ChestBlockEntity chestBlockEntity) {
                        double d1 = chestBlockEntity.getBlockPos().distToCenterSqr(mob.position());
                        if (d1 < d) {
                            TransportItemsBetweenContainers.TransportItemTarget transportItemTarget1 = this.isTargetValidToPick(
                                mob, level, chestBlockEntity, visitedPositions, unreachablePositions, targetSearchArea
                            );
                            if (transportItemTarget1 != null) {
                                transportItemTarget = transportItemTarget1;
                                d = d1;
                            }
                        }
                    }
                }
            }
        }

        return transportItemTarget == null ? Optional.empty() : Optional.of(transportItemTarget);
    }

    private TransportItemsBetweenContainers.@Nullable TransportItemTarget isTargetValidToPick(
        PathfinderMob mob, Level level, BlockEntity blockEntity, Set<GlobalPos> visitedPositions, Set<GlobalPos> unreachablePositions, AABB searchArea
    ) {
        BlockPos blockPos = blockEntity.getBlockPos();
        boolean flag = searchArea.contains(blockPos.getX(), blockPos.getY(), blockPos.getZ());
        if (!flag) {
            return null;
        } else {
            TransportItemsBetweenContainers.TransportItemTarget transportItemTarget = TransportItemsBetweenContainers.TransportItemTarget.tryCreatePossibleTarget(
                blockEntity, level
            );
            if (transportItemTarget == null) {
                return null;
            } else {
                boolean flag1 = this.isWantedBlock(mob, transportItemTarget.state)
                    && !this.isPositionAlreadyVisited(visitedPositions, unreachablePositions, transportItemTarget, level)
                    // Paper start - ItemTransportingEntityValidateTargetEvent
                    && !this.isContainerLocked(transportItemTarget)
                    && org.bukkit.craftbukkit.event.CraftEventFactory.callTransporterValidateTarget(mob, level, transportItemTarget.pos);
                    // Paper end - ItemTransportingEntityValidateTargetEvent
                return flag1 ? transportItemTarget : null;
            }
        }
    }

    private boolean isContainerLocked(TransportItemsBetweenContainers.TransportItemTarget target) {
        return target.blockEntity instanceof BaseContainerBlockEntity baseContainerBlockEntity && baseContainerBlockEntity.isLocked();
    }

    private boolean hasValidTarget(Level level, PathfinderMob mob) {
        boolean flag = this.target != null && this.isWantedBlock(mob, this.target.state) && this.targetHasNotChanged(level, this.target);
        if (flag && !this.isTargetBlocked(level, this.target)) {
            if (!this.state.equals(TransportItemsBetweenContainers.TransportItemState.TRAVELLING)) {
                return true;
            }

            if (this.hasValidTravellingPath(level, this.target, mob)) {
                return true;
            }

            this.markVisitedBlockPosAsUnreachable(mob, level, this.target.pos);
        }

        return false;
    }

    private boolean hasValidTravellingPath(Level level, TransportItemsBetweenContainers.TransportItemTarget target, PathfinderMob mob) {
        Path path = mob.getNavigation().getPath() == null ? mob.getNavigation().createPath(target.pos, 0) : mob.getNavigation().getPath();
        Vec3 positionToReachTargetFrom = this.getPositionToReachTargetFrom(path, mob);
        boolean isWithinTargetDistance = this.isWithinTargetDistance(getInteractionRange(mob), target, level, mob, positionToReachTargetFrom);
        boolean flag = path == null && !isWithinTargetDistance;
        return flag || this.targetIsReachableFromPosition(level, isWithinTargetDistance, positionToReachTargetFrom, target, mob);
    }

    private Vec3 getPositionToReachTargetFrom(@Nullable Path path, PathfinderMob mob) {
        boolean flag = path == null || path.getEndNode() == null;
        Vec3 vec3 = flag ? mob.position() : path.getEndNode().asBlockPos().getBottomCenter();
        return this.setMiddleYPosition(mob, vec3);
    }

    private Vec3 setMiddleYPosition(PathfinderMob mob, Vec3 pos) {
        return pos.add(0.0, mob.getBoundingBox().getYsize() / 2.0, 0.0);
    }

    private boolean isTargetBlocked(Level level, TransportItemsBetweenContainers.TransportItemTarget target) {
        return ChestBlock.isChestBlockedAt(level, target.pos);
    }

    private boolean targetHasNotChanged(Level level, TransportItemsBetweenContainers.TransportItemTarget target) {
        return target.blockEntity.equals(level.getBlockEntity(target.pos));
    }

    private Stream<TransportItemsBetweenContainers.TransportItemTarget> getConnectedTargets(
        TransportItemsBetweenContainers.TransportItemTarget target, Level level
    ) {
        if (target.state.getValueOrElse(ChestBlock.TYPE, ChestType.SINGLE) != ChestType.SINGLE) {
            TransportItemsBetweenContainers.TransportItemTarget transportItemTarget = TransportItemsBetweenContainers.TransportItemTarget.tryCreatePossibleTarget(
                ChestBlock.getConnectedBlockPos(target.pos, target.state), level
            );
            return transportItemTarget != null ? Stream.of(target, transportItemTarget) : Stream.of(target);
        } else {
            return Stream.of(target);
        }
    }

    private AABB getTargetSearchArea(PathfinderMob mob) {
        int horizontalSearchDistance = this.getHorizontalSearchDistance(mob);
        return new AABB(mob.blockPosition()).inflate(horizontalSearchDistance, this.getVerticalSearchDistance(mob), horizontalSearchDistance);
    }

    private int getHorizontalSearchDistance(PathfinderMob mob) {
        return mob.isPassenger() ? 1 : this.horizontalSearchDistance;
    }

    private int getVerticalSearchDistance(PathfinderMob mob) {
        return mob.isPassenger() ? 1 : this.verticalSearchDistance;
    }

    private static Set<GlobalPos> getVisitedPositions(PathfinderMob mob) {
        return mob.getBrain().getMemory(MemoryModuleType.VISITED_BLOCK_POSITIONS).orElse(Set.of());
    }

    private static Set<GlobalPos> getUnreachablePositions(PathfinderMob mob) {
        return mob.getBrain().getMemory(MemoryModuleType.UNREACHABLE_TRANSPORT_BLOCK_POSITIONS).orElse(Set.of());
    }

    private boolean isPositionAlreadyVisited(
        Set<GlobalPos> visitedPositions, Set<GlobalPos> unreachablePositions, TransportItemsBetweenContainers.TransportItemTarget target, Level level
    ) {
        return this.getConnectedTargets(target, level)
            .map(transportItemTarget -> new GlobalPos(level.dimension(), transportItemTarget.pos))
            .anyMatch(globalPos -> visitedPositions.contains(globalPos) || unreachablePositions.contains(globalPos));
    }

    private static boolean hasFinishedPath(PathfinderMob mob) {
        return mob.getNavigation().getPath() != null && mob.getNavigation().getPath().isDone();
    }

    protected void setVisitedBlockPos(PathfinderMob mob, Level level, BlockPos pos) {
        Set<GlobalPos> set = new HashSet<>(getVisitedPositions(mob));
        set.add(new GlobalPos(level.dimension(), pos));
        if (set.size() > 10) {
            this.enterCooldownAfterNoMatchingTargetFound(mob);
        } else {
            mob.getBrain().setMemoryWithExpiry(MemoryModuleType.VISITED_BLOCK_POSITIONS, set, 6000L);
        }
    }

    protected void markVisitedBlockPosAsUnreachable(PathfinderMob mob, Level level, BlockPos pos) {
        Set<GlobalPos> set = new HashSet<>(getVisitedPositions(mob));
        set.remove(new GlobalPos(level.dimension(), pos));
        Set<GlobalPos> set1 = new HashSet<>(getUnreachablePositions(mob));
        set1.add(new GlobalPos(level.dimension(), pos));
        if (set1.size() > 50) {
            this.enterCooldownAfterNoMatchingTargetFound(mob);
        } else {
            mob.getBrain().setMemoryWithExpiry(MemoryModuleType.VISITED_BLOCK_POSITIONS, set, 6000L);
            mob.getBrain().setMemoryWithExpiry(MemoryModuleType.UNREACHABLE_TRANSPORT_BLOCK_POSITIONS, set1, 6000L);
        }
    }

    private boolean isWantedBlock(PathfinderMob mob, BlockState state) {
        return isPickingUpItems(mob) ? this.sourceBlockType.test(state) : this.destinationBlockType.test(state);
    }

    private static double getInteractionRange(PathfinderMob mob) {
        return hasFinishedPath(mob) ? 1.0 : 0.5;
    }

    private boolean isWithinTargetDistance(
        double distance, TransportItemsBetweenContainers.TransportItemTarget target, Level level, PathfinderMob mob, Vec3 pos
    ) {
        AABB boundingBox = mob.getBoundingBox();
        AABB aabb = AABB.ofSize(pos, boundingBox.getXsize(), boundingBox.getYsize(), boundingBox.getZsize());
        return target.state.getCollisionShape(level, target.pos).bounds().inflate(distance, 0.5, distance).move(target.pos).intersects(aabb);
    }

    private boolean targetIsReachableFromPosition(
        Level level, boolean withinDistance, Vec3 pos, TransportItemsBetweenContainers.TransportItemTarget target, PathfinderMob mob
    ) {
        return withinDistance && this.canSeeAnyTargetSide(target, level, mob, pos);
    }

    private boolean canSeeAnyTargetSide(TransportItemsBetweenContainers.TransportItemTarget target, Level level, PathfinderMob mob, Vec3 pos) {
        Vec3 center = target.pos.getCenter();
        return Direction.stream()
            .map(direction -> center.add(0.5 * direction.getStepX(), 0.5 * direction.getStepY(), 0.5 * direction.getStepZ()))
            .map(vec3 -> level.clip(new ClipContext(pos, vec3, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob)))
            .anyMatch(blockHitResult -> blockHitResult.getType() == HitResult.Type.BLOCK && blockHitResult.getBlockPos().equals(target.pos));
    }

    private boolean isAnotherMobInteractingWithTarget(TransportItemsBetweenContainers.TransportItemTarget target, Level level) {
        return this.getConnectedTargets(target, level).anyMatch(this.shouldQueueForTarget);
    }

    private static boolean isPickingUpItems(PathfinderMob mob) {
        return mob.getMainHandItem().isEmpty();
    }

    private static boolean matchesGettingItemsRequirement(Container container) {
        return !container.isEmpty();
    }

    private static boolean matchesLeavingItemsRequirement(PathfinderMob mob, Container container) {
        return container.isEmpty() || hasItemMatchingHandItem(mob, container);
    }

    private static boolean hasItemMatchingHandItem(PathfinderMob mob, Container container) {
        ItemStack mainHandItem = mob.getMainHandItem();

        for (ItemStack itemStack : container) {
            if (ItemStack.isSameItem(itemStack, mainHandItem)) {
                return true;
            }
        }

        return false;
    }

    private void pickUpItems(PathfinderMob mob, Container container) {
        mob.setItemSlot(EquipmentSlot.MAINHAND, pickupItemFromContainer(container));
        mob.setGuaranteedDrop(EquipmentSlot.MAINHAND);
        container.setChanged();
        this.clearMemoriesAfterMatchingTargetFound(mob);
    }

    private void putDownItem(PathfinderMob mob, Container container) {
        ItemStack itemStack = addItemsToContainer(mob, container);
        container.setChanged();
        mob.setItemSlot(EquipmentSlot.MAINHAND, itemStack);
        if (itemStack.isEmpty()) {
            this.clearMemoriesAfterMatchingTargetFound(mob);
        } else {
            this.stopTargetingCurrentTarget(mob);
        }
    }

    private static ItemStack pickupItemFromContainer(Container container) {
        int i = 0;

        for (ItemStack itemStack : container) {
            if (!itemStack.isEmpty()) {
                int min = Math.min(itemStack.getCount(), 16);
                return container.removeItem(i, min);
            }

            i++;
        }

        return ItemStack.EMPTY;
    }

    private static ItemStack addItemsToContainer(PathfinderMob mob, Container container) {
        int i = 0;
        ItemStack mainHandItem = mob.getMainHandItem();

        for (ItemStack itemStack : container) {
            if (itemStack.isEmpty()) {
                container.setItem(i, mainHandItem);
                return ItemStack.EMPTY;
            }

            if (ItemStack.isSameItemSameComponents(itemStack, mainHandItem) && itemStack.getCount() < itemStack.getMaxStackSize()) {
                int i1 = itemStack.getMaxStackSize() - itemStack.getCount();
                int min = Math.min(i1, mainHandItem.getCount());
                itemStack.setCount(itemStack.getCount() + min);
                mainHandItem.setCount(mainHandItem.getCount() - i1);
                container.setItem(i, itemStack);
                if (mainHandItem.isEmpty()) {
                    return ItemStack.EMPTY;
                }
            }

            i++;
        }

        return mainHandItem;
    }

    protected void stopTargetingCurrentTarget(PathfinderMob mob) {
        this.ticksSinceReachingTarget = 0;
        this.target = null;
        mob.getNavigation().stop();
        mob.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }

    protected void clearMemoriesAfterMatchingTargetFound(PathfinderMob mob) {
        this.stopTargetingCurrentTarget(mob);
        mob.getBrain().eraseMemory(MemoryModuleType.VISITED_BLOCK_POSITIONS);
        mob.getBrain().eraseMemory(MemoryModuleType.UNREACHABLE_TRANSPORT_BLOCK_POSITIONS);
    }

    private void enterCooldownAfterNoMatchingTargetFound(PathfinderMob mob) {
        this.stopTargetingCurrentTarget(mob);
        mob.getBrain().setMemory(MemoryModuleType.TRANSPORT_ITEMS_COOLDOWN_TICKS, 140);
        mob.getBrain().eraseMemory(MemoryModuleType.VISITED_BLOCK_POSITIONS);
        mob.getBrain().eraseMemory(MemoryModuleType.UNREACHABLE_TRANSPORT_BLOCK_POSITIONS);
    }

    @Override
    protected void stop(ServerLevel level, PathfinderMob entity, long gameTime) {
        this.onStartTravelling(entity);
        if (entity.getNavigation() instanceof GroundPathNavigation groundPathNavigation) {
            groundPathNavigation.setCanPathToTargetsBelowSurface(false);
        }
    }

    private void stopInPlace(PathfinderMob entity) {
        entity.getNavigation().stop();
        entity.setXxa(0.0F);
        entity.setYya(0.0F);
        entity.setSpeed(0.0F);
        entity.setDeltaMovement(0.0, entity.getDeltaMovement().y, 0.0);
    }

    public static enum ContainerInteractionState {
        PICKUP_ITEM,
        PICKUP_NO_ITEM,
        PLACE_ITEM,
        PLACE_NO_ITEM;
    }

    @FunctionalInterface
    public interface OnTargetReachedInteraction extends TriConsumer<PathfinderMob, TransportItemsBetweenContainers.TransportItemTarget, Integer> {
    }

    public static enum TransportItemState {
        TRAVELLING,
        QUEUING,
        INTERACTING;
    }

    public record TransportItemTarget(BlockPos pos, Container container, BlockEntity blockEntity, BlockState state) {
        public static TransportItemsBetweenContainers.@Nullable TransportItemTarget tryCreatePossibleTarget(BlockEntity blockEntity, Level level) {
            BlockPos blockPos = blockEntity.getBlockPos();
            BlockState blockState = blockEntity.getBlockState();
            Container blockEntityContainer = getBlockEntityContainer(blockEntity, blockState, level, blockPos);
            return blockEntityContainer != null
                ? new TransportItemsBetweenContainers.TransportItemTarget(blockPos, blockEntityContainer, blockEntity, blockState)
                : null;
        }

        public static TransportItemsBetweenContainers.@Nullable TransportItemTarget tryCreatePossibleTarget(BlockPos pos, Level level) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            return blockEntity == null ? null : tryCreatePossibleTarget(blockEntity, level);
        }

        private static @Nullable Container getBlockEntityContainer(BlockEntity blockEntity, BlockState state, Level level, BlockPos pos) {
            if (state.getBlock() instanceof ChestBlock chestBlock) {
                return ChestBlock.getContainer(chestBlock, state, level, pos, false);
            } else {
                return blockEntity instanceof Container container ? container : null;
            }
        }
    }
}
