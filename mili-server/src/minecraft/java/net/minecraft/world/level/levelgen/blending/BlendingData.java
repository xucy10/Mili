package net.minecraft.world.level.levelgen.blending;

import com.google.common.primitives.Doubles;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.doubles.DoubleArrays;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction8;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

public class BlendingData {
    private static final double BLENDING_DENSITY_FACTOR = 0.1;
    protected static final int CELL_WIDTH = 4;
    protected static final int CELL_HEIGHT = 8;
    protected static final int CELL_RATIO = 2;
    private static final double SOLID_DENSITY = 1.0;
    private static final double AIR_DENSITY = -1.0;
    private static final int CELLS_PER_SECTION_Y = 2;
    private static final int QUARTS_PER_SECTION = QuartPos.fromBlock(16);
    private static final int CELL_HORIZONTAL_MAX_INDEX_INSIDE = QUARTS_PER_SECTION - 1;
    private static final int CELL_HORIZONTAL_MAX_INDEX_OUTSIDE = QUARTS_PER_SECTION;
    private static final int CELL_COLUMN_INSIDE_COUNT = 2 * CELL_HORIZONTAL_MAX_INDEX_INSIDE + 1;
    private static final int CELL_COLUMN_OUTSIDE_COUNT = 2 * CELL_HORIZONTAL_MAX_INDEX_OUTSIDE + 1;
    static final int CELL_COLUMN_COUNT = CELL_COLUMN_INSIDE_COUNT + CELL_COLUMN_OUTSIDE_COUNT;
    private final LevelHeightAccessor areaWithOldGeneration;
    private static final List<Block> SURFACE_BLOCKS = List.of(
        Blocks.PODZOL,
        Blocks.GRAVEL,
        Blocks.GRASS_BLOCK,
        Blocks.STONE,
        Blocks.COARSE_DIRT,
        Blocks.SAND,
        Blocks.RED_SAND,
        Blocks.MYCELIUM,
        Blocks.SNOW_BLOCK,
        Blocks.TERRACOTTA,
        Blocks.DIRT
    );
    protected static final double NO_VALUE = Double.MAX_VALUE;
    private boolean hasCalculatedData;
    private final double[] heights;
    private final List<@Nullable List<@Nullable Holder<Biome>>> biomes;
    private final transient double[][] densities;

    private BlendingData(int sectionX, int sectionZ, Optional<double[]> heights) {
        this.heights = heights.orElseGet(() -> Util.make(new double[CELL_COLUMN_COUNT], doubles -> Arrays.fill(doubles, Double.MAX_VALUE)));
        this.densities = new double[CELL_COLUMN_COUNT][];
        ObjectArrayList<List<Holder<Biome>>> list = new ObjectArrayList<>(CELL_COLUMN_COUNT);
        list.size(CELL_COLUMN_COUNT);
        this.biomes = list;
        int blockPosX = SectionPos.sectionToBlockCoord(sectionX);
        int i = SectionPos.sectionToBlockCoord(sectionZ) - blockPosX;
        this.areaWithOldGeneration = LevelHeightAccessor.create(blockPosX, i);
    }

    public static @Nullable BlendingData unpack(BlendingData.@Nullable Packed packed) {
        return packed == null ? null : new BlendingData(packed.minSection(), packed.maxSection(), packed.heights());
    }

    public BlendingData.Packed pack() {
        boolean flag = false;

        for (double d : this.heights) {
            if (d != Double.MAX_VALUE) {
                flag = true;
                break;
            }
        }

        return new BlendingData.Packed(
            this.areaWithOldGeneration.getMinSectionY(),
            this.areaWithOldGeneration.getMaxSectionY() + 1,
            flag ? Optional.of(DoubleArrays.copy(this.heights)) : Optional.empty()
        );
    }

    public static @Nullable BlendingData getOrUpdateBlendingData(WorldGenRegion region, int chunkX, int chunkZ) {
        ChunkAccess chunk = region.getChunk(chunkX, chunkZ);
        BlendingData blendingData = chunk.getBlendingData();
        if (blendingData != null && !chunk.getHighestGeneratedStatus().isBefore(ChunkStatus.BIOMES)) {
            blendingData.calculateData(chunk, sideByGenerationAge(region, chunkX, chunkZ, false));
            return blendingData;
        } else {
            return null;
        }
    }

