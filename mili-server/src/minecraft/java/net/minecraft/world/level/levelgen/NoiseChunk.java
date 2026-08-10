package net.minecraft.world.level.levelgen;

import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ColumnPos;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.material.MaterialRuleList;
import org.jspecify.annotations.Nullable;

public class NoiseChunk implements DensityFunction.ContextProvider, DensityFunction.FunctionContext {
    final int cellCountXZ;
    final int cellCountY;
    final int cellNoiseMinY;
    private final int firstCellX;
    private final int firstCellZ;
    final int firstNoiseX;
    final int firstNoiseZ;
    final List<NoiseChunk.NoiseInterpolator> interpolators;
    final List<NoiseChunk.CacheAllInCell> cellCaches;
    private final Map<DensityFunction, DensityFunction> wrapped = new IdentityHashMap<>(); // Mili optimize: use IdentityHashMap for reference-based lookup
    private final Long2IntMap preliminarySurfaceLevelCache = new Long2IntOpenHashMap();
    private final Aquifer aquifer;
    private final DensityFunction preliminarySurfaceLevel;
    private final NoiseChunk.BlockStateFiller blockStateRule;
    private final Blender blender;
    private final NoiseChunk.FlatCache blendAlpha;
    private final NoiseChunk.FlatCache blendOffset;
    private final DensityFunctions.BeardifierOrMarker beardifier;
    private long lastBlendingDataPos = ChunkPos.INVALID_CHUNK_POS;
    private Blender.BlendingOutput lastBlendingOutput = new Blender.BlendingOutput(1.0, 0.0);
    final int noiseSizeXZ;
    final int cellWidth;
    final int cellHeight;
    boolean interpolating;
    boolean fillingCell;
    private int cellStartBlockX;
    int cellStartBlockY;
    private int cellStartBlockZ;
    int inCellX;
    int inCellY;
    int inCellZ;
    long interpolationCounter;
    long arrayInterpolationCounter;
    int arrayIndex;
    private final DensityFunction.ContextProvider sliceFillingContextProvider = new DensityFunction.ContextProvider() {
        @Override
        public DensityFunction.FunctionContext forIndex(int arrayIndex) {
            NoiseChunk.this.cellStartBlockY = (arrayIndex + NoiseChunk.this.cellNoiseMinY) * NoiseChunk.this.cellHeight;
            NoiseChunk.this.interpolationCounter++;
            NoiseChunk.this.inCellY = 0;
            NoiseChunk.this.arrayIndex = arrayIndex;
            return NoiseChunk.this;
        }

        @Override
        public void fillAllDirectly(double[] values, DensityFunction function) {
            for (int i = 0; i < NoiseChunk.this.cellCountY + 1; i++) {
                NoiseChunk.this.cellStartBlockY = (i + NoiseChunk.this.cellNoiseMinY) * NoiseChunk.this.cellHeight;
                NoiseChunk.this.interpolationCounter++;
                NoiseChunk.this.inCellY = 0;
                NoiseChunk.this.arrayIndex = i;
                values[i] = function.compute(NoiseChunk.this);
            }
        }
    };

    public static NoiseChunk forChunk(
        ChunkAccess chunk,
        RandomState state,
        DensityFunctions.BeardifierOrMarker beardifierOrMarker,
        NoiseGeneratorSettings noiseGeneratorSettings,
        Aquifer.FluidPicker fluidPicke,
        Blender blender
    ) {
        NoiseSettings noiseSettings = noiseGeneratorSettings.noiseSettings().clampToHeightAccessor(chunk);
        ChunkPos pos = chunk.getPos();
        int i = 16 / noiseSettings.getCellWidth();
        return new NoiseChunk(i, state, pos.getMinBlockX(), pos.getMinBlockZ(), noiseSettings, beardifierOrMarker, noiseGeneratorSettings, fluidPicke, blender);
    }

