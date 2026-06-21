package fun.bm.mili.scheduler;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * 死锁检测器 / Deadlock detector.
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
 */
public final class DeadlockDetector {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final DeadlockDetector INSTANCE = new DeadlockDetector();

    private final Map<Thread, LockInfo> threadLocks = new ConcurrentHashMap<>();
    private final Map<Long, Thread> lockOwners = new ConcurrentHashMap<>();
    private final AtomicLong deadlockCount = new AtomicLong(0);
    private final AtomicLong timeoutCount = new AtomicLong(0);
    private volatile long timeoutMs = 5000L;

    private DeadlockDetector() {}

    public static DeadlockDetector getInstance() { return INSTANCE; }
    public void setTimeoutMs(long ms) { this.timeoutMs = ms; }

    /**
     * 获取锁 / Acquire a lock.
     *
     * <p>包含死锁检测和超时保护 / Includes deadlock detection and timeout protection.
     *
     * @param lockId 锁 ID / Lock ID
     * @param lockName 锁名称（用于日志）/ Lock name (for logging)
     * @return true 如果成功获取 / true if acquired successfully
     */
    public boolean acquireLock(long lockId, String lockName) {
        Thread current = Thread.currentThread();

        while (true) {
            Thread owner = lockOwners.putIfAbsent(lockId, current);
            if (owner == null) {
                break; // 成功获取 / Successfully acquired
            }
            if (owner == current) {
                return true; // 重入锁 / Reentrant lock
            }

            // 死锁检测 / Deadlock detection
            LockInfo ownerInfo = threadLocks.get(owner);
            if (ownerInfo != null && ownerInfo.isWaitingFor != null) {
                if (wouldCauseDeadlock(current, owner)) {
                    deadlockCount.incrementAndGet();
                    LOGGER.error("[DeadlockDetector] DEADLOCK: {} wants lock {} held by {}",
                            current.getName(), lockName, owner.getName());
                    dumpLockState();
                    return false;
                }
            }

            // 超时等待 / Timeout wait
            long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
            while (lockOwners.get(lockId) != current && System.nanoTime() < deadline) {
                LockSupport.parkNanos(lockId + "-wait", 1_000_000L); // 1ms granularity
                if (Thread.interrupted()) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            if (lockOwners.get(lockId) != current) {
                timeoutCount.incrementAndGet();
                LOGGER.warn("[DeadlockDetector] Timeout {}ms for lock {}", timeoutMs, lockName);
                return false;
            }
        }

        LockInfo info = threadLocks.computeIfAbsent(current, t -> new LockInfo());
        info.heldLocks.add(lockId);
        info.lastLockName = lockName;
        return true;
    }

    /**
     * 释放锁 / Release a lock.
     *
     * <p>只有持有者才能释放 / Only the owner can release.
     */
    public void releaseLock(long lockId) {
        Thread current = Thread.currentThread();
        if (lockOwners.remove(lockId, current)) {
            LockInfo info = threadLocks.get(current);
            if (info != null) {
                info.heldLocks.remove(lockId);
                if (info.isWaitingFor != null && info.isWaitingFor == lockId) {
                    info.isWaitingFor = null;
                }
                if (info.heldLocks.isEmpty()) {
                    threadLocks.remove(current);
                }
            }
        }
    }

    /**
     * 标记线程正在等待指定锁 / Mark thread as waiting for a lock.
     */
    public void markWaitingFor(long lockId) {
        LockInfo info = threadLocks.computeIfAbsent(Thread.currentThread(), t -> new LockInfo());
        info.isWaitingFor = lockId;
    }

    /**
     * 清除等待标记 / Clear waiting-for mark.
     */
    public void clearWaitingFor() {
        LockInfo info = threadLocks.get(Thread.currentThread());
        if (info != null) {
            info.isWaitingFor = null;
        }
    }

    /**
     * 检测是否会导致死锁 / Check if acquiring would cause deadlock.
     * 遍历等待链检测循环 / Traverses wait-for chain to detect cycles.
     */
    private boolean wouldCauseDeadlock(Thread waiter, Thread holder) {
        Set<Thread> visited = new HashSet<>();
        Thread current = holder;
        while (current != null && current != waiter) {
            if (!visited.add(current)) return false; // 已访问过，非循环 / Already visited, not a cycle
            LockInfo info = threadLocks.get(current);
            if (info == null || info.isWaitingFor == null) break;
            current = lockOwners.get(info.isWaitingFor);
        }
        return current == waiter;
    }

    /**
     * 转储锁状态（用于调试）/ Dump lock state (for debugging).
     */
    public void dumpLockState() {
        LOGGER.warn("[DeadlockDetector] === Lock Dump ===");
        for (Map.Entry<Thread, LockInfo> entry : threadLocks.entrySet()) {
            Thread t = entry.getKey();
            LockInfo info = entry.getValue();
            LOGGER.warn("  {} held={} last={} waitingFor={}",
                    t.getName(), info.heldLocks.size(), info.lastLockName, info.isWaitingFor);
        }
        LOGGER.warn("[DeadlockDetector] === End Dump ===");
    }

    public String getStats() {
        return String.format("deadlocks=%d, timeouts=%d, active=%d",
                deadlockCount.get(), timeoutCount.get(), threadLocks.size());
    }

    public void cleanup() {
        threadLocks.clear();
        lockOwners.clear();
    }

    private static final class LockInfo {
        final Set<Long> heldLocks = ConcurrentHashMap.newKeySet();
        volatile Long isWaitingFor = null;
        volatile String lastLockName = "";
    }
}
package fun.bm.mili.scheduler;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

public final class DeadlockDetector {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final DeadlockDetector INSTANCE = new DeadlockDetector();

