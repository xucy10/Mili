package net.minecraft.world.level.lighting;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;
import org.jspecify.annotations.Nullable;

public abstract class LayerLightSectionStorage<M extends DataLayerStorageMap<M>> {
    private final LightLayer layer;
    protected final LightChunkGetter chunkSource;
    protected final Long2ByteMap sectionStates = new Long2ByteOpenHashMap();
    private final LongSet columnsWithSources = new LongOpenHashSet();
    protected volatile M visibleSectionData;
    protected final M updatingSectionData;
    protected final LongSet changedSections = new LongOpenHashSet();
    protected final LongSet sectionsAffectedByLightUpdates = new LongOpenHashSet();
    protected final Long2ObjectMap<DataLayer> queuedSections = Long2ObjectMaps.synchronize(new Long2ObjectOpenHashMap<>());
    private final LongSet columnsToRetainQueuedDataFor = new LongOpenHashSet();
    private final LongSet toRemove = new LongOpenHashSet();
    protected volatile boolean hasInconsistencies;

    protected LayerLightSectionStorage(LightLayer layer, LightChunkGetter chunkSource, M updatingSectionData) {
        this.layer = layer;
        this.chunkSource = chunkSource;
        this.updatingSectionData = updatingSectionData;
        this.visibleSectionData = updatingSectionData.copy();
        this.visibleSectionData.disableCache();
        this.sectionStates.defaultReturnValue((byte)0);
    }

    protected boolean storingLightForSection(long sectionPos) {
        return this.getDataLayer(sectionPos, true) != null;
    }

    protected @Nullable DataLayer getDataLayer(long sectionPos, boolean cached) {
        return this.getDataLayer(cached ? this.updatingSectionData : this.visibleSectionData, sectionPos);
    }

    protected @Nullable DataLayer getDataLayer(M map, long sectionPos) {
        return map.getLayer(sectionPos);
    }

    protected @Nullable DataLayer getDataLayerToWrite(long sectionPos) {
        DataLayer layer = this.updatingSectionData.getLayer(sectionPos);
        if (layer == null) {
            return null;
        } else {
            if (this.changedSections.add(sectionPos)) {
                layer = layer.copy();
                this.updatingSectionData.setLayer(sectionPos, layer);
                this.updatingSectionData.clearCache();
            }

            return layer;
        }
    }

    public @Nullable DataLayer getDataLayerData(long sectionPos) {
        DataLayer dataLayer = this.queuedSections.get(sectionPos);
        return dataLayer != null ? dataLayer : this.getDataLayer(sectionPos, false);
    }

    protected abstract int getLightValue(long pos);

    protected int getStoredLevel(long pos) {
        long packedSectionPos = SectionPos.blockToSection(pos);
        DataLayer dataLayer = this.getDataLayer(packedSectionPos, true);
        return dataLayer.get(
            SectionPos.sectionRelative(BlockPos.getX(pos)), SectionPos.sectionRelative(BlockPos.getY(pos)), SectionPos.sectionRelative(BlockPos.getZ(pos))
        );
    }

    protected void setStoredLevel(long pos, int lightLevel) {
        long packedSectionPos = SectionPos.blockToSection(pos);
        DataLayer dataLayer;
        if (this.changedSections.add(packedSectionPos)) {
            dataLayer = this.updatingSectionData.copyDataLayer(packedSectionPos);
        } else {
            dataLayer = this.getDataLayer(packedSectionPos, true);
        }

        dataLayer.set(
            SectionPos.sectionRelative(BlockPos.getX(pos)),
            SectionPos.sectionRelative(BlockPos.getY(pos)),
            SectionPos.sectionRelative(BlockPos.getZ(pos)),
            lightLevel
        );
        SectionPos.aroundAndAtBlockPos(pos, this.sectionsAffectedByLightUpdates::add);
    }

