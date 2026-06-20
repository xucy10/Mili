package net.minecraft.world.level.block.entity.trialspawner;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OminousItemSpawner;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

public enum TrialSpawnerState implements StringRepresentable {
    INACTIVE("inactive", 0, TrialSpawnerState.ParticleEmission.NONE, -1.0, false),
    WAITING_FOR_PLAYERS("waiting_for_players", 4, TrialSpawnerState.ParticleEmission.SMALL_FLAMES, 200.0, true),
    ACTIVE("active", 8, TrialSpawnerState.ParticleEmission.FLAMES_AND_SMOKE, 1000.0, true),
    WAITING_FOR_REWARD_EJECTION("waiting_for_reward_ejection", 8, TrialSpawnerState.ParticleEmission.SMALL_FLAMES, -1.0, false),
    EJECTING_REWARD("ejecting_reward", 8, TrialSpawnerState.ParticleEmission.SMALL_FLAMES, -1.0, false),
    COOLDOWN("cooldown", 0, TrialSpawnerState.ParticleEmission.SMOKE_INSIDE_AND_TOP_FACE, -1.0, false);

    private static final float DELAY_BEFORE_EJECT_AFTER_KILLING_LAST_MOB = 40.0F;
    private static final int TIME_BETWEEN_EACH_EJECTION = Mth.floor(30.0F);
    private final String name;
    private final int lightLevel;
    private final double spinningMobSpeed;
    private final TrialSpawnerState.ParticleEmission particleEmission;
    private final boolean isCapableOfSpawning;

    private TrialSpawnerState(
        final String name,
        final int lightLevel,
        final TrialSpawnerState.ParticleEmission particleEmission,
        final double spinningMobSpeed,
        final boolean isCapableOfSpawning
    ) {
        this.name = name;
        this.lightLevel = lightLevel;
        this.particleEmission = particleEmission;
        this.spinningMobSpeed = spinningMobSpeed;
        this.isCapableOfSpawning = isCapableOfSpawning;
    }

    TrialSpawnerState tickAndGetNext(BlockPos pos, TrialSpawner spawner, ServerLevel level) {
        TrialSpawnerStateData stateData = spawner.getStateData();
        TrialSpawnerConfig trialSpawnerConfig = spawner.activeConfig();

        return switch (this) {
            case INACTIVE -> stateData.getOrCreateDisplayEntity(spawner, level, WAITING_FOR_PLAYERS) == null ? this : WAITING_FOR_PLAYERS;
            case WAITING_FOR_PLAYERS -> {
                if (!spawner.canSpawnInLevel(level)) {
                    stateData.resetStatistics();
                    yield this;
                } else if (!stateData.hasMobToSpawn(spawner, level.random)) {
                    yield INACTIVE;
                } else {
                    stateData.tryDetectPlayers(level, pos, spawner);
                    yield stateData.detectedPlayers.isEmpty() ? this : ACTIVE;
                }
            }
            case ACTIVE -> {
                if (!spawner.canSpawnInLevel(level)) {
                    stateData.resetStatistics();
                    yield WAITING_FOR_PLAYERS;
                } else if (!stateData.hasMobToSpawn(spawner, level.random)) {
                    yield INACTIVE;
                } else {
                    int i = stateData.countAdditionalPlayers(pos);
                    stateData.tryDetectPlayers(level, pos, spawner);
                    if (spawner.isOminous()) {
                        this.spawnOminousOminousItemSpawner(level, pos, spawner);
                    }

                    if (stateData.hasFinishedSpawningAllMobs(trialSpawnerConfig, i)) {
                        if (stateData.haveAllCurrentMobsDied()) {
                            stateData.cooldownEndsAt = level.getGameTime() + spawner.getTargetCooldownLength();
                            stateData.totalMobsSpawned = 0;
                            stateData.nextMobSpawnsAt = 0L;
                            yield WAITING_FOR_REWARD_EJECTION;
                        }
                    } else if (stateData.isReadyToSpawnNextMob(level, trialSpawnerConfig, i)) {
                        spawner.spawnMob(level, pos).ifPresent(uuid -> {
                            stateData.currentMobs.add(uuid);
                            stateData.totalMobsSpawned++;
                            stateData.nextMobSpawnsAt = level.getGameTime() + trialSpawnerConfig.ticksBetweenSpawn();
                            trialSpawnerConfig.spawnPotentialsDefinition().getRandom(level.getRandom()).ifPresent(spawnData -> {
                                stateData.nextSpawnData = Optional.of(spawnData);
                                spawner.markUpdated();
                            });
                        });
                    }

                    yield this;
                }
            }
            case WAITING_FOR_REWARD_EJECTION -> {
                if (stateData.isReadyToOpenShutter(level, 40.0F, spawner.getTargetCooldownLength())) {
                    level.playSound(null, pos, SoundEvents.TRIAL_SPAWNER_OPEN_SHUTTER, SoundSource.BLOCKS);
                    yield EJECTING_REWARD;
                } else {
                    yield this;
                }
            }
            case EJECTING_REWARD -> {
                if (!stateData.isReadyToEjectItems(level, TIME_BETWEEN_EACH_EJECTION, spawner.getTargetCooldownLength())) {
                    yield this;
                } else if (stateData.detectedPlayers.isEmpty()) {
                    level.playSound(null, pos, SoundEvents.TRIAL_SPAWNER_CLOSE_SHUTTER, SoundSource.BLOCKS);
                    stateData.ejectingLootTable = Optional.empty();
                    yield COOLDOWN;
                } else {
                    if (stateData.ejectingLootTable.isEmpty()) {
                        stateData.ejectingLootTable = trialSpawnerConfig.lootTablesToEject().getRandom(level.getRandom());
                    }

                    stateData.ejectingLootTable.ifPresent(resourceKey -> spawner.ejectReward(level, pos, (ResourceKey<LootTable>)resourceKey));
                    stateData.detectedPlayers.remove(stateData.detectedPlayers.iterator().next());
                    yield this;
                }
            }
            case COOLDOWN -> {
                stateData.tryDetectPlayers(level, pos, spawner);
                if (!stateData.detectedPlayers.isEmpty()) {
                    stateData.totalMobsSpawned = 0;
                    stateData.nextMobSpawnsAt = 0L;
                    yield ACTIVE;
                } else if (stateData.isCooldownFinished(level)) {
                    spawner.removeOminous(level, pos);
                    stateData.reset();
                    yield WAITING_FOR_PLAYERS;
                } else {
                    yield this;
                }
            }
        };
    }

