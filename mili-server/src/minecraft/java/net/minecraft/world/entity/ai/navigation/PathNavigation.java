package net.minecraft.world.entity.ai.navigation;

import com.google.common.collect.ImmutableSet;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.util.debug.ServerDebugSubscribers;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public abstract class PathNavigation {
    private static final int MAX_TIME_RECOMPUTE = 20;
    private static final int STUCK_CHECK_INTERVAL = 100;
    private static final float STUCK_THRESHOLD_DISTANCE_FACTOR = 0.25F;
    protected final Mob mob;
    protected final Level level;
    protected @Nullable Path path;
    protected double speedModifier;
    protected int tick;
    protected int lastStuckCheck;
    protected Vec3 lastStuckCheckPos = Vec3.ZERO;
    protected Vec3i timeoutCachedNode = Vec3i.ZERO;
    protected long timeoutTimer;
    protected long lastTimeoutCheck;
    protected double timeoutLimit;
    protected float maxDistanceToWaypoint = 0.5F;
    protected boolean hasDelayedRecomputation;
    protected long timeLastRecompute;
    protected NodeEvaluator nodeEvaluator;
    private @Nullable BlockPos targetPos;
    private int reachRange;
    private float maxVisitedNodesMultiplier = 1.0F;
    public final PathFinder pathFinder;
    private boolean isStuck;
    private float requiredPathLength = 16.0F;

    public PathNavigation(Mob mob, Level level) {
        this.mob = mob;
        this.level = level;
        this.pathFinder = this.createPathFinder(Mth.floor(mob.getAttributeBaseValue(Attributes.FOLLOW_RANGE) * 16.0));
        if (level instanceof ServerLevel serverLevel) {
            ServerDebugSubscribers serverDebugSubscribers = serverLevel.getServer().debugSubscribers();
            this.pathFinder.setCaptureDebug(() -> serverDebugSubscribers.hasAnySubscriberFor(DebugSubscriptions.ENTITY_PATHS));
        }
    }

    public void updatePathfinderMaxVisitedNodes() {
        int floor = Mth.floor(this.getMaxPathLength() * 16.0F);
        this.pathFinder.setMaxVisitedNodes(floor);
    }

    public void setRequiredPathLength(float requiredPathLength) {
        this.requiredPathLength = requiredPathLength;
        this.updatePathfinderMaxVisitedNodes();
    }

    private float getMaxPathLength() {
        return Math.max((float)this.mob.getAttributeValue(Attributes.FOLLOW_RANGE), this.requiredPathLength);
    }

    public void resetMaxVisitedNodesMultiplier() {
        this.maxVisitedNodesMultiplier = 1.0F;
    }

    public void setMaxVisitedNodesMultiplier(float multiplier) {
        this.maxVisitedNodesMultiplier = multiplier;
    }

    public @Nullable BlockPos getTargetPos() {
        return this.targetPos;
    }

    protected abstract PathFinder createPathFinder(int maxVisitedNodes);

    public void setSpeedModifier(double speedModifier) {
        this.speedModifier = speedModifier;
    }

    public void recomputePath() {
        if (this.tick - this.timeLastRecompute > 20L) { // Folia - region threading
            if (this.targetPos != null) {
                // Luminol - Recompute path when path finding out of current tick region
                if (me.earthme.luminol.config.modules.fixes.PathfindingFixesConfig.breakDownPathfindingWhenOutOfRegion) {
                    // The target seems to be pointed to a position out of current region, so reset if it is
                    // also it will interrupt the pathfinding
                    if (!ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(this.mob.level(), this.targetPos)) {
                        // also we need to reset the path field so all of it will be stopped
                        this.path = null;

                        this.targetPos = null;
                        return;
                    }
                }
                // Luminol end
                this.path = null;
                this.path = this.createPath(this.targetPos, this.reachRange);
                this.timeLastRecompute = this.tick; // Folia - region threading
                this.hasDelayedRecomputation = false;
            }
        } else {
            this.hasDelayedRecomputation = true;
        }
    }

    public final @Nullable Path createPath(double x, double y, double z, int reachRange) {
        return this.createPath(BlockPos.containing(x, y, z), reachRange);
    }

    public @Nullable Path createPath(Stream<BlockPos> targets, int reachRange) {
        return this.createPath(targets.collect(Collectors.toSet()), 8, false, reachRange);
    }

    public @Nullable Path createPath(Set<BlockPos> targets, int reachRange) {
        return this.createPath(targets, 8, false, reachRange);
    }

    public @Nullable Path createPath(BlockPos pos, int reachRange) {
        // Paper start - EntityPathfindEvent
        return this.createPath(pos, null, reachRange);
    }
    public @Nullable Path createPath(BlockPos target, @Nullable Entity entity, int reachRange) {
        return this.createPath(ImmutableSet.of(target), entity, 8, false, reachRange);
        // Paper end - EntityPathfindEvent
    }

    public @Nullable Path createPath(BlockPos pos, int reachRange, int followRange) {
        return this.createPath(ImmutableSet.of(pos), 8, false, reachRange, followRange);
    }

    public @Nullable Path createPath(Entity entity, int reachRange) {
        return this.createPath(ImmutableSet.of(entity.blockPosition()), entity, 16, true, reachRange); // Paper - EntityPathfindEvent
    }

    protected @Nullable Path createPath(Set<BlockPos> targets, int regionOffset, boolean offsetUpward, int reachRange) {
        return this.createPath(targets, regionOffset, offsetUpward, reachRange, this.getMaxPathLength());
    }

    protected @Nullable Path createPath(Set<BlockPos> targets, int regionOffset, boolean offsetUpward, int reachRange, float followRange) {
        // Paper start - EntityPathfindEvent
        return this.createPath(targets, null, regionOffset, offsetUpward, reachRange, followRange);
    }

    protected @Nullable Path createPath(Set<BlockPos> targets, @Nullable Entity target, int regionOffset, boolean offsetUpward, int reachRange) {
        return this.createPath(targets, target, regionOffset, offsetUpward, reachRange, this.getMaxPathLength());
    }

    protected @Nullable Path createPath(Set<BlockPos> targets, @Nullable Entity target, int regionOffset, boolean offsetUpward, int reachRange, float followRange) {
        // Paper end - EntityPathfindEvent
        if (targets.isEmpty()) {
            return null;
        } else if (this.mob.getY() < this.level.getMinY()) {
            return null;
        } else if (!this.canUpdatePath()) {
            return null;
        } else if (this.path != null && !this.path.isDone() && targets.contains(this.targetPos)) {
            return this.path;
        } else {
            // Paper start - EntityPathfindEvent
            boolean copiedSet = false;
            for (BlockPos possibleTarget : targets) {
                if (!this.mob.level().getWorldBorder().isWithinBounds(possibleTarget) || !new com.destroystokyo.paper.event.entity.EntityPathfindEvent(this.mob.getBukkitEntity(), // Paper - don't path out of world border
                    org.bukkit.craftbukkit.util.CraftLocation.toBukkit(possibleTarget, this.mob.level()), target == null ? null : target.getBukkitEntity()).callEvent()) {
                    if (!copiedSet) {
                        copiedSet = true;
                        targets = new java.util.HashSet<>(targets);
                    }
                    // note: since we copy the set this remove call is safe, since we're iterating over the old copy
                    targets.remove(possibleTarget);
                    if (targets.isEmpty()) {
                        return null;
                    }
                }
            }
            // Paper end - EntityPathfindEvent
            // Luminol - Do not path find for targets out of current region
            if (me.earthme.luminol.config.modules.fixes.PathfindingFixesConfig.doNotPathfindToNotOwnedTargets) {
                // filter the targets not owned by current region
                targets = new HashSet<>(targets); // well no idea about how to determine if this should be copied to a modifiable one
                targets.removeIf(pos -> !ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(this.mob.level(), pos));

                // return if no available (observe the logic in the first if block)
                if (targets.isEmpty()) {
                    return null;
                }
            }
            // Luminol end
            ProfilerFiller profilerFiller = Profiler.get();
            profilerFiller.push("pathfind");
            BlockPos blockPos = offsetUpward ? this.mob.blockPosition().above() : this.mob.blockPosition();
            int i = (int)(followRange + regionOffset);
            PathNavigationRegion pathNavigationRegion = new PathNavigationRegion(this.level, blockPos.offset(-i, -i, -i), blockPos.offset(i, i, i));
            Path path = this.pathFinder.findPath(pathNavigationRegion, this.mob, targets, followRange, reachRange, this.maxVisitedNodesMultiplier);
            profilerFiller.pop();
            if (path != null && path.getTarget() != null) {
                this.targetPos = path.getTarget();
                this.reachRange = reachRange;
                this.resetStuckTimeout();
            }

            return path;
        }
    }

    // Paper start - Perf: Optimise pathfinding
    private int lastFailure = 0;
    private int pathfindFailures = 0;
    // Paper end - Perf: Optimise pathfinding

    public boolean moveTo(double x, double y, double z, double speedModifier) {
        return this.moveTo(this.createPath(x, y, z, 1), speedModifier);
    }

    public boolean moveTo(double x, double y, double z, int reachRange, double speedModifier) {
        return this.moveTo(this.createPath(x, y, z, reachRange), speedModifier);
    }

    public boolean moveTo(Entity entity, double speedModifier) {
        // Paper start - Perf: Optimise pathfinding
        if (this.pathfindFailures > 10 && this.path == null && this.tick < this.lastFailure + 40) { // Folia - region threading
            return false;
        }
        // Paper end - Perf: Optimise pathfinding
        Path path = this.createPath(entity, 1);
        // Paper start - Perf: Optimise pathfinding
        if (path != null && this.moveTo(path, speedModifier)) {
            this.lastFailure = 0;
            this.pathfindFailures = 0;
            return true;
        } else {
            this.pathfindFailures++;
            this.lastFailure = this.tick; // Folia - region threading
            return false;
        }
        // Paper end - Perf: Optimise pathfinding
    }

    public boolean moveTo(@Nullable Path path, double speedModifier) {
        if (path == null) {
            this.path = null;
            return false;
        } else {
            if (!path.sameAs(this.path)) {
                this.path = path;
            }

            if (this.isDone()) {
                return false;
            } else {
                this.trimPath();
                if (this.path.getNodeCount() <= 0) {
                    return false;
                } else {
                    this.speedModifier = speedModifier;
                    Vec3 tempMobPos = this.getTempMobPos();
                    this.lastStuckCheck = this.tick;
                    this.lastStuckCheckPos = tempMobPos;
                    return true;
                }
            }
        }
    }

    public @Nullable Path getPath() {
        return this.path;
    }

    public void tick() {
        this.tick++;
        if (this.hasDelayedRecomputation) {
            this.recomputePath();
        }

        if (!this.isDone()) {
            if (this.canUpdatePath()) {
                this.followThePath();
            } else if (this.path != null && !this.path.isDone()) {
                Vec3 tempMobPos = this.getTempMobPos();
                Vec3 nextEntityPos = this.path.getNextEntityPos(this.mob);
                if (tempMobPos.y > nextEntityPos.y
                    && !this.mob.onGround()
                    && Mth.floor(tempMobPos.x) == Mth.floor(nextEntityPos.x)
                    && Mth.floor(tempMobPos.z) == Mth.floor(nextEntityPos.z)) {
                    this.path.advance();
                }
            }

            if (!this.isDone()) {
                Vec3 tempMobPos = this.path.getNextEntityPos(this.mob);
                // Luminol - Recompute path when path finding out of current tick region
                if (me.earthme.luminol.config.modules.fixes.PathfindingFixesConfig.breakDownPathfindingWhenOutOfRegion) {
                    // we assume that:
                    // 1. The code above doesn't touch the 'main thread context' with the position from 'this.path'
                    // 2. The pathfinder could correctly recompute or discard the incorrect target position and this situation is happening rarely
                    if (!ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(this.mob.level(), tempMobPos)) {
                        this.hasDelayedRecomputation = true;
                        return;
                    }
                }
                // Luminol end
                this.mob.getMoveControl().setWantedPosition(tempMobPos.x, this.getGroundY(tempMobPos), tempMobPos.z, this.speedModifier);
            }
        }
    }

    protected double getGroundY(Vec3 pos) {
        BlockPos blockPos = BlockPos.containing(pos);
        return this.level.getBlockState(blockPos.below()).isAir() ? pos.y : WalkNodeEvaluator.getFloorLevel(this.level, blockPos);
    }

    protected void followThePath() {
        Vec3 tempMobPos = this.getTempMobPos();
        this.maxDistanceToWaypoint = this.mob.getBbWidth() > 0.75F ? this.mob.getBbWidth() / 2.0F : 0.75F - this.mob.getBbWidth() / 2.0F;
        Vec3i nextNodePos = this.path.getNextNodePos();
        double abs = Math.abs(this.mob.getX() - (nextNodePos.getX() + 0.5));
        double abs1 = Math.abs(this.mob.getY() - nextNodePos.getY());
        double abs2 = Math.abs(this.mob.getZ() - (nextNodePos.getZ() + 0.5));
        boolean flag = abs < this.maxDistanceToWaypoint && abs2 < this.maxDistanceToWaypoint && abs1 < 1.0;
        if (flag || this.canCutCorner(this.path.getNextNode().type) && this.shouldTargetNextNodeInDirection(tempMobPos)) {
            this.path.advance();
        }

        this.doStuckDetection(tempMobPos);
    }

    private boolean shouldTargetNextNodeInDirection(Vec3 pos) {
        if (this.path.getNextNodeIndex() + 1 >= this.path.getNodeCount()) {
            return false;
        } else {
            Vec3 vec3 = Vec3.atBottomCenterOf(this.path.getNextNodePos());
            if (!pos.closerThan(vec3, 2.0)) {
                return false;
            } else if (this.canMoveDirectly(pos, this.path.getNextEntityPos(this.mob))) {
                return true;
            } else {
                Vec3 vec31 = Vec3.atBottomCenterOf(this.path.getNodePos(this.path.getNextNodeIndex() + 1));
                Vec3 vec32 = vec3.subtract(pos);
                Vec3 vec33 = vec31.subtract(pos);
                double d = vec32.lengthSqr();
                double d1 = vec33.lengthSqr();
                boolean flag = d1 < d;
                boolean flag1 = d < 0.5;
                if (!flag && !flag1) {
                    return false;
                } else {
                    Vec3 vec34 = vec32.normalize();
                    Vec3 vec35 = vec33.normalize();
                    return vec35.dot(vec34) < 0.0;
                }
            }
        }
    }

    protected void doStuckDetection(Vec3 pos) {
        if (this.tick - this.lastStuckCheck > 100) {
            float f = this.mob.getSpeed() >= 1.0F ? this.mob.getSpeed() : this.mob.getSpeed() * this.mob.getSpeed();
            float f1 = f * 100.0F * 0.25F;
            if (pos.distanceToSqr(this.lastStuckCheckPos) < f1 * f1) {
                this.isStuck = true;
                this.stop();
            } else {
                this.isStuck = false;
            }

            this.lastStuckCheck = this.tick;
            this.lastStuckCheckPos = pos;
        }

        if (this.path != null && !this.path.isDone()) {
            Vec3i nextNodePos = this.path.getNextNodePos();
            long gameTime = this.level.getGameTime();
            if (nextNodePos.equals(this.timeoutCachedNode)) {
                this.timeoutTimer = this.timeoutTimer + (gameTime - this.lastTimeoutCheck);
            } else {
                this.timeoutCachedNode = nextNodePos;
                double d = pos.distanceTo(Vec3.atBottomCenterOf(this.timeoutCachedNode));
                this.timeoutLimit = this.mob.getSpeed() > 0.0F ? d / this.mob.getSpeed() * 20.0 : 0.0;
            }

            if (this.timeoutLimit > 0.0 && this.timeoutTimer > this.timeoutLimit * 3.0) {
                this.timeoutPath();
            }

            this.lastTimeoutCheck = gameTime;
        }
    }

    private void timeoutPath() {
        this.resetStuckTimeout();
        this.stop();
    }

    private void resetStuckTimeout() {
        this.timeoutCachedNode = Vec3i.ZERO;
        this.timeoutTimer = 0L;
        this.timeoutLimit = 0.0;
        this.isStuck = false;
    }

    public boolean isDone() {
        return this.path == null || this.path.isDone();
    }

    public boolean isInProgress() {
        return !this.isDone();
    }

    public void stop() {
        this.path = null;
    }

    protected abstract Vec3 getTempMobPos();

    protected abstract boolean canUpdatePath();

    protected void trimPath() {
        if (this.path != null) {
            for (int i = 0; i < this.path.getNodeCount(); i++) {
                Node node = this.path.getNode(i);
                Node node1 = i + 1 < this.path.getNodeCount() ? this.path.getNode(i + 1) : null;
                BlockState blockState = this.level.getBlockState(new BlockPos(node.x, node.y, node.z));
                if (blockState.is(BlockTags.CAULDRONS)) {
                    this.path.replaceNode(i, node.cloneAndMove(node.x, node.y + 1, node.z));
                    if (node1 != null && node.y >= node1.y) {
                        this.path.replaceNode(i + 1, node.cloneAndMove(node1.x, node.y + 1, node1.z));
                    }
                }
            }
        }
    }

    protected boolean canMoveDirectly(Vec3 currentPos, Vec3 nextPos) {
        return false;
    }

    public boolean canCutCorner(PathType pathType) {
        return pathType != PathType.DANGER_FIRE && pathType != PathType.DANGER_OTHER && pathType != PathType.WALKABLE_DOOR;
    }

    protected static boolean isClearForMovementBetween(Mob mob, Vec3 pos1, Vec3 pos2, boolean avoidFluid) {
        Vec3 vec3 = new Vec3(pos2.x, pos2.y + mob.getBbHeight() * 0.5, pos2.z);
        return mob.level()
                .clip(new ClipContext(pos1, vec3, ClipContext.Block.COLLIDER, avoidFluid ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE, mob))
                .getType()
            == HitResult.Type.MISS;
    }

    public boolean isStableDestination(BlockPos pos) {
        BlockPos blockPos = pos.below();
        return this.level.getBlockState(blockPos).isSolidRender();
    }

    public NodeEvaluator getNodeEvaluator() {
        return this.nodeEvaluator;
    }

    public void setCanFloat(boolean canFloat) {
        this.nodeEvaluator.setCanFloat(canFloat);
    }

    public boolean canFloat() {
        return this.nodeEvaluator.canFloat();
    }

    public boolean shouldRecomputePath(BlockPos pos) {
        if (this.hasDelayedRecomputation) {
            return false;
        } else if (this.path != null && !this.path.isDone() && this.path.getNodeCount() != 0) {
            Node endNode = this.path.getEndNode();
            Vec3 vec3 = new Vec3((endNode.x + this.mob.getX()) / 2.0, (endNode.y + this.mob.getY()) / 2.0, (endNode.z + this.mob.getZ()) / 2.0);
            return pos.closerToCenterThan(vec3, this.path.getNodeCount() - this.path.getNextNodeIndex());
        } else {
            return false;
        }
    }

    public float getMaxDistanceToWaypoint() {
        return this.maxDistanceToWaypoint;
    }

    public boolean isStuck() {
        return this.isStuck;
    }

    public abstract boolean canNavigateGround();

    public void setCanOpenDoors(boolean canOpenDoors) {
        this.nodeEvaluator.setCanOpenDoors(canOpenDoors);
    }
}
