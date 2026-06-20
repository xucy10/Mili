package net.minecraft.world.level.entity;

import it.unimi.dsi.fastutil.longs.Long2ObjectFunction;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import java.util.Objects;
import java.util.Spliterators;
import java.util.PrimitiveIterator.OfLong;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.minecraft.core.SectionPos;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

public class EntitySectionStorage<T extends EntityAccess> {
    public static final int CHONKY_ENTITY_SEARCH_GRACE = 2;
    public static final int MAX_NON_CHONKY_ENTITY_SIZE = 4;
    private final Class<T> entityClass;
    private final Long2ObjectFunction<Visibility> intialSectionVisibility;
    private final Long2ObjectMap<EntitySection<T>> sections = new Long2ObjectOpenHashMap<>();
    private final LongSortedSet sectionIds = new LongAVLTreeSet();

    public EntitySectionStorage(Class<T> entityClass, Long2ObjectFunction<Visibility> initialSectionVisibility) {
        this.entityClass = entityClass;
        this.intialSectionVisibility = initialSectionVisibility;
    }

    // Paper start - support retrieving all entities, regardless of whether they are accessible
    public Iterable<T> getAllEntities() {
        java.util.List<T> ret = new java.util.ArrayList<>();
        for (EntitySection<T> section : this.sections.values()) {
            section.getEntities(ret);
        }
        return ret;
    }
    // Paper end - support retrieving all entities, regardless of whether they are accessible

    public void forEachAccessibleNonEmptySection(AABB boundingBox, AbortableIterationConsumer<EntitySection<T>> consumer) {
        int sectionPosCoord = SectionPos.posToSectionCoord(boundingBox.minX - 2.0);
        int sectionPosCoord1 = SectionPos.posToSectionCoord(boundingBox.minY - 4.0);
        int sectionPosCoord2 = SectionPos.posToSectionCoord(boundingBox.minZ - 2.0);
        int sectionPosCoord3 = SectionPos.posToSectionCoord(boundingBox.maxX + 2.0);
        int sectionPosCoord4 = SectionPos.posToSectionCoord(boundingBox.maxY + 0.0);
        int sectionPosCoord5 = SectionPos.posToSectionCoord(boundingBox.maxZ + 2.0);

        for (int i = sectionPosCoord; i <= sectionPosCoord3; i++) {
            long packedSectionPos = SectionPos.asLong(i, 0, 0);
            long packedSectionPos1 = SectionPos.asLong(i, -1, -1);
            LongIterator longIterator = this.sectionIds.subSet(packedSectionPos, packedSectionPos1 + 1L).iterator();

            while (longIterator.hasNext()) {
                long l = longIterator.nextLong();
                int sectionY = SectionPos.y(l);
                int sectionZ = SectionPos.z(l);
                if (sectionY >= sectionPosCoord1 && sectionY <= sectionPosCoord4 && sectionZ >= sectionPosCoord2 && sectionZ <= sectionPosCoord5) {
                    EntitySection<T> entitySection = this.sections.get(l);
                    if (entitySection != null
                        && !entitySection.isEmpty()
                        && entitySection.getStatus().isAccessible()
                        && consumer.accept(entitySection).shouldAbort()) {
                        return;
                    }
                }
            }
        }
    }

    public LongStream getExistingSectionPositionsInChunk(long pos) {
        int x = ChunkPos.getX(pos);
        int z = ChunkPos.getZ(pos);
        LongSortedSet chunkSections = this.getChunkSections(x, z);
        if (chunkSections.isEmpty()) {
            return LongStream.empty();
        } else {
            OfLong ofLong = chunkSections.iterator();
            return StreamSupport.longStream(Spliterators.spliteratorUnknownSize(ofLong, 1301), false);
        }
    }

    private LongSortedSet getChunkSections(int x, int z) {
        long packedSectionPos = SectionPos.asLong(x, 0, z);
        long packedSectionPos1 = SectionPos.asLong(x, -1, z);
        return this.sectionIds.subSet(packedSectionPos, packedSectionPos1 + 1L);
    }

    public Stream<EntitySection<T>> getExistingSectionsInChunk(long pos) {
        return this.getExistingSectionPositionsInChunk(pos).mapToObj(this.sections::get).filter(Objects::nonNull);
    }

    private static long getChunkKeyFromSectionKey(long pos) {
        return ChunkPos.asLong(SectionPos.x(pos), SectionPos.z(pos));
    }

    public EntitySection<T> getOrCreateSection(long sectionPos) {
        return this.sections.computeIfAbsent(sectionPos, this::createSection);
    }

    public @Nullable EntitySection<T> getSection(long sectionPos) {
        return this.sections.get(sectionPos);
    }

    private EntitySection<T> createSection(long sectionPos) {
        long chunkKeyFromSectionKey = getChunkKeyFromSectionKey(sectionPos);
        Visibility visibility = this.intialSectionVisibility.get(chunkKeyFromSectionKey);
        this.sectionIds.add(sectionPos);
        return new EntitySection<>(this.entityClass, visibility);
    }

    public LongSet getAllChunksWithExistingSections() {
        LongSet set = new LongOpenHashSet();
        this.sections.keySet().forEach(sectionId -> set.add(getChunkKeyFromSectionKey(sectionId)));
        return set;
    }

    public void getEntities(AABB bounds, AbortableIterationConsumer<T> consumer) {
        this.forEachAccessibleNonEmptySection(bounds, section -> section.getEntities(bounds, consumer));
    }

    public <U extends T> void getEntities(EntityTypeTest<T, U> test, AABB bounds, AbortableIterationConsumer<U> consumer) {
        this.forEachAccessibleNonEmptySection(bounds, section -> section.getEntities(test, bounds, consumer));
    }

    public void remove(long sectionId) {
        this.sections.remove(sectionId);
        this.sectionIds.remove(sectionId);
    }

    @VisibleForDebug
    public int count() {
        return this.sectionIds.size();
    }
}
