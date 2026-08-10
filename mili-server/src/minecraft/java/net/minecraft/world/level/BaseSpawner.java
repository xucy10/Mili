package net.minecraft.world.level;

import com.mojang.logging.LogUtils;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public abstract class BaseSpawner {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String SPAWN_DATA_TAG = "SpawnData";
    private static final int EVENT_SPAWN = 1;
    private static final int DEFAULT_SPAWN_DELAY = 20;
    private static final int DEFAULT_MIN_SPAWN_DELAY = 200;
    private static final int DEFAULT_MAX_SPAWN_DELAY = 800;
    private static final int DEFAULT_SPAWN_COUNT = 4;
    private static final int DEFAULT_MAX_NEARBY_ENTITIES = 6;
    private static final int DEFAULT_REQUIRED_PLAYER_RANGE = 16;
    private static final int DEFAULT_SPAWN_RANGE = 4;
    public int spawnDelay = 20;
    public WeightedList<SpawnData> spawnPotentials = WeightedList.of();
    public @Nullable SpawnData nextSpawnData;
    private double spin;
    private double oSpin;
    public int minSpawnDelay = 200;
    public int maxSpawnDelay = 800;
    public int spawnCount = 4;
    private @Nullable Entity displayEntity;
    public int maxNearbyEntities = 6;
    public int requiredPlayerRange = 16;
    public int spawnRange = 4;
    private int tickDelay = 0; // Paper - Configurable mob spawner tick rate

    public void setEntityId(EntityType<?> type, @Nullable Level level, RandomSource random, BlockPos pos) {
        this.getOrCreateNextSpawnData(level, random, pos).getEntityToSpawn().putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
        this.spawnPotentials = WeightedList.of(); // CraftBukkit - SPIGOT-3496, MC-92282
    }

    public boolean isNearPlayer(Level level, BlockPos pos) {
        return level.hasNearbyAlivePlayerThatAffectsSpawningForSpawner(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, this.requiredPlayerRange); // Paper - Affects Spawning API // Leaf - Optimize nearby alive players for spawning
    }

    public void clientTick(Level level, BlockPos pos) {
        if (!this.isNearPlayer(level, pos)) {
            this.oSpin = this.spin;
        } else if (this.displayEntity != null) {
            RandomSource random = level.getRandom();
            double d = pos.getX() + random.nextDouble();
            double d1 = pos.getY() + random.nextDouble();
            double d2 = pos.getZ() + random.nextDouble();
            level.addParticle(ParticleTypes.SMOKE, d, d1, d2, 0.0, 0.0, 0.0);
            level.addParticle(ParticleTypes.FLAME, d, d1, d2, 0.0, 0.0, 0.0);
            if (this.spawnDelay > 0) {
                this.spawnDelay--;
            }

            this.oSpin = this.spin;
            this.spin = (this.spin + 1000.0F / (this.spawnDelay + 200.0F)) % 360.0;
        }
    }

    public void serverTick(ServerLevel level, BlockPos pos) {
        if (spawnCount <= 0 || maxNearbyEntities <= 0) return; // Paper - Ignore impossible spawn tick
        // Paper start - Configurable mob spawner tick rate
        if (spawnDelay > 0 && --tickDelay > 0) return;
        tickDelay = level.paperConfig().tickRates.mobSpawner;
        if (tickDelay == -1) { return; } // If disabled
        // Paper end - Configurable mob spawner tick rate
        if (this.isNearPlayer(level, pos) && level.isSpawnerBlockEnabled()) {
            if (this.spawnDelay < -tickDelay) { // Paper - Configurable mob spawner tick rate
                this.delay(level, pos);
            }

            if (this.spawnDelay > 0) {
                this.spawnDelay -= tickDelay; // Paper - Configurable mob spawner tick rate
            } else {
                boolean flag = false;
                RandomSource random = level.getRandom();
                SpawnData nextSpawnData = this.getOrCreateNextSpawnData(level, random, pos);

                for (int i = 0; i < this.spawnCount; i++) {
                    try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(this::toString, LOGGER)) {
                        ValueInput valueInput = TagValueInput.create(scopedCollector, level.registryAccess(), nextSpawnData.getEntityToSpawn());
                        Optional<EntityType<?>> optional = EntityType.by(valueInput);
                        if (optional.isEmpty()) {
                            this.delay(level, pos);
                            return;
                        }

                        Vec3 vec3 = valueInput.read("Pos", Vec3.CODEC)
                            .orElseGet(
                                () -> new Vec3(
                                    pos.getX() + (random.nextDouble() - random.nextDouble()) * this.spawnRange + 0.5,
                                    pos.getY() + random.nextInt(3) - 1,
                                    pos.getZ() + (random.nextDouble() - random.nextDouble()) * this.spawnRange + 0.5
                                )
                            );
                        if (level.noCollision(optional.get().getSpawnAABB(vec3.x, vec3.y, vec3.z))) {
                            BlockPos blockPos = BlockPos.containing(vec3);
                            if (nextSpawnData.getCustomSpawnRules().isPresent()) {
                                if (!optional.get().getCategory().isFriendly() && level.getDifficulty() == Difficulty.PEACEFUL) {
                                    continue;
                                }

                                SpawnData.CustomSpawnRules customSpawnRules = nextSpawnData.getCustomSpawnRules().get();
                                if (!customSpawnRules.isValidPosition(blockPos, level)) {
                                    continue;
                                }
                            } else if (!SpawnPlacements.checkSpawnRules(optional.get(), level, EntitySpawnReason.SPAWNER, blockPos, level.getRandom())) {
                                continue;
                            }

                            // Paper start - PreCreatureSpawnEvent
                            com.destroystokyo.paper.event.entity.PreSpawnerSpawnEvent event = new com.destroystokyo.paper.event.entity.PreSpawnerSpawnEvent(
                                org.bukkit.craftbukkit.util.CraftLocation.toBukkit(vec3, level),
                                org.bukkit.craftbukkit.entity.CraftEntityType.minecraftToBukkit(optional.get()),
                                org.bukkit.craftbukkit.util.CraftLocation.toBukkit(pos, level)
                            );
                            if (!event.callEvent()) {
                                flag = true;
                                if (event.shouldAbortSpawn()) {
                                    break;
                                }
                                continue;
                            }
                            // Paper end - PreCreatureSpawnEvent

                            Entity entity = EntityType.loadEntityRecursive(valueInput, level, EntitySpawnReason.SPAWNER, entity1 -> {
                                entity1.snapTo(vec3.x, vec3.y, vec3.z, entity1.getYRot(), entity1.getXRot());
                                return entity1;
                            });
                            if (entity == null) {
                                this.delay(level, pos);
                                return;
                            }

                            int size = level.getEntities(
                                    EntityTypeTest.forExactClass(entity.getClass()),
                                    new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1).inflate(this.spawnRange),
                                    EntitySelector.NO_SPECTATORS
                                )
                                .size();
                            if (size >= this.maxNearbyEntities) {
                                this.delay(level, pos);
                                return;
                            }

                            entity.snapTo(entity.getX(), entity.getY(), entity.getZ(), random.nextFloat() * 360.0F, 0.0F);
                            if (entity instanceof Mob mob) {
                                if (nextSpawnData.getCustomSpawnRules().isEmpty() && !mob.checkSpawnRules(level, EntitySpawnReason.SPAWNER)
                                    || !mob.checkSpawnObstruction(level)) {
                                    continue;
                                }

                                boolean flag1 = nextSpawnData.getEntityToSpawn().size() == 1 && nextSpawnData.getEntityToSpawn().getString("id").isPresent();
                                if (flag1) {
                                    ((Mob)entity).finalizeSpawn(level, level.getCurrentDifficultyAt(entity.blockPosition()), EntitySpawnReason.SPAWNER, null);
                                }

                                nextSpawnData.getEquipment().ifPresent(mob::equip);
                                // Spigot start
                                if (mob.level().spigotConfig.nerfSpawnerMobs) {
                                    mob.aware = false;
                                }
                                // Spigot end
                            }

                            // Paper start
                            entity.spawnedViaMobSpawner = true;
                            entity.spawnReason = org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SPAWNER;
                            flag = true;
                            if (org.bukkit.craftbukkit.event.CraftEventFactory.callSpawnerSpawnEvent(entity, pos).isCancelled()) {
                                continue;
                            }
                            if (!level.tryAddFreshEntityWithPassengers(entity, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SPAWNER)) {
                                // Paper end
                                this.delay(level, pos);
                                return;
                            }

                            level.levelEvent(LevelEvent.PARTICLES_MOBBLOCK_SPAWN, pos, 0);
                            level.gameEvent(entity, GameEvent.ENTITY_PLACE, blockPos);
                            if (entity instanceof Mob) {
                                ((Mob)entity).spawnAnim();
                            }

                            //flag = true; // Paper - moved up above cancellable event
                        }
                    }
                }

                if (flag) {
                    this.delay(level, pos);
                }

                return;
            }
        }
    }

    public void delay(Level level, BlockPos pos) {
        RandomSource randomSource = level.random;
        if (this.maxSpawnDelay <= this.minSpawnDelay) {
            this.spawnDelay = this.minSpawnDelay;
        } else {
            this.spawnDelay = this.minSpawnDelay + randomSource.nextInt(this.maxSpawnDelay - this.minSpawnDelay);
        }

        this.spawnPotentials.getRandom(randomSource).ifPresent(spawnData -> this.setNextSpawnData(level, pos, spawnData));
        this.broadcastEvent(level, pos, EVENT_SPAWN);
    }

    public void load(@Nullable Level level, BlockPos pos, ValueInput input) {
        this.spawnDelay = input.getIntOr("Paper.Delay", input.getShortOr("Delay", (short) 20)); // Paper - use int if set
        input.read("SpawnData", SpawnData.CODEC).ifPresent(spawnData -> this.setNextSpawnData(level, pos, spawnData));
        this.spawnPotentials = input.read("SpawnPotentials", SpawnData.LIST_CODEC)
            .orElseGet(() -> WeightedList.of(this.nextSpawnData != null ? this.nextSpawnData : new SpawnData()));
        // Paper start - use int if set
        this.minSpawnDelay = input.getIntOr("Paper.MinSpawnDelay", input.getIntOr("MinSpawnDelay", 200));
        this.maxSpawnDelay = input.getIntOr("Paper.MaxSpawnDelay", input.getIntOr("MaxSpawnDelay", 800));
        // Paper end - use int if set
        this.spawnCount = input.getIntOr("SpawnCount", 4);
        this.maxNearbyEntities = input.getIntOr("MaxNearbyEntities", 6);
        this.requiredPlayerRange = input.getIntOr("RequiredPlayerRange", 16);
        this.spawnRange = input.getIntOr("SpawnRange", 4);
        this.displayEntity = null;
    }

    public void save(ValueOutput output) {
        // Paper start
        if (this.spawnDelay > Short.MAX_VALUE) {
            output.putInt("Paper.Delay", this.spawnDelay);
        }
        output.putShort("Delay", (short) Math.min(Short.MAX_VALUE, this.spawnDelay));

        if (this.minSpawnDelay > Short.MAX_VALUE || this.maxSpawnDelay > Short.MAX_VALUE) {
            output.putInt("Paper.MinSpawnDelay", this.minSpawnDelay);
            output.putInt("Paper.MaxSpawnDelay", this.maxSpawnDelay);
        }
        output.putShort("MinSpawnDelay", (short) Math.min(Short.MAX_VALUE, this.minSpawnDelay));
        output.putShort("MaxSpawnDelay", (short) Math.min(Short.MAX_VALUE, this.maxSpawnDelay));
        // Paper end
        output.putShort("SpawnCount", (short)this.spawnCount);
        output.putShort("MaxNearbyEntities", (short)this.maxNearbyEntities);
        output.putShort("RequiredPlayerRange", (short)this.requiredPlayerRange);
        output.putShort("SpawnRange", (short)this.spawnRange);
        output.storeNullable("SpawnData", SpawnData.CODEC, this.nextSpawnData);
        output.store("SpawnPotentials", SpawnData.LIST_CODEC, this.spawnPotentials);
    }

    public @Nullable Entity getOrCreateDisplayEntity(Level level, BlockPos pos) {
        if (this.displayEntity == null) {
            CompoundTag entityToSpawn = this.getOrCreateNextSpawnData(level, level.getRandom(), pos).getEntityToSpawn();
            if (entityToSpawn.getString("id").isEmpty()) {
                return null;
            }

            this.displayEntity = EntityType.loadEntityRecursive(entityToSpawn, level, EntitySpawnReason.SPAWNER, EntityProcessor.NOP);
            if (entityToSpawn.size() == 1 && this.displayEntity instanceof Mob) {
            }
        }

        return this.displayEntity;
    }

    public boolean onEventTriggered(Level level, int id) {
        if (id == EVENT_SPAWN) {
            if (level.isClientSide()) {
                this.spawnDelay = this.minSpawnDelay;
            }

            return true;
        } else {
            return false;
        }
    }

    public void setNextSpawnData(@Nullable Level level, BlockPos pos, SpawnData nextSpawnData) {
        this.nextSpawnData = nextSpawnData;
    }

    private SpawnData getOrCreateNextSpawnData(@Nullable Level level, RandomSource random, BlockPos pos) {
        if (this.nextSpawnData != null) {
            return this.nextSpawnData;
        } else {
            this.setNextSpawnData(level, pos, this.spawnPotentials.getRandom(random).orElseGet(SpawnData::new));
            return this.nextSpawnData;
        }
    }

    public abstract void broadcastEvent(Level level, BlockPos pos, int eventId);

    public double getSpin() {
        return this.spin;
    }

    public double getOSpin() {
        return this.oSpin;
    }
}