    public NoiseChunk(
        int cellCountXZ,
        RandomState random,
        int firstNoiseX,
        int firstNoiseZ,
        NoiseSettings noiseSettings,
        DensityFunctions.BeardifierOrMarker beardifier,
        NoiseGeneratorSettings noiseGeneratorSettings,
        Aquifer.FluidPicker fluidPicker,
        Blender blendifier
    ) {
        this.cellWidth = noiseSettings.getCellWidth();
        this.cellHeight = noiseSettings.getCellHeight();
        this.cellCountXZ = cellCountXZ;
        this.cellCountY = Mth.floorDiv(noiseSettings.height(), this.cellHeight);
        this.cellNoiseMinY = Mth.floorDiv(noiseSettings.minY(), this.cellHeight);
        this.firstCellX = Math.floorDiv(firstNoiseX, this.cellWidth);
        this.firstCellZ = Math.floorDiv(firstNoiseZ, this.cellWidth);
        this.interpolators = Lists.newArrayList();
        this.cellCaches = Lists.newArrayList();
        this.firstNoiseX = QuartPos.fromBlock(firstNoiseX);
        this.firstNoiseZ = QuartPos.fromBlock(firstNoiseZ);
        this.noiseSizeXZ = QuartPos.fromBlock(cellCountXZ * this.cellWidth);
        this.blender = blendifier;
        this.beardifier = beardifier;
        this.blendAlpha = new NoiseChunk.FlatCache(new NoiseChunk.BlendAlpha(), false);
        this.blendOffset = new NoiseChunk.FlatCache(new NoiseChunk.BlendOffset(), false);
        if (!blendifier.isEmpty()) {
            for (int i = 0; i <= this.noiseSizeXZ; i++) {
                int i1 = this.firstNoiseX + i;
                int blockPosCoord = QuartPos.toBlock(i1);

                for (int i2 = 0; i2 <= this.noiseSizeXZ; i2++) {
                    int i3 = this.firstNoiseZ + i2;
                    int blockPosCoord1 = QuartPos.toBlock(i3);
                    Blender.BlendingOutput blendingOutput = blendifier.blendOffsetAndFactor(blockPosCoord, blockPosCoord1);
                    this.blendAlpha.values[i + i2 * this.blendAlpha.sizeXZ] = blendingOutput.alpha();
                    this.blendOffset.values[i + i2 * this.blendOffset.sizeXZ] = blendingOutput.blendingOffset();
                }
            }
        } else {
            Arrays.fill(this.blendAlpha.values, 1.0);
            Arrays.fill(this.blendOffset.values, 0.0);
        }

        NoiseRouter noiseRouter = random.router();
        NoiseRouter noiseRouter1 = noiseRouter.mapAll(this::wrap);
        this.preliminarySurfaceLevel = noiseRouter1.preliminarySurfaceLevel();
        if (!noiseGeneratorSettings.isAquifersEnabled()) {
            this.aquifer = Aquifer.createDisabled(fluidPicker);
        } else {
            int blockPosCoord = SectionPos.blockToSectionCoord(firstNoiseX);
            int i2 = SectionPos.blockToSectionCoord(firstNoiseZ);
            this.aquifer = Aquifer.create(
                this, new ChunkPos(blockPosCoord, i2), noiseRouter1, random.aquiferRandom(), noiseSettings.minY(), noiseSettings.height(), fluidPicker
            );
        }

        List<NoiseChunk.BlockStateFiller> list = new ArrayList<>();
        DensityFunction densityFunction = DensityFunctions.cacheAllInCell(
                DensityFunctions.add(noiseRouter1.finalDensity(), DensityFunctions.BeardifierMarker.INSTANCE)
            )
            .mapAll(this::wrap);
        list.add(context -> this.aquifer.computeSubstance(context, densityFunction.compute(context)));
        if (noiseGeneratorSettings.oreVeinsEnabled()) {
            list.add(OreVeinifier.create(noiseRouter1.veinToggle(), noiseRouter1.veinRidged(), noiseRouter1.veinGap(), random.oreRandom()));
        }

        this.blockStateRule = new MaterialRuleList(list.toArray(new NoiseChunk.BlockStateFiller[0]));
    }

    protected Climate.Sampler cachedClimateSampler(NoiseRouter noiseRouter, List<Climate.ParameterPoint> points) {
        return new Climate.Sampler(
            noiseRouter.temperature().mapAll(this::wrap),
            noiseRouter.vegetation().mapAll(this::wrap),
            noiseRouter.continents().mapAll(this::wrap),
            noiseRouter.erosion().mapAll(this::wrap),
            noiseRouter.depth().mapAll(this::wrap),
            noiseRouter.ridges().mapAll(this::wrap),
            points
        );
    }

    protected @Nullable BlockState getInterpolatedState() {
        return this.blockStateRule.calculate(this);
    }

