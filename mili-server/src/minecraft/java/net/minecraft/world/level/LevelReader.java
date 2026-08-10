package net.minecraft.world.level;

import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributeReader;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

public interface LevelReader extends ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemLevelReader, BlockAndTintGetter, CollisionGetter, SignalGetter, BiomeManager.NoiseBiomeSource { // Paper - rewrite chunk system
    // Paper start - rewrite chunk system
    @Override
    public default ChunkAccess moonrise$syncLoadNonFull(final int chunkX, final int chunkZ, final ChunkStatus status) {
        if (status == null || status.isOrAfter(ChunkStatus.FULL)) {
            throw new IllegalArgumentException("Status: " + status.toString());
        }
        return ((LevelReader)this).getChunk(chunkX, chunkZ, status, true);
    }
    // Paper end - rewrite chunk system
    @Nullable ChunkAccess getChunk(int x, int z, ChunkStatus chunkStatus, boolean requireChunk);

    @Nullable ChunkAccess getChunkIfLoadedImmediately(int x, int z); // Paper - ifLoaded api (we need this since current impl blocks if the chunk is loading)
    @Nullable default ChunkAccess getChunkIfLoadedImmediately(BlockPos pos) { return this.getChunkIfLoadedImmediately(pos.getX() >> 4, pos.getZ() >> 4);}

    @Deprecated
    boolean hasChunk(int chunkX, int chunkZ);

    int getHeight(Heightmap.Types heightmapType, int x, int z);

    default int getHeight(Heightmap.Types heightmapType, BlockPos pos) {
        return this.getHeight(heightmapType, pos.getX(), pos.getZ());
    }

    int getSkyDarken();

    BiomeManager getBiomeManager();

    default Holder<Biome> getBiome(BlockPos pos) {
        return this.getBiomeManager().getBiome(pos);
    }

    default Stream<BlockState> getBlockStatesIfLoaded(AABB aabb) {
        int floor = Mth.floor(aabb.minX);
        int floor1 = Mth.floor(aabb.maxX);
        int floor2 = Mth.floor(aabb.minY);
        int floor3 = Mth.floor(aabb.maxY);
        int floor4 = Mth.floor(aabb.minZ);
        int floor5 = Mth.floor(aabb.maxZ);
        return this.hasChunksAt(floor, floor2, floor4, floor1, floor3, floor5) ? this.getBlockStates(aabb) : Stream.empty();
    }

    @Override
    default int getBlockTint(BlockPos pos, ColorResolver colorResolver) {
        return colorResolver.getColor(this.getBiome(pos).value(), pos.getX(), pos.getZ());
    }

    @Override
    default Holder<Biome> getNoiseBiome(int x, int y, int z) {
        ChunkAccess chunk = this.getChunk(QuartPos.toSection(x), QuartPos.toSection(z), ChunkStatus.BIOMES, false);
        return chunk != null ? chunk.getNoiseBiome(x, y, z) : this.getUncachedNoiseBiome(x, y, z);
    }

    Holder<Biome> getUncachedNoiseBiome(int x, int y, int z);

    boolean isClientSide();

    int getSeaLevel();

    DimensionType dimensionType();

    @Override
    default int getMinY() {
        return this.dimensionType().minY();
    }

    @Override
    default int getHeight() {
        return this.dimensionType().height();
    }

    default BlockPos getHeightmapPos(Heightmap.Types heightmapType, BlockPos pos) {
        return new BlockPos(pos.getX(), this.getHeight(heightmapType, pos.getX(), pos.getZ()), pos.getZ());
    }

    default boolean isEmptyBlock(BlockPos pos) {
        return this.getBlockState(pos).isAir();
    }

    default boolean canSeeSkyFromBelowWater(BlockPos pos) {
        if (pos.getY() >= this.getSeaLevel()) {
            return this.canSeeSky(pos);
        } else {
            BlockPos blockPos = new BlockPos(pos.getX(), this.getSeaLevel(), pos.getZ());
            if (!this.canSeeSky(blockPos)) {
                return false;
            } else {
                for (BlockPos var4 = blockPos.below(); var4.getY() > pos.getY(); var4 = var4.below()) {
                    BlockState blockState = this.getBlockState(var4);
                    if (blockState.getLightBlock() > 0 && !blockState.liquid()) {
                        return false;
                    }
                }

                return true;
            }
        }
    }

    default float getPathfindingCostFromLightLevels(BlockPos pos) {
        return this.getLightLevelDependentMagicValue(pos) - 0.5F;
    }

