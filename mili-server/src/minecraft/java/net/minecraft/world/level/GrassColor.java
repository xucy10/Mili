package net.minecraft.world.level;

public class GrassColor {
    private static int[] pixels = new int[65536];

    public static void init(int[] grassBuffer) {
        pixels = grassBuffer;
    }

    public static int get(double temperature, double downfall) {
        return ColorMapColorUtil.get(temperature, downfall, pixels, -65281);
    }

    public static int getDefaultColor() {
        return get(0.5, 1.0);
    }
}
