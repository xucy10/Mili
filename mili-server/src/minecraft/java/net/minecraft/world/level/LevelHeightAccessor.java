package net.minecraft.world.level;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

public interface LevelHeightAccessor {
    int getHeight();

    int getMinY();

    default int getMaxY() {
        return this.getMinY() + this.getHeight() - 1;
    }

    default int getSectionsCount() {
        return this.getMaxSectionY() - this.getMinSectionY() + 1;
    }

    default int getMinSectionY() {
        return SectionPos.blockToSectionCoord(this.getMinY());
    }

    default int getMaxSectionY() {
        return SectionPos.blockToSectionCoord(this.getMaxY());
    }

    default boolean isInsideBuildHeight(int y) {
        return y >= this.getMinY() && y <= this.getMaxY();
    }

    default boolean isOutsideBuildHeight(BlockPos pos) {
        return this.isOutsideBuildHeight(pos.getY());
    }

    default boolean isOutsideBuildHeight(int y) {
        return y < this.getMinY() || y > this.getMaxY();
    }

    default int getSectionIndex(int y) {
        return this.getSectionIndexFromSectionY(SectionPos.blockToSectionCoord(y));
    }

    default int getSectionIndexFromSectionY(int sectionIndex) {
        return sectionIndex - this.getMinSectionY();
    }

    default int getSectionYFromSectionIndex(int sectionIndex) {
        return sectionIndex + this.getMinSectionY();
    }

    static LevelHeightAccessor create(final int minBuildHeight, final int height) {
        return new LevelHeightAccessor() {
            @Override
            public int getHeight() {
                return height;
            }

            @Override
            public int getMinY() {
                return minBuildHeight;
            }
        };
    }
}
