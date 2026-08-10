package net.minecraft.world.level.levelgen.structure;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.mojang.datafixers.DataFixer;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.LegacyTagFixer;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.jspecify.annotations.Nullable;

public class LegacyStructureDataHandler implements LegacyTagFixer {
    public static final int LAST_MONOLYTH_STRUCTURE_DATA_VERSION = 1493;
    private static final Map<String, String> CURRENT_TO_LEGACY_MAP = Util.make(Maps.newHashMap(), map -> {
        map.put("Village", "Village");
        map.put("Mineshaft", "Mineshaft");
        map.put("Mansion", "Mansion");
        map.put("Igloo", "Temple");
        map.put("Desert_Pyramid", "Temple");
        map.put("Jungle_Pyramid", "Temple");
        map.put("Swamp_Hut", "Temple");
        map.put("Stronghold", "Stronghold");
        map.put("Monument", "Monument");
        map.put("Fortress", "Fortress");
        map.put("EndCity", "EndCity");
    });
    private static final Map<String, String> LEGACY_TO_CURRENT_MAP = Util.make(Maps.newHashMap(), map -> {
        map.put("Iglu", "Igloo");
        map.put("TeDP", "Desert_Pyramid");
        map.put("TeJP", "Jungle_Pyramid");
        map.put("TeSH", "Swamp_Hut");
    });
    private static final Set<String> OLD_STRUCTURE_REGISTRY_KEYS = Set.of(
        "pillager_outpost",
        "mineshaft",
        "mansion",
        "jungle_pyramid",
        "desert_pyramid",
        "igloo",
        "ruined_portal",
        "shipwreck",
        "swamp_hut",
        "stronghold",
        "monument",
        "ocean_ruin",
        "fortress",
        "endcity",
        "buried_treasure",
        "village",
        "nether_fossil",
        "bastion_remnant"
    );
    private final boolean hasLegacyData;
    private final Map<String, Long2ObjectMap<CompoundTag>> dataMap = Maps.newHashMap();
    private final Map<String, StructureFeatureIndexSavedData> indexMap = Maps.newHashMap();
    private final @Nullable DimensionDataStorage dimensionDataStorage;
    private final List<String> legacyKeys;
    private final List<String> currentKeys;
    private final DataFixer dataFixer;
    private boolean cachesInitialized;

    public LegacyStructureDataHandler(
        @Nullable DimensionDataStorage dimensionDataStorage, List<String> legacyKeys, List<String> currentKeys, DataFixer dataFixer
    ) {
        this.dimensionDataStorage = dimensionDataStorage;
        this.legacyKeys = legacyKeys;
        this.currentKeys = currentKeys;
        this.dataFixer = dataFixer;
        boolean flag = false;

        for (String string : this.currentKeys) {
            flag |= this.dataMap.get(string) != null;
        }

        this.hasLegacyData = flag;
    }

    @Override
    public void markChunkDone(ChunkPos chunkPos) {
        long packedChunkPos = chunkPos.toLong();

        for (String string : this.legacyKeys) {
            StructureFeatureIndexSavedData structureFeatureIndexSavedData = this.indexMap.get(string);
            if (structureFeatureIndexSavedData != null && structureFeatureIndexSavedData.hasUnhandledIndex(packedChunkPos)) {
                structureFeatureIndexSavedData.removeIndex(packedChunkPos);
            }
        }
    }

    @Override
    public int targetDataVersion() {
        return 1493;
    }

    @Override
    public CompoundTag applyFix(CompoundTag tag) {
        if (!this.cachesInitialized && this.dimensionDataStorage != null) {
            this.populateCaches(this.dimensionDataStorage);
        }

        int dataVersion = NbtUtils.getDataVersion(tag);
        if (dataVersion < 1493) {
            tag = ca.spottedleaf.dataconverter.minecraft.MCDataConverter.convertTag(ca.spottedleaf.dataconverter.minecraft.datatypes.MCTypeRegistry.CHUNK, tag, dataVersion, 1493); // Paper - rewrite dataconverter system
            if (tag.getCompound("Level").flatMap(compoundTag -> compoundTag.getBoolean("hasLegacyStructureData")).orElse(false)) {
                tag = this.updateFromLegacy(tag);
            }
        }

        return tag;
    }

