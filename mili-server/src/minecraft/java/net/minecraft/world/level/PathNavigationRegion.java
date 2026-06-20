package net.minecraft.world.level;

import com.google.common.base.Suppliers;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class PathNavigationRegion implements CollisionGetter {
    protected final int centerX;
    protected final int centerZ;
    protected final ChunkAccess[][] chunks;
    protected boolean allEmpty;
    protected final Level level;
    private final Supplier<Holder<Biome>> plains;

    public PathNavigationRegion(Level level, BlockPos centerPos, BlockPos offsetPos) {
        this.level = level;
        this.plains = Suppliers.memoize(() -> level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS));
        this.centerX = SectionPos.blockToSectionCoord(centerPos.getX());
        this.centerZ = SectionPos.blockToSectionCoord(centerPos.getZ());
        int sectionPosX = SectionPos.blockToSectionCoord(offsetPos.getX());
        int sectionPosZ = SectionPos.blockToSectionCoord(offsetPos.getZ());
        this.chunks = new ChunkAccess[sectionPosX - this.centerX + 1][sectionPosZ - this.centerZ + 1];
        ChunkSource chunkSource = level.getChunkSource();
        this.allEmpty = true;

        for (int i = this.centerX; i <= sectionPosX; i++) {
            for (int i1 = this.centerZ; i1 <= sectionPosZ; i1++) {
                this.chunks[i - this.centerX][i1 - this.centerZ] = chunkSource.getChunkNow(i, i1);
            }
        }

        for (int i = SectionPos.blockToSectionCoord(centerPos.getX()); i <= SectionPos.blockToSectionCoord(offsetPos.getX()); i++) {
            for (int i1 = SectionPos.blockToSectionCoord(centerPos.getZ()); i1 <= SectionPos.blockToSectionCoord(offsetPos.getZ()); i1++) {
                ChunkAccess chunkAccess = this.chunks[i - this.centerX][i1 - this.centerZ];
                if (chunkAccess != null && !chunkAccess.isYSpaceEmpty(centerPos.getY(), offsetPos.getY())) {
                    this.allEmpty = false;
                    return;
                }
            }
        }
    }

    private ChunkAccess getChunk(BlockPos pos) {
        return this.getChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }

    private ChunkAccess getChunk(int x, int z) {
        int i = x - this.centerX;
        int i1 = z - this.centerZ;
        if (i >= 0 && i < this.chunks.length && i1 >= 0 && i1 < this.chunks[i].length) { // Paper - if this changes, update getChunkIfLoaded below
            ChunkAccess chunkAccess = this.chunks[i][i1];
            return (ChunkAccess)(chunkAccess != null ? chunkAccess : new EmptyLevelChunk(this.level, new ChunkPos(x, z), this.plains.get()));
        } else {
            return new EmptyLevelChunk(this.level, new ChunkPos(x, z), this.plains.get());
        }
    }

    // Paper start - if loaded util
    @Nullable
    private ChunkAccess getChunkIfLoaded(int x, int z) {
        // Based on getChunk(int, int)
        int xx = x - this.centerX;
        int zz = z - this.centerZ;

        if (xx >= 0 && xx < this.chunks.length && zz >= 0 && zz < this.chunks[xx].length) {
            return this.chunks[xx][zz];
        }
        return null;
    }

    @Nullable
    @Override
    public final FluidState getFluidIfLoaded(BlockPos pos) {
        ChunkAccess chunk = getChunkIfLoaded(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? null : chunk.getFluidState(pos);
    }

    @Nullable
    @Override
    public final BlockState getBlockStateIfLoaded(BlockPos pos) {
        ChunkAccess chunk = getChunkIfLoaded(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? null : chunk.getBlockState(pos);
    }
    // Paper end

    @Override
    public WorldBorder getWorldBorder() {
        return this.level.getWorldBorder();
    }

    @Override
    public BlockGetter getChunkForCollisions(int chunkX, int chunkZ) {
        return this.getChunk(chunkX, chunkZ);
    }

    @Override
    public List<VoxelShape> getEntityCollisions(@Nullable Entity entity, AABB collisionBox) {
        return List.of();
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
        ChunkAccess chunk = this.getChunk(pos);
        return chunk.getBlockEntity(pos);
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        if (this.isOutsideBuildHeight(pos)) {
            return Blocks.AIR.defaultBlockState();
        } else {
            ChunkAccess chunk = this.getChunk(pos);
            return chunk.getBlockState(pos);
        }
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        if (this.isOutsideBuildHeight(pos)) {
            return Fluids.EMPTY.defaultFluidState();
        } else {
            ChunkAccess chunk = this.getChunk(pos);
            return chunk.getFluidState(pos);
        }
    }

    @Override
    public int getMinY() {
        return this.level.getMinY();
    }

    @Override
    public int getHeight() {
        return this.level.getHeight();
    }
}
