package net.minecraft.world.level.entity;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.util.CsvOutput;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;

public class PersistentEntitySectionManager<T extends EntityAccess> implements AutoCloseable {
    static final Logger LOGGER = LogUtils.getLogger();
    final Set<UUID> knownUuids = Sets.newHashSet();
    final LevelCallback<T> callbacks;
    public final EntityPersistentStorage<T> permanentStorage;
    private final EntityLookup<T> visibleEntityStorage;
    final EntitySectionStorage<T> sectionStorage;
    private final LevelEntityGetter<T> entityGetter;
    private final Long2ObjectMap<Visibility> chunkVisibility = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectMap<PersistentEntitySectionManager.ChunkLoadStatus> chunkLoadStatuses = new Long2ObjectOpenHashMap<>();
    private final LongSet chunksToUnload = new LongOpenHashSet();
    private final Queue<ChunkEntities<T>> loadingInbox = Queues.newConcurrentLinkedQueue();

    public PersistentEntitySectionManager(Class<T> entityClass, LevelCallback<T> callbacks, EntityPersistentStorage<T> permanentStorage) {
        this.visibleEntityStorage = new EntityLookup<>();
        this.sectionStorage = new EntitySectionStorage<>(entityClass, this.chunkVisibility);
        this.chunkVisibility.defaultReturnValue(Visibility.HIDDEN);
        this.chunkLoadStatuses.defaultReturnValue(PersistentEntitySectionManager.ChunkLoadStatus.FRESH);
        this.callbacks = callbacks;
        this.permanentStorage = permanentStorage;
        this.entityGetter = new LevelEntityGetterAdapter<>(this.visibleEntityStorage, this.sectionStorage);
    }

    // CraftBukkit start - add method to get all entities in chunk
    public List<Entity> getEntities(ChunkPos chunkPos) {
        return this.sectionStorage.getExistingSectionsInChunk(chunkPos.toLong()).flatMap(EntitySection::getEntities).map(entity -> (Entity) entity).collect(Collectors.toList());
    }

    public boolean isPending(long pair) {
        return this.chunkLoadStatuses.get(pair) == ChunkLoadStatus.PENDING;
    }
    // CraftBukkit end

    void removeSectionIfEmpty(long sectionKey, EntitySection<T> section) {
        if (section.isEmpty()) {
            this.sectionStorage.remove(sectionKey);
        }
    }

    private boolean addEntityUuid(T entity) {
        org.spigotmc.AsyncCatcher.catchOp("Entity add by UUID"); // Paper
        if (!this.knownUuids.add(entity.getUUID())) {
            LOGGER.warn("UUID of added entity already exists: {}", entity);
            return false;
        } else {
            return true;
        }
    }

    public boolean addNewEntity(T entity) {
        return this.addEntity(entity, false);
    }

    private boolean addEntity(T entity, boolean worldGenSpawned) {
        org.spigotmc.AsyncCatcher.catchOp("Entity add"); // Paper
        // Paper start - chunk system hooks
        // I don't want to know why this is a generic type.
        Entity entityCasted = (Entity)entity;
        boolean wasRemoved = entityCasted.isRemoved();
        boolean screened = ca.spottedleaf.moonrise.common.PlatformHooks.get().screenEntity((net.minecraft.server.level.ServerLevel)entityCasted.level(), entityCasted, worldGenSpawned, true);
        if ((!wasRemoved && entityCasted.isRemoved()) || !screened) {
            // removed by callback
            return false;
        }
        // Paper end - chunk system hooks
        if (!this.addEntityUuid(entity)) {
            return false;
        } else {
            long packedSectionPos = SectionPos.asLong(entity.blockPosition());
            EntitySection<T> section = this.sectionStorage.getOrCreateSection(packedSectionPos);
            section.add(entity);
            entity.setLevelCallback(new PersistentEntitySectionManager.Callback(entity, packedSectionPos, section));
            if (!worldGenSpawned) {
                this.callbacks.onCreated(entity);
            }

            Visibility effectiveStatus = getEffectiveStatus(entity, section.getStatus());
            if (effectiveStatus.isAccessible()) {
                this.startTracking(entity);
            }

            if (effectiveStatus.isTicking()) {
                this.startTicking(entity);
            }

            return true;
        }
    }

