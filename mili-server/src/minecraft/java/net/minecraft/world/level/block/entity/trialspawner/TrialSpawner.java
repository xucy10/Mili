package net.minecraft.world.level.block.entity.trialspawner;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.TrialSpawnerBlock;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.slf4j.Logger;

public final class TrialSpawner {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int DETECT_PLAYER_SPAWN_BUFFER = 40;
    private static final int DEFAULT_TARGET_COOLDOWN_LENGTH = 36000;
    private static final int DEFAULT_PLAYER_SCAN_RANGE = 14;
    private static final int MAX_MOB_TRACKING_DISTANCE = 47;
    private static final int MAX_MOB_TRACKING_DISTANCE_SQR = Mth.square(47);
    private static final float SPAWNING_AMBIENT_SOUND_CHANCE = 0.02F;
    private final TrialSpawnerStateData data = new TrialSpawnerStateData();
    public TrialSpawner.FullConfig config;
    public final TrialSpawner.StateAccessor stateAccessor;
    private PlayerDetector playerDetector;
    private final PlayerDetector.EntitySelector entitySelector;
    private boolean overridePeacefulAndMobSpawnRule;
    public boolean isOminous;

    public TrialSpawner(
        TrialSpawner.FullConfig config, TrialSpawner.StateAccessor stateAccessor, PlayerDetector playerDetector, PlayerDetector.EntitySelector entitySelector
    ) {
        this.config = config;
        this.stateAccessor = stateAccessor;
        this.playerDetector = playerDetector;
        this.entitySelector = entitySelector;
    }

    public TrialSpawnerConfig activeConfig() {
        return this.isOminous ? this.config.ominous().value() : this.config.normal.value();
    }

    public TrialSpawnerConfig normalConfig() {
        return this.config.normal.value();
    }

    public TrialSpawnerConfig ominousConfig() {
        return this.config.ominous.value();
    }

    public void load(ValueInput input) {
        input.read(TrialSpawnerStateData.Packed.MAP_CODEC).ifPresent(this.data::apply);
        this.config = input.read(TrialSpawner.FullConfig.MAP_CODEC).orElse(TrialSpawner.FullConfig.DEFAULT);
    }

    public void store(ValueOutput output) {
        output.store(TrialSpawnerStateData.Packed.MAP_CODEC, this.data.pack());
        output.store(TrialSpawner.FullConfig.MAP_CODEC, this.config);
    }

    public void applyOminous(ServerLevel level, BlockPos pos) {
        level.setBlock(pos, level.getBlockState(pos).setValue(TrialSpawnerBlock.OMINOUS, true), Block.UPDATE_ALL);
        level.levelEvent(LevelEvent.PARTICLES_TRIAL_SPAWNER_BECOME_OMINOUS, pos, 1);
        this.isOminous = true;
        this.data.resetAfterBecomingOminous(this, level);
    }

    public void removeOminous(ServerLevel level, BlockPos pos) {
        level.setBlock(pos, level.getBlockState(pos).setValue(TrialSpawnerBlock.OMINOUS, false), Block.UPDATE_ALL);
        this.isOminous = false;
    }

    public boolean isOminous() {
        return this.isOminous;
    }

    public int getTargetCooldownLength() {
        return this.config.targetCooldownLength;
    }

    public int getRequiredPlayerRange() {
        return this.config.requiredPlayerRange;
    }

    public TrialSpawnerState getState() {
        return this.stateAccessor.getState();
    }

    public TrialSpawnerStateData getStateData() {
        return this.data;
    }

    public void setState(Level level, TrialSpawnerState state) {
        this.stateAccessor.setState(level, state);
    }

    public void markUpdated() {
        this.stateAccessor.markUpdated();
    }

    public PlayerDetector getPlayerDetector() {
        return this.playerDetector;
    }

    public PlayerDetector.EntitySelector getEntitySelector() {
        return this.entitySelector;
    }

    public boolean canSpawnInLevel(ServerLevel level) {
        return level.getGameRules().get(GameRules.SPAWNER_BLOCKS_WORK)
            && (this.overridePeacefulAndMobSpawnRule || level.getDifficulty() != Difficulty.PEACEFUL && level.getGameRules().get(GameRules.SPAWN_MOBS));
    }

