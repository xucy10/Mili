package net.minecraft.world.entity.ai.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.FlyNodeEvaluator;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.phys.Vec3;

public class FlyingPathNavigation extends PathNavigation {
    public FlyingPathNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        this.nodeEvaluator = new FlyNodeEvaluator();
        return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
    }

    @Override
    protected boolean canMoveDirectly(Vec3 currentPos, Vec3 nextPos) {
        return isClearForMovementBetween(this.mob, currentPos, nextPos, true);
    }

    @Override
    protected boolean canUpdatePath() {
        return this.canFloat() && this.mob.isInLiquid() || !this.mob.isPassenger();
    }

    @Override
    protected Vec3 getTempMobPos() {
        return this.mob.position();
    }

    @Override
    public Path createPath(Entity entity, int reachRange) {
        return this.createPath(entity.blockPosition(), entity, reachRange); // Paper - EntityPathfindEvent
    }

    @Override
    public void tick() {
        this.tick++;
        if (this.hasDelayedRecomputation) {
            this.recomputePath();
        }

        if (!this.isDone()) {
            if (this.canUpdatePath()) {
                this.followThePath();
            } else if (this.path != null && !this.path.isDone()) {
                Vec3 nextEntityPos = this.path.getNextEntityPos(this.mob);
                if (this.mob.getBlockX() == Mth.floor(nextEntityPos.x)
                    && this.mob.getBlockY() == Mth.floor(nextEntityPos.y)
                    && this.mob.getBlockZ() == Mth.floor(nextEntityPos.z)) {
                    this.path.advance();
                }
            }

            if (!this.isDone()) {
                Vec3 nextEntityPos = this.path.getNextEntityPos(this.mob);
                // Luminol - Recompute path when path finding out of current tick region
                if (me.earthme.luminol.config.modules.fixes.PathfindingFixesConfig.breakDownPathfindingWhenOutOfRegion) {
                    // we assume that:
                    // 1. The code above doesn't touch the 'main thread context' with the position from 'this.path'
                    // 2. The pathfinder could correctly recompute or discard the incorrect target position and this situation is happening rarely
                    if (!ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(this.mob.level(), nextEntityPos)) {
                        this.hasDelayedRecomputation = true;
                        return;
                    }
                }
                // Luminol end
                this.mob.getMoveControl().setWantedPosition(nextEntityPos.x, nextEntityPos.y, nextEntityPos.z, this.speedModifier);
            }
        }
    }

    @Override
    public boolean isStableDestination(BlockPos pos) {
        return this.level.getBlockState(pos).entityCanStandOn(this.level, pos, this.mob);
    }

    @Override
    public boolean canNavigateGround() {
        return false;
    }
}
