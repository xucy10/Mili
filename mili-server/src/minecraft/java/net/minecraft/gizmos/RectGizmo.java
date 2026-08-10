package net.minecraft.gizmos;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public record RectGizmo(Vec3 a, Vec3 b, Vec3 c, Vec3 d, GizmoStyle style) implements Gizmo {
    public static RectGizmo fromCuboidFace(Vec3 pos1, Vec3 pos2, Direction face, GizmoStyle style) {
        return switch (face) {
            case DOWN -> new RectGizmo(
                new Vec3(pos1.x, pos1.y, pos1.z), new Vec3(pos2.x, pos1.y, pos1.z), new Vec3(pos2.x, pos1.y, pos2.z), new Vec3(pos1.x, pos1.y, pos2.z), style
            );
            case UP -> new RectGizmo(
                new Vec3(pos1.x, pos2.y, pos1.z), new Vec3(pos1.x, pos2.y, pos2.z), new Vec3(pos2.x, pos2.y, pos2.z), new Vec3(pos2.x, pos2.y, pos1.z), style
            );
            case NORTH -> new RectGizmo(
                new Vec3(pos1.x, pos1.y, pos1.z), new Vec3(pos1.x, pos2.y, pos1.z), new Vec3(pos2.x, pos2.y, pos1.z), new Vec3(pos2.x, pos1.y, pos1.z), style
            );
            case SOUTH -> new RectGizmo(
                new Vec3(pos1.x, pos1.y, pos2.z), new Vec3(pos2.x, pos1.y, pos2.z), new Vec3(pos2.x, pos2.y, pos2.z), new Vec3(pos1.x, pos2.y, pos2.z), style
            );
            case WEST -> new RectGizmo(
                new Vec3(pos1.x, pos1.y, pos1.z), new Vec3(pos1.x, pos1.y, pos2.z), new Vec3(pos1.x, pos2.y, pos2.z), new Vec3(pos1.x, pos2.y, pos1.z), style
            );
            case EAST -> new RectGizmo(
                new Vec3(pos2.x, pos1.y, pos1.z), new Vec3(pos2.x, pos2.y, pos1.z), new Vec3(pos2.x, pos2.y, pos2.z), new Vec3(pos2.x, pos1.y, pos2.z), style
            );
        };
    }

    @Override
    public void emit(GizmoPrimitives primitives, float alphaMultiplier) {
        if (this.style.hasFill()) {
            int i = this.style.multipliedFill(alphaMultiplier);
            primitives.addQuad(this.a, this.b, this.c, this.d, i);
        }

        if (this.style.hasStroke()) {
            int i = this.style.multipliedStroke(alphaMultiplier);
            primitives.addLine(this.a, this.b, i, this.style.strokeWidth());
            primitives.addLine(this.b, this.c, i, this.style.strokeWidth());
            primitives.addLine(this.c, this.d, i, this.style.strokeWidth());
            primitives.addLine(this.d, this.a, i, this.style.strokeWidth());
        }
    }
}