    public static Set<Direction8> sideByGenerationAge(WorldGenLevel level, int chunkX, int chunkZ, boolean oldNoiseGeneration) {
        Set<Direction8> set = EnumSet.noneOf(Direction8.class);

        for (Direction8 direction8 : Direction8.values()) {
            int i = chunkX + direction8.getStepX();
            int i1 = chunkZ + direction8.getStepZ();
            if (level.getChunk(i, i1).isOldNoiseGeneration() == oldNoiseGeneration) {
                set.add(direction8);
            }
        }

        return set;
    }

    private void calculateData(ChunkAccess chunk, Set<Direction8> directions) {
        if (!this.hasCalculatedData) {
            if (directions.contains(Direction8.NORTH) || directions.contains(Direction8.WEST) || directions.contains(Direction8.NORTH_WEST)) {
                this.addValuesForColumn(getInsideIndex(0, 0), chunk, 0, 0);
            }

            if (directions.contains(Direction8.NORTH)) {
                for (int i = 1; i < QUARTS_PER_SECTION; i++) {
                    this.addValuesForColumn(getInsideIndex(i, 0), chunk, 4 * i, 0);
                }
            }

            if (directions.contains(Direction8.WEST)) {
                for (int i = 1; i < QUARTS_PER_SECTION; i++) {
                    this.addValuesForColumn(getInsideIndex(0, i), chunk, 0, 4 * i);
                }
            }

            if (directions.contains(Direction8.EAST)) {
                for (int i = 1; i < QUARTS_PER_SECTION; i++) {
                    this.addValuesForColumn(getOutsideIndex(CELL_HORIZONTAL_MAX_INDEX_OUTSIDE, i), chunk, 15, 4 * i);
                }
            }

            if (directions.contains(Direction8.SOUTH)) {
                for (int i = 0; i < QUARTS_PER_SECTION; i++) {
                    this.addValuesForColumn(getOutsideIndex(i, CELL_HORIZONTAL_MAX_INDEX_OUTSIDE), chunk, 4 * i, 15);
                }
            }

            if (directions.contains(Direction8.EAST) && directions.contains(Direction8.NORTH_EAST)) {
                this.addValuesForColumn(getOutsideIndex(CELL_HORIZONTAL_MAX_INDEX_OUTSIDE, 0), chunk, 15, 0);
            }

            if (directions.contains(Direction8.EAST) && directions.contains(Direction8.SOUTH) && directions.contains(Direction8.SOUTH_EAST)) {
                this.addValuesForColumn(getOutsideIndex(CELL_HORIZONTAL_MAX_INDEX_OUTSIDE, CELL_HORIZONTAL_MAX_INDEX_OUTSIDE), chunk, 15, 15);
            }

            this.hasCalculatedData = true;
        }
    }

    private void addValuesForColumn(int index, ChunkAccess chunk, int x, int z) {
        if (this.heights[index] == Double.MAX_VALUE) {
            this.heights[index] = this.getHeightAtXZ(chunk, x, z);
        }

        this.densities[index] = this.getDensityColumn(chunk, x, z, Mth.floor(this.heights[index]));
        this.biomes.set(index, this.getBiomeColumn(chunk, x, z));
    }

    private int getHeightAtXZ(ChunkAccess chunk, int x, int z) {
        int min;
        if (chunk.hasPrimedHeightmap(Heightmap.Types.WORLD_SURFACE_WG)) {
            min = Math.min(chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z), this.areaWithOldGeneration.getMaxY());
        } else {
            min = this.areaWithOldGeneration.getMaxY();
        }

        int minY = this.areaWithOldGeneration.getMinY();
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos(x, min, z);

        while (mutableBlockPos.getY() > minY) {
            if (SURFACE_BLOCKS.contains(chunk.getBlockState(mutableBlockPos).getBlock())) {
                return mutableBlockPos.getY();
            }

            mutableBlockPos.move(Direction.DOWN);
        }