    protected void markSectionAndNeighborsAsAffected(long sectionPos) {
        int sectionX = SectionPos.x(sectionPos);
        int sectionY = SectionPos.y(sectionPos);
        int sectionZ = SectionPos.z(sectionPos);

        for (int i = -1; i <= 1; i++) {
            for (int i1 = -1; i1 <= 1; i1++) {
                for (int i2 = -1; i2 <= 1; i2++) {
                    this.sectionsAffectedByLightUpdates.add(SectionPos.asLong(sectionX + i1, sectionY + i2, sectionZ + i));
                }
            }
        }
    }

    protected DataLayer createDataLayer(long sectionPos) {
        DataLayer dataLayer = this.queuedSections.get(sectionPos);
        return dataLayer != null ? dataLayer : new DataLayer();
    }

    protected boolean hasInconsistencies() {
        return this.hasInconsistencies;
    }

    protected void markNewInconsistencies(LightEngine<M, ?> lightEngine) {
        if (this.hasInconsistencies) {
            this.hasInconsistencies = false;

            for (long l : this.toRemove) {
                DataLayer dataLayer = this.queuedSections.remove(l);
                DataLayer dataLayer1 = this.updatingSectionData.removeLayer(l);
                if (this.columnsToRetainQueuedDataFor.contains(SectionPos.getZeroNode(l))) {
                    if (dataLayer != null) {
                        this.queuedSections.put(l, dataLayer);
                    } else if (dataLayer1 != null) {
                        this.queuedSections.put(l, dataLayer1);
                    }
                }
            }

            this.updatingSectionData.clearCache();

            for (long lx : this.toRemove) {
                this.onNodeRemoved(lx);
                this.changedSections.add(lx);
            }

            this.toRemove.clear();
            ObjectIterator<Entry<DataLayer>> objectIterator = Long2ObjectMaps.fastIterator(this.queuedSections);

            while (objectIterator.hasNext()) {
                Entry<DataLayer> entry = objectIterator.next();
                long longKey = entry.getLongKey();
                if (this.storingLightForSection(longKey)) {
                    DataLayer dataLayer1 = entry.getValue();
                    if (this.updatingSectionData.getLayer(longKey) != dataLayer1) {
                        this.updatingSectionData.setLayer(longKey, dataLayer1);
                        this.changedSections.add(longKey);
                    }

                    objectIterator.remove();
                }
            }

            this.updatingSectionData.clearCache();
        }
    }

    protected void onNodeAdded(long sectionPos) {
    }

    protected void onNodeRemoved(long sectionPos) {
    }

    protected void setLightEnabled(long sectionPos, boolean lightEnabled) {
        if (lightEnabled) {
            this.columnsWithSources.add(sectionPos);
        } else {
            this.columnsWithSources.remove(sectionPos);
        }
    }

    protected boolean lightOnInSection(long sectionPos) {
        long zeroNode = SectionPos.getZeroNode(sectionPos);
        return this.columnsWithSources.contains(zeroNode);
    }

    protected boolean lightOnInColumn(long columnPos) {
        return this.columnsWithSources.contains(columnPos);
    }

    public void retainData(long sectionColumnPos, boolean retain) {
        if (retain) {
            this.columnsToRetainQueuedDataFor.add(sectionColumnPos);
        } else {
            this.columnsToRetainQueuedDataFor.remove(sectionColumnPos);
        }
    }

    protected void queueSectionData(long sectionPos, @Nullable DataLayer data) {
        if (data != null) {
            this.queuedSections.put(sectionPos, data);
            this.hasInconsistencies = true;
        } else {
            this.queuedSections.remove(sectionPos);
        }
    }

