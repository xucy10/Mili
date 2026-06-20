package net.minecraft.world.entity.boss.enderdragon.phases;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.EndPodiumFeature;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class DragonHoldingPatternPhase extends AbstractDragonPhaseInstance {
    private static final TargetingConditions NEW_TARGET_TARGETING = TargetingConditions.forCombat().ignoreLineOfSight();
    private @Nullable Path currentPath;
    private @Nullable Vec3 targetLocation;
    private boolean clockwise;

    public DragonHoldingPatternPhase(EnderDragon dragon) {
        super(dragon);
    }

    @Override
    public EnderDragonPhase<DragonHoldingPatternPhase> getPhase() {
        return EnderDragonPhase.HOLDING_PATTERN;
    }

    @Override
    public void doServerTick(ServerLevel level) {
        double d = this.targetLocation == null ? 0.0 : this.targetLocation.distanceToSqr(this.dragon.getX(), this.dragon.getY(), this.dragon.getZ());
        if (d < 100.0 || d > 22500.0 || this.dragon.horizontalCollision || this.dragon.verticalCollision) {
            this.findNewTarget(level);
        }
    }

    @Override
    public void begin() {
        this.currentPath = null;
        this.targetLocation = null;
    }

    @Override
    public @Nullable Vec3 getFlyTargetLocation() {
        return this.targetLocation;
    }

    private void findNewTarget(ServerLevel level) {
        if (this.currentPath != null && this.currentPath.isDone()) {
            BlockPos heightmapPos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, this.dragon.getPodium()); // Paper - Allow changing the EnderDragon podium
            int i = this.dragon.getDragonFight() == null ? 0 : this.dragon.getDragonFight().getCrystalsAlive();
            if (this.dragon.getRandom().nextInt(i + 3) == 0) {
                this.dragon.getPhaseManager().setPhase(EnderDragonPhase.LANDING_APPROACH);
                return;
            }

            Player nearestPlayer = level.getNearestPlayer(NEW_TARGET_TARGETING, this.dragon, heightmapPos.getX(), heightmapPos.getY(), heightmapPos.getZ());
            double d;
            if (nearestPlayer != null) {
                d = heightmapPos.distToCenterSqr(nearestPlayer.position()) / 512.0;
            } else {
                d = 64.0;
            }

            if (nearestPlayer != null && (this.dragon.getRandom().nextInt((int)(d + 2.0)) == 0 || this.dragon.getRandom().nextInt(i + 2) == 0)) {
                this.strafePlayer(nearestPlayer);
                return;
            }
        }

        if (this.currentPath == null || this.currentPath.isDone()) {
            int i1 = this.dragon.findClosestNode();
            int ix = i1;
            if (this.dragon.getRandom().nextInt(8) == 0) {
                this.clockwise = !this.clockwise;
                ix = i1 + 6;
            }

            if (this.clockwise) {
                ix++;
            } else {
                ix--;
            }

            if (this.dragon.getDragonFight() != null && this.dragon.getDragonFight().getCrystalsAlive() >= 0) {
                ix %= 12;
                if (ix < 0) {
                    ix += 12;
                }
            } else {
                ix -= 12;
                ix &= 7;
                ix += 12;
            }

            this.currentPath = this.dragon.findPath(i1, ix, null);
            if (this.currentPath != null) {
                this.currentPath.advance();
            }
        }

        this.navigateToNextPathNode();
    }

    private void strafePlayer(Player player) {
        this.dragon.getPhaseManager().setPhase(EnderDragonPhase.STRAFE_PLAYER);
        this.dragon.getPhaseManager().getPhase(EnderDragonPhase.STRAFE_PLAYER).setTarget(player);
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

    @Override
    public void onCrystalDestroyed(EndCrystal crystal, BlockPos pos, DamageSource damageSource, @Nullable Player player) {
        if (player != null && this.dragon.canAttack(player)) {
            this.strafePlayer(player);
        }
    }
}