    static <T extends EntityAccess> Visibility getEffectiveStatus(T entity, Visibility visibility) {
        return entity.isAlwaysTicking() ? Visibility.TICKING : visibility;
    }

    public boolean isTicking(ChunkPos chunkPos) {
        return this.chunkVisibility.get(chunkPos.toLong()).isTicking();
    }

    public void addLegacyChunkEntities(Stream<T> entities) {
        entities.forEach(entity -> this.addEntity((T)entity, true));
    }

    public void addWorldGenChunkEntities(Stream<T> entities) {
        entities.forEach(entity -> this.addEntity((T)entity, false));
    }

    void startTicking(T entity) {
        org.spigotmc.AsyncCatcher.catchOp("Entity start ticking"); // Paper
        this.callbacks.onTickingStart(entity);
    }

    void stopTicking(T entity) {
        org.spigotmc.AsyncCatcher.catchOp("Entity stop ticking"); // Paper
        this.callbacks.onTickingEnd(entity);
    }

    void startTracking(T entity) {
        org.spigotmc.AsyncCatcher.catchOp("Entity start tracking"); // Paper
        this.visibleEntityStorage.add(entity);
        this.callbacks.onTrackingStart(entity);
    }

    void stopTracking(T entity) {
        org.spigotmc.AsyncCatcher.catchOp("Entity stop tracking"); // Paper
        this.callbacks.onTrackingEnd(entity);
        this.visibleEntityStorage.remove(entity);
    }

    public void updateChunkStatus(ChunkPos chunkPos, FullChunkStatus fullChunkStatus) {
        Visibility visibility = Visibility.fromFullChunkStatus(fullChunkStatus);
        this.updateChunkStatus(chunkPos, visibility);
    }

    public void updateChunkStatus(ChunkPos pos, Visibility visibility) {
        org.spigotmc.AsyncCatcher.catchOp("Update chunk status"); // Paper
        long packedChunkPos = pos.toLong();
        if (visibility == Visibility.HIDDEN) {
            this.chunkVisibility.remove(packedChunkPos);
            this.chunksToUnload.add(packedChunkPos);
        } else {
            this.chunkVisibility.put(packedChunkPos, visibility);
            this.chunksToUnload.remove(packedChunkPos);
            this.ensureChunkQueuedForLoad(packedChunkPos);
        }

        this.sectionStorage.getExistingSectionsInChunk(packedChunkPos).forEach(entitySection -> {
            Visibility visibility1 = entitySection.updateChunkStatus(visibility);
            boolean isAccessible = visibility1.isAccessible();
            boolean isAccessible1 = visibility.isAccessible();
            boolean isTicking = visibility1.isTicking();
            boolean isTicking1 = visibility.isTicking();
            if (isTicking && !isTicking1) {
                entitySection.getEntities().filter(entity -> !entity.isAlwaysTicking()).forEach(this::stopTicking);
            }

            if (isAccessible && !isAccessible1) {
                entitySection.getEntities().filter(entity -> !entity.isAlwaysTicking()).forEach(this::stopTracking);
            } else if (!isAccessible && isAccessible1) {
                entitySection.getEntities().filter(entity -> !entity.isAlwaysTicking()).forEach(this::startTracking);
            }

            if (!isTicking && isTicking1) {
                entitySection.getEntities().filter(entity -> !entity.isAlwaysTicking()).forEach(this::startTicking);
            }
        });
    }

    public void ensureChunkQueuedForLoad(long chunkPosValue) {
        org.spigotmc.AsyncCatcher.catchOp("Entity chunk save"); // Paper
        PersistentEntitySectionManager.ChunkLoadStatus chunkLoadStatus = this.chunkLoadStatuses.get(chunkPosValue);
        if (chunkLoadStatus == PersistentEntitySectionManager.ChunkLoadStatus.FRESH) {
            this.requestChunkLoad(chunkPosValue);
        }
    }