    protected void updateSectionStatus(long sectionPos, boolean isEmpty) {
        byte b = this.sectionStates.get(sectionPos);
        byte b1 = LayerLightSectionStorage.SectionState.hasData(b, !isEmpty);
        if (b != b1) {
            this.putSectionState(sectionPos, b1);
            int i = isEmpty ? -1 : 1;

            for (int i1 = -1; i1 <= 1; i1++) {
                for (int i2 = -1; i2 <= 1; i2++) {
                    for (int i3 = -1; i3 <= 1; i3++) {
                        if (i1 != 0 || i2 != 0 || i3 != 0) {
                            long l = SectionPos.offset(sectionPos, i1, i2, i3);
                            byte b2 = this.sectionStates.get(l);
                            this.putSectionState(
                                l, LayerLightSectionStorage.SectionState.neighborCount(b2, LayerLightSectionStorage.SectionState.neighborCount(b2) + i)
                            );
                        }
                    }
                }
            }
        }
    }

    protected void putSectionState(long sectionPos, byte sectionState) {
        if (sectionState != 0) {
            if (this.sectionStates.put(sectionPos, sectionState) == 0) {
                this.initializeSection(sectionPos);
            }
        } else if (this.sectionStates.remove(sectionPos) != 0) {
            this.removeSection(sectionPos);
        }
    }

    private void initializeSection(long sectionPos) {
        if (!this.toRemove.remove(sectionPos)) {
            this.updatingSectionData.setLayer(sectionPos, this.createDataLayer(sectionPos));
            this.changedSections.add(sectionPos);
            this.onNodeAdded(sectionPos);
            this.markSectionAndNeighborsAsAffected(sectionPos);
            this.hasInconsistencies = true;
        }
    }

    private void removeSection(long sectionPos) {
        this.toRemove.add(sectionPos);
        this.hasInconsistencies = true;
    }

    protected void swapSectionMap() {
        if (!this.changedSections.isEmpty()) {
            M dataLayerStorageMap = this.updatingSectionData.copy();
            dataLayerStorageMap.disableCache();
            this.visibleSectionData = dataLayerStorageMap;
            this.changedSections.clear();
        }

        if (!this.sectionsAffectedByLightUpdates.isEmpty()) {
            LongIterator longIterator = this.sectionsAffectedByLightUpdates.iterator();

            while (longIterator.hasNext()) {
                long l = longIterator.nextLong();
                this.chunkSource.onLightUpdate(this.layer, SectionPos.of(l));
            }

            this.sectionsAffectedByLightUpdates.clear();
        }
    }

    public LayerLightSectionStorage.SectionType getDebugSectionType(long sectionPos) {
        return LayerLightSectionStorage.SectionState.type(this.sectionStates.get(sectionPos));
    }

    protected static class SectionState {
        public static final byte EMPTY = 0;
        private static final int MIN_NEIGHBORS = 0;
        private static final int MAX_NEIGHBORS = 26;
        private static final byte HAS_DATA_BIT = 32;
        private static final byte NEIGHBOR_COUNT_BITS = 31;

        public static byte hasData(byte sectionState, boolean hasData) {
            return (byte)(hasData ? sectionState | 32 : sectionState & -33);
        }

        public static byte neighborCount(byte sectionState, int neighborCount) {
            if (neighborCount >= 0 && neighborCount <= 26) {
                return (byte)(sectionState & -32 | neighborCount & 31);
            } else {
                throw new IllegalArgumentException("Neighbor count was not within range [0; 26]");
            }
        }

        public static boolean hasData(byte sectionState) {
            return (sectionState & 32) != 0;
        }

        public static int neighborCount(byte sectionState) {
            return sectionState & 31;
        }

        public static LayerLightSectionStorage.SectionType type(byte sectionState) {
            if (sectionState == 0) {
                return LayerLightSectionStorage.SectionType.EMPTY;
            } else {
                return hasData(sectionState) ? LayerLightSectionStorage.SectionType.LIGHT_AND_DATA : LayerLightSectionStorage.SectionType.LIGHT_ONLY;
            }
        }
    }

    public static enum SectionType {
        EMPTY("2"),
        LIGHT_ONLY("1"),
        LIGHT_AND_DATA("0");

        private final String display;

        private SectionType(final String display) {
            this.display = display;
        }

        public String display() {
            return this.display;
        }
    }
}
