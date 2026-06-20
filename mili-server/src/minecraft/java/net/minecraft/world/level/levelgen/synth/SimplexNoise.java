package net.minecraft.world.level.levelgen.synth;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public class SimplexNoise {
    protected static final int[][] GRADIENT = new int[][]{
        {1, 1, 0},
        {-1, 1, 0},
        {1, -1, 0},
        {-1, -1, 0},
        {1, 0, 1},
        {-1, 0, 1},
        {1, 0, -1},
        {-1, 0, -1},
        {0, 1, 1},
        {0, -1, 1},
        {0, 1, -1},
        {0, -1, -1},
        {1, 1, 0},
        {0, -1, 1},
        {-1, 1, 0},
        {0, -1, -1}
    };
    private static final double SQRT_3 = Math.sqrt(3.0);
    private static final double F2 = 0.5 * (SQRT_3 - 1.0);
    private static final double G2 = (3.0 - SQRT_3) / 6.0;
    private final int[] p = new int[512];
    public final double xo;
    public final double yo;
    public final double zo;

    public SimplexNoise(RandomSource random) {
        this.xo = random.nextDouble() * 256.0;
        this.yo = random.nextDouble() * 256.0;
        this.zo = random.nextDouble() * 256.0;
        int i = 0;

        while (i < 256) {
            this.p[i] = i++;
        }

        for (int ix = 0; ix < 256; ix++) {
            int randomInt = random.nextInt(256 - ix);
            int i1 = this.p[ix];
            this.p[ix] = this.p[randomInt + ix];
            this.p[randomInt + ix] = i1;
        }
    }

    private int p(int index) {
        return this.p[index & 0xFF];
    }

    protected static double dot(int[] gradient, double x, double y, double z) {
        return gradient[0] * x + gradient[1] * y + gradient[2] * z;
    }

    private double getCornerNoise3D(int gradientIndex, double x, double y, double z, double offset) {
        double d = offset - x * x - y * y - z * z;
        double d1;
        if (d < 0.0) {
            d1 = 0.0;
        } else {
            d *= d;
            d1 = d * d * dot(GRADIENT[gradientIndex], x, y, z);
        }

        return d1;
    }

    public double getValue(double x, double y) {
        double d = (x + y) * F2;
        int floor = Mth.floor(x + d);
        int floor1 = Mth.floor(y + d);
        double d1 = (floor + floor1) * G2;
        double d2 = floor - d1;
        double d3 = floor1 - d1;
        double d4 = x - d2;
        double d5 = y - d3;
        int i;
        int i1;
        if (d4 > d5) {
            i = 1;
            i1 = 0;
        } else {
            i = 0;
            i1 = 1;
        }

        double d6 = d4 - i + G2;
        double d7 = d5 - i1 + G2;
        double d8 = d4 - 1.0 + 2.0 * G2;
        double d9 = d5 - 1.0 + 2.0 * G2;
        int i2 = floor & 0xFF;
        int i3 = floor1 & 0xFF;
        int i4 = this.p(i2 + this.p(i3)) % 12;
        int i5 = this.p(i2 + i + this.p(i3 + i1)) % 12;
        int i6 = this.p(i2 + 1 + this.p(i3 + 1)) % 12;
        double cornerNoise3D = this.getCornerNoise3D(i4, d4, d5, 0.0, 0.5);
        double cornerNoise3D1 = this.getCornerNoise3D(i5, d6, d7, 0.0, 0.5);
        double cornerNoise3D2 = this.getCornerNoise3D(i6, d8, d9, 0.0, 0.5);
        return 70.0 * (cornerNoise3D + cornerNoise3D1 + cornerNoise3D2);
    }

    public double getValue(double x, double y, double z) {
        double d = 0.3333333333333333;
        double d1 = (x + y + z) * 0.3333333333333333;
        int floor = Mth.floor(x + d1);
        int floor1 = Mth.floor(y + d1);
        int floor2 = Mth.floor(z + d1);
        double d2 = 0.16666666666666666;
        double d3 = (floor + floor1 + floor2) * 0.16666666666666666;
        double d4 = floor - d3;
        double d5 = floor1 - d3;
        double d6 = floor2 - d3;
        double d7 = x - d4;
        double d8 = y - d5;
        double d9 = z - d6;
        int i;
        int i1;
        int i2;
        int i3;
        int i4;
        int i5;
        if (d7 >= d8) {
            if (d8 >= d9) {
                i = 1;
                i1 = 0;
                i2 = 0;
                i3 = 1;
                i4 = 1;
                i5 = 0;
            } else if (d7 >= d9) {
                i = 1;
                i1 = 0;
                i2 = 0;
                i3 = 1;
                i4 = 0;
                i5 = 1;
            } else {
                i = 0;
                i1 = 0;
                i2 = 1;
                i3 = 1;
                i4 = 0;
                i5 = 1;
            }
        } else if (d8 < d9) {
            i = 0;
            i1 = 0;
            i2 = 1;
            i3 = 0;
            i4 = 1;
            i5 = 1;
        } else if (d7 < d9) {
            i = 0;
            i1 = 1;
            i2 = 0;
            i3 = 0;
            i4 = 1;
            i5 = 1;
        } else {
            i = 0;
            i1 = 1;
            i2 = 0;
            i3 = 1;
            i4 = 1;
            i5 = 0;
        }

        double d10 = d7 - i + 0.16666666666666666;
        double d11 = d8 - i1 + 0.16666666666666666;
        double d12 = d9 - i2 + 0.16666666666666666;
        double d13 = d7 - i3 + 0.3333333333333333;
        double d14 = d8 - i4 + 0.3333333333333333;
        double d15 = d9 - i5 + 0.3333333333333333;
        double d16 = d7 - 1.0 + 0.5;
        double d17 = d8 - 1.0 + 0.5;
        double d18 = d9 - 1.0 + 0.5;
        int i6 = floor & 0xFF;
        int i7 = floor1 & 0xFF;
        int i8 = floor2 & 0xFF;
        int i9 = this.p(i6 + this.p(i7 + this.p(i8))) % 12;
        int i10 = this.p(i6 + i + this.p(i7 + i1 + this.p(i8 + i2))) % 12;
        int i11 = this.p(i6 + i3 + this.p(i7 + i4 + this.p(i8 + i5))) % 12;
        int i12 = this.p(i6 + 1 + this.p(i7 + 1 + this.p(i8 + 1))) % 12;
        double cornerNoise3D = this.getCornerNoise3D(i9, d7, d8, d9, 0.6);
        double cornerNoise3D1 = this.getCornerNoise3D(i10, d10, d11, d12, 0.6);
        double cornerNoise3D2 = this.getCornerNoise3D(i11, d13, d14, d15, 0.6);
        double cornerNoise3D3 = this.getCornerNoise3D(i12, d16, d17, d18, 0.6);
        return 32.0 * (cornerNoise3D + cornerNoise3D1 + cornerNoise3D2 + cornerNoise3D3);
    }
}
