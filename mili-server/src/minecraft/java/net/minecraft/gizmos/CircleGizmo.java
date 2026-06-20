package net.minecraft.gizmos;

import net.minecraft.world.phys.Vec3;

public record CircleGizmo(Vec3 pos, float radius, GizmoStyle style) implements Gizmo {
    private static final int CIRCLE_VERTICES = 20;
    private static final float SEGMENT_SIZE_RADIANS = (float) (Math.PI / 10);

    @Override
    public void emit(GizmoPrimitives primitives, float alphaMultiplier) {
        if (this.style.hasStroke() || this.style.hasFill()) {
            Vec3[] vec3s = new Vec3[21];

            for (int i = 0; i < 20; i++) {
                float f = i * (float) (Math.PI / 10);
                Vec3 vec3 = this.pos.add((float)(this.radius * Math.cos(f)), 0.0, (float)(this.radius * Math.sin(f)));
                vec3s[i] = vec3;
            }

            vec3s[20] = vec3s[0];
            if (this.style.hasFill()) {
                int i = this.style.multipliedFill(alphaMultiplier);
                primitives.addTriangleFan(vec3s, i);
            }

            if (this.style.hasStroke()) {
                int i = this.style.multipliedStroke(alphaMultiplier);

                for (int i1 = 0; i1 < 20; i1++) {
                    primitives.addLine(vec3s[i1], vec3s[i1 + 1], i, this.style.strokeWidth());
                }
            }
        }
    }
}
