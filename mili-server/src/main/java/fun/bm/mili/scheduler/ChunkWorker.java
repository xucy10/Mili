package fun.bm.mili.scheduler;

import ca.spottedleaf.moonrise.common.util.TickThread;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

public final class ChunkWorker {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final ServerLevel level;
    private final int chunkX;
    private final int chunkZ;
    private final ChunkBorderCache borderCache;
    private final ChunkIndependentScheduler scheduler;

    private volatile LevelChunk chunk;
    private volatile boolean released;
    private volatile boolean highInteraction;

    // Two-phase commit flags
    private volatile boolean phase1Complete;  // border captured
    private volatile boolean phase2Ready;     // ready for cross-chunk injection

    private final AtomicBoolean ticking = new AtomicBoolean(false);
    private final AtomicLong lastTickNanos = new AtomicLong(0);

    // Dedicated thread for this worker (null if using thread pool)
    private volatile Thread workerThread;

    public ChunkWorker(ServerLevel level, int chunkX, int chunkZ,
                       ChunkIndependentScheduler scheduler) {
        this.level = level;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.borderCache = new ChunkBorderCache(chunkX, chunkZ);
        this.scheduler = scheduler;
    }

    /**
     * Execute a full tick cycle with two-phase commit protocol.
     *
     * Phase 1: Capture border state (reads only, no writes to neighbors)
     * Phase 2: Execute chunk tick (mutates own chunk only)
     * Phase 3: Signal cross-chunk readiness
     * Phase 4: Accept injected border updates from CrossChunkBus
     */
    public void tick() {
        if (!ticking.compareAndSet(false, true)) return;
        try {
            if (released || chunk == null || !chunk.isLoaded()) return;

            // Register current thread for TickThread.isTickThreadFor compatibility
            Thread currentThread = Thread.currentThread();
            this.workerThread = currentThread;
            if (scheduler != null) {
                scheduler.registerWorkerThread(currentThread, this);
            }

            try {
                // Phase 1: Capture border state (read-only)
                borderCache.captureBorderState(chunk);
                phase1Complete = true;

                // Classify: high-interaction chunks use strict serialization
                highInteraction = borderCache.isHighInteraction();

                // Phase 2: Execute chunk tick
                if (chunk != null && chunk.isLoaded()) {
                    // Hook into Minecraft chunk tick via EntityTickList / BlockEntityTicker
                    // This is the integration point patched into ServerLevel / ChunkMap
                    chunk.tick(level);
                }

                // Phase 3: Signal cross-chunk readiness
                phase2Ready = true;

                // Phase 4: Process any border updates queued by CrossChunkBus
                // for low-interaction chunks only (high-interaction chunks use
                // Folia region fallback which handles this natively)
                if (!highInteraction) {
                    scheduler.getCrossChunkBus().processBorderUpdates(this);
                }

            } finally {
                // Clear thread registration
                if (scheduler != null) {
                    scheduler.unregisterWorkerThread(currentThread);
                }
                this.workerThread = null;
            }

            lastTickNanos.set(System.nanoTime());

        } catch (Exception e) {
            LOGGER.error("ChunkWorker tick error at ({}, {}): {}", chunkX, chunkZ, e.getMessage());
        } finally {
            ticking.set(false);
        }
    }

    public void resetForNextTick() {
        phase1Complete = false;
        phase2Ready = false;
    }

    public boolean waitForTickCompletion(long timeoutNanos) {
        long deadline = System.nanoTime() + timeoutNanos;
        while (ticking.get() && System.nanoTime() < deadline) {
            LockSupport.parkNanos("chunk-worker-wait", 100_000L);
        }
        return !ticking.get();
    }

    /** Assign (or re-assign) the chunk this worker is responsible for. */
    public void assignChunk(LevelChunk newChunk) {
        this.chunk = Objects.requireNonNull(newChunk);
        this.released = false;
        this.borderCache.markDirty();
        resetForNextTick();
    }

    /** Release this worker; it will no longer tick. */
    public void release() {
        this.released = true;
        this.chunk = null;
    }

    // ---- Getters ----

    public ServerLevel getLevel() { return level; }
    public int getChunkX() { return chunkX; }
    public int getChunkZ() { return chunkZ; }
    public LevelChunk getChunk() { return chunk; }
    public ChunkBorderCache getBorderCache() { return borderCache; }
    public boolean isHighInteraction() { return highInteraction; }
    public boolean isReleased() { return released; }
    public boolean isPhase1Complete() { return phase1Complete; }
    public boolean isPhase2Ready() { return phase2Ready; }
    public boolean isTicking() { return ticking.get(); }
    public long getLastTickNanos() { return lastTickNanos.get(); }

    @Override
    public String toString() {
        return "ChunkWorker[" + chunkX + "," + chunkZ + "]";
    }
}
