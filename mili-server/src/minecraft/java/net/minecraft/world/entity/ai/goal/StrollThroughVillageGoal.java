package net.minecraft.world.entity.ai.goal;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class StrollThroughVillageGoal extends Goal {
    private static final int DISTANCE_THRESHOLD = 10;
    private final PathfinderMob mob;
    private final int interval;
    private @Nullable BlockPos wantedPos;

    public StrollThroughVillageGoal(PathfinderMob mob, int interval) {
        this.mob = mob;
        this.interval = reducedTickDelay(interval);
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.mob.hasControllingPassenger()) {
            return false;
        } else if (this.mob.level().isBrightOutside()) {
            return false;
        } else if (this.mob.getRandom().nextInt(this.interval) != 0) {
            return false;
        } else {
            ServerLevel serverLevel = (ServerLevel)this.mob.level();
            BlockPos blockPos = this.mob.blockPosition();
            if (!serverLevel.isCloseToVillage(blockPos, 6)) {
                return false;
            } else {
                Vec3 pos = LandRandomPos.getPos(this.mob, 15, 7, pos1 -> -serverLevel.sectionsToVillage(SectionPos.of(pos1)));
                this.wantedPos = pos == null ? null : BlockPos.containing(pos);
                return this.wantedPos != null;
            }
        }
    }

    @Override
    public boolean canContinueToUse() {
        return this.wantedPos != null && !this.mob.getNavigation().isDone() && this.mob.getNavigation().getTargetPos().equals(this.wantedPos);
    }

    @Override
    public void tick() {
        if (this.wantedPos != null) {
            PathNavigation navigation = this.mob.getNavigation();
            if (navigation.isDone() && !this.wantedPos.closerToCenterThan(this.mob.position(), 10.0)) {
                Vec3 vec3 = Vec3.atBottomCenterOf(this.wantedPos);
                Vec3 vec31 = this.mob.position();
                Vec3 vec32 = vec31.subtract(vec3);
                vec3 = vec32.scale(0.4).add(vec3);
                Vec3 vec33 = vec3.subtract(vec31).normalize().scale(10.0).add(vec31);
                BlockPos blockPos = BlockPos.containing(vec33);
                blockPos = this.mob.level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockPos);
                if (!navigation.moveTo(blockPos.getX(), blockPos.getY(), blockPos.getZ(), 1.0)) {
                    this.moveRandomly();
                }
            }
        }
    }

    private void moveRandomly() {
        RandomSource random = this.mob.getRandom();
        BlockPos heightmapPos = this.mob
            .level()
            .getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, this.mob.blockPosition().offset(-8 + random.nextInt(16), 0, -8 + random.nextInt(16)));
        this.mob.getNavigation().moveTo(heightmapPos.getX(), heightmapPos.getY(), heightmapPos.getZ(), 1.0);
    }
}
