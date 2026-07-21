package fun.bm.mili.utils;

import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class EntityDirtyTracker {
    private static volatile boolean enabled = false;

    private static final ConcurrentHashMap<Integer, EntityState> states = new ConcurrentHashMap<>();
    private static final AtomicInteger totalChecks = new AtomicInteger();
    private static final AtomicInteger skippedEntities = new AtomicInteger();
    private static final AtomicLong savedTicks = new AtomicLong();

    public static void setEnabled(boolean v) { enabled = v; }
    public static boolean isEnabled() { return enabled; }

    public static boolean shouldSkipTick(Entity entity) {
        if (!enabled) return false;

        int id = entity.getId();
        EntityState state = states.computeIfAbsent(id, k -> new EntityState());
        totalChecks.incrementAndGet();

        double x = entity.getX();
        double y = entity.getY();
        double z = entity.getZ();
        float yRot = entity.getYRot();
        float xRot = entity.getXRot();

        double dx = x - state.lastX;
        double dy = y - state.lastY;
        double dz = z - state.lastZ;

        boolean positionChanged = (dx * dx + dy * dy + dz * dz) >
                fun.bm.mili.config.modules.optimizations.EntityDirtyTrackingConfig.positionThreshold *
                        fun.bm.mili.config.modules.optimizations.EntityDirtyTrackingConfig.positionThreshold;
        boolean rotationChanged = yRot != state.lastYRot || xRot != state.lastXRot;
        boolean velocityChanged = entity.getDeltaMovement().lengthSqr() > 0.0001;
        boolean onGroundChanged = entity.onGround() != state.wasOnGround;

        boolean dirty = positionChanged || rotationChanged || velocityChanged || onGroundChanged;

        if (dirty) {
            state.lastX = x;
            state.lastY = y;
            state.lastZ = z;
            state.lastYRot = yRot;
            state.lastXRot = xRot;
            state.wasOnGround = entity.onGround();
            state.idleTicks = 0;
        } else {
            state.idleTicks++;
        }

        int skipThreshold = fun.bm.mili.config.modules.optimizations.EntityDirtyTrackingConfig.skipIdleAfterTicks;
        if (skipThreshold > 0 && state.idleTicks >= skipThreshold) {
            int total = totalChecks.get();
            int skipped = skippedEntities.get();
            double ratio = total > 0 ? (double) skipped / total : 0;
            double maxRatio = fun.bm.mili.config.modules.optimizations.EntityDirtyTrackingConfig.maxSkipRatio;

            if (ratio < maxRatio) {
                skippedEntities.incrementAndGet();
                savedTicks.incrementAndGet();
                return true;
            }
        }

        return false;
    }

    public static void removeEntity(Entity entity) {
        states.remove(entity.getId());
    }

    public static Map<String, Object> getStats() {
        Map<String, Object> stats = new java.util.LinkedHashMap<>();
        stats.put("Tracked Entities", states.size());
        stats.put("Total Checks", totalChecks.get());
        stats.put("Skipped Entities", skippedEntities.get());
        stats.put("Saved Ticks", savedTicks.get());
        int total = totalChecks.get();
        stats.put("Skip Rate", total > 0 ?
                String.format("%.1f%%", (double) skippedEntities.get() / total * 100) : "0%");
        return stats;
    }

    public static void reset() {
        states.clear();
        totalChecks.set(0);
        skippedEntities.set(0);
        savedTicks.set(0);
    }

    private static class EntityState {
        double lastX, lastY, lastZ;
        float lastYRot, lastXRot;
        boolean wasOnGround;
        int idleTicks;
    }
}
