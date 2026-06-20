package net.minecraft.gizmos;

import net.minecraft.util.ARGB;

public record GizmoStyle(int stroke, float strokeWidth, int fill) {
    private static final float DEFAULT_WIDTH = 2.5F;

    public static GizmoStyle stroke(int stroke) {
        return new GizmoStyle(stroke, 2.5F, 0);
    }

    public static GizmoStyle stroke(int stroke, float strokeWidth) {
        return new GizmoStyle(stroke, strokeWidth, 0);
    }

    public static GizmoStyle fill(int fill) {
        return new GizmoStyle(0, 0.0F, fill);
    }

    public static GizmoStyle strokeAndFill(int stroke, float strokeWidth, int fill) {
        return new GizmoStyle(stroke, strokeWidth, fill);
    }

    public boolean hasFill() {
        return this.fill != 0;
    }

    public boolean hasStroke() {
        return this.stroke != 0 && this.strokeWidth > 0.0F;
    }

    public int multipliedStroke(float alphaMultiplier) {
        return ARGB.multiplyAlpha(this.stroke, alphaMultiplier);
    }

    public int multipliedFill(float alphaMultiplier) {
        return ARGB.multiplyAlpha(this.fill, alphaMultiplier);
    }
}
