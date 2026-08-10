package net.minecraft.world.level.levelgen.carver;

import com.mojang.serialization.Codec;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;

public class CaveWorldCarver extends WorldCarver<CaveCarverConfiguration> {
    public CaveWorldCarver(Codec<CaveCarverConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean isStartChunk(CaveCarverConfiguration config, RandomSource random) {
        return random.nextFloat() <= config.probability;
    }

    @Override
    public boolean carve(
        CarvingContext context,
        CaveCarverConfiguration config,
        ChunkAccess chunk,
        Function<BlockPos, Holder<Biome>> biomeAccessor,
        RandomSource random,
        Aquifer aquifer,
        ChunkPos chunkPos,
        CarvingMask carvingMask
    ) {
        int blockPosCoord = SectionPos.sectionToBlockCoord(this.getRange() * 2 - 1);
        int randomInt = random.nextInt(random.nextInt(random.nextInt(this.getCaveBound()) + 1) + 1);

        for (int i = 0; i < randomInt; i++) {
            double d = chunkPos.getBlockX(random.nextInt(16));
            double d1 = config.y.sample(random, context);
            double d2 = chunkPos.getBlockZ(random.nextInt(16));
            double d3 = config.horizontalRadiusMultiplier.sample(random);
            double d4 = config.verticalRadiusMultiplier.sample(random);
            double d5 = config.floorLevel.sample(random);
            WorldCarver.CarveSkipChecker carveSkipChecker = (skipContext, relativeX, relativeY, relativeZ, y) -> shouldSkip(relativeX, relativeY, relativeZ, d5);
            int i1 = 1;
            if (random.nextInt(4) == 0) {
                double d6 = config.yScale.sample(random);
                float f = 1.0F + random.nextFloat() * 6.0F;
                this.createRoom(context, config, chunk, biomeAccessor, aquifer, d, d1, d2, f, d6, carvingMask, carveSkipChecker);
                i1 += random.nextInt(4);
            }

            for (int i2 = 0; i2 < i1; i2++) {
                float f1 = random.nextFloat() * (float) (Math.PI * 2);
                float f = (random.nextFloat() - 0.5F) / 4.0F;
                float thickness = this.getThickness(random);
                int i3 = blockPosCoord - random.nextInt(blockPosCoord / 4);
                int i4 = 0;
                this.createTunnel(
                    context,
                    config,
                    chunk,
                    biomeAccessor,
                    random.nextLong(),
                    aquifer,
                    d,
                    d1,
                    d2,
                    d3,
                    d4,
                    thickness,
                    f1,
                    f,
                    0,
                    i3,
                    this.getYScale(),
                    carvingMask,
                    carveSkipChecker
                );
            }
        }

        return true;
    }

    protected int getCaveBound() {
        return 15;
    }

    protected float getThickness(RandomSource random) {
        float f = random.nextFloat() * 2.0F + random.nextFloat();
        if (random.nextInt(10) == 0) {
            f *= random.nextFloat() * random.nextFloat() * 3.0F + 1.0F;
        }

        return f;
    }

    protected double getYScale() {
        return 1.0;
    }

    protected void createRoom(
        CarvingContext context,
        CaveCarverConfiguration config,
        ChunkAccess chunk,
        Function<BlockPos, Holder<Biome>> biomeAccessor,
        Aquifer aquifer,
        double x,
        double y,
        double z,
        float radius,
        double horizontalVerticalRatio,
        CarvingMask carvingMask,
        WorldCarver.CarveSkipChecker skipChecker
    ) {
        double d = 1.5 + Mth.sin((float) (Math.PI / 2)) * radius;
        double d1 = d * horizontalVerticalRatio;
        this.carveEllipsoid(context, config, chunk, biomeAccessor, aquifer, x + 1.0, y, z, d, d1, carvingMask, skipChecker);
    }

    protected void createTunnel(
        CarvingContext context,
        CaveCarverConfiguration config,
        ChunkAccess chunk,
        Function<BlockPos, Holder<Biome>> biomeAccessor,
        long seed,
        Aquifer aquifer,
        double x,
        double y,
        double z,
        double horizontalRadiusMultiplier,
        double verticalRadiusMultiplier,
        float thickness,
        float yaw,
        float pitch,
        int branchIndex,
        int branchCount,
        double horizontalVerticalRatio,
        CarvingMask carvingMask,
        WorldCarver.CarveSkipChecker skipChecker
    ) {
        RandomSource randomSource = RandomSource.create(seed);
        int i = randomSource.nextInt(branchCount / 2) + branchCount / 4;
        boolean flag = randomSource.nextInt(6) == 0;
        float f = 0.0F;
        float f1 = 0.0F;

        for (int i1 = branchIndex; i1 < branchCount; i1++) {
            double d = 1.5 + Mth.sin((float) Math.PI * i1 / branchCount) * thickness;
            double d1 = d * horizontalVerticalRatio;
            float cos = Mth.cos(pitch);
            x += Mth.cos(yaw) * cos;
            y += Mth.sin(pitch);
            z += Mth.sin(yaw) * cos;
            pitch *= flag ? 0.92F : 0.7F;
            pitch += f1 * 0.1F;
            yaw += f * 0.1F;
            f1 *= 0.9F;
            f *= 0.75F;
            f1 += (randomSource.nextFloat() - randomSource.nextFloat()) * randomSource.nextFloat() * 2.0F;
            f += (randomSource.nextFloat() - randomSource.nextFloat()) * randomSource.nextFloat() * 4.0F;
            if (i1 == i && thickness > 1.0F) {
                this.createTunnel(
                    context,
                    config,
                    chunk,
                    biomeAccessor,
                    randomSource.nextLong(),
                    aquifer,
                    x,
                    y,
                    z,
                    horizontalRadiusMultiplier,
                    verticalRadiusMultiplier,
                    randomSource.nextFloat() * 0.5F + 0.5F,
                    yaw - (float) (Math.PI / 2),
                    pitch / 3.0F,
                    i1,
                    branchCount,
                    1.0,
                    carvingMask,
                    skipChecker
                );
                this.createTunnel(
                    context,
                    config,
                    chunk,
                    biomeAccessor,
                    randomSource.nextLong(),
                    aquifer,
                    x,
                    y,
                    z,
                    horizontalRadiusMultiplier,
                    verticalRadiusMultiplier,
                    randomSource.nextFloat() * 0.5F + 0.5F,
                    yaw + (float) (Math.PI / 2),
                    pitch / 3.0F,
                    i1,
                    branchCount,
                    1.0,
                    carvingMask,
                    skipChecker
                );
                return;
            }

            if (randomSource.nextInt(4) != 0) {
                if (!canReach(chunk.getPos(), x, z, i1, branchCount, thickness)) {
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
                    d * horizontalRadiusMultiplier,
                    d1 * verticalRadiusMultiplier,
                    carvingMask,
                    skipChecker
                );
            }
        }
    }

    private static boolean shouldSkip(double relative, double relativeY, double relativeZ, double minrelativeY) {
        return relativeY <= minrelativeY || relative * relative + relativeY * relativeY + relativeZ * relativeZ >= 1.0;
    }
}
