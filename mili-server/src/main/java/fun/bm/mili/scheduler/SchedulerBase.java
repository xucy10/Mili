package fun.bm.mili.scheduler;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * mili - 调度器基类 / Scheduler base class.
 *
 * <p>为所有 Mili 调度器提供统一的生命周期管理和统计追踪 /
 * Provides unified lifecycle management and statistics tracking for all Mili schedulers.
 *
 * <p>子类需实现 {@link #onStart()} 和 {@link #onStop()} 钩子 /
 * Subclasses must implement {@link #onStart()} and {@link #onStop()} hooks.
 *
 * @author Mili Team
 * @since 1.21.11
 */
public abstract class SchedulerBase {

    protected final Logger logger = LogUtils.getLogger();
    protected final ServerLevel level;
    protected final String name;

    // Lifecycle state
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong startTimeMs = new AtomicLong(0);

    // Statistics
    protected final AtomicLong tasksProcessed = new AtomicLong(0);
    protected final AtomicLong tasksFailed = new AtomicLong(0);

    /**
     * 创建调度器 / Create scheduler.
     *
     * @param level 世界实例 / World instance
     * @param name 调度器名称 / Scheduler name
     */
    protected SchedulerBase(ServerLevel level, String name) {
        this.level = level;
        this.name = name;
    }

    /**
     * 启动调度器 / Start scheduler.
     */
    public final void start() {
        if (running.compareAndSet(false, true)) {
            startTimeMs.set(System.currentTimeMillis());
            try {
                onStart();
                logger.info("[{}] Scheduler started on dim {}", name, level.dimension().identifier());
            } catch (Throwable t) {
                running.set(false);
                logger.error("[{}] Failed to start scheduler", name, t);
            }
        }
    }

    /**
     * 停止调度器 / Stop scheduler.
     */
    public final void stop() {
        if (running.compareAndSet(true, false)) {
            try {
                onStop();
                final long uptimeMs = System.currentTimeMillis() - startTimeMs.get();
                logger.info("[{}] Scheduler stopped after {}ms. Processed: {}, Failed: {}",
                        name, uptimeMs, tasksProcessed.get(), tasksFailed.get());
            } catch (Throwable t) {
                logger.error("[{}] Error during scheduler shutdown", name, t);
            }
        }
    }

    /**
     * 安全关闭线程池 / Safely shutdown executor.
     *
     * @param executor 线程池 / Executor service
     * @param timeoutMs 超时时间 / Timeout in milliseconds
     */
    protected final void shutdownExecutor(ExecutorService executor, long timeoutMs) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 检查是否运行中 / Check if running.
     */
    public final boolean isRunning() {
        return running.get();
    }

    /**
     * 获取运行时间 / Get uptime in milliseconds.
     */
    public final long getUptimeMs() {
        return running.get() ? System.currentTimeMillis() - startTimeMs.get() : 0;
    }

    /**
     * 获取统计摘要 / Get statistics summary.
     */
    public String getStatsSummary() {
        return String.format("%s[processed=%d, failed=%d, uptime=%dms]",
                name, tasksProcessed.get(), tasksFailed.get(), getUptimeMs());
    }

    // ======================== Lifecycle Hooks ========================

    /**
     * 启动钩子 / Start hook.
     * 子类在此初始化资源 / Subclasses initialize resources here.
     */
    protected abstract void onStart();

    /**
     * 停止钩子 / Stop hook.
     * 子类在此清理资源 / Subclasses cleanup resources here.
     */
    protected abstract void onStop();
}
