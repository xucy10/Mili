package net.minecraft.world.level.levelgen;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.PatrollingMonster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;

public class PatrolSpawner implements CustomSpawner {
    //private int nextTick; // Folia - region threading

    @Override
    public void tick(ServerLevel level, boolean spawnEnemies) {
        if (level.paperConfig().entities.behavior.pillagerPatrols.disable || level.paperConfig().entities.behavior.pillagerPatrols.spawnChance == 0) return; // Paper - Add option to disable pillager patrols & Pillager patrol spawn settings and per player options
        if (spawnEnemies) {
            if (level.getGameRules().get(GameRules.SPAWN_PATROLS)) {
                RandomSource randomSource = level.random;
                io.papermc.paper.threadedregions.RegionizedWorldData worldData = level.getCurrentWorldData(); // Folia - region threading
                // this.nextTick--;
                // Paper start - Pillager patrol spawn settings and per player options
                int size = level.getLocalPlayers().size(); // Folia - region threading
                if (size < 1) {
                    return;
                }

                net.minecraft.server.level.ServerPlayer player = level.getLocalPlayers().get(randomSource.nextInt(size)); // Folia - region threading
                if (player.isSpectator()) {
                    return;
                }

                int patrolSpawnDelay;
                if (level.paperConfig().entities.behavior.pillagerPatrols.spawnDelay.perPlayer) {
                    --player.patrolSpawnDelay;
                    patrolSpawnDelay = player.patrolSpawnDelay;
                } else {
                    worldData.patrolSpawnerNextTick--; // Folia - region threading
                    patrolSpawnDelay = worldData.patrolSpawnerNextTick; // Folia - region threading
                }
                if (patrolSpawnDelay <= 0) {
                    long dayCount;
                    if (level.paperConfig().entities.behavior.pillagerPatrols.start.perPlayer) {
                        dayCount = player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.PLAY_TIME)) / 24000L; // PLAY_TIME is counting in ticks
                    } else {
                        dayCount = level.getDayCount();
                    }
                    if (level.paperConfig().entities.behavior.pillagerPatrols.spawnDelay.perPlayer) {
                        player.patrolSpawnDelay += level.paperConfig().entities.behavior.pillagerPatrols.spawnDelay.ticks + randomSource.nextInt(1200);
                    } else {
                        worldData.patrolSpawnerNextTick += level.paperConfig().entities.behavior.pillagerPatrols.spawnDelay.ticks + randomSource.nextInt(1200); // Folia - region threading
                    }

                    if (dayCount >= level.paperConfig().entities.behavior.pillagerPatrols.start.day && level.isBrightOutside()) {
                        if (randomSource.nextDouble() < level.paperConfig().entities.behavior.pillagerPatrols.spawnChance) {
                            // Paper end - Pillager patrol spawn settings and per player options
                            if (size >= 1) {
                                if (!player.isSpectator()) {
                                    if (!level.isCloseToVillage(player.blockPosition(), 2)) {
                                        int i = (24 + randomSource.nextInt(24)) * (randomSource.nextBoolean() ? -1 : 1);
                                        int i1 = (24 + randomSource.nextInt(24)) * (randomSource.nextBoolean() ? -1 : 1);
                                        BlockPos.MutableBlockPos mutableBlockPos = player.blockPosition().mutable().move(i, 0, i1);
                                        int i2 = 10;
                                        if (level.hasChunksAt(
                                            mutableBlockPos.getX() - 10, mutableBlockPos.getZ() - 10, mutableBlockPos.getX() + 10, mutableBlockPos.getZ() + 10
                                        )) {
                                            if (level.environmentAttributes().getValue(EnvironmentAttributes.CAN_PILLAGER_PATROL_SPAWN, mutableBlockPos)) {
                                                int i3 = (int)Math.ceil(level.getCurrentDifficultyAt(mutableBlockPos).getEffectiveDifficulty()) + 1;

                                                for (int i4 = 0; i4 < i3; i4++) {
                                                    mutableBlockPos.setY(
                                                        level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, mutableBlockPos).getY()
                                                    );
                                                    if (i4 == 0) {
                                                        if (!this.spawnPatrolMember(level, mutableBlockPos, randomSource, true)) {
                                                            break;
                                                        }
                                                    } else {
                                                        this.spawnPatrolMember(level, mutableBlockPos, randomSource, false);
                                                    }

                                                    mutableBlockPos.setX(mutableBlockPos.getX() + randomSource.nextInt(5) - randomSource.nextInt(5));
                                                    mutableBlockPos.setZ(mutableBlockPos.getZ() + randomSource.nextInt(5) - randomSource.nextInt(5));
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean spawnPatrolMember(ServerLevel level, BlockPos pos, RandomSource random, boolean leader) {
        BlockState blockState = level.getBlockState(pos);
        if (!NaturalSpawner.isValidEmptySpawnBlock(level, pos, blockState, blockState.getFluidState(), EntityType.PILLAGER)) {
            return false;
        } else if (!PatrollingMonster.checkPatrollingMonsterSpawnRules(EntityType.PILLAGER, level, EntitySpawnReason.PATROL, pos, random)) {
            return false;
        } else {
            PatrollingMonster patrollingMonster = EntityType.PILLAGER.create(level, EntitySpawnReason.PATROL);
            if (patrollingMonster != null) {
                if (leader) {
                    patrollingMonster.setPatrolLeader(true);
                    patrollingMonster.findPatrolTarget();
                }

                patrollingMonster.setPos(pos.getX(), pos.getY(), pos.getZ());
                patrollingMonster.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.PATROL, null);
                level.addFreshEntityWithPassengers(patrollingMonster, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.PATROL); // CraftBukkit
                return true;
            } else {
                return false;
            }
        }
    }
}
