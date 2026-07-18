package fun.bm.mili.chunk;

import com.mojang.logging.LogUtils;
import fun.bm.mili.config.modules.optimizations.ChunkSystemConfig;
import org.bukkit.Chunk;
import org.bukkit.World;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

final class AsyncChunkProcessor {

    private final ConcurrentLinkedQueue<AsyncChunkOperation> queue = new ConcurrentLinkedQueue<>();
    private final AtomicLong totalOps = new AtomicLong(0);

    AsyncChunkProcessor() {}

    void processQueue() {
        int processed = 0;
        long deadline = System.nanoTime() + ChunkSystemConfig.asyncTimeBudgetNs;

        while (processed < ChunkSystemConfig.maxAsyncOpsPerCycle && System.nanoTime() < deadline) {
            AsyncChunkOperation op = queue.poll();
            if (op == null) break;

            try {
                op.execute();
                totalOps.incrementAndGet();
                processed++;
            } catch (Exception e) {
                LogUtils.getLogger().warn("[Mili] Async chunk operation failed", e);
            }
        }
    }

    boolean enqueue(AsyncChunkOperation operation) {
        if (queue.size() < ChunkSystemConfig.maxAsyncQueueSize) {
            queue.add(operation);
            return true;
        }
        return false;
    }

    int queueSize() {
        return queue.size();
    }

    long getTotalOps() {
        return totalOps.get();
    }

    void clear() {
        queue.clear();
    }

    CompletableFuture<Void> preloadArea(World world, int centerX, int centerZ, int radius) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        boolean accepted = enqueue(new AsyncChunkOperation() {
            @Override
            public void execute() {
                try {
                    int chunksLoaded = 0;
                    for (int dx = -radius; dx <= radius; dx++) {
                        for (int dz = -radius; dz <= radius; dz++) {
                            Chunk chunk = world.getChunkAt(centerX + dx, centerZ + dz);
                            if (chunk != null && chunk.isLoaded()) {
                                chunksLoaded++;
                            }
                        }
                    }
                    future.complete(null);
                    LogUtils.getLogger().debug(
                            "[Mili] Preloaded {} chunks around ({}, {})",
                            chunksLoaded, centerX, centerZ
                    );
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }

            @Override
            public void onRejected() {
                future.completeExceptionally(new RuntimeException("Async queue full"));
            }
        });

        if (!accepted) {
            future.completeExceptionally(new RuntimeException("Async queue full"));
        }

        return future;
    }

    interface AsyncChunkOperation {
        void execute();
        default void onRejected() {}
    }
}