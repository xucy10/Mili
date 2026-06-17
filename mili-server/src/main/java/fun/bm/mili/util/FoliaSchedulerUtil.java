package fun.bm.mili.util;

import org.bukkit.Bukkit;
import org.leavesmc.leaves.plugin.MinecraftInternalPlugin;

import java.util.concurrent.TimeUnit;

/**
 * Folia 调度器工具类 — 嵌入自 FoliaLib (github.com/handyplus/FoliaLib) /
 * Folia scheduler utility — embedded from FoliaLib.
 *
 * <p>提供与 FoliaLib {@code HandySchedulerUtil} 相同的方法名，
 * 内部直接包装 Folia 原生调度器 / Provides same method names as FoliaLib's
 * HandySchedulerUtil, wrapping Folia native schedulers directly.
 *
 * <p>本项目为 Folia-only，无需 Bukkit 兼容分支 / This project is Folia-only,
 * no Bukkit compatibility branch needed.
 *
 * <p>用法 / Usage:
 * <pre>
 *   FoliaSchedulerUtil.runTask(() -> { ... });
 *   FoliaSchedulerUtil.runTaskLater(() -> { ... }, 20L);
 *   FoliaSchedulerUtil.runTaskAsynchronously(() -> { ... });
 *   FoliaSchedulerUtil.runTaskTimerAsynchronously(() -> { ... }, 0L, 40L);
 * </pre>
 */
public final class FoliaSchedulerUtil {

    private FoliaSchedulerUtil() {}

    private static org.bukkit.plugin.Plugin plugin() {
        return MinecraftInternalPlugin.INSTANCE;
    }

    /**
     * 全局区域调度 (下一 tick) / Run on global region (next tick).
     */
    public static void runTask(Runnable task) {
        Bukkit.getGlobalRegionScheduler().run(plugin(), a -> task.run());
    }

    /**
     * 延迟全局区域调度 / Delayed global region scheduling.
     *
     * @param task  任务 / Task
     * @param delay 延迟 tick 数 / Delay in ticks (min 1)
     */
    public static void runTaskLater(Runnable task, long delay) {
        delay = Math.max(1, delay);
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin(), a -> task.run(), delay);
    }

    /**
     * 循环全局区域调度 / Repeating global region scheduling.
     *
     * @param task   任务 / Task
     * @param delay  初始延迟 tick / Initial delay in ticks (min 1)
     * @param period 间隔 tick / Period in ticks (min 1)
     */
    public static void runTaskTimer(Runnable task, long delay, long period) {
        delay = Math.max(1, delay);
        period = Math.max(1, period);
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin(), a -> task.run(), delay, period);
    }

    /**
     * 异步调度 (立即) / Async scheduling (immediate).
     */
    public static void runTaskAsynchronously(Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin(), a -> task.run());
    }

    /**
     * 延迟异步调度 / Delayed async scheduling.
     *
     * @param task  任务 / Task
     * @param delay 延迟 tick 数 / Delay in ticks
     */
    public static void runTaskLaterAsynchronously(Runnable task, long delay) {
        delay = Math.max(1, delay);
        Bukkit.getAsyncScheduler().runDelayed(plugin(), a -> task.run(), delay * 50, TimeUnit.MILLISECONDS);
    }

    /**
     * 循环异步调度 / Repeating async scheduling.
     *
     * @param task   任务 / Task
     * @param delay  初始延迟 tick / Initial delay in ticks
     * @param period 间隔 tick / Period in ticks
     */
    public static void runTaskTimerAsynchronously(Runnable task, long delay, long period) {
        delay = Math.max(1, delay);
        period = Math.max(1, period);
        Bukkit.getAsyncScheduler().runAtFixedRate(plugin(), a -> task.run(),
                delay * 50, period * 50, TimeUnit.MILLISECONDS);
    }

    /**
     * 取消所有调度任务 / Cancel all scheduled tasks.
     */
    public static void cancelTask() {
        Bukkit.getGlobalRegionScheduler().cancelTasks(plugin());
        Bukkit.getAsyncScheduler().cancelTasks(plugin());
    }
}
