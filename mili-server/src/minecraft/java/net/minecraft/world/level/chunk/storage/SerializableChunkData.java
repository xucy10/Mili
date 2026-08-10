package net.minecraft.world.level.chunk.storage;

import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import it.unimi.dsi.fastutil.shorts.ShortList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Map.Entry;
import net.minecraft.Optionull;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtException;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.ShortTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkType;
import net.minecraft.world.level.levelgen.BelowZeroRetrogen;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.ProtoChunkTicks;
import net.minecraft.world.ticks.SavedTick;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public record SerializableChunkData(
    PalettedContainerFactory containerFactory,
    ChunkPos chunkPos,
    int minSectionY,
    long lastUpdateTime,
    long inhabitedTime,
    ChunkStatus chunkStatus,
    BlendingData.@Nullable Packed blendingData,
    @Nullable BelowZeroRetrogen belowZeroRetrogen,
    UpgradeData upgradeData,
    long @Nullable [] carvingMask,
    Map<Heightmap.Types, long[]> heightmaps,
    ChunkAccess.PackedTicks packedTicks,
    @Nullable ShortList[] postProcessingSections,
    boolean lightCorrect,
    List<SerializableChunkData.SectionData> sectionData,
    List<CompoundTag> entities,
    List<CompoundTag> blockEntities,
    CompoundTag structureData
    , net.minecraft.nbt.@Nullable Tag persistentDataContainer // CraftBukkit - persistentDataContainer
) {
    private static final Codec<List<SavedTick<Block>>> BLOCK_TICKS_CODEC = SavedTick.codec(BuiltInRegistries.BLOCK.byNameCodec()).listOf();
    private static final Codec<List<SavedTick<Fluid>>> FLUID_TICKS_CODEC = SavedTick.codec(BuiltInRegistries.FLUID.byNameCodec()).listOf();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TAG_UPGRADE_DATA = "UpgradeData";
    private static final String BLOCK_TICKS_TAG = "block_ticks";
    private static final String FLUID_TICKS_TAG = "fluid_ticks";
    public static final String X_POS_TAG = "xPos";
    public static final String Z_POS_TAG = "zPos";
    public static final String HEIGHTMAPS_TAG = "Heightmaps";
    public static final String IS_LIGHT_ON_TAG = "isLightOn";
    public static final String SECTIONS_TAG = "sections";
    public static final String BLOCK_LIGHT_TAG = "BlockLight";
    public static final String SKY_LIGHT_TAG = "SkyLight";

    // Paper start - guard against serializing mismatching coordinates
    // TODO Note: This needs to be re-checked each update
    public static ChunkPos getChunkCoordinate(final CompoundTag chunkData) {
        final int dataVersion = NbtUtils.getDataVersion(chunkData);
        if (dataVersion < 2842) { // Level tag is removed after this version
            final CompoundTag levelData = chunkData.getCompoundOrEmpty("Level");
            return new ChunkPos(levelData.getIntOr("xPos", 0), levelData.getIntOr("zPos", 0));
        } else {
            return new ChunkPos(chunkData.getIntOr("xPos", 0), chunkData.getIntOr("zPos", 0));
        }
    }
    // Paper end - guard against serializing mismatching coordinates
    // Paper start - Attempt to recalculate regionfile header if it is corrupt
    // TODO: Check on update
    public static long getLastWorldSaveTime(final CompoundTag chunkData) {
        final int dataVersion = NbtUtils.getDataVersion(chunkData);
        if (dataVersion < 2842) { // Level tag is removed after this version
            final CompoundTag levelData = chunkData.getCompoundOrEmpty("Level");
            return levelData.getLongOr("LastUpdate", 0L);
        } else {
            return chunkData.getLongOr("LastUpdate", 0L);
        }
    }
    // Paper end - Attempt to recalculate regionfile header if it is corrupt

    // Paper start - Do not let the server load chunks from newer versions
    private static final int CURRENT_DATA_VERSION = net.minecraft.SharedConstants.getCurrentVersion().dataVersion().version();
    private static final boolean JUST_CORRUPT_IT = Boolean.getBoolean("Paper.ignoreWorldDataVersion");
    // Paper end - Do not let the server load chunks from newer versions

    public static SerializableChunkData parse(LevelHeightAccessor level, PalettedContainerFactory containerFactory, CompoundTag tag) {
        net.minecraft.server.level.ServerLevel serverLevel = (net.minecraft.server.level.ServerLevel) level; // Paper - Anti-Xray This is is seemingly only called from ChunkMap, where, we have a server level. We'll fight this later if needed.
        if (tag.getString("Status").isEmpty()) {
            return null;
        } else {
            // Paper start - Do not let the server load chunks from newer versions
            tag.getInt("DataVersion").ifPresent(dataVersion -> {
                if (!JUST_CORRUPT_IT && dataVersion > CURRENT_DATA_VERSION) {
                    new RuntimeException("Server attempted to load chunk saved with newer version of minecraft! " + dataVersion + " > " + CURRENT_DATA_VERSION).printStackTrace();
                    System.exit(1);
                }
            });
            // Paper end - Do not let the server load chunks from newer versions
            ChunkPos chunkPos = new ChunkPos(tag.getIntOr("xPos", 0), tag.getIntOr("zPos", 0)); // Paper - guard against serializing mismatching coordinates; diff on change, see ChunkSerializer#getChunkCoordinate
            long longOr = tag.getLongOr("LastUpdate", 0L);
            long longOr1 = tag.getLongOr("InhabitedTime", 0L);
            ChunkStatus chunkStatus = tag.read("Status", ChunkStatus.CODEC).orElse(ChunkStatus.EMPTY);
            UpgradeData upgradeData = tag.getCompound("UpgradeData").map(compoundTag1 -> new UpgradeData(compoundTag1, level)).orElse(UpgradeData.EMPTY);
            boolean booleanOr = chunkStatus.isOrAfter(ChunkStatus.LIGHT) && (tag.get("isLightOn") != null && tag.getIntOr(ca.spottedleaf.moonrise.patches.starlight.util.SaveUtil.STARLIGHT_VERSION_TAG, -1) == ca.spottedleaf.moonrise.patches.starlight.util.SaveUtil.STARLIGHT_LIGHT_VERSION); // Paper - starlight
            BlendingData.Packed packed = tag.read("blending_data", BlendingData.Packed.CODEC).orElse(null);
            BelowZeroRetrogen belowZeroRetrogen = tag.read("below_zero_retrogen", BelowZeroRetrogen.CODEC).orElse(null);
            long[] longs = tag.getLongArray("carving_mask").orElse(null);
            Map<Heightmap.Types, long[]> map = new EnumMap<>(Heightmap.Types.class);
            tag.getCompound("Heightmaps").ifPresent(compoundTag1 -> {
                for (Heightmap.Types types : chunkStatus.heightmapsAfter()) {
                    compoundTag1.getLongArray(types.getSerializationKey()).ifPresent(longs1 -> map.put(types, longs1));
                }
            });
            List<SavedTick<Block>> list = SavedTick.filterTickListForChunk(tag.read("block_ticks", BLOCK_TICKS_CODEC).orElse(List.of()), chunkPos);
            List<SavedTick<Fluid>> list1 = SavedTick.filterTickListForChunk(tag.read("fluid_ticks", FLUID_TICKS_CODEC).orElse(List.of()), chunkPos);
            ChunkAccess.PackedTicks packedTicks = new ChunkAccess.PackedTicks(list, list1);
            ListTag listOrEmpty = tag.getListOrEmpty("PostProcessing");
            ShortList[] lists = new ShortList[listOrEmpty.size()];

            for (int i = 0; i < listOrEmpty.size(); i++) {
                ListTag listTag = listOrEmpty.getList(i).orElse(null);
                if (listTag != null && !listTag.isEmpty()) {
                    ShortList list2 = new ShortArrayList(listTag.size());

                    for (int i1 = 0; i1 < listTag.size(); i1++) {
                        list2.add(listTag.getShortOr(i1, (short)0));
                    }

                    lists[i] = list2;
                }
            }

            List<CompoundTag> list3 = tag.getList("entities").stream().flatMap(ListTag::compoundStream).toList();
            List<CompoundTag> list4 = tag.getList("block_entities").stream().flatMap(ListTag::compoundStream).toList();
            CompoundTag compoundOrEmpty = tag.getCompoundOrEmpty("structures");
            ListTag listOrEmpty1 = tag.getListOrEmpty("sections");
            List<SerializableChunkData.SectionData> list5 = new ArrayList<>(listOrEmpty1.size());
            Codec<PalettedContainer<Holder<Biome>>> codec = containerFactory.biomeContainerRWCodec(); // CraftBukkit - read/write
            Codec<PalettedContainer<BlockState>> codec1 = containerFactory.blockStatesContainerCodec();

            for (int i2 = 0; i2 < listOrEmpty1.size(); i2++) {
                Optional<CompoundTag> compound = listOrEmpty1.getCompound(i2);
                if (!compound.isEmpty()) {
                    CompoundTag compoundTag = compound.get(); final CompoundTag sectionData = compoundTag; // Paper - OBFHELPER
                    int byteOr = compoundTag.getByteOr("Y", (byte)0);
                    LevelChunkSection levelChunkSection;
                    if (byteOr >= level.getMinSectionY() && byteOr <= level.getMaxSectionY()) {
                        final BlockState[] presetBlockStates = serverLevel.chunkPacketBlockController.getPresetBlockStates(serverLevel, chunkPos, byteOr); // Paper - Anti-Xray - Add preset block states
                        final Codec<PalettedContainer<BlockState>> blockStateCodec = presetBlockStates == null ? codec1 : PalettedContainer.codecRW(BlockState.CODEC, containerFactory.blockStatesStrategy(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), presetBlockStates); // Paper - Anti-Xray
                        PalettedContainer<BlockState> palettedContainer = compoundTag.getCompound("block_states")
                            .map(
                                compoundTag1 -> blockStateCodec.parse(NbtOps.INSTANCE, compoundTag1) // Paper - Anti-Xray
                                    .promotePartial(string -> logErrors(chunkPos, byteOr, string))
                                    .getOrThrow(SerializableChunkData.ChunkReadException::new)
                            )
                            .orElseGet(containerFactory::createForBlockStates);
                        PalettedContainer<Holder<Biome>> palettedContainerRo = compoundTag.getCompound("biomes") // CraftBukkit - read/write
                            .map(
                                compoundTag1 -> codec.parse(NbtOps.INSTANCE, compoundTag1)
                                    .promotePartial(string -> logErrors(chunkPos, byteOr, string))
                                    .getOrThrow(SerializableChunkData.ChunkReadException::new)
                            )
                            .orElseGet(containerFactory::createForBiomes);
                        levelChunkSection = new LevelChunkSection(palettedContainer, palettedContainerRo);
                    } else {
                        levelChunkSection = null;
                    }

                    DataLayer dataLayer = compoundTag.getByteArray("BlockLight").map(DataLayer::new).orElse(null);
                    DataLayer dataLayer1 = compoundTag.getByteArray("SkyLight").map(DataLayer::new).orElse(null);
                    // Paper start - starlight
                    SerializableChunkData.SectionData serializableChunkData = new SerializableChunkData.SectionData(byteOr, levelChunkSection, dataLayer, dataLayer1);
                    if (sectionData.contains(ca.spottedleaf.moonrise.patches.starlight.util.SaveUtil.BLOCKLIGHT_STATE_TAG)) {
                        ((ca.spottedleaf.moonrise.patches.starlight.storage.StarlightSectionData)(Object)serializableChunkData).starlight$setBlockLightState(sectionData.getIntOr(ca.spottedleaf.moonrise.patches.starlight.util.SaveUtil.BLOCKLIGHT_STATE_TAG, 0));
                    }

                    if (sectionData.contains(ca.spottedleaf.moonrise.patches.starlight.util.SaveUtil.SKYLIGHT_STATE_TAG)) {
                        ((ca.spottedleaf.moonrise.patches.starlight.storage.StarlightSectionData)(Object)serializableChunkData).starlight$setSkyLightState(sectionData.getIntOr(ca.spottedleaf.moonrise.patches.starlight.util.SaveUtil.SKYLIGHT_STATE_TAG, 0));
                    }
                    list5.add(serializableChunkData);
                    // Paper end - starlight
                }
            }

            return new SerializableChunkData(
                containerFactory,
                chunkPos,
                level.getMinSectionY(),
                longOr,
                longOr1,
                chunkStatus,
                packed,
                belowZeroRetrogen,
                upgradeData,
                longs,
                map,
                packedTicks,
                lists,
                booleanOr,
                list5,
                list3,
                list4,
                compoundOrEmpty
                , tag.get("ChunkBukkitValues") // CraftBukkit - ChunkBukkitValues
            );
        }
    }

    // Paper start - starlight
    private ProtoChunk loadStarlightLightData(final ServerLevel world, final ProtoChunk ret) {

        final boolean hasSkyLight = world.dimensionType().hasSkyLight();
        final int minSection = ca.spottedleaf.moonrise.common.util.WorldUtil.getMinLightSection(world);

        final ca.spottedleaf.moonrise.patches.starlight.light.SWMRNibbleArray[] blockNibbles = ca.spottedleaf.moonrise.patches.starlight.light.StarLightEngine.getFilledEmptyLight(world);
        final ca.spottedleaf.moonrise.patches.starlight.light.SWMRNibbleArray[] skyNibbles = ca.spottedleaf.moonrise.patches.starlight.light.StarLightEngine.getFilledEmptyLight(world);

        if (!this.lightCorrect) {
            ((ca.spottedleaf.moonrise.patches.starlight.chunk.StarlightChunk)ret).starlight$setBlockNibbles(blockNibbles);
            ((ca.spottedleaf.moonrise.patches.starlight.chunk.StarlightChunk)ret).starlight$setSkyNibbles(skyNibbles);
            return ret;
        }

        try {
            for (final SerializableChunkData.SectionData sectionData : this.sectionData) {
                final int y = sectionData.y();
                final DataLayer blockLight = sectionData.blockLight();
                final DataLayer skyLight = sectionData.skyLight();

                final int blockState = ((ca.spottedleaf.moonrise.patches.starlight.storage.StarlightSectionData)(Object)sectionData).starlight$getBlockLightState();
                final int skyState = ((ca.spottedleaf.moonrise.patches.starlight.storage.StarlightSectionData)(Object)sectionData).starlight$getSkyLightState();

                if (blockState >= 0) {
                    if (blockLight != null) {
                        blockNibbles[y - minSection] = new ca.spottedleaf.moonrise.patches.starlight.light.SWMRNibbleArray(ca.spottedleaf.moonrise.common.util.MixinWorkarounds.clone(blockLight.getData()), blockState); // clone for data safety
                    } else {
                        blockNibbles[y - minSection] = new ca.spottedleaf.moonrise.patches.starlight.light.SWMRNibbleArray(null, blockState);
                    }
                }

                if (skyState >= 0 && hasSkyLight) {
                    if (skyLight != null) {
                        skyNibbles[y - minSection] = new ca.spottedleaf.moonrise.patches.starlight.light.SWMRNibbleArray(ca.spottedleaf.moonrise.common.util.MixinWorkarounds.clone(skyLight.getData()), skyState); // clone for data safety
                    } else {
                        skyNibbles[y - minSection] = new ca.spottedleaf.moonrise.patches.starlight.light.SWMRNibbleArray(null, skyState);
                    }
                }
            }

            ((ca.spottedleaf.moonrise.patches.starlight.chunk.StarlightChunk)ret).starlight$setBlockNibbles(blockNibbles);
            ((ca.spottedleaf.moonrise.patches.starlight.chunk.StarlightChunk)ret).starlight$setSkyNibbles(skyNibbles);
        } catch (final Throwable thr) {
            ret.setLightCorrect(false);

            LOGGER.error("Failed to parse light data for chunk " + ret.getPos() + " in world '" + ca.spottedleaf.moonrise.common.util.WorldUtil.getWorldName(world) + "'", thr);
        }

        return ret;
    }
    // Paper end - starlight

    public ProtoChunk read(ServerLevel level, PoiManager poiManager, RegionStorageInfo regionStorageInfo, ChunkPos pos) {
        if (!Objects.equals(pos, this.chunkPos)) {
            LOGGER.error("Chunk file at {} is in the wrong location; relocating. (Expected {}, got {})", pos, pos, this.chunkPos);
            level.getServer().reportMisplacedChunk(this.chunkPos, pos, regionStorageInfo);
        }

        int sectionsCount = level.getSectionsCount();
        LevelChunkSection[] levelChunkSections = new LevelChunkSection[sectionsCount];
        boolean hasSkyLight = level.dimensionType().hasSkyLight();
        ChunkSource chunkSource = level.getChunkSource();
        LevelLightEngine lightEngine = chunkSource.getLightEngine();
        PalettedContainerFactory palettedContainerFactory = level.palettedContainerFactory();
        boolean flag = false;

        for (SerializableChunkData.SectionData sectionData : this.sectionData) {
            SectionPos sectionPos = SectionPos.of(pos, sectionData.y);
            if (sectionData.chunkSection != null) {
                levelChunkSections[level.getSectionIndexFromSectionY(sectionData.y)] = sectionData.chunkSection;
                //poiManager.checkConsistencyWithBlocks(sectionPos, sectionData.chunkSection); // Paper - rewrite chunk system
            }

            boolean flag1 = sectionData.blockLight != null;
            boolean flag2 = hasSkyLight && sectionData.skyLight != null;
            if (flag1 || flag2) {
                if (!flag) {
                    lightEngine.retainData(pos, true);
                    flag = true;
                }

                if (flag1) {
                    lightEngine.queueSectionData(LightLayer.BLOCK, sectionPos, sectionData.blockLight);
                }

                if (flag2) {
                    lightEngine.queueSectionData(LightLayer.SKY, sectionPos, sectionData.skyLight);
                }
            }
        }

        ChunkType chunkType = this.chunkStatus.getChunkType();
        ChunkAccess chunkAccess;
        if (chunkType == ChunkType.LEVELCHUNK) {
            LevelChunkTicks<Block> levelChunkTicks = new LevelChunkTicks<>(this.packedTicks.blocks());
            LevelChunkTicks<Fluid> levelChunkTicks1 = new LevelChunkTicks<>(this.packedTicks.fluids());
            chunkAccess = new LevelChunk(
                level.getLevel(),
                pos,
                this.upgradeData,
                levelChunkTicks,
                levelChunkTicks1,
                this.inhabitedTime,
                levelChunkSections,
                postLoadChunk(level, this.entities, this.blockEntities),
                BlendingData.unpack(this.blendingData)
            );
        } else {
            ProtoChunkTicks<Block> protoChunkTicks = ProtoChunkTicks.load(this.packedTicks.blocks());
            ProtoChunkTicks<Fluid> protoChunkTicks1 = ProtoChunkTicks.load(this.packedTicks.fluids());
            ProtoChunk protoChunk = new ProtoChunk(
                pos,
                this.upgradeData,
                levelChunkSections,
                protoChunkTicks,
                protoChunkTicks1,
                level,
                palettedContainerFactory,
                BlendingData.unpack(this.blendingData)
            );
            chunkAccess = protoChunk;
            protoChunk.setInhabitedTime(this.inhabitedTime);
            if (this.belowZeroRetrogen != null) {
                protoChunk.setBelowZeroRetrogen(this.belowZeroRetrogen);
            }

            protoChunk.setPersistedStatus(this.chunkStatus);
            if (this.chunkStatus.isOrAfter(ChunkStatus.INITIALIZE_LIGHT)) {
                protoChunk.setLightEngine(lightEngine);
            }
        }

        // CraftBukkit start - load chunk persistent data from nbt - SPIGOT-6814: Already load PDC here to account for 1.17 to 1.18 chunk upgrading.
        if (this.persistentDataContainer instanceof CompoundTag compoundTag) {
            chunkAccess.persistentDataContainer.putAll(compoundTag);
        }
        // CraftBukkit end

        chunkAccess.setLightCorrect(this.lightCorrect);
        EnumSet<Heightmap.Types> set = EnumSet.noneOf(Heightmap.Types.class);

        for (Heightmap.Types types : chunkAccess.getPersistedStatus().heightmapsAfter()) {
            long[] longs = this.heightmaps.get(types);
            if (longs != null) {
                chunkAccess.setHeightmap(types, longs);
            } else {
                set.add(types);
            }
        }

        Heightmap.primeHeightmaps(chunkAccess, set);
        chunkAccess.setAllStarts(unpackStructureStart(StructurePieceSerializationContext.fromLevel(level), this.structureData, level.getSeed()));
        chunkAccess.setAllReferences(unpackStructureReferences(level.registryAccess(), pos, this.structureData));

        for (int i = 0; i < this.postProcessingSections.length; i++) {
            ShortList list = this.postProcessingSections[i];
            if (list != null) {
                chunkAccess.addPackedPostProcess(list, i);
            }
        }

        if (chunkType == ChunkType.LEVELCHUNK) {
            return this.loadStarlightLightData(level, new ImposterProtoChunk((LevelChunk)chunkAccess, false)); // Paper - starlight
        } else {
            ProtoChunk protoChunk1 = (ProtoChunk)chunkAccess;

            for (CompoundTag compoundTag : this.entities) {
                protoChunk1.addEntity(compoundTag);
            }

            for (CompoundTag compoundTag : this.blockEntities) {
                protoChunk1.setBlockEntityNbt(compoundTag);
            }

            if (this.carvingMask != null) {
                protoChunk1.setCarvingMask(new CarvingMask(this.carvingMask, chunkAccess.getMinY()));
            }

            return this.loadStarlightLightData(level, protoChunk1); // Paper - starlight
        }
    }

    private static void logErrors(ChunkPos chunkPos, int sectionY, String error) {
        LOGGER.error("Recoverable errors when loading section [{}, {}, {}]: {}", chunkPos.x, sectionY, chunkPos.z, error);
    }

    public static SerializableChunkData copyOf(ServerLevel level, ChunkAccess chunk) {
        if (!chunk.canBeSerialized()) {
            throw new IllegalArgumentException("Chunk can't be serialized: " + chunk);
        } else {
            ChunkPos pos = chunk.getPos();
            List<SerializableChunkData.SectionData> list = new ArrayList<>(); final List<SerializableChunkData.SectionData> sectionsList = list; // Paper - starlight - OBFHELPER
            LevelChunkSection[] sections = chunk.getSections();
            LevelLightEngine lightEngine = level.getChunkSource().getLightEngine();

            // Paper start - starlight
            final int minLightSection = ca.spottedleaf.moonrise.common.util.WorldUtil.getMinLightSection(level);
            final int maxLightSection = ca.spottedleaf.moonrise.common.util.WorldUtil.getMaxLightSection(level);
            final int minBlockSection = ca.spottedleaf.moonrise.common.util.WorldUtil.getMinSection(level);

            final LevelChunkSection[] chunkSections = chunk.getSections();
            final ca.spottedleaf.moonrise.patches.starlight.light.SWMRNibbleArray[] blockNibbles = ((ca.spottedleaf.moonrise.patches.starlight.chunk.StarlightChunk)chunk).starlight$getBlockNibbles();
            final ca.spottedleaf.moonrise.patches.starlight.light.SWMRNibbleArray[] skyNibbles = ((ca.spottedleaf.moonrise.patches.starlight.chunk.StarlightChunk)chunk).starlight$getSkyNibbles();

            for (int lightSection = minLightSection; lightSection <= maxLightSection; ++lightSection) {
                final int lightSectionIdx = lightSection - minLightSection;
                final int blockSectionIdx = lightSection - minBlockSection;

                final LevelChunkSection chunkSection = (blockSectionIdx >= 0 && blockSectionIdx < chunkSections.length) ? chunkSections[blockSectionIdx].copy() : null;
                final ca.spottedleaf.moonrise.patches.starlight.light.SWMRNibbleArray.SaveState blockNibble = blockNibbles[lightSectionIdx].getSaveState();
                final ca.spottedleaf.moonrise.patches.starlight.light.SWMRNibbleArray.SaveState skyNibble = skyNibbles[lightSectionIdx].getSaveState();

                if (chunkSection == null && blockNibble == null && skyNibble == null) {
                    continue;
                }

                final SerializableChunkData.SectionData sectionData = new SerializableChunkData.SectionData(
                    lightSection, chunkSection,
                    blockNibble == null ? null : (blockNibble.data == null ? null : new DataLayer(blockNibble.data)),
                    skyNibble == null ? null : (skyNibble.data == null ? null : new DataLayer(skyNibble.data))
                );

                if (blockNibble != null) {
                    ((ca.spottedleaf.moonrise.patches.starlight.storage.StarlightSectionData)(Object)sectionData).starlight$setBlockLightState(blockNibble.state);
                }

                if (skyNibble != null) {
                    ((ca.spottedleaf.moonrise.patches.starlight.storage.StarlightSectionData)(Object)sectionData).starlight$setSkyLightState(skyNibble.state);
                }

                sectionsList.add(sectionData);
            }
            // Paper end - starlight

            List<CompoundTag> list1 = new ArrayList<>(chunk.getBlockEntitiesPos().size());

            for (BlockPos blockPos : chunk.getBlockEntitiesPos()) {
                CompoundTag blockEntityNbtForSaving = chunk.getBlockEntityNbtForSaving(blockPos, level.registryAccess());
                if (blockEntityNbtForSaving != null) {
                    list1.add(blockEntityNbtForSaving);
                }
            }

            List<CompoundTag> list2 = new ArrayList<>();
            long[] longs = null;
            if (chunk.getPersistedStatus().getChunkType() == ChunkType.PROTOCHUNK) {
                ProtoChunk protoChunk = (ProtoChunk)chunk;
                list2.addAll(protoChunk.getEntities());
                CarvingMask carvingMask = protoChunk.getCarvingMask();
                if (carvingMask != null) {
                    longs = carvingMask.toArray();
                }
            }

            Map<Heightmap.Types, long[]> map = new EnumMap<>(Heightmap.Types.class);

            for (Entry<Heightmap.Types, Heightmap> entry : chunk.getHeightmaps()) {
                if (chunk.getPersistedStatus().heightmapsAfter().contains(entry.getKey())) {
                    long[] rawData = entry.getValue().getRawData();
                    map.put(entry.getKey(), (long[])rawData.clone());
                }
            }

            ChunkAccess.PackedTicks ticksForSerialization = chunk.getTicksForSerialization(level.getRedstoneGameTime()); // Folia - region threading
            ShortList[] lists = Arrays.stream(chunk.getPostProcessing())
                .map(list3 -> list3 != null && !list3.isEmpty() ? new ShortArrayList(list3) : null)
                .toArray(ShortList[]::new);
            CompoundTag compoundTag = packStructureData(
                StructurePieceSerializationContext.fromLevel(level), pos, chunk.getAllStarts(), chunk.getAllReferences()
            );
            // CraftBukkit start - store chunk persistent data in nbt
            CompoundTag persistentDataContainer = null;
            if (!chunk.persistentDataContainer.isEmpty()) { // SPIGOT-6814: Always save PDC to account for 1.17 to 1.18 chunk upgrading.
                persistentDataContainer = chunk.persistentDataContainer.toTagCompound();
            }
            // CraftBukkit end
            return new SerializableChunkData(
                level.palettedContainerFactory(),
                pos,
                chunk.getMinSectionY(),
                level.getGameTime(),
                chunk.getInhabitedTime(),
                chunk.getPersistedStatus(),
                Optionull.map(chunk.getBlendingData(), BlendingData::pack),
                chunk.getBelowZeroRetrogen(),
                chunk.getUpgradeData().copy(),
                longs,
                map,
                ticksForSerialization,
                lists,
                chunk.isLightCorrect(),
                list,
                list2,
                list1,
                compoundTag
                , persistentDataContainer // CraftBukkit - persistentDataContainer
            );
        }
    }

    public CompoundTag write() {
        CompoundTag compoundTag = NbtUtils.addCurrentDataVersion(new CompoundTag());
        compoundTag.putInt("xPos", this.chunkPos.x);
        compoundTag.putInt("yPos", this.minSectionY);
        compoundTag.putInt("zPos", this.chunkPos.z);
        compoundTag.putLong("LastUpdate", this.lastUpdateTime); // Paper - Diff on change
        compoundTag.putLong("InhabitedTime", this.inhabitedTime);
        compoundTag.putString("Status", BuiltInRegistries.CHUNK_STATUS.getKey(this.chunkStatus).toString());
        compoundTag.storeNullable("blending_data", BlendingData.Packed.CODEC, this.blendingData);
        compoundTag.storeNullable("below_zero_retrogen", BelowZeroRetrogen.CODEC, this.belowZeroRetrogen);
        if (!this.upgradeData.isEmpty()) {
            compoundTag.put("UpgradeData", this.upgradeData.write());
        }

        ListTag listTag = new ListTag();
        Codec<PalettedContainer<BlockState>> codec = this.containerFactory.blockStatesContainerCodec();
        Codec<PalettedContainerRO<Holder<Biome>>> codec1 = this.containerFactory.biomeContainerCodec();

        for (SerializableChunkData.SectionData sectionData : this.sectionData) {
            CompoundTag compoundTag1 = new CompoundTag(); final CompoundTag sectionNBT = compoundTag1; // Paper - starlight - OBFHELPER
            LevelChunkSection levelChunkSection = sectionData.chunkSection;
            if (levelChunkSection != null) {
                compoundTag1.store("block_states", codec, levelChunkSection.getStates());
                compoundTag1.store("biomes", codec1, levelChunkSection.getBiomes());
            }

            if (sectionData.blockLight != null) {
                compoundTag1.putByteArray("BlockLight", sectionData.blockLight.getData());
            }

            if (sectionData.skyLight != null) {
                compoundTag1.putByteArray("SkyLight", sectionData.skyLight.getData());
            }

            // Paper start - starlight
            final int blockState = ((ca.spottedleaf.moonrise.patches.starlight.storage.StarlightSectionData)(Object)sectionData).starlight$getBlockLightState();
            final int skyState = ((ca.spottedleaf.moonrise.patches.starlight.storage.StarlightSectionData)(Object)sectionData).starlight$getSkyLightState();

            if (blockState > 0) {
                sectionNBT.putInt(ca.spottedleaf.moonrise.patches.starlight.util.SaveUtil.BLOCKLIGHT_STATE_TAG, blockState);
            }

            if (skyState > 0) {
                sectionNBT.putInt(ca.spottedleaf.moonrise.patches.starlight.util.SaveUtil.SKYLIGHT_STATE_TAG, skyState);
            }
            // Paper end - starlight

            if (!compoundTag1.isEmpty()) {
                compoundTag1.putByte("Y", (byte)sectionData.y);
                listTag.add(compoundTag1);
            }
        }

        compoundTag.put("sections", listTag);
        if (this.lightCorrect) {
            compoundTag.putBoolean("isLightOn", true);
        }

        ListTag listTag1 = new ListTag();
        listTag1.addAll(this.blockEntities);
        compoundTag.put("block_entities", listTag1);
        if (this.chunkStatus.getChunkType() == ChunkType.PROTOCHUNK) {
            ListTag listTag2 = new ListTag();
            listTag2.addAll(this.entities);
            compoundTag.put("entities", listTag2);
            if (this.carvingMask != null) {
                compoundTag.putLongArray("carving_mask", this.carvingMask);
            }
        }

        saveTicks(compoundTag, this.packedTicks);
        compoundTag.put("PostProcessing", packOffsets(this.postProcessingSections));
        CompoundTag compoundTag2 = new CompoundTag();
        this.heightmaps.forEach((types, longs) -> compoundTag2.put(types.getSerializationKey(), new LongArrayTag(longs)));
        compoundTag.put("Heightmaps", compoundTag2);
        compoundTag.put("structures", this.structureData);
        // CraftBukkit start - store chunk persistent data in nbt
        if (this.persistentDataContainer != null) { // SPIGOT-6814: Always save PDC to account for 1.17 to 1.18 chunk upgrading.
            compoundTag.put("ChunkBukkitValues", this.persistentDataContainer);
        }
        // CraftBukkit end
        // Paper start - starlight
        if (this.lightCorrect && !this.chunkStatus.isBefore(net.minecraft.world.level.chunk.status.ChunkStatus.LIGHT)) {
            // clobber vanilla value to force vanilla to relight
            compoundTag.putBoolean("isLightOn", false);
            // store our light version
            compoundTag.putInt(ca.spottedleaf.moonrise.patches.starlight.util.SaveUtil.STARLIGHT_VERSION_TAG, ca.spottedleaf.moonrise.patches.starlight.util.SaveUtil.STARLIGHT_LIGHT_VERSION);
        }
        // Paper end - starlight
        return compoundTag;
    }

    private static void saveTicks(CompoundTag tag, ChunkAccess.PackedTicks ticks) {
        tag.store("block_ticks", BLOCK_TICKS_CODEC, ticks.blocks());
        tag.store("fluid_ticks", FLUID_TICKS_CODEC, ticks.fluids());
    }

    public static ChunkStatus getChunkStatusFromTag(@Nullable CompoundTag tag) {
        return tag != null ? tag.read("Status", ChunkStatus.CODEC).orElse(ChunkStatus.EMPTY) : ChunkStatus.EMPTY;
    }

    private static LevelChunk.@Nullable PostLoadProcessor postLoadChunk(ServerLevel level, List<CompoundTag> entities, List<CompoundTag> blockEntities) {
        return entities.isEmpty() && blockEntities.isEmpty()
            ? null
            : chunk -> {
                if (!entities.isEmpty()) {
                    try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(chunk.problemPath(), LOGGER)) {
                        level.addLegacyChunkEntities(
                            EntityType.loadEntitiesRecursive(
                                TagValueInput.create(scopedCollector, level.registryAccess(), entities), level, EntitySpawnReason.LOAD
                            )
                        );
                    }
                }

                for (CompoundTag compoundTag : blockEntities) {
                    boolean booleanOr = compoundTag.getBooleanOr("keepPacked", false);
                    if (booleanOr) {
                        chunk.setBlockEntityNbt(compoundTag);
                    } else {
                        BlockPos posFromTag = BlockEntity.getPosFromTag(chunk.getPos(), compoundTag);
                        BlockEntity blockEntity = BlockEntity.loadStatic(posFromTag, chunk.getBlockState(posFromTag), compoundTag, level.registryAccess());
                        if (blockEntity != null) {
                            chunk.setBlockEntity(blockEntity);
                        }
                    }
                }
            };
    }

    private static CompoundTag packStructureData(
        StructurePieceSerializationContext context, ChunkPos pos, Map<Structure, StructureStart> structureStarts, Map<Structure, LongSet> references
    ) {
        CompoundTag compoundTag = new CompoundTag();
        CompoundTag compoundTag1 = new CompoundTag();
        Registry<Structure> registry = context.registryAccess().lookupOrThrow(Registries.STRUCTURE);

        for (Entry<Structure, StructureStart> entry : structureStarts.entrySet()) {
            Identifier key = registry.getKey(entry.getKey());
            compoundTag1.put(key.toString(), entry.getValue().createTag(context, pos));
        }

        compoundTag.put("starts", compoundTag1);
        CompoundTag compoundTag2 = new CompoundTag();

        for (Entry<Structure, LongSet> entry1 : references.entrySet()) {
            if (!entry1.getValue().isEmpty()) {
                Identifier key1 = registry.getKey(entry1.getKey());
                compoundTag2.putLongArray(key1.toString(), entry1.getValue().toLongArray());
            }
        }

        compoundTag.put("References", compoundTag2);
        return compoundTag;
    }

    private static Map<Structure, StructureStart> unpackStructureStart(StructurePieceSerializationContext context, CompoundTag tag, long seed) {
        Map<Structure, StructureStart> map = Maps.newHashMap();
        Registry<Structure> registry = context.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        CompoundTag compoundOrEmpty = tag.getCompoundOrEmpty("starts");

        for (String string : compoundOrEmpty.keySet()) {
            Identifier identifier = Identifier.tryParse(string);
            Structure structure = registry.getValue(identifier);
            if (structure == null) {
                LOGGER.error("Unknown structure start: {}", identifier);
            } else {
                StructureStart structureStart = StructureStart.loadStaticStart(context, compoundOrEmpty.getCompoundOrEmpty(string), seed);
                if (structureStart != null) {
                    // CraftBukkit start - load persistent data for structure start
                    net.minecraft.nbt.Tag persistentBase = compoundOrEmpty.getCompoundOrEmpty(string).get("StructureBukkitValues");
                    if (persistentBase instanceof CompoundTag compoundTag) {
                        structureStart.persistentDataContainer.putAll(compoundTag);
                    }
                    // CraftBukkit end
                    map.put(structure, structureStart);
                }
            }
        }

        return map;
    }

    private static Map<Structure, LongSet> unpackStructureReferences(RegistryAccess registries, ChunkPos pos, CompoundTag tag) {
        Map<Structure, LongSet> map = Maps.newHashMap();
        Registry<Structure> registry = registries.lookupOrThrow(Registries.STRUCTURE);
        CompoundTag compoundOrEmpty = tag.getCompoundOrEmpty("References");
        compoundOrEmpty.forEach((string, tag1) -> {
            Identifier identifier = Identifier.tryParse(string);
            Structure structure = registry.getValue(identifier);
            if (structure == null) {
                LOGGER.warn("Found reference to unknown structure '{}' in chunk {}, discarding", identifier, pos);
            } else {
                Optional<long[]> longArray = tag1.asLongArray();
                if (!longArray.isEmpty()) {
                    map.put(structure, new LongOpenHashSet(Arrays.stream(longArray.get()).filter(l -> {
                        ChunkPos chunkPos = new ChunkPos(l);
                        if (chunkPos.getChessboardDistance(pos) > 8) {
                            LOGGER.warn("Found invalid structure reference [ {} @ {} ] for chunk {}.", identifier, chunkPos, pos);
                            return false;
                        } else {
                            return true;
                        }
                    }).toArray()));
                }
            }
        });
        return map;
    }

    private static ListTag packOffsets(@Nullable ShortList[] offsets) {
        ListTag listTag = new ListTag();

        for (ShortList list : offsets) {
            ListTag listTag1 = new ListTag();
            if (list != null) {
                for (int i = 0; i < list.size(); i++) {
                    listTag1.add(ShortTag.valueOf(list.getShort(i)));
                }
            }

            listTag.add(listTag1);
        }

        return listTag;
    }

    public static class ChunkReadException extends NbtException {
        public ChunkReadException(String message) {
            super(message);
        }
    }

    // Paper start - starlight - convert from record
    public static final class SectionData implements ca.spottedleaf.moonrise.patches.starlight.storage.StarlightSectionData { // Paper - starlight - our diff
        private final int y;
        @javax.annotation.Nullable
        private final net.minecraft.world.level.chunk.LevelChunkSection chunkSection;
        @javax.annotation.Nullable
        private final net.minecraft.world.level.chunk.DataLayer blockLight;
        @javax.annotation.Nullable
        private final net.minecraft.world.level.chunk.DataLayer skyLight;

        // Paper start - starlight - our diff
        private int blockLightState = -1;
        private int skyLightState = -1;

        @Override
        public final int starlight$getBlockLightState() {
            return this.blockLightState;
        }

        @Override
        public final void starlight$setBlockLightState(final int state) {
            this.blockLightState = state;
        }

        @Override
        public final int starlight$getSkyLightState() {
            return this.skyLightState;
        }

        @Override
        public final void starlight$setSkyLightState(final int state) {
            this.skyLightState = state;
        }
        // Paper end - starlight - our diff

        public SectionData(int y, @javax.annotation.Nullable net.minecraft.world.level.chunk.LevelChunkSection chunkSection, @javax.annotation.Nullable net.minecraft.world.level.chunk.DataLayer blockLight, @javax.annotation.Nullable net.minecraft.world.level.chunk.DataLayer skyLight) {
            this.y = y;
            this.chunkSection = chunkSection;
            this.blockLight = blockLight;
            this.skyLight = skyLight;
        }

        public int y() {
            return y;
        }

        @javax.annotation.Nullable
        public net.minecraft.world.level.chunk.LevelChunkSection chunkSection() {
            return chunkSection;
        }

        @javax.annotation.Nullable
        public net.minecraft.world.level.chunk.DataLayer blockLight() {
            return blockLight;
        }

        @javax.annotation.Nullable
        public net.minecraft.world.level.chunk.DataLayer skyLight() {
            return skyLight;
        }
        // Paper end - starlight - convert from record
    }
}
