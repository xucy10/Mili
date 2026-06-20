package net.minecraft.gizmos;

import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public record CuboidGizmo(AABB aabb, GizmoStyle style, boolean coloredCornerStroke) implements Gizmo {
    @Override
    public void emit(GizmoPrimitives primitives, float alphaMultiplier) {
        double d = this.aabb.minX;
        double d1 = this.aabb.minY;
        double d2 = this.aabb.minZ;
        double d3 = this.aabb.maxX;
        double d4 = this.aabb.maxY;
        double d5 = this.aabb.maxZ;
        if (this.style.hasFill()) {
            int i = this.style.multipliedFill(alphaMultiplier);
            primitives.addQuad(new Vec3(d3, d1, d2), new Vec3(d3, d4, d2), new Vec3(d3, d4, d5), new Vec3(d3, d1, d5), i);
            primitives.addQuad(new Vec3(d, d1, d2), new Vec3(d, d1, d5), new Vec3(d, d4, d5), new Vec3(d, d4, d2), i);
            primitives.addQuad(new Vec3(d, d1, d2), new Vec3(d, d4, d2), new Vec3(d3, d4, d2), new Vec3(d3, d1, d2), i);
            primitives.addQuad(new Vec3(d, d1, d5), new Vec3(d3, d1, d5), new Vec3(d3, d4, d5), new Vec3(d, d4, d5), i);
            primitives.addQuad(new Vec3(d, d4, d2), new Vec3(d, d4, d5), new Vec3(d3, d4, d5), new Vec3(d3, d4, d2), i);
            primitives.addQuad(new Vec3(d, d1, d2), new Vec3(d3, d1, d2), new Vec3(d3, d1, d5), new Vec3(d, d1, d5), i);
        }

        if (this.style.hasStroke()) {
            int i = this.style.multipliedStroke(alphaMultiplier);
            primitives.addLine(new Vec3(d, d1, d2), new Vec3(d3, d1, d2), this.coloredCornerStroke ? ARGB.multiply(i, -34953) : i, this.style.strokeWidth());
            primitives.addLine(new Vec3(d, d1, d2), new Vec3(d, d4, d2), this.coloredCornerStroke ? ARGB.multiply(i, -8913033) : i, this.style.strokeWidth());
            primitives.addLine(new Vec3(d, d1, d2), new Vec3(d, d1, d5), this.coloredCornerStroke ? ARGB.multiply(i, -8947713) : i, this.style.strokeWidth());
            primitives.addLine(new Vec3(d3, d1, d2), new Vec3(d3, d4, d2), i, this.style.strokeWidth());
            primitives.addLine(new Vec3(d3, d4, d2), new Vec3(d, d4, d2), i, this.style.strokeWidth());
            primitives.addLine(new Vec3(d, d4, d2), new Vec3(d, d4, d5), i, this.style.strokeWidth());
            primitives.addLine(new Vec3(d, d4, d5), new Vec3(d, d1, d5), i, this.style.strokeWidth());
            primitives.addLine(new Vec3(d, d1, d5), new Vec3(d3, d1, d5), i, this.style.strokeWidth());
            primitives.addLine(new Vec3(d3, d1, d5), new Vec3(d3, d1, d2), i, this.style.strokeWidth());
            primitives.addLine(new Vec3(d, d4, d5), new Vec3(d3, d4, d5), i, this.style.strokeWidth());
            primitives.addLine(new Vec3(d3, d1, d5), new Vec3(d3, d4, d5), i, this.style.strokeWidth());
            primitives.addLine(new Vec3(d3, d4, d2), new Vec3(d3, d4, d5), i, this.style.strokeWidth());
        }
    }
}
