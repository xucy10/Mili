package fun.bm.mili.rust;

import fun.bm.mili.config.modules.experiment.RayTrackingEntityTrackerConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/**
 * Helper for batch entity culling using the Rust native optimizer.
 * <p>
 * Design: one JNI call per tick, processing ALL entities for ALL viewers in a batch.
 * Uses DirectByteBuffer for zero-copy data transfer to Rust.
 * No reflection — calls RustBridge directly.
 */
public final class EntityCullHelper {

    private EntityCullHelper() {}

    /** Entity stride: 8 floats per entity [minX, minY, minZ, maxX, maxY, maxZ, posX, posZ] */
    private static final int ENTITY_STRIDE = 8;
    /** Frustum planes: 6 planes x 4 floats = 24 floats */
    private static final int PLANES_FLOATS = 24;

    /** Cached direct buffers — grown as needed, reused across ticks */
    private static ByteBuffer entityBuffer = null;
    private static ByteBuffer planesBuffer = null;

    /**
     * Check if the Rust native library is loaded and available.
     */
    public static boolean isNativeAvailable() {
        return RustBridge.isLoaded();
    }

    /**
     * Batch cull entities for a single viewer using Rust native code (zero-copy).
     *
     * @param viewer       the viewer player
     * @param entities     list of entities to check
     * @param reachSq      squared reach distance
     * @param hitboxLimit  max AABB dimension before skipping
     * @param frustumPlanes 24 floats: 6 planes x [nx, ny, nz, d], or null to build from camera
     * @return byte array where result[i] is: 0=visible, 2=too_far, 3=too_big, 4=behind; or null on failure
     */
    public static byte[] cullEntitiesBatch(
            Player viewer,
            List<Entity> entities,
            double reachSq,
            double hitboxLimit,
            float[] frustumPlanes
    ) {
        if (!RustBridge.isLoaded() || entities.isEmpty()) {
            return null;
        }

        int n = entities.size();
        int requiredBytes = n * ENTITY_STRIDE * 4; // floats -> bytes

        // Ensure entity buffer is large enough and direct
        if (entityBuffer == null || entityBuffer.capacity() < requiredBytes) {
            entityBuffer = ByteBuffer.allocateDirect(requiredBytes).order(ByteOrder.nativeOrder());
        }

        // Pack entity data into direct buffer
        entityBuffer.clear();
        entityBuffer.limit(requiredBytes);
        java.nio.FloatBuffer floatView = entityBuffer.asFloatBuffer();
        for (int i = 0; i < n; i++) {
            Entity e = entities.get(i);
            AABB box = e.getBoundingBox();
            floatView.put((float) box.minX);
            floatView.put((float) box.minY);
            floatView.put((float) box.minZ);
            floatView.put((float) box.maxX);
            floatView.put((float) box.maxY);
            floatView.put((float) box.maxZ);
            floatView.put((float) e.getX());
            floatView.put((float) e.getZ());
        }

        // Ensure planes buffer
        if (planesBuffer == null || planesBuffer.capacity() < PLANES_FLOATS * 4) {
            planesBuffer = ByteBuffer.allocateDirect(PLANES_FLOATS * 4).order(ByteOrder.nativeOrder());
        }
        planesBuffer.clear();
        java.nio.FloatBuffer planesView = planesBuffer.asFloatBuffer();
        if (frustumPlanes != null && frustumPlanes.length >= PLANES_FLOATS) {
            planesView.put(frustumPlanes, 0, PLANES_FLOATS);
        } else {
            // Build frustum from camera
            Vec3 eye = viewer.getEyePosition(1.0f);
            Vec3 look = viewer.getLookAngle();
            Vec3 up = viewer.getUpVector(1.0f);
            float[] pos = {(float) eye.x, (float) eye.y, (float) eye.z};
            float[] fwd = {(float) look.x, (float) look.y, (float) look.z};
            float[] upArr = {(float) up.x, (float) up.y, (float) up.z};
            float[] built = RustBridge.buildFrustumFromCamera(
                Math.toRadians(70.0), 16.0 / 9.0, 0.05, 1000.0,
                pos, fwd, upArr
            );
            if (built != null && built.length >= PLANES_FLOATS) {
                planesView.put(built, 0, PLANES_FLOATS);
            }
        }

        Vec3 eye = viewer.getEyePosition(1.0f);

        try {
            return RustBridge.batchCullEntitiesDirect(
                entityBuffer, n,
                eye.x, eye.y, eye.z,
                reachSq, hitboxLimit,
                planesBuffer
            );
        } catch (UnsatisfiedLinkError e) {
            return null;
        }
    }

    /**
     * Apply culling results to entities via Cullable interface.
     *
     * @param entities list of entities (same order as passed to cullEntitiesBatch)
     * @param results  culling results from cullEntitiesBatch
     */
    public static void applyCullingResults(List<Entity> entities, byte[] results) {
        if (results == null || results.length != entities.size()) {
            return;
        }

        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            if (!(entity instanceof dev.tr7zw.entityculling.versionless.access.Cullable cullable)) {
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
            cullable.setCulled(culled);
        }
    }
}