    private boolean storeChunkSections(long chunkPosValue, Consumer<T> entityAction) {
        // CraftBukkit start
        return storeChunkSections(chunkPosValue, entityAction, false);
    }
    private boolean storeChunkSections(long chunkPosValue, Consumer<T> entityAction, boolean callEvent) {
        // CraftBukkit end
        PersistentEntitySectionManager.ChunkLoadStatus chunkLoadStatus = this.chunkLoadStatuses.get(chunkPosValue);
        if (chunkLoadStatus == PersistentEntitySectionManager.ChunkLoadStatus.PENDING) {
            return false;
        } else {
            List<T> list = this.sectionStorage
                .getExistingSectionsInChunk(chunkPosValue)
                .flatMap(entitySection -> entitySection.getEntities().filter(EntityAccess::shouldBeSaved))
                .collect(Collectors.toList());
            if (list.isEmpty()) {
                if (chunkLoadStatus == PersistentEntitySectionManager.ChunkLoadStatus.LOADED) {
                    if (callEvent) org.bukkit.craftbukkit.event.CraftEventFactory.callEntitiesUnloadEvent(((net.minecraft.world.level.chunk.storage.EntityStorage) this.permanentStorage).level, new ChunkPos(chunkPosValue), ImmutableList.of()); // CraftBukkit
                    this.permanentStorage.storeEntities(new ChunkEntities<>(new ChunkPos(chunkPosValue), ImmutableList.of()));
                }

                return true;
            } else if (chunkLoadStatus == PersistentEntitySectionManager.ChunkLoadStatus.FRESH) {
                this.requestChunkLoad(chunkPosValue);
                return false;
            } else {
                if (callEvent) org.bukkit.craftbukkit.event.CraftEventFactory.callEntitiesUnloadEvent(((net.minecraft.world.level.chunk.storage.EntityStorage) this.permanentStorage).level, new ChunkPos(chunkPosValue), list.stream().map(entity -> (Entity) entity).collect(Collectors.toList())); // CraftBukkit
                this.permanentStorage.storeEntities(new ChunkEntities<>(new ChunkPos(chunkPosValue), list));
                list.forEach(entityAction);
                return true;
            }
        }
    }

    private void requestChunkLoad(long chunkPosValue) {
        org.spigotmc.AsyncCatcher.catchOp("Entity chunk load request"); // Paper
        this.chunkLoadStatuses.put(chunkPosValue, PersistentEntitySectionManager.ChunkLoadStatus.PENDING);
        ChunkPos chunkPos = new ChunkPos(chunkPosValue);
        this.permanentStorage.loadEntities(chunkPos).thenAccept(this.loadingInbox::add).exceptionally(throwable -> {
            LOGGER.error("Failed to read chunk {}", chunkPos, throwable);
            return null;
        });
    }

    private boolean processChunkUnload(long chunkPosValue) {
        org.spigotmc.AsyncCatcher.catchOp("Entity chunk unload process"); // Paper
        boolean flag = this.storeChunkSections(chunkPosValue, entity -> entity.getPassengersAndSelf().forEach(this::unloadEntity), true); // CraftBukkit - add boolean for event call
        if (!flag) {
            return false;
        } else {
            this.chunkLoadStatuses.remove(chunkPosValue);
            return true;
        }
    }

