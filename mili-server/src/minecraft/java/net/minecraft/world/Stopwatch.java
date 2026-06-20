package net.minecraft.world;

public record Stopwatch(long creationTime, long accumulatedElapsedTime) {
    public Stopwatch(long creationTime) {
        this(creationTime, 0L);
    }

    public long elapsedMilliseconds(long time) {
        long l = time - this.creationTime;
        return this.accumulatedElapsedTime + l;
    }

    public double elapsedSeconds(long time) {
        return this.elapsedMilliseconds(time) / 1000.0;
    }
}
