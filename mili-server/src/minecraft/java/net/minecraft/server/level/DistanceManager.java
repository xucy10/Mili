package net.minecraft.server.level;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteMaps;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntMaps;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.Long2ByteMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.SharedConstants;
import net.minecraft.core.SectionPos;
import net.minecraft.util.TriState;
import net.minecraft.util.thread.TaskScheduler;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public abstract class DistanceManager implements ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkSystemDistanceManager, ca.spottedleaf.moonrise.patches.chunk_tick_iteration.ChunkTickDistanceManager { // Paper - rewrite chunk system // Paper - chunk tick iteration optimisation
    private static final Logger LOGGER = LogUtils.getLogger();
    static final int PLAYER_TICKET_LEVEL = ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING);
    final Long2ObjectMap<ObjectSet<ServerPlayer>> playersPerChunk = new Long2ObjectOpenHashMap<>();
    // Paper - rewrite chunk system
    public final TicketStorage ticketStorage;
    // Paper - chunk tick iteration optimisation
    // Paper - rewrite chunk system

    protected DistanceManager(TicketStorage ticketStorage, Executor dispatcher, Executor mainThreadExecutor) {
        this.ticketStorage = ticketStorage;
        // Paper - rewrite chunk system
        TaskScheduler<Runnable> taskScheduler = TaskScheduler.wrapExecutor("player ticket throttler", mainThreadExecutor);
        this.ticketStorage.moonrise$setChunkMap(this.moonrise$getChunkMap()); // Paper - rewrite chunk system
    }

    // Paper start - rewrite chunk system
    @Override
    public final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager moonrise$getChunkHolderManager() {
        return ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.moonrise$getChunkMap().level).moonrise$getChunkTaskScheduler().chunkHolderManager;
    }
    // Paper end - rewrite chunk system
    // Paper start - chunk tick iteration optimisation
    // Folia - move to regionized world data
    // Note: Cannot do narrow tracking on Paper due to custom spawn range

    @Override
    public final void moonrise$addPlayer(final ServerPlayer player, final SectionPos pos) {
        this.moonrise$getChunkMap().level.getCurrentWorldData().spawnChunkTracker.add(player, pos.x(), pos.z(), ca.spottedleaf.moonrise.patches.chunk_tick_iteration.ChunkTickConstants.PLAYER_SPAWN_TRACK_RANGE); // Folia - region threading
        // Note: Cannot do narrow tracking on Paper due to custom spawn range
    }

    @Override
    public final void moonrise$removePlayer(final ServerPlayer player, final SectionPos pos) {
        this.moonrise$getChunkMap().level.getCurrentWorldData().spawnChunkTracker.remove(player); // Folia - region threading
        // Note: Cannot do narrow tracking on Paper due to custom spawn range
    }

    @Override
    public final void moonrise$updatePlayer(final ServerPlayer player,
                                            final SectionPos oldPos, final SectionPos newPos,
                                            final boolean oldIgnore, final boolean newIgnore) {
        if (newIgnore) {
            this.moonrise$getChunkMap().level.getCurrentWorldData().spawnChunkTracker.remove(player); // Folia - region threading
            // Note: Cannot do narrow tracking on Paper due to custom spawn range
        } else {
            this.moonrise$getChunkMap().level.getCurrentWorldData().spawnChunkTracker.addOrUpdate(player, newPos.x(), newPos.z(), ca.spottedleaf.moonrise.patches.chunk_tick_iteration.ChunkTickConstants.PLAYER_SPAWN_TRACK_RANGE); // Folia - region threading
            // Note: Cannot do narrow tracking on Paper due to custom spawn range
        }
    }

    @Override
    public final boolean moonrise$hasAnyNearbyNarrow(final int chunkX, final int chunkZ) {
        throw new UnsupportedOperationException(); // Note: Cannot do narrow tracking on Paper due to custom spawn range
    }

    // Paper end - chunk tick iteration optimisation
    protected abstract boolean isChunkToRemove(long chunkPos);

    protected abstract @Nullable ChunkHolder getChunk(long chunkPos);

    protected abstract @Nullable ChunkHolder updateChunkScheduling(long chunkPos, int newLevel, @Nullable ChunkHolder holder, int oldLevel);

    public boolean runAllUpdates(ChunkMap chunkMap) {
        return this.moonrise$getChunkHolderManager().processTicketUpdates(); // Paper - rewrite chunk system
    }

    public void addPlayer(SectionPos sectionPos, ServerPlayer player) {
        ChunkPos chunkPos = sectionPos.chunk();
        long packedChunkPos = chunkPos.toLong();
        this.playersPerChunk.computeIfAbsent(packedChunkPos, l -> new ObjectOpenHashSet<>()).add(player);
        // Paper - chunk tick iteration optimisation
        // Paper - rewrite chunk system
    }

    public void removePlayer(SectionPos sectionPos, ServerPlayer player) {
        ChunkPos chunkPos = sectionPos.chunk();
        long packedChunkPos = chunkPos.toLong();
        ObjectSet<ServerPlayer> set = this.playersPerChunk.get(packedChunkPos);
        // Paper start - some state corruption happens here, don't crash, clean up gracefully
        if (set != null) set.remove(player);
        if (set == null || set.isEmpty()) {
        // Paper end - some state corruption happens here, don't crash, clean up gracefully
            this.playersPerChunk.remove(packedChunkPos);
            // Paper - chunk tick iteration optimisation
            // Paper - rewrite chunk system
        }
    }

    private int getPlayerTicketLevel() {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public boolean inEntityTickingRange(long chunkPos) {
        // Paper start - rewrite chunk system
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder chunkHolder = this.moonrise$getChunkHolderManager().getChunkHolder(chunkPos);
        return chunkHolder != null && chunkHolder.isEntityTickingReady();
        // Paper end - rewrite chunk system
    }

    public boolean inBlockTickingRange(long chunkPos) {
        // Paper start - rewrite chunk system
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder chunkHolder = this.moonrise$getChunkHolderManager().getChunkHolder(chunkPos);
        return chunkHolder != null && chunkHolder.isTickingReady();
        // Paper end - rewrite chunk system
    }

    public int getChunkLevel(long chunkPos, boolean simulate) {
        // Paper start - rewrite chunk system
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder chunkHolder = this.moonrise$getChunkHolderManager().getChunkHolder(chunkPos);
        return chunkHolder == null ? ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager.MAX_TICKET_LEVEL + 1 : chunkHolder.getTicketLevel();
        // Paper end - rewrite chunk system
    }

    protected void updatePlayerTickets(int viewDistance) {
        this.moonrise$getChunkMap().setServerViewDistance(viewDistance); // Paper - rewrite chunk system
    }

    public void updateSimulationDistance(int simulationDistance) {
        // Paper start - rewrite chunk system
        // note: vanilla does not clamp to 0, but we do simply because we need a min of 0
        final int clamped = net.minecraft.util.Mth.clamp(simulationDistance, 0, ca.spottedleaf.moonrise.common.util.MoonriseConstants.MAX_VIEW_DISTANCE);

        ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.moonrise$getChunkMap().level).moonrise$getPlayerChunkLoader().setTickDistance(clamped);
        // Paper end - rewrite chunk system
    }

    public int getNaturalSpawnChunkCount() {
        return this.moonrise$getChunkMap().level.getCurrentWorldData().spawnChunkTracker.getTotalPositions(); // Paper - chunk tick iteration optimisation // Folia - region threading
    }

    public TriState hasPlayersNearby(long chunkPos) {
        // Note: Cannot do narrow tracking on Paper due to custom spawn range // Paper - chunk tick iteration optimisation
        return this.moonrise$getChunkMap().level.getCurrentWorldData().spawnChunkTracker.hasObjectsNear(ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkX(chunkPos), ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkZ(chunkPos)) ? net.minecraft.util.TriState.DEFAULT : net.minecraft.util.TriState.FALSE; // Paper - chunk tick iteration optimisation // Folia - region threading
    }

    public void forEachEntityTickingChunk(LongConsumer action) {
        // Paper start - rewrite chunk system
        final ca.spottedleaf.moonrise.common.list.ReferenceList<net.minecraft.world.level.chunk.LevelChunk> chunks = ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.moonrise$getChunkMap().level).moonrise$getEntityTickingChunks();
        final LevelChunk[] raw = chunks.getRawDataUnchecked();
        final int size = chunks.size();

        java.util.Objects.checkFromToIndex(0, size, raw.length);
        for (int i = 0; i < size; ++i) {
            final LevelChunk chunk = raw[i];

            action.accept(ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkKey(chunk.getPos()));
        }
        // Paper end - rewrite chunk system
    }

    public LongIterator getSpawnCandidateChunks() {
        return this.moonrise$getChunkMap().level.getCurrentWorldData().spawnChunkTracker.getPositions().iterator(); // Paper - chunk tick iteration optimisation // Folia - region threading
    }

    public String getDebugStatus() {
        return "N/A"; // Paper - rewrite chunk system
    }

    public boolean hasTickets() {
        return this.ticketStorage.hasTickets();
    }

    class FixedPlayerDistanceChunkTracker extends ChunkTracker {
        protected final Long2ByteMap chunks = new Long2ByteOpenHashMap();
        protected final int maxDistance;

        protected FixedPlayerDistanceChunkTracker(final int maxDistance) {
            super(maxDistance + 2, 16, 256);
            this.maxDistance = maxDistance;
            this.chunks.defaultReturnValue((byte)(maxDistance + 2));
        }

        @Override
        protected int getLevel(long sectionPos) {
            return this.chunks.get(sectionPos);
        }

        @Override
        protected void setLevel(long sectionPos, int level) {
            byte b;
            if (level > this.maxDistance) {
                b = this.chunks.remove(sectionPos);
            } else {
                b = this.chunks.put(sectionPos, (byte)level);
            }

            this.onLevelChange(sectionPos, b, level);
        }

        protected void onLevelChange(long chunkPos, int oldLevel, int newLevel) {
        }

        @Override
        protected int getLevelFromSource(long pos) {
            return this.havePlayer(pos) ? 0 : Integer.MAX_VALUE;
        }

        private boolean havePlayer(long chunkPos) {
            ObjectSet<ServerPlayer> set = DistanceManager.this.playersPerChunk.get(chunkPos);
            return set != null && !set.isEmpty();
        }

        public void runAllUpdates() {
            this.runUpdates(Integer.MAX_VALUE);
        }
    }

