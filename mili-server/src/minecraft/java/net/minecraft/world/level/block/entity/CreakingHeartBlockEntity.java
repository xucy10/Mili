package net.minecraft.world.level.block.entity;

import com.mojang.datafixers.util.Either;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.TrailParticleOption;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.SpawnUtil;
import net.minecraft.util.Util;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CreakingHeartBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.CreakingHeartState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.Mutable;
import org.apache.commons.lang3.mutable.MutableObject;
import org.jspecify.annotations.Nullable;

public class CreakingHeartBlockEntity extends BlockEntity {
    private static final int PLAYER_DETECTION_RANGE = 32;
    public static final int CREAKING_ROAMING_RADIUS = 32;
    private static final int DISTANCE_CREAKING_TOO_FAR = 34;
    private static final int SPAWN_RANGE_XZ = 16;
    private static final int SPAWN_RANGE_Y = 8;
    private static final int ATTEMPTS_PER_SPAWN = 5;
    private static final int UPDATE_TICKS = 20;
    private static final int UPDATE_TICKS_VARIANCE = 5;
    private static final int HURT_CALL_TOTAL_TICKS = 100;
    private static final int NUMBER_OF_HURT_CALLS = 10;
    private static final int HURT_CALL_INTERVAL = 10;
    private static final int HURT_CALL_PARTICLE_TICKS = 50;
    private static final int MAX_DEPTH = 2;
    private static final int MAX_COUNT = 64;
    private static final int TICKS_GRACE_PERIOD = 30;
    private static final Optional<Creaking> NO_CREAKING = Optional.empty();
    private @Nullable Either<Creaking, UUID> creakingInfo;
    private long ticksExisted;
    private int ticker;
    private int emitter;
    private @Nullable Vec3 emitterTarget;
    private int outputSignal;

