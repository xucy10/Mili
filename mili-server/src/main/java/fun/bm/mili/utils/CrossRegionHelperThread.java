package fun.bm.mili.utils;

import io.papermc.paper.threadedregions.RegionizedWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.concurrent.ConcurrentLinkedQueue;

public class CrossRegionHelperThread {

    public static ConcurrentLinkedQueue<?> onRegionTick(ServerLevel level, RegionizedWorldData data) {
        return CrossRegionHelper.onRegionTick(level, data);
    }

    public static void submitDamageCrossRegion(LivingEntity src, LivingEntity tgt, DamageSource ds, long tick) {
        CrossRegionHelper.submitDamageCrossRegion(src, tgt, ds, tick);
    }

    public static void submitRedstoneCrossRegion(ServerLevel sl, BlockPos pos, BlockPos neighbor, Direction dir) {
        CrossRegionHelper.submitRedstoneCrossRegion(sl, pos, neighbor, dir);
    }
}
