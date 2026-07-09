package `fun`.bm.mili.utils;

import io.papermc.paper.threadedregions.RegionizedWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.concurrent.ConcurrentLinkedQueue;

public class CrossRegionHelperThread {

    @SuppressWarnings("rawtypes")
    public static ConcurrentLinkedQueue onRegionTick(ServerLevel level, RegionizedWorldData data) {
        return CrossRegionHelper.INSTANCE.onRegionTick(level, data);
    }

    public static void submitDamageCrossRegion(LivingEntity src, LivingEntity tgt, DamageSource ds, long tick) {
        if (src != null && tgt != null && ds != null) {
            CrossRegionHelper.INSTANCE.submitDamageCrossRegion(src, tgt, ds, tick);
        }
    }

    public static void submitRedstoneCrossRegion(ServerLevel sl, BlockPos pos, BlockPos neighbor, Direction dir) {
        CrossRegionHelper.INSTANCE.submitRedstoneCrossRegion(sl, pos, neighbor, dir);
    }
}
