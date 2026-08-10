package net.minecraft.world.level.levelgen;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.material.FluidState;

public class PhantomSpawner implements CustomSpawner {
    //private int nextTick; // Folia - region threading

    @Override
    public void tick(ServerLevel level, boolean spawnEnemies) {
        if (spawnEnemies) {
            if (level.getGameRules().get(GameRules.SPAWN_PHANTOMS)) {
                // Paper start - Ability to control player's insomnia and phantoms
                if (level.paperConfig().entities.behavior.phantomsSpawnAttemptMaxSeconds <= 0) {
                    return;
                }
                // Paper end - Ability to control player's insomnia and phantoms
                RandomSource randomSource = level.random;
                io.papermc.paper.threadedregions.RegionizedWorldData worldData = level.getCurrentWorldData(); // Folia - region threading
                worldData.phantomSpawnerNextTick--; // Folia - region threading
                if (worldData.phantomSpawnerNextTick <= 0) { // Folia - region threading
                    // Paper start - Ability to control player's insomnia and phantoms
                    int spawnAttemptMinSeconds = level.paperConfig().entities.behavior.phantomsSpawnAttemptMinSeconds;
                    int spawnAttemptMaxSeconds = level.paperConfig().entities.behavior.phantomsSpawnAttemptMaxSeconds;
                    worldData.phantomSpawnerNextTick += (spawnAttemptMinSeconds + randomSource.nextInt(spawnAttemptMaxSeconds - spawnAttemptMinSeconds + 1)) * 20; // Folia - region threading
                    // Paper end - Ability to control player's insomnia and phantoms
                    if (level.getSkyDarken() >= 5 || !level.dimensionType().hasSkyLight()) {
                        for (ServerPlayer serverPlayer : level.getLocalPlayers()) { // Folia - region threading
                            if (!serverPlayer.isSpectator() && (!level.paperConfig().entities.behavior.phantomsDoNotSpawnOnCreativePlayers || !serverPlayer.isCreative())) { // Paper - Add phantom creative and insomniac controls
                                BlockPos blockPos = serverPlayer.blockPosition();
                                if (!level.dimensionType().hasSkyLight() || blockPos.getY() >= level.getSeaLevel() && level.canSeeSky(blockPos)) {
                                    DifficultyInstance currentDifficultyAt = level.getCurrentDifficultyAt(blockPos);
                                    if (currentDifficultyAt.isHarderThan(randomSource.nextFloat() * 3.0F)) {
                                        ServerStatsCounter stats = serverPlayer.getStats();
                                        int i = Mth.clamp(stats.getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST)), 1, Integer.MAX_VALUE);
                                        int i1 = 24000;
                                        // Leaves start - fakeplayer spawn
                                        if (serverPlayer instanceof org.leavesmc.leaves.bot.ServerBot bot && bot.getConfigValue(org.leavesmc.leaves.bot.agent.Configs.SPAWN_PHANTOM)) {
                                            i1 = Math.max(bot.notSleepTicks, 1);
                                        }
                                        // Leaves end - fakeplayer spawn
                                        if (randomSource.nextInt(i) >= 72000) {
                                            BlockPos blockPos1 = blockPos.above(20 + randomSource.nextInt(15))
                                                .east(-10 + randomSource.nextInt(21))
                                                .south(-10 + randomSource.nextInt(21));
                                            BlockState blockState = level.getBlockState(blockPos1);
                                            FluidState fluidState = level.getFluidState(blockPos1);
                                            if (NaturalSpawner.isValidEmptySpawnBlock(level, blockPos1, blockState, fluidState, EntityType.PHANTOM)) {
                                                SpawnGroupData spawnGroupData = null;
                                                int i2 = 1 + randomSource.nextInt(currentDifficultyAt.getDifficulty().getId() + 1);

                                                for (int i3 = 0; i3 < i2; i3++) {
                                                    // Paper start - PhantomPreSpawnEvent
                                                    com.destroystokyo.paper.event.entity.PhantomPreSpawnEvent event = new com.destroystokyo.paper.event.entity.PhantomPreSpawnEvent(org.bukkit.craftbukkit.util.CraftLocation.toBukkit(blockPos1, level), serverPlayer.getBukkitEntity(), org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL);
                                                    if (!event.callEvent()) {
                                                        if (event.shouldAbortSpawn()) {
                                                            break;
                                                        }
                                                        continue;
                                                    }
                                                    // Paper end - PhantomPreSpawnEvent
                                                    Phantom phantom = EntityType.PHANTOM.create(level, EntitySpawnReason.NATURAL);
                                                    if (phantom != null) {
                                                        phantom.spawningEntity = serverPlayer.getUUID(); // Paper - PhantomPreSpawnEvent
                                                        phantom.snapTo(blockPos1, 0.0F, 0.0F);
                                                        spawnGroupData = phantom.finalizeSpawn(
                                                            level, currentDifficultyAt, EntitySpawnReason.NATURAL, spawnGroupData
                                                        );
                                                        level.addFreshEntityWithPassengers(phantom, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL); // CraftBukkit
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
    }
}