    private CompoundTag updateFromLegacy(CompoundTag tag) {
        CompoundTag compoundOrEmpty = tag.getCompoundOrEmpty("Level");
        ChunkPos chunkPos = new ChunkPos(compoundOrEmpty.getIntOr("xPos", 0), compoundOrEmpty.getIntOr("zPos", 0));
        if (this.isUnhandledStructureStart(chunkPos.x, chunkPos.z)) {
            tag = this.updateStructureStart(tag, chunkPos);
        }

        CompoundTag compoundOrEmpty1 = compoundOrEmpty.getCompoundOrEmpty("Structures");
        CompoundTag compoundOrEmpty2 = compoundOrEmpty1.getCompoundOrEmpty("References");

        for (String string : this.currentKeys) {
            boolean flag = OLD_STRUCTURE_REGISTRY_KEYS.contains(string.toLowerCase(Locale.ROOT));
            if (!compoundOrEmpty2.getLongArray(string).isPresent() && flag) {
                int i = 8;
                LongList list = new LongArrayList();

                for (int i1 = chunkPos.x - 8; i1 <= chunkPos.x + 8; i1++) {
                    for (int i2 = chunkPos.z - 8; i2 <= chunkPos.z + 8; i2++) {
                        if (this.hasLegacyStart(i1, i2, string)) {
                            list.add(ChunkPos.asLong(i1, i2));
                        }
                    }
                }

                compoundOrEmpty2.putLongArray(string, list.toLongArray());
            }
        }

        compoundOrEmpty1.put("References", compoundOrEmpty2);
        compoundOrEmpty.put("Structures", compoundOrEmpty1);
        tag.put("Level", compoundOrEmpty);
        return tag;
    }

    private boolean hasLegacyStart(int chunkX, int chunkZ, String key) {
        return this.hasLegacyData
            && this.dataMap.get(key) != null
            && this.indexMap.get(CURRENT_TO_LEGACY_MAP.get(key)).hasStartIndex(ChunkPos.asLong(chunkX, chunkZ));
    }

    private boolean isUnhandledStructureStart(int chunkX, int chunkZ) {
        if (!this.hasLegacyData) {
            return false;
        } else {
            for (String string : this.currentKeys) {
                if (this.dataMap.get(string) != null && this.indexMap.get(CURRENT_TO_LEGACY_MAP.get(string)).hasUnhandledIndex(ChunkPos.asLong(chunkX, chunkZ))
                    )
                 {
                    return true;
                }
            }

            return false;
        }
    }

    private CompoundTag updateStructureStart(CompoundTag tag, ChunkPos chunkPos) {
        CompoundTag compoundOrEmpty = tag.getCompoundOrEmpty("Level");
        CompoundTag compoundOrEmpty1 = compoundOrEmpty.getCompoundOrEmpty("Structures");
        CompoundTag compoundOrEmpty2 = compoundOrEmpty1.getCompoundOrEmpty("Starts");

        for (String string : this.currentKeys) {
            Long2ObjectMap<CompoundTag> map = this.dataMap.get(string);
            if (map != null) {
                long packedChunkPos = chunkPos.toLong();
                if (this.indexMap.get(CURRENT_TO_LEGACY_MAP.get(string)).hasUnhandledIndex(packedChunkPos)) {
                    CompoundTag compoundTag = map.get(packedChunkPos);
                    if (compoundTag != null) {
                        compoundOrEmpty2.put(string, compoundTag);
                    }
                }
            }
        }

        compoundOrEmpty1.put("Starts", compoundOrEmpty2);
        compoundOrEmpty.put("Structures", compoundOrEmpty1);
        tag.put("Level", compoundOrEmpty);
        return tag;
    }