    private void spawnOminousOminousItemSpawner(ServerLevel level, BlockPos pos, TrialSpawner spawner) {
        TrialSpawnerStateData stateData = spawner.getStateData();
        TrialSpawnerConfig trialSpawnerConfig = spawner.activeConfig();
        ItemStack itemStack = stateData.getDispensingItems(level, trialSpawnerConfig, pos).getRandom(level.random).orElse(ItemStack.EMPTY);
        if (!itemStack.isEmpty()) {
            if (this.timeToSpawnItemSpawner(level, stateData)) {
                calculatePositionToSpawnSpawner(level, pos, spawner, stateData).ifPresent(vec3 -> {
                    OminousItemSpawner ominousItemSpawner = OminousItemSpawner.create(level, itemStack);
                    ominousItemSpawner.snapTo(vec3);
                    level.addFreshEntity(ominousItemSpawner);
                    float f = (level.getRandom().nextFloat() - level.getRandom().nextFloat()) * 0.2F + 1.0F;
                    level.playSound(null, BlockPos.containing(vec3), SoundEvents.TRIAL_SPAWNER_SPAWN_ITEM_BEGIN, SoundSource.BLOCKS, 1.0F, f);
                    stateData.cooldownEndsAt = level.getGameTime() + spawner.ominousConfig().ticksBetweenItemSpawners();
                });
            }
        }
    }

    private static Optional<Vec3> calculatePositionToSpawnSpawner(ServerLevel level, BlockPos pos, TrialSpawner spawner, TrialSpawnerStateData data) {
        List<Player> list = data.detectedPlayers
            .stream()
            .map(level::getPlayerByUUID)
            .filter(Objects::nonNull)
            .filter(
                player -> !player.isCreative()
                    && !player.isSpectator()
                    && player.isAlive()
                    && player.distanceToSqr(pos.getCenter()) <= Mth.square(spawner.getRequiredPlayerRange())
            )
            .toList();
        if (list.isEmpty()) {
            return Optional.empty();
        } else {
            Entity entity = selectEntityToSpawnItemAbove(list, data.currentMobs, spawner, pos, level);
            return entity == null ? Optional.empty() : calculatePositionAbove(entity, level);
        }
    }

