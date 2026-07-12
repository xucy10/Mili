package fun.bm.mili.chunk;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

public final class ChunkHotness {

    private static final int HISTORY_SIZE = 10;
    private static final long MAX_SINGLE_SAMPLE_NS = 1_000_000L;
    private static final double DECAY_FACTOR = 0.9;
    private static final double DISTANCE_DECAY_POWER = 2.0;

    volatile double score = 0.0;
    volatile boolean active = false;
    volatile double nearestPlayerDistanceSq = Double.MAX_VALUE;

    private final AtomicLong totalAccessNanos = new AtomicLong(0);
    private final AtomicInteger accessCount = new AtomicInteger(0);

    private final long[] accessHistory = new long[HISTORY_SIZE];
    private int historyPos = 0;
    private int historyCount = 0;

    private volatile long lastAccessTime = 0;
    private volatile long creationTime = System.nanoTime();

    public void update(boolean nearPlayer, double distanceToNearestPlayer) {
        this.active = nearPlayer;
        this.nearestPlayerDistanceSq = distanceToNearestPlayer;

        if (nearPlayer) {
            this.score += 1.0;
        } else {
            this.score *= DECAY_FACTOR;
        }

        double distanceFactor = Math.max(0, 1.0 / (1.0 + Math.pow(distanceToNearestPlayer, DISTANCE_DECAY_POWER / 2.0)));
        this.score += distanceFactor * 0.5;

        this.lastAccessTime = System.nanoTime();
    }

    public void recordAccess(long durationNanos) {
        long clampedDuration = Math.min(durationNanos, MAX_SINGLE_SAMPLE_NS);
        totalAccessNanos.addAndGet(clampedDuration);
        accessCount.incrementAndGet();

        accessHistory[historyPos] = clampedDuration;
        historyPos = (historyPos + 1) % HISTORY_SIZE;
        if (historyCount < HISTORY_SIZE) historyCount++;

        score += Math.min(clampedDuration / 1000000.0, 1.0);
    }

    public double getScore() {
        return Math.max(0, score);
    }

    public boolean isActive() {
        return active || (System.nanoTime() - lastAccessTime < 5_000_000_000L);
    }

    public int getAccessCount() {
        return accessCount.get();
    }

    public long getTotalAccessNanos() {
        return totalAccessNanos.get();
    }

    public double getAverageAccessTimeMs() {
        int count = accessCount.get();
        if (count == 0) return 0.0;
        return (totalAccessNanos.get() / 1_000_000.0) / count;
    }

    public double getRecentAverageAccessMs() {
        if (historyCount == 0) return 0.0;

        long sum = 0;
        for (int i = 0; i < historyCount; i++) {
            sum += accessHistory[i];
        }

        return (sum / 1_000_000.0) / historyCount;
    }

    public double getNearestPlayerDistance() {
        return Math.sqrt(Math.max(0, nearestPlayerDistanceSq));
    }

    public long getLastAccessTime() {
        return lastAccessTime;
    }

    public long getAgeSeconds() {
        return (System.nanoTime() - creationTime) / 1_000_000_000L;
    }

    public void reset() {
        score = 0.0;
        active = false;
        nearestPlayerDistanceSq = Double.MAX_VALUE;
        totalAccessNanos.set(0);
        accessCount.set(0);
        historyPos = 0;
        historyCount = 0;
        lastAccessTime = 0;
        creationTime = System.nanoTime();
        for (int i = 0; i < HISTORY_SIZE; i++) {
            accessHistory[i] = 0;
        }
    }
}