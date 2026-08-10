package net.minecraft.world.entity.npc.wanderingtrader;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.equine.TraderLlama;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ServerLevelData;
import org.jspecify.annotations.Nullable;

public class WanderingTraderSpawner implements CustomSpawner {
    private static final int DEFAULT_TICK_DELAY = 1200;
    public static final int DEFAULT_SPAWN_DELAY = 24000;
    private static final int MIN_SPAWN_CHANCE = 25;
    private static final int MAX_SPAWN_CHANCE = 75;
    private static final int SPAWN_CHANCE_INCREASE = 25;
    private static final int SPAWN_ONE_IN_X_CHANCE = 10;
    private static final int NUMBER_OF_SPAWN_ATTEMPTS = 10;
    private final RandomSource random = io.papermc.paper.threadedregions.util.ThreadLocalRandomSource.INSTANCE; // Folia - region threading
    private final ServerLevelData serverLevelData;
    // Folia - region threading

    public WanderingTraderSpawner(ServerLevelData serverLevelData) {
        this.serverLevelData = serverLevelData;
        // Paper start - Add Wandering Trader spawn rate config options
        //this.tickDelay = Integer.MIN_VALUE; // Folia - region threading - moved to regionisedworlddata
        // this.spawnDelay = serverLevelData.getWanderingTraderSpawnDelay();
        // this.spawnChance = serverLevelData.getWanderingTraderSpawnChance();
        // if (this.spawnDelay == 0 && this.spawnChance == 0) {
        //     this.spawnDelay = 24000;
        //     serverLevelData.setWanderingTraderSpawnDelay(this.spawnDelay);
        //     this.spawnChance = 25;
        //     serverLevelData.setWanderingTraderSpawnChance(this.spawnChance);
        // }
        // Paper end - Add Wandering Trader spawn rate config options
    }

    @Override
    public void tick(ServerLevel level, boolean spawnEnemies) {
        io.papermc.paper.threadedregions.RegionizedWorldData worldData = level.getCurrentWorldData(); // Folia - region threading
        // Paper start - Add Wandering Trader spawn rate config options
        if (worldData.wanderingTraderTickDelay == Integer.MIN_VALUE) { // Folia - region threading
            worldData.wanderingTraderTickDelay = level.paperConfig().entities.spawning.wanderingTrader.spawnMinuteLength; // Folia - region threading
            worldData.wanderingTraderSpawnDelay = level.paperConfig().entities.spawning.wanderingTrader.spawnDayLength; // Folia - region threading
            worldData.wanderingTraderSpawnChance = level.paperConfig().entities.spawning.wanderingTrader.spawnChanceMin; // Folia - region threading
        }
        if (level.getGameRules().get(GameRules.SPAWN_WANDERING_TRADERS)) {
            if (worldData.wanderingTraderTickDelay - 1 <= 0) { // Paper - Prevent tickDelay going below 0 // Folia - region threading
                worldData.wanderingTraderTickDelay = level.paperConfig().entities.spawning.wanderingTrader.spawnMinuteLength; // Folia - region threading
                worldData.wanderingTraderSpawnDelay = worldData.wanderingTraderSpawnDelay - level.paperConfig().entities.spawning.wanderingTrader.spawnMinuteLength; // Folia - region threading
                //this.serverLevelData.setWanderingTraderSpawnDelay(this.spawnDelay); // Paper - We don't need to save this value to disk if it gets set back to a hardcoded value anyways
                if (worldData.wanderingTraderSpawnDelay <= 0) { // Folia - region threading
                    worldData.wanderingTraderSpawnDelay = level.paperConfig().entities.spawning.wanderingTrader.spawnDayLength; // Folia - region threading
                    int i = worldData.wanderingTraderSpawnChance; // Folia - region threading
                    worldData.wanderingTraderSpawnChance = Mth.clamp(worldData.wanderingTraderSpawnChance + level.paperConfig().entities.spawning.wanderingTrader.spawnChanceFailureIncrement, level.paperConfig().entities.spawning.wanderingTrader.spawnChanceMin, level.paperConfig().entities.spawning.wanderingTrader.spawnChanceMax); // Folia - region threading
                    //this.serverLevelData.setWanderingTraderSpawnChance(this.spawnChance); // Paper - We don't need to save this value to disk if it gets set back to a hardcoded value anyways
                    if (this.random.nextInt(100) <= i) {
                        if (this.spawn(level)) {
                            worldData.wanderingTraderSpawnChance = level.paperConfig().entities.spawning.wanderingTrader.spawnChanceMin; // Folia - region threading
                            // Paper end - Add Wandering Trader spawn rate config options
                        }
                    }
                }
            } else { worldData.wanderingTraderTickDelay--; } // Paper - Prevent tickDelay going below 0
        }
    }

