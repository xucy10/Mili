package net.minecraft.world.level.levelgen.carver;

import com.mojang.serialization.Codec;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;

public class CanyonWorldCarver extends WorldCarver<CanyonCarverConfiguration> {
    public CanyonWorldCarver(Codec<CanyonCarverConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean isStartChunk(CanyonCarverConfiguration config, RandomSource random) {
        return random.nextFloat() <= config.probability;
    }

    @Override
    public boolean carve(
        CarvingContext context,
        CanyonCarverConfiguration config,
        ChunkAccess chunk,
        Function<BlockPos, Holder<Biome>> biomeAccessor,
        RandomSource random,
        Aquifer aquifer,
        ChunkPos chunkPos,
        CarvingMask carvingMask
    ) {
        int i = (this.getRange() * 2 - 1) * 16;
        double d = chunkPos.getBlockX(random.nextInt(16));
        int i1 = config.y.sample(random, context);
        double d1 = chunkPos.getBlockZ(random.nextInt(16));
        float f = random.nextFloat() * (float) (Math.PI * 2);
        float f1 = config.verticalRotation.sample(random);
        double d2 = config.yScale.sample(random);
        float f2 = config.shape.thickness.sample(random);
        int i2 = (int)(i * config.shape.distanceFactor.sample(random));
        int i3 = 0;
        this.doCarve(context, config, chunk, biomeAccessor, random.nextLong(), aquifer, d, i1, d1, f2, f, f1, 0, i2, d2, carvingMask);
        return true;
    }

    private void doCarve(
        CarvingContext context,
        CanyonCarverConfiguration config,
        ChunkAccess chunk,
        Function<BlockPos, Holder<Biome>> biomeAccessor,
        long seed,
        Aquifer aquifer,
        double x,
        double y,
        double z,
        float thickness,
        float yaw,
        float pitch,
        int branchIndex,
        int branchCount,
        double horizontalVerticalRatio,
        CarvingMask carvingMask
    ) {
        RandomSource randomSource = RandomSource.create(seed);
        float[] floats = this.initWidthFactors(context, config, randomSource);
        float f = 0.0F;
        float f1 = 0.0F;

        for (int i = branchIndex; i < branchCount; i++) {
            double d = 1.5 + Mth.sin(i * (float) Math.PI / branchCount) * thickness;
            double d1 = d * horizontalVerticalRatio;
            d *= config.shape.horizontalRadiusFactor.sample(randomSource);
            d1 = this.updateVerticalRadius(config, randomSource, d1, branchCount, i);
            float cos = Mth.cos(pitch);
            float sin = Mth.sin(pitch);
            x += Mth.cos(yaw) * cos;
            y += sin;
            z += Mth.sin(yaw) * cos;
            pitch *= 0.7F;
            pitch += f1 * 0.05F;
            yaw += f * 0.05F;
            f1 *= 0.8F;
            f *= 0.5F;
            f1 += (randomSource.nextFloat() - randomSource.nextFloat()) * randomSource.nextFloat() * 2.0F;
            f += (randomSource.nextFloat() - randomSource.nextFloat()) * randomSource.nextFloat() * 4.0F;
            if (randomSource.nextInt(4) != 0) {
                if (!canReach(chunk.getPos(), x, z, i, branchCount, thickness)) {
                    return;
                }

                this.carveEllipsoid(
                    context,
                    config,
                    chunk,
                    biomeAccessor,
                    aquifer,
                    x,
                    y,
                    z,
                    d,
                    d1,
                    carvingMask,
                    (skipContext, relativeX, relativeY, relativeZ, skipY) -> this.shouldSkip(skipContext, floats, relativeX, relativeY, relativeZ, skipY)
                );
            }
        }
    }

    private float[] initWidthFactors(CarvingContext context, CanyonCarverConfiguration config, RandomSource random) {
        int genDepth = context.getGenDepth();
        float[] floats = new float[genDepth];
        float f = 1.0F;

        for (int i = 0; i < genDepth; i++) {
            if (i == 0 || random.nextInt(config.shape.widthSmoothness) == 0) {
                f = 1.0F + random.nextFloat() * random.nextFloat();
            }

            floats[i] = f * f;
        }

        return floats;
    }

    private double updateVerticalRadius(CanyonCarverConfiguration config, RandomSource random, double verticalRadius, float branchCount, float currentBranch) {
        float f = 1.0F - Mth.abs(0.5F - currentBranch / branchCount) * 2.0F;
        float f1 = config.shape.verticalRadiusDefaultFactor + config.shape.verticalRadiusCenterFactor * f;
        return f1 * verticalRadius * Mth.randomBetween(random, 0.75F, 1.0F);
    }

    private boolean shouldSkip(CarvingContext context, float[] widthFactors, double relativeX, double relativeY, double relativeZ, int y) {
        int i = y - context.getMinGenY();
        return (relativeX * relativeX + relativeZ * relativeZ) * widthFactors[i - 1] + relativeY * relativeY / 6.0 >= 1.0;
    }
}
