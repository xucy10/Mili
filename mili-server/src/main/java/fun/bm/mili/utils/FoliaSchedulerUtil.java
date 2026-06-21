package fun.bm.mili.utils;

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
 * <p><b>重要: 全局区域调度 ≠ 实体区域调度 / Global region ≠ entity region!</b>
 * <ul>
 *   <li>{@link #runTask} / {@link #runTaskLater} → 全局区域线程 (global region thread)</li>
 *   <li>实体状态修改必须使用 {@code entity.getScheduler()} → 实体所属区域线程</li>
 * </ul>
 */
public final class FoliaSchedulerUtil {

    private FoliaSchedulerUtil() {}

    private static org.bukkit.plugin.Plugin plugin() {
        return MinecraftInternalPlugin.INSTANCE;
    }

    /**
     * 全局区域调度 (下一 tick) / Run on global region (next tick).
     *
     * @return ScheduledTask 引用，可传给 {@link #cancelTask(Object)} / task handle
     */
    public static Object runTask(Runnable task) {
        return Bukkit.getGlobalRegionScheduler().run(plugin(), a -> task.run());
    }

    /**
     * 延迟全局区域调度 / Delayed global region scheduling.
     *
     * @param delay 延迟 tick 数 (min 1) / Delay in ticks
     * @return ScheduledTask 引用 / task handle
     */
    public static Object runTaskLater(Runnable task, long delay) {
        delay = Math.max(1, delay);
        return Bukkit.getGlobalRegionScheduler().runDelayed(plugin(), a -> task.run(), delay);
    }

    /**
     * 循环全局区域调度 / Repeating global region scheduling.
     *
     * @param delay  初始延迟 tick (min 1) / Initial delay
     * @param period 间隔 tick (min 1) / Period
     * @return ScheduledTask 引用 / task handle
     */
    public static Object runTaskTimer(Runnable task, long delay, long period) {
        delay = Math.max(1, delay);
        period = Math.max(1, period);
        return Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin(), a -> task.run(), delay, period);
    }

    /**
     * 异步调度 (立即) / Async scheduling (immediate).
     *
     * @return ScheduledTask 引用 / task handle
     */
    public static Object runTaskAsynchronously(Runnable task) {
        return Bukkit.getAsyncScheduler().runNow(plugin(), a -> task.run());
    }

    /**
     * 延迟异步调度 / Delayed async scheduling.
     *
     * @param delay 延迟 tick 数 / Delay in ticks
     * @return ScheduledTask 引用 / task handle
     */
    public static Object runTaskLaterAsynchronously(Runnable task, long delay) {
        delay = Math.max(1, delay);
        return Bukkit.getAsyncScheduler().runDelayed(plugin(), a -> task.run(), delay * 50, TimeUnit.MILLISECONDS);
    }

    /**
     * 循环异步调度 / Repeating async scheduling.
     *
     * @param delay  初始延迟 tick / Initial delay
     * @param period 间隔 tick / Period
     * @return ScheduledTask 引用 / task handle
     */
    public static Object runTaskTimerAsynchronously(Runnable task, long delay, long period) {
        delay = Math.max(1, delay);
        period = Math.max(1, period);
        return Bukkit.getAsyncScheduler().runAtFixedRate(plugin(), a -> task.run(),
                delay * 50, period * 50, TimeUnit.MILLISECONDS);
    }

    /**
     * 取消单个任务 / Cancel a single task.
     *
     * @param taskHandle {@link #runTask} 等方法返回的引用 / handle from run methods
     */
    public static void cancelTask(Object taskHandle) {
        if (taskHandle instanceof io.papermc.paper.threadedregions.scheduler.ScheduledTask st) {
            st.cancel();
        }
    }

    /**
     * 取消所有通过本工具调度的任务 / Cancel ALL tasks scheduled via this utility.
     * <b>慎用 / Use with caution!</b>
     */
    public static void cancelAll() {
        Bukkit.getGlobalRegionScheduler().cancelTasks(plugin());
        Bukkit.getAsyncScheduler().cancelTasks(plugin());
    }
}
