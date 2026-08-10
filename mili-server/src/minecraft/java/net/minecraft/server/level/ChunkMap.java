package net.minecraft.server.level;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import com.google.common.collect.ImmutableList.Builder;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtException;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.util.CsvOutput;
import net.minecraft.util.Mth;
import net.minecraft.util.StaticCache2D;
import net.minecraft.util.TriState;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.util.thread.ConsecutiveExecutor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.ChunkType;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import net.minecraft.world.level.entity.ChunkStatusUpdateListener;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.LegacyStructureDataHandler;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class ChunkMap extends SimpleRegionStorage implements ChunkHolder.PlayerProvider, GeneratingChunkMap, ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemChunkMap { // Paper - rewrite chunk system
    private static final ChunkResult<List<ChunkAccess>> UNLOADED_CHUNK_LIST_RESULT = ChunkResult.error("Unloaded chunks found in range");
    private static final CompletableFuture<ChunkResult<List<ChunkAccess>>> UNLOADED_CHUNK_LIST_FUTURE = CompletableFuture.completedFuture(
        UNLOADED_CHUNK_LIST_RESULT
    );
    private static final byte CHUNK_TYPE_REPLACEABLE = -1;
    private static final byte CHUNK_TYPE_UNKNOWN = 0;
    private static final byte CHUNK_TYPE_FULL = 1;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CHUNK_SAVED_PER_TICK = 200;
    private static final int CHUNK_SAVED_EAGERLY_PER_TICK = 20;
    private static final int EAGER_CHUNK_SAVE_COOLDOWN_IN_MILLIS = 10000;
    private static final int MAX_ACTIVE_CHUNK_WRITES = 128;
    public static final int MIN_VIEW_DISTANCE = 2;
    public static final int MAX_VIEW_DISTANCE = 32;
    public static final int FORCED_TICKET_LEVEL = ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING);
    // Paper - rewrite chunk system
    public final ServerLevel level;
    private final ThreadedLevelLightEngine lightEngine;
    private final BlockableEventLoop<Runnable> mainThreadExecutor;
    private final RandomState randomState;
    private final ChunkGeneratorStructureState chunkGeneratorState;
    private final TicketStorage ticketStorage;
    private final PoiManager poiManager;
    public final LongSet toDrop = new LongOpenHashSet();
    private boolean modified;
    // Paper - rewrite chunk system
    private final ChunkStatusUpdateListener chunkStatusListener;
    public final ChunkMap.DistanceManager distanceManager;
    private final String storageName;
    //private final PlayerMap playerMap = new PlayerMap(); // Folia - region threading
    //public final Int2ObjectMap<ChunkMap.TrackedEntity> entityMap = new Int2ObjectOpenHashMap<>(); // Folia - region threading
    private final Long2ByteMap chunkTypeCache = new Long2ByteOpenHashMap();
    // Paper - rewrite chunk system
    public int serverViewDistance;
    public final WorldGenContext worldGenContext; // Paper - public

    // CraftBukkit start - recursion-safe executor for Chunk loadCallback() and unloadCallback()
    public final CallbackExecutor callbackExecutor = new CallbackExecutor();
    public static final class CallbackExecutor implements java.util.concurrent.Executor, Runnable {

        private final java.util.Queue<Runnable> queue = new java.util.ArrayDeque<>();

        @Override
        public void execute(Runnable runnable) {
            this.queue.add(runnable);
        }

        @Override
        public void run() {
            Runnable task;
            while ((task = this.queue.poll()) != null) {
                task.run();
            }
        }
    };
    // CraftBukkit end

    // Paper start
    public final ChunkHolder getUnloadingChunkHolder(int chunkX, int chunkZ) {
        return null; // Paper - rewrite chunk system
    }
    // Paper end
    // Paper start - rewrite chunk system
    @Override
    public final void moonrise$writeFinishCallback(final ChunkPos pos) throws IOException {
        // see ChunkStorage#write
        this.markChunkDone(pos);
    }
    // Paper end - rewrite chunk system

    public ChunkMap(
        ServerLevel level,
        LevelStorageSource.LevelStorageAccess levelStorageAccess,
        DataFixer fixerUpper,
        StructureTemplateManager structureManager,
        Executor dispatcher,
        BlockableEventLoop<Runnable> mainThreadExecutor,
        LightChunkGetter lightChunk,
        ChunkGenerator generator,
        ChunkStatusUpdateListener chunkStatusListener,
        Supplier<DimensionDataStorage> overworldDataStorage,
        TicketStorage ticketStorage,
        int serverViewDistance,
        boolean sync
    ) {
        super(
            new RegionStorageInfo(levelStorageAccess.getLevelId(), level.dimension(), "chunk"),
            levelStorageAccess.getDimensionPath(level.dimension()).resolve("region"),
            fixerUpper,
            sync,
            DataFixTypes.CHUNK,
            LegacyStructureDataHandler.getLegacyTagFixer(level.dimension(), overworldDataStorage, fixerUpper)
        );
        Path dimensionPath = levelStorageAccess.getDimensionPath(level.dimension());
        this.storageName = dimensionPath.getFileName().toString();
        this.level = level;
        RegistryAccess registryAccess = level.registryAccess();
        long seed = level.getSeed();
        // CraftBukkit start - SPIGOT-7051: It's a rigged game! Use delegate for random state creation, otherwise it is not so random.
        ChunkGenerator randomGenerator = generator;
        if (randomGenerator instanceof org.bukkit.craftbukkit.generator.CustomChunkGenerator customChunkGenerator) {
            randomGenerator = customChunkGenerator.getDelegate();
        }
        if (randomGenerator instanceof NoiseBasedChunkGenerator noiseBasedChunkGenerator) {
        // CraftBukkit end
            this.randomState = RandomState.create(noiseBasedChunkGenerator.generatorSettings().value(), registryAccess.lookupOrThrow(Registries.NOISE), seed);
        } else {
            this.randomState = RandomState.create(NoiseGeneratorSettings.dummy(), registryAccess.lookupOrThrow(Registries.NOISE), seed);
        }

        this.chunkGeneratorState = generator.createState(registryAccess.lookupOrThrow(Registries.STRUCTURE_SET), this.randomState, seed, level.spigotConfig); // Spigot
        this.mainThreadExecutor = mainThreadExecutor;
        ConsecutiveExecutor consecutiveExecutor = new ConsecutiveExecutor(dispatcher, "worldgen");
        this.chunkStatusListener = chunkStatusListener;
        ConsecutiveExecutor consecutiveExecutor1 = new ConsecutiveExecutor(dispatcher, "light");
        // Paper - rewrite chunk system
        this.lightEngine = new ThreadedLevelLightEngine(
            lightChunk, this, this.level.dimensionType().hasSkyLight(), consecutiveExecutor1, null // Paper - rewrite chunk system
        );
        this.distanceManager = new ChunkMap.DistanceManager(ticketStorage, dispatcher, mainThreadExecutor);
        this.ticketStorage = ticketStorage;
        this.poiManager = new PoiManager(
            new RegionStorageInfo(levelStorageAccess.getLevelId(), level.dimension(), "poi"),
            dimensionPath.resolve("poi"),
            fixerUpper,
            sync,
            registryAccess,
            level.getServer(),
            level
        );
        this.setServerViewDistance(serverViewDistance);
        this.worldGenContext = new WorldGenContext(level, generator, structureManager, this.lightEngine, null, this::setChunkUnsaved); // Paper - rewrite chunk system
    }

    private void setChunkUnsaved(ChunkPos chunkPos) {
        // Paper - rewrite chunk system
    }

    // Paper start - Optional per player mob spawns
    public void updatePlayerMobTypeMap(final Entity entity) {
        if (!this.level.paperConfig().entities.spawning.perPlayerMobSpawns) {
            return;
        }

        final int index = entity.getType().getCategory().ordinal();
        final ca.spottedleaf.moonrise.common.list.ReferenceList<ServerPlayer> inRange =
            this.level.moonrise$getNearbyPlayers().getPlayers(entity.chunkPosition(), ca.spottedleaf.moonrise.common.misc.NearbyPlayers.NearbyMapType.TICK_VIEW_DISTANCE);
        if (inRange == null) {
            return;
        }

        final ServerPlayer[] backingSet = inRange.getRawDataUnchecked();
        for (int i = 0, len = inRange.size(); i < len; i++) {
            ++(backingSet[i].mobCounts[index]);
        }
    }

    // Paper start - per player mob count backoff
    public void updateFailurePlayerMobTypeMap(int chunkX, int chunkZ, net.minecraft.world.entity.MobCategory mobCategory) {
        if (!this.level.paperConfig().entities.spawning.perPlayerMobSpawns) {
            return;
        }
        int idx = mobCategory.ordinal();
        final ca.spottedleaf.moonrise.common.list.ReferenceList<ServerPlayer> inRange =
            this.level.moonrise$getNearbyPlayers().getPlayersByChunk(chunkX, chunkZ, ca.spottedleaf.moonrise.common.misc.NearbyPlayers.NearbyMapType.TICK_VIEW_DISTANCE);
        if (inRange == null) {
            return;
        }
        final ServerPlayer[] backingSet = inRange.getRawDataUnchecked();
        for (int i = 0, len = inRange.size(); i < len; i++) {
            ++(backingSet[i].mobBackoffCounts[idx]);
        }
    }
    // Paper end - per player mob count backoff
    public int getMobCountNear(final ServerPlayer player, final net.minecraft.world.entity.MobCategory mobCategory) {
        return player.mobCounts[mobCategory.ordinal()] + player.mobBackoffCounts[mobCategory.ordinal()]; // Paper - per player mob count backoff
    }
    // Paper end - Optional per player mob spawns

    protected ChunkGenerator generator() {
        return this.worldGenContext.generator();
    }

    protected ChunkGeneratorStructureState generatorState() {
        return this.chunkGeneratorState;
    }

    protected RandomState randomState() {
        return this.randomState;
    }

    public boolean isChunkTracked(ServerPlayer player, int x, int z) {
        return ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getPlayerChunkLoader().isChunkSent(player, x, z); // Paper - rewrite chunk system
    }

    private boolean isChunkOnTrackedBorder(ServerPlayer player, int x, int z) {
        return ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getPlayerChunkLoader().isChunkSent(player, x, z, true); // Paper - rewrite chunk system
    }

    protected ThreadedLevelLightEngine getLightEngine() {
        return this.lightEngine;
    }

    public @Nullable ChunkHolder getUpdatingChunkIfPresent(long chunkPos) {
        // Paper start - rewrite chunk system
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder holder = ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolder(chunkPos);
        return holder == null ? null : holder.vanillaChunkHolder;
        // Paper end - rewrite chunk system
    }

    public @Nullable ChunkHolder getVisibleChunkIfPresent(long chunkPos) {
        // Paper start - rewrite chunk system
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder holder = ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolder(chunkPos);
        return holder == null ? null : holder.vanillaChunkHolder;
        // Paper end - rewrite chunk system
    }

    public @Nullable ChunkStatus getLatestStatus(long chunkPos) {
        ChunkHolder visibleChunkIfPresent = this.getVisibleChunkIfPresent(chunkPos);
        return visibleChunkIfPresent != null ? visibleChunkIfPresent.getLatestStatus() : null;
    }

    protected IntSupplier getChunkQueueLevel(long chunkPos) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public String getChunkDebugData(ChunkPos pos) {
        ChunkHolder visibleChunkIfPresent = this.getVisibleChunkIfPresent(pos.toLong());
        if (visibleChunkIfPresent == null) {
            return "null";
        } else {
            String string = visibleChunkIfPresent.getTicketLevel() + "\n";
            ChunkStatus latestStatus = visibleChunkIfPresent.getLatestStatus();
            ChunkAccess latestChunk = visibleChunkIfPresent.getLatestChunk();
            if (latestStatus != null) {
                string = string + "St: §" + latestStatus.getIndex() + latestStatus + "§r\n";
            }

            if (latestChunk != null) {
                string = string + "Ch: §" + latestChunk.getPersistedStatus().getIndex() + latestChunk.getPersistedStatus() + "§r\n";
            }

            FullChunkStatus fullStatus = visibleChunkIfPresent.getFullStatus();
            string = string + '§' + fullStatus.ordinal() + fullStatus;
            return string + "§r";
        }
    }

    CompletableFuture<ChunkResult<List<ChunkAccess>>> getChunkRangeFuture(ChunkHolder chunkHolder, int range, IntFunction<ChunkStatus> statusGetter) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public ReportedException debugFuturesAndCreateReportedException(IllegalStateException exception, String details) {
        StringBuilder stringBuilder = new StringBuilder();
        Consumer<ChunkHolder> consumer = chunk -> chunk.getAllFutures()
            .forEach(
                pair -> {
                    ChunkStatus chunkStatus = pair.getFirst();
                    CompletableFuture<ChunkResult<ChunkAccess>> completableFuture = pair.getSecond();
                    if (completableFuture != null && completableFuture.isDone() && completableFuture.join() == null) {
                        stringBuilder.append(chunk.getPos())
                            .append(" - status: ")
                            .append(chunkStatus)
                            .append(" future: ")
                            .append(completableFuture)
                            .append(System.lineSeparator());
                    }
                }
            );
        stringBuilder.append("Updating:").append(System.lineSeparator());
        ca.spottedleaf.moonrise.common.PlatformHooks.get().getUpdatingChunkHolders(this.level).forEach(consumer); // Paper
        stringBuilder.append("Visible:").append(System.lineSeparator());
        ca.spottedleaf.moonrise.common.PlatformHooks.get().getVisibleChunkHolders(this.level).forEach(consumer); // Paper
        CrashReport crashReport = CrashReport.forThrowable(exception, "Chunk loading");
        CrashReportCategory crashReportCategory = crashReport.addCategory("Chunk loading");
        crashReportCategory.setDetail("Details", details);
        crashReportCategory.setDetail("Futures", stringBuilder);
        return new ReportedException(crashReport);
    }

    public CompletableFuture<ChunkResult<LevelChunk>> prepareEntityTickingChunk(ChunkHolder chunk) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    @Nullable ChunkHolder updateChunkScheduling(long chunkPos, int newLevel, @Nullable ChunkHolder holder, int oldLevel) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    private void onLevelChange(ChunkPos chunkPos, IntSupplier queueLevelGetter, int ticketLevel, IntConsumer queueLevelSetter) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    @Override
    public void close() throws IOException {
        throw new UnsupportedOperationException("Use ServerChunkCache#close"); // Paper - rewrite chunk system
    }

    protected void saveAllChunks(boolean flush) {
        // Paper start - rewrite chunk system
        ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getChunkTaskScheduler().chunkHolderManager.saveAllChunks(
            flush, false, false, false
        );
        // Paper end - rewrite chunk system
    }

    protected void tick(BooleanSupplier hasMoreTime) {
        final ca.spottedleaf.leafprofiler.RegionizedProfiler.Handle profiler = io.papermc.paper.threadedregions.TickRegionScheduler.getProfiler(); // Folia - profiler
        ProfilerFiller profilerFiller = Profiler.get();
        profilerFiller.push("poi");
        profiler.startTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.POI_MANAGER_TICK); try { // Folia - profiler
        this.poiManager.tick(hasMoreTime);
        } finally { profiler.stopTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.POI_MANAGER_TICK); } // Folia - profiler
        profilerFiller.popPush("chunk_unload");
        if (!this.level.noSave()) {
            profiler.startTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.PROCESS_UNLOADS); try { // Folia - profiler
            this.processUnloads(hasMoreTime);
            } finally { profiler.stopTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.PROCESS_UNLOADS); } // Folia - profiler
        }

        profilerFiller.pop();
    }

    public boolean hasWork() {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    private void processUnloads(BooleanSupplier hasMoreTime) {
        ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getChunkTaskScheduler().chunkHolderManager.processUnloads(); // Paper - rewrite chunk system
        ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getChunkTaskScheduler().chunkHolderManager.autoSave(); // Paper - rewrite chunk system
    }

    private void saveChunksEagerly(BooleanSupplier hasMoreTime) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    private void scheduleUnload(long chunkPos, ChunkHolder chunkHolder) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    protected boolean promoteChunkMap() {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    private CompletableFuture<ChunkAccess> scheduleChunkLoad(ChunkPos chunkPos) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    private ChunkAccess handleChunkLoadFailure(Throwable exception, ChunkPos chunkPos) {
        Throwable throwable = exception instanceof CompletionException completionException ? completionException.getCause() : exception;
        Throwable throwable1 = throwable instanceof ReportedException reportedException ? reportedException.getCause() : throwable;
        boolean flag = throwable1 instanceof Error;
        boolean flag1 = throwable1 instanceof IOException || throwable1 instanceof NbtException;
        if (!flag) {
            if (!flag1) {
            }

            this.level.getServer().reportChunkLoadFailure(throwable1, this.storageInfo(), chunkPos);
            return this.createEmptyChunk(chunkPos);
        } else {
            CrashReport crashReport = CrashReport.forThrowable(exception, "Exception loading chunk");
            CrashReportCategory crashReportCategory = crashReport.addCategory("Chunk being loaded");
            crashReportCategory.setDetail("pos", chunkPos);
            this.markPositionReplaceable(chunkPos);
            throw new ReportedException(crashReport);
        }
    }

    private ChunkAccess createEmptyChunk(ChunkPos chunkPos) {
        this.markPositionReplaceable(chunkPos);
        return new ProtoChunk(chunkPos, UpgradeData.EMPTY, this.level, this.level.palettedContainerFactory(), null);
    }

    private void markPositionReplaceable(ChunkPos chunkPos) {
        this.chunkTypeCache.put(chunkPos.toLong(), (byte)-1);
    }

    private byte markPosition(ChunkPos chunkPos, ChunkType chunkType) {
        return this.chunkTypeCache.put(chunkPos.toLong(), (byte)(chunkType == ChunkType.PROTOCHUNK ? -1 : 1));
    }

    @Override
    public GenerationChunkHolder acquireGeneration(long chunkPos) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    @Override
    public void releaseGeneration(GenerationChunkHolder chunk) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    @Override
    public CompletableFuture<ChunkAccess> applyStep(GenerationChunkHolder chunk, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    @Override
    public ChunkGenerationTask scheduleGenerationTask(ChunkStatus targetStatus, ChunkPos pos) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    private void runGenerationTask(ChunkGenerationTask task) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    @Override
    public void runGenerationTasks() {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public CompletableFuture<ChunkResult<LevelChunk>> prepareTickingChunk(ChunkHolder holder) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    private void onChunkReadyToSend(ChunkHolder chunkHolder, LevelChunk chunk) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public CompletableFuture<ChunkResult<LevelChunk>> prepareAccessibleChunk(ChunkHolder chunk) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    Stream<ChunkHolder> allChunksWithAtLeastStatus(ChunkStatus status) {
        // Paper start - rewrite chunk system
        final int i = ChunkLevel.byStatus(status);
        return ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getChunkTaskScheduler().chunkHolderManager
            .getOldChunkHolders()
            .stream()
            .filter(holder -> holder.getTicketLevel() <= i);
        // Paper end - rewrite chunk system
    }

    private boolean saveChunkIfNeeded(ChunkHolder chunk, long gameTime) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public boolean save(ChunkAccess chunk) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    private boolean isExistingChunkFull(ChunkPos chunkPos) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public void setServerViewDistance(int viewDistance) {
        // Paper start - rewrite chunk system
        final int clamped = Mth.clamp(viewDistance, 2, ca.spottedleaf.moonrise.common.util.MoonriseConstants.MAX_VIEW_DISTANCE);
        if (clamped == this.serverViewDistance) {
            return;
        }

        this.serverViewDistance = clamped;
        ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getPlayerChunkLoader().setLoadDistance(this.serverViewDistance + 1);
        // Paper end - rewrite chunk system
    }

    int getPlayerViewDistance(ServerPlayer player) {
        return ca.spottedleaf.moonrise.common.PlatformHooks.get().getSendViewDistance(player); // Paper - rewrite chunk system
    }

    private void markChunkPendingToSend(ServerPlayer player, ChunkPos chunkPos) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    private static void markChunkPendingToSend(ServerPlayer player, LevelChunk chunk) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    private static void dropChunk(ServerPlayer player, ChunkPos chunkPos) {
        // Paper - rewrite chunk system
    }

    // Paper start - rewrite chunk system
    @Override
    public CompletableFuture<Optional<CompoundTag>> read(final ChunkPos pos) {
        final CompletableFuture<Optional<CompoundTag>> ret = new CompletableFuture<>();

        ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.loadDataAsync(
            this.level, pos.x, pos.z, ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionFileType.CHUNK_DATA,
            (final CompoundTag data, final Throwable thr) -> {
                if (thr != null) {
                    ret.completeExceptionally(thr);
                } else {
                    ret.complete(Optional.ofNullable(data));
                }
            }, false
        );

        return ret;
    }

    @Override
    public CompletableFuture<Void> write(final ChunkPos pos, final Supplier<CompoundTag> tag) {
        ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.scheduleSave(
            this.level, pos.x, pos.z, tag.get(),
            ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionFileType.CHUNK_DATA
        );
        return null;
    }

    @Override
    public CompletableFuture<Void> synchronize(final boolean flush) {
        try {
            ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.flush(this.level);
            if (flush) {
                ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.flushRegionStorages(this.level);
            }
            return CompletableFuture.completedFuture(null);
        } catch (final Exception ex) {
            return CompletableFuture.failedFuture(ex);
        }
    }
    // Paper end - rewrite chunk system

    public @Nullable LevelChunk getChunkToSend(long chunkPos) {
        ChunkHolder visibleChunkIfPresent = this.getVisibleChunkIfPresent(chunkPos);
        return visibleChunkIfPresent == null ? null : visibleChunkIfPresent.getChunkToSend();
    }

    public int size() {
        return ca.spottedleaf.moonrise.common.PlatformHooks.get().getVisibleChunkHolderCount(this.level); // Paper
    }

    public net.minecraft.server.level.DistanceManager getDistanceManager() {
        return this.distanceManager;
    }

    void dumpChunks(Writer writer) throws IOException {
        CsvOutput csvOutput = CsvOutput.builder()
            .addColumn("x")
            .addColumn("z")
            .addColumn("level")
            .addColumn("in_memory")
            .addColumn("status")
            .addColumn("full_status")
            .addColumn("accessible_ready")
            .addColumn("ticking_ready")
            .addColumn("entity_ticking_ready")
            .addColumn("ticket")
            .addColumn("spawning")
            .addColumn("block_entity_count")
            .addColumn("ticking_ticket")
            .addColumn("ticking_level")
            .addColumn("block_ticks")
            .addColumn("fluid_ticks")
            .build(writer);

        for (ChunkHolder entry : ca.spottedleaf.moonrise.common.PlatformHooks.get().getVisibleChunkHolders(this.level)) { // Paper - Moonrise
            long longKey = entry.pos.toLong(); // Paper - Moonrise
            ChunkPos chunkPos = new ChunkPos(longKey);
            ChunkHolder chunkHolder = entry; // Paper - Moonrise
            Optional<ChunkAccess> optional = Optional.ofNullable(chunkHolder.getLatestChunk());
            Optional<LevelChunk> optional1 = optional.flatMap(chunk -> chunk instanceof LevelChunk ? Optional.of((LevelChunk)chunk) : Optional.empty());
            csvOutput.writeRow(
                chunkPos.x,
                chunkPos.z,
                chunkHolder.getTicketLevel(),
                optional.isPresent(),
                optional.map(ChunkAccess::getPersistedStatus).orElse(null),
                optional1.map(LevelChunk::getFullStatus).orElse(null),
                printFuture(chunkHolder.getFullChunkFuture()),
                printFuture(chunkHolder.getTickingChunkFuture()),
                printFuture(chunkHolder.getEntityTickingChunkFuture()),
                this.ticketStorage.getTicketDebugString(longKey, false),
                this.anyPlayerCloseEnoughForSpawning(chunkPos),
                optional1.<Integer>map(chunk -> chunk.getBlockEntities().size()).orElse(0),
                this.ticketStorage.getTicketDebugString(longKey, true),
                this.distanceManager.getChunkLevel(longKey, true),
                optional1.<Integer>map(chunk -> chunk.getBlockTicks().count()).orElse(0),
                optional1.<Integer>map(chunk -> chunk.getFluidTicks().count()).orElse(0)
            );
        }
    }

    private static String printFuture(CompletableFuture<ChunkResult<LevelChunk>> future) {
        try {
            ChunkResult<LevelChunk> chunkResult = future.getNow(null);
            if (chunkResult != null) {
                return chunkResult.isSuccess() ? "done" : "unloaded";
            } else {
                return "not completed";
            }
        } catch (CompletionException var2) {
            return "failed " + var2.getCause().getMessage();
        } catch (CancellationException var3) {
            return "cancelled";
        }
    }

    private CompletableFuture<Optional<CompoundTag>> readChunk(ChunkPos pos) {
        return this.read(pos).thenApplyAsync(optional -> optional.map(this::upgradeChunkTag), Util.backgroundExecutor().forName("upgradeChunk"));
    }

    public CompoundTag upgradeChunkTag(CompoundTag tag) { // Paper - rewrite chunk system - public
        return this.upgradeChunkTag(tag, -1, getChunkDataFixContextTag(this.level.getTypeKey(), this.generator().getTypeNameForDataFixer()), this.level); // CraftBukkit
    }

    public static CompoundTag getChunkDataFixContextTag(ResourceKey<net.minecraft.world.level.dimension.LevelStem> stemKey, Optional<ResourceKey<MapCodec<? extends ChunkGenerator>>> generator) { // CraftBukkit
        CompoundTag compoundTag = new CompoundTag();
        compoundTag.putString("dimension", stemKey.identifier().toString()); // CraftBukkit
        generator.ifPresent(resourceKey -> compoundTag.putString("generator", resourceKey.identifier().toString()));
        return compoundTag;
    }

    // Paper start - optimise chunk tick iteration
    private boolean isChunkNearPlayer(final ChunkMap chunkMap, final ChunkPos chunkPos, final LevelChunk levelChunk) {
        final ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkData chunkData = ((ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkSystemLevelChunk)levelChunk).moonrise$getChunkHolder().holderData;
        final ca.spottedleaf.moonrise.common.misc.NearbyPlayers.TrackedChunk nearbyPlayers = this.level.moonrise$getNearbyPlayers().getChunk(chunkPos); // Folia - region threading
        if (nearbyPlayers == null) {
            return false;
        }

        // Note: cannot use narrow on Paper due to custom spawn range

        final ca.spottedleaf.moonrise.common.list.ReferenceList<ServerPlayer> players = nearbyPlayers.getPlayers(ca.spottedleaf.moonrise.common.misc.NearbyPlayers.NearbyMapType.SPAWN_RANGE);

        if (players == null) {
            return false;
        }

        final ServerPlayer[] raw = players.getRawDataUnchecked();
        final int len = players.size();

        Objects.checkFromIndexSize(0, len, raw.length);
        for (int i = 0; i < len; ++i) {
            // Paper start - PlayerNaturallySpawnCreaturesEvent
            com.destroystokyo.paper.event.entity.PlayerNaturallySpawnCreaturesEvent event = raw[i].playerNaturallySpawnedEvent;
            if (event == null || event.isCancelled()) {
                continue;
            }
            double blockRange = (double) ((event.getSpawnRadius() << 4) * (event.getSpawnRadius() << 4));
            if (chunkMap.playerIsCloseEnoughForSpawning(raw[i], chunkPos, blockRange)) {
                // Paper end - PlayerNaturallySpawnCreaturesEvent
                return true;
            }
        }

        return false;
    }
    // Paper end - optimise chunk tick iteration

    void collectSpawningChunks(List<LevelChunk> output) {
        // Paper start - optimise chunk tick iteration
        final ca.spottedleaf.moonrise.common.list.ReferenceList<net.minecraft.world.level.chunk.LevelChunk> tickingChunks = this.level.getCurrentWorldData().getEntityTickingChunks(); // Folia - region threading

        final LevelChunk[] raw = tickingChunks.getRawDataUnchecked();
        final int size = tickingChunks.size();

        Objects.checkFromToIndex(0, size, raw.length);
        for (int i = 0; i < size; ++i) {
            final LevelChunk levelChunk = raw[i];

            if (!this.isChunkNearPlayer((ChunkMap)(Object)this, levelChunk.getPos(), levelChunk)) {
                continue;
            }

            output.add(levelChunk);
        }
        // Paper end - optimise chunk tick iteration
    }

    void forEachBlockTickingChunk(Consumer<LevelChunk> action) {
        this.distanceManager.forEachEntityTickingChunk(chunkPos -> {
            ChunkHolder chunkHolder = this.getVisibleChunkIfPresent(chunkPos); // Paper - rewrite chunk system
            if (chunkHolder != null) {
                LevelChunk tickingChunk = chunkHolder.getTickingChunk();
                if (tickingChunk != null) {
                    action.accept(tickingChunk);
                }
            }
        });
    }

    public boolean anyPlayerCloseEnoughForSpawning(ChunkPos chunkPos) {
        // Spigot start
        return this.anyPlayerCloseEnoughForSpawning(chunkPos, false);
    }

    boolean anyPlayerCloseEnoughForSpawning(ChunkPos chunkPos, boolean reducedRange) {
        // Paper - chunk tick iteration optimisation - cannot use narrow check due to custom range
        return this.anyPlayerCloseEnoughForSpawningInternal(chunkPos, reducedRange); // Paper - chunk tick iteration optimisation
        // Spigot end
    }

    boolean anyPlayerCloseEnoughTo(BlockPos pos, int radius) {
        Vec3 vec3 = new Vec3(pos);

        for (ServerPlayer serverPlayer : this.level.getLocalPlayers()) { // Folia - region threading
            if (this.playerIsCloseEnoughTo(serverPlayer, vec3, radius)) {
                return true;
            }
        }

        return false;
    }

    private boolean anyPlayerCloseEnoughForSpawningInternal(ChunkPos chunkPos) {
        // Spigot start
        return this.anyPlayerCloseEnoughForSpawningInternal(chunkPos, false);
    }

    private boolean anyPlayerCloseEnoughForSpawningInternal(ChunkPos chunkPos, boolean reducedRange) {
        double blockRange; // Paper - use from event
        // Spigot end
        // Paper start - chunk tick iteration optimisation
        final ca.spottedleaf.moonrise.common.list.ReferenceList<ServerPlayer> players = ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getNearbyPlayers().getPlayers(
            chunkPos, ca.spottedleaf.moonrise.common.misc.NearbyPlayers.NearbyMapType.SPAWN_RANGE
        );
        if (players == null) {
            return false;
        }

        final ServerPlayer[] raw = players.getRawDataUnchecked();
        final int len = players.size();

        Objects.checkFromIndexSize(0, len, raw.length);
        for (int i = 0; i < len; ++i) {
            final ServerPlayer serverPlayer = raw[i];
            // Paper start - PlayerNaturallySpawnCreaturesEvent
            com.destroystokyo.paper.event.entity.PlayerNaturallySpawnCreaturesEvent event;
            blockRange = 16384.0D;
            if (reducedRange) {
                event = serverPlayer.playerNaturallySpawnedEvent;
                if (event == null || event.isCancelled()) continue;
                blockRange = (double) ((event.getSpawnRadius() << 4) * (event.getSpawnRadius() << 4));
            }
            if (this.playerIsCloseEnoughForSpawning(serverPlayer, chunkPos, blockRange)) {
                // Paper end - PlayerNaturallySpawnCreaturesEvent
                return true;
            }
        }

        return false;
        // Paper end - chunk tick iteration optimisation
    }

    public List<ServerPlayer> getPlayersCloseForSpawning(ChunkPos chunkPos) {
        // Paper start - chunk tick iteration optimisation
        final ca.spottedleaf.moonrise.common.list.ReferenceList<ServerPlayer> players = ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getNearbyPlayers().getPlayers(
            chunkPos, ca.spottedleaf.moonrise.common.misc.NearbyPlayers.NearbyMapType.SPAWN_RANGE
        );
        if (players == null) {
            return new ArrayList<>();
        }

        List<ServerPlayer> ret = null;

        final ServerPlayer[] raw = players.getRawDataUnchecked();
        final int len = players.size();

        Objects.checkFromIndexSize(0, len, raw.length);
        for (int i = 0; i < len; ++i) {
            final ServerPlayer player = raw[i];
            if (this.playerIsCloseEnoughForSpawning(player, chunkPos, 16384.0D)) { // Spigot
                if (ret == null) {
                    ret = new ArrayList<>(len - i);
                    ret.add(player);
                } else {
                    ret.add(player);
                }
            }
        }

        return ret == null ? new ArrayList<>() : ret;
        // Paper end - chunk tick iteration optimisation
    }

    public boolean playerIsCloseEnoughForSpawning(ServerPlayer player, ChunkPos chunkPos, double range) { // Spigot // Paper - chunk tick iteration optimisation - public
        if (player.isSpectator()) {
            return false;
        } else {
            double d = euclideanDistanceSquared(chunkPos, player.position());
            return d < range; // Spigot
        }
    }

    private boolean playerIsCloseEnoughTo(ServerPlayer player, Vec3 pos, int radius) {
        if (player.isSpectator()) {
            return false;
        } else {
            double d = player.position().distanceTo(pos);
            return d < radius;
        }
    }

    private static double euclideanDistanceSquared(ChunkPos chunkPos, Vec3 pos) {
        double d = SectionPos.sectionToBlockCoord(chunkPos.x, 8);
        double d1 = SectionPos.sectionToBlockCoord(chunkPos.z, 8);
        double d2 = d - pos.x;
        double d3 = d1 - pos.z;
        return d2 * d2 + d3 * d3;
    }

    private boolean skipPlayer(ServerPlayer player) {
        return player.isSpectator() && !this.level.getGameRules().get(GameRules.SPECTATORS_GENERATE_CHUNKS);
    }

    void updatePlayerStatus(ServerPlayer player, boolean track) {
        boolean flag = this.skipPlayer(player);
        //boolean flag1 = this.playerMap.ignoredOrUnknown(player); // Folia - region threading
        if (track) {
            //this.playerMap.addPlayer(player, flag); // Folia - region threading
            this.updatePlayerPos(player);
            if (!flag) {
                //this.distanceManager.addPlayer(SectionPos.of(player), player); // Folia - region threading
                ((ca.spottedleaf.moonrise.patches.chunk_tick_iteration.ChunkTickDistanceManager)this.distanceManager).moonrise$addPlayer(player, SectionPos.of(player)); // Paper - chunk tick iteration optimisation
            }

            player.setChunkTrackingView(ChunkTrackingView.EMPTY);
            ca.spottedleaf.moonrise.common.PlatformHooks.get().addPlayerToDistanceMaps(this.level, player); // Paper - rewrite chunk system
        } else {
            SectionPos lastSectionPos = player.getLastSectionPos();
            //this.playerMap.removePlayer(player); // Folia - region threading
            if (true) { // Folia - region threading
                //this.distanceManager.removePlayer(lastSectionPos, player); // Folia - region threading
                ((ca.spottedleaf.moonrise.patches.chunk_tick_iteration.ChunkTickDistanceManager)this.distanceManager).moonrise$removePlayer(player, SectionPos.of(player)); // Paper - chunk tick iteration optimisation
            }

            ca.spottedleaf.moonrise.common.PlatformHooks.get().removePlayerFromDistanceMaps(this.level, player); // Paper - rewrite chunk system
        }
    }

    private void updatePlayerPos(ServerPlayer player) {
        SectionPos sectionPos = SectionPos.of(player);
        player.setLastSectionPos(sectionPos);
    }

    public void move(ServerPlayer player) {
        // Paper - optimise entity tracker

        SectionPos lastSectionPos = player.getLastSectionPos();
        SectionPos sectionPos = SectionPos.of(player);
        //boolean flag = this.playerMap.ignored(player); // Folia - region threading
        boolean flag1 = this.skipPlayer(player);
        //boolean flag2 = lastSectionPos.asLong() != sectionPos.asLong(); // Folia - region threading
        if (true) { // Folia - region threading
            this.updatePlayerPos(player);
            ((ca.spottedleaf.moonrise.patches.chunk_tick_iteration.ChunkTickDistanceManager)this.distanceManager).moonrise$updatePlayer(player, lastSectionPos, sectionPos, false, flag1); // Paper - chunk tick iteration optimisation // Folia - region threading
            // Folia - region threading

            // Paper - rewrite chunk system
        }
        ca.spottedleaf.moonrise.common.PlatformHooks.get().updateMaps(this.level, player); // Paper - rewrite chunk system
    }

    private void updateChunkTracking(ServerPlayer player) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    private void applyChunkTrackingView(ServerPlayer player, ChunkTrackingView chunkTrackingView) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    @Override
    public List<ServerPlayer> getPlayers(ChunkPos pos, boolean boundaryOnly) {
        // Paper start - rewrite chunk system
        final ChunkHolder holder = this.getVisibleChunkIfPresent(pos.toLong());
        if (holder == null) {
            return new ArrayList<>();
        } else {
            return ((ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkSystemChunkHolder)holder).moonrise$getPlayers(boundaryOnly);
        }
        // Paper end - rewrite chunk system
    }

    public void addEntity(Entity entity) {
        org.spigotmc.AsyncCatcher.catchOp("entity track"); // Spigot
        // Paper start - ignore and warn about illegal addEntity calls instead of crashing server
        if (!entity.valid || entity.level() != this.level || entity.moonrise$getTrackedEntity() != null) { // Folia - region threading
            LOGGER.error("Illegal ChunkMap::addEntity for world " + this.level.getWorld().getName()
                + ": " + entity  + (entity.moonrise$getTrackedEntity() != null ? " ALREADY CONTAINED (This would have crashed your server)" : ""), new Throwable()); // Folia - region threading
            return;
        }
        // Paper end - ignore and warn about illegal addEntity calls instead of crashing server
        if (entity instanceof ServerPlayer && ((ServerPlayer) entity).suppressTrackerForLogin) return; // Paper - Fire PlayerJoinEvent when Player is actually ready; Delay adding to tracker until after list packets
        if (!(entity instanceof EnderDragonPart)) {
            EntityType<?> type = entity.getType();
            int i = type.clientTrackingRange() * 16;
            i = org.spigotmc.TrackingRange.getEntityTrackingRange(entity, i); // Spigot
            if (i != 0) {
                int updateInterval = type.updateInterval();
                if (entity.moonrise$getTrackedEntity() != null) { // Folia - region threading
                    throw (IllegalStateException)Util.pauseInIde(new IllegalStateException("Entity is already tracked!"));
                } else {
                    ChunkMap.TrackedEntity trackedEntity = new ChunkMap.TrackedEntity(entity, i, updateInterval, type.trackDeltas());
                    //this.entityMap.put(entity.getId(), trackedEntity); // Folia - region threading
                    // Paper start - optimise entity tracker
                    if (((ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerEntity)entity).moonrise$getTrackedEntity() != null) {
                        throw new IllegalStateException("Entity is already tracked");
                    }
                    ((ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerEntity)entity).moonrise$setTrackedEntity(trackedEntity);
                    // Paper end - optimise entity tracker
                    trackedEntity.updatePlayers(this.level.getLocalPlayers()); // Folia - region threading
                    if (entity instanceof ServerPlayer serverPlayer) {
                        this.updatePlayerStatus(serverPlayer, true);

                        // Folia start - region threading
                        for (Entity possible : this.level.getCurrentWorldData().trackerEntities) {
                            ChunkMap.TrackedEntity trackedEntity1 = possible.moonrise$getTrackedEntity();
                            if (trackedEntity1 == null) {
                                continue;
                            }
                            // Folia end - region threading
                            if (trackedEntity1.entity != serverPlayer) {
                                trackedEntity1.updatePlayer(serverPlayer);
                            }
                        }
                    }
                }
            }
        }
    }

    protected void removeEntity(Entity entity) {
        org.spigotmc.AsyncCatcher.catchOp("entity untrack"); // Spigot
        if (entity instanceof ServerPlayer serverPlayer) {
            this.updatePlayerStatus(serverPlayer, false);

            // Folia start - region threading
            for (Entity possible : this.level.getCurrentWorldData().getLocalEntities()) {
                ChunkMap.TrackedEntity trackedEntity = possible.moonrise$getTrackedEntity();
                if (trackedEntity == null) {
                    continue;
                }
                // Folia end - region threading
                trackedEntity.removePlayer(serverPlayer);
            }
            // Folia end - region threading
        }

        ChunkMap.TrackedEntity trackedEntity1 = entity.moonrise$getTrackedEntity(); // Folia - region threading
        if (trackedEntity1 != null) {
            trackedEntity1.broadcastRemoved();
        }
        ((ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerEntity)entity).moonrise$setTrackedEntity(null); // Paper - optimise entity tracker
    }

    // Paper start - optimise entity tracker
    private void newTrackerTick() {
        // Folia start - profiler
        final ca.spottedleaf.leafprofiler.RegionizedProfiler.Handle profiler = io.papermc.paper.threadedregions.TickRegionScheduler.getProfiler();
        // Mili start - batch entity culling buffer
        final java.util.Map<ServerPlayer, java.util.List<Entity>> mili$batchCullMap = new it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap<>();
        // Mili end - batch entity culling buffer
        final int totalEntities;
        profiler.startTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.ENTITY_TRACKER_TICK); try { // Folia - profiler
        // Folia end - profiler
        final io.papermc.paper.threadedregions.RegionizedWorldData worldData = this.level.getCurrentWorldData(); // Folia - region threading
        final ca.spottedleaf.moonrise.patches.chunk_system.level.entity.server.ServerEntityLookup entityLookup = (ca.spottedleaf.moonrise.patches.chunk_system.level.entity.server.ServerEntityLookup)((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getEntityLookup();;
        final ca.spottedleaf.moonrise.common.misc.NearbyPlayers nearbyPlayers = this.level.moonrise$getNearbyPlayers(); // Folia - region threading

        final ca.spottedleaf.moonrise.common.list.ReferenceList<net.minecraft.world.entity.Entity> trackerEntities = worldData.trackerEntities; // Folia - region threading
        final Entity[] trackerEntitiesRaw = trackerEntities.getRawDataUnchecked();
        for (int i = 0, len = totalEntities = trackerEntities.size(); i < len; ++i) { // Folia - region threading
            final Entity entity = trackerEntitiesRaw[i];
            // Mili start - fix ghost entities: skip null entries caused by concurrent swap-remove during iteration
            // When many entities die rapidly, setRemoved() synchronously removes from trackerEntities via swap-remove,
            // which can move a null to a position within the original iteration range.
            if (entity == null) {
                continue;
            }
            // Mili end - fix ghost entities
            final ChunkMap.TrackedEntity tracker = ((ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerEntity)entity).moonrise$getTrackedEntity();
            if (tracker == null) {
                continue;
            }
            // Mili start - batch entity culling: collect entities for batch processing
            if (fun.bm.mili.config.modules.experiment.RayTrackingEntityTrackerConfig.enabled
                && !tracker.seenBy.isEmpty()
                && !entity.isRemoved() // Mili - skip removed entities in batch culling
                && entity instanceof dev.tr7zw.entityculling.versionless.access.Cullable cullable) {
                // Check if entity should be force-visible
                if (!cullable.isForcedVisible() && !entity.isCurrentlyGlowing() && !entity.getType().skipRaytracningCheck) {
                    for (final ServerPlayerConnection conn : tracker.seenBy) {
                        ServerPlayer viewer = conn.getPlayer();
                        mili$batchCullMap.computeIfAbsent(viewer, k -> new java.util.ArrayList<>()).add(entity);
                    }
                } else {
                    cullable.setCulled(false);
                }
            }
            // Mili end - batch entity culling
            ((ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerTrackedEntity)tracker).moonrise$tick(nearbyPlayers.getChunk(entity.chunkPosition())); // Folia - region threading
            if (((ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerTrackedEntity)tracker).moonrise$hasPlayers()
                || ((ca.spottedleaf.moonrise.patches.chunk_system.entity.ChunkSystemEntity)entity).moonrise$getChunkStatus().isOrAfter(FullChunkStatus.ENTITY_TICKING)) {
                tracker.serverEntity.sendChanges();
            }
        }
        // Mili start - batch entity culling: one JNI call per viewer
        if (!mili$batchCullMap.isEmpty()) {
            double reachSq = (double) fun.bm.mili.config.modules.experiment.RayTrackingEntityTrackerConfig.tracingDistance
                * fun.bm.mili.config.modules.experiment.RayTrackingEntityTrackerConfig.tracingDistance;
            double hitboxLimit = fun.bm.mili.config.modules.experiment.RayTrackingEntityTrackerConfig.hitboxLimit;
            for (java.util.Map.Entry<ServerPlayer, java.util.List<Entity>> entry : mili$batchCullMap.entrySet()) {
                ServerPlayer viewer = entry.getKey();
                java.util.List<Entity> entityList = entry.getValue();
                byte[] results = fun.bm.mili.rust.EntityCullHelper.cullEntitiesBatch(
                    viewer, entityList, reachSq, hitboxLimit, null
                );
                if (results != null) {
                    fun.bm.mili.rust.EntityCullHelper.applyCullingResults(entityList, results);
                }
            }
            mili$batchCullMap.clear();
        }
        // Mili end - batch entity culling
        profiler.addCounter(ca.spottedleaf.leafprofiler.LProfilerRegistry.TRACKED_ENTITY_COUNTS, (long)totalEntities); // Folia - profiler
        } finally { profiler.stopTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.ENTITY_TRACKER_TICK); } // Folia - profiler
    }
    // Paper end - optimise entity tracker

    protected void tick() {
        // Paper start - optimise entity tracker
        if (true) {
            this.newTrackerTick();
            return;
        }
        // Paper end - optimise entity tracker
        // Paper - rewrite chunk system

        // Folia - region threading
    }

    public void sendToTrackingPlayers(Entity entity, Packet<? super ClientGamePacketListener> packet) {
        ChunkMap.TrackedEntity trackedEntity = entity.moonrise$getTrackedEntity(); // Folia - region threading
        if (trackedEntity != null) {
            trackedEntity.sendToTrackingPlayers(packet);
        }
    }

    public void sendToTrackingPlayersFiltered(Entity entity, Packet<? super ClientGamePacketListener> packet, Predicate<ServerPlayer> filter) {
        ChunkMap.TrackedEntity trackedEntity = entity.moonrise$getTrackedEntity(); // Folia - region threading
        if (trackedEntity != null) {
            trackedEntity.sendToTrackingPlayersFiltered(packet, filter);
        }
    }

    protected void sendToTrackingPlayersAndSelf(Entity entity, Packet<? super ClientGamePacketListener> packet) {
        ChunkMap.TrackedEntity trackedEntity = entity.moonrise$getTrackedEntity(); // Folia - region threading
        if (trackedEntity != null) {
            trackedEntity.sendToTrackingPlayersAndSelf(packet);
        }
    }

    public boolean isTrackedByAnyPlayer(Entity entity) {
        ChunkMap.TrackedEntity trackedEntity = entity.moonrise$getTrackedEntity(); // Folia - region threading
        return trackedEntity != null && !trackedEntity.seenBy.isEmpty();
    }

    public void forEachEntityTrackedBy(ServerPlayer player, Consumer<Entity> action) {
        throw new UnsupportedOperationException(); // Folia - region threading
    }

    public void resendBiomesForChunks(List<ChunkAccess> chunks) {
        Map<ServerPlayer, List<LevelChunk>> map = new HashMap<>();

        for (ChunkAccess chunkAccess : chunks) {
            ChunkPos pos = chunkAccess.getPos();
            LevelChunk levelChunk1;
            if (chunkAccess instanceof LevelChunk levelChunk) {
                levelChunk1 = levelChunk;
            } else {
                levelChunk1 = this.level.getChunk(pos.x, pos.z);
            }

            for (ServerPlayer serverPlayer : this.getPlayers(pos, false)) {
                map.computeIfAbsent(serverPlayer, serverPlayer1 -> new ArrayList<>()).add(levelChunk1);
            }
        }

        map.forEach((serverPlayer1, list) -> serverPlayer1.connection.send(ClientboundChunksBiomesPacket.forChunks((List<LevelChunk>)list)));
    }

    protected PoiManager getPoiManager() {
        return this.poiManager;
    }

    public String getStorageName() {
        return this.storageName;
    }

    void onFullChunkStatusChange(ChunkPos chunkPos, FullChunkStatus fullChunkStatus) {
        this.chunkStatusListener.onChunkStatusChange(chunkPos, fullChunkStatus);
    }

    public void waitForLightBeforeSending(ChunkPos chunkPos, int range) {
        // Paper - rewrite chunk system
    }

    public void forEachReadyToSendChunk(Consumer<LevelChunk> action) {
        for (ChunkHolder chunkHolder : ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getChunkTaskScheduler().chunkHolderManager.getOldChunkHolders()) { // Paper - rewrite chunk system
            LevelChunk chunkToSend = chunkHolder.getChunkToSend();
            if (chunkToSend != null) {
                action.accept(chunkToSend);
            }
        }
    }

    public class DistanceManager extends net.minecraft.server.level.DistanceManager implements ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkSystemDistanceManager { // Paper - rewrite chunk system
        protected DistanceManager(final TicketStorage ticketStorage, final Executor dispatcher, final Executor mainThreadExecutor) {
            super(ticketStorage, dispatcher, mainThreadExecutor);
        }

        // Paper start - rewrite chunk system
        @Override
        public final ChunkMap moonrise$getChunkMap() {
            return ChunkMap.this;
        }
        // Paper end - rewrite chunk system

        @Override
        protected boolean isChunkToRemove(long chunkPos) {
            throw new UnsupportedOperationException(); // Paper - rewrite chunk system
        }

        @Override
        protected @Nullable ChunkHolder getChunk(long chunkPos) {
            return ChunkMap.this.getUpdatingChunkIfPresent(chunkPos);
        }

        @Override
        protected @Nullable ChunkHolder updateChunkScheduling(long chunkPos, int newLevel, @Nullable ChunkHolder holder, int oldLevel) {
            return ChunkMap.this.updateChunkScheduling(chunkPos, newLevel, holder, oldLevel);
        }
    }

    public class TrackedEntity implements ServerEntity.Synchronizer, ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerTrackedEntity { // Paper - optimise entity tracker
        public final ServerEntity serverEntity;
        final Entity entity;
        private final int range;
        SectionPos lastSectionPos;
        public final Set<ServerPlayerConnection> seenBy = new it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet<>(); // Paper - Perf: optimise map impl

        // Paper start - optimise entity tracker
        private long lastChunkUpdate = -1L;
        private ca.spottedleaf.moonrise.common.misc.NearbyPlayers.TrackedChunk lastTrackedChunk;

        @Override
        public final void moonrise$tick(final ca.spottedleaf.moonrise.common.misc.NearbyPlayers.TrackedChunk chunk) {
            if (chunk == null) {
                this.moonrise$clearPlayers();
                return;
            }

            final ca.spottedleaf.moonrise.common.list.ReferenceList<ServerPlayer> players = chunk.getPlayers(ca.spottedleaf.moonrise.common.misc.NearbyPlayers.NearbyMapType.VIEW_DISTANCE);

            if (players == null) {
                this.moonrise$clearPlayers();
                return;
            }

            final long lastChunkUpdate = this.lastChunkUpdate;
            final long currChunkUpdate = chunk.getUpdateCount();
            final ca.spottedleaf.moonrise.common.misc.NearbyPlayers.TrackedChunk lastTrackedChunk = this.lastTrackedChunk;
            this.lastChunkUpdate = currChunkUpdate;
            this.lastTrackedChunk = chunk;

            final ServerPlayer[] playersRaw = players.getRawDataUnchecked();

            for (int i = 0, len = players.size(); i < len; ++i) {
                final ServerPlayer player = playersRaw[i];
                this.updatePlayer(player);
            }

            if (lastChunkUpdate != currChunkUpdate || lastTrackedChunk != chunk) {
                // need to purge any players possible not in the chunk list
                for (final ServerPlayerConnection conn : new java.util.ArrayList<>(this.seenBy)) {
                    final ServerPlayer player = conn.getPlayer();
                    if (!players.contains(player)) {
                        this.removePlayer(player);
                    }
                }
            }
        }

        @Override
        public final void moonrise$removeNonTickThreadPlayers() {
            boolean foundToRemove = false;
            for (final ServerPlayerConnection conn : this.seenBy) {
                if (!ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(conn.getPlayer())) {
                    foundToRemove = true;
                    break;
                }
            }

            if (!foundToRemove) {
                return;
            }

            for (final ServerPlayerConnection conn : new java.util.ArrayList<>(this.seenBy)) {
                ServerPlayer player = conn.getPlayer();
                if (!ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(player)) {
                    this.removePlayer(player);
                }
            }
        }

        @Override
        public final void moonrise$clearPlayers() {
            this.lastChunkUpdate = -1;
            this.lastTrackedChunk = null;
            if (this.seenBy.isEmpty()) {
                return;
            }
            for (final ServerPlayerConnection conn : new java.util.ArrayList<>(this.seenBy)) {
                ServerPlayer player = conn.getPlayer();
                this.removePlayer(player);
            }
        }

        @Override
        public final boolean moonrise$hasPlayers() {
            return !this.seenBy.isEmpty();
        }
        // Paper end - optimise entity tracker

        public TrackedEntity(final Entity entity, final int range, final int updateInterval, final boolean trackDelta) {
            this.serverEntity = new ServerEntity(ChunkMap.this.level, entity, updateInterval, trackDelta, this, this.seenBy); // Paper
            this.entity = entity;
            this.range = range;
            this.lastSectionPos = SectionPos.of(entity);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ChunkMap.TrackedEntity && ((ChunkMap.TrackedEntity)other).entity.getId() == this.entity.getId();
        }

        @Override
        public int hashCode() {
            return this.entity.getId();
        }

        @Override
        public void sendToTrackingPlayers(Packet<? super ClientGamePacketListener> packet) {
            for (ServerPlayerConnection serverPlayerConnection : this.seenBy) {
                serverPlayerConnection.send(packet);
            }
        }

        @Override
        public void sendToTrackingPlayersAndSelf(Packet<? super ClientGamePacketListener> packet) {
            this.sendToTrackingPlayers(packet);
            if (this.entity instanceof ServerPlayer serverPlayer) {
                serverPlayer.connection.send(packet);
            }
        }

        @Override
        public void sendToTrackingPlayersFiltered(Packet<? super ClientGamePacketListener> packet, Predicate<ServerPlayer> filter) {
            for (ServerPlayerConnection serverPlayerConnection : this.seenBy) {
                if (filter.test(serverPlayerConnection.getPlayer())) {
                    serverPlayerConnection.send(packet);
                }
            }
        }

        public void broadcastRemoved() {
            for (ServerPlayerConnection serverPlayerConnection : this.seenBy) {
                this.serverEntity.removePairing(serverPlayerConnection.getPlayer());
            }
        }

        public void removePlayer(ServerPlayer player) {
            org.spigotmc.AsyncCatcher.catchOp("player tracker clear"); // Spigot
            if (this.seenBy.remove(player.connection)) {
                this.serverEntity.removePairing(player);
                if (this.seenBy.isEmpty()) {
                    ChunkMap.this.level.debugSynchronizers().dropEntity(this.entity);
                }
            }
        }

        public void updatePlayer(ServerPlayer player) {
            org.spigotmc.AsyncCatcher.catchOp("player tracker update"); // Spigot
            if (player != this.entity) {
                // Paper start - remove allocation of Vec3D here
                // Vec3 vec3 = player.position().subtract(this.entity.position());
                double vec3_dx = player.getX() - this.entity.getX();
                double vec3_dz = player.getZ() - this.entity.getZ();
                // Paper end - remove allocation of Vec3D here
                int playerViewDistance = ChunkMap.this.getPlayerViewDistance(player);
                double d = Math.min(this.getEffectiveRange(), playerViewDistance * 16);
                double d1 = vec3_dx * vec3_dx + vec3_dz * vec3_dz; // Paper
                double d2 = d * d;
                // Paper start - Configurable entity tracking range by Y
                boolean flag = d1 <= d2;
                if (flag && level.paperConfig().entities.trackingRangeY.enabled) {
                    double rangeY = level.paperConfig().entities.trackingRangeY.get(this.entity, -1);
                    if (rangeY != -1) {
                        double vec3_dy = player.getY() - this.entity.getY();
                        flag = vec3_dy * vec3_dy <= rangeY * rangeY;
                    }
                }
                flag = flag && this.entity.broadcastToPlayer(player) && ChunkMap.this.isChunkTracked(player, this.entity.chunkPosition().x, this.entity.chunkPosition().z);
                // Paper end - Configurable entity tracking range by Y
                // Folia start - region threading
                if (flag && (this.entity instanceof ServerPlayer thisEntity) && thisEntity.broadcastedDeath) {
                    flag = false;
                }
                // Folia end - region threading
                // CraftBukkit start - respect vanish API
                if (flag && (!ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(player) || !player.getBukkitEntity().canSeeChunkMapUpdatePlayer(this.entity.getBukkitEntity()))) { // Paper - only consider hits // Folia - region threading // SparklyPaper - optimize canSee checks
                    flag = false;
                }
                // CraftBukkit end
                if (flag) {
                    if (this.seenBy.add(player.connection)) {
                        // Paper start - entity tracking events
                        if (io.papermc.paper.event.player.PlayerTrackEntityEvent.getHandlerList().getRegisteredListeners().length == 0 || new io.papermc.paper.event.player.PlayerTrackEntityEvent(player.getBukkitEntity(), this.entity.getBukkitEntity()).callEvent()) {
                        this.serverEntity.addPairing(player);
                        if (this.seenBy.size() == 1) {
                            ChunkMap.this.level.debugSynchronizers().registerEntity(this.entity);
                        }

                        ChunkMap.this.level.debugSynchronizers().startTrackingEntity(player, this.entity);
                        }
                        // Paper end - entity tracking events
                        this.serverEntity.onPlayerAdd(); // Paper - fix desync when a player is added to the tracker
                    }
                } else {
                    this.removePlayer(player);
                    // Leaves start - render bot
                    if (entity instanceof org.leavesmc.leaves.bot.ServerBot bot) {
                        if (bot.needSendFakeData(player)) {
                            bot.sendFakeData(player.connection, false);
                        }
                    }
                    // Leaves end - render bot
                }
            }
        }

        private int scaledRange(int trackingDistance) {
            return ChunkMap.this.level.getServer().getScaledTrackingDistance(trackingDistance);
        }

        private int getEffectiveRange() {
            // Paper start - optimise entity tracker
            final Entity entity = this.entity;
            int range = this.range;

            if (entity.getPassengers() == ImmutableList.<Entity>of()) {
                return this.scaledRange(range);
            }

            // note: we change to List
            final List<Entity> passengers = (List<Entity>)entity.getIndirectPassengers();
            for (int i = 0, len = passengers.size(); i < len; ++i) {
                final Entity passenger = passengers.get(i);
                // note: max should be branchless
                range = Math.max(range, ca.spottedleaf.moonrise.common.PlatformHooks.get().modifyEntityTrackingRange(passenger, passenger.getType().clientTrackingRange() << 4));
            }

            return this.scaledRange(range);
            // Paper end - optimise entity tracker
        }

        public void updatePlayers(List<ServerPlayer> playersList) {
            for (ServerPlayer serverPlayer : playersList) {
                this.updatePlayer(serverPlayer);
            }
        }
    }
}
