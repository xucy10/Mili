package net.minecraft.util.worldupdate;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Reference2FloatMap;
import it.unimi.dsi.fastutil.objects.Reference2FloatMaps;
import it.unimi.dsi.fastutil.objects.Reference2FloatOpenHashMap;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ThreadFactory;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.storage.LegacyTagFixer;
import net.minecraft.world.level.chunk.storage.RecreatingSimpleRegionStorage;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.structure.LegacyStructureDataHandler;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.WorldData;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class WorldUpgrader implements AutoCloseable {
    static final Logger LOGGER = LogUtils.getLogger();
    private static final ThreadFactory THREAD_FACTORY = new ThreadFactoryBuilder().setDaemon(true).build();
    private static final String NEW_DIRECTORY_PREFIX = "new_";
    static final Component STATUS_UPGRADING_POI = Component.translatable("optimizeWorld.stage.upgrading.poi");
    static final Component STATUS_FINISHED_POI = Component.translatable("optimizeWorld.stage.finished.poi");
    static final Component STATUS_UPGRADING_ENTITIES = Component.translatable("optimizeWorld.stage.upgrading.entities");
    static final Component STATUS_FINISHED_ENTITIES = Component.translatable("optimizeWorld.stage.finished.entities");
    static final Component STATUS_UPGRADING_CHUNKS = Component.translatable("optimizeWorld.stage.upgrading.chunks");
    static final Component STATUS_FINISHED_CHUNKS = Component.translatable("optimizeWorld.stage.finished.chunks");
    final Registry<LevelStem> dimensions;
    final Set<ResourceKey<Level>> levels;
    final boolean eraseCache;
    final boolean recreateRegionFiles;
    final LevelStorageSource.LevelStorageAccess levelStorage;
    private final Thread thread;
    final DataFixer dataFixer;
    volatile boolean running = true;
    private volatile boolean finished;
    volatile float progress;
    volatile int totalChunks;
    volatile int totalFiles;
    volatile int converted;
    volatile int skipped;
    final Reference2FloatMap<ResourceKey<Level>> progressMap = Reference2FloatMaps.synchronize(new Reference2FloatOpenHashMap<>());
    volatile Component status = Component.translatable("optimizeWorld.stage.counting");
    static final Pattern REGEX = Pattern.compile("^r\\.(-?[0-9]+)\\.(-?[0-9]+)\\\\"+ net.minecraft.world.level.chunk.storage.RegionFileStorage.getExtensionName() +"$"); // Luminol - Configurable region file format
    final DimensionDataStorage overworldDataStorage;

    public WorldUpgrader(
        LevelStorageSource.LevelStorageAccess levelStorage,
        DataFixer dataFixer,
        WorldData worldData,
        RegistryAccess registryAccess,
        boolean eraseCache,
        boolean recreateRegionFiles
    ) {
        this.dimensions = registryAccess.lookupOrThrow(Registries.LEVEL_STEM);
        this.levels = java.util.stream.Stream.of(levelStorage.dimensionType).map(Registries::levelStemToLevel).collect(Collectors.toUnmodifiableSet()); // CraftBukkit
        this.eraseCache = eraseCache;
        this.dataFixer = dataFixer;
        this.levelStorage = levelStorage;
        this.overworldDataStorage = new DimensionDataStorage(this.levelStorage.getDimensionPath(Level.OVERWORLD).resolve("data"), dataFixer, registryAccess);
        this.recreateRegionFiles = recreateRegionFiles;
        this.thread = THREAD_FACTORY.newThread(this::work);
        this.thread.setUncaughtExceptionHandler((thread, throwable) -> {
            LOGGER.error("Error upgrading world", throwable);
            this.status = Component.translatable("optimizeWorld.stage.failed");
            this.finished = true;
        });
        this.thread.start();
    }

    public void cancel() {
        this.running = false;

        try {
            this.thread.join();
        } catch (InterruptedException var2) {
        }
    }

    private void work() {
        long millis = Util.getMillis();
        LOGGER.info("Upgrading entities");
        new WorldUpgrader.EntityUpgrader().upgrade();
        LOGGER.info("Upgrading POIs");
        new WorldUpgrader.PoiUpgrader().upgrade();
        LOGGER.info("Upgrading blocks");
        new WorldUpgrader.ChunkUpgrader().upgrade();
        this.overworldDataStorage.saveAndJoin();
        millis = Util.getMillis() - millis;
        LOGGER.info("World optimizaton finished after {} seconds", millis / 1000L);
        this.finished = true;
    }

    public boolean isFinished() {
        return this.finished;
    }

    public Set<ResourceKey<Level>> levels() {
        return this.levels;
    }

    public float dimensionProgress(ResourceKey<Level> level) {
        return this.progressMap.getFloat(level);
    }

    public float getProgress() {
        return this.progress;
    }

    public int getTotalChunks() {
        return this.totalChunks;
    }

    public int getConverted() {
        return this.converted;
    }

    public int getSkipped() {
        return this.skipped;
    }

    public Component getStatus() {
        return this.status;
    }

    @Override
    public void close() {
        this.overworldDataStorage.close();
    }

    static Path resolveRecreateDirectory(Path path) {
        return path.resolveSibling("new_" + path.getFileName().toString());
    }

    abstract class AbstractUpgrader {
        private final Component upgradingStatus;
        private final Component finishedStatus;
        private final String type;
        private final String folderName;
        protected @Nullable CompletableFuture<Void> previousWriteFuture;
        protected final DataFixTypes dataFixType;

        AbstractUpgrader(
            final DataFixTypes dataFixType, final String type, final String folderName, final Component upgradingStatus, final Component finishedStatus
        ) {
            this.dataFixType = dataFixType;
            this.type = type;
            this.folderName = folderName;
            this.upgradingStatus = upgradingStatus;
            this.finishedStatus = finishedStatus;
        }

        public void upgrade() {
            WorldUpgrader.this.totalFiles = 0;
            WorldUpgrader.this.totalChunks = 0;
            WorldUpgrader.this.converted = 0;
            WorldUpgrader.this.skipped = 0;
            List<WorldUpgrader.DimensionToUpgrade> dimensionsToUpgrade = this.getDimensionsToUpgrade();
            if (WorldUpgrader.this.totalChunks != 0) {
                float f = WorldUpgrader.this.totalFiles;
                WorldUpgrader.this.status = this.upgradingStatus;

                while (WorldUpgrader.this.running) {
                    boolean flag = false;
                    float f1 = 0.0F;

                    for (WorldUpgrader.DimensionToUpgrade dimensionToUpgrade : dimensionsToUpgrade) {
                        ResourceKey<Level> resourceKey = dimensionToUpgrade.dimensionKey;
                        ListIterator<WorldUpgrader.FileToUpgrade> listIterator = dimensionToUpgrade.files;
                        SimpleRegionStorage simpleRegionStorage = dimensionToUpgrade.storage;
                        if (listIterator.hasNext()) {
                            WorldUpgrader.FileToUpgrade fileToUpgrade = listIterator.next();
                            boolean flag1 = true;

                            for (ChunkPos chunkPos : fileToUpgrade.chunksToUpgrade) {
                                flag1 = flag1 && this.processOnePosition(resourceKey, simpleRegionStorage, chunkPos);
                                flag = true;
                            }

                            if (WorldUpgrader.this.recreateRegionFiles) {
                                if (flag1) {
                                    this.onFileFinished(fileToUpgrade.file);
                                } else {
                                    WorldUpgrader.LOGGER.error("Failed to convert region file {}", fileToUpgrade.file.getPath());
                                }
                            }
                        }

                        float f2 = listIterator.nextIndex() / f;
                        WorldUpgrader.this.progressMap.put(resourceKey, f2);
                        f1 += f2;
                    }

                    WorldUpgrader.this.progress = f1;
                    if (!flag) {
                        break;
                    }
                }

                WorldUpgrader.this.status = this.finishedStatus;

                for (WorldUpgrader.DimensionToUpgrade dimensionToUpgrade1 : dimensionsToUpgrade) {
                    try {
                        dimensionToUpgrade1.storage.close();
                    } catch (Exception var14) {
                        WorldUpgrader.LOGGER.error("Error upgrading chunk", (Throwable)var14);
                    }
                }
            }
        }

        private List<WorldUpgrader.DimensionToUpgrade> getDimensionsToUpgrade() {
            List<WorldUpgrader.DimensionToUpgrade> list = Lists.newArrayList();

            for (ResourceKey<Level> resourceKey : WorldUpgrader.this.levels) {
                RegionStorageInfo regionStorageInfo = new RegionStorageInfo(WorldUpgrader.this.levelStorage.getLevelId(), resourceKey, this.type);
                Path path = WorldUpgrader.this.levelStorage.getDimensionPath(resourceKey).resolve(this.folderName);
                SimpleRegionStorage simpleRegionStorage = this.createStorage(regionStorageInfo, path);
                ListIterator<WorldUpgrader.FileToUpgrade> filesToProcess = this.getFilesToProcess(regionStorageInfo, path);
                list.add(new WorldUpgrader.DimensionToUpgrade(resourceKey, simpleRegionStorage, filesToProcess));
            }

            return list;
        }

        protected abstract SimpleRegionStorage createStorage(RegionStorageInfo regionStorageInfo, Path path);

        private ListIterator<WorldUpgrader.FileToUpgrade> getFilesToProcess(RegionStorageInfo regionStorageInfo, Path path) {
            List<WorldUpgrader.FileToUpgrade> allChunkPositions = getAllChunkPositions(regionStorageInfo, path);
            WorldUpgrader.this.totalFiles = WorldUpgrader.this.totalFiles + allChunkPositions.size();
            WorldUpgrader.this.totalChunks = WorldUpgrader.this.totalChunks
                + allChunkPositions.stream().mapToInt(fileToUpgrade -> fileToUpgrade.chunksToUpgrade.size()).sum();
            return allChunkPositions.listIterator();
        }

        private static List<WorldUpgrader.FileToUpgrade> getAllChunkPositions(RegionStorageInfo regionStorageInfo, Path path) {
            File[] files = path.toFile().listFiles((directory, filename) -> filename.endsWith(net.minecraft.world.level.chunk.storage.RegionFileStorage.getExtensionName())); // Luminol - Configurable region file format
            if (files == null) {
                return List.of();
            } else {
                List<WorldUpgrader.FileToUpgrade> list = Lists.newArrayList();

                for (File file : files) {
                    Matcher matcher = WorldUpgrader.REGEX.matcher(file.getName());
                    if (matcher.matches()) {
                        int i = Integer.parseInt(matcher.group(1)) << 5;
                        int i1 = Integer.parseInt(matcher.group(2)) << 5;
                        List<ChunkPos> list1 = Lists.newArrayList();

                        try (abomination.IRegionFile regionFile = net.minecraft.world.level.chunk.storage.RegionFileStorage.createNew(regionStorageInfo, file.toPath(), path, true)) {  // Luminol - Configurable region file format
                            for (int i2 = 0; i2 < 32; i2++) {
                                for (int i3 = 0; i3 < 32; i3++) {
                                    ChunkPos chunkPos = new ChunkPos(i2 + i, i3 + i1);
                                    if (regionFile.doesChunkExist(chunkPos)) {
                                        list1.add(chunkPos);
                                    }
                                }
                            }

                            if (!list1.isEmpty()) {
                                list.add(new WorldUpgrader.FileToUpgrade(regionFile, list1));
                            }
                        } catch (Throwable var18) {
                            WorldUpgrader.LOGGER.error("Failed to read chunks from region file {}", file.toPath(), var18);
                        }
                    }
                }

                return list;
            }
        }

        private boolean processOnePosition(ResourceKey<Level> dimension, SimpleRegionStorage regionStorage, ChunkPos chunkPos) {
            boolean flag = false;

            try {
                flag = this.tryProcessOnePosition(regionStorage, chunkPos, dimension);
            } catch (CompletionException | ReportedException var7) {
                Throwable cause = var7.getCause();
                if (!(cause instanceof IOException)) {
                    throw var7;
                }

                WorldUpgrader.LOGGER.error("Error upgrading chunk {}", chunkPos, cause);
            }

            if (flag) {
                WorldUpgrader.this.converted++;
            } else {
                WorldUpgrader.this.skipped++;
            }

            return flag;
        }

        protected abstract boolean tryProcessOnePosition(SimpleRegionStorage regionStorage, ChunkPos chunkPos, ResourceKey<Level> dimension);

        private void onFileFinished(abomination.IRegionFile regionFile) { // Luminol - Configurable region file format
            if (WorldUpgrader.this.recreateRegionFiles) {
                if (this.previousWriteFuture != null) {
                    this.previousWriteFuture.join();
                }

                Path path = regionFile.getPath();
                Path parent = path.getParent();
                Path path1 = WorldUpgrader.resolveRecreateDirectory(parent).resolve(path.getFileName().toString());

                try {
                    if (path1.toFile().exists()) {
                        Files.delete(path);
                        Files.move(path1, path);
                    } else {
                        WorldUpgrader.LOGGER.error("Failed to replace an old region file. New file {} does not exist.", path1);
                    }
                } catch (IOException var6) {
                    WorldUpgrader.LOGGER.error("Failed to replace an old region file", (Throwable)var6);
                }
            }
        }
    }

    class ChunkUpgrader extends WorldUpgrader.AbstractUpgrader {
        ChunkUpgrader() {
            super(DataFixTypes.CHUNK, "chunk", "region", WorldUpgrader.STATUS_UPGRADING_CHUNKS, WorldUpgrader.STATUS_FINISHED_CHUNKS);
        }

        @Override
        protected boolean tryProcessOnePosition(SimpleRegionStorage regionStorage, ChunkPos chunkPos, ResourceKey<Level> dimension) {
            CompoundTag compoundTag = regionStorage.read(chunkPos).join().orElse(null);
            if (compoundTag != null) {
                int dataVersion = NbtUtils.getDataVersion(compoundTag);
                ChunkGenerator chunkGenerator = WorldUpgrader.this.dimensions.getValueOrThrow(Registries.levelToLevelStem(dimension)).generator();
                CompoundTag compoundTag1 = regionStorage.upgradeChunkTag(
                    compoundTag, -1, ChunkMap.getChunkDataFixContextTag(Registries.levelToLevelStem(dimension), chunkGenerator.getTypeNameForDataFixer()), null // CraftBukkit
                );
                ChunkPos chunkPos1 = new ChunkPos(compoundTag1.getIntOr("xPos", 0), compoundTag1.getIntOr("zPos", 0));
                if (!chunkPos1.equals(chunkPos)) {
                    WorldUpgrader.LOGGER.warn("Chunk {} has invalid position {}", chunkPos, chunkPos1);
                }

                boolean flag = dataVersion < SharedConstants.getCurrentVersion().dataVersion().version();
                if (WorldUpgrader.this.eraseCache) {
                    flag = flag || compoundTag1.contains("Heightmaps");
                    compoundTag1.remove("Heightmaps");
                    flag = flag || compoundTag1.contains("isLightOn");
                    compoundTag1.remove("isLightOn");
                    ListTag listOrEmpty = compoundTag1.getListOrEmpty("sections");

                    for (int i = 0; i < listOrEmpty.size(); i++) {
                        Optional<CompoundTag> compound = listOrEmpty.getCompound(i);
                        if (!compound.isEmpty()) {
                            CompoundTag compoundTag2 = compound.get();
                            flag = flag || compoundTag2.contains("BlockLight");
                            compoundTag2.remove("BlockLight");
                            flag = flag || compoundTag2.contains("SkyLight");
                            compoundTag2.remove("SkyLight");
                        }
                    }
                }

                if (flag || WorldUpgrader.this.recreateRegionFiles) {
                    if (this.previousWriteFuture != null) {
                        this.previousWriteFuture.join();
                    }

                    this.previousWriteFuture = regionStorage.write(chunkPos, compoundTag1);
                    return true;
                }
            }

            return false;
        }

        @Override
        protected SimpleRegionStorage createStorage(RegionStorageInfo regionStorageInfo, Path path) {
            Supplier<LegacyTagFixer> legacyTagFixer = LegacyStructureDataHandler.getLegacyTagFixer(
                regionStorageInfo.dimension(), () -> WorldUpgrader.this.overworldDataStorage, WorldUpgrader.this.dataFixer
            );
            return (SimpleRegionStorage)(WorldUpgrader.this.recreateRegionFiles
                ? new RecreatingSimpleRegionStorage(
                    regionStorageInfo.withTypeSuffix("source"),
                    path,
                    regionStorageInfo.withTypeSuffix("target"),
                    WorldUpgrader.resolveRecreateDirectory(path),
                    WorldUpgrader.this.dataFixer,
                    true,
                    DataFixTypes.CHUNK,
                    legacyTagFixer
                )
                : new SimpleRegionStorage(regionStorageInfo, path, WorldUpgrader.this.dataFixer, true, DataFixTypes.CHUNK, legacyTagFixer));
        }
    }

    record DimensionToUpgrade(ResourceKey<Level> dimensionKey, SimpleRegionStorage storage, ListIterator<WorldUpgrader.FileToUpgrade> files) {
    }

    class EntityUpgrader extends WorldUpgrader.SimpleRegionStorageUpgrader {
        EntityUpgrader() {
            super(DataFixTypes.ENTITY_CHUNK, "entities", WorldUpgrader.STATUS_UPGRADING_ENTITIES, WorldUpgrader.STATUS_FINISHED_ENTITIES);
        }

        @Override
        protected CompoundTag upgradeTag(SimpleRegionStorage regionStorage, CompoundTag chunkTag) {
            return regionStorage.upgradeChunkTag(chunkTag, -1);
        }
    }

    record FileToUpgrade(abomination.IRegionFile file, List<ChunkPos> chunksToUpgrade) { // Luminol - Configurable region file format
    }

    class PoiUpgrader extends WorldUpgrader.SimpleRegionStorageUpgrader {
        PoiUpgrader() {
            super(DataFixTypes.POI_CHUNK, "poi", WorldUpgrader.STATUS_UPGRADING_POI, WorldUpgrader.STATUS_FINISHED_POI);
        }

        @Override
        protected CompoundTag upgradeTag(SimpleRegionStorage regionStorage, CompoundTag chunkTag) {
            return regionStorage.upgradeChunkTag(chunkTag, 1945);
        }
    }

    abstract class SimpleRegionStorageUpgrader extends WorldUpgrader.AbstractUpgrader {
        SimpleRegionStorageUpgrader(final DataFixTypes dataFixType, final String type, final Component upgradingStatus, final Component finishedStatus) {
            super(dataFixType, type, type, upgradingStatus, finishedStatus);
        }

        @Override
        protected SimpleRegionStorage createStorage(RegionStorageInfo regionStorageInfo, Path path) {
            return (SimpleRegionStorage)(WorldUpgrader.this.recreateRegionFiles
                ? new RecreatingSimpleRegionStorage(
                    regionStorageInfo.withTypeSuffix("source"),
                    path,
                    regionStorageInfo.withTypeSuffix("target"),
                    WorldUpgrader.resolveRecreateDirectory(path),
                    WorldUpgrader.this.dataFixer,
                    true,
                    this.dataFixType,
                    LegacyTagFixer.EMPTY
                )
                : new SimpleRegionStorage(regionStorageInfo, path, WorldUpgrader.this.dataFixer, true, this.dataFixType));
        }

        @Override
        protected boolean tryProcessOnePosition(SimpleRegionStorage regionStorage, ChunkPos chunkPos, ResourceKey<Level> dimension) {
            CompoundTag compoundTag = regionStorage.read(chunkPos).join().orElse(null);
            if (compoundTag != null) {
                int dataVersion = NbtUtils.getDataVersion(compoundTag);
                CompoundTag compoundTag1 = this.upgradeTag(regionStorage, compoundTag);
                boolean flag = dataVersion < SharedConstants.getCurrentVersion().dataVersion().version();
                if (flag || WorldUpgrader.this.recreateRegionFiles) {
                    if (this.previousWriteFuture != null) {
                        this.previousWriteFuture.join();
                    }

                    this.previousWriteFuture = regionStorage.write(chunkPos, compoundTag1);
                    return true;
                }
            }

            return false;
        }

        protected abstract CompoundTag upgradeTag(SimpleRegionStorage regionStorage, CompoundTag chunkTag);
    }
}
