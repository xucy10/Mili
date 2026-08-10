package net.minecraft.world.level.chunk;

import com.google.common.base.Suppliers;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Util;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.feature.FeatureCountTracker;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureSpawnOverride;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jspecify.annotations.Nullable;

public abstract class ChunkGenerator {
    public static final Codec<ChunkGenerator> CODEC = BuiltInRegistries.CHUNK_GENERATOR
        .byNameCodec()
        .dispatchStable(ChunkGenerator::codec, Function.identity());
    protected final BiomeSource biomeSource;
    private final Supplier<List<FeatureSorter.StepFeatureData>> featuresPerStep;
    public final Function<Holder<Biome>, BiomeGenerationSettings> generationSettingsGetter;

    public ChunkGenerator(BiomeSource biomeSource) {
        this(biomeSource, biome -> biome.value().getGenerationSettings());
    }

    public ChunkGenerator(BiomeSource biomeSource, Function<Holder<Biome>, BiomeGenerationSettings> generationSettingsGetter) {
        this.biomeSource = biomeSource;
        this.generationSettingsGetter = generationSettingsGetter;
        this.featuresPerStep = Suppliers.memoize(
            () -> FeatureSorter.buildFeaturesPerStep(List.copyOf(biomeSource.possibleBiomes()), biome -> generationSettingsGetter.apply(biome).features(), true)
        );
    }

    public void validate() {
        this.featuresPerStep.get();
    }