    public CreakingHeartBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityType.CREAKING_HEART, pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CreakingHeartBlockEntity creakingHeart) {
        creakingHeart.ticksExisted++;
        if (level instanceof ServerLevel serverLevel) {
            int i = creakingHeart.computeAnalogOutputSignal();
            if (creakingHeart.outputSignal != i) {
                creakingHeart.outputSignal = i;
                level.updateNeighbourForOutputSignal(pos, Blocks.CREAKING_HEART);
            }

            if (creakingHeart.emitter > 0) {
                if (creakingHeart.emitter > 50) {
                    creakingHeart.emitParticles(serverLevel, 1, true);
                    creakingHeart.emitParticles(serverLevel, 1, false);
                }

                if (creakingHeart.emitter % 10 == 0 && creakingHeart.emitterTarget != null) {
                    creakingHeart.getCreakingProtector().ifPresent(creaking1 -> creakingHeart.emitterTarget = creaking1.getBoundingBox().getCenter());
                    Vec3 vec3 = Vec3.atCenterOf(pos);
                    float f = 0.2F + 0.8F * (100 - creakingHeart.emitter) / 100.0F;
                    Vec3 vec31 = vec3.subtract(creakingHeart.emitterTarget).scale(f).add(creakingHeart.emitterTarget);
                    BlockPos blockPos = BlockPos.containing(vec31);
                    float f1 = creakingHeart.emitter / 2.0F / 100.0F + 0.5F;
                    serverLevel.playSound(null, blockPos, SoundEvents.CREAKING_HEART_HURT, SoundSource.BLOCKS, f1, 1.0F);
                }

                creakingHeart.emitter--;
            }

            if (creakingHeart.ticker-- < 0) {
                creakingHeart.ticker = creakingHeart.level == null ? 20 : creakingHeart.level.random.nextInt(5) + 20;
                BlockState blockState = updateCreakingState(level, state, pos, creakingHeart);
                if (blockState != state) {
                    level.setBlock(pos, blockState, Block.UPDATE_ALL);
                    if (blockState.getValue(CreakingHeartBlock.STATE) == CreakingHeartState.UPROOTED) {
                        return;
                    }
                }

                if (creakingHeart.creakingInfo == null) {
                    if (blockState.getValue(CreakingHeartBlock.STATE) == CreakingHeartState.AWAKE) {
                        if (serverLevel.isSpawningMonsters()) {
                            Player nearestPlayer = level.getNearestPlayerThatAffectsSpawning(pos.getX(), pos.getY(), pos.getZ(), 32.0, false); // Paper - Affects Spawning API
                            if (nearestPlayer != null) {
                                Creaking creaking = spawnProtector(serverLevel, creakingHeart);
                                if (creaking != null) {
                                    creakingHeart.setCreakingInfo(creaking);
                                    creaking.makeSound(SoundEvents.CREAKING_SPAWN);
                                    level.playSound(null, creakingHeart.getBlockPos(), SoundEvents.CREAKING_HEART_SPAWN, SoundSource.BLOCKS, 1.0F, 1.0F);
                                }
                            }
                        }
                    }
                } else {
                    Optional<Creaking> creakingProtector = creakingHeart.getCreakingProtector();
                    if (creakingProtector.isPresent()) {
                        Creaking creaking = creakingProtector.get();
                        if (!level.environmentAttributes().getValue(EnvironmentAttributes.CREAKING_ACTIVE, pos) && !creaking.isPersistenceRequired()
                            || creakingHeart.distanceToCreaking() > 34.0
                            || creaking.playerIsStuckInYou()) {
                            creakingHeart.removeProtector(null);
                        }
                    }
                }
            }
        }
    }

    private static BlockState updateCreakingState(Level level, BlockState state, BlockPos pos, CreakingHeartBlockEntity creakingHeart) {
        if (!CreakingHeartBlock.hasRequiredLogs(state, level, pos) && creakingHeart.creakingInfo == null) {
            return state.setValue(CreakingHeartBlock.STATE, CreakingHeartState.UPROOTED);
        } else {
            CreakingHeartState creakingHeartState = level.environmentAttributes().getValue(EnvironmentAttributes.CREAKING_ACTIVE, pos)
                ? CreakingHeartState.AWAKE
                : CreakingHeartState.DORMANT;
            return state.setValue(CreakingHeartBlock.STATE, creakingHeartState);
        }
    }

    private double distanceToCreaking() {
        return this.getCreakingProtector().map(creaking -> Math.sqrt(creaking.distanceToSqr(Vec3.atBottomCenterOf(this.getBlockPos())))).orElse(0.0);
    }

    private void clearCreakingInfo() {
        this.creakingInfo = null;
        this.setChanged();
    }

    public void setCreakingInfo(Creaking creaking) {
        this.creakingInfo = Either.left(creaking);
        this.setChanged();
    }

    public void setCreakingInfo(UUID creakingUuid) {
        this.creakingInfo = Either.right(creakingUuid);
        this.ticksExisted = 0L;
        this.setChanged();
    }

    private Optional<Creaking> getCreakingProtector() {
        if (this.creakingInfo == null) {
            return NO_CREAKING;
        } else {
            if (this.creakingInfo.left().isPresent()) {
                Creaking creaking = this.creakingInfo.left().get();
                if (!creaking.isRemoved()) {
                    return Optional.of(creaking);
                }

                this.setCreakingInfo(creaking.getUUID());
            }

            if (this.level instanceof ServerLevel serverLevel && this.creakingInfo.right().isPresent()) {
                UUID uuid = this.creakingInfo.right().get();
                if (serverLevel.getEntity(uuid) instanceof Creaking creaking1) {
                    this.setCreakingInfo(creaking1);
                    return Optional.of(creaking1);
                } else {
                    if (this.ticksExisted >= 30L) {
                        this.clearCreakingInfo();
                    }

                    return NO_CREAKING;
                }
            } else {
                return NO_CREAKING;
            }
        }
    }

    private static @Nullable Creaking spawnProtector(ServerLevel level, CreakingHeartBlockEntity creakingHeart) {
        BlockPos blockPos = creakingHeart.getBlockPos();
        Optional<Creaking> optional = SpawnUtil.trySpawnMob(
            EntityType.CREAKING, EntitySpawnReason.SPAWNER, level, blockPos, 5, 16, 8, SpawnUtil.Strategy.ON_TOP_OF_COLLIDER_NO_LEAVES, true
        );
        if (optional.isEmpty()) {
            return null;
        } else {
            Creaking creaking = optional.get();
            level.gameEvent(creaking, GameEvent.ENTITY_PLACE, creaking.position());
            level.broadcastEntityEvent(creaking, EntityEvent.POOF);
            creaking.setTransient(blockPos);
            return creaking;
        }
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    public void creakingHurt() {
        if (this.getCreakingProtector().orElse(null) instanceof Creaking creaking) {
            if (this.level instanceof ServerLevel serverLevel) {
                if (this.emitter <= 0) {
                    this.emitParticles(serverLevel, 20, false);
                    if (this.getBlockState().getValue(CreakingHeartBlock.STATE) == CreakingHeartState.AWAKE) {
                        int randomInt = this.level.getRandom().nextIntBetweenInclusive(2, 3);

                        for (int i = 0; i < randomInt; i++) {
                            this.spreadResin(serverLevel).ifPresent(pos -> {
                                this.level.playSound(null, pos, SoundEvents.RESIN_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
                                this.level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(this.getBlockState()));
                            });
                        }
                    }

                    this.emitter = 100;
                    this.emitterTarget = creaking.getBoundingBox().getCenter();
                }
            }
        }
    }

    private Optional<BlockPos> spreadResin(ServerLevel level) {
        Mutable<BlockPos> mutable = new MutableObject<>(null);
        BlockPos.breadthFirstTraversal(this.worldPosition, 2, 64, (pos, output) -> {
            for (Direction direction : Util.shuffledCopy(Direction.values(), level.random)) {
                BlockPos blockPos = pos.relative(direction);
                if (level.getBlockState(blockPos).is(BlockTags.PALE_OAK_LOGS)) {
                    output.accept(blockPos);
                }
            }
        }, pos -> {
            if (!level.getBlockState(pos).is(BlockTags.PALE_OAK_LOGS)) {
                return BlockPos.TraversalNodeStatus.ACCEPT;
            } else {
                for (Direction direction : Util.shuffledCopy(Direction.values(), level.random)) {
                    BlockPos blockPos = pos.relative(direction);
                    BlockState blockState = level.getBlockState(blockPos);
                    Direction opposite = direction.getOpposite();
                    if (blockState.isAir()) {
                        blockState = Blocks.RESIN_CLUMP.defaultBlockState();
                    } else if (blockState.is(Blocks.WATER) && blockState.getFluidState().isSource()) {
                        blockState = Blocks.RESIN_CLUMP.defaultBlockState().setValue(MultifaceBlock.WATERLOGGED, true);
                    }

                    if (blockState.is(Blocks.RESIN_CLUMP) && !MultifaceBlock.hasFace(blockState, opposite)) {
                        level.setBlock(blockPos, blockState.setValue(MultifaceBlock.getFaceProperty(opposite), true), Block.UPDATE_ALL);
                        mutable.setValue(blockPos);
                        return BlockPos.TraversalNodeStatus.STOP;
                    }
                }

                return BlockPos.TraversalNodeStatus.ACCEPT;
            }
        });
        return Optional.ofNullable(mutable.get());
    }

    private void emitParticles(ServerLevel level, int count, boolean reverseDirection) {
        if (this.getCreakingProtector().orElse(null) instanceof Creaking creaking) {
            int i = reverseDirection ? 16545810 : 6250335;
            RandomSource randomSource = level.random;

            for (double d = 0.0; d < count; d++) {
                AABB boundingBox = creaking.getBoundingBox();
                Vec3 vec3 = boundingBox.getMinPosition()
                    .add(
                        randomSource.nextDouble() * boundingBox.getXsize(),
                        randomSource.nextDouble() * boundingBox.getYsize(),
                        randomSource.nextDouble() * boundingBox.getZsize()
                    );
                Vec3 vec31 = Vec3.atLowerCornerOf(this.getBlockPos()).add(randomSource.nextDouble(), randomSource.nextDouble(), randomSource.nextDouble());
                if (reverseDirection) {
                    Vec3 vec32 = vec3;
                    vec3 = vec31;
                    vec31 = vec32;
                }

                TrailParticleOption trailParticleOption = new TrailParticleOption(vec31, i, randomSource.nextInt(40) + 10);
                level.sendParticles(trailParticleOption, true, true, vec3.x, vec3.y, vec3.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        this.removeProtector(null);
    }

    public void removeProtector(@Nullable DamageSource damageSource) {
        if (this.getCreakingProtector().orElse(null) instanceof Creaking creaking) {
            if (damageSource == null) {
                creaking.tearDown();
            } else {
                creaking.creakingDeathEffects(damageSource);
                creaking.setTearingDown();
                creaking.setHealth(0.0F);
            }

            this.clearCreakingInfo();
        }
    }

    public boolean isProtector(Creaking creaking) {
        return this.getCreakingProtector().map(creaking1 -> creaking1 == creaking).orElse(false);
    }

    public int getAnalogOutputSignal() {
        return this.outputSignal;
    }

    public int computeAnalogOutputSignal() {
        if (this.creakingInfo != null && !this.getCreakingProtector().isEmpty()) {
            double d = this.distanceToCreaking();
            double d1 = Math.clamp(d, 0.0, 32.0) / 32.0;
            return 15 - (int)Math.floor(d1 * 15.0);
        } else {
            return 0;
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.read("creaking", UUIDUtil.CODEC).ifPresentOrElse(this::setCreakingInfo, this::clearCreakingInfo);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (this.creakingInfo != null) {
            output.store("creaking", UUIDUtil.CODEC, this.creakingInfo.map(Entity::getUUID, uuid -> (UUID)uuid));
        }
    }
}
