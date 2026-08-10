package net.minecraft.world.level;

public class DryFoliageColor {
    public static final int FOLIAGE_DRY_DEFAULT = -10732494;
    private static int[] pixels = new int[65536];

    public static void init(int[] pixels) {
        DryFoliageColor.pixels = pixels;
    }

    public static int get(double x, double y) {
        return ColorMapColorUtil.get(x, y, pixels, -10732494);
    }
}
