package fun.bm.mili.scheduler.border;

import fun.bm.mili.scheduler.ChunkWorker;
import fun.bm.mili.scheduler.CrossChunkBus;

public final class FluidBorderRelay {

    private final CrossChunkBus bus;

    public FluidBorderRelay(CrossChunkBus bus) {
        this.bus = bus;
    }

    public void relayFluidBorder(ChunkWorker source, ChunkWorker target) {
        // Fluid border relay requires Folia region thread context.
        // Currently handled by Folia region fallback for high-interaction chunks.
    }
}
