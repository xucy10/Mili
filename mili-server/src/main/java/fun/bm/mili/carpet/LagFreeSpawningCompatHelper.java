package fun.bm.mili.carpet;

import ca.spottedleaf.moonrise.common.util.TickThread;
import com.mojang.logging.LogUtils;
import io.papermc.paper.threadedregions.RegionizedWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class LagFreeSpawningCompatHelper {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<RegionizedWorldData, Map<EntityType<?>, Mob>> PRECOOKED_MOBS = new ConcurrentHashMap<>();

    public static boolean hasNoCollision(ServerLevel world, AABB bb) {
        if (!TickThread.isTickThreadFor(world, bb)) return world.noCollision(bb);
        int minX = Mth.floor(bb.minX);
        int minY = Mth.floor(bb.minY);
        int minZ = Mth.floor(bb.minZ);
        int maxY = Mth.ceil(bb.maxY) - 1;
        BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();

        if (bb.getXsize() <= 1.0 && bb.getZsize() <= 1.0) {
            for (int y = minY; y <= maxY; y++) {
                blockPos.set(minX, y, minZ);
                VoxelShape shape = world.getBlockState(blockPos).getCollisionShape(world, blockPos);
                if (shape != Shapes.empty()) {
                    return shape != Shapes.block() && world.noCollision(bb);
                }
            }
            return true;
        }

        int maxX = Mth.ceil(bb.maxX) - 1;
        int maxZ = Mth.ceil(bb.maxZ) - 1;

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    blockPos.set(x, y, z);
                    VoxelShape shape = world.getBlockState(blockPos).getCollisionShape(world, blockPos);
                    if (shape != Shapes.empty()) {
                        return shape != Shapes.block() && world.noCollision(bb);
                    }
                }
            }
        }

        int minBelow = minY - 1;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                blockPos.set(x, minBelow, z);
                BlockState state = world.getBlockState(blockPos);
                Block block = state.getBlock();
                if (state.is(BlockTags.FENCES)
                        || state.is(BlockTags.WALLS)
                        || block instanceof FenceGateBlock && !state.getValue(FenceGateBlock.OPEN)) {
                    if (x == minX || x == maxX || z == minZ || z == maxZ) {
                        return world.noCollision(bb);
                    }
                    return false;
                }
            }
        }

        return true;
    }

    public static double limitDistanceSq(MobCategory category, double distanceSq) {
        return distanceSq > 16384.0 && category != MobCategory.CREATURE ? 0.0 : distanceSq;
    }

    public static @Nullable Mob getOrCreateMob(ServerLevel level, EntityType<?> entityType) {
        RegionizedWorldData data = level.getCurrentWorldData();
        if (data == null) return createMob(level, entityType, null);
        Map<EntityType<?>, Mob> cache = PRECOOKED_MOBS.computeIfAbsent(data, key -> new ConcurrentHashMap<>());
        Mob mob = cache.get(entityType);
        if (mob != null && !mob.isRemoved()) {
            if (!TickThread.isTickThreadFor(mob)) return createMob(level, entityType, data);
            return mob;
        }

        return createMob(level, entityType, data);
    }

    public static @Nullable Mob createMob(ServerLevel level, EntityType<?> entityType, @Nullable RegionizedWorldData data) {
        try {
            if (entityType.create(level, EntitySpawnReason.NATURAL) instanceof Mob created) {
                if (data != null) {
                    Map<EntityType<?>, Mob> cache = PRECOOKED_MOBS.get(data);
                    if (cache != null) {
                        cache.put(entityType, created);
                    }
                }
                return created;
            }
            LOGGER.warn("Can't precook non-mob entity type: {}", entityType);
        } catch (Exception exception) {
            LOGGER.warn("Failed to precook mob {}", entityType, exception);
        }

        return null;
    }

    public static void markSpawned(ServerLevel level, EntityType<?> entityType) {
        RegionizedWorldData data = level.getCurrentWorldData();
        if (data == null) return;
        Map<EntityType<?>, Mob> cache = PRECOOKED_MOBS.get(data);
        if (cache != null) {
            cache.remove(entityType);
        }
    }
}
