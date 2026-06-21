package fun.bm.mili.kaiiju;

import com.mojang.logging.LogUtils;
import io.papermc.paper.threadedregions.RegionizedWorldData;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MiliEntityThrottler {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<RegionizedWorldData, MiliEntityThrottler> INSTANCES = new ConcurrentHashMap<>();

    private final RegionizedWorldData regionData;
    private final Object2ObjectOpenHashMap<EntityType<?>, TickInfo> tickInfoMap = new Object2ObjectOpenHashMap<>();

    private MiliEntityThrottler(RegionizedWorldData regionData) {
        this.regionData = regionData;
    }

    public static MiliEntityThrottler getOrCreate(RegionizedWorldData regionData) {
        return INSTANCES.computeIfAbsent(regionData, MiliEntityThrottler::new);
    }

    public static void remove(RegionizedWorldData regionData) {
        INSTANCES.remove(regionData);
    }

    public void tickLimiterStart() {
        for (TickInfo info : tickInfoMap.values()) {
            info.currentTick = 0;
        }
    }

    public boolean shouldSkipTick(Entity entity) {
        if (entity == null || entity.isRemoved()) return false;
        if (!MiliEntityLimitsConfig.enabled) return false;

        EntityType<?> type = entity.getType();
        TickInfo info = tickInfoMap.computeIfAbsent(type, t -> {
            TickInfo ti = new TickInfo();
            ti.toTick = getLimit(type);
            ti.toRemove = getRemoval(type);
            return ti;
        });

        info.currentTick++;

        if (info.toRemove > 0 && info.currentTick <= info.toRemove) {
            return false;
        }
        if (info.currentTick < info.continueFrom) return true;
        if (info.currentTick - info.continueFrom < info.toTick) return false;
        return true;
    }

    public void tickLimiterFinish() {
        for (var entry : tickInfoMap.entrySet()) {
            EntityType<?> type = entry.getKey();
            TickInfo info = entry.getValue();

            int limit = getLimit(type);
            int removal = getRemoval(type);

            int additionals = 0;
            int nextContinueFrom = info.continueFrom + info.toTick;
            if (nextContinueFrom >= info.currentTick) {
                additionals = limit - (info.currentTick - info.continueFrom);
                nextContinueFrom = 0;
            }
            info.continueFrom = nextContinueFrom;
            info.toTick = limit + additionals;

            if (info.toRemove == 0 && info.currentTick > removal && removal > 0) {
                info.toRemove = info.currentTick - removal;
            } else if (info.toRemove != 0) {
                info.toRemove = 0;
            }
        }
    }

    private static int getLimit(EntityType<?> type) {
        if (!MiliEntityLimitsConfig.enabled) return Integer.MAX_VALUE;
        if (type == EntityType.WITHER) return MiliEntityLimitsConfig.witherLimit;
        if (type == EntityType.ENDER_DRAGON) return MiliEntityLimitsConfig.enderDragonLimit;
        if (type == EntityType.IRON_GOLEM) return MiliEntityLimitsConfig.ironGolemLimit;
        return MiliEntityLimitsConfig.defaultLimit;
    }

    private static int getRemoval(EntityType<?> type) {
        if (!MiliEntityLimitsConfig.enabled) return 0;
        if (type == EntityType.WITHER) return MiliEntityLimitsConfig.witherRemoval;
        if (type == EntityType.ENDER_DRAGON) return MiliEntityLimitsConfig.enderDragonRemoval;
        if (type == EntityType.IRON_GOLEM) return MiliEntityLimitsConfig.ironGolemRemoval;
        return MiliEntityLimitsConfig.defaultRemoval;
    }

    private static final class TickInfo {
        int currentTick;
        int continueFrom;
        int toTick;
        int toRemove;
    }
}
