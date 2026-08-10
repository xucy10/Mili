package net.minecraft.world.level.chunk.storage;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.thread.ConsecutiveExecutor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.entity.ChunkEntities;
import net.minecraft.world.level.entity.EntityPersistentStorage;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import org.slf4j.Logger;

public class EntityStorage implements EntityPersistentStorage<Entity> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ENTITIES_TAG = "Entities";
    private static final String POSITION_TAG = "Position";
    public final ServerLevel level;
    private final SimpleRegionStorage simpleRegionStorage;
    private final LongSet emptyChunks = new LongOpenHashSet();
    public final ConsecutiveExecutor entityDeserializerQueue;

    public EntityStorage(SimpleRegionStorage simpleRegionStorage, ServerLevel level, Executor executor) {
        this.simpleRegionStorage = simpleRegionStorage;
        this.level = level;
        this.entityDeserializerQueue = new ConsecutiveExecutor(executor, "entity-deserializer");
    }

    @Override
    public CompletableFuture<ChunkEntities<Entity>> loadEntities(ChunkPos pos) {
        if (this.emptyChunks.contains(pos.toLong())) {
            return CompletableFuture.completedFuture(emptyChunk(pos));
        } else {
            CompletableFuture<Optional<CompoundTag>> completableFuture = this.simpleRegionStorage.read(pos);
            this.reportLoadFailureIfPresent(completableFuture, pos);
            return completableFuture.thenApplyAsync(entitiesTag -> {
                if (entitiesTag.isEmpty()) {
                    this.emptyChunks.add(pos.toLong());
                    return emptyChunk(pos);
                } else {
                    try {
                        ChunkPos chunkPos = entitiesTag.get().read("Position", ChunkPos.CODEC).orElseThrow();
                        if (!Objects.equals(pos, chunkPos)) {
                            LOGGER.error("Chunk file at {} is in the wrong location. (Expected {}, got {})", pos, pos, chunkPos);
                            this.level.getServer().reportMisplacedChunk(chunkPos, pos, this.simpleRegionStorage.storageInfo());
                        }
                    } catch (Exception var11) {
                        LOGGER.warn("Failed to parse chunk {} position info", pos, var11);
                        this.level.getServer().reportChunkLoadFailure(var11, this.simpleRegionStorage.storageInfo(), pos);
                    }

                    CompoundTag compoundTag = this.simpleRegionStorage.upgradeChunkTag(entitiesTag.get(), -1);

                    ChunkEntities var8;
                    try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(ChunkAccess.problemPath(pos), LOGGER)) {
                        ValueInput valueInput = TagValueInput.create(scopedCollector, this.level.registryAccess(), compoundTag);
                        ValueInput.ValueInputList valueInputList = valueInput.childrenListOrEmpty("Entities");
                        List<Entity> list = EntityType.loadEntitiesRecursive(valueInputList, this.level, EntitySpawnReason.LOAD).toList();
                        var8 = new ChunkEntities<>(pos, list);
                    }

                    return var8;
                }
            }, this.entityDeserializerQueue::schedule);
        }
    }

    private static ChunkEntities<Entity> emptyChunk(ChunkPos pos) {
        return new ChunkEntities<>(pos, List.of());
    }

    @Override
    public void storeEntities(ChunkEntities<Entity> entities) {
        ChunkPos pos = entities.getPos();
        if (entities.isEmpty()) {
            if (this.emptyChunks.add(pos.toLong())) {
                this.reportSaveFailureIfPresent(this.simpleRegionStorage.write(pos, IOWorker.STORE_EMPTY), pos);
            }
        } else {
            try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(ChunkAccess.problemPath(pos), LOGGER)) {
                ListTag listTag = new ListTag();
                final java.util.Map<net.minecraft.world.entity.EntityType<?>, Integer> savedEntityCounts = new java.util.HashMap<>(); // Paper - Entity load/save limit per chunk
                entities.getEntities().forEach(entity -> {
                    // Paper start - Entity load/save limit per chunk
                    final EntityType<?> entityType = entity.getType();
                    final int saveLimit = this.level.paperConfig().chunks.entityPerChunkSaveLimit.getOrDefault(entityType, -1);
                    if (saveLimit > -1) {
                        if (savedEntityCounts.getOrDefault(entityType, 0) >= saveLimit) {
                            return;
                        }
                        savedEntityCounts.merge(entityType, 1, Integer::sum);
                    }
                    // Paper end - Entity load/save limit per chunk
                    TagValueOutput tagValueOutput = TagValueOutput.createWithContext(scopedCollector.forChild(entity.problemPath()), entity.registryAccess());
                    if (entity.save(tagValueOutput)) {
                        CompoundTag compoundTag1 = tagValueOutput.buildResult();
                        listTag.add(compoundTag1);
                    }
                });
                CompoundTag compoundTag = NbtUtils.addCurrentDataVersion(new CompoundTag());
                compoundTag.put("Entities", listTag);
                compoundTag.store("Position", ChunkPos.CODEC, pos);
                this.reportSaveFailureIfPresent(this.simpleRegionStorage.write(pos, compoundTag), pos);
                this.emptyChunks.remove(pos.toLong());
            }
        }
    }

    private void reportSaveFailureIfPresent(CompletableFuture<?> future, ChunkPos pos) {
        future.exceptionally(throwable -> {
            LOGGER.error("Failed to store entity chunk {}", pos, throwable);
            this.level.getServer().reportChunkSaveFailure(throwable, this.simpleRegionStorage.storageInfo(), pos);
            return null;
        });
    }

    private void reportLoadFailureIfPresent(CompletableFuture<?> future, ChunkPos pos) {
        future.exceptionally(throwable -> {
            LOGGER.error("Failed to load entity chunk {}", pos, throwable);
            this.level.getServer().reportChunkLoadFailure(throwable, this.simpleRegionStorage.storageInfo(), pos);
            return null;
        });
    }

    @Override
    public void flush(boolean synchronize) {
        this.simpleRegionStorage.synchronize(synchronize).join();
        this.entityDeserializerQueue.runAll();
    }

    @Override
    public void close() throws IOException {
        this.simpleRegionStorage.close();
    }
}
