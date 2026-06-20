package net.minecraft.world.entity;

import com.google.common.collect.Sets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class LightningBolt extends Entity {
    private static final int START_LIFE = 2;
    private static final double DAMAGE_RADIUS = 3.0;
    private static final double DETECTION_RADIUS = 15.0;
    public int life;
    public long seed;
    public int flashes;
    public boolean visualOnly;
    private @Nullable ServerPlayer cause;
    private final Set<Entity> hitEntities = Sets.newHashSet();
    private int blocksSetOnFire;
    public boolean isEffect; // Paper - Properly handle lightning effects api

    public LightningBolt(EntityType<? extends LightningBolt> type, Level level) {
        super(type, level);
        this.life = 2;
        this.seed = this.random.nextLong();
        this.flashes = this.random.nextInt(3) + 1;
    }

    public void setVisualOnly(boolean visualOnly) {
        this.visualOnly = visualOnly;
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.WEATHER;
    }

    public @Nullable ServerPlayer getCause() {
        return this.cause;
    }

    public void setCause(@Nullable ServerPlayer cause) {
        this.cause = cause;
    }

    private void powerLightningRod() {
        BlockPos strikePosition = this.getStrikePosition();
        BlockState blockState = this.level().getBlockState(strikePosition);
        if (blockState.getBlock() instanceof LightningRodBlock lightningRodBlock) {
            lightningRodBlock.onLightningStrike(blockState, this.level(), strikePosition);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.isEffect && this.life == 2) { // Paper - Properly handle lightning effects api
            if (this.level().isClientSide()) {
                this.level()
                    .playLocalSound(
                        this.getX(),
                        this.getY(),
                        this.getZ(),
                        SoundEvents.LIGHTNING_BOLT_THUNDER,
                        SoundSource.WEATHER,
                        10000.0F,
                        0.8F + this.random.nextFloat() * 0.2F,
                        false
                    );
                this.level()
                    .playLocalSound(
                        this.getX(),
                        this.getY(),
                        this.getZ(),
                        SoundEvents.LIGHTNING_BOLT_IMPACT,
                        SoundSource.WEATHER,
                        2.0F,
                        0.5F + this.random.nextFloat() * 0.2F,
                        false
                    );
            } else {
                Difficulty difficulty = this.level().getDifficulty();
                if (difficulty == Difficulty.NORMAL || difficulty == Difficulty.HARD) {
                    this.spawnFire(4);
                }

                this.powerLightningRod();
                clearCopperOnLightningStrike(this.level(), this.getStrikePosition(), this); // Paper - Call EntityChangeBlockEvent
                this.gameEvent(GameEvent.LIGHTNING_STRIKE);
            }
        }

        this.life--;
        if (this.life < 0) {
            if (this.flashes == 0) {
                if (this.level() instanceof ServerLevel) {
                    List<Entity> entities = this.level()
                        .getEntities(
                            this,
                            new AABB(
                                this.getX() - 15.0, this.getY() - 15.0, this.getZ() - 15.0, this.getX() + 15.0, this.getY() + 6.0 + 15.0, this.getZ() + 15.0
                            ),
                            entity1 -> entity1.isAlive() && !this.hitEntities.contains(entity1)
                        );

                    for (ServerPlayer serverPlayer : ((ServerLevel)this.level()).getPlayers(player -> player.distanceTo(this) < 256.0F)) {
                        CriteriaTriggers.LIGHTNING_STRIKE.trigger(serverPlayer, this, entities);
                    }
                }

                this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
            } else if (this.life < -this.random.nextInt(10)) {
                this.flashes--;
                this.life = 1;
                this.seed = this.random.nextLong();
                this.spawnFire(0);
            }
        }

        if (this.life >= 0 && !this.isEffect) { // Paper - Properly handle lightning effects api
            if (!(this.level() instanceof ServerLevel)) {
                this.level().setSkyFlashTime(2);
            } else if (!this.visualOnly && !this.isEffect) { // Paper - Properly handle lightning effects api
                List<Entity> entities = this.level()
                    .getEntities(
                        this,
                        new AABB(this.getX() - 3.0, this.getY() - 3.0, this.getZ() - 3.0, this.getX() + 3.0, this.getY() + 6.0 + 3.0, this.getZ() + 3.0),
                        Entity::isAlive
                    );

                for (Entity entity : entities) {
                    entity.thunderHit((ServerLevel)this.level(), this);
                }

                this.hitEntities.addAll(entities);
                if (this.cause != null) {
                    CriteriaTriggers.CHANNELED_LIGHTNING.trigger(this.cause, entities);
                }
            }
        }
    }

    private BlockPos getStrikePosition() {
        Vec3 vec3 = this.position();
        return BlockPos.containing(vec3.x, vec3.y - 1.0E-6, vec3.z);
    }

    private void spawnFire(int extraIgnitions) {
        if (!this.visualOnly && !this.isEffect && this.level() instanceof ServerLevel serverLevel) { // Paper - Properly handle lightning effects api
            BlockPos var7 = this.blockPosition();
            if (serverLevel.canSpreadFireAround(var7)) {
                BlockState state = BaseFireBlock.getState(serverLevel, var7);
                if (serverLevel.getBlockState(var7).isAir() && state.canSurvive(serverLevel, var7)) {
                    if (!org.bukkit.craftbukkit.event.CraftEventFactory.callBlockIgniteEvent(this.level(), var7, this).isCancelled()) { // CraftBukkit
                    serverLevel.setBlockAndUpdate(var7, state);
                    this.blocksSetOnFire++;
                    } // CraftBukkit
                }

                for (int i = 0; i < extraIgnitions; i++) {
                    BlockPos blockPos1 = var7.offset(this.random.nextInt(3) - 1, this.random.nextInt(3) - 1, this.random.nextInt(3) - 1);
                    state = BaseFireBlock.getState(serverLevel, blockPos1);
                    if (serverLevel.getBlockState(blockPos1).isAir() && state.canSurvive(serverLevel, blockPos1)) {
                        if (!org.bukkit.craftbukkit.event.CraftEventFactory.callBlockIgniteEvent(this.level(), blockPos1, this).isCancelled()) { // CraftBukkit
                        serverLevel.setBlockAndUpdate(blockPos1, state);
                        this.blocksSetOnFire++;
                        } // CraftBukkit
                    }
                }
            }
        }
    }

    private static void clearCopperOnLightningStrike(Level level, BlockPos pos, Entity lightning) { // Paper - Call EntityChangeBlockEvent
        BlockState blockState = level.getBlockState(pos);
        boolean flag = HoneycombItem.WAX_OFF_BY_BLOCK.get().get(blockState.getBlock()) != null;
        boolean flag1 = blockState.getBlock() instanceof WeatheringCopper;
        if (flag1 || flag) {
            if (flag1) {
                // Paper start - Call EntityChangeBlockEvent
                BlockState newBlockState = WeatheringCopper.getFirst(level.getBlockState(pos));
                if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(lightning, pos, newBlockState)) {
                    level.setBlockAndUpdate(pos, newBlockState);
                }
                // Paper end - Call EntityChangeBlockEvent
            }

            BlockPos.MutableBlockPos mutableBlockPos = pos.mutable();
            int i = level.random.nextInt(3) + 3;

            for (int i1 = 0; i1 < i; i1++) {
                int i2 = level.random.nextInt(8) + 1;
                randomWalkCleaningCopper(level, pos, mutableBlockPos, i2, lightning); // Paper - transmit LightningBolt instance to call EntityChangeBlockEvent
            }
        }
    }

    private static void randomWalkCleaningCopper(Level level, BlockPos pos, BlockPos.MutableBlockPos mutable, int steps, Entity lightning) { // Paper - transmit LightningBolt instance to call EntityChangeBlockEvent
        mutable.set(pos);

        for (int i = 0; i < steps; i++) {
            Optional<BlockPos> optional = randomStepCleaningCopper(level, mutable, lightning); // Paper - transmit LightningBolt instance to call EntityChangeBlockEvent
            if (optional.isEmpty()) {
                break;
            }

            mutable.set(optional.get());
        }
    }

    private static Optional<BlockPos> randomStepCleaningCopper(Level level, BlockPos pos, Entity lightning) { // Paper - transmit LightningBolt instance to call EntityChangeBlockEvent
        for (BlockPos blockPos : BlockPos.randomInCube(level.random, 10, pos, 1)) {
            BlockState blockState = level.getBlockState(blockPos);
            if (blockState.getBlock() instanceof WeatheringCopper) {
                // Paper start - call EntityChangeBlockEvent
                WeatheringCopper.getPrevious(blockState).ifPresent(blockState1 -> {
                    if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(lightning, blockPos, blockState1)) {
                        level.setBlockAndUpdate(blockPos, blockState1);
                    }
                });
                // Paper end - call EntityChangeBlockEvent
                level.levelEvent(LevelEvent.PARTICLES_ELECTRIC_SPARK, blockPos, -1);
                return Optional.of(blockPos);
            }
        }

        return Optional.empty();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double d = 64.0 * getViewScale();
        return distance < d * d;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }

    public int getBlocksSetOnFire() {
        return this.blocksSetOnFire;
    }

    public Stream<Entity> getHitEntities() {
        return this.hitEntities.stream().filter(Entity::isAlive);
    }

    @Override
    public final boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        return false;
    }

    // KioCG start
    @Override
    public boolean shouldTickHot() {
        return false;
    }
    // KioCG end
}
