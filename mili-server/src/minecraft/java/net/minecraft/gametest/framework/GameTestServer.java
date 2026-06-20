package net.minecraft.gametest.framework;

import com.google.common.base.Stopwatch;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ServicesKeySet;
import com.mojang.brigadier.StringReader;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Lifecycle;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;
import java.util.stream.Stream.Builder;
import net.minecraft.CrashReport;
import net.minecraft.ReportType;
import net.minecraft.SystemReport;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceSelectorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gizmos.GizmoCollector;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.Services;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.WorldStem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.LoggingLevelLoadListener;
import net.minecraft.server.notifications.EmptyNotificationService;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.players.ProfileResolver;
import net.minecraft.server.players.UserNameToIdResolver;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.debugchart.LocalSampleLogger;
import net.minecraft.util.debugchart.SampleLogger;
import net.minecraft.world.Difficulty;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class GameTestServer extends MinecraftServer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int PROGRESS_REPORT_INTERVAL = 20;
    private static final int TEST_POSITION_RANGE = 14999992;
    private static final Services NO_SERVICES = new Services(
        null, ServicesKeySet.EMPTY, null, new GameTestServer.MockUserNameToIdResolver(), new GameTestServer.MockProfileResolver()
    );
    private static final FeatureFlagSet ENABLED_FEATURES = FeatureFlags.REGISTRY
        .allFlags()
        .subtract(FeatureFlagSet.of(FeatureFlags.REDSTONE_EXPERIMENTS, FeatureFlags.MINECART_IMPROVEMENTS));
    private final LocalSampleLogger sampleLogger = new LocalSampleLogger(4);
    private final Optional<String> testSelection;
    private final boolean verify;
    private List<GameTestBatch> testBatches = new ArrayList<>();
    private final Stopwatch stopwatch = Stopwatch.createUnstarted();
    private static final WorldOptions WORLD_OPTIONS = new WorldOptions(0L, false, false);
    private @Nullable MultipleTestTracker testTracker;

    public static GameTestServer create(
        Thread serverThread, LevelStorageSource.LevelStorageAccess storageSource, PackRepository packRepository, Optional<String> testSelection, boolean verify
    ) {
        packRepository.reload();
        ArrayList<String> list = new ArrayList<>(packRepository.getAvailableIds());
        list.remove("vanilla");
        list.addFirst("vanilla");
        WorldDataConfiguration worldDataConfiguration = new WorldDataConfiguration(new DataPackConfig(list, List.of()), ENABLED_FEATURES);
        LevelSettings levelSettings = new LevelSettings(
            "Test Level", GameType.CREATIVE, false, Difficulty.NORMAL, true, new GameRules(ENABLED_FEATURES), worldDataConfiguration
        );
        WorldLoader.PackConfig packConfig = new WorldLoader.PackConfig(packRepository, worldDataConfiguration, false, true);
        WorldLoader.InitConfig initConfig = new WorldLoader.InitConfig(packConfig, Commands.CommandSelection.DEDICATED, LevelBasedPermissionSet.OWNER);

        try {
            LOGGER.debug("Starting resource loading");
            Stopwatch stopwatch = Stopwatch.createStarted();
            WorldStem worldStem = Util.<WorldStem>blockUntilDone(
                    executor -> WorldLoader.load(
                        initConfig,
                        context -> {
                            Registry<LevelStem> frozen = new MappedRegistry<>(Registries.LEVEL_STEM, Lifecycle.stable()).freeze();
                            WorldDimensions.Complete complete = context.datapackWorldgen()
                                .lookupOrThrow(Registries.WORLD_PRESET)
                                .getOrThrow(WorldPresets.FLAT)
                                .value()
                                .createWorldDimensions()
                                .bake(frozen);
                            return new WorldLoader.DataLoadOutput<>(
                                new PrimaryLevelData(levelSettings, WORLD_OPTIONS, complete.specialWorldProperty(), complete.lifecycle()),
                                complete.dimensionsRegistryAccess()
                            );
                        },
                        WorldStem::new,
                        Util.backgroundExecutor(),
                        executor
                    )
                )
                .get();
            stopwatch.stop();
            LOGGER.debug("Finished resource loading after {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));
            return new GameTestServer(serverThread, storageSource, packRepository, worldStem, testSelection, verify);
        } catch (Exception var12) {
            LOGGER.warn("Failed to load vanilla datapack, bit oops", (Throwable)var12);
            System.exit(-1);
            throw new IllegalStateException();
        }
    }

    private GameTestServer(
        Thread serverThread,
        LevelStorageSource.LevelStorageAccess storageSource,
        PackRepository packRepository,
        WorldStem worldStem,
        Optional<String> testSelection,
        boolean verify
    ) {
        super(
            null, // Paper
            null, // Paper
            serverThread,
            storageSource,
            packRepository,
            worldStem,
            Proxy.NO_PROXY,
            DataFixers.getDataFixer(),
            NO_SERVICES,
            LoggingLevelLoadListener.forDedicatedServer()
        );
        this.testSelection = testSelection;
        this.verify = verify;
    }

    @Override
    public boolean initServer() {
        // Paper start
        this.setPlayerList(new PlayerList(this, this.registries(), this.playerDataStorage, new EmptyNotificationService()) {
            @Override
            public void loadAndSaveFiles() {
                throw new UnsupportedOperationException("Should not be called in a GameTestServer");
            }
        });
        this.initPostWorld();
        Gizmos.withCollector(GizmoCollector.NOOP);
        this.loadLevel("blah");
        // Paper end
        ServerLevel serverLevel = this.overworld();
        this.testBatches = this.evaluateTestsToRun(serverLevel);
        LOGGER.info("Started game test server");
        return true;
    }

    private List<GameTestBatch> evaluateTestsToRun(ServerLevel level) {
        Registry<GameTestInstance> registry = level.registryAccess().lookupOrThrow(Registries.TEST_INSTANCE);
        Collection<Holder.Reference<GameTestInstance>> collection;
        GameTestBatchFactory.TestDecorator testDecorator;
        if (this.testSelection.isPresent()) {
            collection = getTestsForSelection(level.registryAccess(), this.testSelection.get()).filter(reference -> !reference.value().manualOnly()).toList();
            if (this.verify) {
                testDecorator = GameTestServer::rotateAndMultiply;
                LOGGER.info("Verify requested. Will run each test that matches {} {} times", this.testSelection.get(), 100 * Rotation.values().length);
            } else {
                testDecorator = GameTestBatchFactory.DIRECT;
                LOGGER.info("Will run tests matching {} ({} tests)", this.testSelection.get(), collection.size());
            }
        } else {
            collection = registry.listElements().filter(reference -> !reference.value().manualOnly()).toList();
            testDecorator = GameTestBatchFactory.DIRECT;
        }

        return GameTestBatchFactory.divideIntoBatches(collection, testDecorator, level);
    }

    private static Stream<GameTestInfo> rotateAndMultiply(Holder.Reference<GameTestInstance> test, ServerLevel level) {
        Builder<GameTestInfo> builder = Stream.builder();

        for (Rotation rotation : Rotation.values()) {
            for (int i = 0; i < 100; i++) {
                builder.add(new GameTestInfo(test, rotation, level, RetryOptions.noRetries()));
            }
        }

        return builder.build();
    }

    public static Stream<Holder.Reference<GameTestInstance>> getTestsForSelection(RegistryAccess registries, String selection) {
        return ResourceSelectorArgument.parse(new StringReader(selection), registries.lookupOrThrow(Registries.TEST_INSTANCE)).stream();
    }

    @Override
    // Folia start - region threading
    public void tickServer(long startTime, long scheduledEnd, long targetBuffer,
                           io.papermc.paper.threadedregions.TickRegions.TickRegionData region) {
        if (true) throw new UnsupportedOperationException();
        super.tickServer(startTime, scheduledEnd, targetBuffer, region);
        // Folia end - region threading
        ServerLevel serverLevel = this.overworld();
        if (!this.haveTestsStarted()) {
            this.startTests(serverLevel);
        }

        if (serverLevel.getGameTime() % 20L == 0L) {
            LOGGER.info(this.testTracker.getProgressBar());
        }

        if (this.testTracker.isDone()) {
            this.halt(false);
            LOGGER.info(this.testTracker.getProgressBar());
            GlobalTestReporter.finish();
            LOGGER.info("========= {} GAME TESTS COMPLETE IN {} ======================", this.testTracker.getTotalCount(), this.stopwatch.stop());
            if (this.testTracker.hasFailedRequired()) {
                LOGGER.info("{} required tests failed :(", this.testTracker.getFailedRequiredCount());
                this.testTracker.getFailedRequired().forEach(GameTestServer::logFailedTest);
            } else {
                LOGGER.info("All {} required tests passed :)", this.testTracker.getTotalCount());
            }

            if (this.testTracker.hasFailedOptional()) {
                LOGGER.info("{} optional tests failed", this.testTracker.getFailedOptionalCount());
                this.testTracker.getFailedOptional().forEach(GameTestServer::logFailedTest);
            }

            LOGGER.info("====================================================");
        }
    }

    private static void logFailedTest(GameTestInfo info) {
        if (info.getRotation() != Rotation.NONE) {
            LOGGER.info("   - {} with rotation {}: {}", info.id(), info.getRotation().getSerializedName(), info.getError().getDescription().getString());
        } else {
            LOGGER.info("   - {}: {}", info.id(), info.getError().getDescription().getString());
        }
    }

    @Override
    public SampleLogger getTickTimeLogger() {
        return this.sampleLogger;
    }

    @Override
    public boolean isTickTimeLoggingEnabled() {
        return false;
    }

    @Override
    public void waitUntilNextTick() {
        this.runAllTasks();
    }

    @Override
    public SystemReport fillServerSystemReport(SystemReport report) {
        report.setDetail("Type", "Game test server");
        return report;
    }

    @Override
    public void onServerExit() {
        super.onServerExit();
        LOGGER.info("Game test server shutting down");
        System.exit(this.testTracker != null ? this.testTracker.getFailedRequiredCount() : -1);
    }

    @Override
    public void onServerCrash(CrashReport report) {
        super.onServerCrash(report);
        LOGGER.error("Game test server crashed\n{}", report.getFriendlyReport(ReportType.CRASH));
        System.exit(1);
    }

    private void startTests(ServerLevel level) {
        BlockPos blockPos = new BlockPos(
            level.random.nextIntBetweenInclusive(-14999992, 14999992), -59, level.random.nextIntBetweenInclusive(-14999992, 14999992)
        );
        level.setRespawnData(LevelData.RespawnData.of(level.dimension(), blockPos, 0.0F, 0.0F));
        GameTestRunner gameTestRunner = GameTestRunner.Builder.fromBatches(this.testBatches, level)
            .newStructureSpawner(new StructureGridSpawner(blockPos, 8, false))
            .build();
        Collection<GameTestInfo> testInfos = gameTestRunner.getTestInfos();
        this.testTracker = new MultipleTestTracker(testInfos);
        LOGGER.info("{} tests are now running at position {}!", this.testTracker.getTotalCount(), blockPos.toShortString());
        this.stopwatch.reset();
        this.stopwatch.start();
        gameTestRunner.start();
    }

    private boolean haveTestsStarted() {
        return this.testTracker != null;
    }

    @Override
    public boolean isHardcore() {
        return false;
    }

    @Override
    public LevelBasedPermissionSet operatorUserPermissions() {
        return LevelBasedPermissionSet.ALL;
    }

    @Override
    public PermissionSet getFunctionCompilationPermissions() {
        return LevelBasedPermissionSet.OWNER;
    }

    @Override
    public boolean shouldRconBroadcast() {
        return false;
    }

    @Override
    public boolean isDedicatedServer() {
        return false;
    }

    @Override
    public int getRateLimitPacketsPerSecond() {
        return 0;
    }

    @Override
    public boolean useNativeTransport() {
        return false;
    }

    @Override
    public boolean isPublished() {
        return false;
    }

    @Override
    public boolean shouldInformAdmins() {
        return false;
    }

    // Paper start
    @Override
    public org.bukkit.command.CommandSender getBukkitSender(final net.minecraft.commands.CommandSourceStack wrapper) {
        throw new UnsupportedOperationException("Not supported.");
    }
    // Paper end

    @Override
    public boolean isSingleplayerOwner(NameAndId nameAndId) {
        return false;
    }

    @Override
    public int getMaxPlayers() {
        return 1;
    }

    static class MockProfileResolver implements ProfileResolver {
        @Override
        public Optional<GameProfile> fetchByName(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<GameProfile> fetchById(UUID id) {
            return Optional.empty();
        }
    }

    static class MockUserNameToIdResolver implements UserNameToIdResolver {
        private final Set<NameAndId> savedIds = new HashSet<>();

        @Override
        public void add(NameAndId nameAndId) {
            this.savedIds.add(nameAndId);
        }

        @Override
        public Optional<NameAndId> get(String name) {
            return this.savedIds.stream().filter(nameAndId -> nameAndId.name().equals(name)).findFirst().or(() -> Optional.of(NameAndId.createOffline(name)));
        }

        @Override
        public Optional<NameAndId> get(UUID uuid) {
            return this.savedIds.stream().filter(nameAndId -> nameAndId.id().equals(uuid)).findFirst();
        }

        @Override
        public void resolveOfflineUsers(boolean resolveOfflineUsers) {
        }

        @Override
        public void save() {
        }

        // Paper start
        @Override
        public void save(final boolean async) {
        }

        @Override
        public @Nullable NameAndId getIfCached(final String name) {
            return this.get(name).orElse(null);
        }
        // Paper end
    }
}