/*  // Paper - rewrite chunk system
    class PlayerTicketTracker extends DistanceManager.FixedPlayerDistanceChunkTracker {
        private int viewDistance;
        private final Long2IntMap queueLevels = Long2IntMaps.synchronize(new Long2IntOpenHashMap());
        private final LongSet toUpdate = new LongOpenHashSet();

        protected PlayerTicketTracker(final int maxDistance) {
            super(maxDistance);
            this.viewDistance = 0;
            this.queueLevels.defaultReturnValue(maxDistance + 2);
        }

        @Override
        protected void onLevelChange(long chunkPos, int oldLevel, int newLevel) {
            this.toUpdate.add(chunkPos);
        }

        public void updateViewDistance(int viewDistance) {
            for (Entry entry : this.chunks.long2ByteEntrySet()) {
                byte byteValue = entry.getByteValue();
                long longKey = entry.getLongKey();
                this.onLevelChange(longKey, byteValue, this.haveTicketFor(byteValue), byteValue <= viewDistance);
            }

            this.viewDistance = viewDistance;
        }

        private void onLevelChange(long chunkPos, int level, boolean hadTicket, boolean hasTicket) {
            if (hadTicket != hasTicket) {
                Ticket ticket = new Ticket(TicketType.PLAYER_LOADING, DistanceManager.PLAYER_TICKET_LEVEL);
                if (hasTicket) {
                    DistanceManager.this.ticketDispatcher.submit(() -> DistanceManager.this.mainThreadExecutor.execute(() -> {
                        if (this.haveTicketFor(this.getLevel(chunkPos))) {
                            DistanceManager.this.ticketStorage.addTicket(chunkPos, ticket);
                            DistanceManager.this.ticketsToRelease.add(chunkPos);
                        } else {
                            DistanceManager.this.ticketDispatcher.release(chunkPos, () -> {}, false);
                        }
                    }), chunkPos, () -> level);
                } else {
                    DistanceManager.this.ticketDispatcher
                        .release(
                            chunkPos,
                            () -> DistanceManager.this.mainThreadExecutor.execute(() -> DistanceManager.this.ticketStorage.removeTicket(chunkPos, ticket)),
                            true
                        );
                }
            }
        }

        @Override
        public void runAllUpdates() {
            super.runAllUpdates();
            if (!this.toUpdate.isEmpty()) {
                LongIterator longIterator = this.toUpdate.iterator();

                while (longIterator.hasNext()) {
                    long l = longIterator.nextLong();
                    int i = this.queueLevels.get(l);
                    int level = this.getLevel(l);
                    if (i != level) {
                        DistanceManager.this.ticketDispatcher.onLevelChange(new ChunkPos(l), () -> this.queueLevels.get(l), level, i1 -> {
                            if (i1 >= this.queueLevels.defaultReturnValue()) {
                                this.queueLevels.remove(l);
                            } else {
                                this.queueLevels.put(l, i1);
                            }
                        });
                        this.onLevelChange(l, level, this.haveTicketFor(i), this.haveTicketFor(level));
                    }
                }

                this.toUpdate.clear();
            }
        }

        private boolean haveTicketFor(int level) {
            return level <= this.viewDistance;
        }
    }*/  // Paper - rewrite chunk system
}