    private final Map<Thread, LockInfo> threadLocks = new ConcurrentHashMap<>();
    private final Map<Long, Thread> lockOwners = new ConcurrentHashMap<>();
    private final AtomicLong deadlockCount = new AtomicLong(0);
    private final AtomicLong timeoutCount = new AtomicLong(0);
    private volatile long timeoutMs = 5000L;

    private DeadlockDetector() {}

    public static DeadlockDetector getInstance() { return INSTANCE; }
    public void setTimeoutMs(long ms) { this.timeoutMs = ms; }

    public boolean acquireLock(long lockId, String lockName) {
        Thread current = Thread.currentThread();

        while (true) {
            Thread owner = lockOwners.putIfAbsent(lockId, current);
            if (owner == null) {
                break;
            }
            if (owner == current) {
                return true;
            }

            LockInfo ownerInfo = threadLocks.get(owner);
            if (ownerInfo != null && ownerInfo.isWaitingFor != null) {
                if (wouldCauseDeadlock(current, owner)) {
                    deadlockCount.incrementAndGet();
                    LOGGER.error("[DeadlockDetector] DEADLOCK: {} wants lock {} held by {}",
                            current.getName(), lockName, owner.getName());
                    dumpLockState();
                    return false;
                }
            }

            long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
            while (lockOwners.get(lockId) != current && System.nanoTime() < deadline) {
                LockSupport.parkNanos(lockId + "-wait", 1_000L);
                if (Thread.interrupted()) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            if (lockOwners.get(lockId) != current) {
                timeoutCount.incrementAndGet();
                LOGGER.warn("[DeadlockDetector] Timeout {}ms for lock {}", timeoutMs, lockName);
                return false;
            }
        }

        LockInfo info = threadLocks.computeIfAbsent(current, t -> new LockInfo());
        info.heldLocks.add(lockId);
        info.lastLockName = lockName;
        return true;
    }

    public void releaseLock(long lockId) {
        Thread current = Thread.currentThread();
        if (lockOwners.remove(lockId, current)) {
            LockInfo info = threadLocks.get(current);
            if (info != null) {
                info.heldLocks.remove(lockId);
                if (info.heldLocks.isEmpty()) {
                    threadLocks.remove(current);
                }
            }
        }
    }

    public void markWaitingFor(long lockId) {
        LockInfo info = threadLocks.computeIfAbsent(Thread.currentThread(), t -> new LockInfo());
        info.isWaitingFor = lockId;
    }

    private boolean wouldCauseDeadlock(Thread waiter, Thread holder) {
        Set<Thread> visited = new HashSet<>();
        Thread current = holder;
        while (current != null && current != waiter) {
            if (!visited.add(current)) return false;
            LockInfo info = threadLocks.get(current);
            if (info == null || info.isWaitingFor == null) break;
            current = lockOwners.get(info.isWaitingFor);
        }
        return current == waiter;
    }

    public void dumpLockState() {
        LOGGER.warn("[DeadlockDetector] === Lock Dump ===");
        for (Map.Entry<Thread, LockInfo> entry : threadLocks.entrySet()) {
            Thread t = entry.getKey();
            LockInfo info = entry.getValue();
            LOGGER.warn("  {} held={} last={} waitingFor={}",
                    t.getName(), info.heldLocks.size(), info.lastLockName, info.isWaitingFor);
        }
        LOGGER.warn("[DeadlockDetector] === End Dump ===");
    }

    public String getStats() {
        return String.format("deadlocks=%d, timeouts=%d, active=%d",
                deadlockCount.get(), timeoutCount.get(), threadLocks.size());
    }

    public void cleanup() {
        threadLocks.clear();
        lockOwners.clear();
    }

    private static final class LockInfo {
        final Set<Long> heldLocks = ConcurrentHashMap.newKeySet();
        volatile Long isWaitingFor = null;
        volatile String lastLockName = "";
    }
}
