package net.minecraft.world.level.levelgen.blending;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.ImmutableMap.Builder;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction8;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.data.worldgen.NoiseData;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.material.FluidState;
import org.apache.commons.lang3.mutable.MutableDouble;
import org.apache.commons.lang3.mutable.MutableObject;
import org.jspecify.annotations.Nullable;

public class Blender {
    private static final Blender EMPTY = new Blender(new Long2ObjectOpenHashMap(), new Long2ObjectOpenHashMap()) {
        @Override
        public Blender.BlendingOutput blendOffsetAndFactor(int x, int z) {
            return new Blender.BlendingOutput(1.0, 0.0);
        }

        @Override
        public double blendDensity(DensityFunction.FunctionContext context, double density) {
            return density;
        }

        @Override
        public BiomeResolver getBiomeResolver(BiomeResolver resolver) {
            return resolver;
        }
    };
    private static final NormalNoise SHIFT_NOISE = NormalNoise.create(new XoroshiroRandomSource(42L), NoiseData.DEFAULT_SHIFT);
    private static final int HEIGHT_BLENDING_RANGE_CELLS = QuartPos.fromSection(7) - 1;
    private static final int HEIGHT_BLENDING_RANGE_CHUNKS = QuartPos.toSection(HEIGHT_BLENDING_RANGE_CELLS + 3);
    private static final int DENSITY_BLENDING_RANGE_CELLS = 2;
    private static final int DENSITY_BLENDING_RANGE_CHUNKS = QuartPos.toSection(5);
    private static final double OLD_CHUNK_XZ_RADIUS = 8.0;
    private final Long2ObjectOpenHashMap<BlendingData> heightAndBiomeBlendingData;
    private final Long2ObjectOpenHashMap<BlendingData> densityBlendingData;

    public static Blender empty() {
        return EMPTY;
    }

    public static Blender of(@Nullable WorldGenRegion region) {
        if (!SharedConstants.DEBUG_DISABLE_BLENDING && region != null) {
            ChunkPos center = region.getCenter();
            if (!region.isOldChunkAround(center, HEIGHT_BLENDING_RANGE_CHUNKS)) {
                return EMPTY;
            } else {
                Long2ObjectOpenHashMap<BlendingData> map = new Long2ObjectOpenHashMap<>();
                Long2ObjectOpenHashMap<BlendingData> map1 = new Long2ObjectOpenHashMap<>();
                int squared = Mth.square(HEIGHT_BLENDING_RANGE_CHUNKS + 1);

                for (int i = -HEIGHT_BLENDING_RANGE_CHUNKS; i <= HEIGHT_BLENDING_RANGE_CHUNKS; i++) {
                    for (int i1 = -HEIGHT_BLENDING_RANGE_CHUNKS; i1 <= HEIGHT_BLENDING_RANGE_CHUNKS; i1++) {
                        if (i * i + i1 * i1 <= squared) {
                            int i2 = center.x + i;
                            int i3 = center.z + i1;
                            BlendingData orUpdateBlendingData = BlendingData.getOrUpdateBlendingData(region, i2, i3);
                            if (orUpdateBlendingData != null) {
                                map.put(ChunkPos.asLong(i2, i3), orUpdateBlendingData);
                                if (i >= -DENSITY_BLENDING_RANGE_CHUNKS
                                    && i <= DENSITY_BLENDING_RANGE_CHUNKS
                                    && i1 >= -DENSITY_BLENDING_RANGE_CHUNKS
                                    && i1 <= DENSITY_BLENDING_RANGE_CHUNKS) {
                                    map1.put(ChunkPos.asLong(i2, i3), orUpdateBlendingData);
                                }
                            }
                        }
                    }
                }

                return map.isEmpty() && map1.isEmpty() ? EMPTY : new Blender(map, map1);
            }
        } else {
            return EMPTY;
        }
    }

    Blender(Long2ObjectOpenHashMap<BlendingData> heightAndBiomeBlendingData, Long2ObjectOpenHashMap<BlendingData> densityBlendingData) {
        this.heightAndBiomeBlendingData = heightAndBiomeBlendingData;
        this.densityBlendingData = densityBlendingData;
    }

    public boolean isEmpty() {
        return this.heightAndBiomeBlendingData.isEmpty() && this.densityBlendingData.isEmpty();
    }

