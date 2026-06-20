package net.minecraft.server;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.DataFixer;
import com.mojang.jtracy.DiscontinuousFrame;
import com.mojang.jtracy.TracyClient;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.net.Proxy;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportType;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.SystemReport;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.MiscOverworldFeatures;
import net.minecraft.gametest.framework.GameTestTicker;
import net.minecraft.nbt.Tag;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.chat.ChatDecorator;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.obfuscate.DontObfuscate;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.bossevents.CustomBossEvents;
import net.minecraft.server.level.ChunkLoadCounter;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DemoMode;
import net.minecraft.server.level.PlayerSpawnFinder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.server.level.progress.ChunkLoadStatusView;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.server.network.ServerConnectionListener;
import net.minecraft.server.network.TextFilter;
import net.minecraft.server.notifications.NotificationManager;
import net.minecraft.server.notifications.ServerActivityMonitor;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.server.players.UserWhiteList;
import net.minecraft.server.waypoints.ServerWaypointManager;
import net.minecraft.tags.TagLoader;
import net.minecraft.util.Crypt;
import net.minecraft.util.CryptException;
import net.minecraft.util.FileUtil;
import net.minecraft.util.ModCheck;
import net.minecraft.util.Mth;
import net.minecraft.util.NativeModuleLister;
import net.minecraft.util.PngInfo;
import net.minecraft.util.RandomSource;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.Util;
import net.minecraft.util.debug.ServerDebugSubscribers;
import net.minecraft.util.debugchart.SampleLogger;
import net.minecraft.util.debugchart.TpsDebugDimensions;
import net.minecraft.util.profiling.EmptyProfileResults;
import net.minecraft.util.profiling.ProfileResults;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.profiling.ResultField;
import net.minecraft.util.profiling.SingleTickProfiler;
import net.minecraft.util.profiling.jfr.Environment;
import net.minecraft.util.profiling.jfr.JvmProfiler;
import net.minecraft.util.profiling.jfr.callback.ProfiledDuration;
import net.minecraft.util.profiling.metrics.profiling.ActiveMetricsRecorder;
import net.minecraft.util.profiling.metrics.profiling.InactiveMetricsRecorder;
import net.minecraft.util.profiling.metrics.profiling.MetricsRecorder;
import net.minecraft.util.profiling.metrics.profiling.ServerMetricsSamplersProvider;
import net.minecraft.util.profiling.metrics.storage.MetricsPersister;
import net.minecraft.util.thread.ReentrantBlockableEventLoop;
import net.minecraft.world.Difficulty;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.Stopwatches;
import net.minecraft.world.entity.ai.village.VillageSiege;
import net.minecraft.world.entity.npc.CatSpawner;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTraderSpawner;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.FuelValues;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.ChunkIOErrorReporter;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.PatrolSpawner;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.CommandStorage;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PlayerDataStorage;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WorldData;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.ScoreboardSaveData;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public abstract class MinecraftServer extends ReentrantBlockableEventLoop<TickTask> implements ServerInfo, CommandSource, ChunkIOErrorReporter, ca.spottedleaf.moonrise.patches.chunk_system.server.ChunkSystemMinecraftServer { // Paper - rewrite chunk system
    private static MinecraftServer SERVER; // Paper
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final net.kyori.adventure.text.logger.slf4j.ComponentLogger COMPONENT_LOGGER = net.kyori.adventure.text.logger.slf4j.ComponentLogger.logger(LOGGER.getName()); // Paper
    public static final String VANILLA_BRAND = "vanilla";
    private static final float AVERAGE_TICK_TIME_SMOOTHING = 0.8F;
    private static final int TICK_STATS_SPAN = 100;
    private static final long OVERLOADED_THRESHOLD_NANOS = 30L * TimeUtil.NANOSECONDS_PER_SECOND / 20L; // CraftBukkit
    private static final int OVERLOADED_TICKS_THRESHOLD = 20;
    private static final long OVERLOADED_WARNING_INTERVAL_NANOS = 10L * TimeUtil.NANOSECONDS_PER_SECOND;
    private static final int OVERLOADED_TICKS_WARNING_INTERVAL = 100;
    public static final long STATUS_EXPIRE_TIME_NANOS = 5L * TimeUtil.NANOSECONDS_PER_SECOND; // Folia - region threading - public
    private static final long PREPARE_LEVELS_DEFAULT_DELAY_NANOS = 10L * TimeUtil.NANOSECONDS_PER_MILLISECOND;
    private static final int MAX_STATUS_PLAYER_SAMPLE = 12;
    public static final int SPAWN_POSITION_SEARCH_RADIUS = 5;
    private static final int SERVER_ACTIVITY_MONITOR_SECONDS_BETWEEN_NOTIFICATIONS = 30;
    private static final int AUTOSAVE_INTERVAL = 6000;
    private static final int MIMINUM_AUTOSAVE_TICKS = 100;
    private static final int MAX_TICK_LATENCY = 3;
    public static final int ABSOLUTE_MAX_WORLD_SIZE = 29999984;
    public static final LevelSettings DEMO_SETTINGS = new LevelSettings(
        "Demo World", GameType.SURVIVAL, false, Difficulty.NORMAL, false, new GameRules(FeatureFlags.DEFAULT_FLAGS), WorldDataConfiguration.DEFAULT
    );
    public static final NameAndId ANONYMOUS_PLAYER_PROFILE = new NameAndId(Util.NIL_UUID, "Anonymous Player");
    public LevelStorageSource.LevelStorageAccess storageSource;
    public final PlayerDataStorage playerDataStorage;
    private final List<Runnable> tickables = Lists.newArrayList();
    private MetricsRecorder metricsRecorder = InactiveMetricsRecorder.INSTANCE;
    private Consumer<ProfileResults> onMetricsRecordingStopped = results -> this.stopRecordingMetrics();
    private Consumer<Path> onMetricsRecordingFinished = path -> {};
    private boolean willStartRecordingMetrics;
    private MinecraftServer.@Nullable TimeProfiler debugCommandProfiler;
    private boolean debugCommandProfilerDelayStart;
    private ServerConnectionListener connection;
    // Paper - per world load listener - moved LevelLoadListener to ServerLevel
    private @Nullable ServerStatus status;
    private ServerStatus.@Nullable Favicon statusIcon;
    private final RandomSource random = RandomSource.create();
    public final DataFixer fixerUpper;
    private String localIp;
    private int port = -1;
    private final LayeredRegistryAccess<RegistryLayer> registries;
    private Map<ResourceKey<Level>, ServerLevel> levels = Maps.newLinkedHashMap();
    private PlayerList playerList;
    private volatile boolean running = true;
    private volatile boolean isRestarting = false; // Paper - flag to signify we're attempting to restart
    private boolean stopped;
    // Folia - region threading
    protected final Proxy proxy;
    private boolean onlineMode;
    private boolean preventProxyConnections;
    private net.kyori.adventure.text.Component motd; // Paper - Adventure
    private int playerIdleTimeout;
    private final long[] tickTimesNanos = new long[100];
    private long aggregatedTickTimesNanos = 0L;
    private @Nullable KeyPair keyPair;
    private @Nullable GameProfile singleplayerProfile;
    private boolean isDemo;
    private volatile boolean isReady;
    private long lastOverloadWarningNanos;
    protected final Services services;
    private final NotificationManager notificationManager;
    private final ServerActivityMonitor serverActivityMonitor;
    private long lastServerStatus;
    public final Thread serverThread;
    private long lastTickNanos = Util.getNanos();
    private long taskExecutionStartNanos = Util.getNanos();
    private long idleTimeNanos;
    private long nextTickTimeNanos = Util.getNanos();
    private boolean waitingForNextTick = false;
    private long delayedTasksMaxNextTickTimeNanos;
    private boolean mayHaveDelayedTasks;
    private final PackRepository packRepository;
    private final ServerScoreboard scoreboard = new ServerScoreboard(this);
    private @Nullable Stopwatches stopwatches;
    private @Nullable CommandStorage commandStorage;
    private final CustomBossEvents customBossEvents = new CustomBossEvents();
    private final ServerFunctionManager functionManager;
    private boolean enforceWhitelist;
    private boolean usingWhitelist;
    private float smoothedTickTimeMillis;
    public final Executor executor;
    private @Nullable String serverId;
    public MinecraftServer.ReloadableResources resources;
    private final StructureTemplateManager structureTemplateManager;
    private final ServerTickRateManager tickRateManager;
    private final ServerDebugSubscribers debugSubscribers = new ServerDebugSubscribers(this);
    protected WorldData worldData;
    private LevelData.RespawnData effectiveRespawnData = LevelData.RespawnData.DEFAULT;
    public PotionBrewing potionBrewing;
    private FuelValues fuelValues;
    private int emptyTicks;
    private volatile boolean isSaving;
    private static final AtomicReference<@Nullable RuntimeException> fatalException = new AtomicReference<>();
    private final SuppressedExceptionCollector suppressedExceptions = new SuppressedExceptionCollector();
    private final DiscontinuousFrame tickFrame;
    private final PacketProcessor packetProcessor;

    // CraftBukkit start
    public final WorldLoader.DataLoadContext worldLoaderContext;
    public org.bukkit.craftbukkit.CraftServer server;
    public joptsimple.OptionSet options;
    public org.bukkit.command.ConsoleCommandSender console;
    //public static int currentTick; // Paper - improve tick loop // Folia - region threading
    public java.util.Queue<Runnable> processQueue = new java.util.concurrent.ConcurrentLinkedQueue<Runnable>();
    public int autosavePeriod;
    // Paper - don't store the vanilla dispatcher
    public boolean forceTicks;
    // CraftBukkit end
    // Spigot start
    public static final int TPS = 20;
    public static final int TICK_TIME = 1000000000 / MinecraftServer.TPS;
    // Spigot end
    public volatile boolean hasFullyShutdown; // Paper - Improved watchdog support
    public volatile boolean abnormalExit; // Paper - Improved watchdog support
    public volatile Thread shutdownThread; // Paper - Improved watchdog support
    public final io.papermc.paper.configuration.PaperConfigurations paperConfigurations; // Paper - add paper configuration files
    public boolean isIteratingOverLevels = false; // Paper - Throw exception on world create while being ticked
    private final Set<String> pluginsBlockingSleep = new java.util.HashSet<>(); // Paper - API to allow/disallow tick sleeping
    public static final long SERVER_INIT = System.nanoTime(); // Paper - Lag compensation
    // Paper start - improve tick loop
    public final ca.spottedleaf.moonrise.common.time.TickData tickTimes1s  = new ca.spottedleaf.moonrise.common.time.TickData(java.util.concurrent.TimeUnit.SECONDS.toNanos(1L));
    public final ca.spottedleaf.moonrise.common.time.TickData tickTimes5s  = new ca.spottedleaf.moonrise.common.time.TickData(java.util.concurrent.TimeUnit.SECONDS.toNanos(5L));
    public final ca.spottedleaf.moonrise.common.time.TickData tickTimes10s = new ca.spottedleaf.moonrise.common.time.TickData(java.util.concurrent.TimeUnit.SECONDS.toNanos(10L));
    public final ca.spottedleaf.moonrise.common.time.TickData tickTimes15s = new ca.spottedleaf.moonrise.common.time.TickData(java.util.concurrent.TimeUnit.SECONDS.toNanos(15L));
    public final ca.spottedleaf.moonrise.common.time.TickData tickTimes1m  = new ca.spottedleaf.moonrise.common.time.TickData(java.util.concurrent.TimeUnit.MINUTES.toNanos(1L));
    public final ca.spottedleaf.moonrise.common.time.TickData tickTimes5m  = new ca.spottedleaf.moonrise.common.time.TickData(java.util.concurrent.TimeUnit.MINUTES.toNanos(5L));
    public final ca.spottedleaf.moonrise.common.time.TickData tickTimes15m = new ca.spottedleaf.moonrise.common.time.TickData(java.util.concurrent.TimeUnit.MINUTES.toNanos(15L));

    private final ca.spottedleaf.moonrise.common.time.Schedule tickSchedule = new ca.spottedleaf.moonrise.common.time.Schedule(0L);

    private long lastTickStart;
    private long currentTickStart;
    private long scheduledTickStart;
    private long taskExecutionTime;
    private final Object statsLock = new Object();
    private @Nullable double[] tps;
    private ca.spottedleaf.moonrise.common.time.TickData.@Nullable MSPTData msptData5s;

    private void addTickTime(final ca.spottedleaf.moonrise.common.time.TickTime time) {
        synchronized (this.statsLock) {
            this.tickTimes1s.addDataFrom(time);
            this.tickTimes5s.addDataFrom(time);
            this.tickTimes10s.addDataFrom(time);
            this.tickTimes15s.addDataFrom(time);
            this.tickTimes1m.addDataFrom(time);
            this.tickTimes5m.addDataFrom(time);
            this.tickTimes15m.addDataFrom(time);
            this.clearTickTimeStatistics();
        }
    }

    private void clearTickTimeStatistics() {
        this.msptData5s = null;
        this.tps = null;
    }

    private static double getTPS(final ca.spottedleaf.moonrise.common.time.TickData tickData, final long tickInterval) {
        final Double avg = tickData.getTPSAverage(null, tickInterval);
        if (avg == null) {
            return 1.0E9 / (double)tickInterval;
        }

        return avg;
    }

    public double[] getTPS() {
        synchronized (this.statsLock) {
            double[] tps = this.tps;
            if (tps == null) {
                tps = this.computeTPS();
                this.tps = tps;
            }
            return tps.clone();
        }
    }

    public ca.spottedleaf.moonrise.common.time.TickData.@Nullable MSPTData getMSPTData5s() {
        synchronized (this.statsLock) {
            if (this.msptData5s == null) {
                this.msptData5s = this.tickTimes5s.getMSPTData(null, this.tickRateManager().nanosecondsPerTick());
            }
            return this.msptData5s;
        }
    }

    public double[] computeTPS() {
        final long interval = this.tickRateManager().nanosecondsPerTick();
        return new double[] {
            getTPS(this.tickTimes1m, interval),
            getTPS(this.tickTimes5m, interval),
            getTPS(this.tickTimes15m, interval)
        };
    }
    // Paper end - improve tick loop

    // Folia start - regionised ticking
    public final io.papermc.paper.threadedregions.RegionizedServer regionizedServer = new io.papermc.paper.threadedregions.RegionizedServer();

    @Override
    public <V> CompletableFuture<V> submit(java.util.function.Supplier<V> task) {
        if (true) {
            throw new UnsupportedOperationException();
        }
        return super.submit(task);
    }

    @Override
    public CompletableFuture<Void> submit(Runnable task) {
        if (true) {
            throw new UnsupportedOperationException();
        }
        return super.submit(task);
    }

    @Override
    public void schedule(TickTask task) {
        if (true) {
            throw new UnsupportedOperationException();
        }
        super.schedule(task);
    }

    @Override
    public void executeBlocking(Runnable runnable) {
        if (true) {
            throw new UnsupportedOperationException();
        }
        super.executeBlocking(runnable);
    }

    @Override
    public void execute(Runnable runnable) {
        if (true) {
            throw new UnsupportedOperationException();
        }
        super.execute(runnable);
    }
    // Folia end - regionised ticking

    public static <S extends MinecraftServer> S spin(Function<Thread, S> threadFunction) {
        ca.spottedleaf.dataconverter.minecraft.datatypes.MCTypeRegistry.init(); // Paper - rewrite data converter system
        AtomicReference<S> atomicReference = new AtomicReference<>();
        Thread thread = new ca.spottedleaf.moonrise.common.util.TickThread(() -> atomicReference.get().runServer(), "Server thread");
        thread.setUncaughtExceptionHandler((thread1, exception) -> LOGGER.error("Uncaught exception in server thread", exception));
        thread.setPriority(Thread.NORM_PRIORITY+2); // Paper - Perf: Boost priority
        if (Runtime.getRuntime().availableProcessors() > 4) {
            thread.setPriority(8);
        }

        S minecraftServer = (S)threadFunction.apply(thread);
        atomicReference.set(minecraftServer);
        thread.start();
        return minecraftServer;
    }

    // Paper start - rewrite chunk system
    private volatile Throwable chunkSystemCrash;

    @Override
    public final void moonrise$setChunkSystemCrash(final Throwable throwable) {
        this.chunkSystemCrash = throwable;
    }

    private static final long CHUNK_TASK_QUEUE_BACKOFF_MIN_TIME = 25L * 1000L; // 25us
    private static final long MAX_CHUNK_EXEC_TIME = 1000L; // 1us
    private static final long TASK_EXECUTION_FAILURE_BACKOFF = 5L * 1000L; // 5us

    // Folia - region threading - moved to regionized data

    private boolean tickMidTickTasks(final io.papermc.paper.threadedregions.RegionizedWorldData worldData) { // Folia - region threading
        return worldData.world.getChunkSource().pollTask(); // Folia - region threading
    }

    @Override
    public final void moonrise$executeMidTickTasks() {
        io.papermc.paper.threadedregions.RegionizedWorldData worldData = io.papermc.paper.threadedregions.TickRegionScheduler.getCurrentRegionizedWorldData(); // Folia - region threading
        final long startTime = System.nanoTime();
        if ((startTime - worldData.lastMidTickExecute) <= CHUNK_TASK_QUEUE_BACKOFF_MIN_TIME || (startTime - worldData.lastMidTickExecuteFailure) <= TASK_EXECUTION_FAILURE_BACKOFF) { // Folia - region threading
            // it's shown to be bad to constantly hit the queue (chunk loads slow to a crawl), even if no tasks are executed.
            // so, backoff to prevent this
            return;
        }

        for (;;) {
            final boolean moreTasks = this.tickMidTickTasks(worldData); // Folia - region threading
            final long currTime = System.nanoTime();
            final long diff = currTime - startTime;

            if (!moreTasks || diff >= MAX_CHUNK_EXEC_TIME) {
                if (!moreTasks) {
                    worldData.lastMidTickExecuteFailure = currTime; // Folia - region threading
                }

                // note: negative values reduce the time
                long overuse = diff - MAX_CHUNK_EXEC_TIME;
                if (overuse >= (10L * 1000L * 1000L)) { // 10ms
                    // make sure something like a GC or dumb plugin doesn't screw us over...
                    overuse = 10L * 1000L * 1000L; // 10ms
                }

                final double overuseCount = (double)overuse/(double)MAX_CHUNK_EXEC_TIME;
                final long extraSleep = (long)Math.round(overuseCount*CHUNK_TASK_QUEUE_BACKOFF_MIN_TIME);

                worldData.lastMidTickExecute = currTime + extraSleep; // Folia - region threading
                return;
            }
        }
    }

   @Override
   public final void moonrise$issueEmergencySave() {
       LOGGER.warn("Performing emergency save...");
       LOGGER.info("Saving all players...");
       this.getPlayerList().saveAll();
       LOGGER.info("Saved all players");
       LOGGER.info("Saving all worlds...");
       for (final ServerLevel world : this.getAllLevels()) {
           LOGGER.info("Saving chunks in world '" + ca.spottedleaf.moonrise.common.util.WorldUtil.getWorldName(world) + "'...");
           ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)world).moonrise$issueEmergencySave();
           LOGGER.info("Saved chunks in world '" + ca.spottedleaf.moonrise.common.util.WorldUtil.getWorldName(world) + "'...");
       }
       LOGGER.info("Saved all worlds");
       LOGGER.warn("Performed emergency save");
   }
    // Paper end - rewrite chunk system

    public MinecraftServer(
        // CraftBukkit start
        joptsimple.OptionSet options,
        WorldLoader.DataLoadContext worldLoaderContext,
        // CraftBukkit end
        Thread serverThread,
        LevelStorageSource.LevelStorageAccess storageSource,
        PackRepository packRepository,
        WorldStem worldStem,
        Proxy proxy,
        DataFixer fixerUpper,
        Services services,
        LevelLoadListener levelLoadListener
    ) {
        super("Server");
        SERVER = this; // Paper - better singleton
        this.registries = worldStem.registries();
        this.worldData = worldStem.worldData();
        if (false && !this.registries.compositeAccess().lookupOrThrow(Registries.LEVEL_STEM).containsKey(LevelStem.OVERWORLD)) { // CraftBukkit - initialised later
            throw new IllegalStateException("Missing Overworld dimension data");
        } else {
            this.proxy = proxy;
            this.packRepository = packRepository;
            this.resources = new MinecraftServer.ReloadableResources(worldStem.resourceManager(), worldStem.dataPackResources());
            this.services = services;
            // this.connection = new ServerConnectionListener(this); // Spigot
            this.tickRateManager = new ServerTickRateManager(this);
            // Paper - per level load listener - move LevelLoadListener to ServerLevel
            this.storageSource = storageSource;
            this.playerDataStorage = storageSource.createPlayerStorage();
            this.fixerUpper = fixerUpper;
            this.functionManager = new ServerFunctionManager(this, this.resources.managers.getFunctionLibrary());
            HolderGetter<Block> holderGetter = this.registries
                .compositeAccess()
                .lookupOrThrow(Registries.BLOCK)
                .filterFeatures(this.worldData.enabledFeatures());
            this.structureTemplateManager = new StructureTemplateManager(worldStem.resourceManager(), storageSource, fixerUpper, holderGetter);
            this.serverThread = serverThread;
            this.executor = Util.backgroundExecutor();
            this.potionBrewing = PotionBrewing.bootstrap(this.worldData.enabledFeatures());
            this.resources.managers.getRecipeManager().finalizeRecipeLoading(this.worldData.enabledFeatures());
            this.fuelValues = FuelValues.vanillaBurnTimes(this.registries.compositeAccess(), this.worldData.enabledFeatures());
            this.tickFrame = TracyClient.createDiscontinuousFrame("Server Tick");
            this.notificationManager = new NotificationManager();
            this.serverActivityMonitor = new ServerActivityMonitor(this.notificationManager, 30);
            this.packetProcessor = null; // Folia - region threading
        }
        // CraftBukkit start
        this.options = options;
        this.worldLoaderContext = worldLoaderContext;
        // Paper start - Handled by TerminalConsoleAppender
        // Try to see if we're actually running in a terminal, disable jline if not
        /*
        if (System.console() == null && System.getProperty("jline.terminal") == null) {
            System.setProperty("jline.terminal", "jline.UnsupportedTerminal");
            Main.useJline = false;
        }

        try {
            this.reader = new ConsoleReader(System.in, System.out);
            this.reader.setExpandEvents(false); // Avoid parsing exceptions for uncommonly used event designators
        } catch (Throwable e) {
            try {
                // Try again with jline disabled for Windows users without C++ 2008 Redistributable
                System.setProperty("jline.terminal", "jline.UnsupportedTerminal");
                System.setProperty("user.language", "en");
                Main.useJline = false;
                this.reader = new ConsoleReader(System.in, System.out);
                this.reader.setExpandEvents(false);
            } catch (IOException ex) {
                MinecraftServer.LOGGER.warn((String) null, ex);
            }
        }
        */
        // Paper end
        io.papermc.paper.log.LogManagerShutdownThread.unhook(); // Paper - Improved watchdog support
        Runtime.getRuntime().addShutdownHook(new org.bukkit.craftbukkit.util.ServerShutdownThread(this));
        // CraftBukkit end
        this.paperConfigurations = services.paper().configurations(); // Paper - add paper configuration files
    }

    protected abstract boolean initServer() throws IOException;

    public ChunkLoadStatusView createChunkLoadStatusView(final int radius) {
        return new ChunkLoadStatusView() {
            private @Nullable ChunkMap chunkMap;
            private int centerChunkX;
            private int centerChunkZ;

            @Override
            public void moveTo(ResourceKey<Level> dimension, ChunkPos chunkPos) {
                ServerLevel level = MinecraftServer.this.getLevel(dimension);
                this.chunkMap = level != null ? level.getChunkSource().chunkMap : null;
                this.centerChunkX = chunkPos.x;
                this.centerChunkZ = chunkPos.z;
            }

            @Override
            public @Nullable ChunkStatus get(int x, int z) {
                return this.chunkMap == null
                    ? null
                    : this.chunkMap.getLatestStatus(ChunkPos.asLong(x + this.centerChunkX - radius, z + this.centerChunkZ - radius));
            }

            @Override
            public int radius() {
                return radius;
            }
        };
    }

    protected void loadLevel(String levelId) { // CraftBukkit
        boolean flag = !JvmProfiler.INSTANCE.isRunning()
            && SharedConstants.DEBUG_JFR_PROFILING_ENABLE_LEVEL_LOADING
            && JvmProfiler.INSTANCE.start(Environment.from(this));
        ProfiledDuration profiledDuration = JvmProfiler.INSTANCE.onWorldLoadedStarted();
        // Paper start - rework world loading process
        io.papermc.paper.world.PaperWorldLoader loader = io.papermc.paper.world.PaperWorldLoader.create(this, levelId);
        loader.loadInitialWorlds();
        // Paper end - rework world loading process
        if (profiledDuration != null) {
            profiledDuration.finish(true);
        }

        if (flag) {
            try {
                JvmProfiler.INSTANCE.stop();
            } catch (Throwable var4) {
                LOGGER.warn("Failed to stop JFR profiling", var4);
            }
        }
    }

    // Paper start - rework world loading process
    protected void initPostWorld() {
        // Paper start - Configurable player collision; Handle collideRule team for player collision toggle
        final ServerScoreboard scoreboard = this.getScoreboard();
        final java.util.Collection<String> toRemove = scoreboard.getPlayerTeams().stream().filter(team -> team.getName().startsWith("collideRule_")).map(net.minecraft.world.scores.PlayerTeam::getName).collect(java.util.stream.Collectors.toList());
        for (String teamName : toRemove) {
            scoreboard.removePlayerTeam(scoreboard.getPlayerTeam(teamName)); // Clean up after ourselves
        }

        if (!io.papermc.paper.configuration.GlobalConfiguration.get().collisions.enablePlayerCollisions) {
            this.getPlayerList().collideRuleTeamName = org.apache.commons.lang3.StringUtils.left("collideRule_" + java.util.concurrent.ThreadLocalRandom.current().nextInt(), 16);
            net.minecraft.world.scores.PlayerTeam collideTeam = scoreboard.addPlayerTeam(this.getPlayerList().collideRuleTeamName);
            collideTeam.setSeeFriendlyInvisibles(false); // Because we want to mimic them not being on a team at all
        }
        // Paper end - Configurable player collision; Handle collideRule team for player collision toggle
        this.server.enablePlugins(org.bukkit.plugin.PluginLoadOrder.POSTWORLD);
        if (false) this.server.spark.registerCommandBeforePlugins(this.server); // Paper - spark // Luminol - Force disable builtin spark
        if (false) this.server.spark.enableAfterPlugins(this.server); // Paper - spark // Luminol - Force disable builtin spark
        if (io.papermc.paper.plugin.PluginInitializerManager.instance().pluginRemapper != null) io.papermc.paper.plugin.PluginInitializerManager.instance().pluginRemapper.pluginsEnabled(); // Paper - Remap plugins
        io.papermc.paper.command.brigadier.PaperCommands.INSTANCE.setValid(); // Paper - reset invalid state for event fire below
        io.papermc.paper.plugin.lifecycle.event.LifecycleEventRunner.INSTANCE.callReloadableRegistrarEvent(io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents.COMMANDS, io.papermc.paper.command.brigadier.PaperCommands.INSTANCE, org.bukkit.plugin.Plugin.class, io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent.Cause.INITIAL); // Paper - call commands event for regular plugins
        this.server.getCommandMap().registerServerAliases(); // Paper - relocate initial CommandMap#registerServerAliases() call
        ((org.bukkit.craftbukkit.help.SimpleHelpMap) this.server.getHelpMap()).initializeCommands();
        this.server.getPluginManager().callEvent(new org.bukkit.event.server.ServerLoadEvent(org.bukkit.event.server.ServerLoadEvent.LoadType.STARTUP));
        this.connection.acceptConnections();
    }
    // Paper end - rework world loading process

    protected void forceDifficulty() {
    }

    // Paper start - rework world loading process
    public void createLevel(
        LevelStem levelStem,
        io.papermc.paper.world.PaperWorldLoader.WorldLoadingInfo loadingInfo,
        LevelStorageSource.LevelStorageAccess levelStorageAccess,
        net.minecraft.world.level.storage.PrimaryLevelData serverLevelData
    ) {
        WorldOptions worldOptions = serverLevelData.worldGenOptions();
        long seed = worldOptions.seed();
        long l = BiomeManager.obfuscateSeed(seed);
        List<CustomSpawner> list = ImmutableList.of(
            new PhantomSpawner(), new PatrolSpawner(), new CatSpawner(), new VillageSiege(), new WanderingTraderSpawner(serverLevelData)
        );
        final org.bukkit.generator.ChunkGenerator chunkGenerator = this.server.getGenerator(loadingInfo.name());
        org.bukkit.generator.BiomeProvider biomeProvider = this.server.getBiomeProvider(loadingInfo.name());
        final org.bukkit.generator.WorldInfo worldInfo = new org.bukkit.craftbukkit.generator.CraftWorldInfo(
            serverLevelData,
            levelStorageAccess,
            org.bukkit.World.Environment.getEnvironment(loadingInfo.dimension()),
            levelStem.type().value(),
            levelStem.generator(),
            this.registryAccess()
        );
        if (biomeProvider == null && chunkGenerator != null) {
            biomeProvider = chunkGenerator.getDefaultBiomeProvider(worldInfo);
        }
        final ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, loadingInfo.stemKey().identifier());
        ServerLevel serverLevel;
        if (loadingInfo.stemKey() == LevelStem.OVERWORLD) {
            serverLevel = new ServerLevel(
                this,
                this.executor,
                levelStorageAccess,
                serverLevelData,
                dimensionKey,
                levelStem,
                serverLevelData.isDebugWorld(),
                l,
                list,
                true,
                null,
                org.bukkit.World.Environment.getEnvironment(loadingInfo.dimension()),
                chunkGenerator,
                biomeProvider
            );
            this.worldData = serverLevelData;
            this.worldData.setGameType(((net.minecraft.server.dedicated.DedicatedServer) this).getProperties().gameMode.get()); // From DedicatedServer.init
        DimensionDataStorage dataStorage = serverLevel.getDataStorage();
        this.scoreboard.load(dataStorage.computeIfAbsent(ScoreboardSaveData.TYPE).getData());
        this.commandStorage = new CommandStorage(dataStorage);
        this.stopwatches = dataStorage.computeIfAbsent(Stopwatches.TYPE);
            this.server.scoreboardManager = new org.bukkit.craftbukkit.scoreboard.CraftScoreboardManager(this, serverLevel.getScoreboard());
        } else {
            final List<CustomSpawner> spawners;
            if (io.papermc.paper.configuration.GlobalConfiguration.get().misc.useDimensionTypeForCustomSpawners && levelStem.type().is(net.minecraft.world.level.dimension.BuiltinDimensionTypes.OVERWORLD)) {
                spawners = list;
            } else {
                spawners = Collections.emptyList();
            }
            serverLevel = new ServerLevel(
                this,
                this.executor,
                levelStorageAccess,
                serverLevelData,
                dimensionKey,
                levelStem,
                this.worldData.isDebugWorld(),
                l,
                spawners,
                true,
                this.overworld().getRandomSequences(),
                org.bukkit.World.Environment.getEnvironment(loadingInfo.dimension()),
                chunkGenerator,
                biomeProvider
            );
        }
        this.addLevel(serverLevel);
        this.initWorld(serverLevel, serverLevelData, worldOptions);
    }
    public void initWorld(ServerLevel serverLevel, net.minecraft.world.level.storage.PrimaryLevelData serverLevelData, WorldOptions worldOptions) {
        final boolean isDebugWorld = this.worldData.isDebugWorld();
        if (serverLevel.generator != null) {
            serverLevel.getWorld().getPopulators().addAll(serverLevel.generator.getDefaultPopulators(serverLevel.getWorld()));
        }
        this.initWorldBorder(serverLevelData, serverLevel);
        this.server.getPluginManager().callEvent(new org.bukkit.event.world.WorldInitEvent(serverLevel.getWorld()));
    // Paper end - rework world loading process
        if (!serverLevelData.isInitialized()) {
            try {
                setInitialSpawn(serverLevel, serverLevelData, worldOptions.generateBonusChest(), isDebugWorld, serverLevel.levelLoadListener); // Paper - per world level load listener
                serverLevelData.setInitialized(true);
                if (isDebugWorld) {
                    this.setupDebugLevel(this.worldData);
                }
            } catch (Throwable var28) {
                CrashReport crashReport = CrashReport.forThrowable(var28, "Exception initializing level");

                try {
                    serverLevel.fillReportDetails(crashReport);
                } catch (Throwable var27) {
                }

                throw new ReportedException(crashReport);
            }

            serverLevelData.setInitialized(true);
        }

        GlobalPos globalPos = this.selectLevelLoadFocusPos();
        serverLevel.levelLoadListener.updateFocus(globalPos.dimension(), new ChunkPos(globalPos.pos())); // Paper - per world load listener
        if (serverLevelData.getCustomBossEvents() != null) { // Paper - rework world loading process
            this.getCustomBossEvents().load(serverLevelData.getCustomBossEvents(), this.registryAccess()); // Paper - rework world loading process
        }

        // Paper start - rework world loading process
    }
    private void initWorldBorder(net.minecraft.world.level.storage.PrimaryLevelData serverLevelData, ServerLevel serverLevel) {
        final ServerLevel serverLevel1 = serverLevel; // Rename for below code
        // Paper end - rework world loading process
            Optional<WorldBorder.Settings> legacyWorldBorderSettings = serverLevelData.getLegacyWorldBorderSettings();
            if (legacyWorldBorderSettings.isPresent()) {
                WorldBorder.Settings settings = legacyWorldBorderSettings.get();
                DimensionDataStorage dataStorage1 = serverLevel1.getDataStorage();
                if (dataStorage1.get(WorldBorder.TYPE) == null) {
                    double coordinateScale = serverLevel1.dimensionType().coordinateScale();
                    WorldBorder.Settings settings1 = new WorldBorder.Settings(
                        settings.centerX() / coordinateScale,
                        settings.centerZ() / coordinateScale,
                        settings.damagePerBlock(),
                        settings.safeZone(),
                        settings.warningBlocks(),
                        settings.warningTime(),
                        settings.size(),
                        settings.lerpTime(),
                        settings.lerpTarget()
                    );
                    WorldBorder worldBorder = new WorldBorder(settings1);
                    worldBorder.applyInitialSettings(serverLevel1.getGameTime());
                    dataStorage1.set(WorldBorder.TYPE, worldBorder);
                }

                serverLevelData.setLegacyWorldBorderSettings(Optional.empty()); // Paper - rework world loading process
            }

        // Paper start - rework world loading process
        serverLevel.getWorldBorder().world = serverLevel;
        serverLevel.getWorldBorder().setAbsoluteMaxSize(this.getAbsoluteMaxWorldSize());
        this.getPlayerList().addWorldborderListener(serverLevel);
        // Paper end - rework world loading process
    }

    private static void setInitialSpawn(
        ServerLevel level, ServerLevelData levelData, boolean generateBonusChest, boolean debug, LevelLoadListener levelLoadListener
    ) {
        if (SharedConstants.DEBUG_ONLY_GENERATE_HALF_THE_WORLD && SharedConstants.DEBUG_WORLD_RECREATE) {
            levelData.setSpawn(LevelData.RespawnData.of(level.dimension(), new BlockPos(0, 64, -100), 0.0F, 0.0F));
        } else if (debug) {
            levelData.setSpawn(LevelData.RespawnData.of(level.dimension(), BlockPos.ZERO.above(80), 0.0F, 0.0F));
        } else {
            ServerChunkCache chunkSource = level.getChunkSource();
            // CraftBukkit start
            if (level.generator != null) {
                java.util.Random rand = new java.util.Random(level.getSeed());
                org.bukkit.Location spawn = level.generator.getFixedSpawnLocation(level.getWorld(), rand);

                if (spawn != null) {
                    if (spawn.getWorld() != level.getWorld()) {
                        throw new IllegalStateException("Cannot set spawn point for " + levelData.getLevelName() + " to be in another world (" + spawn.getWorld().getName() + ")");
                    } else {
                        levelData.setSpawn(
                            net.minecraft.world.level.storage.LevelData.RespawnData.of(
                                level.dimension(),
                                org.bukkit.craftbukkit.util.CraftLocation.toBlockPosition(spawn),
                                spawn.getYaw(),
                                spawn.getPitch()
                            )
                        );
                        return;
                    }
                }
            }
            // CraftBukkit end
            ChunkPos chunkPos = new ChunkPos(chunkSource.randomState().sampler().findSpawnPosition());
            levelLoadListener.start(LevelLoadListener.Stage.PREPARE_GLOBAL_SPAWN, 0);
            levelLoadListener.updateFocus(level.dimension(), chunkPos);
            int spawnHeight = chunkSource.getGenerator().getSpawnHeight(level);
            if (spawnHeight < level.getMinY()) {
                // Folia start - region threading
                // pre-apply offset to make the rest easier
                BlockPos worldPosition = chunkPos.getWorldPosition().offset(8, 0, 8);
                spawnHeight = level.moonrise$getChunkTaskScheduler().syncLoadNonFull(
                        worldPosition.getX() >> 4, worldPosition.getZ() >> 4,
                        net.minecraft.world.level.chunk.status.ChunkStatus.SPAWN
                ).getHeight(Heightmap.Types.WORLD_SURFACE, worldPosition.getX(), worldPosition.getZ()) + 1; // add one to match LevelReader#getHeight
                // Folia end - region threading
            }

            levelData.setSpawn(LevelData.RespawnData.of(level.dimension(), chunkPos.getWorldPosition().offset(8, spawnHeight, 8), 0.0F, 0.0F));
            int i = 0;
            int i1 = 0;
            int i2 = 0;
            int i3 = -1;

            for (int i4 = 0; i4 < Mth.square(11); i4++) {
                if (i >= -5 && i <= 5 && i1 >= -5 && i1 <= 5) {
                    BlockPos spawnPosInChunk = PlayerSpawnFinder.getSpawnPosInChunk(level, new ChunkPos(chunkPos.x + i, chunkPos.z + i1));
                    if (spawnPosInChunk != null) {
                        levelData.setSpawn(LevelData.RespawnData.of(level.dimension(), spawnPosInChunk, 0.0F, 0.0F));
                        break;
                    }
                }

                if (i == i1 || i < 0 && i == -i1 || i > 0 && i == 1 - i1) {
                    int i5 = i2;
                    i2 = -i3;
                    i3 = i5;
                }

                i += i2;
                i1 += i3;
            }

            if (false && generateBonusChest) { // Folia - region threading - too lazy to hack around this one
                level.registryAccess()
                    .lookup(Registries.CONFIGURED_FEATURE)
                    .flatMap(registry -> registry.get(MiscOverworldFeatures.BONUS_CHEST))
                    .ifPresent(holder -> holder.value().place(level, chunkSource.getGenerator(), level.random, levelData.getRespawnData().pos()));
            }

            levelLoadListener.finish(LevelLoadListener.Stage.PREPARE_GLOBAL_SPAWN);
        }
    }

    private void setupDebugLevel(WorldData worldData) {
        worldData.setDifficulty(Difficulty.PEACEFUL);
        worldData.setDifficultyLocked(true);
        ServerLevelData serverLevelData = worldData.overworldData();
        serverLevelData.setRaining(false);
        serverLevelData.setThundering(false);
        serverLevelData.setClearWeatherTime(1000000000);
        serverLevelData.setDayTime(6000L);
        serverLevelData.setGameType(GameType.SPECTATOR);
    }

    // CraftBukkit start
    public void prepareLevel(ServerLevel serverLevel) {
        this.forceTicks = true;
        // CraftBukkit end
        ChunkLoadCounter chunkLoadCounter = new ChunkLoadCounter();

        if (true) { // CraftBukkit
            //chunkLoadCounter.track(serverLevel, () -> { // Folia - region threading
                TicketStorage ticketStorage = serverLevel.getDataStorage().get(TicketStorage.TYPE);
                if (ticketStorage != null) {
                    ticketStorage.activateAllDeactivatedTickets();
                }
            //}); // Folia - region threading
        }

        serverLevel.levelLoadListener.start(LevelLoadListener.Stage.LOAD_INITIAL_CHUNKS, chunkLoadCounter.totalChunks()); // Paper - per world load listener

        do {
            serverLevel.levelLoadListener.update(LevelLoadListener.Stage.LOAD_INITIAL_CHUNKS, chunkLoadCounter.readyChunks(), chunkLoadCounter.totalChunks()); // Paper - per world load listener
            //this.executeModerately(); // CraftBukkit // Folia - region threading
        } while (chunkLoadCounter.pendingChunks() > 0);

        serverLevel.levelLoadListener.finish(LevelLoadListener.Stage.LOAD_INITIAL_CHUNKS); // Paper - per world load listener
        serverLevel.setSpawnSettings(serverLevel.isSpawningMonsters()); // Paper - per level difficulty (from setDifficulty(ServerLevel, Difficulty, boolean))
        this.updateEffectiveRespawnData();
        this.forceTicks = false; // CraftBukkit
        //serverLevel.entityManager.tick(); // SPIGOT-6526: Load pending entities so they are available to the API // Paper - rewrite chunk system
        io.papermc.paper.threadedregions.RegionizedServer.getInstance().addWorld(serverLevel); // Folia - region threading
        new org.bukkit.event.world.WorldLoadEvent(serverLevel.getWorld()).callEvent(); // Paper - call WorldLoadEvent
    }

    protected GlobalPos selectLevelLoadFocusPos() {
        return this.worldData.overworldData().getRespawnData().globalPos();
    }

    public GameType getDefaultGameType() {
        return this.worldData.getGameType();
    }

    public boolean isHardcore() {
        return this.worldData.isHardcore();
    }

    public abstract LevelBasedPermissionSet operatorUserPermissions();

    public abstract PermissionSet getFunctionCompilationPermissions();

    public abstract boolean shouldRconBroadcast();

    public boolean saveAllChunks(boolean suppressLogs, boolean flush, boolean force) {
        // Paper start - add close param
        return this.saveAllChunks(suppressLogs, flush, force, false);
    }
    public boolean saveAllChunks(boolean suppressLogs, boolean flush, boolean force, boolean close) {
        // Paper end - add close param
        if (this.overworld() != null) this.scoreboard.storeToSaveDataIfDirty(this.overworld().getDataStorage().computeIfAbsent(ScoreboardSaveData.TYPE)); // Paper - don't try to save if the overworld was not loaded, generally during early startup failures
        boolean flag = false;

        for (ServerLevel serverLevel : this.getAllLevels()) {
            if (!suppressLogs) {
                LOGGER.info("Saving chunks for level '{}'/{}", serverLevel, serverLevel.dimension().identifier());
            }

            serverLevel.save(null, flush, SharedConstants.DEBUG_DONT_SAVE_WORLD || serverLevel.noSave && !force, close); // Paper - add close param
            flag = true;
        }

        // CraftBukkit start - moved to ServerLevel#save
        // this.worldData.setCustomBossEvents(this.getCustomBossEvents().save(this.registryAccess()));
        // this.storageSource.saveDataTag(this.registryAccess(), this.worldData, this.getPlayerList().getSingleplayerData());
        // CraftBukkit end
        if (flush) {
            for (ServerLevel serverLevel : this.getAllLevels()) {
                LOGGER.info("ThreadedChunkStorage ({}): All chunks are saved", serverLevel.getChunkSource().chunkMap.getStorageName()); // Luminol - configurable region format
            }

            LOGGER.info("ThreadedChunkStorage: All dimensions are saved"); // Luminol - configurable region format
        }

        return flag;
    }

    public boolean saveEverything(boolean suppressLogs, boolean flush, boolean force) {
        boolean var4;
        try {
            this.isSaving = true;
            this.getPlayerList().saveAll(); // Paper - Incremental chunk and player saving; diff on change
            var4 = this.saveAllChunks(suppressLogs, flush, force);
        } finally {
            this.isSaving = false;
        }

        return var4;
    }

    @Override
    public void close() {
        this.stopServer();
    }

    // CraftBukkit start
    private boolean hasStopped = false;
    private boolean hasLoggedStop = false; // Paper - Debugging
    private final Object stopLock = new Object();
    public final boolean hasStopped() {
        synchronized (this.stopLock) {
            return this.hasStopped;
        }
    }
    // CraftBukkit end

    // Folia start - region threading
    private final java.util.concurrent.atomic.AtomicBoolean hasStartedShutdownThread = new java.util.concurrent.atomic.AtomicBoolean();

    private void haltServerRegionThreading() {
        if (this.hasStartedShutdownThread.getAndSet(true)) {
            // already started shutdown
            return;
        }
        new io.papermc.paper.threadedregions.RegionShutdownThread("Region shutdown thread").start();
    }

    public void haltCurrentRegion() {
        if (!ca.spottedleaf.moonrise.common.util.TickThread.isShutdownThread()) {
            throw new IllegalStateException();
        }
    }
    // Folia end - region threading

    public void stopServer() {
        // Folia start - region threading
        // halt scheduler
        // don't wait, we may be on a scheduler thread
        io.papermc.paper.threadedregions.TickRegions.getScheduler().halt(false, 0L);
        // cannot run shutdown logic on this thread, as it may be a scheduler
        if (true) {
            if (!ca.spottedleaf.moonrise.common.util.TickThread.isShutdownThread()) {
                this.haltServerRegionThreading();
                return;
            } // else: fall through to regular stop logic
        }
        // Folia end - region threading
        // CraftBukkit start - prevent double stopping on multiple threads
        synchronized(this.stopLock) {
            if (this.hasStopped) return;
            this.hasStopped = true;
        }
        if (!hasLoggedStop && isDebugging()) io.papermc.paper.util.TraceUtil.dumpTraceForThread("Server stopped"); // Paper - Debugging
        shutdownThread = Thread.currentThread(); // Paper - Improved watchdog support
        org.spigotmc.WatchdogThread.doStop(); // Paper - Improved watchdog support
        // CraftBukkit end
        //this.packetProcessor.close(); // Folia - region threading
        if (this.metricsRecorder.isRecording()) {
            this.cancelRecordingMetrics();
        }

        LOGGER.info("Stopping server");
        Commands.COMMAND_SENDING_POOL.shutdownNow(); // Paper - Perf: Async command map building; Shutdown and don't bother finishing
        // CraftBukkit start
        if (this.server != null) {
            if (false) this.server.spark.disable(); // Paper - spark // Luminol - Force disable builtin spark
            this.server.disablePlugins();
            this.server.waitForAsyncTasksShutdown(); // Paper - Wait for Async Tasks during shutdown
        }
        // CraftBukkit end
        if (io.papermc.paper.plugin.PluginInitializerManager.instance().pluginRemapper != null) io.papermc.paper.plugin.PluginInitializerManager.instance().pluginRemapper.shutdown(); // Paper - Plugin remapping
        this.getConnection().stop();
        this.isSaving = true;
        if (this.playerList != null) {
            //LOGGER.info("Saving players"); // Folia - move to shutdown thread logic
            //this.playerList.saveAll(); // Folia - move to shutdown thread logic
            this.playerList.removeAll(this.isRestarting); // Paper
            try { Thread.sleep(100); } catch (InterruptedException ex) {} // CraftBukkit - SPIGOT-625 - give server at least a chance to send packets
        }

        // Folia start - region threading
        if (true) {
            // the rest till part 2 is handled by the region shutdown thread
            return;
        }
        // Folia end - region threading

        LOGGER.info("Saving worlds");

        for (ServerLevel serverLevel : this.getAllLevels()) {
            if (serverLevel != null) {
                serverLevel.noSave = false;
            }
        }

        while (false && this.levels.values().stream().anyMatch(level -> level.getChunkSource().chunkMap.hasWork())) { // Paper - rewrite chunk system
            this.nextTickTimeNanos = Util.getNanos() + TimeUtil.NANOSECONDS_PER_MILLISECOND;

            for (ServerLevel serverLevelx : this.getAllLevels()) {
                serverLevelx.getChunkSource().deactivateTicketsOnClosing();
                serverLevelx.getChunkSource().tick(() -> true, false);
            }

            this.waitUntilNextTick();
        }
        // Paper start - rewrite chunk system
        // note: make sure we call deactivateTicketsOnClosing
        for (final ServerLevel world : this.getAllLevels()) {
            world.getChunkSource().deactivateTicketsOnClosing();
        }
        // Paper end - rewrite chunk system

        this.saveAllChunks(false, true, false, true); // Paper - rewrite chunk system

        this.isSaving = false;
        // Folia start - region threading
        this.stopPart2();
    }
    public void stopPart2() {
        // Folia end - region threading
        this.resources.close();

        try {
            this.storageSource.close();
        } catch (IOException var4) {
            LOGGER.error("Failed to unlock level {}", this.storageSource.getLevelId(), var4);
        }
        // Spigot start
        io.papermc.paper.util.MCUtil.ASYNC_EXECUTOR.shutdown(); // Paper
        try {
            io.papermc.paper.util.MCUtil.ASYNC_EXECUTOR.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS); // Paper
        } catch (java.lang.InterruptedException ignored) {} // Paper
        if (org.spigotmc.SpigotConfig.saveUserCacheOnStopOnly) {
            LOGGER.info("Saving usercache.json");
            this.services().nameToIdCache().save(false); // Paper - Perf: Async GameProfileCache saving
        }
        // Spigot end
        // Paper start - rewrite chunk system
        LOGGER.info("Waiting for all RegionFile I/O tasks to complete...");
        ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.flush((MinecraftServer)(Object)this);
        LOGGER.info("All RegionFile I/O tasks to complete");
        if ((Object)this instanceof net.minecraft.server.dedicated.DedicatedServer) {
            ca.spottedleaf.moonrise.common.util.MoonriseCommon.haltExecutors();
        }
        // Paper end - rewrite chunk system
        // Paper start - Improved watchdog support - move final shutdown items here
        Util.shutdownExecutors();
        this.onServerExit();
        // Paper end - Improved watchdog support - move final shutdown items here
    }

    public String getLocalIp() {
        return this.localIp;
    }

    public void setLocalIp(String localIp) {
        this.localIp = localIp;
    }

    public boolean isRunning() {
        return this.running;
    }

    public void halt(boolean waitForShutdown) {
        // Paper start - allow passing of the intent to restart
        this.safeShutdown(waitForShutdown, false);
    }
    public void safeShutdown(boolean waitForShutdown, boolean isRestarting) {
        this.isRestarting = isRestarting;
        this.hasLoggedStop = true; // Paper - Debugging
        if (isDebugging()) io.papermc.paper.util.TraceUtil.dumpTraceForThread("Server stopped"); // Paper - Debugging
        // Paper end
        this.running = false;
        this.stopServer(); // Folia - region threading
        if (waitForShutdown) {
            try {
                this.serverThread.join();
            } catch (InterruptedException var3) {
                LOGGER.error("Error while shutting down", (Throwable)var3);
            }
        }
    }

    // Paper start - improve tick loop
    private void initTickSchedule() {
        final long interval;
        if (this.isPaused() || !this.tickRateManager.isSprinting()) {
            interval = this.tickRateManager.nanosecondsPerTick();
        } else {
            interval = 0L;
        }
        this.tickSchedule.setNextPeriod(this.nextTickTimeNanos, interval);
        this.lastTickStart = ca.spottedleaf.concurrentutil.util.TimeUtil.DEADLINE_NOT_SET;
        this.scheduledTickStart = this.tickSchedule.getDeadline(interval);
    }

    private void recordEndOfTick() {
        final long prevStart = this.lastTickStart;
        final long currStart = this.currentTickStart;
        this.lastTickStart = this.currentTickStart;
        final long scheduledStart = this.scheduledTickStart;
        this.scheduledTickStart = this.nextTickTimeNanos; // set scheduledStart for next tick

        final long now = Util.getNanos();

        final ca.spottedleaf.moonrise.common.time.TickTime time = new ca.spottedleaf.moonrise.common.time.TickTime(
            prevStart,
            scheduledStart,
            currStart,
            0L,
            now,
            0L,
            this.taskExecutionTime,
            0L,
            false
        );
        this.taskExecutionTime = 0L;

        this.addTickTime(time);
    }

    private void runAllTasksAtTickStart() {
        this.startMeasuringTaskExecutionTime();

        // note: To avoid possibly spinning forever, only execute tasks that are roughly available at the beginning
        //       of this call. Packet processing and chunk system tasks are possibly always being queued.
        final ProfilerFiller profiler = Profiler.get();
        profiler.push("moonrise:run_all_tasks");

        profiler.push("moonrise:run_all_server");
        // avoid calling MinecraftServer#pollTask - we just want to execute queued tasks
        while (super.pollTask()) {
            // execute small amounts of other tasks just in case the number of tasks we are
            // draining is large - chunk system and packet processing may be latency sensitive

            ((ca.spottedleaf.moonrise.patches.chunk_system.server.ChunkSystemMinecraftServer)this).moonrise$executeMidTickTasks(); // Paper - rewrite chunk system
            this.packetProcessor.executeSinglePacket();
        }
        profiler.popPush("moonrise:run_all_packets");
        while (this.packetProcessor.executeSinglePacket()) {
            // execute possibly latency sensitive chunk system tasks (see above)
            ((ca.spottedleaf.moonrise.patches.chunk_system.server.ChunkSystemMinecraftServer)this).moonrise$executeMidTickTasks(); // Paper - rewrite chunk system
        }
        profiler.popPush("moonrise:run_all_chunk");
        // Paper start - rewrite chunk system
        for (final ServerLevel world : this.getAllLevels()) {
            profiler.push(world.toString() + " " + world.dimension().identifier()); // keep same formatting from regular tick, see tickChildren

            // note: legacy tasks may expect a distance manager update
            profiler.push("moonrise:distance_manager_update");
            ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)world).moonrise$getChunkTaskScheduler().chunkHolderManager.processTicketUpdates();
            profiler.popPush("moonrise:legacy_chunk_tasks");
            world.getChunkSource().mainThreadProcessor.executeAllRecentInternalTasks();
            profiler.popPush("moonrise:chunk_system_tasks");
            ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)world).moonrise$getChunkTaskScheduler().executeAllRecentlyQueuedMainThreadTasks();
            profiler.pop();

            profiler.pop(); // world name
        }
        // Paper end - rewrite chunk system
        profiler.pop(); // moonrise:run_all_chunk
        profiler.pop(); // moonrise:run_all_tasks

        this.finishMeasuringTaskExecutionTime();
    }

    private void recordTaskExecutionTimeWhileWaiting() {
        final ProfilerFiller profiler = Profiler.get();

        profiler.push("moonrise:execute_tasks_until_tick");
        this.waitingForNextTick = true;
        // implement waitForTasks
        final boolean isLoggingEnabled = this.isTickTimeLoggingEnabled();
        try {
            final long deadline = this.nextTickTimeNanos;
            for (;;) {
                final long start = Util.getNanos();
                if (start - deadline >= 0L) {
                    // start is ahead of deadline
                    break;
                }

                // execute tasks while there are tasks and there is time left
                // note: we do not need to bypass the task execution check here (like managedBlock) since it checks time
                while (this.pollTask() && (Util.getNanos() - deadline < 0L));

                final long now = Util.getNanos();

                // record execution time
                this.taskExecutionTime += (now - start);

                // wait for unpark or deadline
                final long toWait = deadline - now;
                if (toWait > 0L) {
                    LockSupport.parkNanos("waiting for tick or tasks", toWait);
                    if (isLoggingEnabled) {
                        this.idleTimeNanos += Util.getNanos() - now;
                    }
                } else {
                    // done
                    break;
                }
            }
        } finally {
            this.waitingForNextTick = false;
        }
        profiler.pop();
    }
    // Paper end - improve tick loop

    protected void runServer() {
        try {
            if (!this.initServer()) {
                throw new IllegalStateException("Failed to initialize server");
            }

            this.nextTickTimeNanos = Util.getNanos();
            this.initTickSchedule(); // Paper - improve tick loop
            this.statusIcon = this.loadStatusIcon().orElse(null);
            this.status = this.buildServerStatus();

            if (false) this.server.spark.enableBeforePlugins(); // Paper - spark // Luminol - Force disable builtin spark
            // Folia start - region threading
            if (true) {
                io.papermc.paper.threadedregions.RegionizedServer.getInstance().init(); // Folia - region threading - only after loading worlds
                final long actualDoneTimeMs = System.currentTimeMillis() - org.bukkit.craftbukkit.Main.BOOT_TIME.toEpochMilli(); // Paper - Improve startup message
                LOGGER.info("Done ({})! For help, type \"help\"", String.format(java.util.Locale.ROOT, "%.3fs", actualDoneTimeMs / 1000.00D)); // Paper - Improve startup message
                for (;;) {
                    try {
                        Thread.sleep(Long.MAX_VALUE);
                    } catch (final InterruptedException ex) {}
                }
            }
            // Folia end - region threading
            // Spigot start
            // Paper start
            LOGGER.info("Running delayed init tasks");
            new io.papermc.paper.threadedregions.RegionizedServerInitEvent().callEvent(); // Call Folia init event
            this.server.getScheduler().mainThreadHeartbeat(); // run all 1 tick delay tasks during init,
            // this is going to be the first thing the tick process does anyway, so move done and run it after
            // everything is init before watchdog tick.
            // anything at 3+ won't be caught here but also will trip watchdog....
            // tasks are default scheduled at -1 + delay, and first tick will tick at 1
            final long actualDoneTimeMs = System.currentTimeMillis() - org.bukkit.craftbukkit.Main.BOOT_TIME.toEpochMilli(); // Paper - Improve startup message
            LOGGER.info("Done ({})! For help, type \"help\"", String.format(java.util.Locale.ROOT, "%.3fs", actualDoneTimeMs / 1000.00D)); // Paper - Improve startup message
            org.spigotmc.WatchdogThread.tick();
            // Paper end
            org.spigotmc.WatchdogThread.hasStarted = true; // Paper
            // Paper start - Add onboarding message for initial server start
            if (io.papermc.paper.configuration.GlobalConfiguration.isFirstStart) {
                LOGGER.info("*************************************************************************************");
                LOGGER.info("This is the first time you're starting this server.");
                LOGGER.info("It's recommended you read our 'Getting Started' documentation for guidance.");
                LOGGER.info("View this and more helpful information here: https://docs.papermc.io/paper/next-steps");
                LOGGER.info("*************************************************************************************");
            }
            // Paper end - Add onboarding message for initial server start
            // Paper start - Improve outdated version checking
            if (System.getProperty("paper.disableStartupVersionCheck") == null && io.papermc.paper.configuration.GlobalConfiguration.get().updateChecker.enabled) {
                CompletableFuture.runAsync(com.destroystokyo.paper.PaperVersionFetcher::getUpdateStatusStartupMessage, io.papermc.paper.util.MCUtil.ASYNC_EXECUTOR);
            }
            // Paper end - Improve outdated version checking

            while (this.running) {
                final long tickStart = System.nanoTime(); // Paper - improve tick loop
                long l; // Paper - improve tick loop - diff on change, expect this to be tick interval
                if (!this.isPaused() && this.tickRateManager.isSprinting() && this.tickRateManager.checkShouldSprintThisTick()) {
                    l = 0L;
                    this.tickSchedule.setNextPeriod(tickStart, l); // Paper - improve tick loop
                } else {
                    l = this.tickRateManager.nanosecondsPerTick();
                    // Paper start - improve tick loop
                    // handle catchup logic
                    final long ticksBehind = Math.max(1L, this.tickSchedule.getPeriodsAhead(l, tickStart));
                    final long catchup = (long)Math.max(
                        1,
                        5 //ConfigHolder.getConfig().tickLoop.catchupTicks.getOrDefault(MoonriseConfig.TickLoop.DEFAULT_CATCHUP_TICKS).intValue()
                    );

                    // adjust ticksBehind so that it is not greater-than catchup
                    if (ticksBehind > catchup) {
                        final long difference = ticksBehind - catchup;
                        this.tickSchedule.advanceBy(difference, l);
                    }

                    // start next tick
                    this.tickSchedule.advanceBy(1L, l);
                    // Paper end - improve tick loop
                }

                this.nextTickTimeNanos = this.tickSchedule.getDeadline(l);
                this.lastOverloadWarningNanos = this.nextTickTimeNanos;

                this.currentTickStart = tickStart;
                //++MinecraftServer.currentTick; // Folia - region threading
                // Paper end - improve tick loop

                boolean flag = l == 0L;
                if (this.debugCommandProfilerDelayStart) {
                    this.debugCommandProfilerDelayStart = false;
                    //this.debugCommandProfiler = new MinecraftServer.TimeProfiler(Util.getNanos(), this.tickCount); // Folia - region threading
                }

                // Paper - improve tick loop - done above

                try (Profiler.Scope scope = Profiler.use(this.createProfiler())) {
                    this.processPacketsAndTick(flag);
                    ProfilerFiller profilerFiller = Profiler.get();
                    profilerFiller.push("nextTickWait");
                    this.mayHaveDelayedTasks = true;
                    this.delayedTasksMaxNextTickTimeNanos = Math.max(Util.getNanos() + l, this.nextTickTimeNanos);
                    this.startMeasuringTaskExecutionTime();
                    this.recordTaskExecutionTimeWhileWaiting(); // Paper - improve tick loop - record task execution here on MSPT
                    this.finishMeasuringTaskExecutionTime();
                    if (flag) {
                        this.tickRateManager.endTickWork();
                    }

                    profilerFiller.pop();
                    this.logFullTickTime();
                } finally {
                    this.endMetricsRecordingTick();
                }

                this.isReady = true;
                JvmProfiler.INSTANCE.onServerTick(this.smoothedTickTimeMillis);
            }
        } catch (Throwable var69) {
            LOGGER.error("Encountered an unexpected exception", var69);
            CrashReport crashReport = constructOrExtractCrashReport(var69);
            this.fillSystemReport(crashReport.getSystemReport());
            Path path = this.getServerDirectory().resolve("crash-reports").resolve("crash-" + Util.getFilenameFormattedDateTime() + "-server.txt");
            if (crashReport.saveToFile(path, ReportType.CRASH)) {
                LOGGER.error("This crash report has been saved to: {}", path.toAbsolutePath());
            } else {
                LOGGER.error("We were unable to save this crash report to disk.");
            }

            this.onServerCrash(crashReport);
        } finally {
            try {
                this.stopped = true;
                this.stopServer();
            } catch (Throwable var64) {
                LOGGER.error("Exception stopping the server", var64);
            } finally {
                //this.onServerExit(); // Paper - Improved watchdog support; moved into stop
            }
        }
    }

    private void logFullTickTime() {
        long nanos = Util.getNanos();
        if (this.isTickTimeLoggingEnabled()) {
            this.getTickTimeLogger().logSample(nanos - this.lastTickNanos);
        }

        this.lastTickNanos = nanos;
    }

    private void startMeasuringTaskExecutionTime() {
        if (this.isTickTimeLoggingEnabled()) {
            this.taskExecutionStartNanos = Util.getNanos();
            this.idleTimeNanos = 0L;
        }
    }

    private void finishMeasuringTaskExecutionTime() {
        if (this.isTickTimeLoggingEnabled()) {
            SampleLogger tickTimeLogger = this.getTickTimeLogger();
            tickTimeLogger.logPartialSample(Util.getNanos() - this.taskExecutionStartNanos - this.idleTimeNanos, TpsDebugDimensions.SCHEDULED_TASKS.ordinal());
            tickTimeLogger.logPartialSample(this.idleTimeNanos, TpsDebugDimensions.IDLE.ordinal());
        }
    }

    private static CrashReport constructOrExtractCrashReport(Throwable cause) {
        ReportedException reportedException = null;

        for (Throwable throwable = cause; throwable != null; throwable = throwable.getCause()) {
            if (throwable instanceof ReportedException reportedException1) {
                reportedException = reportedException1;
            }
        }

        CrashReport report;
        if (reportedException != null) {
            report = reportedException.getReport();
            if (reportedException != cause) {
                report.addCategory("Wrapped in").setDetailError("Wrapping exception", cause);
            }
        } else {
            report = new CrashReport("Exception in server tick loop", cause);
        }

        return report;
    }

    private boolean haveTime() {
        // CraftBukkit start
        return this.forceTicks || this.runningTask() || Util.getNanos() < (this.mayHaveDelayedTasks ? this.delayedTasksMaxNextTickTimeNanos : this.nextTickTimeNanos);
    }

    private void executeModerately() {
        this.runAllTasks();
        java.util.concurrent.locks.LockSupport.parkNanos("executing tasks", 1000L);
        // CraftBukkit end
    }

    public static boolean throwIfFatalException() {
        RuntimeException runtimeException = fatalException.get();
        if (runtimeException != null) {
            throw runtimeException;
        } else {
            return true;
        }
    }

    public static void setFatalException(RuntimeException fatalException) {
        MinecraftServer.fatalException.compareAndSet(null, fatalException);
    }

    @Override
    public void managedBlock(BooleanSupplier isDone) {
        super.managedBlock(() -> throwIfFatalException() && isDone.getAsBoolean());
    }

    public NotificationManager notificationManager() {
        return this.notificationManager;
    }

    protected void waitUntilNextTick() {
        // Paper - improve tick loop - moved to start of tick
        this.waitingForNextTick = true;

        try {
            this.managedBlock(() -> Util.getNanos() - this.nextTickTimeNanos >= 0L); // Paper - improve tick loop - do not oversleep
        } finally {
            this.waitingForNextTick = false;
        }
    }

    @Override
    public void waitForTasks() {
        boolean isTickTimeLoggingEnabled = this.isTickTimeLoggingEnabled();
        long l = isTickTimeLoggingEnabled ? Util.getNanos() : 0L;
        long l1 = this.waitingForNextTick ? this.nextTickTimeNanos - Util.getNanos() : 100000L;
        LockSupport.parkNanos("waiting for tasks", l1);
        if (isTickTimeLoggingEnabled) {
            this.idleTimeNanos = this.idleTimeNanos + (Util.getNanos() - l);
        }
    }

    @Override
    public TickTask wrapRunnable(Runnable runnable) {
        throw new UnsupportedOperationException(); // Folia - region threading
    }

    @Override
    protected boolean shouldRun(TickTask runnable) {
        throw new UnsupportedOperationException(); // Folia - region threading
    }

    @Override
    public boolean pollTask() {
        if (true) throw new UnsupportedOperationException(); // Folia - region threading
        boolean flag = this.packetProcessor.executeSinglePacket() | this.pollTaskInternal(); // Paper - improve tick loop - process packets while waiting inbetween ticks
        this.mayHaveDelayedTasks = flag;
        return flag;
    }

    private boolean pollTaskInternal() {
        if (true) throw new UnsupportedOperationException(); // Folia - region threading
        if (super.pollTask()) {
            this.moonrise$executeMidTickTasks(); // Paper - rewrite chunk system
            return true;
        } else {
            boolean ret = false; // Paper - force execution of all worlds, do not just bias the first
            if (this.tickRateManager.isSprinting() || this.shouldRunAllTasks() || this.haveTime()) {
                for (ServerLevel serverLevel : this.getAllLevels()) {
                    if (serverLevel.getChunkSource().pollTask()) {
                        ret = true; // Paper - force execution of all worlds, do not just bias the first
                    }
                }
            }

            return ret; // Paper - force execution of all worlds, do not just bias the first
        }
    }

    @Override
    public void doRunTask(TickTask task) {
        if (true) throw new UnsupportedOperationException(); // Folia - region threading
        Profiler.get().incrementCounter("runTask");
        super.doRunTask(task);
    }

    private Optional<ServerStatus.Favicon> loadStatusIcon() {
        Optional<Path> optional = Optional.of(this.getFile("server-icon.png"))
            .filter(path -> Files.isRegularFile(path))
            .or(() -> this.storageSource.getIconFile().filter(path -> Files.isRegularFile(path)));
        return optional.flatMap(path -> {
            try {
                byte[] allBytes = Files.readAllBytes(path);
                PngInfo pngInfo = PngInfo.fromBytes(allBytes);
                if (pngInfo.width() == 64 && pngInfo.height() == 64) {
                    return Optional.of(new ServerStatus.Favicon(allBytes));
                } else {
                    throw new IllegalArgumentException("Invalid world icon size [" + pngInfo.width() + ", " + pngInfo.height() + "], but expected [64, 64]");
                }
            } catch (Exception var3) {
                LOGGER.error("Couldn't load server icon", (Throwable)var3);
                return Optional.empty();
            }
        });
    }

    public Optional<Path> getWorldScreenshotFile() {
        return this.storageSource.getIconFile();
    }

    public Path getServerDirectory() {
        return Path.of("");
    }

    public ServerActivityMonitor getServerActivityMonitor() {
        return this.serverActivityMonitor;
    }

    public void onServerCrash(CrashReport report) {
    }

    public void onServerExit() {
    }

    public boolean isPaused() {
        return false;
    }

    // Folia start - region threading
    public void tickServer(long startTime, long scheduledEnd, long targetBuffer,
                           io.papermc.paper.threadedregions.TickRegions.TickRegionData region) {
        final ca.spottedleaf.leafprofiler.RegionizedProfiler.Handle foliaProfiler = io.papermc.paper.threadedregions.TickRegionScheduler.getProfiler(); // Folia - profiler
        // Folia end - region threading
        org.spigotmc.WatchdogThread.tick(); // Spigot
        long nanos = startTime; // Folia - region threading
        int i = this.pauseWhenEmptySeconds() * 20;
        this.removeDisabledPluginsBlockingSleep(); // Paper - API to allow/disallow tick sleeping
        if (false && i > 0) { // Folia - region threading - this is complicated to implement, and even if done correctly is messy
            if (this.playerList.getPlayerCount() == 0 && !this.tickRateManager.isSprinting() && this.pluginsBlockingSleep.isEmpty()) { // Paper - API to allow/disallow tick sleeping
                this.emptyTicks++;
            } else {
                this.emptyTicks = 0;
            }

            if (this.emptyTicks >= i) {
                if (false) this.server.spark.tickStart(); // Paper - spark // Luminol - Force disable builtin spark
                if (this.emptyTicks == i) {
                    LOGGER.info("Server empty for {} seconds, pausing", this.pauseWhenEmptySeconds());
                    this.autoSave();
                }

                this.server.getScheduler().mainThreadHeartbeat(); // CraftBukkit
                // Paper start - avoid issues with certain tasks not processing during sleep
                Runnable task;
                while ((task = this.processQueue.poll()) != null) {
                    task.run();
                }
                for (final ServerLevel level : this.levels.values()) {
                    // process unloads
                    level.getChunkSource().tick(() -> true, false);
                }
                // Paper end - avoid issues with certain tasks not processing during sleep
                //this.server.spark.executeMainThreadTasks(); // Paper - spark // Folia - region threading
                this.tickConnection();
                if (false) this.server.spark.tickEnd(((double)(System.nanoTime() - this.currentTickStart) / 1000000D)); // Paper - spark // Luminol - Force disable builtin spark
                return;
            }
        }


        // Folia start - region threading
        region.world.getCurrentWorldData().updateTickData();
        BooleanSupplier hasTimeLeft = () -> {
            return scheduledEnd - System.nanoTime() > targetBuffer;
        };
        // Folia end - region threading

        if (false) this.server.spark.tickStart(); // Paper - spark // Luminol - Force disable builtin spark
        new com.destroystokyo.paper.event.server.ServerTickStartEvent((int)region.getCurrentTick()).callEvent(); // Paper - Server Tick Events // Folia - region threading
        // Folia start - region threading
        if (region != null) {
            foliaProfiler.startTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.INTERNAL_TICK_TASKS); try { // Folia - profiler
            region.getTaskQueueData().drainTasks();
            } finally { foliaProfiler.stopTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.INTERNAL_TICK_TASKS); } // Folia - profiler
            foliaProfiler.startTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.PLAYER_PACKET_PROCESSING); try { // Folia - profiler
            // run all player packets
            for (ServerPlayer player : new java.util.ArrayList<>(region.world.getCurrentWorldData().getLocalPlayers())) {
                PacketProcessor packetProcessor = ((org.bukkit.craftbukkit.entity.CraftPlayer)player.getBukkitEntity()).packetProcessor;
                while (ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(player) && packetProcessor.executeSinglePacket());
            }
            } finally { foliaProfiler.stopTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.PLAYER_PACKET_PROCESSING); } // Folia - profiler
            foliaProfiler.startTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.PLUGIN_TICK_TASKS); try { // Folia - profiler
            ((io.papermc.paper.threadedregions.scheduler.FoliaRegionScheduler)org.bukkit.Bukkit.getRegionScheduler()).tick();
            } finally { foliaProfiler.stopTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.PLUGIN_TICK_TASKS); } // Folia - profiler
            // now run all the entity schedulers
            long tickedEntitySchedulers = 0L; // Folia - profiler
            foliaProfiler.startTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.ENTITY_SCHEDULER_TICK); try { // Folia - profiler
            for (io.papermc.paper.threadedregions.EntityScheduler scheduler : region.world.getCurrentWorldData().entitySchedulerTickList.getAllSchedulers()) {
                net.minecraft.world.entity.Entity handle = scheduler.entity.getHandleRaw();
                if (!ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(handle) || scheduler.isRetired()) {
                    continue;
                }

                ++tickedEntitySchedulers; // Folia - profiler
                scheduler.executeTick();
            }
            foliaProfiler.addCounter(ca.spottedleaf.leafprofiler.LProfilerRegistry.ENTITY_SCHEDULERS_TICKED, tickedEntitySchedulers); // Folia - profiler
            } finally { foliaProfiler.stopTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.ENTITY_SCHEDULER_TICK); } // Folia - profiler
        }
        // Folia end - region threading
        //this.tickCount++; // Folia - region threading
        //this.tickRateManager.tick(); // Folia - region threading
        // KioCG start - ChunkHot
        final ca.spottedleaf.moonrise.common.list.IteratorSafeOrderedReferenceSet<net.minecraft.world.level.chunk.LevelChunk> chunks = new ca.spottedleaf.moonrise.common.list.IteratorSafeOrderedReferenceSet<>();
        if (region != null){
            for (net.minecraft.world.level.chunk.LevelChunk chunk : region.world.getCurrentWorldData().getTickingChunks()) {
            /* wait for rewrite - temporarily crash fix
            for (net.minecraft.server.level.ServerChunkCache.ChunkAndHolder chunkAndHolder : region.world.getCurrentWorldData().getTickingChunks()){
                final net.minecraft.world.level.chunk.LevelChunk chunk = chunkAndHolder.chunk();
            */
                if (!ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(region.world, chunk.locX, chunk.locZ)){
                    continue;
                }

                chunks.add(chunk);
            }
        }
        if (region != null && io.papermc.paper.threadedregions.RegionizedServer.getCurrentTick() % 20 == 0){
            final java.util.Iterator<net.minecraft.world.level.chunk.LevelChunk> chunkIterator = chunks.unsafeIterator();
            while (chunkIterator.hasNext()){
                final net.minecraft.world.level.chunk.LevelChunk targetChunk = chunkIterator.next();

                targetChunk.getChunkHot().nextTick();
                targetChunk.getChunkHot().start();
            }
        }
        //KioCG end
        this.tickChildren(hasTimeLeft, region); // Folia - region threading
        // KioCG start - ChunkHot
        if (region != null && io.papermc.paper.threadedregions.RegionizedServer.getCurrentTick() % 20 == 0){
            final java.util.Iterator<net.minecraft.world.level.chunk.LevelChunk> chunkIterator = chunks.unsafeIterator();
            while (chunkIterator.hasNext()){
                final net.minecraft.world.level.chunk.LevelChunk targetChunk = chunkIterator.next();

                if (!targetChunk.getChunkHot().isStarted()){
                    continue;
                }

                targetChunk.getChunkHot().stop();
            }
        }
        //KioCG end
        if (false && nanos - this.lastServerStatus >= STATUS_EXPIRE_TIME_NANOS) { // Folia - region threading
            this.lastServerStatus = nanos;
            this.status = this.buildServerStatus();
        }

        //this.ticksUntilAutosave--; // Folia - region threading
        // Paper start - Incremental chunk and player saving
        final ProfilerFiller profiler = Profiler.get();
        int playerSaveInterval = io.papermc.paper.configuration.GlobalConfiguration.get().playerAutoSave.rate;
        if (playerSaveInterval < 0) {
            playerSaveInterval = autosavePeriod;
        }
        profiler.push("save");
        final boolean fullSave = autosavePeriod > 0 && io.papermc.paper.threadedregions.RegionizedServer.getCurrentTick() % autosavePeriod == 0; // Folia - region threading
        foliaProfiler.startTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.AUTOSAVE); try { // Folia - profiler
        try {
            this.isSaving = true;
            if (playerSaveInterval > 0) {
                this.playerList.saveAll(playerSaveInterval);
            }
            for (final ServerLevel level : (region == null ? this.getAllLevels() : Arrays.asList(region.world))) { // Folia - region threading
                if (level.paperConfig().chunks.autoSaveInterval.value() > 0) {
                    level.saveIncrementally(region == null && fullSave); // Folia - region threading - don't save level.dat
                }
            }
        } finally {
            this.isSaving = false;
        }
        } finally { foliaProfiler.stopTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.AUTOSAVE); } // Folia - profiler
        profiler.pop();
        // Paper end - Incremental chunk and player saving

        ProfilerFiller profilerFiller = Profiler.get();
        //this.server.spark.executeMainThreadTasks(); // Paper - spark // Folia - region threading
        // Paper start - Server Tick Events
        long endTime = System.nanoTime();
        long remaining = scheduledEnd - endTime; // Folia - region ticking
        new com.destroystokyo.paper.event.server.ServerTickEndEvent((int)io.papermc.paper.threadedregions.RegionizedServer.getCurrentTick(), ((double)(endTime - startTime) / 1000000D), remaining).callEvent(); // Folia - region ticking
        // Paper end - Server Tick Events
        if (false) this.server.spark.tickEnd(((double)(endTime - startTime) / 1000000D)); // Paper - spark // Folia - region threading // Luminol - Force disable builtin spark
        // Folia - region threading
    }

    protected void processPacketsAndTick(boolean sprinting) {
        ProfilerFiller profilerFiller = Profiler.get();
        profilerFiller.push("tick");
        this.tickFrame.start();
        // Paper - improve tick loop - moved into runAllTasksAtTickStart
        this.runAllTasksAtTickStart(); // Paper - improve tick loop
        if (true) throw new UnsupportedOperationException(); // Folia - region threading
        // Paper start - rewrite chunk system
        final Throwable crash = this.chunkSystemCrash;
        if (crash != null) {
            this.chunkSystemCrash = null;
            throw new RuntimeException("Chunk system crash propagated to tick()", crash);
        }
        // Paper end - rewrite chunk system
        this.tickFrame.end();
        this.recordEndOfTick(); // Paper - improve tick loop
        profilerFiller.pop();
    }

    private void autoSave() {
        //this.ticksUntilAutosave = this.autosavePeriod; // CraftBukkit // Folia - region threading
        LOGGER.debug("Autosave started");
        ProfilerFiller profilerFiller = Profiler.get();
        profilerFiller.push("save");
        this.saveEverything(true, false, false);
        profilerFiller.pop();
        LOGGER.debug("Autosave finished");
    }

    private void logTickMethodTime(long startTime) {
        if (this.isTickTimeLoggingEnabled()) {
            this.getTickTimeLogger().logPartialSample(Util.getNanos() - startTime, TpsDebugDimensions.TICK_SERVER_METHOD.ordinal());
        }
    }

    // Folia - region threading - use absolute time instead of this

    public void onTickRateChanged() {
        // Folia - region threading - use absolute time instead of this
    }

    protected abstract SampleLogger getTickTimeLogger();

    public abstract boolean isTickTimeLoggingEnabled();

    // Folia start - region threading
    public void rebuildServerStatus() {
        this.status = this.buildServerStatus();
    }
    // Folia end - region threading

    private ServerStatus buildServerStatus() {
        ServerStatus.Players players = this.buildPlayerStatus();
        return new ServerStatus(
            io.papermc.paper.adventure.PaperAdventure.asVanilla(this.motd), // Paper - Adventure
            Optional.of(players),
            Optional.of(ServerStatus.Version.current()),
            Optional.ofNullable(this.statusIcon),
            this.enforceSecureProfile()
        );
    }

    private ServerStatus.Players buildPlayerStatus() {
        List<ServerPlayer> players = new java.util.ArrayList<>(this.playerList.getPlayers()); // Folia - region threading
        int maxPlayers = this.getMaxPlayers();
        if (this.hidesOnlinePlayers()) {
            return new ServerStatus.Players(maxPlayers, players.size(), List.of());
        } else {
            int min = Math.min(players.size(), org.spigotmc.SpigotConfig.playerSample); // Paper - PaperServerListPingEvent
            ObjectArrayList<NameAndId> list = new ObjectArrayList<>(min);
            int randomInt = Mth.nextInt(this.random, 0, players.size() - min);

            for (int i = 0; i < min; i++) {
                ServerPlayer serverPlayer = players.get(randomInt + i);
                list.add(serverPlayer.allowsListing() ? serverPlayer.nameAndId() : ANONYMOUS_PLAYER_PROFILE);
            }

            Util.shuffle(list, this.random);
            return new ServerStatus.Players(maxPlayers, players.size(), list);
        }
    }

    // Folia - region threading - moved to regionised data

    protected void tickChildren(BooleanSupplier hasTimeLeft, io.papermc.paper.threadedregions.TickRegions.TickRegionData region) { // Folia - region threading
        final ca.spottedleaf.leafprofiler.RegionizedProfiler.Handle profiler = io.papermc.paper.threadedregions.TickRegionScheduler.getProfiler(); // Folia - profiler
        final io.papermc.paper.threadedregions.RegionizedWorldData regionizedWorldData = io.papermc.paper.threadedregions.TickRegionScheduler.getCurrentRegionizedWorldData(); // Folia - regionised ticking
        ProfilerFiller profilerFiller = Profiler.get();
        //this.getPlayerList().getPlayers().forEach(serverPlayer1 -> serverPlayer1.connection.suspendFlushing()); // Folia - region threading
        //this.server.getScheduler().mainThreadHeartbeat(); // CraftBukkit // Folia - region threading
        // Folia - region threading - moved to global tick - and moved entity scheduler to tickRegion
        //io.papermc.paper.adventure.providers.ClickCallbackProviderImpl.ADVENTURE_CLICK_MANAGER.handleQueue(this.tickCount); // Paper // Folia - region threading - moved to global tick
        //io.papermc.paper.adventure.providers.ClickCallbackProviderImpl.DIALOG_CLICK_MANAGER.handleQueue(this.tickCount); // Paper // Folia - region threading - moved to global tick
        profilerFiller.push("commandFunctions");
        //this.getFunctions().tick(); // Folia - region threading - TODO Purge functions
        profilerFiller.popPush("levels");
        this.updateEffectiveRespawnData();

        // CraftBukkit start
        // Run tasks that are waiting on processing
        if (false) while (!this.processQueue.isEmpty()) { // Folia - region threading
            this.processQueue.remove().run();
        }

        // Send time updates to everyone, it will get the right time from the world the player is in.
        // Paper start - Perf: Optimize time updates
        if (true) { ServerLevel level = region.world; // Folia - region threading
            final boolean doDaylight = level.getGameRules().get(GameRules.ADVANCE_TIME);
            final long dayTime = level.getDayTime();
            long worldTime = level.getGameTime();
            final ClientboundSetTimePacket worldPacket = new ClientboundSetTimePacket(worldTime, dayTime, doDaylight);
            for (Player entityhuman : level.getLocalPlayers()) { // Folia - region threading
                if (!(entityhuman instanceof ServerPlayer) || (io.papermc.paper.threadedregions.RegionizedServer.getCurrentTick() + entityhuman.getId()) % 20 != 0) { // Folia - region threading
                    continue;
                }
                ServerPlayer entityplayer = (ServerPlayer) entityhuman;
                long playerTime = entityplayer.getPlayerTime();
                boolean relativeTime = entityplayer.relativeTime;
                ClientboundSetTimePacket packet = ((relativeTime || !doDaylight) && playerTime == dayTime) ? worldPacket :
                    new ClientboundSetTimePacket(worldTime, playerTime, relativeTime && doDaylight);
                entityplayer.connection.send(packet); // Add support for per player time
                // Paper end - Perf: Optimize time updates
            }
        }

        //this.isIteratingOverLevels = true; // Paper - Throw exception on world create while being ticked // Folia - region threading
        if (true) { ServerLevel serverLevel = region.world; // Folia - region threading
            // Folia - region threading
            profilerFiller.push(() -> serverLevel + " " + serverLevel.dimension().identifier());
            /* Drop global time updates
            if (this.tickCount % 20 == 0) {
                profilerFiller.push("timeSync");
                this.synchronizeTime(serverLevel);
                profilerFiller.pop();
            }
            // CraftBukkit end */

            profilerFiller.push("tick");
            // Luminol start - Portal rate limiter
            regionizedWorldData.portalRateThrottler.begin();
            // Luminol end

            try {
                profiler.startTimer(serverLevel.tickTimerId); try { // Folia - profiler
                serverLevel.tick(hasTimeLeft, region); // Folia - region threading
                } finally { profiler.stopTimer(serverLevel.tickTimerId); } // Folia - profiler
            } catch (Throwable var7) {
                CrashReport crashReport = CrashReport.forThrowable(var7, "Exception ticking world");
                serverLevel.fillReportDetails(crashReport);
                throw new ReportedException(crashReport);
            }

            profilerFiller.pop();
            profilerFiller.pop();
            regionizedWorldData.explosionDensityCache.clear(); // Paper - Optimize explosions // Folia - region threading
            // Luminol start - Portal rate limiter
            regionizedWorldData.portalRateThrottler.done();
            // Luminol end
        }
        //this.isIteratingOverLevels = false; // Paper - Throw exception on world create while being ticked // Folia - region threading

        profilerFiller.popPush("connection");
        profiler.startTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.CONNECTION_TICK); try { // Folia - profiler
        regionizedWorldData.tickConnections(); // Folia - region threading
        } finally { profiler.stopTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.CONNECTION_TICK); } // Folia - profiler
        profilerFiller.popPush("players");
        //this.playerList.tick(); // Folia - region threading
        profilerFiller.popPush("debugSubscribers");
        this.debugSubscribers.tick();
        if (this.tickRateManager.runsNormally()) {
            profilerFiller.popPush("gameTests");
            GameTestTicker.SINGLETON.tick();
        }

        profilerFiller.popPush("server gui refresh");

        if (false) for (Runnable runnable : this.tickables) { // Folia - region threading - TODO WTF is this?
            runnable.run();
        }

        profilerFiller.popPush("send chunks");

        if (false) for (ServerPlayer serverPlayer : this.playerList.getPlayers()) { // Folia - region threading
            serverPlayer.connection.chunkSender.sendNextChunks(serverPlayer);
            serverPlayer.connection.resumeFlushing();
        }

        profilerFiller.pop();
        this.serverActivityMonitor.tick();
    }

    // Paper start - per world respawn data - read "server global" respawn data from overworld dimension reference
    public void updateEffectiveRespawnData() {
        ServerLevel serverLevel = this.findRespawnDimension();
        LevelData.RespawnData respawnData = serverLevel.serverLevelData.getRespawnData();
        respawnData = respawnData.withLevel(serverLevel.dimension());
    // Paper end - per world respawn data - read "server global" respawn data from overworld dimension reference
        this.effectiveRespawnData = serverLevel.getWorldBorderAdjustedRespawnData(respawnData);
    }

    public void tickConnection() {
        this.getConnection().tick();
    }

    private void synchronizeTime(ServerLevel level) {
        this.playerList
            .broadcastAll(
                new ClientboundSetTimePacket(level.getGameTime(), level.getDayTime(), level.getGameRules().get(GameRules.ADVANCE_TIME)), level.dimension()
            );
    }

    public void forceTimeSynchronization() {
        ProfilerFiller profilerFiller = Profiler.get();
        profilerFiller.push("timeSync");

        for (ServerLevel serverLevel : this.getAllLevels()) {
            this.synchronizeTime(serverLevel);
        }

        profilerFiller.pop();
    }

    public void addTickable(Runnable tickable) {
        this.tickables.add(tickable);
    }

    protected void setId(String serverId) {
        this.serverId = serverId;
    }

    public boolean isShutdown() {
        return !this.serverThread.isAlive();
    }

    public Path getFile(String path) {
        return this.getServerDirectory().resolve(path);
    }

    public final ServerLevel overworld() {
        return this.levels.get(Level.OVERWORLD);
    }

    public @Nullable ServerLevel getLevel(ResourceKey<Level> dimension) {
        return this.levels.get(dimension);
    }

    // CraftBukkit start
    public void addLevel(ServerLevel level) {
        Map<ResourceKey<Level>, ServerLevel> oldLevels = this.levels;
        Map<ResourceKey<Level>, ServerLevel> newLevels = Maps.newLinkedHashMap(oldLevels);
        newLevels.put(level.dimension(), level);
        this.levels = Collections.unmodifiableMap(newLevels);
    }

    public void removeLevel(ServerLevel level) {
        Map<ResourceKey<Level>, ServerLevel> oldLevels = this.levels;
        Map<ResourceKey<Level>, ServerLevel> newLevels = Maps.newLinkedHashMap(oldLevels);
        newLevels.remove(level.dimension());
        this.levels = Collections.unmodifiableMap(newLevels);
    }
    // CraftBukkit end

    public Set<ResourceKey<Level>> levelKeys() {
        return this.levels.keySet();
    }

    public Iterable<ServerLevel> getAllLevels() {
        return this.levels.values();
    }

    @Override
    public String getServerVersion() {
        return SharedConstants.getCurrentVersion().name();
    }

    @Override
    public int getPlayerCount() {
        return this.playerList.getPlayerCount();
    }

    public String[] getPlayerNames() {
        return this.playerList.getPlayerNamesArray();
    }

    @DontObfuscate
    public String getServerModName() {
        return me.earthme.luminol.config.modules.misc.ServerModNameConfig.fakeVanilla ? "vanilla" : me.earthme.luminol.config.modules.misc.ServerModNameConfig.serverModName; // Paper // Luminol - Add config for this
    }

    public SystemReport fillSystemReport(SystemReport systemReport) {
        systemReport.setDetail("Server Running", () -> Boolean.toString(this.running));
        if (this.playerList != null) {
            systemReport.setDetail(
                "Player Count", () -> this.playerList.getPlayerCount() + " / " + this.playerList.getMaxPlayers() + "; " + this.playerList.getPlayers()
            );
        }

        systemReport.setDetail("Active Data Packs", () -> PackRepository.displayPackList(this.packRepository.getSelectedPacks()));
        systemReport.setDetail("Available Data Packs", () -> PackRepository.displayPackList(this.packRepository.getAvailablePacks()));
        systemReport.setDetail(
            "Enabled Feature Flags",
            () -> FeatureFlags.REGISTRY.toNames(this.worldData.enabledFeatures()).stream().map(Identifier::toString).collect(Collectors.joining(", "))
        );
        systemReport.setDetail("World Generation", () -> this.worldData.worldGenSettingsLifecycle().toString());
        systemReport.setDetail("World Seed", () -> String.valueOf(this.worldData.worldGenOptions().seed()));
        systemReport.setDetail("Suppressed Exceptions", this.suppressedExceptions::dump);
        if (this.serverId != null) {
            systemReport.setDetail("Server Id", () -> this.serverId);
        }

        return this.fillServerSystemReport(systemReport);
    }

    public abstract SystemReport fillServerSystemReport(SystemReport report);

    public ModCheck getModdedStatus() {
        return ModCheck.identify("vanilla", this::getServerModName, "Server", MinecraftServer.class);
    }

    @Override
    public void sendSystemMessage(Component message) {
        LOGGER.info(io.papermc.paper.adventure.PaperAdventure.ANSI_SERIALIZER.serialize(io.papermc.paper.adventure.PaperAdventure.asAdventure(message))); // Paper - Log message with colors
    }

    public KeyPair getKeyPair() {
        return Objects.requireNonNull(this.keyPair);
    }

    public int getPort() {
        return this.port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public @Nullable GameProfile getSingleplayerProfile() {
        return this.singleplayerProfile;
    }

    public void setSingleplayerProfile(@Nullable GameProfile singleplayerProfile) {
        this.singleplayerProfile = singleplayerProfile;
    }

    public boolean isSingleplayer() {
        return this.singleplayerProfile != null;
    }

    protected void initializeKeyPair() {
        LOGGER.info("Generating keypair");

        try {
            this.keyPair = Crypt.generateKeyPair();
        } catch (CryptException var2) {
            throw new IllegalStateException("Failed to generate key pair", var2);
        }
    }

    // Paper start - per level difficulty, WorldDifficultyChangeEvent
    public void setDifficulty(ServerLevel level, Difficulty difficulty, @Nullable CommandSourceStack source, boolean forced) {
        net.minecraft.world.level.storage.PrimaryLevelData worldData = level.serverLevelData;
        if (forced || !worldData.isDifficultyLocked()) {
            new io.papermc.paper.event.world.WorldDifficultyChangeEvent(
                level.getWorld(), source, org.bukkit.craftbukkit.util.CraftDifficulty.toBukkit(difficulty)
            ).callEvent();
            worldData.setDifficulty(worldData.isHardcore() ? Difficulty.HARD : difficulty);
            level.setSpawnSettings(level.isSpawningMonsters());
            // this.getPlayerList().getPlayers().forEach(this::sendDifficultyUpdate);
            // Paper end - per level difficulty
        }
    }

    public int getScaledTrackingDistance(int trackingDistance) {
        return trackingDistance;
    }

    public void updateMobSpawningFlags() {
        for (ServerLevel serverLevel : this.getAllLevels()) {
            serverLevel.setSpawnSettings(serverLevel.isSpawningMonsters());
        }
    }

    public void setDifficultyLocked(boolean locked) {
        this.worldData.setDifficultyLocked(locked);
        this.getPlayerList().getPlayers().forEach(this::sendDifficultyUpdate);
    }

    private void sendDifficultyUpdate(ServerPlayer player) {
        LevelData levelData = player.level().getLevelData();
        player.connection.send(new ClientboundChangeDifficultyPacket(levelData.getDifficulty(), levelData.isDifficultyLocked()));
    }

    public boolean isDemo() {
        return this.isDemo;
    }

    public void setDemo(boolean demo) {
        this.isDemo = demo;
    }

    public Map<String, String> getCodeOfConducts() {
        return Map.of();
    }

    public Optional<MinecraftServer.ServerResourcePackInfo> getServerResourcePack() {
        return Optional.empty();
    }

    public boolean isResourcePackRequired() {
        return this.getServerResourcePack().filter(MinecraftServer.ServerResourcePackInfo::isRequired).isPresent();
    }

    public abstract boolean isDedicatedServer();

    public abstract int getRateLimitPacketsPerSecond();

    public boolean usesAuthentication() {
        return this.onlineMode;
    }

    public void setUsesAuthentication(boolean online) {
        this.onlineMode = online;
    }

    public boolean getPreventProxyConnections() {
        return this.preventProxyConnections;
    }

    public void setPreventProxyConnections(boolean preventProxyConnections) {
        this.preventProxyConnections = preventProxyConnections;
    }

    public abstract boolean useNativeTransport();

    public boolean allowFlight() {
        return true;
    }

    @Override
    public String getMotd() {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(this.motd); // Paper - Adventure
    }

    public void setMotd(String motd) {
        // Paper start - Adventure
        this.motd = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserializeOr(motd, net.kyori.adventure.text.Component.empty());
    }

    public net.kyori.adventure.text.Component motd() {
        return this.motd;
    }

    public void motd(net.kyori.adventure.text.Component motd) {
        // Paper end - Adventure
        this.motd = motd;
    }

    public boolean isStopped() {
        return this.stopped;
    }

    public PlayerList getPlayerList() {
        return this.playerList;
    }

    public void setPlayerList(PlayerList list) {
        this.playerList = list;
    }

    public abstract boolean isPublished();

    public void setDefaultGameType(GameType gameMode) {
        this.worldData.setGameType(gameMode);
    }

    public int enforceGameTypeForPlayers(@Nullable GameType gameMode) {
        if (gameMode == null) {
            return 0;
        } else {
            int i = 0;

            for (ServerPlayer serverPlayer : this.getPlayerList().getPlayers()) {
                serverPlayer.getBukkitEntity().taskScheduler.schedule((ServerPlayer player) -> { // Folia - region threading
                // Paper start - Expand PlayerGameModeChangeEvent
                org.bukkit.event.player.PlayerGameModeChangeEvent event = player.setGameMode(gameMode, org.bukkit.event.player.PlayerGameModeChangeEvent.Cause.DEFAULT_GAMEMODE, null); // Folia - region threading
                if (event == null || event.isCancelled()) {
                    return; // Folia - region threading
                }
                }, null, 1L); // Folia - region threading
                i++;
                // Paper end - Expand PlayerGameModeChangeEvent
            }

            return i;
        }
    }

    public ServerConnectionListener getConnection() {
        return this.connection == null ? this.connection = new ServerConnectionListener(this) : this.connection; // Spigot
    }

    public boolean isReady() {
        return this.isReady;
    }

    public boolean publishServer(@Nullable GameType gameMode, boolean commands, int port) {
        return false;
    }

    public int getTickCount() {
        throw new UnsupportedOperationException(); // Folia - region threading
    }

    public boolean isUnderSpawnProtection(ServerLevel level, BlockPos pos, Player player) {
        return false;
    }

    public boolean repliesToStatus() {
        return true;
    }

    public boolean hidesOnlinePlayers() {
        return false;
    }

    public Proxy getProxy() {
        return this.proxy;
    }

    public int playerIdleTimeout() {
        return this.playerIdleTimeout;
    }

    public void setPlayerIdleTimeout(int idleTimeout) {
        this.playerIdleTimeout = idleTimeout;
    }

    public Services services() {
        return this.services;
    }

    public @Nullable ServerStatus getStatus() {
        return this.status;
    }

    public void invalidateStatus() {
        // Folia start - region threading
        if (true) {
            // we don't need this to notify the global tick region
            io.papermc.paper.threadedregions.RegionizedServer.getInstance().addTaskWithoutNotify(() -> {
                io.papermc.paper.threadedregions.RegionizedServer.getInstance().invalidateStatus();
            });
            return;
        }
        // Folia end - region threading
        this.lastServerStatus = 0L;
    }

    public int getAbsoluteMaxWorldSize() {
        return 29999984;
    }

    @Override
    public boolean scheduleExecutables() {
        return super.scheduleExecutables() && !this.isStopped();
    }

    @Override
    public void executeIfPossible(Runnable task) {
        if (true) throw new UnsupportedOperationException(); // Folia - region threading
        if (this.isStopped()) {
            throw new io.papermc.paper.util.ServerStopRejectedExecutionException("Server already shutting down"); // Paper - do not prematurely disconnect players on stop
        } else {
            super.executeIfPossible(task);
        }
    }

    @Override
    public Thread getRunningThread() {
        return this.serverThread;
    }

    public int getCompressionThreshold() {
        return 256;
    }

    public boolean enforceSecureProfile() {
        return false;
    }

    public long getNextTickTime() {
        return this.nextTickTimeNanos;
    }

    public DataFixer getFixerUpper() {
        return this.fixerUpper;
    }

    public ServerAdvancementManager getAdvancements() {
        return this.resources.managers.getAdvancements();
    }

    public ServerFunctionManager getFunctions() {
        return this.functionManager;
    }

    // Paper start - Add ServerResourcesReloadedEvent
    @Deprecated @io.papermc.paper.annotation.DoNotUse
    public CompletableFuture<Void> reloadResources(Collection<String> selectedIds) {
        return this.reloadResources(selectedIds, io.papermc.paper.event.server.ServerResourcesReloadedEvent.Cause.PLUGIN);
    }

    public CompletableFuture<Void> reloadResources(Collection<String> selectedIds, io.papermc.paper.event.server.ServerResourcesReloadedEvent.Cause cause) {
        // Paper end - Add ServerResourcesReloadedEvent
        CompletableFuture<Void> completableFuture = CompletableFuture.<ImmutableList>supplyAsync(
                () -> selectedIds.stream().map(this.packRepository::getPack).filter(Objects::nonNull).map(Pack::open).collect(ImmutableList.toImmutableList()),
                this
            )
            .thenCompose(
                list -> {
                    CloseableResourceManager closeableResourceManager = new MultiPackResourceManager(PackType.SERVER_DATA, list);
                    List<Registry.PendingTags<?>> list1 = TagLoader.loadTagsForExistingRegistries(closeableResourceManager, this.registries.compositeAccess(), io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent.Cause.RELOAD); // Paper - tag lifecycle - add cause
                    return ReloadableServerResources.loadResources(
                            closeableResourceManager,
                            this.registries,
                            list1,
                            this.worldData.enabledFeatures(),
                            this.isDedicatedServer() ? Commands.CommandSelection.DEDICATED : Commands.CommandSelection.INTEGRATED,
                            this.getFunctionCompilationPermissions(),
                            this.executor,
                            this
                        )
                        .whenComplete((reloadableServerResources, throwable) -> {
                            if (throwable != null) {
                                closeableResourceManager.close();
                            }
                        })
                        .thenApply(reloadableServerResources -> new MinecraftServer.ReloadableResources(closeableResourceManager, reloadableServerResources));
                }
            )
            .thenAcceptAsync(
                reloadableResources -> {
                    io.papermc.paper.command.brigadier.PaperBrigadier.moveBukkitCommands(this.resources.managers().getCommands(), reloadableResources.managers().commands); // Paper
                    this.resources.close();
                    this.resources = reloadableResources;
                    this.packRepository.setSelected(selectedIds, false); // Paper - add pendingReload flag to determine required pack loading - false as this is *after* a reload (see above)
                    WorldDataConfiguration worldDataConfiguration = new WorldDataConfiguration(
                        getSelectedPacks(this.packRepository, true), this.worldData.enabledFeatures()
                    );
                    this.worldData.setDataConfiguration(worldDataConfiguration);
                    this.resources.managers.updateStaticRegistryTags();
                    this.resources.managers.getRecipeManager().finalizeRecipeLoading(this.worldData.enabledFeatures());
                    this.potionBrewing = this.potionBrewing.reload(this.worldData.enabledFeatures()); // Paper - Custom Potion Mixes
                    if (Thread.currentThread() != this.serverThread) return; // Paper
                    // Paper start - we don't need to save everything, just advancements
                    // this.getPlayerList().saveAll();
                    for (final ServerPlayer player : this.getPlayerList().getPlayers()) {
                        player.getAdvancements().save();
                    }
                    // Paper end - we don't need to save everything, just advancements
                    this.getPlayerList().reloadResources();
                    this.functionManager.replaceLibrary(this.resources.managers.getFunctionLibrary());
                    this.structureTemplateManager.onResourceManagerReload(this.resources.resourceManager);
                    this.fuelValues = FuelValues.vanillaBurnTimes(this.registries.compositeAccess(), this.worldData.enabledFeatures());
                    org.bukkit.craftbukkit.block.data.CraftBlockData.reloadCache(); // Paper - cache block data strings; they can be defined by datapacks so refresh it here
                    // Paper start - brigadier command API
                    io.papermc.paper.command.brigadier.PaperCommands.INSTANCE.setValid(); // reset invalid state for event fire below
                    io.papermc.paper.plugin.lifecycle.event.LifecycleEventRunner.INSTANCE.callReloadableRegistrarEvent(io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents.COMMANDS, io.papermc.paper.command.brigadier.PaperCommands.INSTANCE, org.bukkit.plugin.Plugin.class, io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent.Cause.RELOAD); // call commands event for regular plugins
                    final org.bukkit.craftbukkit.help.SimpleHelpMap helpMap = (org.bukkit.craftbukkit.help.SimpleHelpMap) this.server.getHelpMap();
                    helpMap.clear();
                    helpMap.initializeGeneralTopics();
                    helpMap.initializeCommands();
                    this.server.syncCommands(); // Refresh commands after event
                    // Paper end
                    new io.papermc.paper.event.server.ServerResourcesReloadedEvent(cause).callEvent(); // Paper - Add ServerResourcesReloadedEvent; fire after everything has been reloaded
                },
                this
            );
        if (this.isSameThread()) {
            this.managedBlock(completableFuture::isDone);
        }

        return completableFuture;
    }

    public static WorldDataConfiguration configurePackRepository(
        PackRepository packRepository, WorldDataConfiguration initialDataConfig, boolean initMode, boolean safeMode
    ) {
        DataPackConfig dataPackConfig = initialDataConfig.dataPacks();
        FeatureFlagSet featureFlagSet = initMode ? FeatureFlagSet.of() : initialDataConfig.enabledFeatures();
        FeatureFlagSet featureFlagSet1 = initMode ? FeatureFlags.REGISTRY.allFlags() : initialDataConfig.enabledFeatures();
        packRepository.reload(true); // Paper - will load resource packs
        if (safeMode) {
            return configureRepositoryWithSelection(packRepository, List.of("vanilla"), featureFlagSet, false);
        } else {
            Set<String> set = Sets.newLinkedHashSet();

            for (String string : dataPackConfig.getEnabled()) {
                if (packRepository.isAvailable(string)) {
                    set.add(string);
                } else {
                    LOGGER.warn("Missing data pack {}", string);
                }
            }

            for (Pack pack : packRepository.getAvailablePacks()) {
                String id = pack.getId();
                if (!dataPackConfig.getDisabled().contains(id)) {
                    FeatureFlagSet requestedFeatures = pack.getRequestedFeatures();
                    boolean flag = set.contains(id);
                    if (!flag && pack.getPackSource().shouldAddAutomatically()) {
                        if (requestedFeatures.isSubsetOf(featureFlagSet1)) {
                            LOGGER.info("Found new data pack {}, loading it automatically", id);
                            set.add(id);
                        } else {
                            LOGGER.info(
                                "Found new data pack {}, but can't load it due to missing features {}",
                                id,
                                FeatureFlags.printMissingFlags(featureFlagSet1, requestedFeatures)
                            );
                        }
                    }

                    if (flag && !requestedFeatures.isSubsetOf(featureFlagSet1)) {
                        LOGGER.warn(
                            "Pack {} requires features {} that are not enabled for this world, disabling pack.",
                            id,
                            FeatureFlags.printMissingFlags(featureFlagSet1, requestedFeatures)
                        );
                        set.remove(id);
                    }
                }
            }

            if (set.isEmpty()) {
                LOGGER.info("No datapacks selected, forcing vanilla");
                set.add("vanilla");
            }

            return configureRepositoryWithSelection(packRepository, set, featureFlagSet, true);
        }
    }

    private static WorldDataConfiguration configureRepositoryWithSelection(
        PackRepository packRepository, Collection<String> selectedPacks, FeatureFlagSet enabledFeatures, boolean safeMode
    ) {
        packRepository.setSelected(selectedPacks, true); // Paper - add pendingReload flag to determine required pack loading - before the initial server load
        enableForcedFeaturePacks(packRepository, enabledFeatures);
        DataPackConfig selectedPacks1 = getSelectedPacks(packRepository, safeMode);
        FeatureFlagSet featureFlagSet = packRepository.getRequestedFeatureFlags().join(enabledFeatures);
        return new WorldDataConfiguration(selectedPacks1, featureFlagSet);
    }

    private static void enableForcedFeaturePacks(PackRepository packRepository, FeatureFlagSet enabledFeatures) {
        FeatureFlagSet requestedFeatureFlags = packRepository.getRequestedFeatureFlags();
        FeatureFlagSet featureFlagSet = enabledFeatures.subtract(requestedFeatureFlags);
        if (!featureFlagSet.isEmpty()) {
            Set<String> set = new ObjectArraySet<>(packRepository.getSelectedIds());

            for (Pack pack : packRepository.getAvailablePacks()) {
                if (featureFlagSet.isEmpty()) {
                    break;
                }

                if (pack.getPackSource() == PackSource.FEATURE) {
                    String id = pack.getId();
                    FeatureFlagSet requestedFeatures = pack.getRequestedFeatures();
                    if (!requestedFeatures.isEmpty() && requestedFeatures.intersects(featureFlagSet) && requestedFeatures.isSubsetOf(enabledFeatures)) {
                        if (!set.add(id)) {
                            throw new IllegalStateException("Tried to force '" + id + "', but it was already enabled");
                        }

                        LOGGER.info("Found feature pack ('{}') for requested feature, forcing to enabled", id);
                        featureFlagSet = featureFlagSet.subtract(requestedFeatures);
                    }
                }
            }

            packRepository.setSelected(set, true); // Paper - add pendingReload flag to determine required pack loading - before the initial server start
        }
    }

    private static DataPackConfig getSelectedPacks(PackRepository packRepository, boolean safeMode) {
        Collection<String> selectedIds = packRepository.getSelectedIds();
        List<String> list = ImmutableList.copyOf(selectedIds);
        List<String> list1 = safeMode ? packRepository.getAvailableIds().stream().filter(packId -> !selectedIds.contains(packId)).toList() : List.of();
        return new DataPackConfig(list, list1);
    }

    public void kickUnlistedPlayers() {
        if (this.isEnforceWhitelist() && this.isUsingWhitelist()) {
            PlayerList playerList = this.getPlayerList();
            UserWhiteList whiteList = playerList.getWhiteList();

            for (ServerPlayer serverPlayer : Lists.newArrayList(playerList.getPlayers())) {
                if (!whiteList.isWhiteListed(serverPlayer.nameAndId()) && !this.getPlayerList().isOp(serverPlayer.nameAndId())) { // Paper - Fix kicking ops when whitelist is reloaded (MC-171420)
                    serverPlayer.connection.disconnect(net.kyori.adventure.text.Component.text(org.spigotmc.SpigotConfig.whitelistMessage), org.bukkit.event.player.PlayerKickEvent.Cause.WHITELIST); // Paper - use configurable message & kick event cause
                }
            }
        }
    }

    public PackRepository getPackRepository() {
        return this.packRepository;
    }

    public Commands getCommands() {
        return this.resources.managers.getCommands();
    }

    public CommandSourceStack createCommandSourceStack() {
        ServerLevel serverLevel = this.findRespawnDimension();
        return new CommandSourceStack(
            this,
            Vec3.atLowerCornerOf(this.getRespawnData().pos()),
            Vec2.ZERO,
            serverLevel,
            LevelBasedPermissionSet.OWNER,
            "Server",
            Component.literal("Server"),
            this,
            null
        );
    }

    public ServerLevel findRespawnDimension() {
        ResourceKey<Level> resourceKey = ((net.minecraft.world.level.storage.PrimaryLevelData) this.getWorldData().overworldData()).respawnDimension; // Paper - per world respawn data - read "server global" respawn data from overworld dimension reference
        ServerLevel level = this.getLevel(resourceKey);
        return level != null ? level : this.overworld();
    }

    @io.papermc.paper.annotation.DoNotUse @Deprecated(forRemoval = true) // Paper - per world respawn data - set through Level
    public void setRespawnData(LevelData.RespawnData respawnData) {
        ServerLevelData serverLevelData = this.worldData.overworldData();
        LevelData.RespawnData respawnData1 = serverLevelData.getRespawnData();
        if (!respawnData1.equals(respawnData)) {
            serverLevelData.setSpawn(respawnData);
            this.getPlayerList().broadcastAll(new ClientboundSetDefaultSpawnPositionPacket(respawnData));
            this.updateEffectiveRespawnData();
        }
    }

    public LevelData.RespawnData getRespawnData() {
        return this.effectiveRespawnData;
    }

    @Override
    public boolean acceptsSuccess() {
        return true;
    }

    @Override
    public boolean acceptsFailure() {
        return true;
    }

    @Override
    public abstract boolean shouldInformAdmins();

    public RecipeManager getRecipeManager() {
        return this.resources.managers.getRecipeManager();
    }

    public ServerScoreboard getScoreboard() {
        return this.scoreboard;
    }

    public CommandStorage getCommandStorage() {
        if (this.commandStorage == null) {
            throw new NullPointerException("Called before server init");
        } else {
            return this.commandStorage;
        }
    }

    public Stopwatches getStopwatches() {
        if (this.stopwatches == null) {
            throw new NullPointerException("Called before server init");
        } else {
            return this.stopwatches;
        }
    }

    public CustomBossEvents getCustomBossEvents() {
        return this.customBossEvents;
    }

    public boolean isEnforceWhitelist() {
        return this.enforceWhitelist;
    }

    public void setEnforceWhitelist(boolean enforceWhitelist) {
        this.enforceWhitelist = enforceWhitelist;
    }

    public boolean isUsingWhitelist() {
        return this.usingWhitelist;
    }

    public void setUsingWhitelist(boolean usingWhitelist) {
        this.usingWhitelist = usingWhitelist;
    }

    public float getCurrentSmoothedTickTime() {
        return this.smoothedTickTimeMillis;
    }

    public ServerTickRateManager tickRateManager() {
        return this.tickRateManager;
    }

    public long getAverageTickTimeNanos() {
        // Folia start - region threading
        if (io.papermc.paper.threadedregions.TickRegionScheduler.getCurrentTickingTask() instanceof io.papermc.paper.threadedregions.TickRegionScheduler.RegionScheduleHandle handle) {
            return (long)Math.ceil(handle.getTickReport5s(System.nanoTime()).timePerTickData().segmentAll().average());
        }
        return 0L;
        // Folia end - region threading
    }

    public long[] getTickTimesNanos() {
        return this.tickTimesNanos;
    }

    public LevelBasedPermissionSet getProfilePermissions(NameAndId nameAndId) {
        if (this.getPlayerList().isOp(nameAndId)) {
            ServerOpListEntry serverOpListEntry = this.getPlayerList().getOps().get(nameAndId);
            if (serverOpListEntry != null) {
                return serverOpListEntry.permissions();
            } else if (this.isSingleplayerOwner(nameAndId)) {
                return LevelBasedPermissionSet.OWNER;
            } else if (this.isSingleplayer()) {
                return this.getPlayerList().isAllowCommandsForAllPlayers() ? LevelBasedPermissionSet.OWNER : LevelBasedPermissionSet.ALL;
            } else {
                return this.operatorUserPermissions();
            }
        } else {
            return LevelBasedPermissionSet.ALL;
        }
    }

    public abstract boolean isSingleplayerOwner(NameAndId nameAndId);

    public void dumpServerProperties(Path path) throws IOException {
    }

    private void saveDebugReport(Path path) {
        Path path1 = path.resolve("levels");

        try {
            for (Entry<ResourceKey<Level>, ServerLevel> entry : this.levels.entrySet()) {
                Identifier identifier = entry.getKey().identifier();
                Path path2 = path1.resolve(identifier.getNamespace()).resolve(identifier.getPath());
                Files.createDirectories(path2);
                entry.getValue().saveDebugReport(path2);
            }

            this.dumpGameRules(path.resolve("gamerules.txt"));
            this.dumpClasspath(path.resolve("classpath.txt"));
            this.dumpMiscStats(path.resolve("stats.txt"));
            this.dumpThreads(path.resolve("threads.txt"));
            this.dumpServerProperties(path.resolve("server.properties.txt"));
            this.dumpNativeModules(path.resolve("modules.txt"));
        } catch (IOException var7) {
            LOGGER.warn("Failed to save debug report", (Throwable)var7);
        }
    }

    private void dumpMiscStats(Path path) throws IOException {
        try (Writer bufferedWriter = Files.newBufferedWriter(path)) {
            bufferedWriter.write(String.format(Locale.ROOT, "pending_tasks: %d\n", this.getPendingTasksCount()));
            bufferedWriter.write(String.format(Locale.ROOT, "average_tick_time: %f\n", this.getCurrentSmoothedTickTime()));
            bufferedWriter.write(String.format(Locale.ROOT, "tick_times: %s\n", Arrays.toString(this.tickTimesNanos)));
            bufferedWriter.write(String.format(Locale.ROOT, "queue: %s\n", Util.backgroundExecutor()));
        }
    }

    private void dumpGameRules(Path path) throws IOException {
        try (Writer bufferedWriter = Files.newBufferedWriter(path)) {
            final List<String> list = Lists.newArrayList();
            final GameRules gameRules = this.worldData.getGameRules();
            gameRules.visitGameRuleTypes(new GameRuleTypeVisitor() {
                @Override
                public <T> void visit(GameRule<T> rule) {
                    list.add(String.format(Locale.ROOT, "%s=%s\n", rule.getIdentifier(), gameRules.getAsString(rule)));
                }
            });

            for (String string : list) {
                bufferedWriter.write(string);
            }
        }
    }

    private void dumpClasspath(Path path) throws IOException {
        try (Writer bufferedWriter = Files.newBufferedWriter(path)) {
            String property = System.getProperty("java.class.path");
            String string = File.pathSeparator;

            for (String string1 : Splitter.on(string).split(property)) {
                bufferedWriter.write(string1);
                bufferedWriter.write("\n");
            }
        }
    }

    private void dumpThreads(Path path) throws IOException {
        ThreadMXBean threadMxBean = ManagementFactory.getThreadMXBean();
        ThreadInfo[] threadInfos = threadMxBean.dumpAllThreads(true, true);
        Arrays.sort(threadInfos, Comparator.comparing(ThreadInfo::getThreadName));

        try (Writer bufferedWriter = Files.newBufferedWriter(path)) {
            for (ThreadInfo threadInfo : threadInfos) {
                bufferedWriter.write(threadInfo.toString());
                bufferedWriter.write(10);
            }
        }
    }

    private void dumpNativeModules(Path path) throws IOException {
        try (Writer bufferedWriter = Files.newBufferedWriter(path)) {
            List<NativeModuleLister.NativeModuleInfo> list;
            try {
                list = Lists.newArrayList(NativeModuleLister.listModules());
            } catch (Throwable var7) {
                LOGGER.warn("Failed to list native modules", var7);
                return;
            }

            list.sort(Comparator.comparing(nativeModuleInfo1 -> nativeModuleInfo1.name));

            for (NativeModuleLister.NativeModuleInfo nativeModuleInfo : list) {
                bufferedWriter.write(nativeModuleInfo.toString());
                bufferedWriter.write(10);
            }
        }
    }

    // Paper start - rewrite chunk system
    @Override
    public boolean isSameThread() {
        return ca.spottedleaf.moonrise.common.util.TickThread.isTickThread();
    }
    // Paper end - rewrite chunk system

    // CraftBukkit start
    public boolean isDebugging() {
        return false;
    }

    public static MinecraftServer getServer() {
        return SERVER;
    }
    // CraftBukkit end

    private ProfilerFiller createProfiler() {
        if (this.willStartRecordingMetrics) {
            this.metricsRecorder = ActiveMetricsRecorder.createStarted(
                new ServerMetricsSamplersProvider(Util.timeSource, this.isDedicatedServer()),
                Util.timeSource,
                Util.ioPool(),
                new MetricsPersister("server"),
                this.onMetricsRecordingStopped,
                path -> {
                    this.executeBlocking(() -> this.saveDebugReport(path.resolve("server")));
                    this.onMetricsRecordingFinished.accept(path);
                }
            );
            this.willStartRecordingMetrics = false;
        }

        this.metricsRecorder.startTick();
        return SingleTickProfiler.decorateFiller(this.metricsRecorder.getProfiler(), SingleTickProfiler.createTickProfiler("Server"));
    }

    public void endMetricsRecordingTick() {
        this.metricsRecorder.endTick();
    }

    public boolean isRecordingMetrics() {
        return this.metricsRecorder.isRecording();
    }

    public void startRecordingMetrics(Consumer<ProfileResults> output, Consumer<Path> onMetricsRecordingFinished) {
        this.onMetricsRecordingStopped = profileResults -> {
            this.stopRecordingMetrics();
            output.accept(profileResults);
        };
        this.onMetricsRecordingFinished = onMetricsRecordingFinished;
        this.willStartRecordingMetrics = true;
    }

    public void stopRecordingMetrics() {
        this.metricsRecorder = InactiveMetricsRecorder.INSTANCE;
    }

    public void finishRecordingMetrics() {
        this.metricsRecorder.end();
    }

    public void cancelRecordingMetrics() {
        this.metricsRecorder.cancel();
    }

    public Path getWorldPath(LevelResource levelResource) {
        return this.storageSource.getLevelPath(levelResource);
    }

    public boolean forceSynchronousWrites() {
        return true;
    }

    public StructureTemplateManager getStructureManager() {
        return this.structureTemplateManager;
    }

    public WorldData getWorldData() {
        return this.worldData;
    }

    public RegistryAccess.Frozen registryAccess() {
        return this.registries.compositeAccess();
    }

    public LayeredRegistryAccess<RegistryLayer> registries() {
        return this.registries;
    }

    public ReloadableServerRegistries.Holder reloadableRegistries() {
        return this.resources.managers.fullRegistries();
    }

    public TextFilter createTextFilterForPlayer(ServerPlayer player) {
        return TextFilter.DUMMY;
    }

    public ServerPlayerGameMode createGameModeForPlayer(ServerPlayer player) {
        return (ServerPlayerGameMode)(this.isDemo() ? new DemoMode(player) : new ServerPlayerGameMode(player));
    }

    public @Nullable GameType getForcedGameType() {
        return null;
    }

    public ResourceManager getResourceManager() {
        return this.resources.resourceManager;
    }

    public boolean isCurrentlySaving() {
        return this.isSaving;
    }

    public boolean isTimeProfilerRunning() {
        return this.debugCommandProfilerDelayStart || this.debugCommandProfiler != null;
    }

    public void startTimeProfiler() {
        this.debugCommandProfilerDelayStart = true;
    }

    public ProfileResults stopTimeProfiler() {
        throw new UnsupportedOperationException(); // Folia - region threading
    }

    public int getMaxChainedNeighborUpdates() {
        return 1000000;
    }

    public void logChatMessage(Component content, ChatType.Bound boundChatType, @Nullable String header) {
        // Paper start
        net.kyori.adventure.text.Component string = io.papermc.paper.adventure.PaperAdventure.asAdventure(boundChatType.decorate(content));
        if (header != null) {
            COMPONENT_LOGGER.info("[{}] {}", header, string);
        } else {
            COMPONENT_LOGGER.info("{}", string);
            // Paper end
        }
    }

    public final java.util.concurrent.ExecutorService chatExecutor = java.util.concurrent.Executors.newCachedThreadPool(
        new com.google.common.util.concurrent.ThreadFactoryBuilder().setDaemon(true).setNameFormat("Async Chat Thread - #%d").setUncaughtExceptionHandler(new net.minecraft.DefaultUncaughtExceptionHandlerWithName(net.minecraft.server.MinecraftServer.LOGGER)).build()); // Paper
    public final ChatDecorator improvedChatDecorator = new io.papermc.paper.adventure.ImprovedChatDecorator(this); // Paper - adventure

    public ChatDecorator getChatDecorator() {
        return this.improvedChatDecorator; // Paper - support async chat decoration events
    }

    public boolean logIPs() {
        return true;
    }

    public void handleCustomClickAction(Identifier id, Optional<Tag> payload) {
        LOGGER.debug("Received custom click action {} with payload {}", id, payload.orElse(null));
    }

    @io.papermc.paper.annotation.DoNotUse @Deprecated(forRemoval = true) // Paper - per level load listener
    public LevelLoadListener getLevelLoadListener() {
        throw new UnsupportedOperationException(); // Paper - per level load listener
    }

    public boolean setAutoSave(boolean autoSave) {
        boolean flag = false;

        for (ServerLevel serverLevel : this.getAllLevels()) {
            if (serverLevel != null && serverLevel.noSave == autoSave) {
                serverLevel.noSave = !autoSave;
                flag = true;
            }
        }

        return flag;
    }

    public boolean isAutoSave() {
        for (ServerLevel serverLevel : this.getAllLevels()) {
            if (serverLevel != null && !serverLevel.noSave) {
                return true;
            }
        }

        return false;
    }

    // Paper start - per-world game rules
    public <T> void onGameRuleChanged(ServerLevel serverLevel, GameRule<T> rule, T value) {
        this.notificationManager().onGameRuleChanged(serverLevel, rule, value);
        // Paper end - per-world game rules
        if (rule == GameRules.REDUCED_DEBUG_INFO) {
            byte b = (byte)((Boolean)value ? 22 : 23);

            for (ServerPlayer serverPlayer : serverLevel.players()) { // Paper - per-world game rules
                serverPlayer.connection.send(new ClientboundEntityEventPacket(serverPlayer, b));
            }
        } else if (rule == GameRules.LIMITED_CRAFTING || rule == GameRules.IMMEDIATE_RESPAWN) {
            ClientboundGameEventPacket.Type type = rule == GameRules.LIMITED_CRAFTING
                ? ClientboundGameEventPacket.LIMITED_CRAFTING
                : ClientboundGameEventPacket.IMMEDIATE_RESPAWN;
            ClientboundGameEventPacket clientboundGameEventPacket = new ClientboundGameEventPacket(type, (Boolean)value ? 1.0F : 0.0F);
            serverLevel.players().forEach(serverPlayer1 -> serverPlayer1.connection.send(clientboundGameEventPacket)); // Paper - per-world game rules
        } else if (rule == GameRules.LOCATOR_BAR) {
            // this.getAllLevels().forEach(serverLevel -> { // Paper - per-world game rules
                me.earthme.luminol.utils.FoliaServerWaypointManager waypointManager = serverLevel.getWaypointManager(); // Luminol - Restore waypoints
                if ((Boolean)value) {
                    serverLevel.players().forEach(waypointManager::updatePlayer);
                } else {
                    waypointManager.breakAllConnections();
                }
            // }); // Paper - per-world game rules
        } else if (rule == GameRules.SPAWN_MONSTERS) {
            serverLevel.setSpawnSettings(serverLevel.isSpawningMonsters()); // Paper - per-world game rules
        }
    }

    public boolean acceptsTransfers() {
        return false;
    }

    private void storeChunkIoError(CrashReport crashReport, ChunkPos chunkPos, RegionStorageInfo regionStorageInfo) {
        Util.ioPool().execute(() -> {
            try {
                Path file = this.getFile("debug");
                FileUtil.createDirectoriesSafe(file);
                String string = FileUtil.sanitizeName(regionStorageInfo.level());
                Path path = file.resolve("chunk-" + string + "-" + Util.getFilenameFormattedDateTime() + "-server.txt");
                FileStore fileStore = Files.getFileStore(file);
                long usableSpace = fileStore.getUsableSpace();
                if (usableSpace < 8192L) {
                    LOGGER.warn("Not storing chunk IO report due to low space on drive {}", fileStore.name());
                    return;
                }

                CrashReportCategory crashReportCategory = crashReport.addCategory("Chunk Info");
                crashReportCategory.setDetail("Level", regionStorageInfo::level);
                crashReportCategory.setDetail("Dimension", () -> regionStorageInfo.dimension().identifier().toString());
                crashReportCategory.setDetail("Storage", regionStorageInfo::type);
                crashReportCategory.setDetail("Position", chunkPos::toString);
                crashReport.saveToFile(path, ReportType.CHUNK_IO_ERROR);
                LOGGER.info("Saved details to {}", crashReport.getSaveFile());
            } catch (Exception var11) {
                LOGGER.warn("Failed to store chunk IO exception", (Throwable)var11);
            }
        });
    }

    @Override
    public void reportChunkLoadFailure(Throwable throwable, RegionStorageInfo regionStorageInfo, ChunkPos chunkPos) {
        LOGGER.error("Failed to load chunk {},{}", chunkPos.x, chunkPos.z, throwable);
        this.suppressedExceptions.addEntry("chunk/load", throwable);
        this.storeChunkIoError(CrashReport.forThrowable(throwable, "Chunk load failure"), chunkPos, regionStorageInfo);
    }

    @Override
    public void reportChunkSaveFailure(Throwable throwable, RegionStorageInfo regionStorageInfo, ChunkPos chunkPos) {
        LOGGER.error("Failed to save chunk {},{}", chunkPos.x, chunkPos.z, throwable);
        this.suppressedExceptions.addEntry("chunk/save", throwable);
        this.storeChunkIoError(CrashReport.forThrowable(throwable, "Chunk save failure"), chunkPos, regionStorageInfo);
    }

    public void reportPacketHandlingException(Throwable throwable, PacketType<?> packetType) {
        this.suppressedExceptions.addEntry("packet/" + packetType, throwable);
    }

    public PotionBrewing potionBrewing() {
        return this.potionBrewing;
    }

    public FuelValues fuelValues() {
        return this.fuelValues;
    }

    public ServerLinks serverLinks() {
        return ServerLinks.EMPTY;
    }

    protected int pauseWhenEmptySeconds() {
        return 0;
    }

    public PacketProcessor packetProcessor() {
        return this.packetProcessor;
    }

    public ServerDebugSubscribers debugSubscribers() {
        return this.debugSubscribers;
    }

    public record ReloadableResources(CloseableResourceManager resourceManager, ReloadableServerResources managers) implements AutoCloseable {
        @Override
        public void close() {
            this.resourceManager.close();
        }
    }

    public record ServerResourcePackInfo(UUID id, String url, String hash, boolean isRequired, @Nullable Component prompt) {
    }

    static class TimeProfiler {
        final long startNanos;
        final int startTick;

        TimeProfiler(long startNanos, int startTick) {
            this.startNanos = startNanos;
            this.startTick = startTick;
        }

        ProfileResults stop(final long endTimeNano, final int endTimeTicks) {
            return new ProfileResults() {
                @Override
                public List<ResultField> getTimes(String sectionPath) {
                    return Collections.emptyList();
                }

                @Override
                public boolean saveResults(Path path) {
                    return false;
                }

                @Override
                public long getStartTimeNano() {
                    return TimeProfiler.this.startNanos;
                }

                @Override
                public int getStartTimeTicks() {
                    return TimeProfiler.this.startTick;
                }

                @Override
                public long getEndTimeNano() {
                    return endTimeNano;
                }

                @Override
                public int getEndTimeTicks() {
                    return endTimeTicks;
                }

                @Override
                public String getProfilerResults() {
                    return "";
                }
            };
        }
    }

    // Paper start - Add tick times API and /mspt command
    public static class TickTimes {
        private final long[] times;

        public TickTimes(int length) {
            times = new long[length];
        }

        void add(int index, long time) {
            times[index % times.length] = time;
        }

        public long[] getTimes() {
            return times.clone();
        }

        public double getAverage() {
            long total = 0L;
            for (long value : times) {
                total += value;
            }
            return ((double) total / (double) times.length) * 1.0E-6D;
        }
    }
    // Paper end - Add tick times API and /mspt command

    // Paper start - API to check if the server is sleeping
    public boolean isTickPaused() {
        return false; // Folia - region threading
    }

    public void addPluginAllowingSleep(final String pluginName, final boolean value) {
        // Folia - region threading
    }

    private void removeDisabledPluginsBlockingSleep() {
        // Folia - region threading
    }
    // Paper end - API to check if the server is sleeping
}
