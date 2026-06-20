package fun.bm.mili.scheduler.group;

import com.mojang.logging.LogUtils;
import fun.bm.mili.scheduler.ChunkBorderCache;
import fun.bm.mili.scheduler.ChunkIndependentScheduler;
import fun.bm.mili.scheduler.ChunkWorker;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class DynamicChunkGroup {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final int groupId;
    private final LongSet members = new LongOpenHashSet();
    private final List<ChunkWorker> workers = new ArrayList<>();

    private volatile boolean highInteraction;
    private volatile boolean merged;

    public DynamicChunkGroup(int groupId) {
        this.groupId = groupId;
    }

    public void addMember(ChunkWorker worker) {
        long key = ChunkPos.asLong(worker.getChunkX(), worker.getChunkZ());
        synchronized (members) {
            members.add(key);
            workers.add(worker);
            if (worker.isHighInteraction()) {
                highInteraction = true;
            }
        }
    }

    public void removeMember(ChunkWorker worker) {
        long key = ChunkPos.asLong(worker.getChunkX(), worker.getChunkZ());
        synchronized (members) {
            members.remove(key);
            workers.remove(worker);
            recalculateInteraction();
        }
    }

    private void recalculateInteraction() {
        highInteraction = workers.stream().anyMatch(ChunkWorker::isHighInteraction);
    }

    public boolean shouldMergeWith(DynamicChunkGroup other) {
        if (other == null || other == this) return false;
        if (this.highInteraction || other.highInteraction) return true;
        return false;
    }

    public DynamicChunkGroup merge(DynamicChunkGroup other) {
        DynamicChunkGroup merged = new DynamicChunkGroup(Math.min(this.groupId, other.groupId));
        synchronized (this.members) {
            synchronized (other.members) {
                for (ChunkWorker w : this.workers) merged.addMember(w);
                for (ChunkWorker w : other.workers) merged.addMember(w);
            }
        }
        this.merged = true;
        other.merged = true;
        return merged;
    }

    public void tickAll() {
        for (ChunkWorker worker : workers) {
            if (!worker.isReleased()) {
                worker.tick();
            }
        }
    }

    public boolean isHighInteraction() { return highInteraction; }
    public boolean isMerged() { return merged; }
    public int getGroupId() { return groupId; }
    public int getMemberCount() {
        synchronized (members) {
            return members.size();
        }
    }
}
