package net.minecraft.world.entity.boss.enderdragon.phases;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.EndPodiumFeature;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class DragonLandingApproachPhase extends AbstractDragonPhaseInstance {
    private static final TargetingConditions NEAR_EGG_TARGETING = TargetingConditions.forCombat().ignoreLineOfSight();
    private @Nullable Path currentPath;
    private @Nullable Vec3 targetLocation;

    public DragonLandingApproachPhase(EnderDragon dragon) {
        super(dragon);
    }

    @Override
    public EnderDragonPhase<DragonLandingApproachPhase> getPhase() {
        return EnderDragonPhase.LANDING_APPROACH;
    }

    @Override
    public void begin() {
        this.currentPath = null;
        this.targetLocation = null;
    }

    @Override
    public void doServerTick(ServerLevel level) {
        double d = this.targetLocation == null ? 0.0 : this.targetLocation.distanceToSqr(this.dragon.getX(), this.dragon.getY(), this.dragon.getZ());
        if (d < 100.0 || d > 22500.0 || this.dragon.horizontalCollision || this.dragon.verticalCollision) {
            this.findNewTarget(level);
        }
    }

    @Override
    public @Nullable Vec3 getFlyTargetLocation() {
        return this.targetLocation;
    }

    private void findNewTarget(ServerLevel level) {
        if (this.currentPath == null || this.currentPath.isDone()) {
            int i = this.dragon.findClosestNode();
            BlockPos heightmapPos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, this.dragon.getPodium()); // Paper - Allow changing the EnderDragon podium
            Player nearestPlayer = level.getNearestPlayer(NEAR_EGG_TARGETING, this.dragon, heightmapPos.getX(), heightmapPos.getY(), heightmapPos.getZ());
            int i1;
            if (nearestPlayer != null) {
                Vec3 vec3 = new Vec3(nearestPlayer.getX(), 0.0, nearestPlayer.getZ()).normalize();
                i1 = this.dragon.findClosestNode(-vec3.x * 40.0, 105.0, -vec3.z * 40.0);
            } else {
                i1 = this.dragon.findClosestNode(40.0, heightmapPos.getY(), 0.0);
            }

            Node node = new Node(heightmapPos.getX(), heightmapPos.getY(), heightmapPos.getZ());
            this.currentPath = this.dragon.findPath(i, i1, node);
            if (this.currentPath != null) {
                this.currentPath.advance();
            }
        }

        this.navigateToNextPathNode();
        if (this.currentPath != null && this.currentPath.isDone()) {
            this.dragon.getPhaseManager().setPhase(EnderDragonPhase.LANDING);
        }
    }

    private void navigateToNextPathNode() {
        if (this.currentPath != null && !this.currentPath.isDone()) {
            Vec3i nextNodePos = this.currentPath.getNextNodePos();
            this.currentPath.advance();
            double d = nextNodePos.getX();
            double d1 = nextNodePos.getZ();

            double d2;
            do {
                d2 = nextNodePos.getY() + this.dragon.getRandom().nextFloat() * 20.0F;
            } while (d2 < nextNodePos.getY());

            this.targetLocation = new Vec3(d, d2, d1);
        }
    }
}
