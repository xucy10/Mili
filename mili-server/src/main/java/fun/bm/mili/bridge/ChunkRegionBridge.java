package fun.bm.mili.bridge;

import com.mojang.logging.LogUtils;
import fun.bm.mili.chunk.MiliChunkSystem;
import fun.bm.mili.utils.RegionBalancer;
import fun.bm.mili.utils.RegionLoadMonitor;
import fun.bm.mili.utils.SmartRegionManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import fun.bm.mili.scheduler.MiliScheduler;
import fun.bm.mili.scheduler.RecurringTask;

public final class ChunkRegionBridge {

    private ChunkRegionBridge() {}

    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    private static volatile ScheduledExecutorService bridgeExecutor;
    private static volatile RecurringTask.Handle bridgeTask;

    private static final long syncIntervalMs = 250;

    public static void init() {
        if (!initialized.compareAndSet(false, true)) return;

        // Mili start - 250ms 高频且需遍历全部世界与玩家，独占线程以保证巡检延迟可控，
        // 不与共享 BACKGROUND 池中的低频任务互相抢占。仍经 RecurringTask 以获得异常隔离，
        // 生命周期交治理层托管。
        bridgeExecutor = MiliScheduler.namedScheduledPool("chunk-region-bridge", 1);
        bridgeTask = RecurringTask.startOn(bridgeExecutor, "chunk-region-bridge",
                syncIntervalMs, ChunkRegionBridge::syncLoadData);
        // Mili end

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
        if (!initialized.compareAndSet(true, false)) return;

        // Mili start - 取消句柄即可，池的生命周期由治理层统一收口
        if (bridgeTask != null) {
            bridgeTask.cancel();
            bridgeTask = null;
        }
        bridgeExecutor = null;
        // Mili end

        LogUtils.getLogger().info("[Mili] ChunkRegionBridge shutdown");
    }
}