    public Blender.BlendingOutput blendOffsetAndFactor(int x, int z) {
        int quartPosX = QuartPos.fromBlock(x);
        int quartPosZ = QuartPos.fromBlock(z);
        double blendingDataValue = this.getBlendingDataValue(quartPosX, 0, quartPosZ, BlendingData::getHeight);
        if (blendingDataValue != Double.MAX_VALUE) {
            return new Blender.BlendingOutput(0.0, heightToOffset(blendingDataValue));
        } else {
            MutableDouble mutableDouble = new MutableDouble(0.0);
            MutableDouble mutableDouble1 = new MutableDouble(0.0);
            MutableDouble mutableDouble2 = new MutableDouble(Double.POSITIVE_INFINITY);
            this.heightAndBiomeBlendingData
                .forEach(
                    (l, blendingData) -> blendingData.iterateHeights(
                        QuartPos.fromSection(ChunkPos.getX(l)), QuartPos.fromSection(ChunkPos.getZ(l)), (x1, z1, height) -> {
                            double d2 = Mth.length((float)(quartPosX - x1), (float)(quartPosZ - z1));
                            if (!(d2 > HEIGHT_BLENDING_RANGE_CELLS)) {
                                if (d2 < mutableDouble2.doubleValue()) {
                                    mutableDouble2.setValue(d2);
                                }

                                double d3 = 1.0 / (d2 * d2 * d2 * d2);
                                mutableDouble1.add(height * d3);
                                mutableDouble.add(d3);
                            }
                        }
                    )
                );
            if (mutableDouble2.doubleValue() == Double.POSITIVE_INFINITY) {
                return new Blender.BlendingOutput(1.0, 0.0);
            } else {
                double d = mutableDouble1.doubleValue() / mutableDouble.doubleValue();
                double d1 = Mth.clamp(mutableDouble2.doubleValue() / (HEIGHT_BLENDING_RANGE_CELLS + 1), 0.0, 1.0);
                d1 = 3.0 * d1 * d1 - 2.0 * d1 * d1 * d1;
                return new Blender.BlendingOutput(d1, heightToOffset(d));
            }
        }
    }

    private static double heightToOffset(double height) {
        double d = 1.0;
        double d1 = height + 0.5;
        double d2 = Mth.positiveModulo(d1, 8.0);
        return 1.0 * (32.0 * (d1 - 128.0) - 3.0 * (d1 - 120.0) * d2 + 3.0 * d2 * d2) / (128.0 * (32.0 - 3.0 * d2));
    }

    public double blendDensity(DensityFunction.FunctionContext context, double density) {
        int quartPosX = QuartPos.fromBlock(context.blockX());
        int i = context.blockY() / 8;
        int quartPosZ = QuartPos.fromBlock(context.blockZ());
        double blendingDataValue = this.getBlendingDataValue(quartPosX, i, quartPosZ, BlendingData::getDensity);
        if (blendingDataValue != Double.MAX_VALUE) {
            return blendingDataValue;
        } else {
            MutableDouble mutableDouble = new MutableDouble(0.0);
            MutableDouble mutableDouble1 = new MutableDouble(0.0);
            MutableDouble mutableDouble2 = new MutableDouble(Double.POSITIVE_INFINITY);
            this.densityBlendingData
                .forEach(
                    (l, blendingData) -> blendingData.iterateDensities(
                        QuartPos.fromSection(ChunkPos.getX(l)), QuartPos.fromSection(ChunkPos.getZ(l)), i - 1, i + 1, (x, y, z, density1) -> {
                            double len = Mth.length(quartPosX - x, (i - y) * 2, quartPosZ - z);
                            if (!(len > 2.0)) {
                                if (len < mutableDouble2.doubleValue()) {
                                    mutableDouble2.setValue(len);
                                }

                                double d2 = 1.0 / (len * len * len * len);
                                mutableDouble1.add(density1 * d2);
                                mutableDouble.add(d2);
                            }
                        }
                    )
                );
            if (mutableDouble2.doubleValue() == Double.POSITIVE_INFINITY) {
                return density;
            } else {
                double d = mutableDouble1.doubleValue() / mutableDouble.doubleValue();
                double d1 = Mth.clamp(mutableDouble2.doubleValue() / 3.0, 0.0, 1.0);
                return Mth.lerp(d1, d, density);
            }
        }
    }