    @Override
    public int blockX() {
        return this.cellStartBlockX + this.inCellX;
    }

    @Override
    public int blockY() {
        return this.cellStartBlockY + this.inCellY;
    }

    @Override
    public int blockZ() {
        return this.cellStartBlockZ + this.inCellZ;
    }

    public int maxPreliminarySurfaceLevel(int minX, int minZ, int maxX, int maxZ) {
        int i = Integer.MIN_VALUE;

        for (int i1 = minZ; i1 <= maxZ; i1 += 4) {
            for (int i2 = minX; i2 <= maxX; i2 += 4) {
                int i3 = this.preliminarySurfaceLevel(i2, i1);
                if (i3 > i) {
                    i = i3;
                }
            }
        }

        return i;
    }

    public int preliminarySurfaceLevel(int x, int z) {
        int blockPosCoord = QuartPos.toBlock(QuartPos.fromBlock(x));
        int blockPosCoord1 = QuartPos.toBlock(QuartPos.fromBlock(z));
        return this.preliminarySurfaceLevelCache.computeIfAbsent(ColumnPos.asLong(blockPosCoord, blockPosCoord1), this::computePreliminarySurfaceLevel);
    }

    private int computePreliminarySurfaceLevel(long packedChunkPos) {
        int x = ColumnPos.getX(packedChunkPos);
        int z = ColumnPos.getZ(packedChunkPos);
        return Mth.floor(this.preliminarySurfaceLevel.compute(new DensityFunction.SinglePointContext(x, 0, z)));
    }

    @Override
    public Blender getBlender() {
        return this.blender;
    }

    private void fillSlice(boolean isSlice0, int start) {
        this.cellStartBlockX = start * this.cellWidth;
        this.inCellX = 0;

        for (int i = 0; i < this.cellCountXZ + 1; i++) {
            int i1 = this.firstCellZ + i;
            this.cellStartBlockZ = i1 * this.cellWidth;
            this.inCellZ = 0;
            this.arrayInterpolationCounter++;

            for (NoiseChunk.NoiseInterpolator noiseInterpolator : this.interpolators) {
                double[] doubles = (isSlice0 ? noiseInterpolator.slice0 : noiseInterpolator.slice1)[i];
                noiseInterpolator.fillArray(doubles, this.sliceFillingContextProvider);
            }
        }

        this.arrayInterpolationCounter++;
    }

    public void initializeForFirstCellX() {
        if (this.interpolating) {
            throw new IllegalStateException("Staring interpolation twice");
        } else {
            this.interpolating = true;
            this.interpolationCounter = 0L;
            this.fillSlice(true, this.firstCellX);
        }
    }

    public void advanceCellX(int increment) {
        this.fillSlice(false, this.firstCellX + increment + 1);
        this.cellStartBlockX = (this.firstCellX + increment) * this.cellWidth;
    }

    @Override
    public NoiseChunk forIndex(int arrayIndex) {
        int i = Math.floorMod(arrayIndex, this.cellWidth);
        int i1 = Math.floorDiv(arrayIndex, this.cellWidth);
        int i2 = Math.floorMod(i1, this.cellWidth);
        int i3 = this.cellHeight - 1 - Math.floorDiv(i1, this.cellWidth);
        this.inCellX = i2;
        this.inCellY = i3;
        this.inCellZ = i;
        this.arrayIndex = arrayIndex;
        return this;
    }

    @Override
    public void fillAllDirectly(double[] values, DensityFunction function) {
        this.arrayIndex = 0;

        for (int i = this.cellHeight - 1; i >= 0; i--) {
            this.inCellY = i;

            for (int i1 = 0; i1 < this.cellWidth; i1++) {
                this.inCellX = i1;

                for (int i2 = 0; i2 < this.cellWidth; i2++) {
                    this.inCellZ = i2;
                    values[this.arrayIndex++] = function.compute(this);
                }
            }
        }
    }

    public void selectCellYZ(int y, int z) {
        for (NoiseChunk.NoiseInterpolator noiseInterpolator : this.interpolators) {
            noiseInterpolator.selectCellYZ(y, z);
        }

        this.fillingCell = true;
        this.cellStartBlockY = (y + this.cellNoiseMinY) * this.cellHeight;
        this.cellStartBlockZ = (this.firstCellZ + z) * this.cellWidth;
        this.arrayInterpolationCounter++;

        for (NoiseChunk.CacheAllInCell cacheAllInCell : this.cellCaches) {
            cacheAllInCell.noiseFiller.fillArray(cacheAllInCell.values, this);
        }

        this.arrayInterpolationCounter++;
        this.fillingCell = false;
    }