    public Optional<UUID> spawnMob(ServerLevel level, BlockPos pos) {
        RandomSource random = level.getRandom();
        SpawnData nextSpawnData = this.data.getOrCreateNextSpawnData(this, level.getRandom());

        Optional var24;
        try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(() -> "spawner@" + pos, LOGGER)) {
            ValueInput valueInput = TagValueInput.create(scopedCollector, level.registryAccess(), nextSpawnData.entityToSpawn());
            Optional<EntityType<?>> optional = EntityType.by(valueInput);
            if (optional.isEmpty()) {
                return Optional.empty();
            }

            Vec3 vec3 = valueInput.read("Pos", Vec3.CODEC)
                .orElseGet(
                    () -> {
                        TrialSpawnerConfig trialSpawnerConfig = this.activeConfig();
                        return new Vec3(
                            pos.getX() + (random.nextDouble() - random.nextDouble()) * trialSpawnerConfig.spawnRange() + 0.5,
                            pos.getY() + random.nextInt(3) - 1,
                            pos.getZ() + (random.nextDouble() - random.nextDouble()) * trialSpawnerConfig.spawnRange() + 0.5
                        );
                    }
                );
            if (!level.noCollision(optional.get().getSpawnAABB(vec3.x, vec3.y, vec3.z))) {
                return Optional.empty();
            }

            if (!inLineOfSight(level, pos.getCenter(), vec3)) {
                return Optional.empty();
            }

            BlockPos blockPos = BlockPos.containing(vec3);
            if (!SpawnPlacements.checkSpawnRules(optional.get(), level, EntitySpawnReason.TRIAL_SPAWNER, blockPos, level.getRandom())) {
                return Optional.empty();
            }

            if (nextSpawnData.getCustomSpawnRules().isPresent()) {
                SpawnData.CustomSpawnRules customSpawnRules = nextSpawnData.getCustomSpawnRules().get();
                if (!customSpawnRules.isValidPosition(blockPos, level)) {
                    return Optional.empty();
                }
            }

            Entity entity = EntityType.loadEntityRecursive(valueInput, level, EntitySpawnReason.TRIAL_SPAWNER, entity1 -> {
                entity1.snapTo(vec3.x, vec3.y, vec3.z, random.nextFloat() * 360.0F, 0.0F);
                return entity1;
            });
            if (entity == null) {
                return Optional.empty();
            }

            if (entity instanceof Mob mob) {
                if (!mob.checkSpawnObstruction(level)) {
                    return Optional.empty();
                }

                boolean flag = nextSpawnData.getEntityToSpawn().size() == 1 && nextSpawnData.getEntityToSpawn().getString("id").isPresent();
                if (flag) {
                    mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.TRIAL_SPAWNER, null);
                }

                mob.setPersistenceRequired();
                nextSpawnData.getEquipment().ifPresent(mob::equip);
            }

