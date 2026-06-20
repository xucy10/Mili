package net.minecraft.server.level;

import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import net.minecraft.util.thread.TaskScheduler;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

public class ThrottlingChunkTaskDispatcher extends ChunkTaskDispatcher {
    private final LongSet chunkPositionsInExecution = new LongOpenHashSet();
    private final int maxChunksInExecution;
    private final String executorSchedulerName;

    public ThrottlingChunkTaskDispatcher(TaskScheduler<Runnable> executor, Executor dispatcher, int maxChunksInExecution) {
        super(executor, dispatcher);
        this.maxChunksInExecution = maxChunksInExecution;
        this.executorSchedulerName = executor.name();
    }

    @Override
    protected void onRelease(long chunkPos) {
        this.chunkPositionsInExecution.remove(chunkPos);
    }

    @Override
    protected ChunkTaskPriorityQueue.@Nullable TasksForChunk popTasks() {
        return this.chunkPositionsInExecution.size() < this.maxChunksInExecution ? super.popTasks() : null;
    }

    @Override
    protected void scheduleForExecution(ChunkTaskPriorityQueue.TasksForChunk tasks) {
        this.chunkPositionsInExecution.add(tasks.chunkPos());
        super.scheduleForExecution(tasks);
    }

    @VisibleForTesting
    public String getDebugStatus() {
        return this.executorSchedulerName
            + "=["
            + this.chunkPositionsInExecution.longStream().mapToObj(l -> l + ":" + new ChunkPos(l)).collect(Collectors.joining(","))
            + "], s="
            + this.sleeping;
    }
}
