package fun.bm.mili.rust;

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
    // Mili start - fix: static ByteBuffer 多线程并发访问导致数据损坏，改为 ThreadLocal
    private static final ThreadLocal<ByteBuffer> entityBuffer = ThreadLocal.withInitial(() -> null);
    private static final ThreadLocal<ByteBuffer> planesBuffer = ThreadLocal.withInitial(() -> null);
    // Mili end

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
        // Mili start - fix: 整数溢出风险，n * ENTITY_STRIDE * 4 可能溢出 int 范围
        long requiredBytesLong = (long) n * ENTITY_STRIDE * 4;
        if (requiredBytesLong > Integer.MAX_VALUE) {
            return null;
        }
        int requiredBytes = (int) requiredBytesLong;
        // Mili end

        // Mili start - fix: 使用 ThreadLocal 隔离的 ByteBuffer，避免多线程数据损坏
        ByteBuffer entityBuf = entityBuffer.get();
        // Mili end
        // Ensure entity buffer is large enough and direct
        if (entityBuf == null || entityBuf.capacity() < requiredBytes) {
            entityBuf = ByteBuffer.allocateDirect(requiredBytes).order(ByteOrder.nativeOrder());
            // Mili start - fix: 使用 ThreadLocal 隔离的 ByteBuffer
            entityBuffer.set(entityBuf);
            // Mili end
        }

        // Pack entity data into direct buffer
        entityBuf.clear();
        entityBuf.limit(requiredBytes);
        java.nio.FloatBuffer floatView = entityBuf.asFloatBuffer();
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

        // Mili start - fix: 使用 ThreadLocal 隔离的 ByteBuffer
        ByteBuffer planesBuf = planesBuffer.get();
        // Mili end
        // Ensure planes buffer
        if (planesBuf == null || planesBuf.capacity() < PLANES_FLOATS * 4) {
            planesBuf = ByteBuffer.allocateDirect(PLANES_FLOATS * 4).order(ByteOrder.nativeOrder());
            // Mili start - fix: 使用 ThreadLocal 隔离的 ByteBuffer
            planesBuffer.set(planesBuf);
            // Mili end
        }
        planesBuf.clear();
        java.nio.FloatBuffer planesView = planesBuf.asFloatBuffer();
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
            // Mili start - fix: 传递 ThreadLocal 隔离的局部变量而非 static 字段
            return RustBridge.batchCullEntitiesDirect(
                entityBuf, n,
                eye.x, eye.y, eye.z,
                reachSq, hitboxLimit,
                planesBuf
            );
            // Mili end
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