    private double getBlendingDataValue(int x, int y, int z, Blender.CellValueGetter getter) {
        int sectionPosX = QuartPos.toSection(x);
        int sectionPosZ = QuartPos.toSection(z);
        boolean flag = (x & 3) == 0;
        boolean flag1 = (z & 3) == 0;
        double blendingDataValue = this.getBlendingDataValue(getter, sectionPosX, sectionPosZ, x, y, z);
        if (blendingDataValue == Double.MAX_VALUE) {
            if (flag && flag1) {
                blendingDataValue = this.getBlendingDataValue(getter, sectionPosX - 1, sectionPosZ - 1, x, y, z);
            }

            if (blendingDataValue == Double.MAX_VALUE) {
                if (flag) {
                    blendingDataValue = this.getBlendingDataValue(getter, sectionPosX - 1, sectionPosZ, x, y, z);
                }

                if (blendingDataValue == Double.MAX_VALUE && flag1) {
                    blendingDataValue = this.getBlendingDataValue(getter, sectionPosX, sectionPosZ - 1, x, y, z);
                }
            }
        }

        return blendingDataValue;
    }

    private double getBlendingDataValue(Blender.CellValueGetter getter, int sectionX, int sectionZ, int x, int y, int z) {
        BlendingData blendingData = this.heightAndBiomeBlendingData.get(ChunkPos.asLong(sectionX, sectionZ));
        return blendingData != null ? getter.get(blendingData, x - QuartPos.fromSection(sectionX), y, z - QuartPos.fromSection(sectionZ)) : Double.MAX_VALUE;
    }

    public BiomeResolver getBiomeResolver(BiomeResolver resolver) {
        return (x, y, z, sampler) -> {
            Holder<Biome> holder = this.blendBiome(x, y, z);
            return holder == null ? resolver.getNoiseBiome(x, y, z, sampler) : holder;
        };
    }

    private Holder<Biome> blendBiome(int x, int y, int z) {
        MutableDouble mutableDouble = new MutableDouble(Double.POSITIVE_INFINITY);
        MutableObject<Holder<Biome>> mutableObject = new MutableObject<>();
        this.heightAndBiomeBlendingData
            .forEach(
                (l, blendingData) -> blendingData.iterateBiomes(
                    QuartPos.fromSection(ChunkPos.getX(l)), y, QuartPos.fromSection(ChunkPos.getZ(l)), (x1, z1, biome) -> {
                        double d2 = Mth.length((float)(x - x1), (float)(z - z1));
                        if (!(d2 > HEIGHT_BLENDING_RANGE_CELLS)) {
                            if (d2 < mutableDouble.doubleValue()) {
                                mutableObject.setValue(biome);
                                mutableDouble.setValue(d2);
                            }
                        }
                    }
                )
            );
        if (mutableDouble.doubleValue() == Double.POSITIVE_INFINITY) {
            return null;
        } else {
            double d = SHIFT_NOISE.getValue(x, 0.0, z) * 12.0;
            double d1 = Mth.clamp((mutableDouble.doubleValue() + d) / (HEIGHT_BLENDING_RANGE_CELLS + 1), 0.0, 1.0);
            return d1 > 0.5 ? null : mutableObject.get();
        }
    }

