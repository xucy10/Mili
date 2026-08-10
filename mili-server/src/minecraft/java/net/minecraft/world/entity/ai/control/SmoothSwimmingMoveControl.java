package net.minecraft.world.entity.ai.control;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;

public class SmoothSwimmingMoveControl extends MoveControl {
    private static final float FULL_SPEED_TURN_THRESHOLD = 10.0F;
    private static final float STOP_TURN_THRESHOLD = 60.0F;
    private final int maxTurnX;
    private final int maxTurnY;
    private final float inWaterSpeedModifier;
    private final float outsideWaterSpeedModifier;
    private final boolean applyGravity;

    public SmoothSwimmingMoveControl(Mob mob, int maxTurnX, int maxTurnY, float inWaterSpeedModifier, float outsideWaterSpeedModifier, boolean applyGravity) {
        super(mob);
        this.maxTurnX = maxTurnX;
        this.maxTurnY = maxTurnY;
        this.inWaterSpeedModifier = inWaterSpeedModifier;
        this.outsideWaterSpeedModifier = outsideWaterSpeedModifier;
        this.applyGravity = applyGravity;
    }

    @Override
    public void tick() {
        if (this.applyGravity && this.mob.isInWater()) {
            this.mob.setDeltaMovement(this.mob.getDeltaMovement().add(0.0, 0.005, 0.0));
        }

        if (this.operation == MoveControl.Operation.MOVE_TO && !this.mob.getNavigation().isDone()) {
            double d = this.wantedX - this.mob.getX();
            double d1 = this.wantedY - this.mob.getY();
            double d2 = this.wantedZ - this.mob.getZ();
            double d3 = d * d + d1 * d1 + d2 * d2;
            if (d3 < 2.5000003E-7F) {
                this.mob.setZza(0.0F);
            } else {
                float f = (float)(Mth.atan2(d2, d) * 180.0F / (float)Math.PI) - 90.0F;
                this.mob.setYRot(this.rotlerp(this.mob.getYRot(), f, this.maxTurnY));
                this.mob.yBodyRot = this.mob.getYRot();
                this.mob.yHeadRot = this.mob.getYRot();
                float f1 = (float)(this.speedModifier * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED));
                if (this.mob.isInWater()) {
                    this.mob.setSpeed(f1 * this.inWaterSpeedModifier);
                    double squareRoot = Math.sqrt(d * d + d2 * d2);
                    if (Math.abs(d1) > 1.0E-5F || Math.abs(squareRoot) > 1.0E-5F) {
                        float f2 = -((float)(Mth.atan2(d1, squareRoot) * 180.0F / (float)Math.PI));
                        f2 = Mth.clamp(Mth.wrapDegrees(f2), (float)(-this.maxTurnX), (float)this.maxTurnX);
                        this.mob.setXRot(this.rotateTowards(this.mob.getXRot(), f2, 5.0F));
                    }

                    float f2 = Mth.cos(this.mob.getXRot() * (float) (Math.PI / 180.0));
                    float sin = Mth.sin(this.mob.getXRot() * (float) (Math.PI / 180.0));
                    this.mob.zza = f2 * f1;
                    this.mob.yya = -sin * f1;
                } else {
                    float abs = Math.abs(Mth.wrapDegrees(this.mob.getYRot() - f));
                    float turningSpeedFactor = getTurningSpeedFactor(abs);
                    this.mob.setSpeed(f1 * this.outsideWaterSpeedModifier * turningSpeedFactor);
                }
            }
        } else {
            this.mob.setSpeed(0.0F);
            this.mob.setXxa(0.0F);
            this.mob.setYya(0.0F);
            this.mob.setZza(0.0F);
        }
    }

    private static float getTurningSpeedFactor(float degreesToTurn) {
        return 1.0F - Mth.clamp((degreesToTurn - 10.0F) / 50.0F, 0.0F, 1.0F);
    }
}
