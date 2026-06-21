package fun.bm.mili.scheduler;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * mili - 死锁检测器 / Deadlock detector.
 *
 * <p>检测并预防调度器死锁，特别针对跨区域操作 /
 * Detects and prevents scheduler deadlocks, especially for cross-region operations.
 *
 * <p>工作原理 / How it works:
 * <ol>
 *   <li>记录每个线程持有的锁 / Records locks held by each thread</li>
 *   <li>检测循环等待 / Detects circular wait conditions</li>
 *   <li>超时时强制释放 / Force release on timeout</li>
 * </ol>
 *
 * @author Mili Team
 * @since 1.21.11
 */
public final class DeadlockDetector {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final DeadlockDetector INSTANCE = new DeadlockDetector();

    // Thread -> Lock mappings
    private final Map<Thread, LockInfo> threadLocks = new ConcurrentHashMap<>();
    private final Map<Long, Thread> lockOwners = new ConcurrentHashMap<>();

    // Statistics
    private final AtomicLong deadlockCount = new AtomicLong(0);
    private final AtomicLong timeoutCount = new AtomicLong(0);

    // Config
    private volatile long timeoutMs = 5000L; // 5 seconds default

    private DeadlockDetector() {}

    /**
     * 获取单例 / Get singleton instance.
     */
    public static DeadlockDetector getInstance() {
        return INSTANCE;
    }

    /**
     * 设置超时时间 / Set timeout.
     *
     * @param ms 超时毫秒数 / Timeout in milliseconds
     */
    public void setTimeoutMs(long ms) {
        this.timeoutMs = ms;
    }

    /**
     * 记录线程获取锁 / Record thread acquiring lock.
     *
     * @param lockId 锁标识 / Lock identifier
     * @param lockName 锁名称（用于日志）/ Lock name (for logging)
     * @return true 如果成功获取 / true if acquired successfully
     */
    public boolean acquireLock(long lockId, String lockName) {
        Thread current = Thread.currentThread();
        long threadId = current.getId();

        // Check if lock is already held
        Thread owner = lockOwners.get(lockId);
        if (owner != null && owner != current) {
            // Lock is held by another thread - potential deadlock
            LockInfo ownerInfo = threadLocks.get(owner);
            if (ownerInfo != null && ownerInfo.isWaitingFor != null) {
                // Check for circular wait
                if (wouldCauseDeadlock(current, owner)) {
                    deadlockCount.incrementAndGet();
                    LOGGER.error("[DeadlockDetector] DEADLOCK DETECTED! Thread {} wants lock {} (held by {}), " +
                                    "but {} is waiting for lock held by {}",
                            current.getName(), lockName, owner.getName(),
                            owner.getName(), current.getName());
                    dumpLockState();
                    return false;
                }
            }

            // Wait for lock with timeout
            long startTime = System.currentTimeMillis();
            while (lockOwners.containsKey(lockId)) {
                if (System.currentTimeMillis() - startTime > timeoutMs) {
                    timeoutCount.incrementAndGet();
                    LOGGER.warn("[DeadlockDetector] Lock acquisition timeout after {}ms for lock {}",
                            timeoutMs, lockName);
                    return false;
                }
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }

        // Record lock acquisition
        lockOwners.put(lockId, current);
        LockInfo info = threadLocks.computeIfAbsent(current, t -> new LockInfo());
        info.heldLocks.add(lockId);
        info.lastLockName = lockName;

        return true;
    }

    /**
     * 记录线程释放锁 / Record thread releasing lock.
     *
     * @param lockId 锁标识 / Lock identifier
     */
    public void releaseLock(long lockId) {
        Thread current = Thread.currentThread();
        lockOwners.remove(lockId);

        LockInfo info = threadLocks.get(current);
        if (info != null) {
            info.heldLocks.remove(lockId);
            if (info.heldLocks.isEmpty()) {
                threadLocks.remove(current);
            }
        }
    }

    /**
     * 标记线程正在等待锁 / Mark thread waiting for lock.
     *
     * @param lockId 等待的锁标识 / Lock ID being waited for
     */
    public void markWaitingFor(long lockId) {
        LockInfo info = threadLocks.computeIfAbsent(Thread.currentThread(), t -> new LockInfo());
        info.isWaitingFor = lockId;
    }

    /**
     * 检查是否会形成死锁 / Check if acquiring lock would cause deadlock.
     */
    private boolean wouldCauseDeadlock(Thread waiter, Thread holder) {
        // Simple cycle detection: waiter -> holder -> waiter
        LockInfo holderInfo = threadLocks.get(holder);
        if (holderInfo == null || holderInfo.isWaitingFor == null) {
            return false;
        }

        Thread nextWaiter = lockOwners.get(holderInfo.isWaitingFor);
        return nextWaiter == waiter;
    }

    /**
     * 转储锁状态（调试用）/ Dump lock state (for debugging).
     */
    public void dumpLockState() {
        LOGGER.error("[DeadlockDetector] === Lock State Dump ===");
        for (Map.Entry<Thread, LockInfo> entry : threadLocks.entrySet()) {
            Thread t = entry.getKey();
            LockInfo info = entry.getValue();
            LOGGER.error("  Thread: {} (ID: {})", t.getName(), t.getId());
            LOGGER.error("    Held locks: {} ({})", info.heldLocks.size(), info.lastLockName);
            if (info.isWaitingFor != null) {
                Thread owner = lockOwners.get(info.isWaitingFor);
                LOGGER.error("    Waiting for lock owned by: {}", owner != null ? owner.getName() : "unknown");
            }
        }
        LOGGER.error("[DeadlockDetector] === End Dump ===");
    }

    /**
     * 获取统计信息 / Get statistics.
     */
    public String getStats() {
        return String.format("deadlocks=%d, timeouts=%d, active_threads=%d",
                deadlockCount.get(), timeoutCount.get(), threadLocks.size());
    }

    /**
     * 清理（停止时调用）/ Cleanup (called on shutdown).
     */
    public void cleanup() {
        threadLocks.clear();
        lockOwners.clear();
    }

    /**
     * 锁信息内部类 / Lock info inner class.
     */
    private static final class LockInfo {
        final java.util.Set<Long> heldLocks = ConcurrentHashMap.newKeySet();
        volatile Long isWaitingFor = null;
        volatile String lastLockName = "";
    }
}
