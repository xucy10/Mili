package net.minecraft.world.entity;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public record EntityDimensions(float width, float height, float eyeHeight, EntityAttachments attachments, boolean fixed) {
    private EntityDimensions(float width, float height, boolean fixed) {
        this(width, height, defaultEyeHeight(height), EntityAttachments.createDefault(width, height), fixed);
    }

    private static float defaultEyeHeight(float height) {
        return height * 0.85F;
    }

    public AABB makeBoundingBox(Vec3 pos) {
        return this.makeBoundingBox(pos.x, pos.y, pos.z);
    }

    public AABB makeBoundingBox(double x, double y, double z) {
        float f = this.width / 2.0F;
        float f1 = this.height;
        return new AABB(x - f, y, z - f, x + f, y + f1, z + f);
    }

    public EntityDimensions scale(float factor) {
        return this.scale(factor, factor);
    }

    public EntityDimensions scale(float widthFactor, float heightFactor) {
        return !this.fixed && (widthFactor != 1.0F || heightFactor != 1.0F)
            ? new EntityDimensions(
                this.width * widthFactor,
                this.height * heightFactor,
                this.eyeHeight * heightFactor,
                this.attachments.scale(widthFactor, heightFactor, widthFactor),
                false
            )
            : this;
    }

    public static EntityDimensions scalable(float width, float height) {
        return new EntityDimensions(width, height, false);
    }

    public static EntityDimensions fixed(float width, float height) {
        return new EntityDimensions(width, height, true);
    }

    public EntityDimensions withEyeHeight(float eyeHeight) {
        return new EntityDimensions(this.width, this.height, eyeHeight, this.attachments, this.fixed);
    }

    public EntityDimensions withAttachments(EntityAttachments.Builder attachments) {
        return new EntityDimensions(this.width, this.height, this.eyeHeight, attachments.build(this.width, this.height), this.fixed);
    }
}
