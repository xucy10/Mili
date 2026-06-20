package net.minecraft.world.level.levelgen.synth;

import com.google.common.annotations.VisibleForTesting;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public final class ImprovedNoise {
    private static final float SHIFT_UP_EPSILON = 1.0E-7F;
    private final byte[] p;
    public final double xo;
    public final double yo;
    public final double zo;

    // Gale start - C2ME - optimize noise generation
    private static final double[] FLAT_SIMPLEX_GRAD = new double[]{
        1, 1, 0, 0,
        -1, 1, 0, 0,
        1, -1, 0, 0,
        -1, -1, 0, 0,
        1, 0, 1, 0,
        -1, 0, 1, 0,
        1, 0, -1, 0,
        -1, 0, -1, 0,
        0, 1, 1, 0,
        0, -1, 1, 0,
        0, 1, -1, 0,
        0, -1, -1, 0,
        1, 1, 0, 0,
        0, -1, 1, 0,
        -1, 1, 0, 0,
        0, -1, -1, 0,
    };
    // Gale end - C2ME - optimize noise generation

    public ImprovedNoise(RandomSource random) {
        this.xo = random.nextDouble() * 256.0;
        this.yo = random.nextDouble() * 256.0;
        this.zo = random.nextDouble() * 256.0;
        this.p = new byte[256];

        for (int i = 0; i < 256; i++) {
            this.p[i] = (byte)i;
        }

        for (int i = 0; i < 256; i++) {
            int randomInt = random.nextInt(256 - i);
            byte b = this.p[i];
            this.p[i] = this.p[i + randomInt];
            this.p[i + randomInt] = b;
        }
    }

    public double noise(double x, double y, double z) {
        return this.noise(x, y, z, 0.0, 0.0);
    }

    @Deprecated
    public double noise(double x, double y, double z, double yScale, double yMax) {
        double d = x + this.xo;
        double d1 = y + this.yo;
        double d2 = z + this.zo;
        // Gale start - C2ME - optimize noise generation - optimize: remove frequent type conversions
        double floor = Math.floor(d);
        double floor1 = Math.floor(d1);
        double floor2 = Math.floor(d2);
        // Gale end - C2ME - optimize noise generation - optimize: remove frequent type conversions
        double d3 = d - floor;
        double d4 = d1 - floor1;
        double d5 = d2 - floor2;
        double d7;
        if (yScale != 0.0) {
            double d6;
            if (yMax >= 0.0 && yMax < d4) {
                d6 = yMax;
            } else {
                d6 = d4;
            }

            d7 = Math.floor(d6 / yScale + 1.0E-7F) * yScale; // Gale - C2ME - optimize noise generation - optimize: remove frequent type conversions
        } else {
            d7 = 0.0;
        }

        return this.sampleAndLerp((int) floor, (int) floor1, (int) floor2, d3, d4 - d7, d5, d4); // Gale - C2ME - optimize noise generation - optimize: remove frequent type conversions
    }

    public double noiseWithDerivative(double x, double y, double z, double[] values) {
        double d = x + this.xo;
        double d1 = y + this.yo;
        double d2 = z + this.zo;
        // Gale start - C2ME - optimize noise generation - optimize: remove frequent type conversions
        double floor = Math.floor(d);
        double floor1 = Math.floor(d1);
        double floor2 = Math.floor(d2);
        // Gale end - C2ME - optimize noise generation - optimize: remove frequent type conversions
        double d3 = d - floor;
        double d4 = d1 - floor1;
        double d5 = d2 - floor2;
        return this.sampleWithDerivative((int) floor, (int) floor1, (int) floor2, d3, d4, d5, values); // Gale - C2ME - optimize noise generation - optimize: remove frequent type conversions
    }

    private static double gradDot(int gradIndex, double xFactor, double yFactor, double zFactor) {
        return SimplexNoise.dot(SimplexNoise.GRADIENT[gradIndex & 15], xFactor, yFactor, zFactor);
    }

    private int p(int index) {
        return this.p[index & 0xFF] & 0xFF;
    }

    private double sampleAndLerp(int gridX, int gridY, int gridZ, double deltaX, double weirdDeltaY, double deltaZ, double deltaY) {
        // Gale start - C2ME - optimize noise generation - inline math & small optimization: remove frequent type conversions and redundant ops
        final int var0 = gridX & 0xFF;
        final int var1 = (gridX + 1) & 0xFF;
        final int var2 = this.p[var0] & 0xFF;
        final int var3 = this.p[var1] & 0xFF;
        final int var4 = (var2 + gridY) & 0xFF;
        final int var5 = (var3 + gridY) & 0xFF;
        final int var6 = (var2 + gridY + 1) & 0xFF;
        final int var7 = (var3 + gridY + 1) & 0xFF;
        final int var8 = this.p[var4] & 0xFF;
        final int var9 = this.p[var5] & 0xFF;
        final int var10 = this.p[var6] & 0xFF;
        final int var11 = this.p[var7] & 0xFF;

        final int var12 = (var8 + gridZ) & 0xFF;
        final int var13 = (var9 + gridZ) & 0xFF;
        final int var14 = (var10 + gridZ) & 0xFF;
        final int var15 = (var11 + gridZ) & 0xFF;
        final int var16 = (var8 + gridZ + 1) & 0xFF;
        final int var17 = (var9 + gridZ + 1) & 0xFF;
        final int var18 = (var10 + gridZ + 1) & 0xFF;
        final int var19 = (var11 + gridZ + 1) & 0xFF;
        final int var20 = (this.p[var12] & 15) << 2;
        final int var21 = (this.p[var13] & 15) << 2;
        final int var22 = (this.p[var14] & 15) << 2;
        final int var23 = (this.p[var15] & 15) << 2;
        final int var24 = (this.p[var16] & 15) << 2;
        final int var25 = (this.p[var17] & 15) << 2;
        final int var26 = (this.p[var18] & 15) << 2;
        final int var27 = (this.p[var19] & 15) << 2;
        final double var60 = deltaX - 1.0;
        final double var61 = weirdDeltaY - 1.0;
        final double var62 = deltaZ - 1.0;
        final double var87 = FLAT_SIMPLEX_GRAD[(var20) | 0] * deltaX + FLAT_SIMPLEX_GRAD[(var20) | 1] * weirdDeltaY + FLAT_SIMPLEX_GRAD[(var20) | 2] * deltaZ;
        final double var88 = FLAT_SIMPLEX_GRAD[(var21) | 0] * var60 + FLAT_SIMPLEX_GRAD[(var21) | 1] * weirdDeltaY + FLAT_SIMPLEX_GRAD[(var21) | 2] * deltaZ;
        final double var89 = FLAT_SIMPLEX_GRAD[(var22) | 0] * deltaX + FLAT_SIMPLEX_GRAD[(var22) | 1] * var61 + FLAT_SIMPLEX_GRAD[(var22) | 2] * deltaZ;
        final double var90 = FLAT_SIMPLEX_GRAD[(var23) | 0] * var60 + FLAT_SIMPLEX_GRAD[(var23) | 1] * var61 + FLAT_SIMPLEX_GRAD[(var23) | 2] * deltaZ;
        final double var91 = FLAT_SIMPLEX_GRAD[(var24) | 0] * deltaX + FLAT_SIMPLEX_GRAD[(var24) | 1] * weirdDeltaY + FLAT_SIMPLEX_GRAD[(var24) | 2] * var62;
        final double var92 = FLAT_SIMPLEX_GRAD[(var25) | 0] * var60 + FLAT_SIMPLEX_GRAD[(var25) | 1] * weirdDeltaY + FLAT_SIMPLEX_GRAD[(var25) | 2] * var62;
        final double var93 = FLAT_SIMPLEX_GRAD[(var26) | 0] * deltaX + FLAT_SIMPLEX_GRAD[(var26) | 1] * var61 + FLAT_SIMPLEX_GRAD[(var26) | 2] * var62;
        final double var94 = FLAT_SIMPLEX_GRAD[(var27) | 0] * var60 + FLAT_SIMPLEX_GRAD[(var27) | 1] * var61 + FLAT_SIMPLEX_GRAD[(var27) | 2] * var62;

        final double var95 = deltaX * 6.0 - 15.0;
        final double var96 = deltaY * 6.0 - 15.0;
        final double var97 = deltaZ * 6.0 - 15.0;
        final double var98 = deltaX * var95 + 10.0;
        final double var99 = deltaY * var96 + 10.0;
        final double var100 = deltaZ * var97 + 10.0;
        final double var101 = deltaX * deltaX * deltaX * var98;
        final double var102 = deltaY * deltaY * deltaY * var99;
        final double var103 = deltaZ * deltaZ * deltaZ * var100;

        final double var113 = var87 + var101 * (var88 - var87);
        final double var114 = var93 + var101 * (var94 - var93);
        final double var115 = var91 + var101 * (var92 - var91);
        final double var116 = var89 + var101 * (var90 - var89);
        final double var117 = var114 - var115;
        final double var118 = var102 * (var116 - var113);
        final double var119 = var102 * var117;
        final double var120 = var113 + var118;
        final double var121 = var115 + var119;
        return var120 + (var103 * (var121 - var120));
        // Gale end - C2ME - optimize noise generation - inline math & small optimization: remove frequent type conversions and redundant ops
    }

    private double sampleWithDerivative(int gridX, int gridY, int gridZ, double deltaX, double deltaY, double deltaZ, double[] noiseValues) {
        int i = this.p(gridX);
        int i1 = this.p(gridX + 1);
        int i2 = this.p(i + gridY);
        int i3 = this.p(i + gridY + 1);
        int i4 = this.p(i1 + gridY);
        int i5 = this.p(i1 + gridY + 1);
        int i6 = this.p(i2 + gridZ);
        int i7 = this.p(i4 + gridZ);
        int i8 = this.p(i3 + gridZ);
        int i9 = this.p(i5 + gridZ);
        int i10 = this.p(i2 + gridZ + 1);
        int i11 = this.p(i4 + gridZ + 1);
        int i12 = this.p(i3 + gridZ + 1);
        int i13 = this.p(i5 + gridZ + 1);
        int[] ints = SimplexNoise.GRADIENT[i6 & 15];
        int[] ints1 = SimplexNoise.GRADIENT[i7 & 15];
        int[] ints2 = SimplexNoise.GRADIENT[i8 & 15];
        int[] ints3 = SimplexNoise.GRADIENT[i9 & 15];
        int[] ints4 = SimplexNoise.GRADIENT[i10 & 15];
        int[] ints5 = SimplexNoise.GRADIENT[i11 & 15];
        int[] ints6 = SimplexNoise.GRADIENT[i12 & 15];
        int[] ints7 = SimplexNoise.GRADIENT[i13 & 15];
        double d = SimplexNoise.dot(ints, deltaX, deltaY, deltaZ);
        double d1 = SimplexNoise.dot(ints1, deltaX - 1.0, deltaY, deltaZ);
        double d2 = SimplexNoise.dot(ints2, deltaX, deltaY - 1.0, deltaZ);
        double d3 = SimplexNoise.dot(ints3, deltaX - 1.0, deltaY - 1.0, deltaZ);
        double d4 = SimplexNoise.dot(ints4, deltaX, deltaY, deltaZ - 1.0);
        double d5 = SimplexNoise.dot(ints5, deltaX - 1.0, deltaY, deltaZ - 1.0);
        double d6 = SimplexNoise.dot(ints6, deltaX, deltaY - 1.0, deltaZ - 1.0);
        double d7 = SimplexNoise.dot(ints7, deltaX - 1.0, deltaY - 1.0, deltaZ - 1.0);
        double d8 = Mth.smoothstep(deltaX);
        double d9 = Mth.smoothstep(deltaY);
        double d10 = Mth.smoothstep(deltaZ);
        double d11 = Mth.lerp3(d8, d9, d10, ints[0], ints1[0], ints2[0], ints3[0], ints4[0], ints5[0], ints6[0], ints7[0]);
        double d12 = Mth.lerp3(d8, d9, d10, ints[1], ints1[1], ints2[1], ints3[1], ints4[1], ints5[1], ints6[1], ints7[1]);
        double d13 = Mth.lerp3(d8, d9, d10, ints[2], ints1[2], ints2[2], ints3[2], ints4[2], ints5[2], ints6[2], ints7[2]);
        double d14 = Mth.lerp2(d9, d10, d1 - d, d3 - d2, d5 - d4, d7 - d6);
        double d15 = Mth.lerp2(d10, d8, d2 - d, d6 - d4, d3 - d1, d7 - d5);
        double d16 = Mth.lerp2(d8, d9, d4 - d, d5 - d1, d6 - d2, d7 - d3);
        double d17 = Mth.smoothstepDerivative(deltaX);
        double d18 = Mth.smoothstepDerivative(deltaY);
        double d19 = Mth.smoothstepDerivative(deltaZ);
        double d20 = d11 + d17 * d14;
        double d21 = d12 + d18 * d15;
        double d22 = d13 + d19 * d16;
        noiseValues[0] += d20;
        noiseValues[1] += d21;
        noiseValues[2] += d22;
        return Mth.lerp3(d8, d9, d10, d, d1, d2, d3, d4, d5, d6, d7);
    }

    @VisibleForTesting
    public void parityConfigString(StringBuilder builder) {
        NoiseUtils.parityNoiseOctaveConfigString(builder, this.xo, this.yo, this.zo, this.p);
    }
}
