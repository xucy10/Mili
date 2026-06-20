package net.minecraft.world.entity;

import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class InterpolationHandler {
    public static final int DEFAULT_INTERPOLATION_STEPS = 3;
    private final Entity entity;
    private int interpolationSteps;
    private final InterpolationHandler.InterpolationData interpolationData = new InterpolationHandler.InterpolationData(0, Vec3.ZERO, 0.0F, 0.0F);
    private @Nullable Vec3 previousTickPosition;
    private @Nullable Vec2 previousTickRot;
    private final @Nullable Consumer<InterpolationHandler> onInterpolationStart;

    public InterpolationHandler(Entity entity) {
        this(entity, 3, null);
    }

    public InterpolationHandler(Entity entity, int interpolationSteps) {
        this(entity, interpolationSteps, null);
    }

    public InterpolationHandler(Entity entity, @Nullable Consumer<InterpolationHandler> onInterpolationStart) {
        this(entity, 3, onInterpolationStart);
    }

    public InterpolationHandler(Entity entity, int interpolationSteps, @Nullable Consumer<InterpolationHandler> onInterpolationStart) {
        this.interpolationSteps = interpolationSteps;
        this.entity = entity;
        this.onInterpolationStart = onInterpolationStart;
    }

    public Vec3 position() {
        return this.interpolationData.steps > 0 ? this.interpolationData.position : this.entity.position();
    }

    public float yRot() {
        return this.interpolationData.steps > 0 ? this.interpolationData.yRot : this.entity.getYRot();
    }

    public float xRot() {
        return this.interpolationData.steps > 0 ? this.interpolationData.xRot : this.entity.getXRot();
    }

    public void interpolateTo(Vec3 pos, float yRot, float xRot) {
        if (this.interpolationSteps == 0) {
            this.entity.snapTo(pos, yRot, xRot);
            this.cancel();
        } else if (!this.hasActiveInterpolation()
            || !Objects.equals(this.yRot(), yRot)
            || !Objects.equals(this.xRot(), xRot)
            || !Objects.equals(this.position(), pos)) {
            this.interpolationData.steps = this.interpolationSteps;
            this.interpolationData.position = pos;
            this.interpolationData.yRot = yRot;
            this.interpolationData.xRot = xRot;
            this.previousTickPosition = this.entity.position();
            this.previousTickRot = new Vec2(this.entity.getXRot(), this.entity.getYRot());
            if (this.onInterpolationStart != null) {
                this.onInterpolationStart.accept(this);
            }
        }
    }

    public boolean hasActiveInterpolation() {
        return this.interpolationData.steps > 0;
    }

    public void setInterpolationLength(int interpolationLength) {
        this.interpolationSteps = interpolationLength;
    }

    public void interpolate() {
        if (!this.hasActiveInterpolation()) {
            this.cancel();
        } else {
            double d = 1.0 / this.interpolationData.steps;
            if (this.previousTickPosition != null) {
                Vec3 vec3 = this.entity.position().subtract(this.previousTickPosition);
                if (this.entity.level().noCollision(this.entity, this.entity.makeBoundingBox(this.interpolationData.position.add(vec3)))) {
                    this.interpolationData.addDelta(vec3);
                }
            }

            if (this.previousTickRot != null) {
                float f = this.entity.getYRot() - this.previousTickRot.y;
                float f1 = this.entity.getXRot() - this.previousTickRot.x;
                this.interpolationData.addRotation(f, f1);
            }

            double d1 = Mth.lerp(d, this.entity.getX(), this.interpolationData.position.x);
            double d2 = Mth.lerp(d, this.entity.getY(), this.interpolationData.position.y);
            double d3 = Mth.lerp(d, this.entity.getZ(), this.interpolationData.position.z);
            Vec3 vec31 = new Vec3(d1, d2, d3);
            float f2 = (float)Mth.rotLerp(d, (double)this.entity.getYRot(), (double)this.interpolationData.yRot);
            float f3 = (float)Mth.lerp(d, (double)this.entity.getXRot(), (double)this.interpolationData.xRot);
            this.entity.setPos(vec31);
            this.entity.setRot(f2, f3);
            this.interpolationData.decrease();
            this.previousTickPosition = vec31;
            this.previousTickRot = new Vec2(this.entity.getXRot(), this.entity.getYRot());
        }
    }

    public void cancel() {
        this.interpolationData.steps = 0;
        this.previousTickPosition = null;
        this.previousTickRot = null;
    }

    static class InterpolationData {
        protected int steps;
        Vec3 position;
        float yRot;
        float xRot;

        InterpolationData(int steps, Vec3 position, float yRot, float xRot) {
            this.steps = steps;
            this.position = position;
            this.yRot = yRot;
            this.xRot = xRot;
        }

        public void decrease() {
            this.steps--;
        }

        public void addDelta(Vec3 delta) {
            this.position = this.position.add(delta);
        }

        public void addRotation(float yRot, float xRot) {
            this.yRot += yRot;
            this.xRot += xRot;
        }
    }
}
