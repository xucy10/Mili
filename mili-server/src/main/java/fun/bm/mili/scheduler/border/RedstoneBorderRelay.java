package fun.bm.mili.scheduler.border;

import fun.bm.mili.scheduler.ChunkWorker;
import fun.bm.mili.scheduler.CrossChunkBus;

public final class RedstoneBorderRelay {

    private final CrossChunkBus bus;

    public RedstoneBorderRelay(CrossChunkBus bus) {
        this.bus = bus;
    }

    public void flushBorderUpdates(ChunkWorker worker) {
        // Cross-chunk border injection requires Folia region thread context.
        // High-interaction chunks use Folia region fallback natively.
    }
}
