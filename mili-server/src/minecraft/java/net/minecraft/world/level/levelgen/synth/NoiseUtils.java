package net.minecraft.world.level.levelgen.synth;

import java.util.Locale;

public class NoiseUtils {
    public static double biasTowardsExtreme(double value, double bias) {
        return value + Math.sin(Math.PI * value) * bias / Math.PI;
    }

    public static void parityNoiseOctaveConfigString(StringBuilder builder, double xo, double yo, double zo, byte[] p) {
        builder.append(String.format(Locale.ROOT, "xo=%.3f, yo=%.3f, zo=%.3f, p0=%d, p255=%d", (float)xo, (float)yo, (float)zo, p[0], p[255]));
    }

    public static void parityNoiseOctaveConfigString(StringBuilder builder, double xo, double yo, double zo, int[] p) {
        builder.append(String.format(Locale.ROOT, "xo=%.3f, yo=%.3f, zo=%.3f, p0=%d, p255=%d", (float)xo, (float)yo, (float)zo, p[0], p[255]));
    }
}
