package fun.bm.mili.scheduler.border;

import fun.bm.mili.scheduler.ChunkWorker;
import fun.bm.mili.scheduler.CrossChunkBus;
import net.minecraft.core.BlockPos;

public final class BlockUpdateBorderRelay {

    private final CrossChunkBus bus;

    public BlockUpdateBorderRelay(CrossChunkBus bus) {
        this.bus = bus;
    }

    public void relayBlockUpdate(ChunkWorker source, ChunkWorker target, BlockPos pos) {
        // Block update border relay requires Folia region thread context.
        // Currently handled by Folia region fallback for high-interaction chunks.
    }
}