    private synchronized void populateCaches(DimensionDataStorage storage) {
        if (!this.cachesInitialized) {
            for (String string : this.legacyKeys) {
                CompoundTag compoundTag = new CompoundTag();

                try {
                    compoundTag = storage.readTagFromDisk(string, DataFixTypes.SAVED_DATA_STRUCTURE_FEATURE_INDICES, 1493)
                        .getCompoundOrEmpty("data")
                        .getCompoundOrEmpty("Features");
                    if (compoundTag.isEmpty()) {
                        continue;
                    }
                } catch (IOException var8) {
                }

                compoundTag.forEach(
                    (string2, tag) -> {
                        if (tag instanceof CompoundTag compoundTag1) {
                            long packedChunkPos = ChunkPos.asLong(compoundTag1.getIntOr("ChunkX", 0), compoundTag1.getIntOr("ChunkZ", 0));
                            ListTag listOrEmpty = compoundTag1.getListOrEmpty("Children");
                            if (!listOrEmpty.isEmpty()) {
                                Optional<String> optional = listOrEmpty.getCompound(0).flatMap(compoundTag2 -> compoundTag2.getString("id"));
                                optional.map(LEGACY_TO_CURRENT_MAP::get).ifPresent(string3 -> compoundTag1.putString("id", string3));
                            }

                            compoundTag1.getString("id")
                                .ifPresent(
                                    string3 -> this.dataMap
                                        .computeIfAbsent(string3, string4 -> new Long2ObjectOpenHashMap<>())
                                        .put(packedChunkPos, compoundTag1)
                                );
                        }
                    }
                );
                String string1 = string + "_index";
                StructureFeatureIndexSavedData structureFeatureIndexSavedData = storage.computeIfAbsent(StructureFeatureIndexSavedData.type(string1));
                if (structureFeatureIndexSavedData.getAll().isEmpty()) {
                    StructureFeatureIndexSavedData structureFeatureIndexSavedData1 = new StructureFeatureIndexSavedData();
                    this.indexMap.put(string, structureFeatureIndexSavedData1);
                    compoundTag.forEach((string2, tag) -> {
                        if (tag instanceof CompoundTag compoundTag1) {
                            structureFeatureIndexSavedData1.addIndex(ChunkPos.asLong(compoundTag1.getIntOr("ChunkX", 0), compoundTag1.getIntOr("ChunkZ", 0)));
                        }
                    });
                } else {
                    this.indexMap.put(string, structureFeatureIndexSavedData);
                }
            }

            this.cachesInitialized = true;
        }
    }

    public static Supplier<LegacyTagFixer> getLegacyTagFixer(ResourceKey<Level> level, Supplier<@Nullable DimensionDataStorage> storage, DataFixer dataFixer) {
        ResourceKey<net.minecraft.world.level.dimension.LevelStem> stemKey = net.minecraft.core.registries.Registries.levelToLevelStem(level); // CraftBukkit
        if (stemKey == net.minecraft.world.level.dimension.LevelStem.OVERWORLD) { // CraftBukkit
            return () -> new LegacyStructureDataHandler(
                storage.get(),
                ImmutableList.of("Monument", "Stronghold", "Village", "Mineshaft", "Temple", "Mansion"),
                ImmutableList.of("Village", "Mineshaft", "Mansion", "Igloo", "Desert_Pyramid", "Jungle_Pyramid", "Swamp_Hut", "Stronghold", "Monument"),
                dataFixer
            );
        } else if (stemKey == net.minecraft.world.level.dimension.LevelStem.NETHER) { // CraftBukkit
            List<String> list = ImmutableList.of("Fortress");
            return () -> new LegacyStructureDataHandler(storage.get(), list, list, dataFixer);
        } else if (stemKey == net.minecraft.world.level.dimension.LevelStem.END) { // CraftBukkit
            List<String> list = ImmutableList.of("EndCity");
            return () -> new LegacyStructureDataHandler(storage.get(), list, list, dataFixer);
        } else {
            return LegacyTagFixer.EMPTY;
        }
    }
}
