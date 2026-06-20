package net.minecraft.world.entity.ai.control;

import java.util.Optional;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

public class LookControl implements Control {
    protected final Mob mob;
    protected float yMaxRotSpeed;
    protected float xMaxRotAngle;
    protected int lookAtCooldown;
    protected double wantedX;
    protected double wantedY;
    protected double wantedZ;

    public LookControl(Mob mob) {
        this.mob = mob;
    }

    public void setLookAt(Vec3 lookVector) {
        this.setLookAt(lookVector.x, lookVector.y, lookVector.z);
    }

    public void setLookAt(Entity entity) {
        this.setLookAt(entity.getX(), entity.getEyeY(), entity.getZ());
    }

    public void setLookAt(Entity entity, float deltaYaw, float deltaPitch) {
        this.setLookAt(entity.getX(), entity.getEyeY(), entity.getZ(), deltaYaw, deltaPitch);
    }

    public void setLookAt(double x, double y, double z) {
        this.setLookAt(x, y, z, this.mob.getHeadRotSpeed(), this.mob.getMaxHeadXRot());
    }

    public void setLookAt(double x, double y, double z, float deltaYaw, float deltaPitch) {
        this.wantedX = x;
        this.wantedY = y;
        this.wantedZ = z;
        this.yMaxRotSpeed = deltaYaw;
        this.xMaxRotAngle = deltaPitch;
        this.lookAtCooldown = 2;
    }

    public void tick() {
        if (this.resetXRotOnTick()) {
            this.mob.setXRot(0.0F);
        }

        if (this.lookAtCooldown > 0) {
            this.lookAtCooldown--;
            this.getYRotD().ifPresent(rotationWanted -> this.mob.yHeadRot = this.rotateTowards(this.mob.yHeadRot, rotationWanted, this.yMaxRotSpeed));
            this.getXRotD().ifPresent(_float -> this.mob.setXRot(this.rotateTowards(this.mob.getXRot(), _float, this.xMaxRotAngle)));
        } else {
            this.mob.yHeadRot = this.rotateTowards(this.mob.yHeadRot, this.mob.yBodyRot, 10.0F);
        }

        this.clampHeadRotationToBody();
    }

    protected void clampHeadRotationToBody() {
        if (!this.mob.getNavigation().isDone()) {
            this.mob.yHeadRot = Mth.rotateIfNecessary(this.mob.yHeadRot, this.mob.yBodyRot, this.mob.getMaxHeadYRot());
        }
    }

    protected boolean resetXRotOnTick() {
        return true;
    }

    public boolean isLookingAtTarget() {
        return this.lookAtCooldown > 0;
    }

    public double getWantedX() {
        return this.wantedX;
    }

    public double getWantedY() {
        return this.wantedY;
    }

    public double getWantedZ() {
        return this.wantedZ;
    }

    protected Optional<Float> getXRotD() {
        double d = this.wantedX - this.mob.getX();
        double d1 = this.wantedY - this.mob.getEyeY();
        double d2 = this.wantedZ - this.mob.getZ();
        double squareRoot = Math.sqrt(d * d + d2 * d2);
        return !(Math.abs(d1) > 1.0E-5F) && !(Math.abs(squareRoot) > 1.0E-5F)
            ? Optional.empty()
            : Optional.of((float)(-(Mth.atan2(d1, squareRoot) * 180.0F / (float)Math.PI)));
    }

    protected Optional<Float> getYRotD() {
        double d = this.wantedX - this.mob.getX();
        double d1 = this.wantedZ - this.mob.getZ();
        return !(Math.abs(d1) > 1.0E-5F) && !(Math.abs(d) > 1.0E-5F)
            ? Optional.empty()
            : Optional.of((float)(Mth.atan2(d1, d) * 180.0F / (float)Math.PI) - 90.0F);
    }
}
