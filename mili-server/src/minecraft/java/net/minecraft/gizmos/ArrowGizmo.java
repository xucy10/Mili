package net.minecraft.gizmos;

import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public record ArrowGizmo(Vec3 start, Vec3 end, int color, float width) implements Gizmo {
    public static final float DEFAULT_WIDTH = 2.5F;

    @Override
    public void emit(GizmoPrimitives primitives, float alphaMultiplier) {
        int i = ARGB.multiplyAlpha(this.color, alphaMultiplier);
        primitives.addLine(this.start, this.end, i, this.width);
        Quaternionf quaternionf = new Quaternionf().rotationTo(new Vector3f(1.0F, 0.0F, 0.0F), this.end.subtract(this.start).toVector3f().normalize());
        float f = (float)Mth.clamp(this.end.distanceTo(this.start) * 0.1F, 0.1F, 1.0);
        Vector3f[] vector3fs = new Vector3f[]{
            quaternionf.transform(-f, f, 0.0F, new Vector3f()),
            quaternionf.transform(-f, 0.0F, f, new Vector3f()),
            quaternionf.transform(-f, -f, 0.0F, new Vector3f()),
            quaternionf.transform(-f, 0.0F, -f, new Vector3f())
        };

        for (Vector3f vector3f : vector3fs) {
            primitives.addLine(this.end.add(vector3f.x, vector3f.y, vector3f.z), this.end, i, this.width);
        }
    }
}
