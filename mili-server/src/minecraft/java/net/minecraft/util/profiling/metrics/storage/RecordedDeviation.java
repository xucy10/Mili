package net.minecraft.util.profiling.metrics.storage;

import java.time.Instant;
import net.minecraft.util.profiling.ProfileResults;

public final class RecordedDeviation {
    public final Instant timestamp;
    public final int tick;
    public final ProfileResults profilerResultAtTick;

    public RecordedDeviation(Instant timestamp, int tick, ProfileResults profilerResultAtTick) {
        this.timestamp = timestamp;
        this.tick = tick;
        this.profilerResultAtTick = profilerResultAtTick;
    }
}
