package net.minecraft.server.level;

import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.jspecify.annotations.Nullable;

public class ChunkHolder extends GenerationChunkHolder implements ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkSystemChunkHolder { // Paper - rewrite chunk system
    public static final ChunkResult<LevelChunk> UNLOADED_LEVEL_CHUNK = ChunkResult.error("Unloaded level chunk");
    private static final CompletableFuture<ChunkResult<LevelChunk>> UNLOADED_LEVEL_CHUNK_FUTURE = CompletableFuture.completedFuture(UNLOADED_LEVEL_CHUNK);
    private final LevelHeightAccessor levelHeightAccessor;
    // Paper - rewrite chunk system
    private boolean hasChangedSections;
    private final @Nullable ShortSet[] changedBlocksPerSection;
    private final BitSet blockChangedLightSectionFilter = new BitSet();
    private final BitSet skyChangedLightSectionFilter = new BitSet();
    private final LevelLightEngine lightEngine;
    // Paper - rewrite chunk system
    public final ChunkHolder.PlayerProvider playerProvider;
    // Paper - rewrite chunk system

    // Paper start - rewrite chunk system
    private ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder newChunkHolder;

    private static final ServerPlayer[] EMPTY_PLAYER_ARRAY = new ServerPlayer[0];
    private final ca.spottedleaf.moonrise.common.list.ReferenceList<ServerPlayer> playersSentChunkTo = new ca.spottedleaf.moonrise.common.list.ReferenceList<>(EMPTY_PLAYER_ARRAY);

    private ChunkMap getChunkMap() {
        return (ChunkMap)this.playerProvider;
    }

    @Override
    public final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder moonrise$getRealChunkHolder() {
        return this.newChunkHolder;
    }

    @Override
    public final void moonrise$setRealChunkHolder(final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder newChunkHolder) {
        this.newChunkHolder = newChunkHolder;
    }

    @Override
    public final void moonrise$addReceivedChunk(final ServerPlayer player) {
        if (!this.playersSentChunkTo.add(player)) {
            throw new IllegalStateException("Already sent chunk " + this.pos + " in world '" + ca.spottedleaf.moonrise.common.util.WorldUtil.getWorldName(this.getChunkMap().level) + "' to player " + player);
        }
    }

    @Override
    public final void moonrise$removeReceivedChunk(final ServerPlayer player) {
        if (!this.playersSentChunkTo.remove(player)) {
            throw new IllegalStateException("Already sent chunk " + this.pos + " in world '" + ca.spottedleaf.moonrise.common.util.WorldUtil.getWorldName(this.getChunkMap().level) + "' to player " + player);
        }
    }

    @Override
    public final boolean moonrise$hasChunkBeenSent() {
        return this.playersSentChunkTo.size() != 0;
    }

    @Override
    public final boolean moonrise$hasChunkBeenSent(final ServerPlayer to) {
        return this.playersSentChunkTo.contains(to);
    }

    @Override
    public final List<ServerPlayer> moonrise$getPlayers(final boolean onlyOnWatchDistanceEdge) {
        final List<ServerPlayer> ret = new java.util.ArrayList<>();
        final ServerPlayer[] raw = this.playersSentChunkTo.getRawDataUnchecked();
        for (int i = 0, len = this.playersSentChunkTo.size(); i < len; ++i) {
            final ServerPlayer player = raw[i];
            if (onlyOnWatchDistanceEdge && !((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.getChunkMap().level).moonrise$getPlayerChunkLoader().isChunkSent(player, this.pos.x, this.pos.z, onlyOnWatchDistanceEdge)) {
                continue;
            }
            ret.add(player);
        }

        return ret;
    }

    @Override
    public final LevelChunk moonrise$getFullChunk() {
        if (this.newChunkHolder.isFullChunkReady()) {
            if (this.newChunkHolder.getCurrentChunk() instanceof LevelChunk levelChunk) {
                return levelChunk;
            } // else: race condition: chunk unload
        }
        return null;
    }

    private boolean isRadiusLoaded(final int radius) {
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager manager = ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.getChunkMap().level).moonrise$getChunkTaskScheduler()
            .chunkHolderManager;
        final ChunkPos pos = this.pos;
        final int chunkX = pos.x;
        final int chunkZ = pos.z;
        for (int dz = -radius; dz <= radius; ++dz) {
            for (int dx = -radius; dx <= radius; ++dx) {
                if ((dx | dz) == 0) {
                    continue;
                }

                final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder holder = manager.getChunkHolder(dx + chunkX, dz + chunkZ);

                if (holder == null || !holder.isFullChunkReady()) {
                    return false;
                }
            }
        }

        return true;
    }
    // Paper end - rewrite chunk system

