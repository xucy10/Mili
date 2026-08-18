package fun.bm.mili.rust;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * JNI bridge to the Rust optimization library (mili_optimizer).
 * <p>
 * Design: <b>bulk processing only</b> for hot paths. Java collects per-tick data
 * into flat arrays or DirectByteBuffers, passes them to Rust once, and receives
 * results in one batch. This eliminates per-entity JNI overhead.
 * <p>
 * <b>Usage:</b> Call {@link #load()} once at startup.
 */
public final class RustBridge {

    private static volatile boolean loaded = false;

    private RustBridge() {}

    public static synchronized void load() {
        if (loaded) return;
        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();
        // Build candidate list: primary name first, then fallbacks
        java.util.List<String> candidates = new java.util.ArrayList<>();
        if (os.contains("win")) {
            candidates.add("mili_optimizer.dll");
        } else if (os.contains("mac")) {
            if (arch.contains("aarch64") || arch.contains("arm64")) {
                candidates.add("libmili_optimizer.dylib");
            } else {
                candidates.add("libmili_optimizer_x86_64.dylib");
                candidates.add("libmili_optimizer.dylib");
            }
        } else {
            // Linux
            if (arch.contains("aarch64") || arch.contains("arm64")) {
                candidates.add("libmili_optimizer_aarch64.so");
            } else {
                candidates.add("libmili_optimizer.so");
            }
        }
        try {
            Path tmp = Files.createTempDirectory("mili-rust-");
            tmp.toFile().deleteOnExit();
            UnsatisfiedLinkError lastError = null;
            for (String lib : candidates) {
                try (InputStream is = RustBridge.class.getResourceAsStream("/rust/" + lib)) {
                    if (is == null) continue;
                    Path dst = tmp.resolve(lib);
                    Files.copy(is, dst, StandardCopyOption.REPLACE_EXISTING);
                    dst.toFile().deleteOnExit();
                    System.load(dst.toAbsolutePath().toString());
                    nativeInit();
                    loaded = true;
                    return;
                } catch (UnsatisfiedLinkError e) {
                    lastError = e;
                }
            }
            throw new UnsatisfiedLinkError(
                "Native library not found for os=" + os + " arch=" + arch +
                " (tried: " + String.join(", ", candidates) + ")" +
                (lastError != null ? ". Last error: " + lastError.getMessage() : "")
            );
        } catch (IOException e) {
            throw new UnsatisfiedLinkError("Failed to extract native library: " + e.getMessage());
        }
    }

    /**
     * Check if the native library is loaded.
     */
    public static boolean isLoaded() {
        return loaded;
    }

    // ========================================================================
    // Native
    // ========================================================================

    private static native void nativeInit();

    // ========================================================================
    // BULK Entity Culling — zero-copy via DirectByteBuffer
    // ========================================================================

    /**
     * Batch cull entities using DirectByteBuffer (zero-copy).
     * <p>
     * The entity buffer must be a direct ByteBuffer in native byte order,
     * containing numEntities * 8 floats:
     * [minX, minY, minZ, maxX, maxY, maxZ, posX, posZ] per entity.
     * <p>
     * The planes buffer must be a direct ByteBuffer containing 24 floats
     * (6 frustum planes x [nx, ny, nz, d]).
     *
     * @param entityBuffer  direct ByteBuffer with entity data (f32, little-endian)
     * @param numEntities   number of entities
     * @param viewerX       viewer X position
     * @param viewerY       viewer Y position
     * @param viewerZ       viewer Z position
     * @param reachSq       squared reach distance
     * @param hitboxLimit   max AABB dimension before skipping
     * @param planesBuffer  direct ByteBuffer with 6 frustum planes (24 f32)
     * @return byte array where result[i] is: 0=visible, 2=too_far, 3=too_big, 4=behind
     */
    public static native byte[] batchCullEntitiesDirect(
            ByteBuffer entityBuffer,
            int numEntities,
            double viewerX, double viewerY, double viewerZ,
            double reachSq,
            double hitboxLimit,
            ByteBuffer planesBuffer
    );

    /**
     * Batch cull entities using float arrays (fallback, copies data).
     *
     * @param entityData    flat float array: [minX, minY, minZ, maxX, maxY, maxZ, posX, posZ] x N
     * @param numEntities   number of entities
     * @param viewerX       viewer X position
     * @param viewerY       viewer Y position
     * @param viewerZ       viewer Z position
     * @param reachSq       squared reach distance
     * @param hitboxLimit   max AABB dimension before skipping
     * @param frustumPlanes 24 floats: 6 planes x [nx, ny, nz, d]
     * @return byte array where result[i] is: 0=visible, 2=too_far, 3=too_big, 4=behind
     */
    public static native byte[] batchCullEntities(
            float[] entityData,
            int numEntities,
            double viewerX, double viewerY, double viewerZ,
            double reachSq,
            double hitboxLimit,
            float[] frustumPlanes
    );

    // ========================================================================
    // Batch analytics — chunk hotness / dynamic view distance / entity density
    // ========================================================================

    /**
     * Analyze chunk hotness in bulk.
     *
     * @param chunkCoordBuffer direct ByteBuffer with [chunkX, chunkZ] x N (i32)
     * @param numChunks number of chunks packed into the buffer
     * @param playerCoordBuffer direct ByteBuffer with [playerChunkX, playerChunkZ] x M (i32)
     * @param numPlayers number of players packed into the buffer
     * @param radiusSq squared hot-chunk radius threshold
     * @return double array with [nearFlag(0/1), minDistSq] x N
     */
    public static native double[] analyzeChunkHotnessDirect(
            ByteBuffer chunkCoordBuffer,
            int numChunks,
            ByteBuffer playerCoordBuffer,
            int numPlayers,
            double radiusSq
    );

    /**
     * Compute per-player dynamic view distances in bulk.
     *
     * @param playerDataBuffer direct ByteBuffer with [blockX, blockZ, currentViewDistance] x N (i32)
     * @param numPlayers number of players packed into the buffer
     * @param currentTps current server TPS
     * @param tpsHighThreshold high TPS threshold for increasing view distance
     * @param tpsLowThreshold low TPS threshold for decreasing view distance
     * @param minViewDistance minimum allowed view distance
     * @param maxViewDistance maximum allowed view distance
     * @param playerDensityWeight density penalty weight
     * @return int array of target view distances, one per input player
     */
    public static native int[] computeDynamicViewDistancesDirect(
            ByteBuffer playerDataBuffer,
            int numPlayers,
            double currentTps,
            double tpsHighThreshold,
            double tpsLowThreshold,
            int minViewDistance,
            int maxViewDistance,
            double playerDensityWeight
    );

    /**
     * Build an entity density heatmap in bulk.
     *
     * @param entityDataBuffer direct ByteBuffer with [blockX, blockZ, livingFlag] x N (i32)
     * @param numEntities number of entities packed into the buffer
     * @param cellSize grid cell size in blocks
     * @param threshold high-density warning threshold
     * @return long array encoded as [totalEntities, livingEntities, maxDensity, hotCells, cellCount, key1, count1, ...]
     */
    public static native long[] analyzeEntityDensityDirect(
            ByteBuffer entityDataBuffer,
            int numEntities,
            int cellSize,
            int threshold
    );

    /**
     * Evaluate villager activity in bulk.
     *
     * @param villagerStateBuffer direct ByteBuffer with one i32 state bitmask per villager
     * @param blockFlagBuffer direct ByteBuffer with 16 i32 block bitmasks per villager
     * @param numVillagers number of villagers packed into the buffers
     * @param configFlags global activity-policy config bitmask
     * @return byte array where result[i] is 1 if the villager should remain active, otherwise 0
     */
    public static native byte[] evaluateVillagerActivityDirect(
            ByteBuffer villagerStateBuffer,
            ByteBuffer blockFlagBuffer,
            int numVillagers,
            int configFlags
    );

    /**
     * Build 6 frustum planes from camera parameters.
     *
     * @param fovY     vertical FOV in radians
     * @param aspect   aspect ratio (width / height)
     * @param near     near clip distance
     * @param far      far clip distance
     * @param posX/Y/Z camera position
     * @param fwdX/Y/Z camera forward vector (normalized)
     * @param upX/Y/Z  camera up vector (normalized)
     * @return 24 floats: 6 planes x [nx, ny, nz, d]
     */
    public static native float[] buildFrustumFromCamera(
            double fovY, double aspect, double near, double far,
            float[] pos, float[] fwd, float[] up
    );

    // ========================================================================
    // Config Engine — TOML parse/serialize with comment preservation
    // ========================================================================

    public static native String configLoad(String path);
    public static native boolean configSave(String path, String json);
    public static native boolean configSaveMerge(String path, String json);
    public static native boolean configContains(String path, String key);
    public static native String configGetValue(String path, String key);
    public static native boolean configRemove(String path, String key);
    public static native boolean configClear(String path);
}
