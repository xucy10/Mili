package net.minecraft.world.level.lighting;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;

public class SkyLightSectionStorage extends LayerLightSectionStorage<SkyLightSectionStorage.SkyDataLayerStorageMap> {
    protected SkyLightSectionStorage(LightChunkGetter chunkSource) {
        super(
            LightLayer.SKY,
            chunkSource,
            new SkyLightSectionStorage.SkyDataLayerStorageMap(new Long2ObjectOpenHashMap<>(), new Long2IntOpenHashMap(), Integer.MAX_VALUE)
        );
    }

    @Override
    protected int getLightValue(long pos) {
        return this.getLightValue(pos, false);
    }

    protected int getLightValue(long packedPos, boolean updateAll) {
        long packedSectionPos = SectionPos.blockToSection(packedPos);
        int sectionY = SectionPos.y(packedSectionPos);
        SkyLightSectionStorage.SkyDataLayerStorageMap skyDataLayerStorageMap = updateAll ? this.updatingSectionData : this.visibleSectionData;
        int i = skyDataLayerStorageMap.topSections.get(SectionPos.getZeroNode(packedSectionPos));
        if (i != skyDataLayerStorageMap.currentLowestY && sectionY < i) {
            DataLayer dataLayer = this.getDataLayer(skyDataLayerStorageMap, packedSectionPos);
            if (dataLayer == null) {
                for (packedPos = BlockPos.getFlatIndex(packedPos); dataLayer == null; dataLayer = this.getDataLayer(skyDataLayerStorageMap, packedSectionPos)) {
                    if (++sectionY >= i) {
                        return 15;
                    }

                    packedSectionPos = SectionPos.offset(packedSectionPos, Direction.UP);
                }
            }

            return dataLayer.get(
                SectionPos.sectionRelative(BlockPos.getX(packedPos)),
                SectionPos.sectionRelative(BlockPos.getY(packedPos)),
                SectionPos.sectionRelative(BlockPos.getZ(packedPos))
            );
        } else {
            return updateAll && !this.lightOnInSection(packedSectionPos) ? 0 : 15;
        }
    }

    @Override
    protected void onNodeAdded(long sectionPos) {
        int sectionY = SectionPos.y(sectionPos);
        if (this.updatingSectionData.currentLowestY > sectionY) {
            this.updatingSectionData.currentLowestY = sectionY;
            this.updatingSectionData.topSections.defaultReturnValue(this.updatingSectionData.currentLowestY);
        }

        long zeroNode = SectionPos.getZeroNode(sectionPos);
        int i = this.updatingSectionData.topSections.get(zeroNode);
        if (i < sectionY + 1) {
            this.updatingSectionData.topSections.put(zeroNode, sectionY + 1);
        }
    }

    @Override
    protected void onNodeRemoved(long sectionPos) {
        long zeroNode = SectionPos.getZeroNode(sectionPos);
        int sectionY = SectionPos.y(sectionPos);
        if (this.updatingSectionData.topSections.get(zeroNode) == sectionY + 1) {
            long l;
            for (l = sectionPos; !this.storingLightForSection(l) && this.hasLightDataAtOrBelow(sectionY); l = SectionPos.offset(l, Direction.DOWN)) {
                sectionY--;
            }

            if (this.storingLightForSection(l)) {
                this.updatingSectionData.topSections.put(zeroNode, sectionY + 1);
            } else {
                this.updatingSectionData.topSections.remove(zeroNode);
            }
        }
    }

    @Override
    protected DataLayer createDataLayer(long sectionPos) {
        DataLayer dataLayer = this.queuedSections.get(sectionPos);
        if (dataLayer != null) {
            return dataLayer;
        } else {
            int i = this.updatingSectionData.topSections.get(SectionPos.getZeroNode(sectionPos));
            if (i != this.updatingSectionData.currentLowestY && SectionPos.y(sectionPos) < i) {
                long l = SectionPos.offset(sectionPos, Direction.UP);

                DataLayer dataLayer1;
                while ((dataLayer1 = this.getDataLayer(l, true)) == null) {
                    l = SectionPos.offset(l, Direction.UP);
                }

                return repeatFirstLayer(dataLayer1);
            } else {
                return this.lightOnInSection(sectionPos) ? new DataLayer(15) : new DataLayer();
            }
        }
    }

    private static DataLayer repeatFirstLayer(DataLayer dataLayer) {
        if (dataLayer.isDefinitelyHomogenous()) {
            return dataLayer.copy();
        } else {
            byte[] data = dataLayer.getData();
            byte[] bytes = new byte[2048];

            for (int i = 0; i < 16; i++) {
                System.arraycopy(data, 0, bytes, i * 128, 128);
            }

            return new DataLayer(bytes);
        }
    }

    protected boolean hasLightDataAtOrBelow(int y) {
        return y >= this.updatingSectionData.currentLowestY;
    }

    protected boolean isAboveData(long sectionPos) {
        long zeroNode = SectionPos.getZeroNode(sectionPos);
        int i = this.updatingSectionData.topSections.get(zeroNode);
        return i == this.updatingSectionData.currentLowestY || SectionPos.y(sectionPos) >= i;
    }

    protected int getTopSectionY(long sectionPos) {
        return this.updatingSectionData.topSections.get(sectionPos);
    }

    protected int getBottomSectionY() {
        return this.updatingSectionData.currentLowestY;
    }

    protected static final class SkyDataLayerStorageMap extends DataLayerStorageMap<SkyLightSectionStorage.SkyDataLayerStorageMap> {
        int currentLowestY;
        final Long2IntOpenHashMap topSections;

        public SkyDataLayerStorageMap(Long2ObjectOpenHashMap<DataLayer> map, Long2IntOpenHashMap topSections, int currentLowestY) {
            super(map);
            this.topSections = topSections;
            topSections.defaultReturnValue(currentLowestY);
            this.currentLowestY = currentLowestY;
        }

        @Override
        public SkyLightSectionStorage.SkyDataLayerStorageMap copy() {
            return new SkyLightSectionStorage.SkyDataLayerStorageMap(this.map.clone(), this.topSections.clone(), this.currentLowestY);
        }
    }
}