    public void updateForY(int cellEndBlockY, double y) {
        this.inCellY = cellEndBlockY - this.cellStartBlockY;

        for (NoiseChunk.NoiseInterpolator noiseInterpolator : this.interpolators) {
            noiseInterpolator.updateForY(y);
        }
    }

    public void updateForX(int cellEndBlockX, double x) {
        this.inCellX = cellEndBlockX - this.cellStartBlockX;

        for (NoiseChunk.NoiseInterpolator noiseInterpolator : this.interpolators) {
            noiseInterpolator.updateForX(x);
        }
    }

    public void updateForZ(int cellEndBlockZ, double z) {
        this.inCellZ = cellEndBlockZ - this.cellStartBlockZ;
        this.interpolationCounter++;

        for (NoiseChunk.NoiseInterpolator noiseInterpolator : this.interpolators) {
            noiseInterpolator.updateForZ(z);
        }
    }

    public void stopInterpolation() {
        if (!this.interpolating) {
            throw new IllegalStateException("Staring interpolation twice");
        } else {
            this.interpolating = false;
        }
    }

    public void swapSlices() {
        this.interpolators.forEach(NoiseChunk.NoiseInterpolator::swapSlices);
    }

    public Aquifer aquifer() {
        return this.aquifer;
    }

    protected int cellWidth() {
        return this.cellWidth;
    }

    protected int cellHeight() {
        return this.cellHeight;
    }

    Blender.BlendingOutput getOrComputeBlendingOutput(int chunkX, int chunkZ) {
        long packedChunkPos = ChunkPos.asLong(chunkX, chunkZ);
        if (this.lastBlendingDataPos == packedChunkPos) {
            return this.lastBlendingOutput;
        } else {
            this.lastBlendingDataPos = packedChunkPos;
            Blender.BlendingOutput blendingOutput = this.blender.blendOffsetAndFactor(chunkX, chunkZ);
            this.lastBlendingOutput = blendingOutput;
            return blendingOutput;
        }
    }

    protected DensityFunction wrap(DensityFunction densityFunction) {
        // Leaf start - Reduce worldgen allocations
        // Avoid lambda allocation
        DensityFunction func = this.wrapped.get(densityFunction);

        if (func == null) {
            func = this.wrapNew(densityFunction);
            this.wrapped.put(densityFunction, func);
        }

        return func;
        // Leaf end - Reduce worldgen allocations
    }

    private DensityFunction wrapNew(DensityFunction densityFunction) {
        if (densityFunction instanceof DensityFunctions.Marker marker) {
            return (DensityFunction)(switch (marker.type()) {
                case Interpolated -> new NoiseChunk.NoiseInterpolator(marker.wrapped());
                case FlatCache -> new NoiseChunk.FlatCache(marker.wrapped(), true);
                case Cache2D -> new NoiseChunk.Cache2D(marker.wrapped());
                case CacheOnce -> new NoiseChunk.CacheOnce(marker.wrapped());
                case CacheAllInCell -> new NoiseChunk.CacheAllInCell(marker.wrapped());
            });
        } else {
            if (this.blender != Blender.empty()) {
                if (densityFunction == DensityFunctions.BlendAlpha.INSTANCE) {
                    return this.blendAlpha;
                }

                if (densityFunction == DensityFunctions.BlendOffset.INSTANCE) {
                    return this.blendOffset;
                }
            }

            if (densityFunction == DensityFunctions.BeardifierMarker.INSTANCE) {
                return this.beardifier;
            } else {
                return densityFunction instanceof DensityFunctions.HolderHolder holderHolder ? holderHolder.function().value() : densityFunction;
            }
        }
    }

    class BlendAlpha implements NoiseChunk.NoiseChunkDensityFunction {
        @Override
        public DensityFunction wrapped() {
            return DensityFunctions.BlendAlpha.INSTANCE;
        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return this.wrapped().mapAll(visitor);
        }

        @Override
        public double compute(DensityFunction.FunctionContext context) {
            return NoiseChunk.this.getOrComputeBlendingOutput(context.blockX(), context.blockZ()).alpha();
        }

