package fun.bm.mili.scheduler.border;

import fun.bm.mili.scheduler.ChunkBorderCache;
import fun.bm.mili.scheduler.ChunkWorker;
import fun.bm.mili.scheduler.CrossChunkBus;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluids;

public final class FluidBorderRelay {

    private final CrossChunkBus bus;

    public FluidBorderRelay(CrossChunkBus bus) {
        this.bus = bus;
    }

    public void relayFluidBorder(ChunkWorker source, ChunkWorker target, ChunkBorderCache.BorderFace face) {
        if (source == null || target == null) return;

        long targetKey = ((long) target.getChunkX() << 32) | (target.getChunkZ() & 0xFFFFFFFFL);
        ServerLevel level = source.getLevel();
        if (level == null) return;

        bus.enqueueBorderUpdate(source, targetKey, () -> {
            for (int i = 0; i < 16; i++) {
                int tx, tz;
                switch (face) {
                    case EAST  -> { tx = target.getChunkX() * 16;      tz = target.getChunkZ() * 16 + i; }
                    case WEST  -> { tx = target.getChunkX() * 16 + 15; tz = target.getChunkZ() * 16 + i; }
                    case SOUTH -> { tx = target.getChunkX() * 16 + i;  tz = target.getChunkZ() * 16;     }
                    case NORTH -> { tx = target.getChunkX() * 16 + i;  tz = target.getChunkZ() * 16 + 15; }
                    default    -> { return; }
                }
                BlockPos fluidPos = new BlockPos(tx, level.getMinY() + 1, tz);
                level.scheduleTick(fluidPos, Fluids.WATER, 1);
                level.scheduleTick(fluidPos, Fluids.LAVA, 1);
            }
        });
    }
}
