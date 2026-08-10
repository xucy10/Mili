package net.minecraft.world.level;

public interface ColorMapColorUtil {
    static int get(double x, double y, int[] pixels, int defaultValue) {
        y *= x;
        int i = (int)((1.0 - x) * 255.0);
        int i1 = (int)((1.0 - y) * 255.0);
        int i2 = i1 << 8 | i;
        return i2 >= pixels.length ? defaultValue : pixels[i2];
    }
}
