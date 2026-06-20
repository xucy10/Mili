package net.minecraft.world.level.biome;

import com.google.common.hash.Hashing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.util.LinearCongruentialGenerator;
import net.minecraft.util.Mth;

public class BiomeManager {
    public static final int CHUNK_CENTER_QUART = QuartPos.fromBlock(8);
    private static final int ZOOM_BITS = 2;
    private static final int ZOOM = 4;
    private static final int ZOOM_MASK = 3;
    private final BiomeManager.NoiseBiomeSource noiseBiomeSource;
    private final long biomeZoomSeed;
    private static final double maxOffset = 0.4500000001D; // Leaf - Carpet-Fixes - Optimized getBiome method

    public BiomeManager(BiomeManager.NoiseBiomeSource noiseBiomeSource, long biomeZoomSeed) {
        this.noiseBiomeSource = noiseBiomeSource;
        this.biomeZoomSeed = biomeZoomSeed;
    }

    public static long obfuscateSeed(long seed) {
        return Hashing.sha256().hashLong(seed).asLong();
    }

    public BiomeManager withDifferentSource(BiomeManager.NoiseBiomeSource newSource) {
        return new BiomeManager(newSource, this.biomeZoomSeed);
    }

    public Holder<Biome> getBiome(BlockPos pos) {
        // Leaf start - Carpet-Fixes - Optimized getBiome method
        int xMinus2 = pos.getX() - 2;
        int yMinus2 = pos.getY() - 2;
        int zMinus2 = pos.getZ() - 2;
        int x = xMinus2 >> 2; // BlockPos to BiomePos
        int y = yMinus2 >> 2;
        int z = zMinus2 >> 2;
        double quartX = (double) (xMinus2 & 3) / 4.0; // quartLocal divided by 4
        double quartY = (double) (yMinus2 & 3) / 4.0; // 0/4, 1/4, 2/4, 3/4
        double quartZ = (double) (zMinus2 & 3) / 4.0; // [0, 0.25, 0.5, 0.75]
        int smallestX = 0;
        double smallestDist = Double.POSITIVE_INFINITY;
        for (int biomeX = 0; biomeX < 8; ++biomeX) {
            boolean everyOtherQuad = (biomeX & 4) == 0; // 1 1 1 1 0 0 0 0
            boolean everyOtherPair = (biomeX & 2) == 0; // 1 1 0 0 1 1 0 0
            boolean everyOther = (biomeX & 1) == 0; // 1 0 1 0 1 0 1 0
            double quartXX = everyOtherQuad ? quartX : quartX - 1.0; //[-1.0,-0.75,-0.5,-0.25,0.0,0.25,0.5,0.75]
            double quartYY = everyOtherPair ? quartY : quartY - 1.0;
            double quartZZ = everyOther ? quartZ : quartZ - 1.0;

            //This code block is new
            double maxQuartYY = 0.0, maxQuartZZ = 0.0;
            if (biomeX != 0) {
                maxQuartYY = Mth.square(Math.max(quartYY + maxOffset, Math.abs(quartYY - maxOffset)));
                maxQuartZZ = Mth.square(Math.max(quartZZ + maxOffset, Math.abs(quartZZ - maxOffset)));
                double maxQuartXX = Mth.square(Math.max(quartXX + maxOffset, Math.abs(quartXX - maxOffset)));
                if (smallestDist < maxQuartXX + maxQuartYY + maxQuartZZ) continue;
            }
            int xx = everyOtherQuad ? x : x + 1;
            int yy = everyOtherPair ? y : y + 1;
            int zz = everyOther ? z : z + 1;

            //I transferred the code from method_38106 to here, so I could call continue halfway through
            long seed = LinearCongruentialGenerator.next(this.biomeZoomSeed, xx);
            seed = LinearCongruentialGenerator.next(seed, yy);
            seed = LinearCongruentialGenerator.next(seed, zz);
            seed = LinearCongruentialGenerator.next(seed, xx);
            seed = LinearCongruentialGenerator.next(seed, yy);
            seed = LinearCongruentialGenerator.next(seed, zz);
            double offsetX = getFiddle(seed);
            double sqrX = Mth.square(quartXX + offsetX);
            if (biomeX != 0 && smallestDist < sqrX + maxQuartYY + maxQuartZZ) continue; //skip the rest of the loop
            seed = LinearCongruentialGenerator.next(seed, this.biomeZoomSeed);
            double offsetY = getFiddle(seed);
            double sqrY = Mth.square(quartYY + offsetY);
            if (biomeX != 0 && smallestDist < sqrX + sqrY + maxQuartZZ) continue; // skip the rest of the loop
            seed = LinearCongruentialGenerator.next(seed, this.biomeZoomSeed);
            double offsetZ = getFiddle(seed);
            double biomeDist = sqrX + sqrY + Mth.square(quartZZ + offsetZ);

            if (smallestDist > biomeDist) {
                smallestX = biomeX;
                smallestDist = biomeDist;
            }
        }
        return this.noiseBiomeSource.getNoiseBiome(
            (smallestX & 4) == 0 ? x : x + 1,
            (smallestX & 2) == 0 ? y : y + 1,
            (smallestX & 1) == 0 ? z : z + 1
        );
        // Leaf end - Carpet-Fixes - Optimized getBiome method
    }

    public Holder<Biome> getNoiseBiomeAtPosition(double x, double y, double z) {
        int quartPosCoord = QuartPos.fromBlock(Mth.floor(x));
        int quartPosCoord1 = QuartPos.fromBlock(Mth.floor(y));
        int quartPosCoord2 = QuartPos.fromBlock(Mth.floor(z));
        return this.getNoiseBiomeAtQuart(quartPosCoord, quartPosCoord1, quartPosCoord2);
    }

    public Holder<Biome> getNoiseBiomeAtPosition(BlockPos pos) {
        int quartPosX = QuartPos.fromBlock(pos.getX());
        int quartPosY = QuartPos.fromBlock(pos.getY());
        int quartPosZ = QuartPos.fromBlock(pos.getZ());
        return this.getNoiseBiomeAtQuart(quartPosX, quartPosY, quartPosZ);
    }

    public Holder<Biome> getNoiseBiomeAtQuart(int x, int y, int z) {
        return this.noiseBiomeSource.getNoiseBiome(x, y, z);
    }

    private static double getFiddledDistance(long seed, int x, int y, int z, double xNoise, double yNoise, double zNoise) {
        long l = LinearCongruentialGenerator.next(seed, x);
        l = LinearCongruentialGenerator.next(l, y);
        l = LinearCongruentialGenerator.next(l, z);
        l = LinearCongruentialGenerator.next(l, x);
        l = LinearCongruentialGenerator.next(l, y);
        l = LinearCongruentialGenerator.next(l, z);
        double fiddle = getFiddle(l);
        l = LinearCongruentialGenerator.next(l, seed);
        double fiddle1 = getFiddle(l);
        l = LinearCongruentialGenerator.next(l, seed);
        double fiddle2 = getFiddle(l);
        return Mth.square(zNoise + fiddle2) + Mth.square(yNoise + fiddle1) + Mth.square(xNoise + fiddle);
    }

    private static double getFiddle(long seed) {
        return (double)(((seed >> 24) & (1024 - 1)) - (1024/2)) * (0.9 / 1024.0); // Paper - avoid floorMod, fp division, and fp subtraction
    }

    public interface NoiseBiomeSource {
        Holder<Biome> getNoiseBiome(int x, int y, int z);
    }
}
