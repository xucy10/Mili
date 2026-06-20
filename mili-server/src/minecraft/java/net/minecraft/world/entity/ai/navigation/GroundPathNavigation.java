package net.minecraft.world.entity.ai.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;

public class GroundPathNavigation extends PathNavigation {
    private boolean avoidSun;
    private boolean canPathToTargetsBelowSurface;

    public GroundPathNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        this.nodeEvaluator = new WalkNodeEvaluator();
        return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
    }

    @Override
    protected boolean canUpdatePath() {
        return this.mob.onGround() || this.mob.isInLiquid() || this.mob.isPassenger();
    }

    @Override
    protected Vec3 getTempMobPos() {
        return new Vec3(this.mob.getX(), this.getSurfaceY(), this.mob.getZ());
    }

    @Override
    public Path createPath(BlockPos pos, @javax.annotation.Nullable Entity entity, int reachRange) { // Paper - EntityPathfindEvent
        // Folia start - region threading
        if (!ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor((net.minecraft.server.level.ServerLevel)this.level, pos)) {
            return null;
        }
        // Folia end - region threading
        LevelChunk chunkNow = this.level.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
        if (chunkNow == null) {
            return null;
        } else {
            if (!this.canPathToTargetsBelowSurface) {
                pos = this.findSurfacePosition(chunkNow, pos, reachRange);
            }

            return super.createPath(pos, entity, reachRange); // Paper - EntityPathfindEvent
        }
    }

    final BlockPos findSurfacePosition(LevelChunk chunk, BlockPos pos, int reachRange) {
        if (chunk.getBlockState(pos).isAir()) {
            BlockPos.MutableBlockPos mutableBlockPos = pos.mutable().move(Direction.DOWN);

            while (mutableBlockPos.getY() >= this.level.getMinY() && chunk.getBlockState(mutableBlockPos).isAir()) {
                mutableBlockPos.move(Direction.DOWN);
            }

            if (mutableBlockPos.getY() >= this.level.getMinY()) {
                return mutableBlockPos.above();
            }

            mutableBlockPos.setY(pos.getY() + 1);

            while (mutableBlockPos.getY() <= this.level.getMaxY() && chunk.getBlockState(mutableBlockPos).isAir()) {
                mutableBlockPos.move(Direction.UP);
            }

            pos = mutableBlockPos;
        }

        if (!chunk.getBlockState(pos).isSolid()) {
            return pos;
        } else {
            BlockPos.MutableBlockPos mutableBlockPos = pos.mutable().move(Direction.UP);

            while (mutableBlockPos.getY() <= this.level.getMaxY() && chunk.getBlockState(mutableBlockPos).isSolid()) {
                mutableBlockPos.move(Direction.UP);
            }

            return mutableBlockPos.immutable();
        }
    }

    @Override
    public Path createPath(Entity entity, int reachRange) {
        return this.createPath(entity.blockPosition(), entity, reachRange); // Paper - EntityPathfindEvent
    }

    private int getSurfaceY() {
        if (this.mob.isInWater() && this.canFloat()) {
            int blockY = this.mob.getBlockY();
            BlockState blockState = this.level.getBlockState(BlockPos.containing(this.mob.getX(), blockY, this.mob.getZ()));
            int i = 0;

            while (blockState.is(Blocks.WATER)) {
                blockState = this.level.getBlockState(BlockPos.containing(this.mob.getX(), ++blockY, this.mob.getZ()));
                if (++i > 16) {
                    return this.mob.getBlockY();
                }
            }

            return blockY;
        } else {
            return Mth.floor(this.mob.getY() + 0.5);
        }
    }

    @Override
    protected void trimPath() {
        super.trimPath();
        if (this.avoidSun) {
            if (this.level.canSeeSky(BlockPos.containing(this.mob.getX(), this.mob.getY() + 0.5, this.mob.getZ()))) {
                return;
            }

            for (int i = 0; i < this.path.getNodeCount(); i++) {
                Node node = this.path.getNode(i);
                if (this.level.canSeeSky(new BlockPos(node.x, node.y, node.z))) {
                    this.path.truncateNodes(i);
                    return;
                }
            }
        }
    }

    @Override
    public boolean canNavigateGround() {
        return true;
    }

    protected boolean hasValidPathType(PathType pathType) {
        return pathType != PathType.WATER && pathType != PathType.LAVA && pathType != PathType.OPEN;
    }

    public void setAvoidSun(boolean avoidSun) {
        this.avoidSun = avoidSun;
    }

    public void setCanWalkOverFences(boolean canWalkOverFences) {
        this.nodeEvaluator.setCanWalkOverFences(canWalkOverFences);
    }

    public void setCanPathToTargetsBelowSurface(boolean canPathToTargetsBelowSurface) {
        this.canPathToTargetsBelowSurface = canPathToTargetsBelowSurface;
    }
}
