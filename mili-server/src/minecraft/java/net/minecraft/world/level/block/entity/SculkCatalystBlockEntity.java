package net.minecraft.world.level.block.entity;

import com.google.common.annotations.VisibleForTesting;
import net.minecraft.Optionull;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SculkCatalystBlock;
import net.minecraft.world.level.block.SculkSpreader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.BlockPositionSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

public class SculkCatalystBlockEntity extends BlockEntity implements GameEventListener.Provider<SculkCatalystBlockEntity.CatalystListener> {
    private final SculkCatalystBlockEntity.CatalystListener catalystListener;

    public SculkCatalystBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityType.SCULK_CATALYST, pos, blockState);
        this.catalystListener = new SculkCatalystBlockEntity.CatalystListener(blockState, new BlockPositionSource(pos));
    }

    // Paper start - Fix NPE in SculkBloomEvent world access
    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        this.catalystListener.sculkSpreader.level = level;
    }
    // Paper end - Fix NPE in SculkBloomEvent world access

    public static void serverTick(Level level, BlockPos pos, BlockState state, SculkCatalystBlockEntity sculkCatalyst) {
        org.bukkit.craftbukkit.event.CraftEventFactory.sourceBlockOverrideRT.set(sculkCatalyst.getBlockPos()); // CraftBukkit - SPIGOT-7068: Add source block override, not the most elegant way but better than passing down a BlockPosition up to five methods deep. // Folia - region threading
        sculkCatalyst.catalystListener.getSculkSpreader().updateCursors(level, pos, level.getRandom(), true);
        org.bukkit.craftbukkit.event.CraftEventFactory.sourceBlockOverrideRT.set(null); // CraftBukkit // Folia - region threading
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.catalystListener.sculkSpreader.load(input);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        this.catalystListener.sculkSpreader.save(output);
        super.saveAdditional(output);
    }

    @Override
    public SculkCatalystBlockEntity.CatalystListener getListener() {
        return this.catalystListener;
    }

    public static class CatalystListener implements GameEventListener {
        public static final int PULSE_TICKS = 8;
        final SculkSpreader sculkSpreader;
        private final BlockState blockState;
        private final PositionSource positionSource;

        public CatalystListener(BlockState blockState, PositionSource positionSource) {
            this.blockState = blockState;
            this.positionSource = positionSource;
            this.sculkSpreader = SculkSpreader.createLevelSpreader();
        }

        @Override
        public PositionSource getListenerSource() {
            return this.positionSource;
        }

        @Override
        public int getListenerRadius() {
            return 8;
        }

        @Override
        public GameEventListener.DeliveryMode getDeliveryMode() {
            return GameEventListener.DeliveryMode.BY_DISTANCE;
        }

        @Override
        public boolean handleGameEvent(ServerLevel level, Holder<GameEvent> gameEvent, GameEvent.Context context, Vec3 pos) {
            if (gameEvent.is(GameEvent.ENTITY_DIE) && context.sourceEntity() instanceof LivingEntity livingEntity) {
                if (!livingEntity.wasExperienceConsumed()) {
                    int experienceReward = livingEntity.expToReward; // Leaves - exp fix
                    if (livingEntity.shouldDropExperience() && experienceReward > 0) {
                        this.sculkSpreader.addCursors(BlockPos.containing(pos.relative(Direction.UP, 0.5)), experienceReward);
                        this.tryAwardItSpreadsAdvancement(level, livingEntity);
                    }

                    livingEntity.skipDropExperience();
                    this.positionSource
                        .getPosition(level)
                        .ifPresent(position -> this.bloom(level, BlockPos.containing(position), this.blockState, level.getRandom()));
                }

                return true;
            } else {
                return false;
            }
        }

        @VisibleForTesting
        public SculkSpreader getSculkSpreader() {
            return this.sculkSpreader;
        }

        public void bloom(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
            level.setBlock(pos, state.setValue(SculkCatalystBlock.PULSE, true), Block.UPDATE_ALL);
            level.scheduleTick(pos, state.getBlock(), 8);
            level.sendParticles(ParticleTypes.SCULK_SOUL, pos.getX() + 0.5, pos.getY() + 1.15, pos.getZ() + 0.5, 2, 0.2, 0.0, 0.2, 0.0);
            level.playSound(null, pos, SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.BLOCKS, 2.0F, 0.6F + random.nextFloat() * 0.4F);
        }

        private void tryAwardItSpreadsAdvancement(Level level, LivingEntity entity) {
            if (entity.getLastHurtByMob() instanceof ServerPlayer serverPlayer) {
                DamageSource damageSource = entity.getLastDamageSource() == null
                    ? level.damageSources().playerAttack(serverPlayer)
                    : entity.getLastDamageSource();
                CriteriaTriggers.KILL_MOB_NEAR_SCULK_CATALYST.trigger(serverPlayer, entity, damageSource);
            }
        }
    }
}
