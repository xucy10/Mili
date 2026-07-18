package fun.bm.mili.bridge;

import com.mojang.logging.LogUtils;
import fun.bm.mili.chunk.MiliChunkSystem;
import fun.bm.mili.utils.RegionBalancer;
import fun.bm.mili.utils.RegionLoadMonitor;
import fun.bm.mili.utils.SmartRegionManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ChunkRegionBridge {

    private ChunkRegionBridge() {}

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
    private static ScheduledExecutorService scheduler;

    private static final long SYNC_INTERVAL_MS = 250;

    public static void init() {
        if (!INITIALIZED.compareAndSet(false, true)) return;

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Mili-ChunkRegionBridge");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        });

        scheduler.scheduleAtFixedRate(
                ChunkRegionBridge::syncLoadData,
                SYNC_INTERVAL_MS,
                SYNC_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );

        LogUtils.getLogger().info("[Mili] ChunkRegionBridge initialized");
    }

    static void syncLoadData() {
        try {
            for (World world : Bukkit.getWorlds()) {
                int loadedChunks = world.getLoadedChunks().length;
                int playerCount = world.getPlayers().size();

                for (Player player : world.getPlayers()) {
                    if (!player.isOnline()) continue;

                    int cx = player.getLocation().getBlockX() >> 4;
                    int cz = player.getLocation().getBlockZ() >> 4;

                    var hotness = MiliChunkSystem.getChunkHotness(world, cx, cz);
                    if (hotness != null) {
                        long now = System.nanoTime();
                        hotness.recordAccess(now - hotness.getLastAccessTime());
                    }
                }
            }
        } catch (Exception e) {
            LogUtils.getLogger().warn("[Mili] ChunkRegionBridge sync error", e);
        }
    }

    public static void shutdown() {
        if (!INITIALIZED.compareAndSet(true, false)) return;

        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        LogUtils.getLogger().info("[Mili] ChunkRegionBridge shutdown");
    }
}