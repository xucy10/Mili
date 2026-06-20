package net.minecraft.world.level.levelgen.structure;

import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2BooleanMap;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.visitors.CollectFields;
import net.minecraft.nbt.visitors.FieldSelector;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.storage.ChunkScanAccess;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class StructureCheck {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int NO_STRUCTURE = -1;
    private final ChunkScanAccess storageAccess;
    private final RegistryAccess registryAccess;
    private final StructureTemplateManager structureTemplateManager;
    private final ResourceKey<net.minecraft.world.level.dimension.LevelStem> dimension; // Paper - fix missing CB diff
    private final ChunkGenerator chunkGenerator;
    private final RandomState randomState;
    private final LevelHeightAccessor heightAccessor;
    private final BiomeSource biomeSource;
    private final long seed;
    private final DataFixer fixerUpper;
    // Paper start - rewrite chunk system
    // make sure to purge entries from the maps to prevent memory leaks
    private static final int CHUNK_TOTAL_LIMIT = 50 * (2 * 100 + 1) * (2 * 100 + 1); // cache 50 structure lookups
    private static final int PER_FEATURE_CHECK_LIMIT = 50 * (2 * 100 + 1) * (2 * 100 + 1); // cache 50 structure lookups
    private final ca.spottedleaf.moonrise.common.map.SynchronisedLong2ObjectMap<it.unimi.dsi.fastutil.objects.Object2IntMap<Structure>> loadedChunksSafe = new ca.spottedleaf.moonrise.common.map.SynchronisedLong2ObjectMap<>(CHUNK_TOTAL_LIMIT);
    private final java.util.concurrent.ConcurrentHashMap<Structure, ca.spottedleaf.moonrise.common.map.SynchronisedLong2BooleanMap> featureChecksSafe = new java.util.concurrent.ConcurrentHashMap<>();
    // Paper end - rewrite chunk system

    public StructureCheck(
        ChunkScanAccess storageAccess,
        RegistryAccess registryAccess,
        StructureTemplateManager structureTemplateManager,
        ResourceKey<net.minecraft.world.level.dimension.LevelStem> dimension, // Paper - fix missing CB diff
        ChunkGenerator chunkGenerator,
        RandomState randomState,
        LevelHeightAccessor heightAccessor,
        BiomeSource biomeSource,
        long seed,
        DataFixer fixerUpper
    ) {
        this.storageAccess = storageAccess;
        this.registryAccess = registryAccess;
        this.structureTemplateManager = structureTemplateManager;
        this.dimension = dimension;
        this.chunkGenerator = chunkGenerator;
        this.randomState = randomState;
        this.heightAccessor = heightAccessor;
        this.biomeSource = biomeSource;
        this.seed = seed;
        this.fixerUpper = fixerUpper;
    }

    // Paper start - add missing structure salt configs
    @Nullable
    private Integer getSaltOverride(Structure type) {
        if (this.heightAccessor instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            if (type instanceof net.minecraft.world.level.levelgen.structure.structures.MineshaftStructure) {
                return serverLevel.spigotConfig.mineshaftSeed;
            } else if (type instanceof net.minecraft.world.level.levelgen.structure.structures.BuriedTreasureStructure) {
                return serverLevel.spigotConfig.buriedTreasureSeed;
            }
        }
        return null;
    }
    // Paper end - add missing structure seed configs

    public StructureCheckResult checkStart(ChunkPos chunkPos, Structure structure, StructurePlacement placement, boolean skipKnownStructures) {
        long packedChunkPos = chunkPos.toLong();
        Object2IntMap<Structure> map = this.loadedChunksSafe.get(packedChunkPos); // Paper - rewrite chunk system
        if (map != null) {
            return this.checkStructureInfo(map, structure, skipKnownStructures);
        } else {
            StructureCheckResult structureCheckResult = this.tryLoadFromStorage(chunkPos, structure, skipKnownStructures, packedChunkPos);
            if (structureCheckResult != null) {
                return structureCheckResult;
            } else if (!placement.applyAdditionalChunkRestrictions(chunkPos.x, chunkPos.z, this.seed, this.getSaltOverride(structure))) { // Paper - add missing structure seed configs
                return StructureCheckResult.START_NOT_PRESENT;
            } else {
                // Paper start - rewrite chunk system
                boolean flag = this.featureChecksSafe
                    .computeIfAbsent(structure, structure1 -> new ca.spottedleaf.moonrise.common.map.SynchronisedLong2BooleanMap(PER_FEATURE_CHECK_LIMIT))
                    .getOrCompute(packedChunkPos, l -> this.canCreateStructure(chunkPos, structure));
                // Paper end - rewrite chunk system
                return !flag ? StructureCheckResult.START_NOT_PRESENT : StructureCheckResult.CHUNK_LOAD_NEEDED;
            }
        }
    }

    private boolean canCreateStructure(ChunkPos chunkPos, Structure structure) {
        return structure.findValidGenerationPoint(
                new Structure.GenerationContext(
                    this.registryAccess,
                    this.chunkGenerator,
                    this.biomeSource,
                    this.randomState,
                    this.structureTemplateManager,
                    this.seed,
                    chunkPos,
                    this.heightAccessor,
                    structure.biomes()::contains
                )
            )
            .isPresent();
    }

    private @Nullable StructureCheckResult tryLoadFromStorage(ChunkPos chunkPos, Structure structure, boolean skipKnownStructures, long packedChunkPos) {
        CollectFields collectFields = new CollectFields(
            new FieldSelector(IntTag.TYPE, "DataVersion"),
            new FieldSelector("Level", "Structures", CompoundTag.TYPE, "Starts"),
            new FieldSelector("structures", CompoundTag.TYPE, "starts")
        );

        try {
            this.storageAccess.scanChunk(chunkPos, collectFields).join();
        } catch (Exception var13) {
            LOGGER.warn("Failed to read chunk {}", chunkPos, var13);
            return StructureCheckResult.CHUNK_LOAD_NEEDED;
        }

        if (!(collectFields.getResult() instanceof CompoundTag compoundTag)) {
            return null;
        } else {
            int dataVersion = NbtUtils.getDataVersion(compoundTag);
            if (dataVersion <= 1493) {
                return StructureCheckResult.CHUNK_LOAD_NEEDED;
            } else {
                SimpleRegionStorage.injectDatafixingContext(
                    compoundTag, ChunkMap.getChunkDataFixContextTag(this.dimension, this.chunkGenerator.getTypeNameForDataFixer())
                );

                CompoundTag compoundTag1;
                try {
                    compoundTag1 = ca.spottedleaf.dataconverter.minecraft.MCDataConverter.convertTag(ca.spottedleaf.dataconverter.minecraft.datatypes.MCTypeRegistry.CHUNK, compoundTag, dataVersion, ca.spottedleaf.dataconverter.minecraft.util.Version.getCurrentVersion()); // Paper - replace chunk converter
                } catch (Exception var12) {
                    LOGGER.warn("Failed to partially datafix chunk {}", chunkPos, var12);
                    return StructureCheckResult.CHUNK_LOAD_NEEDED;
                }

                Object2IntMap<Structure> map = this.loadStructures(compoundTag1);
                if (map == null) {
                    return null;
                } else {
                    this.storeFullResults(packedChunkPos, map);
                    return this.checkStructureInfo(map, structure, skipKnownStructures);
                }
            }
        }
    }

    private @Nullable Object2IntMap<Structure> loadStructures(CompoundTag tag) {
        Optional<CompoundTag> optional = tag.getCompound("structures").flatMap(compoundTag1 -> compoundTag1.getCompound("starts"));
        if (optional.isEmpty()) {
            return null;
        } else {
            CompoundTag compoundTag = optional.get();
            if (compoundTag.isEmpty()) {
                return Object2IntMaps.emptyMap();
            } else {
                Object2IntMap<Structure> map = new Object2IntOpenHashMap<>();
                Registry<Structure> registry = this.registryAccess.lookupOrThrow(Registries.STRUCTURE);
                compoundTag.forEach((string, tag1) -> {
                    Identifier identifier = Identifier.tryParse(string);
                    if (identifier != null) {
                        Structure structure = registry.getValue(identifier);
                        if (structure != null) {
                            tag1.asCompound().ifPresent(compoundTag1 -> {
                                String stringOr = compoundTag1.getStringOr("id", "");
                                if (!"INVALID".equals(stringOr)) {
                                    int intOr = compoundTag1.getIntOr("references", 0);
                                    map.put(structure, intOr);
                                }
                            });
                        }
                    }
                });
                return map;
            }
        }
    }

    private static Object2IntMap<Structure> deduplicateEmptyMap(Object2IntMap<Structure> map) {
        return map.isEmpty() ? Object2IntMaps.emptyMap() : map;
    }

    private StructureCheckResult checkStructureInfo(Object2IntMap<Structure> structureChunks, Structure structure, boolean skipKnownStructures) {
        int orDefault = structureChunks.getOrDefault(structure, -1);
        return orDefault == -1 || skipKnownStructures && orDefault != 0 ? StructureCheckResult.START_NOT_PRESENT : StructureCheckResult.START_PRESENT;
    }

    public void onStructureLoad(ChunkPos chunkPos, Map<Structure, StructureStart> chunkStarts) {
        long packedChunkPos = chunkPos.toLong();
        Object2IntMap<Structure> map = new Object2IntOpenHashMap<>();
        chunkStarts.forEach((structure, structureStart) -> {
            if (structureStart.isValid()) {
                map.put(structure, structureStart.getReferences());
            }
        });
        this.storeFullResults(packedChunkPos, map);
    }

    private void storeFullResults(long chunkPos, Object2IntMap<Structure> structureChunks) {
        // Paper start - rewrite chunk system
        this.loadedChunksSafe.put(chunkPos, deduplicateEmptyMap(structureChunks));
        // once we insert into loadedChunks, we don't really need to be very careful about removing everything
        // from this map, as everything that checks this map uses loadedChunks first
        // so, one way or another it's a race condition that doesn't matter
        for (ca.spottedleaf.moonrise.common.map.SynchronisedLong2BooleanMap value : this.featureChecksSafe.values()) {
            value.remove(chunkPos);
        }
        // Paper end - rewrite chunk system
    }

    public void incrementReference(ChunkPos pos, Structure structure) {
        this.loadedChunksSafe.compute(pos.toLong(), (_long, map) -> { // Paper start - rewrite chunk system
            if (map == null) {
                map = new Object2IntOpenHashMap<>();
            } else {
                map = map instanceof Object2IntOpenHashMap<Structure> fastClone ? fastClone.clone() : new Object2IntOpenHashMap<>(map);
            }
            // Paper end - rewrite chunk system

            map.computeInt(structure, (structure1, integer) -> integer == null ? 1 : integer + 1);
            return map;
        });
    }
}
