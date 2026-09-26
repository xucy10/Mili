package fun.bm.mili.chunk;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.LongAdder;

public final class ChunkHotness {

    private static final int HISTORY_SIZE = 10;
    private static final long MAX_SINGLE_SAMPLE_NS = 1_000_000L;
    private static final double DECAY_FACTOR = 0.9;
    private static final double DISTANCE_DECAY_POWER = 2.0;

    private final LongAdder totalAccessNanos = new LongAdder();
    private final AtomicInteger accessCount = new AtomicInteger(0);
    private final AtomicLongArray accessHistory = new AtomicLongArray(HISTORY_SIZE);
    private final AtomicLong scoreBits = new AtomicLong(0);
    private final AtomicLong lastAccessTime = new AtomicLong(0);
    private final AtomicLong creationTime = new AtomicLong(System.nanoTime());

    private volatile boolean active = false;
    private volatile double nearestPlayerDistanceSq = Double.MAX_VALUE;

    // Mili start - fix: removed SCORE_MASK that corrupted double bit layout
    // The mask 0x000FFFFFFFFFFFFFL clears the exponent bits of double,
    // causing unpackScore to return NaN or denormalized values.
    // Use direct CAS on the full 64-bit value instead.
    private double unpackScore(long bits) {
        return Double.longBitsToDouble(bits);
    }
    // Mili end

    public void update(boolean nearPlayer, double distanceToNearestPlayer) {
        this.active = nearPlayer;
        this.nearestPlayerDistanceSq = distanceToNearestPlayer;

        long current, next;
        do {
            current = scoreBits.get();
            double oldScore = unpackScore(current);
            double newScore = nearPlayer ? oldScore + 1.0 : oldScore * DECAY_FACTOR;
            double distanceFactor = Math.max(0, 1.0 / (1.0 + Math.pow(distanceToNearestPlayer, DISTANCE_DECAY_POWER / 2.0)));
            newScore += distanceFactor * 0.5;
            next = Double.doubleToLongBits(newScore);
        } while (!scoreBits.compareAndSet(current, next));

        this.lastAccessTime.set(System.nanoTime());
    }

    public void recordAccess(long durationNanos) {
        // Mili start - fix: clamp negative durations to 0 (can happen on clock skew or uninitialized state)
        long clampedDuration = Math.max(0, Math.min(durationNanos, MAX_SINGLE_SAMPLE_NS));
        // Mili end
        totalAccessNanos.add(clampedDuration);
        int count = accessCount.incrementAndGet();

        int pos = (count - 1) % HISTORY_SIZE;
        accessHistory.set(pos, clampedDuration);

        long current, next;
        do {
            current = scoreBits.get();
            double oldScore = unpackScore(current);
            double newScore = oldScore + Math.min(clampedDuration / 1000000.0, 1.0);
            next = Double.doubleToLongBits(newScore);
        } while (!scoreBits.compareAndSet(current, next));
    }

    public double getScore() {
        // Mili start - fix: guard against NaN/NaN from corrupted scoreBits
        double score = unpackScore(scoreBits.get());
        if (Double.isNaN(score) || score < 0) return 0.0;
        return score;
        // Mili end
    }

    public boolean isActive() {
        return active || (System.nanoTime() - lastAccessTime.get() < 5_000_000_000L);
    }

    public int getAccessCount() {
        return accessCount.get();
    }

    public long getTotalAccessNanos() {
        return totalAccessNanos.sum();
    }

    public double getAverageAccessTimeMs() {
        int count = accessCount.get();
        if (count == 0) return 0.0;
        return (totalAccessNanos.sum() / 1_000_000.0) / count;
    }

    public double getRecentAverageAccessMs() {
        int count = Math.min(accessCount.get(), HISTORY_SIZE);
        if (count == 0) return 0.0;

        long sum = 0;
        for (int i = 0; i < count; i++) {
            sum += accessHistory.get(i);
        }
        return (sum / 1_000_000.0) / count;
    }

    public double getNearestPlayerDistance() {
        return Math.sqrt(Math.max(0, nearestPlayerDistanceSq));
    }

    public long getLastAccessTime() {
        return lastAccessTime.get();
    }

    public long getAgeSeconds() {
        return (System.nanoTime() - creationTime.get()) / 1_000_000_000L;
    }

    public void reset() {
        scoreBits.set(0);
        active = false;
        nearestPlayerDistanceSq = Double.MAX_VALUE;
        totalAccessNanos.reset();
        accessCount.set(0);
        lastAccessTime.set(0);
        creationTime.set(System.nanoTime());
        for (int i = 0; i < HISTORY_SIZE; i++) {
            accessHistory.set(i, 0);
        }
    }
}