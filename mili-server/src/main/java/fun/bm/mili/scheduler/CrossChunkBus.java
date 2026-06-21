package fun.bm.mili.scheduler;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

public final class CrossChunkBus {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long COORDINATOR_POLL_NANOS = 50_000_000L;

    private final ServerLevel level;
    private final ConcurrentMap<Long, List<Runnable>> borderUpdateQueue = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, List<Runnable>> injectionQueue = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private Thread coordinatorThread;
    private volatile boolean shutdown;
    private volatile long timeoutNanos = 50_000_000L;

    public CrossChunkBus(ServerLevel level) {
        this.level = level;
    }

    public void setTimeoutNanos(long nanos) {
        this.timeoutNanos = nanos;
    }

    // ======================== Lifecycle ========================

    public void startCoordinator() {
        if (running.compareAndSet(false, true)) {
            shutdown = false;
            coordinatorThread = new Thread(this::coordinatorLoop, "mili-cross-chunk-coordinator");
            coordinatorThread.setDaemon(true);
            coordinatorThread.start();
            LOGGER.debug("CrossChunkBus coordinator started");
        }
    }

    public void stopCoordinator() {
        shutdown = true;
        if (coordinatorThread != null) {
            coordinatorThread.interrupt();
        }
    }

    // ======================== Coordinator Loop ========================

    private void coordinatorLoop() {
        while (!shutdown) {
            try {
                drainBorderUpdates();
                deliverInjections();
                LockSupport.parkNanos(COORDINATOR_POLL_NANOS);
                if (Thread.interrupted()) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } catch (Exception e) {
                LOGGER.error("CrossChunkBus coordinator error", e);
            }
        }
        running.set(false);
    }

    private void drainBorderUpdates() {
        if (borderUpdateQueue.isEmpty()) return;

        for (var iter = borderUpdateQueue.entrySet().iterator(); iter.hasNext();) {
            var entry = iter.next();
            long targetKey = entry.getKey();
            List<Runnable> tasks = entry.getValue();
            if (tasks == null || tasks.isEmpty()) {
                iter.remove();
                continue;
            }
            injectionQueue.computeIfAbsent(targetKey, k -> Collections.synchronizedList(new ArrayList<>()))
                .addAll(tasks);
            iter.remove();
        }
    }

    private void deliverInjections() {
        if (injectionQueue.isEmpty()) return;

        for (var iter = injectionQueue.entrySet().iterator(); iter.hasNext();) {
            var entry = iter.next();
            long targetKey = entry.getKey();
            List<Runnable> injections = entry.getValue();
            if (injections == null || injections.isEmpty()) {
                iter.remove();
                continue;
            }

            int tx = (int) (targetKey >> 32);
            int tz = (int) (targetKey & 0xFFFFFFFFL);
            ChunkWorker target = ChunkIndependentScheduler.getInstance(level).getWorker(tx, tz);

            if (target == null || target.isReleased()) {
                iter.remove();
                continue;
            }

            synchronized (injections) {
                for (Runnable injection : injections) {
                    try {
                        injection.run();
                    } catch (Exception e) {
                        LOGGER.error("CrossChunkBus injection failed for chunk ({},{})", tx, tz, e);
                    }
                }
            }
            iter.remove();
        }
    }

    // ======================== Public API ========================

    public void enqueueBorderUpdate(ChunkWorker source, long targetChunkKey, Runnable injection) {
        if (source == null || injection == null) return;
        borderUpdateQueue.computeIfAbsent(targetChunkKey, k -> Collections.synchronizedList(new ArrayList<>()))
            .add(injection);
    }

    public void processBorderUpdates(ChunkWorker worker) {
        long key = ((long) worker.getChunkX() << 32) | (worker.getChunkZ() & 0xFFFFFFFFL);
        List<Runnable> injections = injectionQueue.remove(key);
        if (injections == null) return;
        for (Runnable injection : injections) {
            try {
                injection.run();
            } catch (Exception e) {
                LOGGER.error("processBorderUpdates failed for chunk ({}, {})",
                    worker.getChunkX(), worker.getChunkZ(), e);
            }
        }
    }

    public void clear() {
        borderUpdateQueue.clear();
        injectionQueue.clear();
    }

    public boolean isRunning() { return running.get(); }
}
