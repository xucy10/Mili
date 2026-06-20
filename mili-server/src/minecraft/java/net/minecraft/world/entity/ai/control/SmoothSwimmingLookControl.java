package net.minecraft.world.entity.ai.control;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;

public class SmoothSwimmingLookControl extends LookControl {
    private final int maxYRotFromCenter;
    private static final int HEAD_TILT_X = 10;
    private static final int HEAD_TILT_Y = 20;

    public SmoothSwimmingLookControl(Mob mob, int maxYRotFromCenter) {
        super(mob);
        this.maxYRotFromCenter = maxYRotFromCenter;
    }

    @Override
    public void tick() {
        if (this.lookAtCooldown > 0) {
            this.lookAtCooldown--;
            this.getYRotD().ifPresent(rotationWanted -> this.mob.yHeadRot = this.rotateTowards(this.mob.yHeadRot, rotationWanted + 20.0F, this.yMaxRotSpeed));
            this.getXRotD().ifPresent(_float -> this.mob.setXRot(this.rotateTowards(this.mob.getXRot(), _float + 10.0F, this.xMaxRotAngle)));
        } else {
            if (this.mob.getNavigation().isDone()) {
                this.mob.setXRot(this.rotateTowards(this.mob.getXRot(), 0.0F, 5.0F));
            }

            this.mob.yHeadRot = this.rotateTowards(this.mob.yHeadRot, this.mob.yBodyRot, this.yMaxRotSpeed);
        }

        float f = Mth.wrapDegrees(this.mob.yHeadRot - this.mob.yBodyRot);
        if (f < -this.maxYRotFromCenter) {
            this.mob.yBodyRot -= 4.0F;
        } else if (f > this.maxYRotFromCenter) {
            this.mob.yBodyRot += 4.0F;
        }
    }
}
