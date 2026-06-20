package net.minecraft.world.level.entity;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;

public class TransientEntitySectionManager<T extends EntityAccess> {
    static final Logger LOGGER = LogUtils.getLogger();
    final LevelCallback<T> callbacks;
    final EntityLookup<T> entityStorage;
    final EntitySectionStorage<T> sectionStorage;
    private final LongSet tickingChunks = new LongOpenHashSet();
    private final LevelEntityGetter<T> entityGetter;

    public TransientEntitySectionManager(Class<T> clazz, LevelCallback<T> callbacks) {
        this.entityStorage = new EntityLookup<>();
        this.sectionStorage = new EntitySectionStorage<>(
            clazz, packedChunkPos -> this.tickingChunks.contains(packedChunkPos) ? Visibility.TICKING : Visibility.TRACKED
        );
        this.callbacks = callbacks;
        this.entityGetter = new LevelEntityGetterAdapter<>(this.entityStorage, this.sectionStorage);
    }

    public void startTicking(ChunkPos pos) {
        long packedChunkPos = pos.toLong();
        this.tickingChunks.add(packedChunkPos);
        this.sectionStorage.getExistingSectionsInChunk(packedChunkPos).forEach(section -> {
            Visibility visibility = section.updateChunkStatus(Visibility.TICKING);
            if (!visibility.isTicking()) {
                section.getEntities().filter(entity -> !entity.isAlwaysTicking()).forEach(this.callbacks::onTickingStart);
            }
        });
    }

    public void stopTicking(ChunkPos pos) {
        long packedChunkPos = pos.toLong();
        this.tickingChunks.remove(packedChunkPos);
        this.sectionStorage.getExistingSectionsInChunk(packedChunkPos).forEach(section -> {
            Visibility visibility = section.updateChunkStatus(Visibility.TRACKED);
            if (visibility.isTicking()) {
                section.getEntities().filter(entity -> !entity.isAlwaysTicking()).forEach(this.callbacks::onTickingEnd);
            }
        });
    }

    public LevelEntityGetter<T> getEntityGetter() {
        return this.entityGetter;
    }

    public void addEntity(T entity) {
        this.entityStorage.add(entity);
        long packedSectionPos = SectionPos.asLong(entity.blockPosition());
        EntitySection<T> section = this.sectionStorage.getOrCreateSection(packedSectionPos);
        section.add(entity);
        entity.setLevelCallback(new TransientEntitySectionManager.Callback(entity, packedSectionPos, section));
        this.callbacks.onCreated(entity);
        this.callbacks.onTrackingStart(entity);
        if (entity.isAlwaysTicking() || section.getStatus().isTicking()) {
            this.callbacks.onTickingStart(entity);
        }
    }

    @VisibleForDebug
    public int count() {
        return this.entityStorage.count();
    }

    void removeSectionIfEmpty(long section, EntitySection<T> entitySection) {
        if (entitySection.isEmpty()) {
            this.sectionStorage.remove(section);
        }
    }

    @VisibleForDebug
    public String gatherStats() {
        return this.entityStorage.count() + "," + this.sectionStorage.count() + "," + this.tickingChunks.size();
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
                Visibility status = this.currentSection.getStatus();
                if (!this.currentSection.remove(this.entity)) {
                    TransientEntitySectionManager.LOGGER
                        .warn("Entity {} wasn't found in section {} (moving to {})", this.entity, SectionPos.of(this.currentSectionKey), packedSectionPos);
                }

                TransientEntitySectionManager.this.removeSectionIfEmpty(this.currentSectionKey, this.currentSection);
                EntitySection<T> section = TransientEntitySectionManager.this.sectionStorage.getOrCreateSection(packedSectionPos);
                section.add(this.entity);
                this.currentSection = section;
                this.currentSectionKey = packedSectionPos;
                TransientEntitySectionManager.this.callbacks.onSectionChange(this.entity);
                if (!this.entity.isAlwaysTicking()) {
                    boolean isTicking = status.isTicking();
                    boolean isTicking1 = section.getStatus().isTicking();
                    if (isTicking && !isTicking1) {
                        TransientEntitySectionManager.this.callbacks.onTickingEnd(this.entity);
                    } else if (!isTicking && isTicking1) {
                        TransientEntitySectionManager.this.callbacks.onTickingStart(this.entity);
                    }
                }
            }
        }

        @Override
        public void onRemove(Entity.RemovalReason reason) {
            if (!this.currentSection.remove(this.entity)) {
                TransientEntitySectionManager.LOGGER
                    .warn("Entity {} wasn't found in section {} (destroying due to {})", this.entity, SectionPos.of(this.currentSectionKey), reason);
            }

            Visibility status = this.currentSection.getStatus();
            if (status.isTicking() || this.entity.isAlwaysTicking()) {
                TransientEntitySectionManager.this.callbacks.onTickingEnd(this.entity);
            }

            TransientEntitySectionManager.this.callbacks.onTrackingEnd(this.entity);
            TransientEntitySectionManager.this.callbacks.onDestroyed(this.entity);
            TransientEntitySectionManager.this.entityStorage.remove(this.entity);
            this.entity.setLevelCallback(NULL);
            TransientEntitySectionManager.this.removeSectionIfEmpty(this.currentSectionKey, this.currentSection);
        }
    }
}