    private void unloadEntity(EntityAccess entity) {
        entity.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK, org.bukkit.event.entity.EntityRemoveEvent.Cause.UNLOAD); // CraftBukkit - add Bukkit remove cause
        entity.setLevelCallback(EntityInLevelCallback.NULL);
    }

    private void processUnloads() {
        this.chunksToUnload
            .removeIf(packedChunkPos -> this.chunkVisibility.get(packedChunkPos) != Visibility.HIDDEN || this.processChunkUnload(packedChunkPos));
    }

    public void processPendingLoads() {
        org.spigotmc.AsyncCatcher.catchOp("Entity chunk process pending loads"); // Paper
        ChunkEntities<T> chunkEntities;
        while ((chunkEntities = this.loadingInbox.poll()) != null) {
            chunkEntities.getEntities().forEach(entity -> this.addEntity((T)entity, true));
            this.chunkLoadStatuses.put(chunkEntities.getPos().toLong(), PersistentEntitySectionManager.ChunkLoadStatus.LOADED);
            // CraftBukkit start - call entity load event
            List<Entity> entities = this.getEntities(chunkEntities.getPos());
            org.bukkit.craftbukkit.event.CraftEventFactory.callEntitiesLoadEvent(((net.minecraft.world.level.chunk.storage.EntityStorage) this.permanentStorage).level, chunkEntities.getPos(), entities);
            // CraftBukkit end
        }
    }

    public void tick() {
        org.spigotmc.AsyncCatcher.catchOp("Entity manager tick"); // Paper
        this.processPendingLoads();
        this.processUnloads();
    }

    private LongSet getAllChunksToSave() {
        LongSet allChunksWithExistingSections = this.sectionStorage.getAllChunksWithExistingSections();

        for (Entry<PersistentEntitySectionManager.ChunkLoadStatus> entry : Long2ObjectMaps.fastIterable(this.chunkLoadStatuses)) {
            if (entry.getValue() == PersistentEntitySectionManager.ChunkLoadStatus.LOADED) {
                allChunksWithExistingSections.add(entry.getLongKey());
            }
        }

        return allChunksWithExistingSections;
    }

    public void autoSave() {
        org.spigotmc.AsyncCatcher.catchOp("Entity manager autosave"); // Paper
        this.getAllChunksToSave().forEach(packedChunkPos -> {
            boolean flag = this.chunkVisibility.get(packedChunkPos) == Visibility.HIDDEN;
            if (flag) {
                this.processChunkUnload(packedChunkPos);
            } else {
                this.storeChunkSections(packedChunkPos, entity -> {});
            }
        });
    }

    public void saveAll() {
        org.spigotmc.AsyncCatcher.catchOp("Entity manager save"); // Paper
        LongSet allChunksToSave = this.getAllChunksToSave();

        while (!allChunksToSave.isEmpty()) {
            this.permanentStorage.flush(false);
            this.processPendingLoads();
            allChunksToSave.removeIf(packedChunkPos -> {
                boolean flag = this.chunkVisibility.get(packedChunkPos) == Visibility.HIDDEN;
                return flag ? this.processChunkUnload(packedChunkPos) : this.storeChunkSections(packedChunkPos, entity -> {});
            });
        }

        this.permanentStorage.flush(true);
    }

    @Override
    public void close() throws IOException {
        // CraftBukkit start
        this.close(true);
    }

    public void close(boolean save) throws IOException {
        if (save) this.saveAll();
        // CraftBukkit end
        this.permanentStorage.close();
    }

    public boolean isLoaded(UUID uuid) {
        return this.knownUuids.contains(uuid);
    }

    public LevelEntityGetter<T> getEntityGetter() {
        return this.entityGetter;
    }

    public boolean canPositionTick(BlockPos pos) {
        return this.chunkVisibility.get(ChunkPos.asLong(pos)).isTicking();
    }

    public boolean canPositionTick(ChunkPos chunkPos) {
        return this.chunkVisibility.get(chunkPos.toLong()).isTicking();
    }

    public boolean areEntitiesLoaded(long chunkPos) {
        return this.chunkLoadStatuses.get(chunkPos) == PersistentEntitySectionManager.ChunkLoadStatus.LOADED;
    }

    public void dumpSections(Writer writer) throws IOException {
        CsvOutput csvOutput = CsvOutput.builder()
            .addColumn("x")
            .addColumn("y")
            .addColumn("z")
            .addColumn("visibility")
            .addColumn("load_status")
            .addColumn("entity_count")
            .build(writer);
        this.sectionStorage
            .getAllChunksWithExistingSections()
            .forEach(
                packedChunkPos -> {
                    PersistentEntitySectionManager.ChunkLoadStatus chunkLoadStatus = this.chunkLoadStatuses.get(packedChunkPos);
                    this.sectionStorage
                        .getExistingSectionPositionsInChunk(packedChunkPos)
                        .forEach(
                            packedSectionPos -> {
                                EntitySection<T> section = this.sectionStorage.getSection(packedSectionPos);
                                if (section != null) {
                                    try {
                                        csvOutput.writeRow(
                                            SectionPos.x(packedSectionPos),
                                            SectionPos.y(packedSectionPos),
                                            SectionPos.z(packedSectionPos),
                                            section.getStatus(),
                                            chunkLoadStatus,
                                            section.size()
                                        );
                                    } catch (IOException var7) {
                                        throw new UncheckedIOException(var7);
                                    }
                                }
                            }
                        );
                }
            );
    }

    @VisibleForDebug
    public String gatherStats() {
        return this.knownUuids.size()
            + ","
            + this.visibleEntityStorage.count()
            + ","
            + this.sectionStorage.count()
            + ","
            + this.chunkLoadStatuses.size()
            + ","
            + this.chunkVisibility.size()
            + ","
            + this.loadingInbox.size()
            + ","
            + this.chunksToUnload.size();
    }

    @VisibleForDebug
    public int count() {
        return this.visibleEntityStorage.count();
    }

    class Callback implements EntityInLevelCallback {
        private final T entity;
        private long currentSectionKey;
        private EntitySection<T> currentSection;

        Callback(final T entity, final long currentSectionKey, final EntitySection<T> currentSection) {
            this.entity = entity;
            this.currentSectionKey = currentSectionKey;
            this.currentSection = currentSection;
        }

        @Override
        public void onMove() {
            BlockPos blockPos = this.entity.blockPosition();
            long packedSectionPos = SectionPos.asLong(blockPos);
            if (packedSectionPos != this.currentSectionKey) {
                org.spigotmc.AsyncCatcher.catchOp("Entity move"); // Paper
                Visibility status = this.currentSection.getStatus();
                if (!this.currentSection.remove(this.entity)) {
                    PersistentEntitySectionManager.LOGGER
                        .warn("Entity {} wasn't found in section {} (moving to {})", this.entity, SectionPos.of(this.currentSectionKey), packedSectionPos);
                }

                PersistentEntitySectionManager.this.removeSectionIfEmpty(this.currentSectionKey, this.currentSection);
                EntitySection<T> section = PersistentEntitySectionManager.this.sectionStorage.getOrCreateSection(packedSectionPos);
                section.add(this.entity);
                this.currentSection = section;
                this.currentSectionKey = packedSectionPos;
                this.updateStatus(status, section.getStatus());
            }
        }

        private void updateStatus(Visibility oldVisibility, Visibility newVisibility) {
            Visibility effectiveStatus = PersistentEntitySectionManager.getEffectiveStatus(this.entity, oldVisibility);
            Visibility effectiveStatus1 = PersistentEntitySectionManager.getEffectiveStatus(this.entity, newVisibility);
            if (effectiveStatus == effectiveStatus1) {
                if (effectiveStatus1.isAccessible()) {
                    PersistentEntitySectionManager.this.callbacks.onSectionChange(this.entity);
                }
            } else {
                boolean isAccessible = effectiveStatus.isAccessible();
                boolean isAccessible1 = effectiveStatus1.isAccessible();
                if (isAccessible && !isAccessible1) {
                    PersistentEntitySectionManager.this.stopTracking(this.entity);
                } else if (!isAccessible && isAccessible1) {
                    PersistentEntitySectionManager.this.startTracking(this.entity);
                }

                boolean isTicking = effectiveStatus.isTicking();
                boolean isTicking1 = effectiveStatus1.isTicking();
                if (isTicking && !isTicking1) {
                    PersistentEntitySectionManager.this.stopTicking(this.entity);
                } else if (!isTicking && isTicking1) {
                    PersistentEntitySectionManager.this.startTicking(this.entity);
                }

                if (isAccessible1) {
                    PersistentEntitySectionManager.this.callbacks.onSectionChange(this.entity);
                }
            }
        }

        @Override
        public void onRemove(Entity.RemovalReason reason) {
            org.spigotmc.AsyncCatcher.catchOp("Entity remove"); // Paper
            if (!this.currentSection.remove(this.entity)) {
                PersistentEntitySectionManager.LOGGER
                    .warn("Entity {} wasn't found in section {} (destroying due to {})", this.entity, SectionPos.of(this.currentSectionKey), reason);
            }

            Visibility effectiveStatus = PersistentEntitySectionManager.getEffectiveStatus(this.entity, this.currentSection.getStatus());
            if (effectiveStatus.isTicking()) {
                PersistentEntitySectionManager.this.stopTicking(this.entity);
            }

            if (effectiveStatus.isAccessible()) {
                PersistentEntitySectionManager.this.stopTracking(this.entity);
            }

            if (reason.shouldDestroy()) {
                PersistentEntitySectionManager.this.callbacks.onDestroyed(this.entity);
            }

            PersistentEntitySectionManager.this.knownUuids.remove(this.entity.getUUID());
            this.entity.setLevelCallback(NULL);
            PersistentEntitySectionManager.this.removeSectionIfEmpty(this.currentSectionKey, this.currentSection);
        }
    }

    static enum ChunkLoadStatus {
        FRESH,
        PENDING,
        LOADED;
    }
}
