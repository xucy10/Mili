package net.minecraft.world.level.levelgen;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import com.google.common.collect.Sets;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import org.apache.commons.lang3.mutable.MutableObject;
import org.jspecify.annotations.Nullable;

public final class NoiseBasedChunkGenerator extends ChunkGenerator {
    public static final MapCodec<NoiseBasedChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                BiomeSource.CODEC.fieldOf("biome_source").forGetter(generator -> generator.biomeSource),
                NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(generator -> generator.settings)
            )
            .apply(instance, instance.stable(NoiseBasedChunkGenerator::new))
    );
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    public final Holder<NoiseGeneratorSettings> settings;
    private final Supplier<Aquifer.FluidPicker> globalFluidPicker;

    public NoiseBasedChunkGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings) {
        super(biomeSource);
        this.settings = settings;
        this.globalFluidPicker = Suppliers.memoize(() -> createFluidPicker(settings.value()));
    }

    private static Aquifer.FluidPicker createFluidPicker(NoiseGeneratorSettings settings) {
        Aquifer.FluidStatus fluidStatus = new Aquifer.FluidStatus(-54, Blocks.LAVA.defaultBlockState());
        int seaLevel = settings.seaLevel();
        Aquifer.FluidStatus fluidStatus1 = new Aquifer.FluidStatus(seaLevel, settings.defaultFluid());
        Aquifer.FluidStatus fluidStatus2 = new Aquifer.FluidStatus(DimensionType.MIN_Y * 2, Blocks.AIR.defaultBlockState());
        return (x, y, z) -> {
            if (SharedConstants.DEBUG_DISABLE_FLUID_GENERATION) {
                return fluidStatus2;
            } else {
                return y < Math.min(-54, seaLevel) ? fluidStatus : fluidStatus1;
            }
        };
    }

    @Override
    public CompletableFuture<ChunkAccess> createBiomes(RandomState randomState, Blender blender, StructureManager structureManager, ChunkAccess chunk) {
        return CompletableFuture.supplyAsync(() -> {
            this.doCreateBiomes(blender, randomState, structureManager, chunk);
            return chunk;
        }, Runnable::run); // Paper - rewrite chunk system
    }

    private void doCreateBiomes(Blender blender, RandomState random, StructureManager structureManager, ChunkAccess chunk) {
        NoiseChunk noiseChunk = chunk.getOrCreateNoiseChunk(chunkAccess -> this.createNoiseChunk(chunkAccess, structureManager, blender, random));
        BiomeResolver biomeResolver = BelowZeroRetrogen.getBiomeResolver(blender.getBiomeResolver(this.biomeSource), chunk);
        chunk.fillBiomesFromNoise(biomeResolver, noiseChunk.cachedClimateSampler(random.router(), this.settings.value().spawnTarget()));
    }

    private NoiseChunk createNoiseChunk(ChunkAccess chunk, StructureManager structureManager, Blender blender, RandomState random) {
        return NoiseChunk.forChunk(
            chunk, random, Beardifier.forStructuresInChunk(structureManager, chunk.getPos()), this.settings.value(), this.globalFluidPicker.get(), blender
        );
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    public Holder<NoiseGeneratorSettings> generatorSettings() {
        return this.settings;
    }

    public boolean stable(ResourceKey<NoiseGeneratorSettings> settings) {
        return this.settings.is(settings);
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        return this.iterateNoiseColumn(level, random, x, z, null, type.isOpaque()).orElse(level.getMinY());
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState random) {
        MutableObject<NoiseColumn> mutableObject = new MutableObject<>();
        this.iterateNoiseColumn(height, random, x, z, mutableObject, null);
        return mutableObject.get();
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
        DecimalFormat decimalFormat = new DecimalFormat("0.000", DecimalFormatSymbols.getInstance(Locale.ROOT));
        NoiseRouter noiseRouter = random.router();
        DensityFunction.SinglePointContext singlePointContext = new DensityFunction.SinglePointContext(pos.getX(), pos.getY(), pos.getZ());
        double d = noiseRouter.ridges().compute(singlePointContext);
        info.add(
            "NoiseRouter T: "
                + decimalFormat.format(noiseRouter.temperature().compute(singlePointContext))
                + " V: "
                + decimalFormat.format(noiseRouter.vegetation().compute(singlePointContext))
                + " C: "
                + decimalFormat.format(noiseRouter.continents().compute(singlePointContext))
                + " E: "
                + decimalFormat.format(noiseRouter.erosion().compute(singlePointContext))
                + " D: "
                + decimalFormat.format(noiseRouter.depth().compute(singlePointContext))
                + " W: "
                + decimalFormat.format(d)
                + " PV: "
                + decimalFormat.format(NoiseRouterData.peaksAndValleys((float)d))
                + " PS: "
                + decimalFormat.format(noiseRouter.preliminarySurfaceLevel().compute(singlePointContext))
                + " N: "
                + decimalFormat.format(noiseRouter.finalDensity().compute(singlePointContext))
        );
    }

    private OptionalInt iterateNoiseColumn(
        LevelHeightAccessor level, RandomState random, int x, int z, @Nullable MutableObject<NoiseColumn> column, @Nullable Predicate<BlockState> stoppingState
    ) {
        NoiseSettings noiseSettings = this.settings.value().noiseSettings().clampToHeightAccessor(level);
        int cellHeight = noiseSettings.getCellHeight();
        int minY = noiseSettings.minY();
        int i = Mth.floorDiv(minY, cellHeight);
        int i1 = Mth.floorDiv(noiseSettings.height(), cellHeight);
        if (i1 <= 0) {
            return OptionalInt.empty();
        } else {
            BlockState[] blockStates;
            if (column == null) {
                blockStates = null;
            } else {
                blockStates = new BlockState[noiseSettings.height()];
                column.setValue(new NoiseColumn(minY, blockStates));
            }

            int cellWidth = noiseSettings.getCellWidth();
            int i2 = Math.floorDiv(x, cellWidth);
            int i3 = Math.floorDiv(z, cellWidth);
            int i4 = Math.floorMod(x, cellWidth);
            int i5 = Math.floorMod(z, cellWidth);
            int i6 = i2 * cellWidth;
            int i7 = i3 * cellWidth;
            double d = (double)i4 / cellWidth;
            double d1 = (double)i5 / cellWidth;
            NoiseChunk noiseChunk = new NoiseChunk(
                1,
                random,
                i6,
                i7,
                noiseSettings,
                DensityFunctions.BeardifierMarker.INSTANCE,
                this.settings.value(),
                this.globalFluidPicker.get(),
                Blender.empty()
            );
            noiseChunk.initializeForFirstCellX();
            noiseChunk.advanceCellX(0);

            for (int i8 = i1 - 1; i8 >= 0; i8--) {
                noiseChunk.selectCellYZ(i8, 0);

                for (int i9 = cellHeight - 1; i9 >= 0; i9--) {
                    int i10 = (i + i8) * cellHeight + i9;
                    double d2 = (double)i9 / cellHeight;
                    noiseChunk.updateForY(i10, d2);
                    noiseChunk.updateForX(x, d);
                    noiseChunk.updateForZ(z, d1);
                    BlockState interpolatedState = noiseChunk.getInterpolatedState();
                    BlockState blockState = interpolatedState == null ? this.settings.value().defaultBlock() : interpolatedState;
                    if (blockStates != null) {
                        int i11 = i8 * cellHeight + i9;
                        blockStates[i11] = blockState;
                    }

                    if (stoppingState != null && stoppingState.test(blockState)) {
                        noiseChunk.stopInterpolation();
                        return OptionalInt.of(i10 + 1);
                    }
                }
            }

            noiseChunk.stopInterpolation();
            return OptionalInt.empty();
        }
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structureManager, RandomState random, ChunkAccess chunk) {
        if (!SharedConstants.debugVoidTerrain(chunk.getPos()) && !SharedConstants.DEBUG_DISABLE_SURFACE) {
            WorldGenerationContext worldGenerationContext = new WorldGenerationContext(this, region, region.getMinecraftWorld()); // Paper - Flat bedrock generator settings
            this.buildSurface(
                chunk,
                worldGenerationContext,
                random,
                structureManager,
                region.getBiomeManager(),
                region.registryAccess().lookupOrThrow(Registries.BIOME),
                Blender.of(region)
            );
        }
    }

    @VisibleForTesting
    public void buildSurface(
        ChunkAccess chunk,
        WorldGenerationContext context,
        RandomState random,
        StructureManager structureManager,
        BiomeManager biomeManager,
        Registry<Biome> biomes,
        Blender blender
    ) {
        NoiseChunk noiseChunk = chunk.getOrCreateNoiseChunk(chunkAccess -> this.createNoiseChunk(chunkAccess, structureManager, blender, random));
        NoiseGeneratorSettings noiseGeneratorSettings = this.settings.value();
        random.surfaceSystem()
            .buildSurface(
                random, biomeManager, biomes, noiseGeneratorSettings.useLegacyRandomSource(), context, chunk, noiseChunk, noiseGeneratorSettings.surfaceRule()
            );
    }

    @Override
    public void applyCarvers(
        WorldGenRegion region, long seed, RandomState random, BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunk
    ) {
        if (!SharedConstants.DEBUG_DISABLE_CARVERS) {
            BiomeManager biomeManager1 = biomeManager.withDifferentSource((x, y, z) -> this.biomeSource.getNoiseBiome(x, y, z, random.sampler()));
            WorldgenRandom worldgenRandom = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
            int i = 8;
            ChunkPos pos = chunk.getPos();
            NoiseChunk noiseChunk = chunk.getOrCreateNoiseChunk(chunkAccess -> this.createNoiseChunk(chunkAccess, structureManager, Blender.of(region), random));
            Aquifer aquifer = noiseChunk.aquifer();
            CarvingContext carvingContext = new CarvingContext(
                this, region.registryAccess(), chunk.getHeightAccessorForGeneration(), noiseChunk, random, this.settings.value().surfaceRule(), region.getMinecraftWorld() // Paper - Flat bedrock generator settings
            );
            CarvingMask carvingMask = ((ProtoChunk)chunk).getOrCreateCarvingMask();

            for (int i1 = -8; i1 <= 8; i1++) {
                for (int i2 = -8; i2 <= 8; i2++) {
                    ChunkPos chunkPos = new ChunkPos(pos.x + i1, pos.z + i2);
                    ChunkAccess chunk1 = region.getChunk(chunkPos.x, chunkPos.z);
                    BiomeGenerationSettings biomeGenerationSettings = chunk1.carverBiome(
                        () -> this.getBiomeGenerationSettings(
                            this.biomeSource
                                .getNoiseBiome(QuartPos.fromBlock(chunkPos.getMinBlockX()), 0, QuartPos.fromBlock(chunkPos.getMinBlockZ()), random.sampler())
                        )
                    );
                    Iterable<Holder<ConfiguredWorldCarver<?>>> carvers = biomeGenerationSettings.getCarvers();
                    int i3 = 0;

                    for (Holder<ConfiguredWorldCarver<?>> holder : carvers) {
                        ConfiguredWorldCarver<?> configuredWorldCarver = holder.value();
                        worldgenRandom.setLargeFeatureSeed(seed + i3, chunkPos.x, chunkPos.z);
                        if (configuredWorldCarver.isStartChunk(worldgenRandom)) {
                            configuredWorldCarver.carve(carvingContext, chunk, biomeManager1::getBiome, worldgenRandom, aquifer, chunkPos, carvingMask);
                        }

                        i3++;
                    }
                }
            }
        }
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
        NoiseSettings noiseSettings = this.settings.value().noiseSettings().clampToHeightAccessor(chunk.getHeightAccessorForGeneration());
        int minY = noiseSettings.minY();
        int i = Mth.floorDiv(minY, noiseSettings.getCellHeight());
        int i1 = Mth.floorDiv(noiseSettings.height(), noiseSettings.getCellHeight());
        return i1 <= 0 ? CompletableFuture.completedFuture(chunk) : CompletableFuture.supplyAsync(() -> {
            int sectionIndex = chunk.getSectionIndex(i1 * noiseSettings.getCellHeight() - 1 + minY);
            int sectionIndex1 = chunk.getSectionIndex(minY);
            Set<LevelChunkSection> set = Sets.newHashSet();

            for (int i2 = sectionIndex; i2 >= sectionIndex1; i2--) {
                LevelChunkSection section = chunk.getSection(i2);
                section.acquire();
                set.add(section);
            }

            ChunkAccess var20;
            try {
                var20 = this.doFill(blender, structureManager, randomState, chunk, i, i1);
            } finally {
                for (LevelChunkSection levelChunkSection1 : set) {
                    levelChunkSection1.release();
                }
            }

            return var20;
        }, Runnable::run); // Paper - rewrite chunk system
    }

    private ChunkAccess doFill(Blender blender, StructureManager structureManager, RandomState random, ChunkAccess chunk, int minCellY, int cellCountY) {
        NoiseChunk noiseChunk = chunk.getOrCreateNoiseChunk(chunkAccess -> this.createNoiseChunk(chunkAccess, structureManager, blender, random));
        Heightmap heightmapUnprimed = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap heightmapUnprimed1 = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        ChunkPos pos = chunk.getPos();
        int minBlockX = pos.getMinBlockX();
        int minBlockZ = pos.getMinBlockZ();
        Aquifer aquifer = noiseChunk.aquifer();
        noiseChunk.initializeForFirstCellX();
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        int cellWidth = noiseChunk.cellWidth();
        int cellHeight = noiseChunk.cellHeight();
        int i = 16 / cellWidth;
        int i1 = 16 / cellWidth;

        for (int i2 = 0; i2 < i; i2++) {
            noiseChunk.advanceCellX(i2);

            for (int i3 = 0; i3 < i1; i3++) {
                int i4 = chunk.getSectionsCount() - 1;
                LevelChunkSection section = chunk.getSection(i4);

                for (int i5 = cellCountY - 1; i5 >= 0; i5--) {
                    noiseChunk.selectCellYZ(i5, i3);

                    for (int i6 = cellHeight - 1; i6 >= 0; i6--) {
                        int i7 = (minCellY + i5) * cellHeight + i6;
                        int i8 = i7 & 15;
                        int sectionIndex = chunk.getSectionIndex(i7);
                        if (i4 != sectionIndex) {
                            i4 = sectionIndex;
                            section = chunk.getSection(sectionIndex);
                        }

                        double d = (double)i6 / cellHeight;
                        noiseChunk.updateForY(i7, d);

                        for (int i9 = 0; i9 < cellWidth; i9++) {
                            int i10 = minBlockX + i2 * cellWidth + i9;
                            int i11 = i10 & 15;
                            double d1 = (double)i9 / cellWidth;
                            noiseChunk.updateForX(i10, d1);

                            for (int i12 = 0; i12 < cellWidth; i12++) {
                                int i13 = minBlockZ + i3 * cellWidth + i12;
                                int i14 = i13 & 15;
                                double d2 = (double)i12 / cellWidth;
                                noiseChunk.updateForZ(i13, d2);
                                BlockState interpolatedState = noiseChunk.getInterpolatedState();
                                if (interpolatedState == null) {
                                    interpolatedState = this.settings.value().defaultBlock();
                                }

                                interpolatedState = this.debugPreliminarySurfaceLevel(noiseChunk, i10, i7, i13, interpolatedState);
                                if (interpolatedState != AIR && !SharedConstants.debugVoidTerrain(chunk.getPos())) {
                                    section.setBlockState(i11, i8, i14, interpolatedState, false);
                                    heightmapUnprimed.update(i11, i7, i14, interpolatedState);
                                    heightmapUnprimed1.update(i11, i7, i14, interpolatedState);
                                    if (aquifer.shouldScheduleFluidUpdate() && !interpolatedState.getFluidState().isEmpty()) {
                                        mutableBlockPos.set(i10, i7, i13);
                                        chunk.markPosForPostprocessing(mutableBlockPos);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            noiseChunk.swapSlices();
        }

        noiseChunk.stopInterpolation();
        return chunk;
    }

    private BlockState debugPreliminarySurfaceLevel(NoiseChunk chunk, int x, int y, int z, BlockState state) {
        if (SharedConstants.DEBUG_AQUIFERS && z >= 0 && z % 4 == 0) {
            int i = chunk.preliminarySurfaceLevel(x, z);
            int i1 = i + 8;
            if (y == i1) {
                state = i1 < this.getSeaLevel() ? Blocks.SLIME_BLOCK.defaultBlockState() : Blocks.HONEY_BLOCK.defaultBlockState();
            }
        }

        return state;
    }

    @Override
    public int getGenDepth() {
        return this.settings.value().noiseSettings().height();
    }

    @Override
    public int getSeaLevel() {
        return this.settings.value().seaLevel();
    }

    @Override
    public int getMinY() {
        return this.settings.value().noiseSettings().minY();
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        if (!this.settings.value().disableMobGeneration()) {
            ChunkPos center = region.getCenter();
            Holder<Biome> biome = region.getBiome(center.getWorldPosition().atY(region.getMaxY()));
            WorldgenRandom worldgenRandom = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
            worldgenRandom.setDecorationSeed(region.getSeed(), center.getMinBlockX(), center.getMinBlockZ());
            NaturalSpawner.spawnMobsForChunkGeneration(region, biome, center, worldgenRandom);
        }
    }
}
