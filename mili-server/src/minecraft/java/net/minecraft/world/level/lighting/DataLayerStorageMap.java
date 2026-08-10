package net.minecraft.world.level.lighting;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.level.chunk.DataLayer;
import org.jspecify.annotations.Nullable;

public abstract class DataLayerStorageMap<M extends DataLayerStorageMap<M>> {
    private static final int CACHE_SIZE = 2;
    private final long[] lastSectionKeys = new long[2];
    private final @Nullable DataLayer[] lastSections = new DataLayer[2];
    private boolean cacheEnabled;
    protected final Long2ObjectOpenHashMap<DataLayer> map;

    protected DataLayerStorageMap(Long2ObjectOpenHashMap<DataLayer> map) {
        this.map = map;
        this.clearCache();
        this.cacheEnabled = true;
    }

    public abstract M copy();

    public DataLayer copyDataLayer(long index) {
        DataLayer dataLayer = this.map.get(index).copy();
        this.map.put(index, dataLayer);
        this.clearCache();
        return dataLayer;
    }

    public boolean hasLayer(long sectionPos) {
        return this.map.containsKey(sectionPos);
    }

    public @Nullable DataLayer getLayer(long sectionPos) {
        if (this.cacheEnabled) {
            for (int i = 0; i < 2; i++) {
                if (sectionPos == this.lastSectionKeys[i]) {
                    return this.lastSections[i];
                }
            }
        }

        DataLayer dataLayer = this.map.get(sectionPos);
        if (dataLayer == null) {
            return null;
        } else {
            if (this.cacheEnabled) {
                for (int i1 = 1; i1 > 0; i1--) {
                    this.lastSectionKeys[i1] = this.lastSectionKeys[i1 - 1];
                    this.lastSections[i1] = this.lastSections[i1 - 1];
                }

                this.lastSectionKeys[0] = sectionPos;
                this.lastSections[0] = dataLayer;
            }

            return dataLayer;
        }
    }

    public @Nullable DataLayer removeLayer(long sectionPos) {
        return this.map.remove(sectionPos);
    }

    public void setLayer(long sectionPos, DataLayer array) {
        this.map.put(sectionPos, array);
    }

    public void clearCache() {
        for (int i = 0; i < 2; i++) {
            this.lastSectionKeys[i] = Long.MAX_VALUE;
            this.lastSections[i] = null;
        }
    }

    public void disableCache() {
        this.cacheEnabled = false;
    }
}
