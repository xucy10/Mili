package net.minecraft.world.level.chunk.storage;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import java.io.IOException;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.StreamTagVisitor;
import net.minecraft.nbt.visitors.CollectFields;
import net.minecraft.nbt.visitors.FieldSelector;
import net.minecraft.util.Unit;
import net.minecraft.util.Util;
import net.minecraft.util.thread.PriorityConsecutiveExecutor;
import net.minecraft.util.thread.StrictQueue;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class IOWorker implements ChunkScanAccess, AutoCloseable {
    public static final Supplier<CompoundTag> STORE_EMPTY = () -> null;
    private static final Logger LOGGER = LogUtils.getLogger();
    private final AtomicBoolean shutdownRequested = new AtomicBoolean();
    private final PriorityConsecutiveExecutor consecutiveExecutor;
    public final RegionFileStorage storage; // Paper - public
    private final SequencedMap<ChunkPos, IOWorker.PendingStore> pendingWrites = new LinkedHashMap<>();
    private final Long2ObjectLinkedOpenHashMap<CompletableFuture<BitSet>> regionCacheForBlender = new Long2ObjectLinkedOpenHashMap<>();
    private static final int REGION_CACHE_SIZE = 1024;

    protected IOWorker(RegionStorageInfo info, Path folder, boolean sync) {
        this.storage = new RegionFileStorage(info, folder, sync);
        this.consecutiveExecutor = new PriorityConsecutiveExecutor(IOWorker.Priority.values().length, Util.ioPool(), "IOWorker-" + info.type());
    }

    public boolean isOldChunkAround(ChunkPos chunkPos, int radius) {
        ChunkPos chunkPos1 = new ChunkPos(chunkPos.x - radius, chunkPos.z - radius);
        ChunkPos chunkPos2 = new ChunkPos(chunkPos.x + radius, chunkPos.z + radius);

        for (int regionX = chunkPos1.getRegionX(); regionX <= chunkPos2.getRegionX(); regionX++) {
            for (int regionZ = chunkPos1.getRegionZ(); regionZ <= chunkPos2.getRegionZ(); regionZ++) {
                BitSet bitSet = this.getOrCreateOldDataForRegion(regionX, regionZ).join();
                if (!bitSet.isEmpty()) {
                    ChunkPos chunkPos3 = ChunkPos.minFromRegion(regionX, regionZ);
                    int max = Math.max(chunkPos1.x - chunkPos3.x, 0);
                    int max1 = Math.max(chunkPos1.z - chunkPos3.z, 0);
                    int min = Math.min(chunkPos2.x - chunkPos3.x, 31);
                    int min1 = Math.min(chunkPos2.z - chunkPos3.z, 31);

                    for (int i = max; i <= min; i++) {
                        for (int i1 = max1; i1 <= min1; i1++) {
                            int i2 = i1 * 32 + i;
                            if (bitSet.get(i2)) {
                                return true;
                            }
                        }
                    }
                }
            }
        }

        return false;
    }

    private CompletableFuture<BitSet> getOrCreateOldDataForRegion(int chunkX, int chunkZ) {
        long packedChunkPos = ChunkPos.asLong(chunkX, chunkZ);
        synchronized (this.regionCacheForBlender) {
            CompletableFuture<BitSet> completableFuture = this.regionCacheForBlender.getAndMoveToFirst(packedChunkPos);
            if (completableFuture == null) {
                completableFuture = this.createOldDataForRegion(chunkX, chunkZ);
                this.regionCacheForBlender.putAndMoveToFirst(packedChunkPos, completableFuture);
                if (this.regionCacheForBlender.size() > 1024) {
                    this.regionCacheForBlender.removeLast();
                }
            }

            return completableFuture;
        }
    }

    private CompletableFuture<BitSet> createOldDataForRegion(int chunkX, int chunkZ) {
        return CompletableFuture.supplyAsync(
            () -> {
                ChunkPos chunkPos = ChunkPos.minFromRegion(chunkX, chunkZ);
                ChunkPos chunkPos1 = ChunkPos.maxFromRegion(chunkX, chunkZ);
                BitSet bitSet = new BitSet();
                ChunkPos.rangeClosed(chunkPos, chunkPos1)
                    .forEach(
                        currentChunk -> {
                            CollectFields collectFields = new CollectFields(
                                new FieldSelector(IntTag.TYPE, "DataVersion"), new FieldSelector(CompoundTag.TYPE, "blending_data")
                            );

                            try {
                                this.scanChunk(currentChunk, collectFields).join();
                            } catch (Exception var7) {
                                LOGGER.warn("Failed to scan chunk {}", currentChunk, var7);
                                return;
                            }

                            if (collectFields.getResult() instanceof CompoundTag compoundTag && this.isOldChunk(compoundTag)) {
                                int i = currentChunk.getRegionLocalZ() * 32 + currentChunk.getRegionLocalX();
                                bitSet.set(i);
                            }
                        }
                    );
                return bitSet;
            },
            Util.backgroundExecutor()
        );
    }

    private boolean isOldChunk(CompoundTag chunkData) {
        return chunkData.getIntOr("DataVersion", 0) < 4295 || chunkData.getCompound("blending_data").isPresent();
    }

    public CompletableFuture<Void> store(ChunkPos chunkPos, CompoundTag chunkData) {
        return this.store(chunkPos, () -> chunkData);
    }

    public CompletableFuture<Void> store(ChunkPos chunkPos, Supplier<CompoundTag> dataSupplier) {
        return this.<CompletableFuture<Void>>submitTask(() -> {
            CompoundTag compoundTag = dataSupplier.get();
            IOWorker.PendingStore pendingStore = this.pendingWrites.computeIfAbsent(chunkPos, chunkPos1 -> new IOWorker.PendingStore(compoundTag));
            pendingStore.data = compoundTag;
            return pendingStore.result;
        }).thenCompose(Function.identity());
    }

    public CompletableFuture<Optional<CompoundTag>> loadAsync(ChunkPos chunkPos) {
        return this.submitThrowingTask(() -> {
            IOWorker.PendingStore pendingStore = this.pendingWrites.get(chunkPos);
            if (pendingStore != null) {
                return Optional.ofNullable(pendingStore.copyData());
            } else {
                try {
                    CompoundTag compoundTag = this.storage.read(chunkPos);
                    return Optional.ofNullable(compoundTag);
                } catch (Exception var4) {
                    LOGGER.warn("Failed to read chunk {}", chunkPos, var4);
                    throw var4;
                }
            }
        });
    }

    public CompletableFuture<Void> synchronize(boolean flushStorage) {
        CompletableFuture<Void> completableFuture = this.<CompletableFuture<Void>>submitTask(
                () -> CompletableFuture.allOf(this.pendingWrites.values().stream().map(pendingStore -> pendingStore.result).toArray(CompletableFuture[]::new))
            )
            .thenCompose(Function.identity());
        return flushStorage ? completableFuture.thenCompose(_void -> this.submitThrowingTask(() -> {
            try {
                this.storage.flush();
                return null;
            } catch (Exception var2x) {
                LOGGER.warn("Failed to synchronize chunks", (Throwable)var2x);
                throw var2x;
            }
        })) : completableFuture.thenCompose(_void -> this.submitTask(() -> null));
    }

    @Override
    public CompletableFuture<Void> scanChunk(ChunkPos chunkPos, StreamTagVisitor visitor) {
        return this.submitThrowingTask(() -> {
            try {
                IOWorker.PendingStore pendingStore = this.pendingWrites.get(chunkPos);
                if (pendingStore != null) {
                    if (pendingStore.data != null) {
                        pendingStore.data.acceptAsRoot(visitor);
                    }
                } else {
                    this.storage.scanChunk(chunkPos, visitor);
                }

                return null;
            } catch (Exception var4) {
                LOGGER.warn("Failed to bulk scan chunk {}", chunkPos, var4);
                throw var4;
            }
        });
    }

    private <T> CompletableFuture<T> submitThrowingTask(IOWorker.ThrowingSupplier<T> task) {
        return this.consecutiveExecutor.scheduleWithResult(IOWorker.Priority.FOREGROUND.ordinal(), completableFuture -> {
            if (!this.shutdownRequested.get()) {
                try {
                    completableFuture.complete(task.get());
                } catch (Exception var4) {
                    completableFuture.completeExceptionally(var4);
                }
            }

            this.tellStorePending();
        });
    }

    private <T> CompletableFuture<T> submitTask(Supplier<T> task) {
        return this.consecutiveExecutor.scheduleWithResult(IOWorker.Priority.FOREGROUND.ordinal(), completableFuture -> {
            if (!this.shutdownRequested.get()) {
                completableFuture.complete(task.get());
            }

            this.tellStorePending();
        });
    }

    private void storePendingChunk() {
        Entry<ChunkPos, IOWorker.PendingStore> entry = this.pendingWrites.pollFirstEntry();
        if (entry != null) {
            this.runStore(entry.getKey(), entry.getValue());
            this.tellStorePending();
        }
    }

    private void tellStorePending() {
        this.consecutiveExecutor.schedule(new StrictQueue.RunnableWithPriority(IOWorker.Priority.BACKGROUND.ordinal(), this::storePendingChunk));
    }

    private void runStore(ChunkPos chunkPos, IOWorker.PendingStore pendingStore) {
        try {
            this.storage.write(chunkPos, pendingStore.data);
            pendingStore.result.complete(null);
        } catch (Exception var4) {
            LOGGER.error("Failed to store chunk {}", chunkPos, var4);
            pendingStore.result.completeExceptionally(var4);
        }
    }

    @Override
    public void close() throws IOException {
        if (this.shutdownRequested.compareAndSet(false, true)) {
            this.waitForShutdown();
            this.consecutiveExecutor.close();

            try {
                this.storage.close();
            } catch (Exception var2) {
                LOGGER.error("Failed to close storage", (Throwable)var2);
            }
        }
    }

    private void waitForShutdown() {
        this.consecutiveExecutor
            .scheduleWithResult(IOWorker.Priority.SHUTDOWN.ordinal(), completableFuture -> completableFuture.complete(Unit.INSTANCE))
            .join();
    }

    public RegionStorageInfo storageInfo() {
        return this.storage.info();
    }

    static class PendingStore {
        @Nullable CompoundTag data;
        final CompletableFuture<Void> result = new CompletableFuture<>();

        public PendingStore(@Nullable CompoundTag data) {
            this.data = data;
        }

        @Nullable CompoundTag copyData() {
            CompoundTag compoundTag = this.data;
            return compoundTag == null ? null : compoundTag.copy();
        }
    }

    static enum Priority {
        FOREGROUND,
        BACKGROUND,
        SHUTDOWN;
    }

    @FunctionalInterface
    interface ThrowingSupplier<T> {
        @Nullable T get() throws Exception;
    }
}
