package net.minecraft.world.entity;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class ElytraAnimationState {
    private static final float DEFAULT_X_ROT = (float) (Math.PI / 12);
    private static final float DEFAULT_Z_ROT = (float) (-Math.PI / 12);
    private float rotX;
    private float rotY;
    private float rotZ;
    private float rotXOld;
    private float rotYOld;
    private float rotZOld;
    private final LivingEntity entity;

    public ElytraAnimationState(LivingEntity entity) {
        this.entity = entity;
    }

    public void tick() {
        this.rotXOld = this.rotX;
        this.rotYOld = this.rotY;
        this.rotZOld = this.rotZ;
        float f1;
        float f2;
        float f3;
        if (this.entity.isFallFlying()) {
            float f = 1.0F;
            Vec3 deltaMovement = this.entity.getDeltaMovement();
            if (deltaMovement.y < 0.0) {
                Vec3 vec3 = deltaMovement.normalize();
                f = 1.0F - (float)Math.pow(-vec3.y, 1.5);
            }

            f1 = Mth.lerp(f, (float) (Math.PI / 12), (float) (Math.PI / 9));
            f2 = Mth.lerp(f, (float) (-Math.PI / 12), (float) (-Math.PI / 2));
            f3 = 0.0F;
        } else if (this.entity.isCrouching()) {
            f1 = (float) (Math.PI * 2.0 / 9.0);
            f2 = (float) (-Math.PI / 4);
            f3 = 0.08726646F;
        } else {
            f1 = (float) (Math.PI / 12);
            f2 = (float) (-Math.PI / 12);
            f3 = 0.0F;
        }

        this.rotX = this.rotX + (f1 - this.rotX) * 0.3F;
        this.rotY = this.rotY + (f3 - this.rotY) * 0.3F;
        this.rotZ = this.rotZ + (f2 - this.rotZ) * 0.3F;
    }

    public float getRotX(float partialTick) {
        return Mth.lerp(partialTick, this.rotXOld, this.rotX);
    }

    public float getRotY(float partialTick) {
        return Mth.lerp(partialTick, this.rotYOld, this.rotY);
    }

    public float getRotZ(float partialTick) {
        return Mth.lerp(partialTick, this.rotZOld, this.rotZ);
    }
}