    private boolean spawn(ServerLevel level) {
        Player randomPlayer = level.getRandomLocalPlayer(); // Folia - region threading
        if (randomPlayer == null) {
            return true;
        } else if (this.random.nextInt(10) != 0) {
            return false;
        } else {
            BlockPos blockPos = randomPlayer.blockPosition();
            int i = 48;
            PoiManager poiManager = level.getPoiManager();
            Optional<BlockPos> optional = poiManager.find(holder -> holder.is(PoiTypes.MEETING), blockPos3 -> true, blockPos, 48, PoiManager.Occupancy.ANY);
            BlockPos blockPos1 = optional.orElse(blockPos);
            BlockPos blockPos2 = this.findSpawnPositionNear(level, blockPos1, 48);
            if (blockPos2 != null && this.hasEnoughSpace(level, blockPos2)) {
                if (level.getBiome(blockPos2).is(BiomeTags.WITHOUT_WANDERING_TRADER_SPAWNS)) {
                    return false;
                }

                WanderingTrader wanderingTrader = EntityType.WANDERING_TRADER.spawn(level, trader -> trader.setDespawnDelay(48000), blockPos2, EntitySpawnReason.EVENT, false, false, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL); // CraftBukkit // Paper - set despawnTimer before spawn events called
                if (wanderingTrader != null) {
                    for (int i1 = 0; i1 < 2; i1++) {
                        this.tryToSpawnLlamaFor(level, wanderingTrader, 4);
                    }

                    //this.serverLevelData.setWanderingTraderId(wanderingTrader.getUUID()); // Folia - region threading - doesn't appear to be used anywhere, so avoid the race condition here...
                    // wanderingTrader.setDespawnDelay(48000); // Paper - moved above, modifiable by plugins on CreatureSpawnEvent
                    wanderingTrader.setWanderTarget(blockPos1);
                    wanderingTrader.setHomeTo(blockPos1, 16);
                    return true;
                }
            }

            return false;
        }
    }

    private void tryToSpawnLlamaFor(ServerLevel level, WanderingTrader trader, int maxDistance) {
        BlockPos blockPos = this.findSpawnPositionNear(level, trader.blockPosition(), maxDistance);
        if (blockPos != null) {
            TraderLlama traderLlama = EntityType.TRADER_LLAMA.spawn(level, blockPos, EntitySpawnReason.EVENT, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL); // CraftBukkit
            if (traderLlama != null) {
                traderLlama.setLeashedTo(trader, true);
            }
        }
    }

    private @Nullable BlockPos findSpawnPositionNear(LevelReader level, BlockPos pos, int maxDistance) {
        BlockPos blockPos = null;
        SpawnPlacementType placementType = SpawnPlacements.getPlacementType(EntityType.WANDERING_TRADER);

        for (int i = 0; i < 10; i++) {
            int i1 = pos.getX() + this.random.nextInt(maxDistance * 2) - maxDistance;
            int i2 = pos.getZ() + this.random.nextInt(maxDistance * 2) - maxDistance;
            int height = level.getHeight(Heightmap.Types.WORLD_SURFACE, i1, i2);
            BlockPos blockPos1 = new BlockPos(i1, height, i2);
            if (placementType.isSpawnPositionOk(level, blockPos1, EntityType.WANDERING_TRADER)) {
                blockPos = blockPos1;
                break;
            }
        }

        return blockPos;
    }

    private boolean hasEnoughSpace(BlockGetter level, BlockPos pos) {
        for (BlockPos blockPos : BlockPos.betweenClosed(pos, pos.offset(1, 2, 1))) {
            if (!level.getBlockState(blockPos).getCollisionShape(level, blockPos).isEmpty()) {
                return false;
            }
        }

        return true;
    }
}
