package net.minecraft.world.level.storage;

import com.google.common.collect.Maps;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.Lifecycle;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtFormatException;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.visitors.FieldSelector;
import net.minecraft.nbt.visitors.SkipFields;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.util.DirectoryLock;
import net.minecraft.util.FileUtil;
import net.minecraft.util.MemoryReserve;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.validation.ContentValidationException;
import net.minecraft.world.level.validation.DirectoryValidator;
import net.minecraft.world.level.validation.ForbiddenSymlinkInfo;
import net.minecraft.world.level.validation.PathAllowList;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class LevelStorageSource {
    static final Logger LOGGER = LogUtils.getLogger();
    public static final String TAG_DATA = "Data";
    private static final PathMatcher NO_SYMLINKS_ALLOWED = path -> false;
    public static final String ALLOWED_SYMLINKS_CONFIG_NAME = "allowed_symlinks.txt";
    private static final int DISK_SPACE_WARNING_THRESHOLD = 67108864;
    public final Path baseDir;
    private final Path backupDir;
    final DataFixer fixerUpper;
    private final DirectoryValidator worldDirValidator;

    public LevelStorageSource(Path baseDir, Path backupDir, DirectoryValidator worldDirValidator, DataFixer fixerUpper) {
        this.fixerUpper = fixerUpper;

        try {
            FileUtil.createDirectoriesSafe(baseDir);
        } catch (IOException var6) {
            throw new UncheckedIOException(var6);
        }

        this.baseDir = baseDir;
        this.backupDir = backupDir;
        this.worldDirValidator = worldDirValidator;
    }

    public static DirectoryValidator parseValidator(Path validator) {
        if (Files.exists(validator)) {
            try {
                DirectoryValidator var2;
                try (BufferedReader bufferedReader = Files.newBufferedReader(validator)) {
                    var2 = new DirectoryValidator(PathAllowList.readPlain(bufferedReader));
                }

                return var2;
            } catch (Exception var6) {
                LOGGER.error("Failed to parse {}, disallowing all symbolic links", "allowed_symlinks.txt", var6);
            }
        }

        return new DirectoryValidator(NO_SYMLINKS_ALLOWED);
    }

    public static LevelStorageSource createDefault(Path savesDir) {
        DirectoryValidator directoryValidator = parseValidator(savesDir.resolve("allowed_symlinks.txt"));
        return new LevelStorageSource(savesDir, savesDir.resolve("../backups"), directoryValidator, DataFixers.getDataFixer());
    }

    public static WorldDataConfiguration readDataConfig(Dynamic<?> dynamic) {
        return WorldDataConfiguration.CODEC.parse(dynamic).resultOrPartial(LOGGER::error).orElse(WorldDataConfiguration.DEFAULT);
    }

    public static WorldLoader.PackConfig getPackConfig(Dynamic<?> dynamic, PackRepository packRepository, boolean safeMode) {
        return new WorldLoader.PackConfig(packRepository, readDataConfig(dynamic), safeMode, false);
    }

    public static LevelDataAndDimensions getLevelDataAndDimensions(
        Dynamic<?> levelData, WorldDataConfiguration dataConfiguration, Registry<LevelStem> levelStemRegistry, HolderLookup.Provider registries
    ) {
        Dynamic<?> dynamic = RegistryOps.injectRegistryContext(levelData, registries);
        Dynamic<?> dynamic1 = dynamic.get("WorldGenSettings").orElseEmptyMap();
        WorldGenSettings worldGenSettings = WorldGenSettings.CODEC.parse(dynamic1).getOrThrow();
        LevelSettings levelSettings = LevelSettings.parse(dynamic, dataConfiguration);
        WorldDimensions.Complete complete = worldGenSettings.dimensions().bake(levelStemRegistry);
        Lifecycle lifecycle = complete.lifecycle().add(registries.allRegistriesLifecycle());
        PrimaryLevelData primaryLevelData = PrimaryLevelData.parse(
            dynamic, levelSettings, complete.specialWorldProperty(), worldGenSettings.options(), lifecycle
        );
        primaryLevelData.pdc = (Tag) dynamic.getElement("BukkitValues", null); // CraftBukkit - Add PDC to world
        return new LevelDataAndDimensions(primaryLevelData, complete);
    }

    public String getName() {
        return "Anvil";
    }

    public LevelStorageSource.LevelCandidates findLevelCandidates() throws LevelStorageException {
        if (!Files.isDirectory(this.baseDir)) {
            throw new LevelStorageException(Component.translatable("selectWorld.load_folder_access"));
        } else {
            try {
                LevelStorageSource.LevelCandidates var3;
                try (Stream<Path> stream = Files.list(this.baseDir)) {
                    List<LevelStorageSource.LevelDirectory> list = stream.filter(path -> Files.isDirectory(path))
                        .map(LevelStorageSource.LevelDirectory::new)
                        .filter(levelDirectory -> Files.isRegularFile(levelDirectory.dataFile()) || Files.isRegularFile(levelDirectory.oldDataFile()))
                        .toList();
                    var3 = new LevelStorageSource.LevelCandidates(list);
                }

                return var3;
            } catch (IOException var6) {
                throw new LevelStorageException(Component.translatable("selectWorld.load_folder_access"));
            }
        }
    }

    public CompletableFuture<List<LevelSummary>> loadLevelSummaries(LevelStorageSource.LevelCandidates candidates) {
        List<CompletableFuture<LevelSummary>> list = new ArrayList<>(candidates.levels.size());

        for (LevelStorageSource.LevelDirectory levelDirectory : candidates.levels) {
            list.add(CompletableFuture.supplyAsync(() -> {
                boolean isLocked;
                try {
                    isLocked = DirectoryLock.isLocked(levelDirectory.path());
                } catch (Exception var13) {
                    LOGGER.warn("Failed to read {} lock", levelDirectory.path(), var13);
                    return null;
                }

                try {
                    return this.readLevelSummary(levelDirectory, isLocked);
                } catch (OutOfMemoryError var12) {
                    MemoryReserve.release();
                    String string = "Ran out of memory trying to read summary of world folder \"" + levelDirectory.directoryName() + "\"";
                    LOGGER.error(LogUtils.FATAL_MARKER, string);
                    OutOfMemoryError outOfMemoryError1 = new OutOfMemoryError("Ran out of memory reading level data");
                    outOfMemoryError1.initCause(var12);
                    CrashReport crashReport = CrashReport.forThrowable(outOfMemoryError1, string);
                    CrashReportCategory crashReportCategory = crashReport.addCategory("World details");
                    crashReportCategory.setDetail("Folder Name", levelDirectory.directoryName());

                    try {
                        long size = Files.size(levelDirectory.dataFile());
                        crashReportCategory.setDetail("level.dat size", size);
                    } catch (IOException var11) {
                        crashReportCategory.setDetailError("level.dat size", var11);
                    }

                    throw new ReportedException(crashReport);
                }
            }, Util.backgroundExecutor().forName("loadLevelSummaries")));
        }

        return Util.sequenceFailFastAndCancel(list).thenApply(list1 -> list1.stream().filter(Objects::nonNull).sorted().toList());
    }

    private int getStorageVersion() {
        return 19133;
    }

    static CompoundTag readLevelDataTagRaw(Path levelPath) throws IOException {
        return NbtIo.readCompressed(levelPath, NbtAccounter.uncompressedQuota());
    }

    static Dynamic<?> readLevelDataTagFixed(Path levelPath, DataFixer dataFixer) throws IOException {
        CompoundTag levelDataTagRaw = readLevelDataTagRaw(levelPath);
        CompoundTag compoundOrEmpty = levelDataTagRaw.getCompoundOrEmpty("Data");
        int dataVersion = NbtUtils.getDataVersion(compoundOrEmpty);
        Dynamic<?> dynamic = DataFixTypes.LEVEL.updateToCurrentVersion(dataFixer, new Dynamic<>(NbtOps.INSTANCE, compoundOrEmpty), dataVersion);
        dynamic = dynamic.update("Player", dynamic1 -> new Dynamic(dynamic1.getOps(), ca.spottedleaf.dataconverter.minecraft.MCDataConverter.convertTag(ca.spottedleaf.dataconverter.minecraft.datatypes.MCTypeRegistry.PLAYER, (net.minecraft.nbt.CompoundTag)dynamic1.getValue(), dataVersion, ca.spottedleaf.dataconverter.minecraft.util.Version.getCurrentVersion()))); // Paper - replace data conversion system
        return dynamic.update("WorldGenSettings", dynamic1 -> DataFixTypes.WORLD_GEN_SETTINGS.updateToCurrentVersion(dataFixer, dynamic1, dataVersion));
    }

    private LevelSummary readLevelSummary(LevelStorageSource.LevelDirectory levelDirectory, boolean locked) {
        Path path = levelDirectory.dataFile();
        if (Files.exists(path)) {
            try {
                if (Files.isSymbolicLink(path)) {
                    List<ForbiddenSymlinkInfo> list = this.worldDirValidator.validateSymlink(path);
                    if (!list.isEmpty()) {
                        LOGGER.warn("{}", ContentValidationException.getMessage(path, list));
                        return new LevelSummary.SymlinkLevelSummary(levelDirectory.directoryName(), levelDirectory.iconFile());
                    }
                }

                if (readLightweightData(path) instanceof CompoundTag compoundTag) {
                    CompoundTag compoundOrEmpty = compoundTag.getCompoundOrEmpty("Data");
                    int dataVersion = NbtUtils.getDataVersion(compoundOrEmpty);
                    Dynamic<?> dynamic = DataFixTypes.LEVEL_SUMMARY
                        .updateToCurrentVersion(this.fixerUpper, new Dynamic<>(NbtOps.INSTANCE, compoundOrEmpty), dataVersion);
                    return this.makeLevelSummary(dynamic, levelDirectory, locked);
                }

                LOGGER.warn("Invalid root tag in {}", path);
            } catch (Exception var9) {
                LOGGER.error("Exception reading {}", path, var9);
            }
        }

        return new LevelSummary.CorruptedLevelSummary(levelDirectory.directoryName(), levelDirectory.iconFile(), getFileModificationTime(levelDirectory));
    }

    private static long getFileModificationTime(LevelStorageSource.LevelDirectory levelDirectory) {
        Instant fileModificationTime = getFileModificationTime(levelDirectory.dataFile());
        if (fileModificationTime == null) {
            fileModificationTime = getFileModificationTime(levelDirectory.oldDataFile());
        }

        return fileModificationTime == null ? -1L : fileModificationTime.toEpochMilli();
    }

    static @Nullable Instant getFileModificationTime(Path dataFilePath) {
        try {
            return Files.getLastModifiedTime(dataFilePath).toInstant();
        } catch (IOException var2) {
            return null;
        }
    }

    LevelSummary makeLevelSummary(Dynamic<?> dynamic, LevelStorageSource.LevelDirectory levelDirectory, boolean locked) {
        LevelVersion levelVersion = LevelVersion.parse(dynamic);
        int levelDataVersion = levelVersion.levelDataVersion();
        if (levelDataVersion != 19132 && levelDataVersion != 19133) {
            throw new NbtFormatException("Unknown data version: " + Integer.toHexString(levelDataVersion));
        } else {
            boolean flag = levelDataVersion != this.getStorageVersion();
            Path path = levelDirectory.iconFile();
            WorldDataConfiguration dataConfig = readDataConfig(dynamic);
            LevelSettings levelSettings = LevelSettings.parse(dynamic, dataConfig);
            FeatureFlagSet featureFlagSet = parseFeatureFlagsFromSummary(dynamic);
            boolean isExperimental = FeatureFlags.isExperimental(featureFlagSet);
            return new LevelSummary(levelSettings, levelVersion, levelDirectory.directoryName(), flag, locked, isExperimental, path);
        }
    }

    private static FeatureFlagSet parseFeatureFlagsFromSummary(Dynamic<?> dataDynamic) {
        Set<Identifier> set = dataDynamic.get("enabled_features")
            .asStream()
            .flatMap(dynamic -> dynamic.asString().result().map(Identifier::tryParse).stream())
            .collect(Collectors.toSet());
        return FeatureFlags.REGISTRY.fromNames(set, identifier -> {});
    }

    private static @Nullable Tag readLightweightData(Path file) throws IOException {
        SkipFields skipFields = new SkipFields(
            new FieldSelector("Data", CompoundTag.TYPE, "Player"), new FieldSelector("Data", CompoundTag.TYPE, "WorldGenSettings")
        );
        NbtIo.parseCompressed(file, skipFields, NbtAccounter.uncompressedQuota());
        return skipFields.getResult();
    }

    public boolean isNewLevelIdAcceptable(String saveName) {
        try {
            Path levelPath = this.getLevelPath(saveName);
            Files.createDirectory(levelPath);
            Files.deleteIfExists(levelPath);
            return true;
        } catch (IOException var3) {
            return false;
        }
    }

    public boolean levelExists(String saveName) {
        try {
            return Files.isDirectory(this.getLevelPath(saveName));
        } catch (InvalidPathException var3) {
            return false;
        }
    }

    public Path getLevelPath(String saveName) {
        return this.baseDir.resolve(saveName);
    }

    public Path getBaseDir() {
        return this.baseDir;
    }

    public Path getBackupPath() {
        return this.backupDir;
    }

    public LevelStorageSource.LevelStorageAccess validateAndCreateAccess(String saveName, ResourceKey<LevelStem> dimensionType) throws IOException, ContentValidationException { // CraftBukkit
        Path levelPath = this.getLevelPath(saveName);
        List<ForbiddenSymlinkInfo> list = Boolean.getBoolean("paper.disableWorldSymlinkValidation") ? List.of() : this.worldDirValidator.validateDirectory(levelPath, true); // Paper - add skipping of symlinks scan
        if (!list.isEmpty()) {
            throw new ContentValidationException(levelPath, list);
        } else {
            return new LevelStorageSource.LevelStorageAccess(saveName, levelPath, dimensionType); // CraftBukkit
        }
    }

    public LevelStorageSource.LevelStorageAccess createAccess(String saveName, ResourceKey<LevelStem> dimensionType) throws IOException { // CraftBukkit
        Path levelPath = this.getLevelPath(saveName);
        return new LevelStorageSource.LevelStorageAccess(saveName, levelPath, dimensionType); // CraftBukkit
    }

    public DirectoryValidator getWorldDirValidator() {
        return this.worldDirValidator;
    }

    // CraftBukkit start
    public static Path getStorageFolder(Path path, ResourceKey<LevelStem> dimensionType) {
        if (dimensionType == LevelStem.OVERWORLD) {
            return path;
        } else if (dimensionType == LevelStem.NETHER) {
            return path.resolve("DIM-1");
        } else if (dimensionType == LevelStem.END) {
            return path.resolve("DIM1");
        } else {
            return path.resolve("dimensions").resolve(dimensionType.identifier().getNamespace()).resolve(dimensionType.identifier().getPath());
        }
    }
    // CraftBukkit end

    public record LevelCandidates(List<LevelStorageSource.LevelDirectory> levels) implements Iterable<LevelStorageSource.LevelDirectory> {
        public boolean isEmpty() {
            return this.levels.isEmpty();
        }

        @Override
        public Iterator<LevelStorageSource.LevelDirectory> iterator() {
            return this.levels.iterator();
        }
    }

    public record LevelDirectory(Path path) {
        public String directoryName() {
            return this.path.getFileName().toString();
        }

        public Path dataFile() {
            return this.resourcePath(LevelResource.LEVEL_DATA_FILE);
        }

        public Path oldDataFile() {
            return this.resourcePath(LevelResource.OLD_LEVEL_DATA_FILE);
        }

        public Path corruptedDataFile(ZonedDateTime dateTime) {
            return this.path.resolve(LevelResource.LEVEL_DATA_FILE.getId() + "_corrupted_" + dateTime.format(FileNameDateFormatter.FORMATTER));
        }

        public Path rawDataFile(ZonedDateTime dateTime) {
            return this.path.resolve(LevelResource.LEVEL_DATA_FILE.getId() + "_raw_" + dateTime.format(FileNameDateFormatter.FORMATTER));
        }

        public Path iconFile() {
            return this.resourcePath(LevelResource.ICON_FILE);
        }

        public Path lockFile() {
            return this.resourcePath(LevelResource.LOCK_FILE);
        }

        public Path resourcePath(LevelResource resource) {
            return this.path.resolve(resource.getId());
        }
    }

    public class LevelStorageAccess implements AutoCloseable {
        final DirectoryLock lock;
        public final LevelStorageSource.LevelDirectory levelDirectory;
        private final String levelId;
        private final Map<LevelResource, Path> resources = Maps.newHashMap();
        // CraftBukkit start
        public final ResourceKey<LevelStem> dimensionType;

        LevelStorageAccess(final String levelId, final Path levelDir, final ResourceKey<LevelStem> dimensionType) throws IOException {
            this.dimensionType = dimensionType;
            // CraftBukkit end
            this.levelId = levelId;
            this.levelDirectory = new LevelStorageSource.LevelDirectory(levelDir);
            this.lock = DirectoryLock.create(levelDir);
        }

        public long estimateDiskSpace() {
            try {
                return Files.getFileStore(this.levelDirectory.path).getUsableSpace();
            } catch (Exception var2) {
                return Long.MAX_VALUE;
            }
        }

        public boolean checkForLowDiskSpace() {
            return this.estimateDiskSpace() < 67108864L;
        }

        public void safeClose() {
            try {
                this.close();
            } catch (IOException var2) {
                LevelStorageSource.LOGGER.warn("Failed to unlock access to level {}", this.getLevelId(), var2);
            }
        }

        public LevelStorageSource parent() {
            return LevelStorageSource.this;
        }

        public LevelStorageSource.LevelDirectory getLevelDirectory() {
            return this.levelDirectory;
        }

        public String getLevelId() {
            return this.levelId;
        }

        public Path getLevelPath(LevelResource folderName) {
            return this.resources.computeIfAbsent(folderName, this.levelDirectory::resourcePath);
        }

        public Path getDimensionPath(ResourceKey<Level> dimensionPath) {
            return LevelStorageSource.getStorageFolder(this.levelDirectory.path(), this.dimensionType); // CraftBukkit
        }

        private void checkLock() {
            if (!this.lock.isValid()) {
                throw new IllegalStateException("Lock is no longer valid");
            }
        }

        public PlayerDataStorage createPlayerStorage() {
            this.checkLock();
            return new PlayerDataStorage(this, LevelStorageSource.this.fixerUpper);
        }

        public LevelSummary getSummary(Dynamic<?> dynamic) {
            this.checkLock();
            return LevelStorageSource.this.makeLevelSummary(dynamic, this.levelDirectory, false);
        }

        public Dynamic<?> getDataTag() throws IOException {
            return this.getDataTag(false);
        }

        public Dynamic<?> getDataTagFallback() throws IOException {
            return this.getDataTag(true);
        }

        private Dynamic<?> getDataTag(boolean useFallback) throws IOException {
            this.checkLock();
            return LevelStorageSource.readLevelDataTagFixed(
                useFallback ? this.levelDirectory.oldDataFile() : this.levelDirectory.dataFile(), LevelStorageSource.this.fixerUpper
            );
        }

        public void saveDataTag(RegistryAccess registries, WorldData serverConfiguration) {
            this.saveDataTag(registries, serverConfiguration, null);
        }

        public void saveDataTag(RegistryAccess registries, WorldData serverConfiguration, @Nullable CompoundTag singlePlayerTag) {
            CompoundTag compoundTag = serverConfiguration.createTag(registries, singlePlayerTag);
            CompoundTag compoundTag1 = new CompoundTag();
            compoundTag1.put("Data", compoundTag);
            this.saveLevelData(compoundTag1);
        }

        private void saveLevelData(CompoundTag tag) {
            Path path = this.levelDirectory.path();

            try {
                Path path1 = Files.createTempFile(path, "level", ".dat");
                NbtIo.writeCompressed(tag, path1);
                Path path2 = this.levelDirectory.oldDataFile();
                Path path3 = this.levelDirectory.dataFile();
                Util.safeReplaceFile(path3, path1, path2);
            } catch (Exception var6) {
                LevelStorageSource.LOGGER.error("Failed to save level {}", path, var6);
            }
        }

        public Optional<Path> getIconFile() {
            return !this.lock.isValid() ? Optional.empty() : Optional.of(this.levelDirectory.iconFile());
        }

        public void deleteLevel() throws IOException {
            this.checkLock();
            final Path path = this.levelDirectory.lockFile();
            LevelStorageSource.LOGGER.info("Deleting level {}", this.levelId);

            for (int i = 1; i <= 5; i++) {
                LevelStorageSource.LOGGER.info("Attempt {}...", i);

                try {
                    Files.walkFileTree(this.levelDirectory.path(), new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                            if (!file.equals(path)) {
                                LevelStorageSource.LOGGER.debug("Deleting {}", file);
                                Files.delete(file);
                            }

                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult postVisitDirectory(Path dir, @Nullable IOException exception) throws IOException {
                            if (exception != null) {
                                throw exception;
                            } else {
                                if (dir.equals(LevelStorageAccess.this.levelDirectory.path())) {
                                    LevelStorageAccess.this.lock.close();
                                    Files.deleteIfExists(path);
                                }

                                Files.delete(dir);
                                return FileVisitResult.CONTINUE;
                            }
                        }
                    });
                    break;
                } catch (IOException var6) {
                    if (i >= 5) {
                        throw var6;
                    }

                    LevelStorageSource.LOGGER.warn("Failed to delete {}", this.levelDirectory.path(), var6);

                    try {
                        Thread.sleep(500L);
                    } catch (InterruptedException var5) {
                    }
                }
            }
        }

        public void renameLevel(String saveName) throws IOException {
            this.modifyLevelDataWithoutDatafix(compoundTag -> compoundTag.putString("LevelName", saveName.trim()));
        }

        public void renameAndDropPlayer(String saveName) throws IOException {
            this.modifyLevelDataWithoutDatafix(compoundTag -> {
                compoundTag.putString("LevelName", saveName.trim());
                compoundTag.remove("Player");
            });
        }

        private void modifyLevelDataWithoutDatafix(Consumer<CompoundTag> modifier) throws IOException {
            this.checkLock();
            CompoundTag levelDataTagRaw = LevelStorageSource.readLevelDataTagRaw(this.levelDirectory.dataFile());
            modifier.accept(levelDataTagRaw.getCompoundOrEmpty("Data"));
            this.saveLevelData(levelDataTagRaw);
        }

        public long makeWorldBackup() throws IOException {
            this.checkLock();
            String string = FileNameDateFormatter.FORMATTER.format(ZonedDateTime.now()) + "_" + this.levelId;
            Path backupPath = LevelStorageSource.this.getBackupPath();

            try {
                FileUtil.createDirectoriesSafe(backupPath);
            } catch (IOException var9) {
                throw new RuntimeException(var9);
            }

            Path path = backupPath.resolve(FileUtil.findAvailableName(backupPath, string, ".zip"));

            try (final ZipOutputStream zipOutputStream = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(path)))) {
                final Path path1 = Paths.get(this.levelId);
                Files.walkFileTree(this.levelDirectory.path(), new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                        if (file.endsWith("session.lock")) {
                            return FileVisitResult.CONTINUE;
                        } else {
                            String string1 = path1.resolve(LevelStorageAccess.this.levelDirectory.path().relativize(file)).toString().replace('\\', '/');
                            ZipEntry zipEntry = new ZipEntry(string1);
                            zipOutputStream.putNextEntry(zipEntry);
                            com.google.common.io.Files.asByteSource(file.toFile()).copyTo(zipOutputStream);
                            zipOutputStream.closeEntry();
                            return FileVisitResult.CONTINUE;
                        }
                    }
                });
            }

            return Files.size(path);
        }

        public boolean hasWorldData() {
            return Files.exists(this.levelDirectory.dataFile()) || Files.exists(this.levelDirectory.oldDataFile());
        }

        @Override
        public void close() throws IOException {
            this.lock.close();
        }

        public boolean restoreLevelDataFromOld() {
            return Util.safeReplaceOrMoveFile(
                this.levelDirectory.dataFile(), this.levelDirectory.oldDataFile(), this.levelDirectory.corruptedDataFile(ZonedDateTime.now()), true
            );
        }

        public @Nullable Instant getFileModificationTime(boolean useFallback) {
            return LevelStorageSource.getFileModificationTime(useFallback ? this.levelDirectory.oldDataFile() : this.levelDirectory.dataFile());
        }
    }
}
