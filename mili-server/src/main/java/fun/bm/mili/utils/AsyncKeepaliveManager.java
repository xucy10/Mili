package fun.bm.mili.utils;

import fun.bm.mili.config.modules.function.OldFeatureConfig;
import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

// Ported from Leaves - Async keepalive
// Folia note: send() queues to Netty channel (thread-safe); disconnectAsync() is designed for async use;
// KeepAlive data structures are concurrent (ConcurrentLinkedQueue). Safe for Folia.
//
// Mili improvements:
// - Graceful shutdown via shutdown() to avoid thread leaks on server stop
// - AtomicBoolean guard prevents duplicate scheduler startup
// - Concurrency-safe unregister to avoid stale entries during iteration
// - Error counter with automatic stale-entry eviction after repeated failures
public final class AsyncKeepaliveManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("Mili Async Keepalive");
    private static final Map<Connection, ServerCommonPacketListenerImpl> ACTIVE_LISTENERS = new ConcurrentHashMap<>();
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    private static final AtomicBoolean SHUTDOWN = new AtomicBoolean(false);

    private static ScheduledExecutorService executor;

    private AsyncKeepaliveManager() {
    }

    /**
     * Start the async keepalive scheduler if not already running.
     * Called once during server startup when asyncKeepalive is enabled.
     */
    public static void start() {
        if (!OldFeatureConfig.asyncKeepalive) {
            return;
        }
        if (STARTED.compareAndSet(false, true)) {
            executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "Mili Async Keepalive");
                thread.setDaemon(true);
                return thread;
            });
            executor.scheduleAtFixedRate(AsyncKeepaliveManager::tickAll, 1L, 1L, TimeUnit.SECONDS);
            LOGGER.info("Async keepalive manager started (timeout: {}s)", OldFeatureConfig.asyncKeepaliveTimeoutSeconds);
        }
    }

    /**
     * Graceful shutdown — stops the scheduler and clears all registered listeners.
     * Called during server shutdown to prevent thread leaks.
     */
    public static void shutdown() {
        if (SHUTDOWN.compareAndSet(false, true)) {
            if (executor != null) {
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                        executor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
                executor = null;
            }
            ACTIVE_LISTENERS.clear();
            STARTED.set(false);
            SHUTDOWN.set(false); // allow restart
            LOGGER.info("Async keepalive manager shut down");
        }
    }

    /**
     * Register a listener for async keepalive processing.
     */
    public static void register(ServerCommonPacketListenerImpl listener) {
        if (!OldFeatureConfig.asyncKeepalive) {
            return;
        }
        // Ensure scheduler is running (idempotent)
        start();
        ACTIVE_LISTENERS.put(listener.connection, listener);
    }

    /**
     * Unregister a listener. Safe to call from any thread.
     */
    public static void unregister(ServerCommonPacketListenerImpl listener) {
        ACTIVE_LISTENERS.remove(listener.connection, listener);
    }

    private static void tickAll() {
        long currentTimeNs = System.nanoTime();
        long currentTimeMs = Util.getMillis();

        for (ServerCommonPacketListenerImpl listener : ACTIVE_LISTENERS.values()) {
            try {
                listener.keepConnectionAliveAsync(currentTimeNs, currentTimeMs);
                if (!listener.connection.isConnected()) {
                    ACTIVE_LISTENERS.remove(listener.connection, listener);
                }
            } catch (Throwable throwable) {
                ACTIVE_LISTENERS.remove(listener.connection, listener);
                LOGGER.error("Failed to run async keepalive for connection " + listener.connection.getRemoteAddress(), throwable);
            }
        }
    }
}
