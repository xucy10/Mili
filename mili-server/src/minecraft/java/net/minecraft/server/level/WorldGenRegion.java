package net.minecraft.server.level;

import com.mojang.logging.LogUtils;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StaticCache2D;
import net.minecraft.util.Util;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.attribute.EnvironmentAttributeReader;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.ChunkType;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.LevelTickAccess;
import net.minecraft.world.ticks.WorldGenTickAccess;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class WorldGenRegion implements WorldGenLevel {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final StaticCache2D<GenerationChunkHolder> cache;
    private final ChunkAccess center;
    private final ServerLevel level;
    private final long seed;
    private final LevelData levelData;
    private final RandomSource random;
    private final DimensionType dimensionType;
    private final WorldGenTickAccess<Block> blockTicks = new WorldGenTickAccess<>(blockPos -> this.getChunk(blockPos).getBlockTicks());
    private final WorldGenTickAccess<Fluid> fluidTicks = new WorldGenTickAccess<>(blockPos -> this.getChunk(blockPos).getFluidTicks());
    private final BiomeManager biomeManager;
    private final ChunkStep generatingStep;
    private @Nullable Supplier<String> currentlyGenerating;
    private final AtomicLong subTickCount = new AtomicLong();
    private static final Identifier WORLDGEN_REGION_RANDOM = Identifier.withDefaultNamespace("worldgen_region_random");

    // Paper start - rewrite chunk system
    /**
     * During feature generation, light data is not initialised and will always return 15 in Starlight. Vanilla
     * can possibly return 0 if partially initialised, which allows some mushroom blocks to generate.
     * In general, the brightness value from the light engine should not be used until the chunk is ready. To emulate
     * Vanilla behavior better, we return 0 as the brightness during world gen unless the target chunk is finished
     * lighting.
     */
    @Override
    public int getBrightness(final net.minecraft.world.level.LightLayer lightLayer, final BlockPos blockPos) {
        final ChunkAccess chunk = this.getChunk(blockPos.getX() >> 4, blockPos.getZ() >> 4);
        if (!chunk.isLightCorrect()) {
            return 0;
        }
        return this.getLightEngine().getLayerListener(lightLayer).getLightValue(blockPos);
    }

    /**
     * See above
     */
    @Override
    public int getRawBrightness(final BlockPos blockPos, final int subtract) {
        final ChunkAccess chunk = this.getChunk(blockPos.getX() >> 4, blockPos.getZ() >> 4);
        if (!chunk.isLightCorrect()) {
            return 0;
        }
        return this.getLightEngine().getRawBrightness(blockPos, subtract);
    }
    // Paper end - rewrite chunk system
    // Folia start - region threading
    private final net.minecraft.world.level.StructureManager structureManager;
    @Override
    public net.minecraft.world.level.StructureManager structureManager() {
        return this.structureManager;
    }
    // Folia end - region threading

    public WorldGenRegion(ServerLevel level, StaticCache2D<GenerationChunkHolder> cache, ChunkStep generatingStep, ChunkAccess center) {
        this.generatingStep = generatingStep;
        this.cache = cache;
        this.center = center;
        this.level = level;
        this.seed = level.getSeed();
        this.levelData = level.getLevelData();
        this.random = level.getChunkSource().randomState().getOrCreateRandomFactory(WORLDGEN_REGION_RANDOM).at(this.center.getPos().getWorldPosition());
        this.dimensionType = level.dimensionType();
        this.biomeManager = new BiomeManager(this, BiomeManager.obfuscateSeed(this.seed));
        this.structureManager = level.structureManager().forWorldGenRegion(this); // Folia - region threading
    }

    public boolean isOldChunkAround(ChunkPos pos, int radius) {
        return this.level.getChunkSource().chunkMap.isOldChunkAround(pos, radius);
    }

    public ChunkPos getCenter() {
        return this.center.getPos();
    }

    @Override
    public void setCurrentlyGenerating(@Nullable Supplier<String> currentlyGenerating) {
        this.currentlyGenerating = currentlyGenerating;
    }

    @Override
    public ChunkAccess getChunk(int chunkX, int chunkZ) {
        return this.getChunk(chunkX, chunkZ, ChunkStatus.EMPTY);
    }

    @Override
    public @Nullable ChunkAccess getChunk(int x, int z, ChunkStatus chunkStatus, boolean requireChunk) {
        int chessboardDistance = this.center.getPos().getChessboardDistance(x, z);
        ChunkStatus chunkStatus1 = chessboardDistance >= this.generatingStep.directDependencies().size()
            ? null
            : this.generatingStep.directDependencies().get(chessboardDistance);
        GenerationChunkHolder generationChunkHolder;
        if (chunkStatus1 != null) {
            generationChunkHolder = this.cache.get(x, z);
            if (chunkStatus.isOrBefore(chunkStatus1)) {
                ChunkAccess chunkIfPresentUnchecked = generationChunkHolder.getChunkIfPresentUnchecked(chunkStatus1);
                if (chunkIfPresentUnchecked != null) {
                    return chunkIfPresentUnchecked;
                }
            }
        } else {
            generationChunkHolder = null;
        }

        CrashReport crashReport = CrashReport.forThrowable(
            new IllegalStateException("Requested chunk unavailable during world generation"), "Exception generating new chunk"
        );
        CrashReportCategory crashReportCategory = crashReport.addCategory("Chunk request details");
        crashReportCategory.setDetail("Requested chunk", String.format(Locale.ROOT, "%d, %d", x, z));
        crashReportCategory.setDetail("Generating status", () -> this.generatingStep.targetStatus().getName());
        crashReportCategory.setDetail("Requested status", chunkStatus::getName);
        crashReportCategory.setDetail(
            "Actual status", () -> generationChunkHolder == null ? "[out of cache bounds]" : generationChunkHolder.getPersistedStatus().getName()
        );
        crashReportCategory.setDetail("Maximum allowed status", () -> chunkStatus1 == null ? "null" : chunkStatus1.getName());
        crashReportCategory.setDetail("Dependencies", this.generatingStep.directDependencies()::toString);
        crashReportCategory.setDetail("Requested distance", chessboardDistance);
        crashReportCategory.setDetail("Generating chunk", this.center.getPos()::toString);
        throw new ReportedException(crashReport);
    }

    @Override
    public boolean hasChunk(int chunkX, int chunkZ) {
        int chessboardDistance = this.center.getPos().getChessboardDistance(chunkX, chunkZ);
        return chessboardDistance < this.generatingStep.directDependencies().size();
    }

    // Paper start - if loaded util
    @Nullable
    @Override
    public ChunkAccess getChunkIfLoadedImmediately(int x, int z) {
        return this.getChunk(x, z, ChunkStatus.FULL, false);
    }

    @Nullable
    @Override
    public final BlockState getBlockStateIfLoaded(BlockPos pos) {
        ChunkAccess chunk = this.getChunkIfLoadedImmediately(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? null : chunk.getBlockState(pos);
    }

    @Nullable
    @Override
    public final FluidState getFluidIfLoaded(BlockPos pos) {
        ChunkAccess chunk = this.getChunkIfLoadedImmediately(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? null : chunk.getFluidState(pos);
    }
    // Paper end

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return this.getChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ())).getBlockState(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return this.getChunk(pos).getFluidState(pos);
    }

    @Override
    public @Nullable Player getNearestPlayer(double x, double y, double z, double distance, @Nullable Predicate<Entity> predicate) {
        return null;
    }

    @Override
    public int getSkyDarken() {
        return 0;
    }

    @Override
    public BiomeManager getBiomeManager() {
        return this.biomeManager;
    }

    @Override
    public Holder<Biome> getUncachedNoiseBiome(int x, int y, int z) {
        return this.level.getUncachedNoiseBiome(x, y, z);
    }

    @Override
    public float getShade(Direction direction, boolean shade) {
        return 1.0F;
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return this.level.getLightEngine();
    }

    @Override
    public boolean destroyBlock(BlockPos pos, boolean dropBlock, @Nullable Entity entity, int recursionLeft) {
        BlockState blockState = this.getBlockState(pos);
        if (blockState.isAir()) {
            return false;
        } else {
            if (dropBlock) LOGGER.warn("Potential async entity add during worldgen", new Throwable()); // Paper - Fix async entity add due to fungus trees; log when this happens
            if (false) { // CraftBukkit - SPIGOT-6833: Do not drop during world generation
                BlockEntity blockEntity = blockState.hasBlockEntity() ? this.getBlockEntity(pos) : null;
                Block.dropResources(blockState, this.level, pos, blockEntity, entity, ItemStack.EMPTY);
            }

            return this.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL, recursionLeft);
        }
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
        ChunkAccess chunk = this.getChunk(pos);
        BlockEntity blockEntity = chunk.getBlockEntity(pos);
        if (blockEntity != null) {
            return blockEntity;
        } else {
            CompoundTag blockEntityNbt = chunk.getBlockEntityNbt(pos);
            BlockState blockState = chunk.getBlockState(pos);
            if (blockEntityNbt != null) {
                if ("DUMMY".equals(blockEntityNbt.getStringOr("id", ""))) {
                    if (!blockState.hasBlockEntity()) {
                        return null;
                    }

                    blockEntity = ((EntityBlock)blockState.getBlock()).newBlockEntity(pos, blockState);
                } else {
                    blockEntity = BlockEntity.loadStatic(pos, blockState, blockEntityNbt, this.level.registryAccess());
                }

                if (blockEntity != null) {
                    chunk.setBlockEntity(blockEntity);
                    return blockEntity;
                }
            }

            if (blockState.hasBlockEntity()) {
                LOGGER.warn("Tried to access a block entity before it was created. {}", pos);
            }

            return null;
        }
    }

    private boolean hasSetFarWarned = false; // Paper - Buffer OOB setBlock calls
    @Override
    public boolean ensureCanWrite(BlockPos pos) {
        int sectionPosX = SectionPos.blockToSectionCoord(pos.getX());
        int sectionPosZ = SectionPos.blockToSectionCoord(pos.getZ());
        ChunkPos center = this.getCenter();
        int abs = Math.abs(center.x - sectionPosX);
        int abs1 = Math.abs(center.z - sectionPosZ);
        if (abs <= this.generatingStep.blockStateWriteRadius() && abs1 <= this.generatingStep.blockStateWriteRadius()) {
            if (this.center.isUpgrading()) {
                LevelHeightAccessor heightAccessorForGeneration = this.center.getHeightAccessorForGeneration();
                if (heightAccessorForGeneration.isOutsideBuildHeight(pos.getY())) {
                    return false;
                }
            }

            return true;
        } else {
            // Paper start - Buffer OOB setBlock calls
            if (!hasSetFarWarned) {
            Util.logAndPauseIfInIde(
                "Detected setBlock in a far chunk ["
                    + sectionPosX
                    + ", "
                    + sectionPosZ
                    + "], pos: "
                    + pos
                    + ", status: "
                    + this.generatingStep.targetStatus()
                    + (this.currentlyGenerating == null ? "" : ", currently generating: " + this.currentlyGenerating.get())
            );
                hasSetFarWarned = true;
                if (this.getServer() != null && this.getServer().isDebugging()) {
                    io.papermc.paper.util.TraceUtil.dumpTraceForThread("far setBlock call");
                }
            }
            // Paper end - Buffer OOB setBlock calls
            return false;
        }
    }

    @Override
    public boolean setBlock(BlockPos pos, BlockState state, @Block.UpdateFlags int flags, int recursionLeft) {
        if (!this.ensureCanWrite(pos)) {
            return false;
        } else {
            ChunkAccess chunk = this.getChunk(pos);
            BlockState blockState = chunk.setBlockState(pos, state, flags); final BlockState previousBlockState = blockState; // Paper - Clear block entity before setting up a DUMMY block entity - obfhelper
            if (blockState != null) {
                this.level.updatePOIOnBlockStateChange(pos, blockState, state);
            }

            if (state.hasBlockEntity()) {
                if (chunk.getPersistedStatus().getChunkType() == ChunkType.LEVELCHUNK) {
                    BlockEntity blockEntity = ((EntityBlock)state.getBlock()).newBlockEntity(pos, state);
                    if (blockEntity != null) {
                        chunk.setBlockEntity(blockEntity);
                    } else {
                        chunk.removeBlockEntity(pos);
                    }
                } else {
                    // Paper start - Clear block entity before setting up a DUMMY block entity
                    // The concept of removing a block entity when the block itself changes is generally lifted
                    // from LevelChunk#setBlockState.
                    // It is however to note that this may only run if the block actually changes.
                    // Otherwise a chest block entity generated by a structure template that is later "updated" to
                    // be waterlogged would remove its existing block entity (see PaperMC/Paper#10750)
                    // This logic is *also* found in LevelChunk#setBlockState.
                    if (previousBlockState != null && !java.util.Objects.equals(previousBlockState.getBlock(), state.getBlock())) {
                        chunk.removeBlockEntity(pos);
                    }
                    // Paper end - Clear block entity before setting up a DUMMY block entity
                    CompoundTag compoundTag = new CompoundTag();
                    compoundTag.putInt("x", pos.getX());
                    compoundTag.putInt("y", pos.getY());
                    compoundTag.putInt("z", pos.getZ());
                    compoundTag.putString("id", "DUMMY");
                    chunk.setBlockEntityNbt(compoundTag);
                }
            } else if (blockState != null && blockState.hasBlockEntity()) {
                chunk.removeBlockEntity(pos);
            }

            if (state.hasPostProcess(this, pos) && (flags & Block.UPDATE_KNOWN_SHAPE) == 0) {
                this.markPosForPostprocessing(pos);
            }

            return true;
        }
    }

    private void markPosForPostprocessing(BlockPos pos) {
        this.getChunk(pos).markPosForPostprocessing(pos);
    }

    @Override
    public boolean addFreshEntity(Entity entity) {
        // CraftBukkit start
        return this.addFreshEntity(entity, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.DEFAULT);
    }

    @Override
    public boolean addFreshEntity(Entity entity, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason reason) {
        // CraftBukkit end
        int sectionPosX = SectionPos.blockToSectionCoord(entity.getBlockX());
        int sectionPosZ = SectionPos.blockToSectionCoord(entity.getBlockZ());
        this.getChunk(sectionPosX, sectionPosZ).addEntity(entity);
        return true;
    }

    @Override
    public boolean removeBlock(BlockPos pos, boolean movedByPiston) {
        return this.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    @Override
    public WorldBorder getWorldBorder() {
        return this.level.getWorldBorder();
    }

    @Override
    public boolean isClientSide() {
        return false;
    }

    @Deprecated
    @Override
    public ServerLevel getLevel() {
        return this.level;
    }

    @Override
    public RegistryAccess registryAccess() {
        return this.level.registryAccess();
    }

    @Override
    public FeatureFlagSet enabledFeatures() {
        return this.level.enabledFeatures();
    }

    @Override
    public LevelData getLevelData() {
        return this.levelData;
    }

    @Override
    public DifficultyInstance getCurrentDifficultyAt(BlockPos pos) {
        if (!this.hasChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()))) {
            throw new RuntimeException("We are asking a region for a chunk out of bound");
        } else {
            return new DifficultyInstance(this.level.getDifficulty(), this.level.getDayTime(), 0L, this.level.getMoonBrightness(pos));
        }
    }

    @Override
    public @Nullable MinecraftServer getServer() {
        return this.level.getServer();
    }

    @Override
    public ChunkSource getChunkSource() {
        return this.level.getChunkSource();
    }

    @Override
    public long getSeed() {
        return this.seed;
    }

    @Override
    public LevelTickAccess<Block> getBlockTicks() {
        return this.blockTicks;
    }

    @Override
    public LevelTickAccess<Fluid> getFluidTicks() {
        return this.fluidTicks;
    }

    @Override
    public int getSeaLevel() {
        return this.level.getSeaLevel();
    }

    @Override
    public RandomSource getRandom() {
        return this.random;
    }

    @Override
    public int getHeight(Heightmap.Types heightmapType, int x, int z) {
        return this.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z)).getHeight(heightmapType, x & 15, z & 15) + 1;
    }

    @Override
    public void playSound(@Nullable Entity entity, BlockPos pos, SoundEvent sound, SoundSource source, float volume, float pitch) {
    }

    @Override
    public void addParticle(ParticleOptions options, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
    }

    @Override
    public void levelEvent(@Nullable Entity entity, int type, BlockPos pos, int data) {
    }

    @Override
    public void gameEvent(Holder<GameEvent> gameEvent, Vec3 pos, GameEvent.Context context) {
    }

    @Override
    public DimensionType dimensionType() {
        return this.dimensionType;
    }

    @Override
    public boolean isStateAtPosition(BlockPos pos, Predicate<BlockState> state) {
        return state.test(this.getBlockState(pos));
    }

    @Override
    public boolean isFluidAtPosition(BlockPos pos, Predicate<FluidState> predicate) {
        return predicate.test(this.getFluidState(pos));
    }

    @Override
    public <T extends Entity> List<T> getEntities(EntityTypeTest<Entity, T> entityTypeTest, AABB bounds, Predicate<? super T> predicate) {
        return Collections.emptyList();
    }

    @Override
    public List<Entity> getEntities(@Nullable Entity entity, AABB boundingBox, @Nullable Predicate<? super Entity> predicate) {
        return Collections.emptyList();
    }

    @Override
    public List<Player> players() {
        return Collections.emptyList();
    }

    @Override
    public int getMinY() {
        return this.level.getMinY();
    }

    @Override
    public int getHeight() {
        return this.level.getHeight();
    }

    @Override
    public long nextSubTickCount() {
        return this.subTickCount.getAndIncrement();
    }

    @Override
    public EnvironmentAttributeReader environmentAttributes() {
        return EnvironmentAttributeReader.EMPTY;
    }
}
