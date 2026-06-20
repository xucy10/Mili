package fun.bm.mili.scheduler;

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
    private volatile boolean borderCaptured;

    private final AtomicBoolean capturing = new AtomicBoolean(false);
    private final AtomicLong lastCaptureNanos = new AtomicLong(0);

    public ChunkWorker(ServerLevel level, int chunkX, int chunkZ,
                       ChunkIndependentScheduler scheduler) {
        this.level = level;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.borderCache = new ChunkBorderCache(chunkX, chunkZ);
        this.scheduler = scheduler;
    }

    /**
     * Phase 1 only: capture border state.
     *
     * SAFETY: This is a READ-ONLY operation — it reads block states at the
     * chunk border but writes nothing to the level or entities. It can safely
     * run from any thread.
     *
     * The actual chunk tick (entity tick, block tick, block entity tick)
     * MUST remain on the Folia region thread. CIS does NOT replace Folia's
     * region tick; it only analyzes chunk interactions to provide scheduling
     * hints.
     *
     * Entity bugs (teleporting, spawn failure, no display, knockback
     * anomalies) were caused by calling chunk.tick() from a non-owning
     * thread, which corrupts Folia's region-local entity state.
     */
    public void captureBorder() {
        if (!capturing.compareAndSet(false, true)) return;
        try {
            if (released || chunk == null) return;

            borderCache.captureBorderState(chunk);
            highInteraction = borderCache.isHighInteraction();
            borderCaptured = true;
            lastCaptureNanos.set(System.nanoTime());

        } catch (Exception e) {
            LOGGER.error("ChunkWorker border capture error at ({}, {}): {}", chunkX, chunkZ, e.getMessage());
        } finally {
            capturing.set(false);
        }
    }

    public void resetForNextTick() {
        borderCaptured = false;
    }

    public boolean waitForCapture(long timeoutNanos) {
        long deadline = System.nanoTime() + timeoutNanos;
        while (capturing.get() && System.nanoTime() < deadline) {
            LockSupport.parkNanos("chunk-worker-wait", 100_000L);
        }
        return !capturing.get();
    }

    public void assignChunk(LevelChunk newChunk) {
        this.chunk = Objects.requireNonNull(newChunk);
        this.released = false;
        this.borderCache.markDirty();
        resetForNextTick();
    }

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
    public boolean isBorderCaptured() { return borderCaptured; }
    public boolean isCapturing() { return capturing.get(); }
    public long getLastCaptureNanos() { return lastCaptureNanos.get(); }

    @Override
    public String toString() {
        return "ChunkWorker[" + chunkX + "," + chunkZ + "]";
    }
}
