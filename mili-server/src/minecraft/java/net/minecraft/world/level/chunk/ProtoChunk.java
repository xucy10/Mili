package net.minecraft.world.level.chunk;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.shorts.ShortList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.BelowZeroRetrogen;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.ProtoChunkTicks;
import net.minecraft.world.ticks.TickContainerAccess;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class ProtoChunk extends ChunkAccess {
    private static final Logger LOGGER = LogUtils.getLogger();
    private volatile @Nullable LevelLightEngine lightEngine;
    private volatile ChunkStatus status = ChunkStatus.EMPTY;
    private final List<CompoundTag> entities = Lists.newArrayList();
    private @Nullable CarvingMask carvingMask;
    private @Nullable BelowZeroRetrogen belowZeroRetrogen;
    private final ProtoChunkTicks<Block> blockTicks;
    private final ProtoChunkTicks<Fluid> fluidTicks;

    public ProtoChunk(
        ChunkPos chunkPos,
        UpgradeData upgradeData,
        LevelHeightAccessor levelHeightAccessor,
        PalettedContainerFactory containerFactory,
        @Nullable BlendingData blendingData
    ) {
        this(chunkPos, upgradeData, null, new ProtoChunkTicks<>(), new ProtoChunkTicks<>(), levelHeightAccessor, containerFactory, blendingData);
    }

    public ProtoChunk(
        ChunkPos chunkPos,
        UpgradeData upgradeData,
        LevelChunkSection @Nullable [] sections,
        ProtoChunkTicks<Block> blockTicks,
        ProtoChunkTicks<Fluid> fluidTicks,
        LevelHeightAccessor levelHeightAccessor,
        PalettedContainerFactory containerFactory,
        @Nullable BlendingData blendingData
    ) {
        super(chunkPos, upgradeData, levelHeightAccessor, containerFactory, 0L, sections, blendingData);
        this.blockTicks = blockTicks;
        this.fluidTicks = fluidTicks;
    }

    @Override
    public TickContainerAccess<Block> getBlockTicks() {
        return this.blockTicks;
    }

    @Override
    public TickContainerAccess<Fluid> getFluidTicks() {
        return this.fluidTicks;
    }

    @Override
    public ChunkAccess.PackedTicks getTicksForSerialization(long gameTime) {
        return new ChunkAccess.PackedTicks(this.blockTicks.pack(gameTime), this.fluidTicks.pack(gameTime));
    }

    // Paper start - If loaded util
    @Override
    public final FluidState getFluidIfLoaded(BlockPos pos) {
        return this.getFluidState(pos);
    }

    @Override
    public final BlockState getBlockStateIfLoaded(BlockPos pos) {
        return this.getBlockState(pos);
    }
    // Paper end

    @Override
    public BlockState getBlockState(BlockPos pos) {
        // Paper start
        return this.getBlockState(pos.getX(), pos.getY(), pos.getZ());
    }

    public BlockState getBlockState(final int x, final int y, final int z) {
        // Paper end
        if (this.isOutsideBuildHeight(y)) {
            return Blocks.VOID_AIR.defaultBlockState();
        } else {
            LevelChunkSection section = this.getSection(this.getSectionIndex(y));
            return section.hasOnlyAir() ? Blocks.AIR.defaultBlockState() : section.getBlockState(x & 15, y & 15, z & 15); // Paper
        }
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        int y = pos.getY();
        if (this.isOutsideBuildHeight(y)) {
            return Fluids.EMPTY.defaultFluidState();
        } else {
            LevelChunkSection section = this.getSection(this.getSectionIndex(y));
            return section.hasOnlyAir() ? Fluids.EMPTY.defaultFluidState() : section.getFluidState(pos.getX() & 15, y & 15, pos.getZ() & 15);
        }
    }

    @Override
    public @Nullable BlockState setBlockState(BlockPos pos, BlockState state, @Block.UpdateFlags int flags) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        if (this.isOutsideBuildHeight(y)) {
            return Blocks.VOID_AIR.defaultBlockState();
        } else {
            int sectionIndex = this.getSectionIndex(y);
            LevelChunkSection section = this.getSection(sectionIndex);
            boolean hasOnlyAir = section.hasOnlyAir();
            if (hasOnlyAir && state.is(Blocks.AIR)) {
                return state;
            } else {
                int relativeBlockPosX = SectionPos.sectionRelative(x);
                int relativeBlockPosY = SectionPos.sectionRelative(y);
                int relativeBlockPosZ = SectionPos.sectionRelative(z);
                BlockState blockState = section.setBlockState(relativeBlockPosX, relativeBlockPosY, relativeBlockPosZ, state);
                if (this.status.isOrAfter(ChunkStatus.INITIALIZE_LIGHT)) {
                    boolean hasOnlyAir1 = section.hasOnlyAir();
                    if (hasOnlyAir1 != hasOnlyAir) {
                        this.lightEngine.updateSectionStatus(pos, hasOnlyAir1);
                    }

                    if (LightEngine.hasDifferentLightProperties(blockState, state)) {
                        // Paper - rewrite chunk system
                        this.lightEngine.checkBlock(pos);
                    }
                }

                EnumSet<Heightmap.Types> set = this.getPersistedStatus().heightmapsAfter();
                EnumSet<Heightmap.Types> set1 = null;

                for (Heightmap.Types types : set) {
                    Heightmap heightmap = this.heightmaps.get(types);
                    if (heightmap == null) {
                        if (set1 == null) {
                            set1 = EnumSet.noneOf(Heightmap.Types.class);
                        }

                        set1.add(types);
                    }
                }

                if (set1 != null) {
                    Heightmap.primeHeightmaps(this, set1);
                }

                for (Heightmap.Types typesx : set) {
                    this.heightmaps.get(typesx).update(relativeBlockPosX, y, relativeBlockPosZ, state);
                }

                return blockState;
            }
        }
    }

    @Override
    public void setBlockEntity(BlockEntity blockEntity) {
        this.pendingBlockEntities.remove(blockEntity.getBlockPos());
        this.blockEntities.put(blockEntity.getBlockPos(), blockEntity);
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
        return this.blockEntities.get(pos);
    }

    public Map<BlockPos, BlockEntity> getBlockEntities() {
        return this.blockEntities;
    }

    public void addEntity(CompoundTag tag) {
        this.entities.add(tag);
    }

    @Override
    public void addEntity(Entity entity) {
        if (!entity.isPassenger()) {
            try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(entity.problemPath(), LOGGER)) {
                TagValueOutput tagValueOutput = TagValueOutput.createWithContext(scopedCollector, entity.registryAccess());
                entity.save(tagValueOutput);
                this.addEntity(tagValueOutput.buildResult());
            }
        }
    }

    @Override
    public void setStartForStructure(Structure structure, StructureStart structureStart) {
        BelowZeroRetrogen belowZeroRetrogen = this.getBelowZeroRetrogen();
        if (belowZeroRetrogen != null && structureStart.isValid()) {
            BoundingBox boundingBox = structureStart.getBoundingBox();
            LevelHeightAccessor heightAccessorForGeneration = this.getHeightAccessorForGeneration();
            if (boundingBox.minY() < heightAccessorForGeneration.getMinY() || boundingBox.maxY() > heightAccessorForGeneration.getMaxY()) {
                return;
            }
        }

        super.setStartForStructure(structure, structureStart);
    }

    public List<CompoundTag> getEntities() {
        return this.entities;
    }

    @Override
    public ChunkStatus getPersistedStatus() {
        return this.status;
    }

    public void setPersistedStatus(ChunkStatus status) {
        this.status = status;
        if (this.belowZeroRetrogen != null && status.isOrAfter(this.belowZeroRetrogen.targetStatus())) {
            this.setBelowZeroRetrogen(null);
        }

        this.markUnsaved();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z) {
        if (this.getHighestGeneratedStatus().isOrAfter(ChunkStatus.BIOMES)) {
            return super.getNoiseBiome(x, y, z);
        } else {
            throw new IllegalStateException("Asking for biomes before we have biomes");
        }
    }

    public static short packOffsetCoordinates(BlockPos pos) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        int i = x & 15;
        int i1 = y & 15;
        int i2 = z & 15;
        return (short)(i | i1 << 4 | i2 << 8);
    }

    public static BlockPos unpackOffsetCoordinates(short packedPos, int yOffset, ChunkPos chunkPos) {
        int blockPosCoord = SectionPos.sectionToBlockCoord(chunkPos.x, packedPos & 15);
        int blockPosCoord1 = SectionPos.sectionToBlockCoord(yOffset, packedPos >>> 4 & 15);
        int blockPosCoord2 = SectionPos.sectionToBlockCoord(chunkPos.z, packedPos >>> 8 & 15);
        return new BlockPos(blockPosCoord, blockPosCoord1, blockPosCoord2);
    }

    @Override
    public void markPosForPostprocessing(BlockPos pos) {
        if (!this.isOutsideBuildHeight(pos)) {
            ChunkAccess.getOrCreateOffsetList(this.postProcessing, this.getSectionIndex(pos.getY())).add(packOffsetCoordinates(pos));
        }
    }

    @Override
    public void addPackedPostProcess(ShortList offsets, int index) {
        ChunkAccess.getOrCreateOffsetList(this.postProcessing, index).addAll(offsets);
    }

    public Map<BlockPos, CompoundTag> getBlockEntityNbts() {
        return Collections.unmodifiableMap(this.pendingBlockEntities);
    }

    @Override
    public @Nullable CompoundTag getBlockEntityNbtForSaving(BlockPos pos, HolderLookup.Provider registries) {
        BlockEntity blockEntity = this.getBlockEntity(pos);
        return blockEntity != null ? blockEntity.saveWithFullMetadata(registries) : this.pendingBlockEntities.get(pos);
    }

    @Override
    public void removeBlockEntity(BlockPos pos) {
        this.blockEntities.remove(pos);
        this.pendingBlockEntities.remove(pos);
    }

    public @Nullable CarvingMask getCarvingMask() {
        return this.carvingMask;
    }

    public CarvingMask getOrCreateCarvingMask() {
        if (this.carvingMask == null) {
            this.carvingMask = new CarvingMask(this.getHeight(), this.getMinY());
        }

        return this.carvingMask;
    }

    public void setCarvingMask(CarvingMask carvingMask) {
        this.carvingMask = carvingMask;
    }

    public void setLightEngine(LevelLightEngine lightEngine) {
        this.lightEngine = lightEngine;
    }

    public void setBelowZeroRetrogen(@Nullable BelowZeroRetrogen belowZeroRetrogen) {
        this.belowZeroRetrogen = belowZeroRetrogen;
    }

    @Override
    public @Nullable BelowZeroRetrogen getBelowZeroRetrogen() {
        return this.belowZeroRetrogen;
    }

    private static <T> LevelChunkTicks<T> unpackTicks(ProtoChunkTicks<T> ticks) {
        return new LevelChunkTicks<>(ticks.scheduledTicks());
    }

    public LevelChunkTicks<Block> unpackBlockTicks() {
        return unpackTicks(this.blockTicks);
    }

    public LevelChunkTicks<Fluid> unpackFluidTicks() {
        return unpackTicks(this.fluidTicks);
    }

    @Override
    public LevelHeightAccessor getHeightAccessorForGeneration() {
        return (LevelHeightAccessor)(this.isUpgrading() ? BelowZeroRetrogen.UPGRADE_HEIGHT_ACCESSOR : this);
    }
}