            // Paper start - TrialSpawnerSpawnEvent + SpawnReason
            entity.spawnedViaMobSpawner = true; // Mark entity as spawned via spawner
            entity.spawnReason = org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.TRIAL_SPAWNER; // Paper - Entity#getEntitySpawnReason
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callTrialSpawnerSpawnEvent(entity, pos).isCancelled()) {
                return Optional.empty();
            }
            if (!level.tryAddFreshEntityWithPassengers(entity, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.TRIAL_SPAWNER)) {
                // Paper end - TrialSpawnerSpawnEvent + SpawnReason
                return Optional.empty();
            }

            TrialSpawner.FlameParticle flameParticle = this.isOminous ? TrialSpawner.FlameParticle.OMINOUS : TrialSpawner.FlameParticle.NORMAL;
            level.levelEvent(LevelEvent.PARTICLES_TRIAL_SPAWNER_SPAWN, pos, flameParticle.encode());
            level.levelEvent(LevelEvent.PARTICLES_TRIAL_SPAWNER_SPAWN_MOB_AT, blockPos, flameParticle.encode());
            level.gameEvent(entity, GameEvent.ENTITY_PLACE, blockPos);
            var24 = Optional.of(entity.getUUID());
        }

        return var24;
    }

    public void ejectReward(ServerLevel level, BlockPos pos, ResourceKey<LootTable> lootTable) {
        LootTable lootTable1 = level.getServer().reloadableRegistries().getLootTable(lootTable); final LootTable sourceLootTable = lootTable1; // Paper - obfhelper
        LootParams lootParams = new LootParams.Builder(level).create(LootContextParamSets.EMPTY);
        ObjectArrayList<ItemStack> randomItems = lootTable1.getRandomItems(lootParams);
        if (!randomItems.isEmpty()) {
            // CraftBukkit start
            org.bukkit.event.block.BlockDispenseLootEvent spawnerDispenseLootEvent = org.bukkit.craftbukkit.event.CraftEventFactory.callBlockDispenseLootEvent(
                level,
                pos,
                null,
                randomItems,
                sourceLootTable
            );
            if (spawnerDispenseLootEvent.isCancelled()) {
                return;
            }

            randomItems = new ObjectArrayList<>(spawnerDispenseLootEvent.getDispensedLoot().stream().map(org.bukkit.craftbukkit.inventory.CraftItemStack::asNMSCopy).toList());
            // CraftBukkit end
            for (ItemStack itemStack : randomItems) {
                DefaultDispenseItemBehavior.spawnItem(level, itemStack, 2, Direction.UP, Vec3.atBottomCenterOf(pos).relative(Direction.UP, 1.2));
            }

            level.levelEvent(LevelEvent.ANIMATION_TRIAL_SPAWNER_EJECT_ITEM, pos, 0);
        }
    }

    public void tickClient(Level level, BlockPos pos, boolean isOminous) {
        TrialSpawnerState state = this.getState();
        state.emitParticles(level, pos, isOminous);
        if (state.hasSpinningMob()) {
            double d = Math.max(0L, this.data.nextMobSpawnsAt - level.getGameTime());
            this.data.oSpin = this.data.spin;
            this.data.spin = (this.data.spin + state.spinningMobSpeed() / (d + 200.0)) % 360.0;
        }

        if (state.isCapableOfSpawning()) {
            RandomSource random = level.getRandom();
            if (random.nextFloat() <= 0.02F) {
                SoundEvent soundEvent = isOminous ? SoundEvents.TRIAL_SPAWNER_AMBIENT_OMINOUS : SoundEvents.TRIAL_SPAWNER_AMBIENT;
                level.playLocalSound(pos, soundEvent, SoundSource.BLOCKS, random.nextFloat() * 0.25F + 0.75F, random.nextFloat() + 0.5F, false);
            }
        }
    }

    public void tickServer(ServerLevel level, BlockPos pos, boolean isOminous) {
        this.isOminous = isOminous;
        TrialSpawnerState state = this.getState();
        if (this.data.currentMobs.removeIf(mob -> shouldMobBeUntracked(level, pos, mob))) {
            this.data.nextMobSpawnsAt = level.getGameTime() + this.activeConfig().ticksBetweenSpawn();
        }

        TrialSpawnerState trialSpawnerState = state.tickAndGetNext(pos, this, level);
        if (trialSpawnerState != state) {
            this.setState(level, trialSpawnerState);
        }
    }

    private static boolean shouldMobBeUntracked(ServerLevel level, BlockPos pos, UUID uuid) {
        Entity entity = level.getEntity(uuid);
        return entity == null
            || !entity.isAlive()
            || !entity.level().dimension().equals(level.dimension())
            || entity.blockPosition().distSqr(pos) > MAX_MOB_TRACKING_DISTANCE_SQR;
    }

    private static boolean inLineOfSight(Level level, Vec3 spawnerPos, Vec3 mobPos) {
        BlockHitResult blockHitResult = level.clip(
            new ClipContext(mobPos, spawnerPos, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty())
        );
        return blockHitResult.getBlockPos().equals(BlockPos.containing(spawnerPos)) || blockHitResult.getType() == HitResult.Type.MISS;
    }

    public static void addSpawnParticles(Level level, BlockPos pos, RandomSource random, SimpleParticleType particleType) {
        for (int i = 0; i < 20; i++) {
            double d = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 2.0;
            double d1 = pos.getY() + 0.5 + (random.nextDouble() - 0.5) * 2.0;
            double d2 = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 2.0;
            level.addParticle(ParticleTypes.SMOKE, d, d1, d2, 0.0, 0.0, 0.0);
            level.addParticle(particleType, d, d1, d2, 0.0, 0.0, 0.0);
        }
    }

    public static void addBecomeOminousParticles(Level level, BlockPos pos, RandomSource random) {
        for (int i = 0; i < 20; i++) {
            double d = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 2.0;
            double d1 = pos.getY() + 0.5 + (random.nextDouble() - 0.5) * 2.0;
            double d2 = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 2.0;
            double d3 = random.nextGaussian() * 0.02;
            double d4 = random.nextGaussian() * 0.02;
            double d5 = random.nextGaussian() * 0.02;
            level.addParticle(ParticleTypes.TRIAL_OMEN, d, d1, d2, d3, d4, d5);
            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, d, d1, d2, d3, d4, d5);
        }
    }

    public static void addDetectPlayerParticles(Level level, BlockPos pos, RandomSource random, int type, ParticleOptions particle) {
        for (int i = 0; i < 30 + Math.min(type, 10) * 5; i++) {
            double d = (2.0F * random.nextFloat() - 1.0F) * 0.65;
            double d1 = (2.0F * random.nextFloat() - 1.0F) * 0.65;
            double d2 = pos.getX() + 0.5 + d;
            double d3 = pos.getY() + 0.1 + random.nextFloat() * 0.8;
            double d4 = pos.getZ() + 0.5 + d1;
            level.addParticle(particle, d2, d3, d4, 0.0, 0.0, 0.0);
        }
    }

    public static void addEjectItemParticles(Level level, BlockPos pos, RandomSource random) {
        for (int i = 0; i < 20; i++) {
            double d = pos.getX() + 0.4 + random.nextDouble() * 0.2;
            double d1 = pos.getY() + 0.4 + random.nextDouble() * 0.2;
            double d2 = pos.getZ() + 0.4 + random.nextDouble() * 0.2;
            double d3 = random.nextGaussian() * 0.02;
            double d4 = random.nextGaussian() * 0.02;
            double d5 = random.nextGaussian() * 0.02;
            level.addParticle(ParticleTypes.SMALL_FLAME, d, d1, d2, d3, d4, d5 * 0.25);
            level.addParticle(ParticleTypes.SMOKE, d, d1, d2, d3, d4, d5);
        }
    }

    public void overrideEntityToSpawn(EntityType<?> entityType, Level level) {
        this.data.reset();
        this.config = this.config.overrideEntity(entityType);
        this.setState(level, TrialSpawnerState.INACTIVE);
    }

    @Deprecated(forRemoval = true)
    @VisibleForTesting
    public void setPlayerDetector(PlayerDetector playerDetector) {
        this.playerDetector = playerDetector;
    }

    @Deprecated(forRemoval = true)
    @VisibleForTesting
    public void overridePeacefulAndMobSpawnRule() {
        this.overridePeacefulAndMobSpawnRule = true;
    }

    public static enum FlameParticle {
        NORMAL(ParticleTypes.FLAME),
        OMINOUS(ParticleTypes.SOUL_FIRE_FLAME);

        public final SimpleParticleType particleType;

        private FlameParticle(final SimpleParticleType particleType) {
            this.particleType = particleType;
        }

        public static TrialSpawner.FlameParticle decode(int id) {
            TrialSpawner.FlameParticle[] flameParticles = values();
            return id <= flameParticles.length && id >= 0 ? flameParticles[id] : NORMAL;
        }

        public int encode() {
            return this.ordinal();
        }
    }

    public record FullConfig(Holder<TrialSpawnerConfig> normal, Holder<TrialSpawnerConfig> ominous, int targetCooldownLength, int requiredPlayerRange) {
        public static final MapCodec<TrialSpawner.FullConfig> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    TrialSpawnerConfig.CODEC
                        .optionalFieldOf("normal_config", Holder.direct(TrialSpawnerConfig.DEFAULT))
                        .forGetter(TrialSpawner.FullConfig::normal),
                    TrialSpawnerConfig.CODEC
                        .optionalFieldOf("ominous_config", Holder.direct(TrialSpawnerConfig.DEFAULT))
                        .forGetter(TrialSpawner.FullConfig::ominous),
                    ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("target_cooldown_length", 36000).forGetter(TrialSpawner.FullConfig::targetCooldownLength),
                    Codec.intRange(1, 128).optionalFieldOf("required_player_range", 14).forGetter(TrialSpawner.FullConfig::requiredPlayerRange)
                )
                .apply(instance, TrialSpawner.FullConfig::new)
        );
        public static final TrialSpawner.FullConfig DEFAULT = new TrialSpawner.FullConfig(
            Holder.direct(TrialSpawnerConfig.DEFAULT), Holder.direct(TrialSpawnerConfig.DEFAULT), 36000, 14
        );

        public TrialSpawner.FullConfig overrideEntity(EntityType<?> entity) {
            return new TrialSpawner.FullConfig(
                Holder.direct(this.normal.value().withSpawning(entity)),
                Holder.direct(this.ominous.value().withSpawning(entity)),
                this.targetCooldownLength,
                this.requiredPlayerRange
            );
        }

        // Paper start - trial spawner API - withers
        public TrialSpawner.FullConfig overrideTargetCooldownLength(final int targetCooldownLength) {
            return new TrialSpawner.FullConfig(
                this.normal,
                this.ominous,
                targetCooldownLength,
                this.requiredPlayerRange
            );
        }

        public TrialSpawner.FullConfig overrideRequiredPlayerRange(final int requiredPlayerRange) {
            return new TrialSpawner.FullConfig(
                this.normal,
                this.ominous,
                this.targetCooldownLength,
                requiredPlayerRange
            );
        }

        public TrialSpawner.FullConfig overrideConfigs(final Holder<TrialSpawnerConfig> normal, final Holder<TrialSpawnerConfig> ominous) {
            return new TrialSpawner.FullConfig(
                normal,
                ominous,
                this.targetCooldownLength,
                this.requiredPlayerRange
            );
        }
        // Paper end - trial spawner API - withers
    }

    public interface StateAccessor {
        void setState(Level level, TrialSpawnerState state);

        TrialSpawnerState getState();

        void markUpdated();
    }
}