    private static Optional<Vec3> calculatePositionAbove(Entity entity, ServerLevel level) {
        Vec3 vec3 = entity.position();
        Vec3 vec31 = vec3.relative(Direction.UP, entity.getBbHeight() + 2.0F + level.random.nextInt(4));
        BlockHitResult blockHitResult = level.clip(new ClipContext(vec3, vec31, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty()));
        Vec3 vec32 = blockHitResult.getBlockPos().getCenter().relative(Direction.DOWN, 1.0);
        BlockPos blockPos = BlockPos.containing(vec32);
        return !level.getBlockState(blockPos).getCollisionShape(level, blockPos).isEmpty() ? Optional.empty() : Optional.of(vec32);
    }

    private static @Nullable Entity selectEntityToSpawnItemAbove(
        List<Player> player, Set<UUID> currentMobs, TrialSpawner spawner, BlockPos pos, ServerLevel level
    ) {
        Stream<Entity> stream = currentMobs.stream()
            .map(level::getEntity)
            .filter(Objects::nonNull)
            .filter(entity -> entity.isAlive() && entity.distanceToSqr(pos.getCenter()) <= Mth.square(spawner.getRequiredPlayerRange()));
        List<? extends Entity> list = level.random.nextBoolean() ? stream.toList() : player;
        if (list.isEmpty()) {
            return null;
        } else {
            return list.size() == 1 ? list.getFirst() : Util.getRandom(list, level.random);
        }
    }

    private boolean timeToSpawnItemSpawner(ServerLevel level, TrialSpawnerStateData data) {
        return level.getGameTime() >= data.cooldownEndsAt;
    }

    public int lightLevel() {
        return this.lightLevel;
    }

    public double spinningMobSpeed() {
        return this.spinningMobSpeed;
    }

    public boolean hasSpinningMob() {
        return this.spinningMobSpeed >= 0.0;
    }

    public boolean isCapableOfSpawning() {
        return this.isCapableOfSpawning;
    }

    public void emitParticles(Level level, BlockPos pos, boolean isOminous) {
        this.particleEmission.emit(level, level.getRandom(), pos, isOminous);
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    static class LightLevel {
        private static final int UNLIT = 0;
        private static final int HALF_LIT = 4;
        private static final int LIT = 8;

        private LightLevel() {
        }
    }

    interface ParticleEmission {
        TrialSpawnerState.ParticleEmission NONE = (level, random, pos, isOminous) -> {};
        TrialSpawnerState.ParticleEmission SMALL_FLAMES = (level, random, pos, isOminous) -> {
            if (random.nextInt(2) == 0) {
                Vec3 vec3 = pos.getCenter().offsetRandom(random, 0.9F);
                addParticle(isOminous ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.SMALL_FLAME, vec3, level);
            }
        };
        TrialSpawnerState.ParticleEmission FLAMES_AND_SMOKE = (level, random, pos, isOminous) -> {
            Vec3 vec3 = pos.getCenter().offsetRandom(random, 1.0F);
            addParticle(ParticleTypes.SMOKE, vec3, level);
            addParticle(isOminous ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, vec3, level);
        };
        TrialSpawnerState.ParticleEmission SMOKE_INSIDE_AND_TOP_FACE = (level, random, pos, isOminous) -> {
            Vec3 vec3 = pos.getCenter().offsetRandom(random, 0.9F);
            if (random.nextInt(3) == 0) {
                addParticle(ParticleTypes.SMOKE, vec3, level);
            }

            if (level.getGameTime() % 20L == 0L) {
                Vec3 vec31 = pos.getCenter().add(0.0, 0.5, 0.0);
                int i = level.getRandom().nextInt(4) + 20;

                for (int i1 = 0; i1 < i; i1++) {
                    addParticle(ParticleTypes.SMOKE, vec31, level);
                }
            }
        };

        private static void addParticle(SimpleParticleType particleType, Vec3 pos, Level level) {
            level.addParticle(particleType, pos.x(), pos.y(), pos.z(), 0.0, 0.0, 0.0);
        }

        void emit(Level level, RandomSource random, BlockPos pos, boolean isOminous);
    }

    static class SpinningMob {
        private static final double NONE = -1.0;
        private static final double SLOW = 200.0;
        private static final double FAST = 1000.0;

        private SpinningMob() {
        }
    }
}
