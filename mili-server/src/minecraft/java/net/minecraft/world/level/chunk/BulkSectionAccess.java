package net.minecraft.world.level.chunk;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public class BulkSectionAccess implements AutoCloseable {
    private final LevelAccessor level;
    private final Long2ObjectMap<LevelChunkSection> acquiredSections = new Long2ObjectOpenHashMap<>();
    private @Nullable LevelChunkSection lastSection;
    private long lastSectionKey;

    public BulkSectionAccess(LevelAccessor level) {
        this.level = level;
    }

    public @Nullable LevelChunkSection getSection(BlockPos pos) {
        int sectionIndex = this.level.getSectionIndex(pos.getY());
        if (sectionIndex >= 0 && sectionIndex < this.level.getSectionsCount()) {
            long packedSectionPos = SectionPos.asLong(pos);
            if (this.lastSection == null || this.lastSectionKey != packedSectionPos) {
                this.lastSection = this.acquiredSections.computeIfAbsent(packedSectionPos, section -> {
                    ChunkAccess chunk = this.level.getChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
                    LevelChunkSection section1 = chunk.getSection(sectionIndex);
                    section1.acquire();
                    return section1;
                });
                this.lastSectionKey = packedSectionPos;
            }

            return this.lastSection;
        } else {
            return null;
        }
    }

    public BlockState getBlockState(BlockPos pos) {
        LevelChunkSection section = this.getSection(pos);
        if (section == null) {
            return Blocks.AIR.defaultBlockState();
        } else {
            int relativeBlockPosX = SectionPos.sectionRelative(pos.getX());
            int relativeBlockPosY = SectionPos.sectionRelative(pos.getY());
            int relativeBlockPosZ = SectionPos.sectionRelative(pos.getZ());
            return section.getBlockState(relativeBlockPosX, relativeBlockPosY, relativeBlockPosZ);
        }
    }

    @Override
    public void close() {
        for (LevelChunkSection levelChunkSection : this.acquiredSections.values()) {
            levelChunkSection.release();
        }
    }
}
