package net.minecraft.world.entity.ai.goal;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class FleeSunGoal extends Goal {
    protected final PathfinderMob mob;
    private double wantedX;
    private double wantedY;
    private double wantedZ;
    private final double speedModifier;
    private final Level level;

    public FleeSunGoal(PathfinderMob mob, double speedModifier) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.level = mob.level();
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return this.mob.getTarget() == null
            && this.level.isBrightOutside()
            && this.mob.isOnFire()
            && this.level.canSeeSky(this.mob.blockPosition())
            && this.mob.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
            && this.setWantedPos();
    }

    protected boolean setWantedPos() {
        Vec3 hidePos = this.getHidePos();
        if (hidePos == null) {
            return false;
        } else {
            this.wantedX = hidePos.x;
            this.wantedY = hidePos.y;
            this.wantedZ = hidePos.z;
            return true;
        }
    }

    @Override
    public boolean canContinueToUse() {
        return !this.mob.getNavigation().isDone();
    }

    @Override
    public void start() {
        this.mob.getNavigation().moveTo(this.wantedX, this.wantedY, this.wantedZ, this.speedModifier);
    }

    protected @Nullable Vec3 getHidePos() {
        RandomSource random = this.mob.getRandom();
        BlockPos blockPos = this.mob.blockPosition();

        for (int i = 0; i < 10; i++) {
            BlockPos blockPos1 = blockPos.offset(random.nextInt(20) - 10, random.nextInt(6) - 3, random.nextInt(20) - 10);
            if (!this.level.canSeeSky(blockPos1) && this.mob.getWalkTargetValue(blockPos1) < 0.0F) {
                return Vec3.atBottomCenterOf(blockPos1);
            }
        }

        return null;
    }
}
