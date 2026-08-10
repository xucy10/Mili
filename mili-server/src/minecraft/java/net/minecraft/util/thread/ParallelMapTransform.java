package net.minecraft.util.thread;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public class ParallelMapTransform {
    private static final int DEFAULT_TASKS_PER_THREAD = 16;

    public static <K, U, V> CompletableFuture<Map<K, V>> schedule(
        Map<K, U> inputs, BiFunction<K, U, @Nullable V> operation, int maxTasksPerBatch, Executor executor
    ) {
        int size = inputs.size();
        if (size == 0) {
            return CompletableFuture.completedFuture(Map.of());
        } else if (size == 1) {
            Entry<K, U> entry = inputs.entrySet().iterator().next();
            K key = entry.getKey();
            U value = entry.getValue();
            return CompletableFuture.supplyAsync(() -> {
                V object = operation.apply(key, value);
                return object != null ? Map.of(key, object) : Map.of();
            }, executor);
        } else {
            ParallelMapTransform.SplitterBase<K, U, V> splitterBase = (ParallelMapTransform.SplitterBase<K, U, V>)(size <= maxTasksPerBatch
                ? new ParallelMapTransform.SingleTaskSplitter<>(operation, size)
                : new ParallelMapTransform.BatchedTaskSplitter<>(operation, size, maxTasksPerBatch));
            return splitterBase.scheduleTasks(inputs, executor);
        }
    }

    public static <K, U, V> CompletableFuture<Map<K, V>> schedule(Map<K, U> inputs, BiFunction<K, U, @Nullable V> operation, Executor executor) {
        int i = Util.maxAllowedExecutorThreads() * 16;
        return schedule(inputs, operation, i, executor);
    }

    static class BatchedTaskSplitter<K, U, V> extends ParallelMapTransform.SplitterBase<K, U, V> {
        private final Map<K, V> result;
        private final int batchSize;
        private final int firstUndersizedBatchIndex;

        BatchedTaskSplitter(BiFunction<K, U, V> operation, int containerSize, int numBatches) {
            super(operation, containerSize, numBatches);
            this.result = new HashMap<>(containerSize);
            this.batchSize = Mth.positiveCeilDiv(containerSize, numBatches);
            int i = this.batchSize * numBatches;
            int i1 = i - containerSize;
            this.firstUndersizedBatchIndex = numBatches - i1;

            assert this.firstUndersizedBatchIndex > 0 && this.firstUndersizedBatchIndex <= numBatches;
        }

        @Override
        protected CompletableFuture<?> scheduleBatch(
            ParallelMapTransform.Container<K, U, V> container, int lastScheduledIndex, int currentIndex, Executor executor
        ) {
            int i = currentIndex - lastScheduledIndex;

            assert i == this.batchSize || i == this.batchSize - 1;

            return CompletableFuture.runAsync(createTask(this.result, lastScheduledIndex, currentIndex, container), executor);
        }

        @Override
        protected int batchSize(int batchIndex) {
            return batchIndex < this.firstUndersizedBatchIndex ? this.batchSize : this.batchSize - 1;
        }

        private static <K, U, V> Runnable createTask(
            Map<K, V> result, int lastScheduledIndex, int currentIndex, ParallelMapTransform.Container<K, U, V> container
        ) {
            return () -> {
                for (int i = lastScheduledIndex; i < currentIndex; i++) {
                    container.applyOperation(i);
                }

                synchronized (result) {
                    for (int i1 = lastScheduledIndex; i1 < currentIndex; i1++) {
                        container.copyOut(i1, result);
                    }
                }
            };
        }

        @Override
        protected CompletableFuture<Map<K, V>> scheduleFinalOperation(CompletableFuture<?> future, ParallelMapTransform.Container<K, U, V> container) {
            Map<K, V> map = this.result;
            return future.thenApply(object -> map);
        }
    }

    record Container<K, U, V>(BiFunction<K, U, V> operation, @Nullable Object[] keys, @Nullable Object[] values) {
        public Container(BiFunction<K, U, V> operation, int size) {
            this(operation, new Object[size], new Object[size]);
        }

        public void put(int index, K key, U value) {
            this.keys[index] = key;
            this.values[index] = value;
        }

        private @Nullable K key(int index) {
            return (K)this.keys[index];
        }

        private @Nullable V output(int index) {
            return (V)this.values[index];
        }

        private @Nullable U input(int index) {
            return (U)this.values[index];
        }

        public void applyOperation(int index) {
            this.values[index] = this.operation.apply(this.key(index), this.input(index));
        }

        public void copyOut(int index, Map<K, V> outputMap) {
            V object = this.output(index);
            if (object != null) {
                K object1 = this.key(index);
                outputMap.put(object1, object);
            }
        }

        public int size() {
            return this.keys.length;
        }
    }

    static class SingleTaskSplitter<K, U, V> extends ParallelMapTransform.SplitterBase<K, U, V> {
        SingleTaskSplitter(BiFunction<K, U, V> operation, int size) {
            super(operation, size, size);
        }

        @Override
        protected int batchSize(int batchIndex) {
            return 1;
        }

        @Override
        protected CompletableFuture<?> scheduleBatch(
            ParallelMapTransform.Container<K, U, V> container, int lastScheduledIndex, int currentIndex, Executor executor
        ) {
            assert lastScheduledIndex + 1 == currentIndex;

            return CompletableFuture.runAsync(() -> container.applyOperation(lastScheduledIndex), executor);
        }

        @Override
        protected CompletableFuture<Map<K, V>> scheduleFinalOperation(CompletableFuture<?> future, ParallelMapTransform.Container<K, U, V> container) {
            return future.thenApply(object -> {
                Map<K, V> map = new HashMap<>(container.size());

                for (int i = 0; i < container.size(); i++) {
                    container.copyOut(i, map);
                }

                return map;
            });
        }
    }

    abstract static class SplitterBase<K, U, V> {
        private int lastScheduledIndex;
        private int currentIndex;
        private final CompletableFuture<?>[] tasks;
        private int batchIndex;
        private final ParallelMapTransform.Container<K, U, V> container;

        SplitterBase(BiFunction<K, U, V> operation, int containerSize, int numBatches) {
            this.container = new ParallelMapTransform.Container<>(operation, containerSize);
            this.tasks = new CompletableFuture[numBatches];
        }

        private int pendingBatchSize() {
            return this.currentIndex - this.lastScheduledIndex;
        }

        public CompletableFuture<Map<K, V>> scheduleTasks(Map<K, U> inputs, Executor executor) {
            inputs.forEach((key, value) -> {
                this.container.put(this.currentIndex++, (K)key, (U)value);
                if (this.pendingBatchSize() == this.batchSize(this.batchIndex)) {
                    this.tasks[this.batchIndex++] = this.scheduleBatch(this.container, this.lastScheduledIndex, this.currentIndex, executor);
                    this.lastScheduledIndex = this.currentIndex;
                }
            });

            assert this.currentIndex == this.container.size();

            assert this.lastScheduledIndex == this.currentIndex;

            assert this.batchIndex == this.tasks.length;

            return this.scheduleFinalOperation(CompletableFuture.allOf(this.tasks), this.container);
        }

        protected abstract int batchSize(int batchIndex);

        protected abstract CompletableFuture<?> scheduleBatch(
            ParallelMapTransform.Container<K, U, V> container, int lastScheduledIndex, int currentIndex, Executor executor
        );

        protected abstract CompletableFuture<Map<K, V>> scheduleFinalOperation(CompletableFuture<?> future, ParallelMapTransform.Container<K, U, V> container);
    }
}