        return minY;
    }

    private static double read1(ChunkAccess chunk, BlockPos.MutableBlockPos pos) {
        return isGround(chunk, pos.move(Direction.DOWN)) ? 1.0 : -1.0;
    }

    private static double read7(ChunkAccess chunk, BlockPos.MutableBlockPos pos) {
        double d = 0.0;

        for (int i = 0; i < 7; i++) {
            d += read1(chunk, pos);
        }

        return d;
    }

    private double[] getDensityColumn(ChunkAccess chunk, int x, int z, int height) {
        double[] doubles = new double[this.cellCountPerColumn()];
        Arrays.fill(doubles, -1.0);
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos(x, this.areaWithOldGeneration.getMaxY() + 1, z);
        double d = read7(chunk, mutableBlockPos);

        for (int i = doubles.length - 2; i >= 0; i--) {
            double d1 = read1(chunk, mutableBlockPos);
            double d2 = read7(chunk, mutableBlockPos);
            doubles[i] = (d + d1 + d2) / 15.0;
            d = d2;
        }

        int i = this.getCellYIndex(Mth.floorDiv(height, 8));
        if (i >= 0 && i < doubles.length - 1) {
            double d1 = (height + 0.5) % 8.0 / 8.0;
            double d2 = (1.0 - d1) / d1;
            double d3 = Math.max(d2, 1.0) * 0.25;
            doubles[i + 1] = -d2 / d3;
            doubles[i] = 1.0 / d3;
        }

        return doubles;
    }

    private List<Holder<Biome>> getBiomeColumn(ChunkAccess chunk, int x, int z) {
        ObjectArrayList<Holder<Biome>> list = new ObjectArrayList<>(this.quartCountPerColumn());
        list.size(this.quartCountPerColumn());

        for (int i = 0; i < list.size(); i++) {
            int i1 = i + QuartPos.fromBlock(this.areaWithOldGeneration.getMinY());
            list.set(i, chunk.getNoiseBiome(QuartPos.fromBlock(x), i1, QuartPos.fromBlock(z)));
        }

        return list;
    }

    private static boolean isGround(ChunkAccess chunk, BlockPos pos) {
        BlockState blockState = chunk.getBlockState(pos);
        return !blockState.isAir()
            && !blockState.is(BlockTags.LEAVES)
            && !blockState.is(BlockTags.LOGS)
            && !blockState.is(Blocks.BROWN_MUSHROOM_BLOCK)
            && !blockState.is(Blocks.RED_MUSHROOM_BLOCK)
            && !blockState.getCollisionShape(chunk, pos).isEmpty();
    }

    protected double getHeight(int x, int y, int z) {
        if (x == CELL_HORIZONTAL_MAX_INDEX_OUTSIDE || z == CELL_HORIZONTAL_MAX_INDEX_OUTSIDE) {
            return this.heights[getOutsideIndex(x, z)];
        } else {
            return x != 0 && z != 0 ? Double.MAX_VALUE : this.heights[getInsideIndex(x, z)];
        }
    }

    private double getDensity(double @Nullable [] heights, int y) {
        if (heights == null) {
            return Double.MAX_VALUE;
        } else {
            int cellYIndex = this.getCellYIndex(y);
            return cellYIndex >= 0 && cellYIndex < heights.length ? heights[cellYIndex] * 0.1 : Double.MAX_VALUE;
        }
    }

    protected double getDensity(int x, int y, int z) {
        if (y == this.getMinY()) {
            return 0.1;
        } else if (x == CELL_HORIZONTAL_MAX_INDEX_OUTSIDE || z == CELL_HORIZONTAL_MAX_INDEX_OUTSIDE) {
            return this.getDensity(this.densities[getOutsideIndex(x, z)], y);
        } else {
            return x != 0 && z != 0 ? Double.MAX_VALUE : this.getDensity(this.densities[getInsideIndex(x, z)], y);
        }
    }

    protected void iterateBiomes(int x, int y, int z, BlendingData.BiomeConsumer consumer) {
        if (y >= QuartPos.fromBlock(this.areaWithOldGeneration.getMinY()) && y <= QuartPos.fromBlock(this.areaWithOldGeneration.getMaxY())) {
            int i = y - QuartPos.fromBlock(this.areaWithOldGeneration.getMinY());

            for (int i1 = 0; i1 < this.biomes.size(); i1++) {
                List<Holder<Biome>> list = this.biomes.get(i1);
                if (list != null) {
                    Holder<Biome> holder = list.get(i);
                    if (holder != null) {
                        consumer.consume(x + getX(i1), z + getZ(i1), holder);
                    }
                }
            }
        }
    }

    protected void iterateHeights(int x, int z, BlendingData.HeightConsumer consumer) {
        for (int i = 0; i < this.heights.length; i++) {
            double d = this.heights[i];
            if (d != Double.MAX_VALUE) {
                consumer.consume(x + getX(i), z + getZ(i), d);
            }
        }
    }

    protected void iterateDensities(int x, int z, int minY, int maxY, BlendingData.DensityConsumer consumer) {
        int columnMinY = this.getColumnMinY();
        int max = Math.max(0, minY - columnMinY);
        int min = Math.min(this.cellCountPerColumn(), maxY - columnMinY);

        for (int i = 0; i < this.densities.length; i++) {
            double[] doubles = this.densities[i];
            if (doubles != null) {
                int i1 = x + getX(i);
                int i2 = z + getZ(i);

                for (int i3 = max; i3 < min; i3++) {
                    consumer.consume(i1, i3 + columnMinY, i2, doubles[i3] * 0.1);
                }
            }
        }
    }

    private int cellCountPerColumn() {
        return this.areaWithOldGeneration.getSectionsCount() * 2;
    }

    private int quartCountPerColumn() {
        return QuartPos.fromSection(this.areaWithOldGeneration.getSectionsCount());
    }

    private int getColumnMinY() {
        return this.getMinY() + 1;
    }

    private int getMinY() {
        return this.areaWithOldGeneration.getMinSectionY() * 2;
    }

    private int getCellYIndex(int y) {
        return y - this.getColumnMinY();
    }

    private static int getInsideIndex(int x, int z) {
        return CELL_HORIZONTAL_MAX_INDEX_INSIDE - x + z;
    }

    private static int getOutsideIndex(int x, int z) {
        return CELL_COLUMN_INSIDE_COUNT + x + CELL_HORIZONTAL_MAX_INDEX_OUTSIDE - z;
    }

    private static int getX(int index) {
        if (index < CELL_COLUMN_INSIDE_COUNT) {
            return zeroIfNegative(CELL_HORIZONTAL_MAX_INDEX_INSIDE - index);
        } else {
            int i = index - CELL_COLUMN_INSIDE_COUNT;
            return CELL_HORIZONTAL_MAX_INDEX_OUTSIDE - zeroIfNegative(CELL_HORIZONTAL_MAX_INDEX_OUTSIDE - i);
        }
    }

    private static int getZ(int index) {
        if (index < CELL_COLUMN_INSIDE_COUNT) {
            return zeroIfNegative(index - CELL_HORIZONTAL_MAX_INDEX_INSIDE);
        } else {
            int i = index - CELL_COLUMN_INSIDE_COUNT;
            return CELL_HORIZONTAL_MAX_INDEX_OUTSIDE - zeroIfNegative(i - CELL_HORIZONTAL_MAX_INDEX_OUTSIDE);
        }
    }

    private static int zeroIfNegative(int value) {
        return value & ~(value >> 31);
    }

    public LevelHeightAccessor getAreaWithOldGeneration() {
        return this.areaWithOldGeneration;
    }

    protected interface BiomeConsumer {
        void consume(int x, int z, Holder<Biome> biome);
    }

    protected interface DensityConsumer {
        void consume(int x, int y, int z, double density);
    }

    protected interface HeightConsumer {
        void consume(int x, int z, double height);
    }

    public record Packed(int minSection, int maxSection, Optional<double[]> heights) {
        private static final Codec<double[]> DOUBLE_ARRAY_CODEC = Codec.DOUBLE.listOf().xmap(Doubles::toArray, Doubles::asList);
        public static final Codec<BlendingData.Packed> CODEC = RecordCodecBuilder.<BlendingData.Packed>create(
                instance -> instance.group(
                        Codec.INT.fieldOf("min_section").forGetter(BlendingData.Packed::minSection),
                        Codec.INT.fieldOf("max_section").forGetter(BlendingData.Packed::maxSection),
                        DOUBLE_ARRAY_CODEC.lenientOptionalFieldOf("heights").forGetter(BlendingData.Packed::heights)
                    )
                    .apply(instance, BlendingData.Packed::new)
            )
            .validate(BlendingData.Packed::validateArraySize);

        private static DataResult<BlendingData.Packed> validateArraySize(BlendingData.Packed packed) {
            return packed.heights.isPresent() && ((double[])packed.heights.get()).length != BlendingData.CELL_COLUMN_COUNT
                ? DataResult.error(() -> "heights has to be of length " + BlendingData.CELL_COLUMN_COUNT)
                : DataResult.success(packed);
        }
    }
}
