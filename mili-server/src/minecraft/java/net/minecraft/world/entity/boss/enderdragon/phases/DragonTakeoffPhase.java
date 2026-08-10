package net.minecraft.world.entity.boss.enderdragon.phases;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.EndPodiumFeature;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class DragonTakeoffPhase extends AbstractDragonPhaseInstance {
    private boolean firstTick;
    private @Nullable Path currentPath;
    private @Nullable Vec3 targetLocation;

    public DragonTakeoffPhase(EnderDragon dragon) {
        super(dragon);
    }

    @Override
    public void doServerTick(ServerLevel level) {
        if (!this.firstTick && this.currentPath != null) {
            BlockPos heightmapPos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, this.dragon.getPodium()); // Paper - Allow changing the EnderDragon podium
            if (!heightmapPos.closerToCenterThan(this.dragon.position(), 10.0)) {
                this.dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
            }
        } else {
            this.firstTick = false;
            this.findNewTarget();
        }
    }

    @Override
    public void begin() {
        this.firstTick = true;
        this.currentPath = null;
        this.targetLocation = null;
    }

    private void findNewTarget() {
        int i = this.dragon.findClosestNode();
        Vec3 headLookVector = this.dragon.getHeadLookVector(1.0F);
        int i1 = this.dragon.findClosestNode(-headLookVector.x * 40.0, 105.0, -headLookVector.z * 40.0);
        if (this.dragon.getDragonFight() != null && this.dragon.getDragonFight().getCrystalsAlive() > 0) {
            i1 %= 12;
            if (i1 < 0) {
                i1 += 12;
            }
        } else {
            i1 -= 12;
            i1 &= 7;
            i1 += 12;
        }

        this.currentPath = this.dragon.findPath(i, i1, null);
        this.navigateToNextPathNode();
    }

    private void navigateToNextPathNode() {
        if (this.currentPath != null) {
            this.currentPath.advance();
            if (!this.currentPath.isDone()) {
                Vec3i nextNodePos = this.currentPath.getNextNodePos();
                this.currentPath.advance();

                double d;
                do {
                    d = nextNodePos.getY() + this.dragon.getRandom().nextFloat() * 20.0F;
                } while (d < nextNodePos.getY());

                this.targetLocation = new Vec3(nextNodePos.getX(), d, nextNodePos.getZ());
            }
        }
    }

    @Override
    public @Nullable Vec3 getFlyTargetLocation() {
        return this.targetLocation;
    }

    @Override
    public EnderDragonPhase<DragonTakeoffPhase> getPhase() {
        return EnderDragonPhase.TAKEOFF;
    }
}
