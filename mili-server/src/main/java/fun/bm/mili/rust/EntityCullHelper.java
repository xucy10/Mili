package fun.bm.mili.rust;

import fun.bm.mili.config.modules.experiment.RayTrackingEntityTrackerConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Helper for batch entity culling using the Rust native optimizer.
 * <p>
 * This class provides a bridge between Minecraft's entity tracking system
 * and the Rust bulk culling implementation. If the Rust native library
 * is not available, it falls back to Java-side culling logic.
 * <p>
 * Design: one JNI call per frame, processing all entities in a batch.
 */
public final class EntityCullHelper {

    private EntityCullHelper() {}

    private static final String RUST_BRIDGE_CLASS = "fun.bm.mili.rust.RustBridge";
    private static final Method BULK_CULL_METHOD = findBulkCullMethod();
    private static final Method BUILD_ENTITY_DATA_METHOD = findBuildEntityDataMethod();

    private static Method findBulkCullMethod() {
        try {
            Class<?> bridge = Class.forName(RUST_BRIDGE_CLASS);
            return bridge.getMethod("bulkCullEntities",
                double[].class, int.class,
                double.class, double.class, double.class,
                double.class, double.class,
                double.class, double.class, double.class,
                double.class);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            return null;
        }
    }

    private static Method findBuildEntityDataMethod() {
        try {
            Class<?> bridge = Class.forName(RUST_BRIDGE_CLASS);
            return bridge.getMethod("buildEntityData",
                double[].class, double[].class, double[].class,
                double[].class, double[].class, double[].class,
                double[].class, double[].class);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            return null;
        }
    }

    /**
     * Check if the Rust bulk culling native method is available.
     */
    public static boolean isNativeAvailable() {
        return BULK_CULL_METHOD != null && BUILD_ENTITY_DATA_METHOD != null;
    }

    /**
     * Batch cull entities for a player using Rust native code.
     *
     * @param player the viewer
     * @param entities list of entities to check
     * @param reach visibility reach distance
     * @param hitboxLimit max AABB dimension before skipping
     * @return array of culling results, or null if native is unavailable
     */
    public static byte[] cullEntitiesNative(
            Player player,
            List<Entity> entities,
            double reach,
            double hitboxLimit
    ) {
        if (!isNativeAvailable() || entities.isEmpty()) {
            return null;
        }

        int n = entities.size();
        double[] minX = new double[n];
        double[] minY = new double[n];
        double[] minZ = new double[n];
        double[] maxX = new double[n];
        double[] maxY = new double[n];
        double[] maxZ = new double[n];
        double[] posX = new double[n];
        double[] posZ = new double[n];

        for (int i = 0; i < n; i++) {
            Entity e = entities.get(i);
            AABB box = e.getBoundingBox();
            minX[i] = box.minX;
            minY[i] = box.minY;
            minZ[i] = box.minZ;
            maxX[i] = box.maxX;
            maxY[i] = box.maxY;
            maxZ[i] = box.maxZ;
            posX[i] = e.getX();
            posZ[i] = e.getZ();
        }

        Vec3 eye = player.getEyePosition(1.0f);
        Vec3 look = player.getLookAngle();

        // FOV cosine: default Minecraft FOV is 70 degrees
        double fovCos = Math.cos(Math.toRadians(70.0 / 2.0));

        try {
            double[] entityData = (double[]) BUILD_ENTITY_DATA_METHOD.invoke(null,
                minX, minY, minZ, maxX, maxY, maxZ, posX, posZ);

            return (byte[]) BULK_CULL_METHOD.invoke(null,
                entityData, n,
                eye.x, eye.y, eye.z,
                reach, hitboxLimit,
                look.x, look.y, look.z,
                fovCos);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /**
     * Apply culling results to entities.
     *
     * @param entities list of entities (same order as passed to cullEntitiesNative)
     * @param results culling results from cullEntitiesNative
     * @param cullableClass the Cullable interface class
     * @param setCulledMethod the setCulled method
     */
    public static void applyCullingResults(
            List<Entity> entities,
            byte[] results,
            Class<?> cullableClass,
            Method setCulledMethod
    ) {
        if (results == null || results.length != entities.size()) {
            return;
        }

        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            if (!cullableClass.isInstance(entity)) {
                continue;
            }

            boolean culled;
            switch (results[i]) {
                case 0: // VISIBLE
                    culled = false;
                    break;
                case 2: // TOO_FAR
                case 3: // TOO_BIG
                    culled = false; // Don't cull, just skip raytrace
                    break;
                case 1: // CULLED
                case 4: // BEHIND
                default:
                    culled = true;
                    break;
            }

            try {
                setCulledMethod.invoke(entity, culled);
            } catch (ReflectiveOperationException ignored) {
            }
        }
    }

    /**
     * Java fallback for single-entity culling checks.
     * Mirrors the logic in Rust for consistency.
     */
    public static boolean shouldCullEntity(
            Entity entity,
            Vec3 viewerPos,
            Vec3 cameraForward,
            double reachSq,
            double hitboxLimit
    ) {
        AABB box = entity.getBoundingBox();

        // Distance check
        double dx = entity.getX() - viewerPos.x;
        double dz = entity.getZ() - viewerPos.z;
        if (dx * dx + dz * dz > reachSq) {
            return false; // Too far — don't cull, just don't raytrace
        }

        // Hitbox size check
        if (box.getXsize() > hitboxLimit || box.getYsize() > hitboxLimit || box.getZsize() > hitboxLimit) {
            return false; // Too big — don't cull
        }

        // Frustum check (simplified)
        double centerX = (box.minX + box.maxX) * 0.5;
        double centerY = (box.minY + box.maxY) * 0.5;
        double centerZ = (box.minZ + box.maxZ) * 0.5;

        double toX = centerX - viewerPos.x;
        double toY = centerY - viewerPos.y;
        double toZ = centerZ - viewerPos.z;

        double lenSq = toX * toX + toY * toY + toZ * toZ;
        if (lenSq > 0) {
            double len = Math.sqrt(lenSq);
            double dot = (toX * cameraForward.x + toY * cameraForward.y + toZ * cameraForward.z) / len;
            double fovCos = Math.cos(Math.toRadians(70.0 / 2.0));
            if (dot < fovCos) {
                return true; // Behind camera
            }
        }

        return false; // Potentially visible — needs raycast
    }
}