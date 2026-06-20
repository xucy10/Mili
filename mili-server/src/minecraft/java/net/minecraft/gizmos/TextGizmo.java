package net.minecraft.gizmos;

import java.util.OptionalDouble;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;

public record TextGizmo(Vec3 pos, String text, TextGizmo.Style style) implements Gizmo {
    @Override
    public void emit(GizmoPrimitives primitives, float alphaMultiplier) {
        TextGizmo.Style style;
        if (alphaMultiplier < 1.0F) {
            style = new TextGizmo.Style(ARGB.multiplyAlpha(this.style.color, alphaMultiplier), this.style.scale, this.style.adjustLeft);
        } else {
            style = this.style;
        }

        primitives.addText(this.pos, this.text, style);
    }

    public record Style(int color, float scale, OptionalDouble adjustLeft) {
        public static final float DEFAULT_SCALE = 0.32F;

        public static TextGizmo.Style whiteAndCentered() {
            return new TextGizmo.Style(-1, 0.32F, OptionalDouble.empty());
        }

        public static TextGizmo.Style forColorAndCentered(int color) {
            return new TextGizmo.Style(color, 0.32F, OptionalDouble.empty());
        }

        public static TextGizmo.Style forColor(int color) {
            return new TextGizmo.Style(color, 0.32F, OptionalDouble.of(0.0));
        }

        public TextGizmo.Style withScale(float scale) {
            return new TextGizmo.Style(this.color, scale, this.adjustLeft);
        }

        public TextGizmo.Style withLeftAlignment(float adjustLeft) {
            return new TextGizmo.Style(this.color, this.scale, OptionalDouble.of(adjustLeft));
        }
    }
}
