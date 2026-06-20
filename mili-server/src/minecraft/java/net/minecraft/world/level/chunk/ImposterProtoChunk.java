package net.minecraft.world.level.chunk;

import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.ticks.BlackholeTickAccess;
import net.minecraft.world.ticks.TickContainerAccess;
import org.jspecify.annotations.Nullable;

public class ImposterProtoChunk extends ProtoChunk implements ca.spottedleaf.moonrise.patches.starlight.chunk.StarlightChunk { // Paper - rewrite chunk system
    private final LevelChunk wrapped;
    private final boolean allowWrites;

    public ImposterProtoChunk(LevelChunk wrapped, boolean allowWrites) {
        super(wrapped.getPos(), UpgradeData.EMPTY, wrapped.levelHeightAccessor, wrapped.getLevel().palettedContainerFactory(), wrapped.getBlendingData());
        this.wrapped = wrapped;
        this.allowWrites = allowWrites;
    }

    // Paper start - rewrite chunk system
    @Override
    public ca.spottedleaf.moonrise.patches.starlight.light.SWMRNibbleArray[] starlight$getBlockNibbles() {
        return ((ca.spottedleaf.moonrise.patches.starlight.chunk.StarlightChunk)this.wrapped).starlight$getBlockNibbles();
    }

    @Override
    public void starlight$setBlockNibbles(final ca.spottedleaf.moonrise.patches.starlight.light.SWMRNibbleArray[] nibbles) {
        ((ca.spottedleaf.moonrise.patches.starlight.chunk.StarlightChunk)this.wrapped).starlight$setBlockNibbles(nibbles);
    }

    @Override
    public ca.spottedleaf.moonrise.patches.starlight.light.SWMRNibbleArray[] starlight$getSkyNibbles() {
        return ((ca.spottedleaf.moonrise.patches.starlight.chunk.StarlightChunk)this.wrapped).starlight$getSkyNibbles();
    }

    @Override
    public void starlight$setSkyNibbles(final ca.spottedleaf.moonrise.patches.starlight.light.SWMRNibbleArray[] nibbles) {
        ((ca.spottedleaf.moonrise.patches.starlight.chunk.StarlightChunk)this.wrapped).starlight$setSkyNibbles(nibbles);
    }

    @Override
    public boolean[] starlight$getSkyEmptinessMap() {
        return ((ca.spottedleaf.moonrise.patches.starlight.chunk.StarlightChunk)this.wrapped).starlight$getSkyEmptinessMap();
    }

    @Override
    public void starlight$setSkyEmptinessMap(final boolean[] emptinessMap) {
        ((ca.spottedleaf.moonrise.patches.starlight.chunk.StarlightChunk)this.wrapped).starlight$setSkyEmptinessMap(emptinessMap);
    }

    @Override
    public boolean[] starlight$getBlockEmptinessMap() {
        return ((ca.spottedleaf.moonrise.patches.starlight.chunk.StarlightChunk)this.wrapped).starlight$getBlockEmptinessMap();
    }