    protected abstract MapCodec<? extends ChunkGenerator> codec();

    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structureSetLookup, RandomState randomState, long seed, org.spigotmc.SpigotWorldConfig conf) { // Spigot
        return ChunkGeneratorStructureState.createForNormal(randomState, seed, this.biomeSource, structureSetLookup, conf); // Spigot
    }

    public Optional<ResourceKey<MapCodec<? extends ChunkGenerator>>> getTypeNameForDataFixer() {
        return BuiltInRegistries.CHUNK_GENERATOR.getResourceKey(this.codec());
    }

    public CompletableFuture<ChunkAccess> createBiomes(RandomState randomState, Blender blender, StructureManager structureManager, ChunkAccess chunk) {
        return CompletableFuture.supplyAsync(() -> {
            chunk.fillBiomesFromNoise(this.biomeSource, randomState.sampler());
            return chunk;
        }, Runnable::run);  // Paper - rewrite chunk system
    }

    public abstract void applyCarvers(
        WorldGenRegion region, long seed, RandomState random, BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunk
    );

    public @Nullable Pair<BlockPos, Holder<Structure>> findNearestMapStructure(
        ServerLevel level, HolderSet<Structure> structure, BlockPos pos, int radius, boolean skipKnownStructures
    ) {
        if (SharedConstants.DEBUG_DISABLE_FEATURES) {
            return null;
        } else {
            // Paper start - StructuresLocateEvent
            final org.bukkit.World bukkitWorld = level.getWorld();
            final org.bukkit.Location origin = org.bukkit.craftbukkit.util.CraftLocation.toBukkit(pos, level);
            final List<org.bukkit.generator.structure.Structure> apiStructures = structure.stream().map(org.bukkit.craftbukkit.generator.structure.CraftStructure::minecraftHolderToBukkit).toList();
            if (!apiStructures.isEmpty()) {
                final io.papermc.paper.event.world.StructuresLocateEvent event = new io.papermc.paper.event.world.StructuresLocateEvent(bukkitWorld, origin, apiStructures, radius, skipKnownStructures);
                if (!event.callEvent()) {
                    return null;
                }
                if (event.getResult() != null) {
                    return Pair.of(io.papermc.paper.util.MCUtil.toBlockPos(event.getResult().pos()), org.bukkit.craftbukkit.generator.structure.CraftStructure.bukkitToMinecraftHolder(event.getResult().structure()));
                }
                pos = org.bukkit.craftbukkit.util.CraftLocation.toBlockPosition(event.getOrigin());
                radius = event.getRadius();
                skipKnownStructures = event.shouldFindUnexplored();
                structure = HolderSet.direct(org.bukkit.craftbukkit.generator.structure.CraftStructure::bukkitToMinecraftHolder, event.getStructures());
            }
            // Paper end
            ChunkGeneratorStructureState generatorState = level.getChunkSource().getGeneratorState();
            Map<StructurePlacement, Set<Holder<Structure>>> map = new Object2ObjectArrayMap<>();

            for (Holder<Structure> holder : structure) {
                for (StructurePlacement structurePlacement : generatorState.getPlacementsForStructure(holder)) {
                    map.computeIfAbsent(structurePlacement, key -> new ObjectArraySet<>()).add(holder);
                }
            }

            if (map.isEmpty()) {
                return null;
            } else {
                Pair<BlockPos, Holder<Structure>> pair = null;
                double d = Double.MAX_VALUE;
                StructureManager structureManager = level.structureManager();
                List<Entry<StructurePlacement, Set<Holder<Structure>>>> list = new ArrayList<>(map.size());

                for (Entry<StructurePlacement, Set<Holder<Structure>>> entry : map.entrySet()) {
                    StructurePlacement structurePlacement1 = entry.getKey();
                    if (structurePlacement1 instanceof ConcentricRingsStructurePlacement concentricRingsStructurePlacement) {
                        Pair<BlockPos, Holder<Structure>> nearestGeneratedStructure = this.getNearestGeneratedStructure(
                            entry.getValue(), level, structureManager, pos, skipKnownStructures, concentricRingsStructurePlacement
                        );
                        if (nearestGeneratedStructure != null) {
                            BlockPos blockPos = nearestGeneratedStructure.getFirst();
                            double d1 = pos.distSqr(blockPos);
                            if (d1 < d) {
                                d = d1;
                                pair = nearestGeneratedStructure;
                            }
                        }
                    } else if (structurePlacement1 instanceof RandomSpreadStructurePlacement) {
                        list.add(entry);
                    }
                }

                if (!list.isEmpty()) {
                    int sectionPosX = SectionPos.blockToSectionCoord(pos.getX());
                    int sectionPosZ = SectionPos.blockToSectionCoord(pos.getZ());

                    for (int i = 0; i <= radius; i++) {
                        boolean flag = false;

                        for (Entry<StructurePlacement, Set<Holder<Structure>>> entry1 : list) {
                            RandomSpreadStructurePlacement randomSpreadStructurePlacement = (RandomSpreadStructurePlacement)entry1.getKey();
                            Pair<BlockPos, Holder<Structure>> nearestGeneratedStructure1 = getNearestGeneratedStructure(
                                entry1.getValue(),
                                level,
                                structureManager,
                                sectionPosX,
                                sectionPosZ,
                                i,
                                skipKnownStructures,
                                generatorState.getLevelSeed(),
                                randomSpreadStructurePlacement
                            );
                            if (nearestGeneratedStructure1 != null) {
                                flag = true;
                                double d2 = pos.distSqr(nearestGeneratedStructure1.getFirst());
                                if (d2 < d) {
                                    d = d2;
                                    pair = nearestGeneratedStructure1;
                                }
                            }
                        }

                        if (flag) {
                            return pair;
                        }
                    }
                }

                return pair;
            }
        }
    }

    private @Nullable Pair<BlockPos, Holder<Structure>> getNearestGeneratedStructure(
        Set<Holder<Structure>> structureHoldersSet,
        ServerLevel level,
        StructureManager structureManager,
        BlockPos pos,
        boolean skipKnownStructures,
        ConcentricRingsStructurePlacement placement
    ) {
        List<ChunkPos> ringPositionsFor = level.getChunkSource().getGeneratorState().getRingPositionsFor(placement);
        if (ringPositionsFor == null) {
            throw new IllegalStateException("Somehow tried to find structures for a placement that doesn't exist");
        } else {
            Pair<BlockPos, Holder<Structure>> pair = null;
            double d = Double.MAX_VALUE;
            BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

            for (ChunkPos chunkPos : ringPositionsFor) {
                if (!level.paperConfig().environment.locateStructuresOutsideWorldBorder && !level.getWorldBorder().isChunkInBounds(chunkPos.x, chunkPos.z)) { continue; } // Paper - Bound treasure maps to world border
                mutableBlockPos.set(SectionPos.sectionToBlockCoord(chunkPos.x, 8), 32, SectionPos.sectionToBlockCoord(chunkPos.z, 8));
                double d1 = mutableBlockPos.distSqr(pos);
                boolean flag = pair == null || d1 < d;
                if (flag) {
                    Pair<BlockPos, Holder<Structure>> structureGeneratingAt = getStructureGeneratingAt(
                        structureHoldersSet, level, structureManager, skipKnownStructures, placement, chunkPos
                    );
                    if (structureGeneratingAt != null) {
                        pair = structureGeneratingAt;
                        d = d1;
                    }
                }
            }

            return pair;
        }
    }

    private static @Nullable Pair<BlockPos, Holder<Structure>> getNearestGeneratedStructure(
        Set<Holder<Structure>> structureHoldersSet,
        LevelReader level,
        StructureManager structureManager,
        int x,
        int y,
        int z,
        boolean skipKnownStructures,
        long seed,
        RandomSpreadStructurePlacement spreadPlacement
    ) {
        int spacing = spreadPlacement.spacing();

        for (int i = -z; i <= z; i++) {
            // Paper start - Perf: iterate over border chunks instead of entire square chunk area
            final int radius = z;
            boolean flag = i == -z || i == z; final boolean onBorderAlongZAxis = flag; // Paper - OBFHELPER

            for (int i1 = -radius; i1 <= radius; i1 += onBorderAlongZAxis ? 1 : radius * 2) {
                // boolean flag1 = i1 == -z || i1 == z;
                // if (flag || flag1) {
                if (true) {
            // Paper end - Perf: iterate over border chunks instead of entire square chunk area
                    int i2 = x + spacing * i;
                    int i3 = y + spacing * i1;
                    ChunkPos potentialStructureChunk = spreadPlacement.getPotentialStructureChunk(seed, i2, i3);
                    Pair<BlockPos, Holder<Structure>> structureGeneratingAt = getStructureGeneratingAt(
                        structureHoldersSet, level, structureManager, skipKnownStructures, spreadPlacement, potentialStructureChunk
                    );
                    if (structureGeneratingAt != null) {
                        return structureGeneratingAt;
                    }
                }
            }
        }

        return null;
    }

    private static @Nullable Pair<BlockPos, Holder<Structure>> getStructureGeneratingAt(
        Set<Holder<Structure>> structureHoldersSet,
        LevelReader level,
        StructureManager structureManager,
        boolean skipKnownStructures,
        StructurePlacement placement,
        ChunkPos chunkPos
    ) {
        for (Holder<Structure> holder : structureHoldersSet) {
            StructureCheckResult structureCheckResult = structureManager.checkStructurePresence(chunkPos, holder.value(), placement, skipKnownStructures);
            if (structureCheckResult != StructureCheckResult.START_NOT_PRESENT) {
                if (!skipKnownStructures && structureCheckResult == StructureCheckResult.START_PRESENT) {
                    return Pair.of(placement.getLocatePos(chunkPos), holder);
                }

                ChunkAccess chunk = ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemLevelReader)level).moonrise$syncLoadNonFull(chunkPos.x, chunkPos.z, ChunkStatus.STRUCTURE_STARTS); // Paper - rewrite chunk system
                StructureStart startForStructure = structureManager.getStartForStructure(SectionPos.bottomOf(chunk), holder.value(), chunk);
                if (startForStructure != null && startForStructure.isValid() && (!skipKnownStructures || tryAddReference(structureManager, startForStructure))) {
                    return Pair.of(placement.getLocatePos(startForStructure.getChunkPos()), holder);
                }
            }
        }

        return null;
    }

    private static boolean tryAddReference(StructureManager structureManager, StructureStart structureStart) {
        if (structureStart.tryReference()) { // Folia - region threading
            structureManager.addReference(structureStart);
            return true;
        } else {
            return false;
        }
    }

    public void addVanillaDecorations(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) { // CraftBukkit - rename
        ChunkPos pos = chunk.getPos();
        if (!SharedConstants.debugVoidTerrain(pos)) {
            SectionPos sectionPos = SectionPos.of(pos, level.getMinSectionY());
            BlockPos blockPos = sectionPos.origin();
            Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
            Map<Integer, List<Structure>> map = registry.stream().collect(Collectors.groupingBy(structure1 -> structure1.step().ordinal()));
            List<FeatureSorter.StepFeatureData> list = this.featuresPerStep.get();
            // Leaf start - Matter - Secure Seed
            WorldgenRandom worldgenRandom = me.earthme.luminol.config.modules.function.SecureSeedConfig.enabled
                ? new su.plo.matter.WorldgenCryptoRandom(blockPos.getX(), blockPos.getZ(), su.plo.matter.Globals.Salt.UNDEFINED, 0)
                : new WorldgenRandom(new XoroshiroRandomSource(RandomSupport.generateUniqueSeed()));
            // Leaf end - Matter - Secure Seed
            long l = worldgenRandom.setDecorationSeed(level.getSeed(), blockPos.getX(), blockPos.getZ());
            Set<Holder<Biome>> set = new ObjectArraySet<>();
            ChunkPos.rangeClosed(sectionPos.chunk(), 1).forEach(chunkPos -> {
                ChunkAccess chunk1 = level.getChunk(chunkPos.x, chunkPos.z);

                for (LevelChunkSection levelChunkSection : chunk1.getSections()) {
                    levelChunkSection.getBiomes().getAll(set::add);
                }
            });
            set.retainAll(this.biomeSource.possibleBiomes());
            int size = list.size();

            try {
                Registry<PlacedFeature> registry1 = level.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
                int max = Math.max(GenerationStep.Decoration.values().length, size);

                for (int i = 0; i < max; i++) {
                    int i1 = 0;
                    if (structureManager.shouldGenerateStructures()) {
                        for (Structure structure : map.getOrDefault(i, Collections.emptyList())) {
                            worldgenRandom.setFeatureSeed(l, i1, i);
                            Supplier<String> supplier = () -> registry.getResourceKey(structure).map(Object::toString).orElseGet(structure::toString);

                            try {
                                level.setCurrentlyGenerating(supplier);
                                structureManager.startsForStructure(sectionPos, structure)
                                    .forEach(
                                        structureStart -> structureStart.placeInChunk(
                                            level, structureManager, this, worldgenRandom, getWritableArea(chunk), pos
                                        )
                                    );
                            } catch (Exception var29) {
                                CrashReport crashReport = CrashReport.forThrowable(var29, "Feature placement");
                                crashReport.addCategory("Feature").setDetail("Description", supplier::get);
                                throw new ReportedException(crashReport);
                            }

                            i1++;
                        }
                    }

                    if (i < size) {
                        IntSet set1 = new IntArraySet();

                        for (Holder<Biome> holder : set) {
                            List<HolderSet<PlacedFeature>> list2 = this.generationSettingsGetter.apply(holder).features();
                            if (i < list2.size()) {
                                HolderSet<PlacedFeature> holderSet = list2.get(i);
                                FeatureSorter.StepFeatureData stepFeatureData = list.get(i);
                                holderSet.stream().map(Holder::value).forEach(feature -> set1.add(stepFeatureData.indexMapping().applyAsInt(feature)));
                            }
                        }

                        int size1 = set1.size();
                        int[] ints = set1.toIntArray();
                        Arrays.sort(ints);
                        FeatureSorter.StepFeatureData stepFeatureData1 = list.get(i);

                        for (int i2 = 0; i2 < size1; i2++) {
                            int i3 = ints[i2];
                            PlacedFeature placedFeature = stepFeatureData1.features().get(i3);
                            Supplier<String> supplier1 = () -> registry1.getResourceKey(placedFeature).map(Object::toString).orElseGet(placedFeature::toString);
                            // Paper start - Configurable feature seeds; change populationSeed used in random
                            long featurePopulationSeed = l;
                            final long configFeatureSeed = level.getMinecraftWorld().paperConfig().featureSeeds.features.getLong(placedFeature.feature());
                            if (configFeatureSeed != -1) {
                                featurePopulationSeed = worldgenRandom.setDecorationSeed(configFeatureSeed, blockPos.getX(), blockPos.getZ()); // See WorldgenRandom.setDecorationSeed from above
                            }
                            worldgenRandom.setFeatureSeed(featurePopulationSeed, i3, i);
                            // Paper end - Configurable feature seeds

                            try {
                                level.setCurrentlyGenerating(supplier1);
                                placedFeature.placeWithBiomeCheck(level, this, worldgenRandom, blockPos);
                            } catch (Exception var30) {
                                CrashReport crashReport1 = CrashReport.forThrowable(var30, "Feature placement");
                                crashReport1.addCategory("Feature").setDetail("Description", supplier1::get);
                                throw new ReportedException(crashReport1);
                            }
                        }
                    }
                }

                level.setCurrentlyGenerating(null);
                if (SharedConstants.DEBUG_FEATURE_COUNT) {
                    FeatureCountTracker.chunkDecorated(level.getLevel());
                }
            } catch (Exception var31) {
                CrashReport crashReport2 = CrashReport.forThrowable(var31, "Biome decoration");
                crashReport2.addCategory("Generation").setDetail("CenterX", pos.x).setDetail("CenterZ", pos.z).setDetail("Decoration Seed", l);
                throw new ReportedException(crashReport2);
            }
        }
    }
   // CraftBukkit start
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        this.applyBiomeDecoration(level, chunk, structureManager, true);
    }

    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager, boolean addVanillaDecorations) {
        if (addVanillaDecorations) {
            this.addVanillaDecorations(level, chunk, structureManager);
        }

        org.bukkit.World world = level.getMinecraftWorld().getWorld();
        // only call when a populator is present (prevents unnecessary entity conversion)
        if (!world.getPopulators().isEmpty()) {
            org.bukkit.craftbukkit.generator.CraftLimitedRegion limitedRegion = new org.bukkit.craftbukkit.generator.CraftLimitedRegion(level, chunk.getPos());
            int x = chunk.getPos().x;
            int z = chunk.getPos().z;
            for (org.bukkit.generator.BlockPopulator populator : world.getPopulators()) {
                WorldgenRandom seededrandom = new WorldgenRandom(new net.minecraft.world.level.levelgen.LegacyRandomSource(level.getSeed()));
                seededrandom.setDecorationSeed(level.getSeed(), x, z);
                populator.populate(world, new org.bukkit.craftbukkit.util.RandomSourceWrapper.RandomWrapper(seededrandom), x, z, limitedRegion);
            }
            limitedRegion.saveEntities();
            limitedRegion.breakLink();
        }
    }
    // CraftBukkit end

    private static BoundingBox getWritableArea(ChunkAccess chunk) {
        ChunkPos pos = chunk.getPos();
        int minBlockX = pos.getMinBlockX();
        int minBlockZ = pos.getMinBlockZ();
        LevelHeightAccessor heightAccessorForGeneration = chunk.getHeightAccessorForGeneration();
        int i = heightAccessorForGeneration.getMinY() + 1;
        int maxY = heightAccessorForGeneration.getMaxY();
        return new BoundingBox(minBlockX, i, minBlockZ, minBlockX + 15, maxY, minBlockZ + 15);
    }

    public abstract void buildSurface(WorldGenRegion region, StructureManager structureManager, RandomState random, ChunkAccess chunk);

    public abstract void spawnOriginalMobs(WorldGenRegion region);

    public int getSpawnHeight(LevelHeightAccessor level) {
        return 64;
    }

    public BiomeSource getBiomeSource() {
        return this.biomeSource;
    }

    public abstract int getGenDepth();

    public WeightedList<MobSpawnSettings.SpawnerData> getMobsAt(Holder<Biome> biome, StructureManager structureManager, MobCategory category, BlockPos pos) {
        Map<Structure, LongSet> allStructuresAt = structureManager.getAllStructuresAt(pos);

        for (Entry<Structure, LongSet> entry : allStructuresAt.entrySet()) {
            Structure structure = entry.getKey();
            StructureSpawnOverride structureSpawnOverride = structure.spawnOverrides().get(category);
            if (structureSpawnOverride != null) {
                MutableBoolean mutableBoolean = new MutableBoolean(false);
                Predicate<StructureStart> predicate = structureSpawnOverride.boundingBox() == StructureSpawnOverride.BoundingBoxType.PIECE
                    ? structureStart -> structureManager.structureHasPieceAt(pos, structureStart)
                    : structureStart -> structureStart.getBoundingBox().isInside(pos);
                structureManager.fillStartsForStructure(structure, entry.getValue(), structureStart -> {
                    if (mutableBoolean.isFalse() && predicate.test(structureStart)) {
                        mutableBoolean.setTrue();
                    }
                });
                if (mutableBoolean.isTrue()) {
                    return structureSpawnOverride.spawns();
                }
            }
        }

        return biome.value().getMobSettings().getMobs(category);
    }

    public void createStructures(
        RegistryAccess registryAccess,
        ChunkGeneratorStructureState structureState,
        StructureManager structureManager,
        ChunkAccess chunk,
        StructureTemplateManager structureTemplateManager,
        ResourceKey<Level> level
    ) {
        if (!SharedConstants.DEBUG_DISABLE_STRUCTURES) {
            ChunkPos pos = chunk.getPos();
            SectionPos sectionPos = SectionPos.bottomOf(chunk);
            RandomState randomState = structureState.randomState();
            structureState.possibleStructureSets()
                .forEach(
                    structures -> {
                        StructurePlacement structurePlacement = structures.value().placement();
                        List<StructureSet.StructureSelectionEntry> list = structures.value().structures();

                        for (StructureSet.StructureSelectionEntry structureSelectionEntry : list) {
                            StructureStart startForStructure = structureManager.getStartForStructure(
                                sectionPos, structureSelectionEntry.structure().value(), chunk
                            );
                            if (startForStructure != null && startForStructure.isValid()) {
                                return;
                            }
                        }

                        if (structurePlacement.isStructureChunk(structureState, pos.x, pos.z, structurePlacement instanceof net.minecraft.world.level.chunk.ChunkGeneratorStructureState.KeyedRandomSpreadStructurePlacement keyed ? keyed.key : null)) { // Paper - Add missing structure set seed configs
                            if (list.size() == 1) {
                                this.tryGenerateStructure(
                                    list.get(0),
                                    structureManager,
                                    registryAccess,
                                    randomState,
                                    structureTemplateManager,
                                    structureState.getLevelSeed(),
                                    chunk,
                                    pos,
                                    sectionPos,
                                    level
                                );
                            } else {
                                ArrayList<StructureSet.StructureSelectionEntry> list1 = new ArrayList<>(list.size());
                                list1.addAll(list);
                                // Leaf start - Matter - Secure Seed
                                WorldgenRandom worldgenRandom;
                                if (me.earthme.luminol.config.modules.function.SecureSeedConfig.enabled) {
                                    worldgenRandom = new su.plo.matter.WorldgenCryptoRandom(pos.x, pos.z, su.plo.matter.Globals.Salt.GENERATE_FEATURE, 0);
                                } else {
                                    worldgenRandom = new WorldgenRandom(new LegacyRandomSource(0L));
                                    worldgenRandom.setLargeFeatureSeed(structureState.getLevelSeed(), pos.x, pos.z);
                                }
                                // Leaf end - Matter - Secure Seed
                                int i = 0;

                                for (StructureSet.StructureSelectionEntry structureSelectionEntry1 : list1) {
                                    i += structureSelectionEntry1.weight();
                                }

                                while (!list1.isEmpty()) {
                                    int randomInt = worldgenRandom.nextInt(i);
                                    int i1 = 0;

                                    for (StructureSet.StructureSelectionEntry structureSelectionEntry2 : list1) {
                                        randomInt -= structureSelectionEntry2.weight();
                                        if (randomInt < 0) {
                                            break;
                                        }

                                        i1++;
                                    }

                                    StructureSet.StructureSelectionEntry structureSelectionEntry3 = list1.get(i1);
                                    if (this.tryGenerateStructure(
                                        structureSelectionEntry3,
                                        structureManager,
                                        registryAccess,
                                        randomState,
                                        structureTemplateManager,
                                        structureState.getLevelSeed(),
                                        chunk,
                                        pos,
                                        sectionPos,
                                        level
                                    )) {
                                        return;
                                    }

                                    list1.remove(i1);
                                    i -= structureSelectionEntry3.weight();
                                }
                            }
                        }
                    }
                );
        }
    }

    private boolean tryGenerateStructure(
        StructureSet.StructureSelectionEntry structureSelectionEntry,
        StructureManager structureManager,
        RegistryAccess registryAccess,
        RandomState random,
        StructureTemplateManager structureTemplateManager,
        long seed,
        ChunkAccess chunk,
        ChunkPos chunkPos,
        SectionPos sectionPos,
        ResourceKey<Level> level
    ) {
        Structure structure = structureSelectionEntry.structure().value();
        int i = fetchReferences(structureManager, chunk, sectionPos, structure);
        HolderSet<Biome> holderSet = structure.biomes();
        Predicate<Holder<Biome>> predicate = holderSet::contains;
        StructureStart structureStart = structure.generate(
            structureSelectionEntry.structure(),
            level,
            registryAccess,
            this,
            this.biomeSource,
            random,
            structureTemplateManager,
            seed,
            chunkPos,
            i,
            chunk,
            predicate
        );
        if (structureStart.isValid()) {
            // CraftBukkit start
            BoundingBox box = structureStart.getBoundingBox();
            org.bukkit.event.world.AsyncStructureSpawnEvent event = new org.bukkit.event.world.AsyncStructureSpawnEvent(structureManager.level.getMinecraftWorld().getWorld(), org.bukkit.craftbukkit.generator.structure.CraftStructure.minecraftToBukkit(structure), new org.bukkit.util.BoundingBox(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()), chunkPos.x, chunkPos.z);
            org.bukkit.Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return true;
            }
            // CraftBukkit end
            structureManager.setStartForStructure(sectionPos, structure, structureStart, chunk);
            return true;
        } else {
            return false;
        }
    }

    private static int fetchReferences(StructureManager structureManager, ChunkAccess chunk, SectionPos sectionPos, Structure structure) {
        StructureStart startForStructure = structureManager.getStartForStructure(sectionPos, structure, chunk);
        return startForStructure != null ? startForStructure.getReferences() : 0;
    }

    public void createReferences(WorldGenLevel level, StructureManager structureManager, ChunkAccess chunk) {
        int i = 8;
        ChunkPos pos = chunk.getPos();
        int i1 = pos.x;
        int i2 = pos.z;
        int minBlockX = pos.getMinBlockX();
        int minBlockZ = pos.getMinBlockZ();
        SectionPos sectionPos = SectionPos.bottomOf(chunk);

        for (int i3 = i1 - 8; i3 <= i1 + 8; i3++) {
            for (int i4 = i2 - 8; i4 <= i2 + 8; i4++) {
                long packedChunkPos = ChunkPos.asLong(i3, i4);

                for (StructureStart structureStart : level.getChunk(i3, i4).getAllStarts().values()) {
                    try {
                        if (structureStart.isValid() && structureStart.getBoundingBox().intersects(minBlockX, minBlockZ, minBlockX + 15, minBlockZ + 15)) {
                            structureManager.addReferenceForStructure(sectionPos, structureStart.getStructure(), packedChunkPos, chunk);
                        }
                    } catch (Exception var21) {
                        CrashReport crashReport = CrashReport.forThrowable(var21, "Generating structure reference");
                        CrashReportCategory crashReportCategory = crashReport.addCategory("Structure");
                        Optional<? extends Registry<Structure>> optional = level.registryAccess().lookup(Registries.STRUCTURE);
                        crashReportCategory.setDetail(
                            "Id", () -> optional.<String>map(registry -> registry.getKey(structureStart.getStructure()).toString()).orElse("UNKNOWN")
                        );
                        crashReportCategory.setDetail("Name", () -> BuiltInRegistries.STRUCTURE_TYPE.getKey(structureStart.getStructure().type()).toString());
                        crashReportCategory.setDetail("Class", () -> structureStart.getStructure().getClass().getCanonicalName());
                        throw new ReportedException(crashReport);
                    }
                }
            }
        }
    }

    public abstract CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk);

    public abstract int getSeaLevel();

    public abstract int getMinY();

    public abstract int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random);

    public abstract NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState random);

    public int getFirstFreeHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        return this.getBaseHeight(x, z, type, level, random);
    }

    public int getFirstOccupiedHeight(int x, int z, Heightmap.Types types, LevelHeightAccessor level, RandomState random) {
        return this.getBaseHeight(x, z, types, level, random) - 1;
    }

    public abstract void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos);

    @Deprecated
    public BiomeGenerationSettings getBiomeGenerationSettings(Holder<Biome> biome) {
        return this.generationSettingsGetter.apply(biome);
    }
}