    @Deprecated
    default float getLightLevelDependentMagicValue(BlockPos pos) {
        float f = this.getMaxLocalRawBrightness(pos) / 15.0F;
        float f1 = f / (4.0F - 3.0F * f);
        return Mth.lerp(this.dimensionType().ambientLight(), f1, 1.0F);
    }

    default ChunkAccess getChunk(BlockPos pos) {
        return this.getChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }

    default ChunkAccess getChunk(int chunkX, int chunkZ) {
        return this.getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);
    }

    default ChunkAccess getChunk(int chunkX, int chunkZ, ChunkStatus chunkStatus) {
        return this.getChunk(chunkX, chunkZ, chunkStatus, true);
    }

    @Override
    default @Nullable BlockGetter getChunkForCollisions(int chunkX, int chunkZ) {
        return this.getChunk(chunkX, chunkZ, ChunkStatus.EMPTY, false);
    }

    default boolean isWaterAt(BlockPos pos) {
        return this.getFluidState(pos).is(FluidTags.WATER);
    }

    default boolean containsAnyLiquid(AABB aabb) {
        int floor = Mth.floor(aabb.minX);
        int ceil = Mth.ceil(aabb.maxX);
        int floor1 = Mth.floor(aabb.minY);
        int ceil1 = Mth.ceil(aabb.maxY);
        int floor2 = Mth.floor(aabb.minZ);
        int ceil2 = Mth.ceil(aabb.maxZ);
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for (int i = floor; i < ceil; i++) {
            for (int i1 = floor1; i1 < ceil1; i1++) {
                for (int i2 = floor2; i2 < ceil2; i2++) {
                    BlockState blockState = this.getBlockState(mutableBlockPos.set(i, i1, i2));
                    if (!blockState.getFluidState().isEmpty()) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    default int getMaxLocalRawBrightness(BlockPos pos) {
        return this.getMaxLocalRawBrightness(pos, this.getSkyDarken());
    }

    default int getMaxLocalRawBrightness(BlockPos pos, int amount) {
        return pos.getX() >= -30000000 && pos.getZ() >= -30000000 && pos.getX() < 30000000 && pos.getZ() < 30000000 ? this.getRawBrightness(pos, amount) : 15;
    }

    @Deprecated
    default boolean hasChunkAt(int x, int z) {
        return this.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
    }

    @Deprecated
    default boolean hasChunkAt(BlockPos pos) {
        return this.hasChunkAt(pos.getX(), pos.getZ());
    }

    @Deprecated
    default boolean hasChunksAt(BlockPos from, BlockPos to) {
        return this.hasChunksAt(from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ());
    }

    @Deprecated
    default boolean hasChunksAt(int fromX, int fromY, int fromZ, int toX, int toY, int toZ) {
        return toY >= this.getMinY() && fromY <= this.getMaxY() && this.hasChunksAt(fromX, fromZ, toX, toZ);
    }

    // Folia start - region threading
    default boolean hasAndOwnsChunksAt(int minX, int minZ, int maxX, int maxZ) {
        int i = SectionPos.blockToSectionCoord(minX);
        int j = SectionPos.blockToSectionCoord(maxX);
        int k = SectionPos.blockToSectionCoord(minZ);
        int l = SectionPos.blockToSectionCoord(maxZ);

        for(int m = i; m <= j; ++m) {
            for(int n = k; n <= l; ++n) {
                if (!this.hasChunk(m, n) || (this instanceof net.minecraft.server.level.ServerLevel world && !ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(world, m, n))) {
                    return false;
                }
            }
        }

        return true;
    }
    // Folia end - region threading

    @Deprecated
    default boolean hasChunksAt(int fromX, int fromZ, int toX, int toZ) {
        int sectionPosCoord = SectionPos.blockToSectionCoord(fromX);
        int sectionPosCoord1 = SectionPos.blockToSectionCoord(toX);
        int sectionPosCoord2 = SectionPos.blockToSectionCoord(fromZ);
        int sectionPosCoord3 = SectionPos.blockToSectionCoord(toZ);

        for (int i = sectionPosCoord; i <= sectionPosCoord1; i++) {
            for (int i1 = sectionPosCoord2; i1 <= sectionPosCoord3; i1++) {
                if (!this.hasChunk(i, i1)) {
                    return false;
                }
            }
        }

        return true;
    }

    RegistryAccess registryAccess();

    FeatureFlagSet enabledFeatures();

    default <T> HolderLookup<T> holderLookup(ResourceKey<? extends Registry<? extends T>> registryKey) {
        Registry<T> registry = this.registryAccess().lookupOrThrow(registryKey);
        return registry.filterFeatures(this.enabledFeatures());
    }

    EnvironmentAttributeReader environmentAttributes();
}
