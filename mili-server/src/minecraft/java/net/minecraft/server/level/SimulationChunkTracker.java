package net.minecraft.server.level;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;

public class SimulationChunkTracker extends ChunkTracker {
    public static final int MAX_LEVEL = 33;
    protected final Long2ByteMap chunks = new Long2ByteOpenHashMap();
    private final TicketStorage ticketStorage;

    public SimulationChunkTracker(TicketStorage ticketStorage) {
        super(34, 16, 256);
        this.ticketStorage = ticketStorage;
        ticketStorage.setSimulationChunkUpdatedListener(this::update);
        this.chunks.defaultReturnValue((byte)33);
    }

    @Override
    protected int getLevelFromSource(long pos) {
        return this.ticketStorage.getTicketLevelAt(pos, true);
    }

    public int getLevel(ChunkPos chunkPos) {
        return this.getLevel(chunkPos.toLong());
    }

    @Override
    protected int getLevel(long chunkPos) {
        return this.chunks.get(chunkPos);
    }

    @Override
    protected void setLevel(long chunkPos, int level) {
        if (level >= 33) {
            this.chunks.remove(chunkPos);
        } else {
            this.chunks.put(chunkPos, (byte)level);
        }
    }

    public void runAllUpdates() {
        this.runUpdates(Integer.MAX_VALUE);
    }
}
