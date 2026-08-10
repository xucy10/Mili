package net.minecraft.server.level;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.FileUtil;
import net.minecraft.util.Util;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.ChunkScanAccess;
import net.minecraft.world.level.entity.ChunkStatusUpdateListener;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class ServerChunkCache extends ChunkSource implements ca.spottedleaf.moonrise.patches.chunk_system.world.ChunkSystemServerChunkCache { // Paper - rewrite chunk system
    private static final Logger LOGGER = LogUtils.getLogger();
    private final DistanceManager distanceManager;
    private final ServerLevel level;
    public final Thread mainThread;
    final ThreadedLevelLightEngine lightEngine;
    public final ServerChunkCache.MainThreadExecutor mainThreadProcessor;
    public final ChunkMap chunkMap;
    private final DimensionDataStorage dataStorage;
    private final TicketStorage ticketStorage;
    //private long lastInhabitedUpdate; // Folia - region threading
    public boolean spawnEnemies = true;
    public boolean spawnFriendlies = true; // Paper - add back spawnFriendlies field
    private static final int CACHE_SIZE = 4;
    private final long[] lastChunkPos = new long[4];
    private final @Nullable ChunkStatus[] lastChunkStatus = new ChunkStatus[4];
    private final @Nullable ChunkAccess[] lastChunk = new ChunkAccess[4];
    // Folia - moved to regionised world data
    // Paper start
    public final ca.spottedleaf.concurrentutil.map.ConcurrentLong2ReferenceChainedHashTable<net.minecraft.world.level.chunk.LevelChunk> fullChunks = new ca.spottedleaf.concurrentutil.map.ConcurrentLong2ReferenceChainedHashTable<>();
    public int getFullChunksCount() {
        return this.fullChunks.size();
    }
    long chunkFutureAwaitCounter;
    // Paper end
    // Paper start - rewrite chunk system

    @Override
    public final void moonrise$setFullChunk(final int chunkX, final int chunkZ, final LevelChunk chunk) {
        final long key = ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkKey(chunkX, chunkZ);
        if (chunk == null) {
            this.fullChunks.remove(key);
        } else {
            this.fullChunks.put(key, chunk);
        }
    }

    @Override
    public final LevelChunk moonrise$getFullChunkIfLoaded(final int chunkX, final int chunkZ) {
        return this.fullChunks.get(ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkKey(chunkX, chunkZ));
    }

    private ChunkAccess syncLoad(final int chunkX, final int chunkZ, final ChunkStatus toStatus) {
        // Folia start - region threading
        if (ca.spottedleaf.moonrise.common.util.TickThread.isTickThread()) {
            ca.spottedleaf.moonrise.common.util.TickThread.ensureTickThread(this.level, chunkX, chunkZ, "Cannot asynchronously load chunks");
        }
        // Folia end - region threading
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler chunkTaskScheduler = ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getChunkTaskScheduler();
        final CompletableFuture<ChunkAccess> completable = new CompletableFuture<>();
        chunkTaskScheduler.scheduleChunkLoad(
            chunkX, chunkZ, toStatus, true, ca.spottedleaf.concurrentutil.util.Priority.BLOCKING,
            completable::complete
        );

        if (!completable.isDone() && chunkTaskScheduler.hasShutdown()) {
            throw new IllegalStateException(
                "Chunk system has shut down, cannot process chunk requests in world '" + ca.spottedleaf.moonrise.common.util.WorldUtil.getWorldName(this.level) + "' at "
                    + "(" + chunkX + "," + chunkZ + ") status: " + toStatus
            );
        }

        if (ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(this.level, chunkX, chunkZ)) {
            ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler.pushChunkWait(this.level, chunkX, chunkZ);
            this.mainThreadProcessor.managedBlock(completable::isDone);
            ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler.popChunkWait();
        }

        final ChunkAccess ret = completable.join();
        if (ret == null) {
            throw new IllegalStateException("Chunk not loaded when requested");
        }

        return ret;
    }

    private ChunkAccess getChunkFallback(final int chunkX, final int chunkZ, final ChunkStatus toStatus,
                                         final boolean load) {
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler chunkTaskScheduler = ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getChunkTaskScheduler();
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager chunkHolderManager = chunkTaskScheduler.chunkHolderManager;

        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder currentChunk = chunkHolderManager.getChunkHolder(ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkKey(chunkX, chunkZ));

        final ChunkAccess ifPresent = currentChunk == null ? null : currentChunk.getChunkIfPresent(toStatus);

        if (ifPresent != null && (toStatus != ChunkStatus.FULL || currentChunk.isFullChunkReady())) {
            return ifPresent;
        }

        final ca.spottedleaf.moonrise.common.PlatformHooks platformHooks = ca.spottedleaf.moonrise.common.PlatformHooks.get();

        if (platformHooks.hasCurrentlyLoadingChunk() && currentChunk != null) {
            final ChunkAccess loading = platformHooks.getCurrentlyLoadingChunk(currentChunk.vanillaChunkHolder);
            if (loading != null && ca.spottedleaf.moonrise.common.util.TickThread.isTickThread()) {
                return loading;
            }
        }

        return load ? this.syncLoad(chunkX, chunkZ, toStatus) : null;
    }
    // Paper end - rewrite chunk system
    // Paper start - chunk tick iteration optimisations
    private final ca.spottedleaf.moonrise.common.util.SimpleThreadUnsafeRandom shuffleRandom = new ca.spottedleaf.moonrise.common.util.SimpleThreadUnsafeRandom(0L);
    private void iterateTickingChunksFaster() {
        final ca.spottedleaf.leafprofiler.RegionizedProfiler.Handle foliaProfiler = io.papermc.paper.threadedregions.TickRegionScheduler.getProfiler(); // Folia - profiler
        foliaProfiler.startTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.RANDOM_TICK); try { // Folia - profiler
        final ServerLevel world = this.level;
        final int randomTickSpeed = world.getGameRules().get(GameRules.RANDOM_TICK_SPEED);

        // TODO check on update: impl of forEachBlockTickingChunk will only iterate ENTITY ticking chunks!
        // TODO check on update: consumer just runs tickChunk
        final ca.spottedleaf.moonrise.common.list.ReferenceList<net.minecraft.world.level.chunk.LevelChunk> entityTickingChunks = world.getCurrentWorldData().getEntityTickingChunks(); // Folia - region threading

        // note: we can use the backing array here because:
        // 1. we do not care about new additions
        // 2. _removes_ are impossible at this stage in the tick
        final LevelChunk[] raw = entityTickingChunks.getRawDataUnchecked();
        final int size = entityTickingChunks.size(); foliaProfiler.addCounter(ca.spottedleaf.leafprofiler.LProfilerRegistry.RANDOM_CHUNK_TICK_COUNT, (long)size); // Folia - profiler

        java.util.Objects.checkFromToIndex(0, size, raw.length);
        for (int i = 0; i < size; ++i) {
            world.tickChunk(raw[i], randomTickSpeed);

            // call mid-tick tasks for chunk system
            if ((i & 7) == 0) {
                ((ca.spottedleaf.moonrise.patches.chunk_system.server.ChunkSystemMinecraftServer)this.level.getServer()).moonrise$executeMidTickTasks();
                continue;
            }
        }
        } finally { foliaProfiler.stopTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.RANDOM_TICK); } // Folia - profiler
    }
    // Paper end - chunk tick iteration optimisations


    public ServerChunkCache(
        ServerLevel level,
        LevelStorageSource.LevelStorageAccess levelStorageAccess,
        DataFixer fixerUpper,
        StructureTemplateManager structureManager,
        Executor dispatcher,
        ChunkGenerator generator,
        int viewDistance,
        int simulationDistance,
        boolean sync,
        ChunkStatusUpdateListener chunkStatusListener,
        Supplier<DimensionDataStorage> overworldDataStorage
    ) {
        this.level = level;
        this.mainThreadProcessor = new ServerChunkCache.MainThreadExecutor(level);
        this.mainThread = Thread.currentThread();
        Path path = levelStorageAccess.getDimensionPath(level.dimension()).resolve("data");

        try {
            FileUtil.createDirectoriesSafe(path);
        } catch (IOException var14) {
            LOGGER.error("Failed to create dimension data storage directory", (Throwable)var14);
        }

        this.dataStorage = new DimensionDataStorage(path, fixerUpper, level.registryAccess());
        this.ticketStorage = this.dataStorage.computeIfAbsent(TicketStorage.TYPE);
        this.chunkMap = new ChunkMap(
            level,
            levelStorageAccess,
            fixerUpper,
            structureManager,
            dispatcher,
            this.mainThreadProcessor,
            this,
            generator,
            chunkStatusListener,
            overworldDataStorage,
            this.ticketStorage,
            viewDistance,
            sync
        );
        this.lightEngine = this.chunkMap.getLightEngine();
        this.distanceManager = this.chunkMap.getDistanceManager();
        this.distanceManager.updateSimulationDistance(simulationDistance);
        this.clearCache();
    }

    // CraftBukkit start - properly implement isChunkLoaded
    public boolean isChunkLoaded(int chunkX, int chunkZ) {
        ChunkHolder chunk = this.chunkMap.getUpdatingChunkIfPresent(ChunkPos.asLong(chunkX, chunkZ));
        if (chunk == null) {
            return false;
        }
        return chunk.getFullChunkNow() != null;
    }
    // CraftBukkit end
    // Paper start
    // Paper - rewrite chunk system

    @Nullable
    public ChunkAccess getChunkAtImmediately(int x, int z) {
        ChunkHolder holder = this.chunkMap.getVisibleChunkIfPresent(ChunkPos.asLong(x, z));
        if (holder == null) {
            return null;
        }

        return holder.getLatestChunk();
    }

    public void addTicketAtLevel(TicketType ticketType, ChunkPos chunkPos, int ticketLevel) {
        this.ticketStorage.addTicket(new Ticket(ticketType, ticketLevel), chunkPos);
    }

    public void removeTicketAtLevel(TicketType ticketType, ChunkPos chunkPos, int ticketLevel) {
        this.ticketStorage.removeTicket(new Ticket(ticketType, ticketLevel), chunkPos);
    }

    // "real" get chunk if loaded
    // Note: Partially copied from the getChunkAt method below
    @Nullable
    public LevelChunk getChunkAtIfCachedImmediately(int x, int z) {
        long k = ChunkPos.asLong(x, z);

        // Note: Bypass cache since we need to check ticket level, and to make this MT-Safe

        ChunkHolder playerChunk = this.getVisibleChunkIfPresent(k);
        if (playerChunk == null) {
            return null;
        }

        return playerChunk.getFullChunkNowUnchecked();
    }

    @Nullable
    public LevelChunk getChunkAtIfLoadedImmediately(int x, int z) {
        return this.fullChunks.get(ChunkPos.asLong(x, z));
    }
    // Paper end

    @Override
    public ThreadedLevelLightEngine getLightEngine() {
        return this.lightEngine;
    }

    private @Nullable ChunkHolder getVisibleChunkIfPresent(long chunkPos) {
        return this.chunkMap.getVisibleChunkIfPresent(chunkPos);
    }

    private void storeInCache(long chunkPos, @Nullable ChunkAccess chunk, ChunkStatus chunkStatus) {
        for (int i = 3; i > 0; i--) {
            this.lastChunkPos[i] = this.lastChunkPos[i - 1];
            this.lastChunkStatus[i] = this.lastChunkStatus[i - 1];
            this.lastChunk[i] = this.lastChunk[i - 1];
        }

        this.lastChunkPos[0] = chunkPos;
        this.lastChunkStatus[0] = chunkStatus;
        this.lastChunk[0] = chunk;
    }

    @Override
    public @Nullable ChunkAccess getChunk(int x, int z, ChunkStatus chunkStatus, boolean requireChunk) {
        // Paper start - rewrite chunk system
        if (chunkStatus == ChunkStatus.FULL) {
            final LevelChunk ret = this.fullChunks.get(ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkKey(x, z));

            if (ret != null) {
                return ret;
            }

            return requireChunk ? this.getChunkFallback(x, z, chunkStatus, requireChunk) : null;
        }

        return this.getChunkFallback(x, z, chunkStatus, requireChunk);
        // Paper end - rewrite chunk system
    }

    @Override
    public @Nullable LevelChunk getChunkNow(int chunkX, int chunkZ) {
        // Paper start - rewrite chunk system
        final LevelChunk ret = this.fullChunks.get(ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkKey(chunkX, chunkZ));
        if (!ca.spottedleaf.moonrise.common.PlatformHooks.get().hasCurrentlyLoadingChunk()) {
            return ret;
        }

        if (ret != null || !ca.spottedleaf.moonrise.common.util.TickThread.isTickThread()) {
            return ret;
        }

        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder holder = ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getChunkTaskScheduler()
            .chunkHolderManager.getChunkHolder(chunkX, chunkZ);
        if (holder == null) {
            return ret;
        }

        return ca.spottedleaf.moonrise.common.PlatformHooks.get().getCurrentlyLoadingChunk(holder.vanillaChunkHolder);
        // Paper end - rewrite chunk system
    }

    private void clearCache() {
        Arrays.fill(this.lastChunkPos, ChunkPos.INVALID_CHUNK_POS);
        Arrays.fill(this.lastChunkStatus, null);
        Arrays.fill(this.lastChunk, null);
    }

    public CompletableFuture<ChunkResult<ChunkAccess>> getChunkFuture(int x, int z, ChunkStatus chunkStatus, boolean requireChunk) {
        if (true) throw new UnsupportedOperationException(); // Folia - region threading
        boolean flag = Thread.currentThread() == this.mainThread;
        CompletableFuture<ChunkResult<ChunkAccess>> chunkFutureMainThread;
        if (flag) {
            chunkFutureMainThread = this.getChunkFutureMainThread(x, z, chunkStatus, requireChunk);
            this.mainThreadProcessor.managedBlock(chunkFutureMainThread::isDone);
        } else {
            chunkFutureMainThread = CompletableFuture.<CompletableFuture<ChunkResult<ChunkAccess>>>supplyAsync(
                    () -> this.getChunkFutureMainThread(x, z, chunkStatus, requireChunk), this.mainThreadProcessor
                )
                .thenCompose(future -> (CompletionStage<ChunkResult<ChunkAccess>>)future);
        }

        return chunkFutureMainThread;
    }

    private CompletableFuture<ChunkResult<ChunkAccess>> getChunkFutureMainThread(int x, int z, ChunkStatus chunkStatus, boolean requireChunk) {
        // Paper start - rewrite chunk system
        ca.spottedleaf.moonrise.common.util.TickThread.ensureTickThread(this.level, x, z, "Scheduling chunk load off-main");

        final int minLevel = ChunkLevel.byStatus(chunkStatus);
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder chunkHolder = ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolder(x, z);

        final boolean needsFullScheduling = chunkStatus == ChunkStatus.FULL && (chunkHolder == null || !chunkHolder.getChunkStatus().isOrAfter(FullChunkStatus.FULL));

        if ((chunkHolder == null || chunkHolder.getTicketLevel() > minLevel || needsFullScheduling) && !requireChunk) {
            return ChunkHolder.UNLOADED_CHUNK_FUTURE;
        }

        final ChunkAccess ifPresent = chunkHolder == null ? null : chunkHolder.getChunkIfPresent(chunkStatus);
        if (needsFullScheduling || ifPresent == null) {
            // schedule
            final CompletableFuture<ChunkResult<ChunkAccess>> ret = new CompletableFuture<>();
            final Consumer<ChunkAccess> complete = (ChunkAccess chunk) -> {
                if (chunk == null) {
                    ret.complete(ChunkHolder.UNLOADED_CHUNK);
                } else {
                    ret.complete(ChunkResult.of(chunk));
                }
            };

            ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getChunkTaskScheduler().scheduleChunkLoad(
                x, z, chunkStatus, true,
                ca.spottedleaf.concurrentutil.util.Priority.HIGHER,
                complete
            );

            return ret;
        } else {
            // can return now
            return CompletableFuture.completedFuture(ChunkResult.of(ifPresent));
        }
        // Paper end - rewrite chunk system
    }

    @Override
    public boolean hasChunk(int x, int z) {
        return this.getChunkNow(x, z) != null; // Paper - rewrite chunk system
    }

    @Override
    public @Nullable LightChunk getChunkForLighting(int chunkX, int chunkZ) {
        // Paper start - rewrite chunk system
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder newChunkHolder = ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolder(chunkX, chunkZ);
        if (newChunkHolder == null) {
            return null;
        }
        return newChunkHolder.getChunkIfPresentUnchecked(ChunkStatus.INITIALIZE_LIGHT.getParent());
        // Paper end - rewrite chunk system
    }

    @Override
    public Level getLevel() {
        return this.level;
    }

    public boolean pollTask() {
        return this.mainThreadProcessor.pollTask();
    }

    public boolean runDistanceManagerUpdates() {
        return ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getChunkTaskScheduler().chunkHolderManager.processTicketUpdates(); // Paper - rewrite chunk system
    }

    public boolean isPositionTicking(long chunkPos) {
        // Paper start - rewrite chunk system
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder newChunkHolder = ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolder(chunkPos);
        return newChunkHolder != null && newChunkHolder.isTickingReady();
        // Paper end - rewrite chunk system
    }

    public void save(boolean flush) {
        // Paper - rewrite chunk system
        this.chunkMap.saveAllChunks(flush);
    }

    @Override
    public void close() throws IOException {
        // CraftBukkit start
        this.close(true);
    }

    public void close(boolean save) throws IOException {
        // CraftBukkit end
        // Paper - rewrite chunk system
        this.dataStorage.close();
        ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getChunkTaskScheduler().chunkHolderManager.close(save, true); // Paper - rewrite chunk system
    }

    // CraftBukkit start - modelled on below
    public void purgeUnload() {
        if (true) return; // Paper - rewrite chunk system
        ProfilerFiller gameprofilerfiller = Profiler.get();

        gameprofilerfiller.push("purge");
        this.ticketStorage.purgeStaleTickets(this.chunkMap);
        this.runDistanceManagerUpdates();
        gameprofilerfiller.popPush("unload");
        this.chunkMap.tick(() -> true);
        gameprofilerfiller.pop();
        this.clearCache();
    }
    // CraftBukkit end

    @Override
    public void tick(BooleanSupplier hasTimeLeft, boolean tickChunks) {
        final ca.spottedleaf.leafprofiler.RegionizedProfiler.Handle foliaProfiler = io.papermc.paper.threadedregions.TickRegionScheduler.getProfiler(); // Folia - profiler
        ProfilerFiller profilerFiller = Profiler.get();
        profilerFiller.push("purge");
        foliaProfiler.startTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.CHUNK_HOLDER_MANAGER_TICK); try { // Folia - profiler
        if (this.level.tickRateManager().runsNormally() || !tickChunks || this.level.spigotConfig.unloadFrozenChunks) { // Spigot
            this.ticketStorage.purgeStaleTickets(this.chunkMap);
        }

        this.runDistanceManagerUpdates();
        } finally { foliaProfiler.stopTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.CHUNK_HOLDER_MANAGER_TICK); } // Folia - profiler
        profilerFiller.popPush("chunks");
        if (tickChunks) {
            foliaProfiler.startTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.PLAYER_CHUNK_LOADER_TICK); try { // Folia - profiler
            ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getPlayerChunkLoader().tick(); // Paper - rewrite chunk system
            } finally { foliaProfiler.stopTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.PLAYER_CHUNK_LOADER_TICK); } // Folia - profiler
            foliaProfiler.startTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.CHUNK_TICK); try { // Folia - profiler
            this.tickChunks();
            } finally { foliaProfiler.stopTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.CHUNK_TICK); } // Folia - profiler
            this.chunkMap.tick();
        }

        profilerFiller.popPush("unload");
        this.chunkMap.tick(hasTimeLeft);
        profilerFiller.pop();
        this.clearCache();
        // Mili start - Global Entities Counter
        if (fun.bm.mili.config.modules.experiment.GlobalEntitiesCounter.enabled) {
            io.papermc.paper.threadedregions.RegionizedWorldData regionizedWorldData = this.level.getCurrentWorldData();
            if (regionizedWorldData != null) {
                if (regionizedWorldData.underGlobalEntitiesCounter) {
                    if (regionizedWorldData.getLocalPlayers().isEmpty()) {
                        fun.bm.mili.utils.EntitiesCounterUtil.onWorldDataUnload(level, regionizedWorldData.uniqueId);
                        regionizedWorldData.underGlobalEntitiesCounter = false;
                    }
                } else {
                    if (!regionizedWorldData.getLocalPlayers().isEmpty()) {
                        regionizedWorldData.underGlobalEntitiesCounter = true;
                    }
                }
            }
        }
        // Mili end - Global Entities Counter
    }

    private void tickChunks() {
        io.papermc.paper.threadedregions.RegionizedWorldData regionizedWorldData = this.level.getCurrentWorldData(); // Folia - region threading
        final ca.spottedleaf.leafprofiler.RegionizedProfiler.Handle foliaProfiler = io.papermc.paper.threadedregions.TickRegionScheduler.getProfiler(); // Folia - profiler
        //long gameTime = this.level.getGameTime(); // Folia - region threading
        long l = 1L; // Folia - region threading
        //this.lastInhabitedUpdate = gameTime; // Folia - region threading
        if (!this.level.isDebug()) {
            ProfilerFiller profilerFiller = Profiler.get();
            profilerFiller.push("pollingChunks");
            if (this.level.tickRateManager().runsNormally()) {
                profilerFiller.push("tickingChunks");
                this.tickChunks(profilerFiller, l);
                profilerFiller.pop();
            }

            foliaProfiler.startTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.BROADCAST_BLOCK_CHANGES); try { // Folia - profiler
            this.broadcastChangedChunks(profilerFiller);
            } finally { foliaProfiler.stopTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.BROADCAST_BLOCK_CHANGES); } // Folia - profiler
            profilerFiller.pop();
        }
    }

    private void broadcastChangedChunks(ProfilerFiller profiler) {
        io.papermc.paper.threadedregions.RegionizedWorldData regionizedWorldData = this.level.getCurrentWorldData(); // Folia - region threading
        profiler.push("broadcast");

        for (ChunkHolder chunkHolder : regionizedWorldData.chunkHoldersToBroadcast) { // Folia - region threading - note: do not need to thread check, as getChunkToSend is only non-null when the chunkholder is loaded
            LevelChunk tickingChunk = chunkHolder.getChunkToSend(); // Paper - rewrite chunk system
            if (tickingChunk != null) {
                chunkHolder.broadcastChanges(tickingChunk);
            }
        }

        regionizedWorldData.chunkHoldersToBroadcast.clear(); // Folia - region threading
        profiler.pop();
    }

    private void tickChunks(ProfilerFiller profiler, long timeInhabited) {
        io.papermc.paper.threadedregions.RegionizedWorldData regionizedWorldData = this.level.getCurrentWorldData(); // Folia - region threading
        final ca.spottedleaf.leafprofiler.RegionizedProfiler.Handle foliaProfiler = io.papermc.paper.threadedregions.TickRegionScheduler.getProfiler(); // Folia - profiler
        profiler.push("naturalSpawnCount");
        int naturalSpawnChunkCount = this.distanceManager.getNaturalSpawnChunkCount();
        // Paper start - Optional per player mob spawns
        NaturalSpawner.SpawnState spawnState;
        foliaProfiler.startTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.MOB_SPAWN_ENTITY_COUNT); try { // Folia - profiler
        if ((this.spawnFriendlies || this.spawnEnemies) && this.level.paperConfig().entities.spawning.perPlayerMobSpawns) { // don't count mobs when animals and monsters are disabled
            // re-set mob counts
            for (ServerPlayer player : this.level.getLocalPlayers()) { // Folia - region threading
                // Paper start - per player mob spawning backoff
                for (int ii = 0; ii < ServerPlayer.MOBCATEGORY_TOTAL_ENUMS; ii++) {
                    player.mobCounts[ii] = 0;

                    int newBackoff = player.mobBackoffCounts[ii] - 1; // TODO make configurable bleed // TODO use nonlinear algorithm?
                    if (newBackoff < 0) {
                        newBackoff = 0;
                    }
                    player.mobBackoffCounts[ii] = newBackoff;
                }
                // Paper end - per player mob spawning backoff
            }
            spawnState = NaturalSpawner.createState(naturalSpawnChunkCount, regionizedWorldData.getLoadedEntities(), this::getFullChunk, null, true); // Folia - region threading - note: function only cares about loaded entities, doesn't need all
        } else {
            // Mili start - Global Entities Counter
            if (fun.bm.mili.config.modules.experiment.GlobalEntitiesCounter.enabled) {
                spawnState = fun.bm.mili.utils.EntitiesCounterUtil.runRemainingTasks(level, regionizedWorldData.getLoadedEntities(), this::getFullChunk, !this.level.paperConfig().entities.spawning.perPlayerMobSpawns ? new LocalMobCapCalculator(this.chunkMap) : null, false);
            } else {
            spawnState = NaturalSpawner.createState(naturalSpawnChunkCount, regionizedWorldData.getLoadedEntities(), this::getFullChunk, !this.level.paperConfig().entities.spawning.perPlayerMobSpawns ? new LocalMobCapCalculator(this.chunkMap) : null, false); // Folia - region threading - note: function only cares about loaded entities, doesn't need all
            }
            // Mili end - Global Entities Counter
        }
        } finally { foliaProfiler.stopTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.MOB_SPAWN_ENTITY_COUNT); } // Folia - profiler
        // Paper end - Optional per player mob spawns
        regionizedWorldData.lastSpawnState = spawnState; // Folia - region threading
        boolean flag = this.level.getGameRules().get(GameRules.SPAWN_MOBS) && !this.level.getLocalPlayers().isEmpty(); // CraftBukkit // Folia - region threading
        int i = this.level.getGameRules().get(GameRules.RANDOM_TICK_SPEED);
        List<MobCategory> filteredSpawningCategories;
        if (spawnState != null && flag && (this.spawnEnemies || this.spawnFriendlies)) { // Paper // Mili - Global Entities Counter
            // Paper start - PlayerNaturallySpawnCreaturesEvent
            for (ServerPlayer entityPlayer : this.level.getLocalPlayers()) { // Folia - region threading
                int chunkRange = Math.min(level.spigotConfig.mobSpawnRange, entityPlayer.getBukkitEntity().getViewDistance());
                chunkRange = Math.min(chunkRange, 8);
                entityPlayer.playerNaturallySpawnedEvent = new com.destroystokyo.paper.event.entity.PlayerNaturallySpawnCreaturesEvent(entityPlayer.getBukkitEntity(), (byte) chunkRange);
                entityPlayer.playerNaturallySpawnedEvent.callEvent();
            }
            // Paper end - PlayerNaturallySpawnCreaturesEvent
            boolean flag1 = this.level.ticksPerSpawnCategory.getLong(org.bukkit.entity.SpawnCategory.ANIMAL) != 0L && this.level.getRedstoneGameTime() % this.level.ticksPerSpawnCategory.getLong(org.bukkit.entity.SpawnCategory.ANIMAL) == 0L; // CraftBukkit // Folia - region threading
            filteredSpawningCategories = NaturalSpawner.getFilteredSpawningCategories(spawnState, this.spawnFriendlies, this.spawnEnemies, flag1, this.level); // CraftBukkit
        } else {
            filteredSpawningCategories = List.of();
        }

        List<LevelChunk> list = regionizedWorldData.temporaryChunkTickList; // Folia - region threading

        try {
            profiler.popPush("filteringSpawningChunks");
            foliaProfiler.startTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.CHUNK_SPAWN_COLLECT_CHUNKS); try { // Folia - profiler
            this.chunkMap.collectSpawningChunks(list);
            } finally { foliaProfiler.stopTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.CHUNK_SPAWN_COLLECT_CHUNKS); } // Folia - profiler
            profiler.popPush("shuffleSpawningChunks");
            // Paper start - chunk tick iteration optimisation
            this.shuffleRandom.setSeed(this.level.random.nextLong());
            if (!this.level.paperConfig().entities.spawning.perPlayerMobSpawns) Util.shuffle(list, this.shuffleRandom); // Paper - Optional per player mob spawns; do not need this when per-player is enabled
            // Paper end - chunk tick iteration optimisation
            profiler.popPush("tickSpawningChunks");

            foliaProfiler.startTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.SPAWN_TICK); try { // Folia - profiler
            foliaProfiler.addCounter(ca.spottedleaf.leafprofiler.LProfilerRegistry.SPAWN_CHUNK_COUNT, (long)list.size()); // Folia - profiler
            for (LevelChunk levelChunk : list) {
                this.tickSpawningChunk(levelChunk, timeInhabited, filteredSpawningCategories, spawnState);
            }
            } finally { foliaProfiler.stopTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.SPAWN_TICK); } // Folia - profiler
        } finally {
            list.clear();
        }

        profiler.popPush("tickTickingChunks");
        this.iterateTickingChunksFaster(); // Paper - chunk tick iteration optimizations
        if (flag) {
            profiler.popPush("customSpawners");
            foliaProfiler.startTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.MISC_MOB_SPAWN_TICK); try { // Folia - profiler
            this.level.tickCustomSpawners(this.spawnEnemies);
            } finally { foliaProfiler.stopTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.MISC_MOB_SPAWN_TICK); } // Folia - profiler
        }

        profiler.pop();
    }

    private void tickSpawningChunk(LevelChunk chunk, long timeInhabited, List<MobCategory> spawnCategories, NaturalSpawner.SpawnState spawnState) {
        ChunkPos pos = chunk.getPos();
        chunk.incrementInhabitedTime(timeInhabited);
        if (true) { // Paper - rewrite chunk system
            this.level.tickThunder(chunk);
        }

        if (spawnState != null && !spawnCategories.isEmpty()) { // Mili - Global Entities Counter
            if (this.level.getWorldBorder().isWithinBounds(pos)) { // Paper - rewrite chunk system
                NaturalSpawner.spawnForChunk(this.level, chunk, spawnState, spawnCategories);
            }
        }
    }

    private void getFullChunk(long chunkPos, Consumer<LevelChunk> fullChunkGetter) {
        // Paper start - rewrite chunk system
        // note: bypass currentlyLoaded from getChunkNow
        final LevelChunk fullChunk = this.fullChunks.get(chunkPos);
        if (fullChunk != null) {
            fullChunkGetter.accept(fullChunk);
        }
        // Paper end - rewrite chunk system
    }

    @Override
    public String gatherStats() {
        return Integer.toString(this.getLoadedChunksCount());
    }

    @VisibleForTesting
    public int getPendingTasksCount() {
        return this.mainThreadProcessor.getPendingTasksCount();
    }

    public ChunkGenerator getGenerator() {
        su.plo.matter.Globals.setupGlobals(level); // Leaf - Matter - Secure Seed
        return this.chunkMap.generator();
    }

    public ChunkGeneratorStructureState getGeneratorState() {
        return this.chunkMap.generatorState();
    }

    public RandomState randomState() {
        return this.chunkMap.randomState();
    }

    @Override
    public int getLoadedChunksCount() {
        return this.chunkMap.size();
    }

    public void blockChanged(BlockPos pos) {
        int sectionPosX = SectionPos.blockToSectionCoord(pos.getX());
        int sectionPosZ = SectionPos.blockToSectionCoord(pos.getZ());
        ChunkHolder visibleChunkIfPresent = this.getVisibleChunkIfPresent(ChunkPos.asLong(sectionPosX, sectionPosZ));
        if (visibleChunkIfPresent != null && visibleChunkIfPresent.blockChanged(pos)) {
            this.level.getCurrentWorldData().chunkHoldersToBroadcast.add(visibleChunkIfPresent); // Folia - region threading
        }
    }

    @Override
    public void onLightUpdate(LightLayer lightLayer, SectionPos pos) {
        Runnable run = () -> { // Folia - region threading
            ChunkHolder visibleChunkIfPresent = this.getVisibleChunkIfPresent(pos.chunk().toLong());
            if (visibleChunkIfPresent != null && visibleChunkIfPresent.sectionLightChanged(lightLayer, pos.y())) {
                ServerChunkCache.this.level.getCurrentWorldData().chunkHoldersToBroadcast.add(visibleChunkIfPresent); // Folia - region threading
            }
        }; // Folia - region threading
        // Folia start - region threading
        io.papermc.paper.threadedregions.RegionizedServer.getInstance().taskQueue.queueChunkTask(
                this.level, pos.getX(), pos.getZ(), run
        );
        // Folia end - region threading
    }

    public boolean hasActiveTickets() {
        return this.ticketStorage.shouldKeepDimensionActive();
    }

    public void addTicket(Ticket ticket, ChunkPos chunkPos) {
        this.ticketStorage.addTicket(ticket, chunkPos);
    }

    public CompletableFuture<?> addTicketAndLoadWithRadius(TicketType ticketType, ChunkPos chunkPos, int radius) {
        // Paper start - rewrite chunk system
        return ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getChunkTaskScheduler().chunkHolderManager.addTicketAndLoadWithRadius(
            ticketType, chunkPos, radius, ChunkStatus.FULL, ca.spottedleaf.concurrentutil.util.Priority.NORMAL
        );
        // Paper end - rewrite chunk system
    }

    public void addTicketWithRadius(TicketType ticketType, ChunkPos chunkPos, int radius) {
        this.ticketStorage.addTicketWithRadius(ticketType, chunkPos, radius);
    }

    public void removeTicketWithRadius(TicketType ticketType, ChunkPos chunkPos, int radius) {
        this.ticketStorage.removeTicketWithRadius(ticketType, chunkPos, radius);
    }

    @Override
    public boolean updateChunkForced(ChunkPos chunkPos, boolean add) {
        return this.ticketStorage.updateChunkForced(chunkPos, add);
    }

    @Override
    public LongSet getForceLoadedChunks() {
        return this.ticketStorage.getForceLoadedChunks();
    }

    public void move(ServerPlayer player) {
        if (!player.isRemoved()) {
            this.chunkMap.move(player);
            if (player.isReceivingWaypoints()) {
                this.level.getWaypointManager().updatePlayer(player);
            }
        }
    }

    public void removeEntity(Entity entity) {
        this.chunkMap.removeEntity(entity);
    }

    public void addEntity(Entity entity) {
        this.chunkMap.addEntity(entity);
    }

    public void sendToTrackingPlayersAndSelf(Entity entity, Packet<? super ClientGamePacketListener> packet) {
        this.chunkMap.sendToTrackingPlayersAndSelf(entity, packet);
    }

    public void sendToTrackingPlayers(Entity entity, Packet<? super ClientGamePacketListener> packet) {
        this.chunkMap.sendToTrackingPlayers(entity, packet);
    }

    public void setViewDistance(int viewDistance) {
        this.chunkMap.setServerViewDistance(viewDistance);
    }

    // Paper start - rewrite chunk system
    public void setSendViewDistance(int viewDistance) {
        ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.level).moonrise$getPlayerChunkLoader().setSendDistance(viewDistance);
    }
    // Paper end - rewrite chunk system

    public void setSimulationDistance(int simulationDistance) {
        this.distanceManager.updateSimulationDistance(simulationDistance);
    }

    @Override
    public void setSpawnSettings(boolean spawnSettings) {
        // CraftBukkit start
        this.setSpawnSettings(spawnSettings, this.spawnFriendlies);
    }
    public void setSpawnSettings(boolean spawnSettings, boolean spawnFriendlies) {
        this.spawnEnemies = spawnSettings;
        this.spawnFriendlies = spawnFriendlies;
        // CraftBukkit end
    }

    public String getChunkDebugData(ChunkPos chunkPos) {
        return this.chunkMap.getChunkDebugData(chunkPos);
    }

    public DimensionDataStorage getDataStorage() {
        return this.dataStorage;
    }

    public PoiManager getPoiManager() {
        return this.chunkMap.getPoiManager();
    }

    public ChunkScanAccess chunkScanner() {
        return this.chunkMap.chunkScanner();
    }

    @VisibleForDebug
    public NaturalSpawner.@Nullable SpawnState getLastSpawnState() {
        io.papermc.paper.threadedregions.RegionizedWorldData worldData = this.level.getCurrentWorldData(); // Folia - region threading
        return worldData == null ? null : worldData.lastSpawnState; // Folia - region threading
    }

    public void deactivateTicketsOnClosing() {
        this.ticketStorage.deactivateTicketsOnClosing();
    }

    public void onChunkReadyToSend(ChunkHolder chunkHolder) {
        if (chunkHolder.hasChangesToBroadcast()) {
            throw new UnsupportedOperationException(); // Folia - region threading
        }
    }

    public final class MainThreadExecutor extends BlockableEventLoop<Runnable> {
        MainThreadExecutor(final Level level) {
            super("Chunk source main thread executor for " + level.dimension().identifier());
        }

        @Override
        public void managedBlock(BooleanSupplier isDone) {
            super.managedBlock(() -> MinecraftServer.throwIfFatalException() && isDone.getAsBoolean());
        }

        @Override
        public Runnable wrapRunnable(Runnable runnable) {
            return runnable;
        }

        @Override
        protected boolean shouldRun(Runnable runnable) {
            return true;
        }

        @Override
        protected boolean scheduleExecutables() {
            return true;
        }

        @Override
        protected Thread getRunningThread() {
            return ServerChunkCache.this.mainThread;
        }

        // Folia start - region threading
        @Override
        public <V> CompletableFuture<V> submit(Supplier<V> task) {
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
        public void schedule(Runnable runnable) {
            if (true) {
                throw new UnsupportedOperationException();
            }
            super.schedule(runnable);
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

        @Override
        public void executeIfPossible(Runnable runnable) {
            if (true) {
                throw new UnsupportedOperationException();
            }
            super.executeIfPossible(runnable);
        }
        // Folia end - region threading

        @Override
        protected void doRunTask(Runnable task) {
            if (true) throw new UnsupportedOperationException(); // Folia - region threading
            Profiler.get().incrementCounter("runTask");
            super.doRunTask(task);
        }

        @Override
        public boolean pollTask() {
            // Folia start - region threading
            if (ServerChunkCache.this.level != io.papermc.paper.threadedregions.TickRegionScheduler.getCurrentRegionizedWorldData().world) {
                throw new IllegalStateException("Polling tasks from non-owned region");
            }
            // Folia end - region threading
            // Paper start - rewrite chunk system
            final ServerChunkCache serverChunkCache = ServerChunkCache.this;
            if (serverChunkCache.runDistanceManagerUpdates()) {
                return true;
            } else {
                return io.papermc.paper.threadedregions.TickRegionScheduler.getCurrentRegion().getData().getTaskQueueData().executeChunkTask(); // Folia - region threading
            }
            // Paper end - rewrite chunk system
        }
    }
}
