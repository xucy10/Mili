package net.minecraft.world.level.levelgen;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.BlockColumn;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public class SurfaceSystem {
    private static final BlockState WHITE_TERRACOTTA = Blocks.WHITE_TERRACOTTA.defaultBlockState();
    private static final BlockState ORANGE_TERRACOTTA = Blocks.ORANGE_TERRACOTTA.defaultBlockState();
    private static final BlockState TERRACOTTA = Blocks.TERRACOTTA.defaultBlockState();
    private static final BlockState YELLOW_TERRACOTTA = Blocks.YELLOW_TERRACOTTA.defaultBlockState();
    private static final BlockState BROWN_TERRACOTTA = Blocks.BROWN_TERRACOTTA.defaultBlockState();
    private static final BlockState RED_TERRACOTTA = Blocks.RED_TERRACOTTA.defaultBlockState();
    private static final BlockState LIGHT_GRAY_TERRACOTTA = Blocks.LIGHT_GRAY_TERRACOTTA.defaultBlockState();
    private static final BlockState PACKED_ICE = Blocks.PACKED_ICE.defaultBlockState();
    private static final BlockState SNOW_BLOCK = Blocks.SNOW_BLOCK.defaultBlockState();
    private final BlockState defaultBlock;
    private final int seaLevel;
    private final BlockState[] clayBands;
    private final NormalNoise clayBandsOffsetNoise;
    private final NormalNoise badlandsPillarNoise;
    private final NormalNoise badlandsPillarRoofNoise;
    private final NormalNoise badlandsSurfaceNoise;
    private final NormalNoise icebergPillarNoise;
    private final NormalNoise icebergPillarRoofNoise;
    private final NormalNoise icebergSurfaceNoise;
    private final PositionalRandomFactory noiseRandom;
    private final NormalNoise surfaceNoise;
    private final NormalNoise surfaceSecondaryNoise;

    public SurfaceSystem(RandomState randomState, BlockState defaultBlock, int seaLevel, PositionalRandomFactory noiseRandom) {
        this.defaultBlock = defaultBlock;
        this.seaLevel = seaLevel;
        this.noiseRandom = noiseRandom;
        this.clayBandsOffsetNoise = randomState.getOrCreateNoise(Noises.CLAY_BANDS_OFFSET);
        this.clayBands = generateBands(noiseRandom.fromHashOf(Identifier.withDefaultNamespace("clay_bands")));
        this.surfaceNoise = randomState.getOrCreateNoise(Noises.SURFACE);
        this.surfaceSecondaryNoise = randomState.getOrCreateNoise(Noises.SURFACE_SECONDARY);
        this.badlandsPillarNoise = randomState.getOrCreateNoise(Noises.BADLANDS_PILLAR);
        this.badlandsPillarRoofNoise = randomState.getOrCreateNoise(Noises.BADLANDS_PILLAR_ROOF);
        this.badlandsSurfaceNoise = randomState.getOrCreateNoise(Noises.BADLANDS_SURFACE);
        this.icebergPillarNoise = randomState.getOrCreateNoise(Noises.ICEBERG_PILLAR);
        this.icebergPillarRoofNoise = randomState.getOrCreateNoise(Noises.ICEBERG_PILLAR_ROOF);
        this.icebergSurfaceNoise = randomState.getOrCreateNoise(Noises.ICEBERG_SURFACE);
    }

    public void buildSurface(
        RandomState randomState,
        BiomeManager biomeManager,
        Registry<Biome> biomes,
        boolean useLegacyRandomSource,
        WorldGenerationContext context,
        final ChunkAccess chunk,
        NoiseChunk noiseChunk,
        SurfaceRules.RuleSource ruleSource
    ) {
        final BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        final ChunkPos pos = chunk.getPos();
        int minBlockX = pos.getMinBlockX();
        int minBlockZ = pos.getMinBlockZ();
        BlockColumn blockColumn = new BlockColumn() {
            @Override
            public BlockState getBlock(int pos1) {
                return chunk.getBlockState(mutableBlockPos.setY(pos1));
            }

            @Override
            public void setBlock(int pos1, BlockState state) {
                LevelHeightAccessor heightAccessorForGeneration = chunk.getHeightAccessorForGeneration();
                if (heightAccessorForGeneration.isInsideBuildHeight(pos1)) {
                    chunk.setBlockState(mutableBlockPos.setY(pos1), state);
                    if (!state.getFluidState().isEmpty()) {
                        chunk.markPosForPostprocessing(mutableBlockPos);
                    }
                }
            }

            @Override
            public String toString() {
                return "ChunkBlockColumn " + pos;
            }
        };
        SurfaceRules.Context context1 = new SurfaceRules.Context(this, randomState, chunk, noiseChunk, biomeManager::getBiome, biomes, context);
        SurfaceRules.SurfaceRule surfaceRule = ruleSource.apply(context1);
        BlockPos.MutableBlockPos mutableBlockPos1 = new BlockPos.MutableBlockPos();

        for (int i = 0; i < 16; i++) {
            for (int i1 = 0; i1 < 16; i1++) {
                int i2 = minBlockX + i;
                int i3 = minBlockZ + i1;
                int i4 = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, i, i1) + 1;
                mutableBlockPos.setX(i2).setZ(i3);
                Holder<Biome> biome = biomeManager.getBiome(mutableBlockPos1.set(i2, useLegacyRandomSource ? 0 : i4, i3));
                if (biome.is(Biomes.ERODED_BADLANDS)) {
                    this.erodedBadlandsExtension(blockColumn, i2, i3, i4, chunk);
                }

                int i5 = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, i, i1) + 1;
                context1.updateXZ(i2, i3);
                int i6 = 0;
                int i7 = Integer.MIN_VALUE;
                int i8 = Integer.MAX_VALUE;
                int minY = chunk.getMinY();

                for (int i9 = i5; i9 >= minY; i9--) {
                    BlockState block = blockColumn.getBlock(i9);
                    if (block.isAir()) {
                        i6 = 0;
                        i7 = Integer.MIN_VALUE;
                    } else if (!block.getFluidState().isEmpty()) {
                        if (i7 == Integer.MIN_VALUE) {
                            i7 = i9 + 1;
                        }
                    } else {
                        if (i8 >= i9) {
                            i8 = DimensionType.WAY_BELOW_MIN_Y;

                            for (int i10 = i9 - 1; i10 >= minY - 1; i10--) {
                                BlockState block1 = blockColumn.getBlock(i10);
                                if (!this.isStone(block1)) {
                                    i8 = i10 + 1;
                                    break;
                                }
                            }
                        }

                        i6++;
                        int i10x = i9 - i8 + 1;
                        context1.updateY(i6, i10x, i7, i2, i9, i3);
                        if (block == this.defaultBlock) {
                            BlockState block1 = surfaceRule.tryApply(i2, i9, i3);
                            if (block1 != null) {
                                blockColumn.setBlock(i9, block1);
                            }
                        }
                    }
                }

                if (biome.is(Biomes.FROZEN_OCEAN) || biome.is(Biomes.DEEP_FROZEN_OCEAN)) {
                    this.frozenOceanExtension(context1.getMinSurfaceLevel(), biome.value(), blockColumn, mutableBlockPos1, i2, i3, i4);
                }
            }
        }
    }

    protected int getSurfaceDepth(int x, int z) {
        double value = this.surfaceNoise.getValue(x, 0.0, z);
        return (int)(value * 2.75 + 3.0 + this.noiseRandom.at(x, 0, z).nextDouble() * 0.25);
    }

    protected double getSurfaceSecondary(int x, int z) {
        return this.surfaceSecondaryNoise.getValue(x, 0.0, z);
    }

    private boolean isStone(BlockState state) {
        return !state.isAir() && state.getFluidState().isEmpty();
    }

    public int getSeaLevel() {
        return this.seaLevel;
    }

    @Deprecated
    public Optional<BlockState> topMaterial(
        SurfaceRules.RuleSource rule,
        CarvingContext context,
        Function<BlockPos, Holder<Biome>> biomeGetter,
        ChunkAccess chunk,
        NoiseChunk noiseChunk,
        BlockPos pos,
        boolean hasFluid
    ) {
        SurfaceRules.Context context1 = new SurfaceRules.Context(
            this, context.randomState(), chunk, noiseChunk, biomeGetter, context.registryAccess().lookupOrThrow(Registries.BIOME), context
        );
        SurfaceRules.SurfaceRule surfaceRule = rule.apply(context1);
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        context1.updateXZ(x, z);
        context1.updateY(1, 1, hasFluid ? y + 1 : Integer.MIN_VALUE, x, y, z);
        BlockState blockState = surfaceRule.tryApply(x, y, z);
        return Optional.ofNullable(blockState);
    }

    private void erodedBadlandsExtension(BlockColumn blockColumn, int x, int z, int height, LevelHeightAccessor level) {
        double d = 0.2;
        double min = Math.min(Math.abs(this.badlandsSurfaceNoise.getValue(x, 0.0, z) * 8.25), this.badlandsPillarNoise.getValue(x * 0.2, 0.0, z * 0.2) * 15.0);
        if (!(min <= 0.0)) {
            double d1 = 0.75;
            double d2 = 1.5;
            double abs = Math.abs(this.badlandsPillarRoofNoise.getValue(x * 0.75, 0.0, z * 0.75) * 1.5);
            double d3 = 64.0 + Math.min(min * min * 2.5, Math.ceil(abs * 50.0) + 24.0);
            int floor = Mth.floor(d3);
            if (height <= floor) {
                for (int i = floor; i >= level.getMinY(); i--) {
                    BlockState block = blockColumn.getBlock(i);
                    if (block.is(this.defaultBlock.getBlock())) {
                        break;
                    }

                    if (block.is(Blocks.WATER)) {
                        return;
                    }
                }

                for (int i = floor; i >= level.getMinY() && blockColumn.getBlock(i).isAir(); i--) {
                    blockColumn.setBlock(i, this.defaultBlock);
                }
            }
        }
    }

    private void frozenOceanExtension(int minSurfaceLevel, Biome biome, BlockColumn blockColumn, BlockPos.MutableBlockPos topWaterPos, int x, int z, int height) {
        double d = 1.28;
        double min = Math.min(Math.abs(this.icebergSurfaceNoise.getValue(x, 0.0, z) * 8.25), this.icebergPillarNoise.getValue(x * 1.28, 0.0, z * 1.28) * 15.0);
        if (!(min <= 1.8)) {
            double d1 = 1.17;
            double d2 = 1.5;
            double abs = Math.abs(this.icebergPillarRoofNoise.getValue(x * 1.17, 0.0, z * 1.17) * 1.5);
            double min1 = Math.min(min * min * 1.2, Math.ceil(abs * 40.0) + 14.0);
            if (biome.shouldMeltFrozenOceanIcebergSlightly(topWaterPos.set(x, this.seaLevel, z), this.seaLevel)) {
                min1 -= 2.0;
            }

            double d3;
            if (min1 > 2.0) {
                d3 = this.seaLevel - min1 - 7.0;
                min1 += this.seaLevel;
            } else {
                min1 = 0.0;
                d3 = 0.0;
            }

            double d4 = min1;
            RandomSource randomSource = this.noiseRandom.at(x, 0, z);
            int i = 2 + randomSource.nextInt(4);
            int i1 = this.seaLevel + 18 + randomSource.nextInt(10);
            int i2 = 0;

            for (int max = Math.max(height, (int)min1 + 1); max >= minSurfaceLevel; max--) {
                if (blockColumn.getBlock(max).isAir() && max < (int)d4 && randomSource.nextDouble() > 0.01
                    || blockColumn.getBlock(max).is(Blocks.WATER) && max > (int)d3 && max < this.seaLevel && d3 != 0.0 && randomSource.nextDouble() > 0.15) {
                    if (i2 <= i && max > i1) {
                        blockColumn.setBlock(max, SNOW_BLOCK);
                        i2++;
                    } else {
                        blockColumn.setBlock(max, PACKED_ICE);
                    }
                }
            }
        }
    }

    private static BlockState[] generateBands(RandomSource random) {
        BlockState[] blockStates = new BlockState[192];
        Arrays.fill(blockStates, TERRACOTTA);

        for (int i = 0; i < blockStates.length; i++) {
            i += random.nextInt(5) + 1;
            if (i < blockStates.length) {
                blockStates[i] = ORANGE_TERRACOTTA;
            }
        }

        makeBands(random, blockStates, 1, YELLOW_TERRACOTTA);
        makeBands(random, blockStates, 2, BROWN_TERRACOTTA);
        makeBands(random, blockStates, 1, RED_TERRACOTTA);
        int ix = random.nextIntBetweenInclusive(9, 15);
        int i1 = 0;

        for (int i2 = 0; i1 < ix && i2 < blockStates.length; i2 += random.nextInt(16) + 4) {
            blockStates[i2] = WHITE_TERRACOTTA;
            if (i2 - 1 > 0 && random.nextBoolean()) {
                blockStates[i2 - 1] = LIGHT_GRAY_TERRACOTTA;
            }

            if (i2 + 1 < blockStates.length && random.nextBoolean()) {
                blockStates[i2 + 1] = LIGHT_GRAY_TERRACOTTA;
            }

            i1++;
        }

        return blockStates;
    }

    private static void makeBands(RandomSource random, BlockState[] output, int minSize, BlockState state) {
        int randomInt = random.nextIntBetweenInclusive(6, 15);

        for (int i = 0; i < randomInt; i++) {
            int i1 = minSize + random.nextInt(3);
            int randomInt1 = random.nextInt(output.length);

            for (int i2 = 0; randomInt1 + i2 < output.length && i2 < i1; i2++) {
                output[randomInt1 + i2] = state;
            }
        }
    }

    protected BlockState getBand(int x, int y, int z) {
        int i = (int)Math.round(this.clayBandsOffsetNoise.getValue(x, 0.0, z) * 4.0);
        return this.clayBands[(y + i + this.clayBands.length) % this.clayBands.length];
    }
}
