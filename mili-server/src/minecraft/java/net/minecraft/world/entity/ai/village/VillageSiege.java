package net.minecraft.world.entity.ai.village;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class VillageSiege implements CustomSpawner {
    private static final Logger LOGGER = LogUtils.getLogger();
    // Folia - region threading

    @Override
    public void tick(ServerLevel level, boolean spawnEnemies) {
        io.papermc.paper.threadedregions.RegionizedWorldData worldData = level.getCurrentWorldData(); // Folia - region threading
        // Folia start - region threading
        // check if the spawn pos is no longer owned by this region
        if (worldData.villageSiegeState.siegeState != VillageSiege.State.SIEGE_DONE
                && !ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(level, worldData.villageSiegeState.spawnX >> 4, worldData.villageSiegeState.spawnZ >> 4, 8)) {
            // can't spawn here, just re-set
            worldData.villageSiegeState = new io.papermc.paper.threadedregions.RegionizedWorldData.VillageSiegeState();
        }
        // Folia end - region threading
        if (!level.isBrightOutside() && spawnEnemies) {
            long l = level.getDayTime() % 24000L;
            if (l == 18000L) {
                worldData.villageSiegeState.siegeState = level.random.nextInt(10) == 0 ? VillageSiege.State.SIEGE_TONIGHT : VillageSiege.State.SIEGE_DONE; // Folia - region threading
            }

            if (worldData.villageSiegeState.siegeState != VillageSiege.State.SIEGE_DONE) { // Folia - region threading
                if (!worldData.villageSiegeState.hasSetupSiege) { // Folia - region threading
                    if (!this.tryToSetupSiege(level)) {
                        return;
                    }

                    worldData.villageSiegeState.hasSetupSiege = true; // Folia - region threading
                }

                if (worldData.villageSiegeState.nextSpawnTime > 0) { // Folia - region threading
                    worldData.villageSiegeState.nextSpawnTime--; // Folia - region threading
                } else {
                    worldData.villageSiegeState.nextSpawnTime = 2; // Folia - region threading
                    if (worldData.villageSiegeState.zombiesToSpawn > 0) { // Folia - region threading
                        this.trySpawn(level);
                        worldData.villageSiegeState.zombiesToSpawn--; // Folia - region threading
                    } else {
                        worldData.villageSiegeState.siegeState = VillageSiege.State.SIEGE_DONE; // Folia - region threading
                    }
                }
            }
        } else {
            worldData.villageSiegeState.siegeState = VillageSiege.State.SIEGE_DONE; // Folia - region threading
            worldData.villageSiegeState.hasSetupSiege = false; // Folia - region threading
        }
    }

    private boolean tryToSetupSiege(ServerLevel level) {
        io.papermc.paper.threadedregions.RegionizedWorldData worldData = level.getCurrentWorldData(); // Folia - region threading
        for (Player player : level.getLocalPlayers()) { // Folia - region threading
            if (!player.isSpectator()) {
                BlockPos blockPos = player.blockPosition();
                if (level.isVillage(blockPos) && !level.getBiome(blockPos).is(BiomeTags.WITHOUT_ZOMBIE_SIEGES)) {
                    for (int i = 0; i < 10; i++) {
                        float f = level.random.nextFloat() * (float) (Math.PI * 2);
                        worldData.villageSiegeState.spawnX = blockPos.getX() + Mth.floor(Mth.cos(f) * 32.0F); // Folia - region threading
                        worldData.villageSiegeState.spawnY = blockPos.getY(); // Folia - region threading
                        worldData.villageSiegeState.spawnZ = blockPos.getZ() + Mth.floor(Mth.sin(f) * 32.0F); // Folia - region threading
                        if (this.findRandomSpawnPos(level, new BlockPos(worldData.villageSiegeState.spawnX, worldData.villageSiegeState.spawnY, worldData.villageSiegeState.spawnZ)) != null) { // Folia - region threading
                            worldData.villageSiegeState.nextSpawnTime = 0; // Folia - region threading
                            worldData.villageSiegeState.zombiesToSpawn = 20; // Folia - region threading
                            break;
                        }
                    }

                    return true;
                }
            }
        }

        return false;
    }

    private void trySpawn(ServerLevel level) {
        io.papermc.paper.threadedregions.RegionizedWorldData worldData = level.getCurrentWorldData(); // Folia - region threading
        Vec3 vec3 = this.findRandomSpawnPos(level, new BlockPos(worldData.villageSiegeState.spawnX, worldData.villageSiegeState.spawnY, worldData.villageSiegeState.spawnZ)); // Folia - region threading
        if (vec3 != null) {
            Zombie zombie;
            try {
                zombie = new Zombie(level);
                zombie.snapTo(vec3.x, vec3.y, vec3.z, level.random.nextFloat() * 360.0F, 0.0F); // Folia - region threading - move up
                zombie.finalizeSpawn(level, level.getCurrentDifficultyAt(zombie.blockPosition()), EntitySpawnReason.EVENT, null);
            } catch (Exception var5) {
                LOGGER.warn("Failed to create zombie for village siege at {}", vec3, var5);
                com.destroystokyo.paper.exception.ServerInternalException.reportInternalException(var5); // Paper - ServerExceptionEvent
                return;
            }

            //zombie.snapTo(vec3.x, vec3.y, vec3.z, level.random.nextFloat() * 360.0F, 0.0F); // Folia - region threading - move up
            level.addFreshEntityWithPassengers(zombie, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.VILLAGE_INVASION); // CraftBukkit
        }
    }

    private @Nullable Vec3 findRandomSpawnPos(ServerLevel level, BlockPos pos) {
        for (int i = 0; i < 10; i++) {
            int i1 = pos.getX() + level.random.nextInt(16) - 8;
            int i2 = pos.getZ() + level.random.nextInt(16) - 8;
            int height = level.getHeight(Heightmap.Types.WORLD_SURFACE, i1, i2);
            BlockPos blockPos = new BlockPos(i1, height, i2);
            if (level.isVillage(blockPos) && Monster.checkMonsterSpawnRules(EntityType.ZOMBIE, level, EntitySpawnReason.EVENT, blockPos, level.random)) {
                return Vec3.atBottomCenterOf(blockPos);
            }
        }

        return null;
    }

    public static enum State { // Folia - region threading
        SIEGE_CAN_ACTIVATE,
        SIEGE_TONIGHT,
        SIEGE_DONE;
    }
}
