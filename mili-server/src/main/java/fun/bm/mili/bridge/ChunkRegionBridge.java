package fun.bm.mili.bridge;

import com.mojang.logging.LogUtils;
import fun.bm.mili.chunk.MiliChunkSystem;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ChunkRegionBridge {

    private ChunkRegionBridge() {}

    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    private static ScheduledExecutorService scheduler;

    private static final long syncIntervalMs = 250;

    public static void init() {
        if (!initialized.compareAndSet(false, true)) return;

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Mili-ChunkRegionBridge");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        });

        scheduler.scheduleAtFixedRate(
                ChunkRegionBridge::syncLoadData,
                syncIntervalMs,
                syncIntervalMs,
                TimeUnit.MILLISECONDS
        );

        LogUtils.getLogger().info("[Mili] ChunkRegionBridge initialized");
    }

    static void syncLoadData() {
        try {
            // Mili start - fix: snapshot Bukkit collections to avoid ConcurrentModificationException
            List<World> worlds = new ArrayList<>(Bukkit.getWorlds());
            for (World world : worlds) {
                List<Player> players;
                try {
                    players = new ArrayList<>(world.getPlayers());
                } catch (Throwable t) {
                    // World unloaded concurrently — skip this world
                    continue;
                }

                int loadedChunks;
                try {
                    loadedChunks = world.getLoadedChunks().length;
                } catch (Throwable t) {
                    loadedChunks = 0;
                }

                int playerCount = players.size();

                for (Player player : players) {
                    if (!player.isOnline()) continue;

                    // Mili start - fix: call getLocation() once to avoid race between two calls
                    Location loc = player.getLocation();
                    int cx = loc.getBlockX() >> 4;
                    int cz = loc.getBlockZ() >> 4;
                    // Mili end

                    var hotness = MiliChunkSystem.getChunkHotness(world, cx, cz);
                    if (hotness != null) {
                        long now = System.nanoTime();
                        hotness.recordAccess(now - hotness.getLastAccessTime());
                    }
                }
            }
        } catch (Throwable e) {
            // Mili start - fix: catch Throwable (not just Exception) to prevent scheduler thread death on Error
            LogUtils.getLogger().warn("[Mili] ChunkRegionBridge sync error", e);
        }
    }

    public static void shutdown() {
        if (!initialized.compareAndSet(true, false)) return;

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