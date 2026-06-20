package net.minecraft.server.level;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public class ChunkLoadCounter {
    private final List<ChunkHolder> pendingChunks = new ArrayList<>();
    private int totalChunks;

    public void track(ServerLevel level, Runnable task) {
        ServerChunkCache chunkSource = level.getChunkSource();
        LongSet set = new LongOpenHashSet();
        chunkSource.runDistanceManagerUpdates();
        chunkSource.chunkMap.allChunksWithAtLeastStatus(ChunkStatus.FULL).forEach(chunkHolder -> set.add(chunkHolder.getPos().toLong()));
        task.run();
        chunkSource.runDistanceManagerUpdates();
        chunkSource.chunkMap.allChunksWithAtLeastStatus(ChunkStatus.FULL).forEach(chunkHolder -> {
            if (!set.contains(chunkHolder.getPos().toLong())) {
                this.pendingChunks.add(chunkHolder);
                this.totalChunks++;
            }
        });
    }

    public int readyChunks() {
        return this.totalChunks - this.pendingChunks();
    }

    public int pendingChunks() {
        this.pendingChunks.removeIf(chunkHolder -> chunkHolder.getLatestStatus() == ChunkStatus.FULL);
        return this.pendingChunks.size();
    }

    public int totalChunks() {
        return this.totalChunks;
    }
}
