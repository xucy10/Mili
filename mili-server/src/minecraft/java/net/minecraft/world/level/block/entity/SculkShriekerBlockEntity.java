package net.minecraft.world.level.block.entity;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.GameEventTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.SpawnUtil;
import net.minecraft.util.Util;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.monster.warden.WardenSpawnTracker;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.SculkShriekerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.BlockPositionSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class SculkShriekerBlockEntity extends BlockEntity implements GameEventListener.Provider<VibrationSystem.Listener>, VibrationSystem {
    private static final int WARNING_SOUND_RADIUS = 10;
    private static final int WARDEN_SPAWN_ATTEMPTS = 20;
    private static final int WARDEN_SPAWN_RANGE_XZ = 5;
    private static final int WARDEN_SPAWN_RANGE_Y = 6;
    private static final int DARKNESS_RADIUS = 40;
    private static final int SHRIEKING_TICKS = 90;
    private static final Int2ObjectMap<SoundEvent> SOUND_BY_LEVEL = Util.make(new Int2ObjectOpenHashMap<>(), map -> {
        map.put(1, SoundEvents.WARDEN_NEARBY_CLOSE);
        map.put(2, SoundEvents.WARDEN_NEARBY_CLOSER);
        map.put(3, SoundEvents.WARDEN_NEARBY_CLOSEST);
        map.put(4, SoundEvents.WARDEN_LISTENING_ANGRY);
    });
    private static final int DEFAULT_WARNING_LEVEL = 0;
    public int warningLevel = 0;
    private final VibrationSystem.User vibrationUser = new SculkShriekerBlockEntity.VibrationUser();
    private VibrationSystem.Data vibrationData = new VibrationSystem.Data();
    private final VibrationSystem.Listener vibrationListener = new VibrationSystem.Listener(this);

    public SculkShriekerBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityType.SCULK_SHRIEKER, pos, blockState);
    }

    @Override
    public VibrationSystem.Data getVibrationData() {
        return this.vibrationData;
    }

    @Override
    public VibrationSystem.User getVibrationUser() {
        return this.vibrationUser;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.warningLevel = input.getIntOr("warning_level", 0);
        this.vibrationData = input.read("listener", VibrationSystem.Data.CODEC).orElseGet(VibrationSystem.Data::new);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("warning_level", this.warningLevel);
        output.store("listener", VibrationSystem.Data.CODEC, this.vibrationData);
    }

    public static @Nullable ServerPlayer tryGetPlayer(@Nullable Entity entity) {
    // Paper start - check global player list where appropriate; ensure level is the same for sculk events
        final ServerPlayer player = tryGetPlayer0(entity);
        return player != null && player.level() == entity.level() ? player : null;
    }
    @Nullable
    private static ServerPlayer tryGetPlayer0(@Nullable Entity entity) {
    // Paper end - check global player list where appropriate
        if (entity instanceof ServerPlayer serverPlayer) {
            return serverPlayer;
        } else if (entity != null && entity.getControllingPassenger() instanceof ServerPlayer serverPlayer) {
            return serverPlayer;
        } else if (entity instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer serverPlayer1) {
            return serverPlayer1;
        } else {
            return entity instanceof ItemEntity itemEntity && itemEntity.getOwner() instanceof ServerPlayer serverPlayer1 ? serverPlayer1 : null;
        }
    }

    public void tryShriek(ServerLevel level, @Nullable ServerPlayer player) {
        if (player != null) {
            BlockState blockState = this.getBlockState();
            if (!blockState.getValue(SculkShriekerBlock.SHRIEKING)) {
                this.warningLevel = 0;
                if (!this.canRespond(level) || this.tryToWarn(level, player)) {
                    this.shriek(level, player);
                }
            }
        }
    }

    private boolean tryToWarn(ServerLevel level, ServerPlayer player) {
        OptionalInt optionalInt = WardenSpawnTracker.tryWarn(level, this.getBlockPos(), player);
        optionalInt.ifPresent(i -> this.warningLevel = i);
        return optionalInt.isPresent();
    }

    private void shriek(ServerLevel level, @Nullable Entity sourceEntity) {
        BlockPos blockPos = this.getBlockPos();
        BlockState blockState = this.getBlockState();
        level.setBlock(blockPos, blockState.setValue(SculkShriekerBlock.SHRIEKING, true), Block.UPDATE_CLIENTS);
        level.scheduleTick(blockPos, blockState.getBlock(), 90);
        level.levelEvent(LevelEvent.PARTICLES_SCULK_SHRIEK, blockPos, 0);
        level.gameEvent(GameEvent.SHRIEK, blockPos, GameEvent.Context.of(sourceEntity));
    }

    private boolean canRespond(ServerLevel level) {
        return this.getBlockState().getValue(SculkShriekerBlock.CAN_SUMMON)
            && level.getDifficulty() != Difficulty.PEACEFUL
            && level.getGameRules().get(GameRules.SPAWN_WARDENS);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (state.getValue(SculkShriekerBlock.SHRIEKING) && this.level instanceof ServerLevel serverLevel) {
            this.tryRespond(serverLevel);
        }
    }

    public void tryRespond(ServerLevel level) {
        if (this.canRespond(level) && this.warningLevel > 0) {
            if (!this.trySummonWarden(level)) {
                this.playWardenReplySound(level);
            }

            Warden.applyDarknessAround(level, Vec3.atCenterOf(this.getBlockPos()), null, 40);
        }
    }

    private void playWardenReplySound(Level level) {
        SoundEvent soundEvent = SOUND_BY_LEVEL.get(this.warningLevel);
        if (soundEvent != null) {
            BlockPos blockPos = this.getBlockPos();
            int i = blockPos.getX() + Mth.randomBetweenInclusive(level.random, -10, 10);
            int i1 = blockPos.getY() + Mth.randomBetweenInclusive(level.random, -10, 10);
            int i2 = blockPos.getZ() + Mth.randomBetweenInclusive(level.random, -10, 10);
            level.playSound(null, (double)i, (double)i1, (double)i2, soundEvent, SoundSource.HOSTILE, 5.0F, 1.0F);
        }
    }

    private boolean trySummonWarden(ServerLevel level) {
        return this.warningLevel >= 4
            && SpawnUtil.trySpawnMob(
                    EntityType.WARDEN, EntitySpawnReason.TRIGGERED, level, this.getBlockPos(), 20, 5, 6, SpawnUtil.Strategy.ON_TOP_OF_COLLIDER, false, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL, null // Paper - Entity#getEntitySpawnReason
                )
                .isPresent();
    }

    @Override
    public VibrationSystem.Listener getListener() {
        return this.vibrationListener;
    }

    class VibrationUser implements VibrationSystem.User {
        private static final int LISTENER_RADIUS = 8;
        private final PositionSource positionSource = new BlockPositionSource(SculkShriekerBlockEntity.this.worldPosition);

        public VibrationUser() {
        }

        @Override
        public int getListenerRadius() {
            return 8;
        }

        @Override
        public PositionSource getPositionSource() {
            return this.positionSource;
        }

        @Override
        public TagKey<GameEvent> getListenableEvents() {
            return GameEventTags.SHRIEKER_CAN_LISTEN;
        }

        @Override
        public boolean canReceiveVibration(ServerLevel level, BlockPos pos, Holder<GameEvent> gameEvent, GameEvent.Context context) {
            return !SculkShriekerBlockEntity.this.getBlockState().getValue(SculkShriekerBlock.SHRIEKING)
                && SculkShriekerBlockEntity.tryGetPlayer(context.sourceEntity()) != null;
        }

        @Override
        public void onReceiveVibration(
            ServerLevel level, BlockPos pos, Holder<GameEvent> gameEvent, @Nullable Entity entity, @Nullable Entity playerEntity, float distance
        ) {
            SculkShriekerBlockEntity.this.tryShriek(level, SculkShriekerBlockEntity.tryGetPlayer(playerEntity != null ? playerEntity : entity));
        }

        @Override
        public void onDataChanged() {
            SculkShriekerBlockEntity.this.setChanged();
        }

        @Override
        public boolean requiresAdjacentChunksToBeTicking() {
            return true;
        }
    }
}
