package net.minecraft.world.level;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.redstone.NeighborUpdater;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.TickPriority;
import org.jspecify.annotations.Nullable;

public interface LevelAccessor extends CommonLevelAccessor, LevelReader, ScheduledTickAccess {
    long nextSubTickCount();

    // Folia start - region threading
    default long getRedstoneGameTime() {
        return this.getLevelData().getGameTime();
    }
    // Folia end - region threading

    @Override
    default <T> ScheduledTick<T> createTick(BlockPos pos, T type, int delay, TickPriority priority) {
        return new ScheduledTick<>(type, pos, this.getRedstoneGameTime() + delay, priority, this.nextSubTickCount()); // Folia - region threading
    }

    @Override
    default <T> ScheduledTick<T> createTick(BlockPos pos, T type, int delay) {
        return new ScheduledTick<>(type, pos, this.getRedstoneGameTime() + delay, this.nextSubTickCount()); // Folia - region threading
    }

    LevelData getLevelData();

    default long getGameTime() { // Folia - region threading - make sure this is present
        return this.getLevelData().getGameTime();
    }

    @Nullable MinecraftServer getServer();

    default Difficulty getDifficulty() {
        return this.getLevelData().getDifficulty();
    }

    ChunkSource getChunkSource();

    @Override
    default boolean hasChunk(int chunkX, int chunkZ) {
        return this.getChunkSource().hasChunk(chunkX, chunkZ);
    }

    RandomSource getRandom();

    default void updateNeighborsAt(BlockPos pos, Block block) {
    }

    default void neighborShapeChanged(
        Direction direction, BlockPos pos, BlockPos neighborPos, BlockState neighborState, @Block.UpdateFlags int flags, int recursionLeft
    ) {
        NeighborUpdater.executeShapeUpdate(this, direction, pos, neighborPos, neighborState, flags, recursionLeft - 1);
    }

    default void playSound(@Nullable Entity entity, BlockPos pos, SoundEvent sound, SoundSource source) {
        this.playSound(entity, pos, sound, source, 1.0F, 1.0F);
    }

    void playSound(@Nullable Entity entity, BlockPos pos, SoundEvent sound, SoundSource source, float volume, float pitch);

    void addParticle(ParticleOptions options, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed);

    void levelEvent(@Nullable Entity entity, int type, BlockPos pos, int data);

    default void levelEvent(int type, BlockPos pos, int data) {
        this.levelEvent(null, type, pos, data);
    }

    void gameEvent(Holder<GameEvent> gameEvent, Vec3 pos, GameEvent.Context context);

    default void gameEvent(@Nullable Entity entity, Holder<GameEvent> gameEvent, Vec3 pos) {
        this.gameEvent(gameEvent, pos, new GameEvent.Context(entity, null));
    }

    default void gameEvent(@Nullable Entity entity, Holder<GameEvent> gameEvent, BlockPos pos) {
        this.gameEvent(gameEvent, pos, new GameEvent.Context(entity, null));
    }

    default void gameEvent(Holder<GameEvent> gameEvent, BlockPos pos, GameEvent.Context context) {
        this.gameEvent(gameEvent, Vec3.atCenterOf(pos), context);
    }

    default void gameEvent(ResourceKey<GameEvent> gameEvent, BlockPos pos, GameEvent.Context context) {
        this.gameEvent(this.registryAccess().lookupOrThrow(Registries.GAME_EVENT).getOrThrow(gameEvent), pos, context);
    }

    net.minecraft.server.level.ServerLevel getMinecraftWorld(); // CraftBukkit
}
