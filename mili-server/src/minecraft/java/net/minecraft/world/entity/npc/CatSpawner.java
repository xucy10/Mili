package net.minecraft.world.entity.npc;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.phys.AABB;

public class CatSpawner implements CustomSpawner {
    private static final int TICK_DELAY = 1200;
    //private int nextTick; // Folia - region threading

    @Override
    public void tick(ServerLevel level, boolean spawnEnemies) {
        io.papermc.paper.threadedregions.RegionizedWorldData worldData = level.getCurrentWorldData(); // Folia - region threading
        worldData.catSpawnerNextTick--; // Folia - region threading
        if (worldData.catSpawnerNextTick <= 0) { // Folia - region threading
            worldData.catSpawnerNextTick = 1200; // Folia - region threading
            Player randomPlayer = level.getRandomLocalPlayer(); // Folia - region threading
            if (randomPlayer != null) {
                RandomSource randomSource = level.random;
                int i = (8 + randomSource.nextInt(24)) * (randomSource.nextBoolean() ? -1 : 1);
                int i1 = (8 + randomSource.nextInt(24)) * (randomSource.nextBoolean() ? -1 : 1);
                BlockPos blockPos = randomPlayer.blockPosition().offset(i, 0, i1);
                int i2 = 10;
                if (level.hasChunksAt(blockPos.getX() - 10, blockPos.getZ() - 10, blockPos.getX() + 10, blockPos.getZ() + 10)) {
                    if (SpawnPlacements.isSpawnPositionOk(EntityType.CAT, level, blockPos)) {
                        if (level.isCloseToVillage(blockPos, 2)) {
                            this.spawnInVillage(level, blockPos);
                        } else if (level.structureManager().getStructureWithPieceAt(blockPos, StructureTags.CATS_SPAWN_IN).isValid()) {
                            this.spawnInHut(level, blockPos);
                        }
                    }
                }
            }
        }
    }

    private void spawnInVillage(ServerLevel level, BlockPos pos) {
        int i = 48;
        if (level.getPoiManager().getCountInRange(holder -> holder.is(PoiTypes.HOME), pos, 48, PoiManager.Occupancy.IS_OCCUPIED) > 4L) {
            List<Cat> entitiesOfClass = level.getEntitiesOfClass(Cat.class, new AABB(pos).inflate(48.0, 8.0, 48.0));
            if (entitiesOfClass.size() < 5) {
                this.spawnCat(pos, level, false);
            }
        }
    }

    private void spawnInHut(ServerLevel level, BlockPos pos) {
        int i = 16;
        List<Cat> entitiesOfClass = level.getEntitiesOfClass(Cat.class, new AABB(pos).inflate(16.0, 8.0, 16.0));
        if (entitiesOfClass.isEmpty()) {
            this.spawnCat(pos, level, true);
        }
    }

    private void spawnCat(BlockPos pos, ServerLevel level, boolean persistent) {
        Cat cat = EntityType.CAT.create(level, EntitySpawnReason.NATURAL);
        if (cat != null) {
            cat.snapTo(pos, 0.0F, 0.0F); // Paper - move up - Fix MC-147659
            cat.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.NATURAL, null);
            if (persistent) {
                cat.setPersistenceRequired();
            }

            level.addFreshEntityWithPassengers(cat);
        }
    }
}