    public static void generateBorderTicks(WorldGenRegion region, ChunkAccess chunk) {
        if (!SharedConstants.DEBUG_DISABLE_BLENDING) {
            ChunkPos pos = chunk.getPos();
            boolean isOldNoiseGeneration = chunk.isOldNoiseGeneration();
            BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
            BlockPos blockPos = new BlockPos(pos.getMinBlockX(), 0, pos.getMinBlockZ());
            BlendingData blendingData = chunk.getBlendingData();
            if (blendingData != null) {
                int minY = blendingData.getAreaWithOldGeneration().getMinY();
                int maxY = blendingData.getAreaWithOldGeneration().getMaxY();
                if (isOldNoiseGeneration) {
                    for (int i = 0; i < 16; i++) {
                        for (int i1 = 0; i1 < 16; i1++) {
                            generateBorderTick(chunk, mutableBlockPos.setWithOffset(blockPos, i, minY - 1, i1));
                            generateBorderTick(chunk, mutableBlockPos.setWithOffset(blockPos, i, minY, i1));
                            generateBorderTick(chunk, mutableBlockPos.setWithOffset(blockPos, i, maxY, i1));
                            generateBorderTick(chunk, mutableBlockPos.setWithOffset(blockPos, i, maxY + 1, i1));
                        }
                    }
                }

                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    if (region.getChunk(pos.x + direction.getStepX(), pos.z + direction.getStepZ()).isOldNoiseGeneration() != isOldNoiseGeneration) {
                        int i2 = direction == Direction.EAST ? 15 : 0;
                        int i3 = direction == Direction.WEST ? 0 : 15;
                        int i4 = direction == Direction.SOUTH ? 15 : 0;
                        int i5 = direction == Direction.NORTH ? 0 : 15;

                        for (int i6 = i2; i6 <= i3; i6++) {
                            for (int i7 = i4; i7 <= i5; i7++) {
                                int i8 = Math.min(maxY, chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, i6, i7)) + 1;

                                for (int i9 = minY; i9 < i8; i9++) {
                                    generateBorderTick(chunk, mutableBlockPos.setWithOffset(blockPos, i6, i9, i7));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static void generateBorderTick(ChunkAccess chunk, BlockPos pos) {
        BlockState blockState = chunk.getBlockState(pos);
        if (blockState.is(BlockTags.LEAVES)) {
            chunk.markPosForPostprocessing(pos);
        }

        FluidState fluidState = chunk.getFluidState(pos);
        if (!fluidState.isEmpty()) {
            chunk.markPosForPostprocessing(pos);
        }
    }

    public static void addAroundOldChunksCarvingMaskFilter(WorldGenLevel level, ProtoChunk chunk) {
        if (!SharedConstants.DEBUG_DISABLE_BLENDING) {
            ChunkPos pos = chunk.getPos();
            Builder<Direction8, BlendingData> builder = ImmutableMap.builder();

            for (Direction8 direction8 : Direction8.values()) {
                int i = pos.x + direction8.getStepX();
                int i1 = pos.z + direction8.getStepZ();
                BlendingData blendingData = level.getChunk(i, i1).getBlendingData();
                if (blendingData != null) {
                    builder.put(direction8, blendingData);
                }
            }

            ImmutableMap<Direction8, BlendingData> map = builder.build();
            if (chunk.isOldNoiseGeneration() || !map.isEmpty()) {
                Blender.DistanceGetter distanceGetter = makeOldChunkDistanceGetter(chunk.getBlendingData(), map);
                CarvingMask.Mask mask = (x, y, z) -> {
                    double d = x + 0.5 + SHIFT_NOISE.getValue(x, y, z) * 4.0;
                    double d1 = y + 0.5 + SHIFT_NOISE.getValue(y, z, x) * 4.0;
                    double d2 = z + 0.5 + SHIFT_NOISE.getValue(z, x, y) * 4.0;
                    return distanceGetter.getDistance(d, d1, d2) < 4.0;
                };
                chunk.getOrCreateCarvingMask().setAdditionalMask(mask);
            }
        }
    }

    public static Blender.DistanceGetter makeOldChunkDistanceGetter(@Nullable BlendingData blendingData, Map<Direction8, BlendingData> surroundingBlendingData) {
        List<Blender.DistanceGetter> list = Lists.newArrayList();
        if (blendingData != null) {
            list.add(makeOffsetOldChunkDistanceGetter(null, blendingData));
        }

        surroundingBlendingData.forEach((direction8, blendingData1) -> list.add(makeOffsetOldChunkDistanceGetter(direction8, blendingData1)));
        return (x, y, z) -> {
            double d = Double.POSITIVE_INFINITY;

            for (Blender.DistanceGetter distanceGetter : list) {
                double distance = distanceGetter.getDistance(x, y, z);
                if (distance < d) {
                    d = distance;
                }
            }

            return d;
        };
    }

    private static Blender.DistanceGetter makeOffsetOldChunkDistanceGetter(@Nullable Direction8 direction, BlendingData blendingData) {
        double d = 0.0;
        double d1 = 0.0;
        if (direction != null) {
            for (Direction direction1 : direction.getDirections()) {
                d += direction1.getStepX() * 16;
                d1 += direction1.getStepZ() * 16;
            }
        }

        double d2 = d;
        double d3 = d1;
        double d4 = blendingData.getAreaWithOldGeneration().getHeight() / 2.0;
        double d5 = blendingData.getAreaWithOldGeneration().getMinY() + d4;
        return (z, d6, d7) -> distanceToCube(z - 8.0 - d2, d6 - d5, d7 - 8.0 - d3, 8.0, d4, 8.0);
    }

    private static double distanceToCube(double x1, double y1, double z1, double x2, double y2, double z2) {
        double d = Math.abs(x1) - x2;
        double d1 = Math.abs(y1) - y2;
        double d2 = Math.abs(z1) - z2;
        return Mth.length(Math.max(0.0, d), Math.max(0.0, d1), Math.max(0.0, d2));
    }

    public record BlendingOutput(double alpha, double blendingOffset) {
    }

    interface CellValueGetter {
        double get(BlendingData blendingData, int x, int y, int z);
    }

    public interface DistanceGetter {
        double getDistance(double x, double y, double z);
    }
}
