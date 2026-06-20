package fun.bm.mili.scheduler.border;

import fun.bm.mili.scheduler.ChunkWorker;
import fun.bm.mili.scheduler.CrossChunkBus;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class BlockUpdateBorderRelay {

    private final CrossChunkBus bus;

    public BlockUpdateBorderRelay(CrossChunkBus bus) {
        this.bus = bus;
    }

    public void relayBlockUpdate(ChunkWorker source, ChunkWorker target, BlockPos pos, int flags) {
        if (source == null || target == null) return;

        long targetKey = ((long) target.getChunkX() << 32) | (target.getChunkZ() & 0xFFFFFFFFL);

        bus.enqueueBorderUpdate(source, targetKey, () -> {
            ServerLevel level = source.getLevel();
            if (level == null) return;
            level.neighborChanged(pos, level.getBlockState(pos).getBlock(), (net.minecraft.core.Orientation) null);
        });
    }
}