    public ChunkHolder(
        ChunkPos pos,
        int ticketLevel,
        LevelHeightAccessor levelHeightAccessor,
        LevelLightEngine lightEngine,
        ChunkHolder.LevelChangeListener onLevelChange,
        ChunkHolder.PlayerProvider playerProvider
    ) {
        super(pos);
        this.levelHeightAccessor = levelHeightAccessor;
        this.lightEngine = lightEngine;
        // Paper - rewrite chunk system
        this.playerProvider = playerProvider;
        // Paper - rewrite chunk system
        this.setTicketLevel(ticketLevel);
        this.changedBlocksPerSection = new ShortSet[levelHeightAccessor.getSectionsCount()];
    }

    // CraftBukkit start
    public LevelChunk getFullChunkNow() {
        // Note: We use the oldTicketLevel for isLoaded checks.
        if (!this.newChunkHolder.isFullChunkReady()) return null; // Paper - rewrite chunk system
        return this.getFullChunkNowUnchecked();
    }

    public LevelChunk getFullChunkNowUnchecked() {
        return (LevelChunk) this.getChunkIfPresentUnchecked(ChunkStatus.FULL);
    }
    // CraftBukkit end

    public CompletableFuture<ChunkResult<LevelChunk>> getTickingChunkFuture() {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public CompletableFuture<ChunkResult<LevelChunk>> getEntityTickingChunkFuture() {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public CompletableFuture<ChunkResult<LevelChunk>> getFullChunkFuture() {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public final @Nullable LevelChunk getTickingChunk() { // Paper - final for inline
        // Paper start - rewrite chunk system
        if (this.newChunkHolder.isTickingReady()) {
            if (this.newChunkHolder.getCurrentChunk() instanceof LevelChunk levelChunk) {
                return levelChunk;
            } // else: race condition: chunk unload
        }
        return null;
        // Paper end - rewrite chunk system
    }

    public @Nullable LevelChunk getChunkToSend() {
        // Paper start - rewrite chunk system
        final LevelChunk ret = this.moonrise$getFullChunk();
        if (ret != null && this.isRadiusLoaded(1)) {
            return ret;
        }
        return null;
        // Paper end - rewrite chunk system
    }

    public CompletableFuture<?> getSendSyncFuture() {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public void addSendDependency(CompletableFuture<?> dependency) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public CompletableFuture<?> getSaveSyncFuture() {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public boolean isReadyForSaving() {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    @Override
    protected void addSaveDependency(CompletableFuture<?> dependency) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public boolean blockChanged(BlockPos pos) {
        LevelChunk tickingChunk = this.playersSentChunkTo.size() == 0 ? null : this.getChunkToSend(); // Paper - rewrite chunk system
        if (tickingChunk == null) {
            return false;
        } else {
            boolean flag = this.hasChangedSections;
            int sectionIndex = this.levelHeightAccessor.getSectionIndex(pos.getY());
            if (sectionIndex < 0 || sectionIndex >= this.changedBlocksPerSection.length) return false; // CraftBukkit - SPIGOT-6086, SPIGOT-6296
            ShortSet set = this.changedBlocksPerSection[sectionIndex];
            if (set == null) {
                this.hasChangedSections = true;
                set = new ShortOpenHashSet();
                this.changedBlocksPerSection[sectionIndex] = set;
            }

            set.add(SectionPos.sectionRelativePos(pos));
            return !flag;
        }
    }

    public boolean sectionLightChanged(LightLayer lightLayer, int y) {
        ChunkAccess chunkIfPresent = this.getChunkIfPresent(ChunkStatus.INITIALIZE_LIGHT);
        if (chunkIfPresent == null) {
            return false;
        } else {
            chunkIfPresent.markUnsaved();
            LevelChunk tickingChunk = this.playersSentChunkTo.size() == 0 ? null : this.getChunkToSend(); // Paper - rewrite chunk system
            if (tickingChunk == null) {
                return false;
            } else {
                int minLightSection = this.lightEngine.getMinLightSection();
                int maxLightSection = this.lightEngine.getMaxLightSection();
                if (y >= minLightSection && y <= maxLightSection) {
                    BitSet bitSet = lightLayer == LightLayer.SKY ? this.skyChangedLightSectionFilter : this.blockChangedLightSectionFilter;
                    int i = y - minLightSection;
                    if (!bitSet.get(i)) {
                        bitSet.set(i);
                        return true;
                    } else {
                        return false;
                    }
                } else {
                    return false;
                }
            }
        }
    }

    public boolean hasChangesToBroadcast() {
        return this.hasChangedSections || !this.skyChangedLightSectionFilter.isEmpty() || !this.blockChangedLightSectionFilter.isEmpty();
    }

    public void broadcastChanges(LevelChunk chunk) {
        if (this.hasChangesToBroadcast()) {
            Level level = chunk.getLevel();
            if (!this.skyChangedLightSectionFilter.isEmpty() || !this.blockChangedLightSectionFilter.isEmpty()) {
                List<ServerPlayer> players = this.moonrise$getPlayers(true); // Paper - rewrite chunk system
                if (!players.isEmpty()) {
                    ClientboundLightUpdatePacket clientboundLightUpdatePacket = new ClientboundLightUpdatePacket(
                        chunk.getPos(), this.lightEngine, this.skyChangedLightSectionFilter, this.blockChangedLightSectionFilter
                    );
                    this.broadcast(players, clientboundLightUpdatePacket);
                }

                this.skyChangedLightSectionFilter.clear();
                this.blockChangedLightSectionFilter.clear();
            }

            if (this.hasChangedSections) {
                List<ServerPlayer> players = this.moonrise$getPlayers(false); // Paper - rewrite chunk system

                for (int i = 0; i < this.changedBlocksPerSection.length; i++) {
                    ShortSet set = this.changedBlocksPerSection[i];
                    if (set != null) {
                        this.changedBlocksPerSection[i] = null;
                        if (!players.isEmpty()) {
                            int sectionYFromSectionIndex = this.levelHeightAccessor.getSectionYFromSectionIndex(i);
                            SectionPos sectionPos = SectionPos.of(chunk.getPos(), sectionYFromSectionIndex);
                            if (set.size() == 1) {
                                BlockPos blockPos = sectionPos.relativeToBlockPos(set.iterator().nextShort());
                                BlockState blockState = level.getBlockState(blockPos);
                                this.broadcast(players, new ClientboundBlockUpdatePacket(blockPos, blockState));
                                this.broadcastBlockEntityIfNeeded(players, level, blockPos, blockState);
                            } else {
                                LevelChunkSection section = chunk.getSection(i);
                                ClientboundSectionBlocksUpdatePacket clientboundSectionBlocksUpdatePacket = new ClientboundSectionBlocksUpdatePacket(
                                    sectionPos, set, section
                                );
                                this.broadcast(players, clientboundSectionBlocksUpdatePacket);
                                clientboundSectionBlocksUpdatePacket.runUpdates(
                                    (blockPos1, blockState1) -> this.broadcastBlockEntityIfNeeded(players, level, blockPos1, blockState1)
                                );
                            }
                        }
                    }
                }

                this.hasChangedSections = false;
            }
        }
    }

    private void broadcastBlockEntityIfNeeded(List<ServerPlayer> players, Level level, BlockPos pos, BlockState state) {
        if (state.hasBlockEntity()) {
            this.broadcastBlockEntity(players, level, pos);
        }
    }

    private void broadcastBlockEntity(List<ServerPlayer> players, Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null) {
            Packet<?> updatePacket = blockEntity.getUpdatePacket();
            if (updatePacket != null) {
                this.broadcast(players, updatePacket);
            }
        }
    }

    private void broadcast(List<ServerPlayer> players, Packet<?> packet) {
        players.forEach(player -> player.connection.send(packet));
    }

    @Override
    public int getTicketLevel() {
        return this.newChunkHolder.getTicketLevel(); // Paper - rewrite chunk system
    }

    @Override
    public int getQueueLevel() {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    private void setQueueLevel(int queueLevel) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public void setTicketLevel(int level) {
        // Paper - rewrite chunk system
    }

    private void scheduleFullChunkPromotion(
        ChunkMap chunkMap, CompletableFuture<ChunkResult<LevelChunk>> future, Executor executor, FullChunkStatus fullChunkStatus
    ) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    private void demoteFullChunk(ChunkMap chunkMap, FullChunkStatus fullChunkStatus) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    // CraftBukkit start
    // ChunkUnloadEvent: Called before the chunk is unloaded: isChunkLoaded is still true and chunk can still be modified by plugins.
    // SPIGOT-7780: Moved out of updateFutures to call all chunk unload events before calling updateHighestAllowedStatus for all chunks
    protected void callEventIfUnloading(ChunkMap chunkMap) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }
    // CraftBukkit end

    protected void updateFutures(ChunkMap chunkMap, Executor executor) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public boolean wasAccessibleSinceLastSave() {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public void refreshAccessibility() {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    @FunctionalInterface
    public interface LevelChangeListener {
        void onLevelChange(ChunkPos chunkPos, IntSupplier queueLevelGetter, int ticketLevel, IntConsumer queueLevelSetter);
    }

    public interface PlayerProvider {
        List<ServerPlayer> getPlayers(ChunkPos pos, boolean boundaryOnly);
    }
}