        @Override
        public void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
            contextProvider.fillAllDirectly(array, this);
        }

        @Override
        public double minValue() {
            return 0.0;
        }

        @Override
        public double maxValue() {
            return 1.0;
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return DensityFunctions.BlendAlpha.CODEC;
        }
    }

    class BlendOffset implements NoiseChunk.NoiseChunkDensityFunction {
        @Override
        public DensityFunction wrapped() {
            return DensityFunctions.BlendOffset.INSTANCE;
        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return this.wrapped().mapAll(visitor);
        }

        @Override
        public double compute(DensityFunction.FunctionContext context) {
            return NoiseChunk.this.getOrComputeBlendingOutput(context.blockX(), context.blockZ()).blendingOffset();
        }

        @Override
        public void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
            contextProvider.fillAllDirectly(array, this);
        }

        @Override
        public double minValue() {
            return Double.NEGATIVE_INFINITY;
        }

        @Override
        public double maxValue() {
            return Double.POSITIVE_INFINITY;
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return DensityFunctions.BlendOffset.CODEC;
        }
    }

    @FunctionalInterface
    public interface BlockStateFiller {
        @Nullable BlockState calculate(DensityFunction.FunctionContext context);
    }

    static class Cache2D implements DensityFunctions.MarkerOrMarked, NoiseChunk.NoiseChunkDensityFunction {
        private final DensityFunction function;
        private long lastPos2D = ChunkPos.INVALID_CHUNK_POS;
        private double lastValue;

        Cache2D(DensityFunction function) {
            this.function = function;
        }

        @Override
        public double compute(DensityFunction.FunctionContext context) {
            int i = context.blockX();
            int i1 = context.blockZ();
            long packedChunkPos = ChunkPos.asLong(i, i1);
            if (this.lastPos2D == packedChunkPos) {
                return this.lastValue;
            } else {
                this.lastPos2D = packedChunkPos;
                double d = this.function.compute(context);
                this.lastValue = d;
                return d;
            }
        }

        @Override
        public void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
            this.function.fillArray(array, contextProvider);
        }

        @Override
        public DensityFunction wrapped() {
            return this.function;
        }

        @Override
        public DensityFunctions.Marker.Type type() {
            return DensityFunctions.Marker.Type.Cache2D;
        }
    }

    class CacheAllInCell implements DensityFunctions.MarkerOrMarked, NoiseChunk.NoiseChunkDensityFunction {
        final DensityFunction noiseFiller;
        final double[] values;

        CacheAllInCell(final DensityFunction noiseFiller) {
            this.noiseFiller = noiseFiller;
            this.values = new double[NoiseChunk.this.cellWidth * NoiseChunk.this.cellWidth * NoiseChunk.this.cellHeight];
            NoiseChunk.this.cellCaches.add(this);
        }

        @Override
        public double compute(DensityFunction.FunctionContext context) {
            if (context != NoiseChunk.this) {
                return this.noiseFiller.compute(context);
            } else if (!NoiseChunk.this.interpolating) {
                throw new IllegalStateException("Trying to sample interpolator outside the interpolation loop");
            } else {
                int i = NoiseChunk.this.inCellX;
                int i1 = NoiseChunk.this.inCellY;
                int i2 = NoiseChunk.this.inCellZ;
                return i >= 0 && i1 >= 0 && i2 >= 0 && i < NoiseChunk.this.cellWidth && i1 < NoiseChunk.this.cellHeight && i2 < NoiseChunk.this.cellWidth
                    ? this.values[((NoiseChunk.this.cellHeight - 1 - i1) * NoiseChunk.this.cellWidth + i) * NoiseChunk.this.cellWidth + i2]
                    : this.noiseFiller.compute(context);
            }
        }

        @Override
        public void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
            contextProvider.fillAllDirectly(array, this);
        }

        @Override
        public DensityFunction wrapped() {
            return this.noiseFiller;
        }

        @Override
        public DensityFunctions.Marker.Type type() {
            return DensityFunctions.Marker.Type.CacheAllInCell;
        }
    }

    class CacheOnce implements DensityFunctions.MarkerOrMarked, NoiseChunk.NoiseChunkDensityFunction {
        private final DensityFunction function;
        private long lastCounter;
        private long lastArrayCounter;
        private double lastValue;
        private double @Nullable [] lastArray;

        CacheOnce(final DensityFunction function) {
            this.function = function;
        }

        @Override
        public double compute(DensityFunction.FunctionContext context) {
            if (context != NoiseChunk.this) {
                return this.function.compute(context);
            } else if (this.lastArray != null && this.lastArrayCounter == NoiseChunk.this.arrayInterpolationCounter) {
                return this.lastArray[NoiseChunk.this.arrayIndex];
            } else if (this.lastCounter == NoiseChunk.this.interpolationCounter) {
                return this.lastValue;
            } else {
                this.lastCounter = NoiseChunk.this.interpolationCounter;
                double d = this.function.compute(context);
                this.lastValue = d;
                return d;
            }
        }

        @Override
        public void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
            if (this.lastArray != null && this.lastArrayCounter == NoiseChunk.this.arrayInterpolationCounter) {
                System.arraycopy(this.lastArray, 0, array, 0, array.length);
            } else {
                this.wrapped().fillArray(array, contextProvider);
                // Mili optimize: reuse lastArray buffer instead of cloning to reduce GC pressure
                if (this.lastArray == null) {
                    this.lastArray = new double[array.length];
                } else if (this.lastArray.length != array.length) {
                    this.lastArray = new double[array.length];
                }
                System.arraycopy(array, 0, this.lastArray, 0, array.length);

                this.lastArrayCounter = NoiseChunk.this.arrayInterpolationCounter;
            }
        }

        @Override
        public DensityFunction wrapped() {
            return this.function;
        }

        @Override
        public DensityFunctions.Marker.Type type() {
            return DensityFunctions.Marker.Type.CacheOnce;
        }
    }

    class FlatCache implements DensityFunctions.MarkerOrMarked, NoiseChunk.NoiseChunkDensityFunction {
        private final DensityFunction noiseFiller;
        final double[] values;
        final int sizeXZ;

        FlatCache(final DensityFunction noiseFiller, final boolean computeValues) {
            this.noiseFiller = noiseFiller;
            this.sizeXZ = NoiseChunk.this.noiseSizeXZ + 1;
            this.values = new double[this.sizeXZ * this.sizeXZ];
            if (computeValues) {
                // Mili optimize: reuse mutable context instead of allocating SinglePointContext per-iteration
                FlatCacheContext context = new FlatCacheContext();
                for (int i = 0; i <= NoiseChunk.this.noiseSizeXZ; i++) {
                    int i1 = NoiseChunk.this.firstNoiseX + i;
                    int blockPosCoord = QuartPos.toBlock(i1);
                    context.blockX = blockPosCoord;

                    for (int i2 = 0; i2 <= NoiseChunk.this.noiseSizeXZ; i2++) {
                        int i3 = NoiseChunk.this.firstNoiseZ + i2;
                        int blockPosCoord1 = QuartPos.toBlock(i3);
                        context.blockZ = blockPosCoord1;
                        this.values[i + i2 * this.sizeXZ] = noiseFiller.compute(context);
                    }
                }
            }
        }

        @Override
        public double compute(DensityFunction.FunctionContext context) {
            int quartPosX = QuartPos.fromBlock(context.blockX());
            int quartPosZ = QuartPos.fromBlock(context.blockZ());
            int i = quartPosX - NoiseChunk.this.firstNoiseX;
            int i1 = quartPosZ - NoiseChunk.this.firstNoiseZ;
            return i >= 0 && i1 >= 0 && i < this.sizeXZ && i1 < this.sizeXZ ? this.values[i + i1 * this.sizeXZ] : this.noiseFiller.compute(context);
        }

        @Override
        public void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
            contextProvider.fillAllDirectly(array, this);
        }

        @Override
        public DensityFunction wrapped() {
            return this.noiseFiller;
        }

        @Override
        public DensityFunctions.Marker.Type type() {
            return DensityFunctions.Marker.Type.FlatCache;
        }
    }

    // Mili optimize: mutable FunctionContext to avoid per-iteration allocation in FlatCache
    private static final class FlatCacheContext implements DensityFunction.FunctionContext {
        int blockX;
        int blockY;
        int blockZ;

        @Override
        public int blockX() {
            return this.blockX;
        }

        @Override
        public int blockY() {
            return this.blockY;
        }

        @Override
        public int blockZ() {
            return this.blockZ;
        }
    }

    interface NoiseChunkDensityFunction extends DensityFunction {
        DensityFunction wrapped();

        @Override
        default double minValue() {
            return this.wrapped().minValue();
        }

        @Override
        default double maxValue() {
            return this.wrapped().maxValue();
        }
    }

    public class NoiseInterpolator implements DensityFunctions.MarkerOrMarked, NoiseChunk.NoiseChunkDensityFunction {
        double[][] slice0;
        double[][] slice1;
        private final DensityFunction noiseFiller;
        private double noise000;
        private double noise001;
        private double noise100;
        private double noise101;
        private double noise010;
        private double noise011;
        private double noise110;
        private double noise111;
        private double valueXZ00;
        private double valueXZ10;
        private double valueXZ01;
        private double valueXZ11;
        private double valueZ0;
        private double valueZ1;
        private double value;

        NoiseInterpolator(final DensityFunction noiseFiller) {
            this.noiseFiller = noiseFiller;
            this.slice0 = this.allocateSlice(NoiseChunk.this.cellCountY, NoiseChunk.this.cellCountXZ);
            this.slice1 = this.allocateSlice(NoiseChunk.this.cellCountY, NoiseChunk.this.cellCountXZ);
            NoiseChunk.this.interpolators.add(this);
        }

        private double[][] allocateSlice(int cellCountY, int cellCountXZ) {
            int i = cellCountXZ + 1;
            int i1 = cellCountY + 1;
            double[][] doubles = new double[i][i1];

            for (int i2 = 0; i2 < i; i2++) {
                doubles[i2] = new double[i1];
            }

            return doubles;
        }

        void selectCellYZ(int y, int z) {
            this.noise000 = this.slice0[z][y];
            this.noise001 = this.slice0[z + 1][y];
            this.noise100 = this.slice1[z][y];
            this.noise101 = this.slice1[z + 1][y];
            this.noise010 = this.slice0[z][y + 1];
            this.noise011 = this.slice0[z + 1][y + 1];
            this.noise110 = this.slice1[z][y + 1];
            this.noise111 = this.slice1[z + 1][y + 1];
        }

        void updateForY(double y) {
            this.valueXZ00 = Mth.lerp(y, this.noise000, this.noise010);
            this.valueXZ10 = Mth.lerp(y, this.noise100, this.noise110);
            this.valueXZ01 = Mth.lerp(y, this.noise001, this.noise011);
            this.valueXZ11 = Mth.lerp(y, this.noise101, this.noise111);
        }

        void updateForX(double x) {
            this.valueZ0 = Mth.lerp(x, this.valueXZ00, this.valueXZ10);
            this.valueZ1 = Mth.lerp(x, this.valueXZ01, this.valueXZ11);
        }

        void updateForZ(double z) {
            this.value = Mth.lerp(z, this.valueZ0, this.valueZ1);
        }

        @Override
        public double compute(DensityFunction.FunctionContext context) {
            if (context != NoiseChunk.this) {
                return this.noiseFiller.compute(context);
            } else if (!NoiseChunk.this.interpolating) {
                throw new IllegalStateException("Trying to sample interpolator outside the interpolation loop");
            } else {
                return NoiseChunk.this.fillingCell
                    ? Mth.lerp3(
                        (double)NoiseChunk.this.inCellX / NoiseChunk.this.cellWidth,
                        (double)NoiseChunk.this.inCellY / NoiseChunk.this.cellHeight,
                        (double)NoiseChunk.this.inCellZ / NoiseChunk.this.cellWidth,
                        this.noise000,
                        this.noise100,
                        this.noise010,
                        this.noise110,
                        this.noise001,
                        this.noise101,
                        this.noise011,
                        this.noise111
                    )
                    : this.value;
            }
        }

        @Override
        public void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
            if (NoiseChunk.this.fillingCell) {
                contextProvider.fillAllDirectly(array, this);
            } else {
                this.wrapped().fillArray(array, contextProvider);
            }
        }

        @Override
        public DensityFunction wrapped() {
            return this.noiseFiller;
        }

        private void swapSlices() {
            double[][] doubles = this.slice0;
            this.slice0 = this.slice1;
            this.slice1 = doubles;
        }

        @Override
        public DensityFunctions.Marker.Type type() {
            return DensityFunctions.Marker.Type.Interpolated;
        }
    }
}