    @Override
    public void starlight$setBlockEmptinessMap(final boolean[] emptinessMap) {
        ((ca.spottedleaf.moonrise.patches.starlight.chunk.StarlightChunk)this.wrapped).starlight$setBlockEmptinessMap(emptinessMap);
    }
    // Paper end - rewrite chunk system

    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
        // Folia start - block reading possibly in-world block data for worldgen threads
        if (!this.allowWrites && !ca.spottedleaf.moonrise.common.util.TickThread.isTickThread()) {
            return null;
        }
        // Folia end - block reading possibly in-world block data for worldgen threads
        return this.wrapped.getBlockEntity(pos);
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return this.wrapped.getBlockState(pos);
    }

    // Paper start
    @Override
    public final BlockState getBlockState(final int x, final int y, final int z) {
        return this.wrapped.getBlockStateFinal(x, y, z);
    }
    // Paper end

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return this.wrapped.getFluidState(pos);
    }

    @Override
    public LevelChunkSection getSection(int index) {
        return this.allowWrites ? this.wrapped.getSection(index) : super.getSection(index);
    }

    @Override
    public @Nullable BlockState setBlockState(BlockPos pos, BlockState state, @Block.UpdateFlags int flags) {
        return this.allowWrites ? this.wrapped.setBlockState(pos, state, flags) : null;
    }

    @Override
    public void setBlockEntity(BlockEntity blockEntity) {
        if (this.allowWrites) {
            this.wrapped.setBlockEntity(blockEntity);
        }
    }

    @Override
    public void addEntity(Entity entity) {
        if (this.allowWrites) {
            this.wrapped.addEntity(entity);
        }
    }

    @Override
    public void setPersistedStatus(ChunkStatus status) {
        if (this.allowWrites) {
            super.setPersistedStatus(status);
        }
    }

    @Override
    public LevelChunkSection[] getSections() {
        return this.wrapped.getSections();
    }

    @Override
    public void setHeightmap(Heightmap.Types type, long[] data) {
    }

    private Heightmap.Types fixType(Heightmap.Types type) {
        if (type == Heightmap.Types.WORLD_SURFACE_WG) {
            return Heightmap.Types.WORLD_SURFACE;
        } else {
            return type == Heightmap.Types.OCEAN_FLOOR_WG ? Heightmap.Types.OCEAN_FLOOR : type;
        }
    }

    @Override
    public Heightmap getOrCreateHeightmapUnprimed(Heightmap.Types type) {
        return this.wrapped.getOrCreateHeightmapUnprimed(type);
    }

    @Override
    public int getHeight(Heightmap.Types type, int x, int z) {
        return this.wrapped.getHeight(this.fixType(type), x, z);
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z) {
        return this.wrapped.getNoiseBiome(x, y, z);
    }

    @Override
    public ChunkPos getPos() {
        return this.wrapped.getPos();
    }

    @Override
    public @Nullable StructureStart getStartForStructure(Structure structure) {
        return this.wrapped.getStartForStructure(structure);
    }

    @Override
    public void setStartForStructure(Structure structure, StructureStart structureStart) {
    }

    @Override
    public Map<Structure, StructureStart> getAllStarts() {
        return this.wrapped.getAllStarts();
    }

    @Override
    public void setAllStarts(Map<Structure, StructureStart> structureStarts) {
    }

    @Override
    public LongSet getReferencesForStructure(Structure structure) {
        return this.wrapped.getReferencesForStructure(structure);
    }

    @Override
    public void addReferenceForStructure(Structure structure, long reference) {
    }

    @Override
    public Map<Structure, LongSet> getAllReferences() {
        return this.wrapped.getAllReferences();
    }

    @Override
    public void setAllReferences(Map<Structure, LongSet> structureReferencesMap) {
    }

    @Override
    public void markUnsaved() {
        this.wrapped.markUnsaved();
    }

    @Override
    public boolean canBeSerialized() {
        return false;
    }

    @Override
    public boolean tryMarkSaved() {
        return false;
    }

    @Override
    public boolean isUnsaved() {
        return false;
    }

    @Override
    public ChunkStatus getPersistedStatus() {
        return this.wrapped.getPersistedStatus();
    }

    @Override
    public void removeBlockEntity(BlockPos pos) {
    }

    @Override
    public void markPosForPostprocessing(BlockPos pos) {
    }

    @Override
    public void setBlockEntityNbt(CompoundTag tag) {
    }

    @Override
    public @Nullable CompoundTag getBlockEntityNbt(BlockPos pos) {
        return this.wrapped.getBlockEntityNbt(pos);
    }

    @Override
    public @Nullable CompoundTag getBlockEntityNbtForSaving(BlockPos pos, HolderLookup.Provider registries) {
        return this.wrapped.getBlockEntityNbtForSaving(pos, registries);
    }

    @Override
    public void findBlocks(Predicate<BlockState> predicate, BiConsumer<BlockPos, BlockState> output) {
        this.wrapped.findBlocks(predicate, output);
    }

    @Override
    public TickContainerAccess<Block> getBlockTicks() {
        return this.allowWrites ? this.wrapped.getBlockTicks() : BlackholeTickAccess.emptyContainer();
    }

    @Override
    public TickContainerAccess<Fluid> getFluidTicks() {
        return this.allowWrites ? this.wrapped.getFluidTicks() : BlackholeTickAccess.emptyContainer();
    }

    @Override
    public ChunkAccess.PackedTicks getTicksForSerialization(long gameTime) {
        return this.wrapped.getTicksForSerialization(gameTime);
    }

    @Override
    public @Nullable BlendingData getBlendingData() {
        return this.wrapped.getBlendingData();
    }

    @Override
    public CarvingMask getCarvingMask() {
        if (this.allowWrites) {
            return super.getCarvingMask();
        } else {
            throw (UnsupportedOperationException)Util.pauseInIde(new UnsupportedOperationException("Meaningless in this context"));
        }
    }

    @Override
    public CarvingMask getOrCreateCarvingMask() {
        if (this.allowWrites) {
            return super.getOrCreateCarvingMask();
        } else {
            throw (UnsupportedOperationException)Util.pauseInIde(new UnsupportedOperationException("Meaningless in this context"));
        }
    }

    public LevelChunk getWrapped() {
        return this.wrapped;
    }

    @Override
    public boolean isLightCorrect() {
        return this.wrapped.isLightCorrect();
    }

    @Override
    public void setLightCorrect(boolean lightCorrect) {
        this.wrapped.setLightCorrect(lightCorrect);
    }

    @Override
    public void fillBiomesFromNoise(BiomeResolver resolver, Climate.Sampler sampler) {
        if (this.allowWrites) {
            this.wrapped.fillBiomesFromNoise(resolver, sampler);
        }
    }

    @Override
    public void initializeLightSources() {
        this.wrapped.initializeLightSources();
    }

    @Override
    public ChunkSkyLightSources getSkyLightSources() {
        return this.wrapped.getSkyLightSources();
    }
}
