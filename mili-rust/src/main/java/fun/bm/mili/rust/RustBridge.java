package fun.bm.mili.rust;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * JNI bridge to the Rust optimization library (mili_optimizer).
 * <p>
 * Design: <b>bulk processing only</b> for hot paths. Java collects per-frame data
 * into flat arrays, passes them to Rust once, and receives results in one batch.
 * This eliminates per-entity JNI overhead.
 * <p>
 * <b>Usage:</b> Call {@link #load()} once at startup.
 */
public final class RustBridge {

    private static volatile boolean loaded = false;

    private RustBridge() {}

    public static synchronized void load() {
        if (loaded) return;
        String os = System.getProperty("os.name").toLowerCase();
        String lib;
        if (os.contains("win")) lib = "mili_optimizer.dll";
        else if (os.contains("mac")) lib = "libmili_optimizer.dylib";
        else lib = "libmili_optimizer.so";
        try {
            Path tmp = Files.createTempDirectory("mili-rust-");
            tmp.toFile().deleteOnExit();
            Path dst = tmp.resolve(lib);
            try (InputStream is = RustBridge.class.getResourceAsStream("/rust/" + lib)) {
                if (is == null) throw new UnsatisfiedLinkError("Native library not found: /rust/" + lib);
                Files.copy(is, dst, StandardCopyOption.REPLACE_EXISTING);
            }
            System.load(dst.toAbsolutePath().toString());
            loaded = true;
        } catch (IOException e) {
            throw new UnsatisfiedLinkError("Failed to extract native library: " + e.getMessage());
        }
    }

    // ========================================================================
    // Native
    // ========================================================================

    private static native void nativeInit();

    // -- Chunk / Region (zero-cost pure Java) --------------------------------

    public static long chunkToRegion(int cx, int cz) {
        return ((long)(cx >> 5) << 32) | ((cz >> 5) & 0xFFFFFFFFL);
    }
    public static long chunkToLocal(int cx, int cz) {
        return ((long)(cx & 0x1F) << 32) | ((cz & 0x1F) & 0xFFFFFFFFL);
    }
    public static int chunkIndex(int cx, int cz) {
        return 4 * ((cx & 0x1F) + (cz & 0x1F) * 32);
    }
    public static long regionKey(int rx, int rz) {
        return ((long)rx << 32) | ((long)rz & 0xFFFFFFFFL);
    }
    public static long decodeHeaderEntry(int entry) {
        return ((long)((entry >>> 8) & 0xFFFFFF) << 32) | ((long)(entry & 0xFF));
    }
    public static int encodeHeaderEntry(int offset, int count) {
        return ((offset & 0xFFFFFF) << 8) | (count & 0xFF);
    }

    // -- VarInt --------------------------------------------------------------

    public static int varintSize(int v) {
        int u = v;
        if ((u & 0xFFFFFF80) == 0) return 1;
        if ((u & 0xFFFFC000) == 0) return 2;
        if ((u & 0xFFE00000) == 0) return 3;
        if ((u & 0xF0000000) == 0) return 4;
        return 5;
    }
    public static int varlongSize(long v) {
        if ((v & 0xFFFFFFFFFFFFFF80L) == 0) return 1;
        if ((v & 0xFFFFFFFFFFFFC000L) == 0) return 2;
        if ((v & 0xFFFFFFFFFFE00000L) == 0) return 3;
        if ((v & 0xFFFFFFFFF0000000L) == 0) return 4;
        if ((v & 0xFFFFFFF800000000L) == 0) return 5;
        if ((v & 0xFFFFFC0000000000L) == 0) return 6;
        if ((v & 0xFFFE000000000000L) == 0) return 7;
        if ((v & 0xFF00000000000000L) == 0) return 8;
        if ((v & 0x8000000000000000L) == 0) return 9;
        return 10;
    }

    // -- Hashing -------------------------------------------------------------

    public static long fnv1aHash(String s) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < s.length(); i++) {
            hash ^= s.charAt(i) & 0xFF;
            hash *= 0x100000001b3L;
        }
        return hash;
    }
    public static int murmur3_32(byte[] data, int seed) {
        int len = data.length;
        int h = seed;
        int c1 = 0xCC9E2D97;
        int c2 = 0x1B873593;
        int i = 0;
        while (i + 4 <= len) {
            int k = (data[i] & 0xFF) | ((data[i+1] & 0xFF) << 8)
                  | ((data[i+2] & 0xFF) << 16) | ((data[i+3] & 0xFF) << 24);
            k *= c1;
            k = Integer.rotateLeft(k, 15);
            k *= c2;
            h ^= k;
            h = Integer.rotateLeft(h, 13);
            h = h * 5 + 0xE6546B64;
            i += 4;
        }
        int k1 = 0;
        switch (len & 3) {
            case 3: k1 ^= (data[i+2] & 0xFF) << 16;
            case 2: k1 ^= (data[i+1] & 0xFF) << 8;
            case 1: k1 ^= (data[i] & 0xFF);
        }
        if ((len & 3) != 0) {
            k1 *= c1;
            k1 = Integer.rotateLeft(k1, 15);
            k1 *= c2;
            h ^= k1;
        }
        h ^= len;
        h ^= h >>> 16;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        h *= 0xC2B2AE35;
        h ^= h >>> 16;
        return h;
    }

    // -- Protocol (infrequent) -----------------------------------------------

    public static native long optimizePacketBatch(String input);

    // -- Scheduler (infrequent) ----------------------------------------------

    public static native long runLightweightTasks(int jobs, int work);

    // -- Bitmap --------------------------------------------------------------

    public static native long bitmapFromHex(String hex);
    public static native void bitmapFree(long ptr);
    public static native void bitmapSet(long ptr, int idx);
    public static native boolean bitmapGet(long ptr, int idx);
    public static native int bitmapCount(long ptr);
    public static native String bitmapToHex(long ptr);

    // ========================================================================
    // BULK Entity Culling — fast path for entity visibility
    // ========================================================================

    /**
     * Batch cull entities for visibility.
     *
     * @param entityData flat array: [minX, minY, minZ, maxX, maxY, maxZ, posX, posZ] × N (double, converted to f32 in Rust)
     * @param numEntities number of entities
     * @param viewerX viewer X position
     * @param viewerY viewer Y position
     * @param viewerZ viewer Z position
     * @param reach visibility reach distance (blocks)
     * @param hitboxLimit max AABB dimension before skipping
     * @param cameraFwdX camera forward vector X
     * @param cameraFwdY camera forward vector Y
     * @param cameraFwdZ camera forward vector Z
     * @param fovCos cosine of half FOV for frustum culling
     * @return byte array where result[i] is: 0=visible, 1=culled, 2=too_far, 3=too_big, 4=behind
     */
    public static native byte[] bulkCullEntities(
            double[] entityData,
            int numEntities,
            double viewerX, double viewerY, double viewerZ,
            double reach,
            double hitboxLimit,
            double cameraFwdX, double cameraFwdY, double cameraFwdZ,
            double fovCos
    );

    /**
     * Build interleaved entity data array for bulkCullEntities.
     * Each entity: 8 doubles (minX, minY, minZ, maxX, maxY, maxZ, posX, posZ).
     */
    public static double[] buildEntityData(
            double[] minX, double[] minY, double[] minZ,
            double[] maxX, double[] maxY, double[] maxZ,
            double[] posX, double[] posZ
    ) {
        int n = minX.length;
        double[] data = new double[n * 8];
        for (int i = 0; i < n; i++) {
            int b = i * 8;
            data[b]     = minX[i];
            data[b + 1] = minY[i];
            data[b + 2] = minZ[i];
            data[b + 3] = maxX[i];
            data[b + 4] = maxY[i];
            data[b + 5] = maxZ[i];
            data[b + 6] = posX[i];
            data[b + 7] = posZ[i];
        }
        return data;
    }

    // ========================================================================
    // BULK Occlusion Culling — one JNI call per frame
    // ========================================================================

    /**
     * Bulk AABB visibility check for N entities.
     *
     * @param aabbData flat array: [minX, minY, minZ, maxX, maxY, maxZ, viewX, viewY, viewZ] × N
     * @param reach    visibility reach distance (blocks)
     * @param expansion AABB expansion in blocks
     * @return byte array where result[i] == 1 means entity i is visible
     */
    public static native byte[] bulkOcclusionCull(double[] aabbData, int reach, double expansion);

    /**
     * Bulk DDA ray stepping for N rays through a shared voxel cache.
     *
     * @param rayData    flat array: [startX, startY, startZ, targetX, targetY, targetZ] × N
     * @param cameraX    camera integer position (block)
     * @param cameraY    camera integer position (block)
     * @param cameraZ    camera integer position (block)
     * @param reach      visibility reach distance (blocks)
     * @param voxelCache flattened 3D byte array: 0=unchecked, 1=air, 2=opaque
     * @param cacheSize  side length of the cubic cache
     * @return byte array where result[i] == 1 means ray i reached its target
     */
    public static native byte[] bulkStepRay(
            double[] rayData,
            int cameraX, int cameraY, int cameraZ,
            int reach, byte[] voxelCache, int cacheSize
    );

    // ========================================================================
    // BULK Mesh / Frustum Culling — chunk section visibility
    // ========================================================================

    /**
     * Batch cull chunk sections using frustum planes.
     *
     * @param sectionData flat array: [minX, minY, minZ, maxX, maxY, maxZ] × N
     * @param frustumPlanes 24 doubles: 6 planes × [nx, ny, nz, d]
     * @return byte array where 1 = visible, 0 = culled
     */
    public static native byte[] bulkCullChunkSections(double[] sectionData, double[] frustumPlanes);

    /**
     * Batch cull spheres using frustum planes.
     *
     * @param centers flat array: [x, y, z] × N
     * @param radii radius per sphere
     * @param frustumPlanes 24 doubles: 6 planes × [nx, ny, nz, d]
     * @return byte array where 1 = visible, 0 = culled
     */
    public static native byte[] bulkCullSpheres(double[] centers, double[] radii, double[] frustumPlanes);

    /**
     * Batch cull AABBs using frustum planes.
     *
     * @param aabbs flat array: [minX, minY, minZ, maxX, maxY, maxZ] × N
     * @param frustumPlanes 24 doubles: 6 planes × [nx, ny, nz, d]
     * @return byte array where 1 = visible, 0 = culled
     */
    public static native byte[] bulkCullAABBs(double[] aabbs, double[] frustumPlanes);

    // ========================================================================
    // BULK Lighting — light level computation
    // ========================================================================

    /**
     * Compute light levels from packed light data.
     *
     * @param packedLights byte array where each byte is (sky << 4) | block
     * @return byte array with max(sky, block) per block
     */
    public static native byte[] bulkComputeLightLevels(byte[] packedLights);

    /**
     * Generate a lightmap texture.
     *
     * @param gamma gamma correction value
     * @param skyBrightness sky brightness factor 0.0-1.0
     * @return int array of 256 RGBA values
     */
    public static native int[] generateLightmap(double gamma, double skyBrightness);

    // ========================================================================
    // Helpers
    // ========================================================================

    public static int unpackHigh(long packed) { return (int) (packed >> 32); }
    public static int unpackLow(long packed) { return (int) packed; }

    /**
     * Build interleaved AABB data array for bulkOcclusionCull.
     * Each entity: 9 doubles (minX, minY, minZ, maxX, maxY, maxZ, viewX, viewY, viewZ).
     */
    public static double[] buildAABBData(
            double[] aabbMinX, double[] aabbMinY, double[] aabbMinZ,
            double[] aabbMaxX, double[] aabbMaxY, double[] aabbMaxZ,
            double[] viewerX, double[] viewerY, double[] viewerZ
    ) {
        int n = aabbMinX.length;
        double[] data = new double[n * 9];
        for (int i = 0; i < n; i++) {
            int b = i * 9;
            data[b]     = aabbMinX[i];
            data[b + 1] = aabbMinY[i];
            data[b + 2] = aabbMinZ[i];
            data[b + 3] = aabbMaxX[i];
            data[b + 4] = aabbMaxY[i];
            data[b + 5] = aabbMaxZ[i];
            data[b + 6] = viewerX[i];
            data[b + 7] = viewerY[i];
            data[b + 8] = viewerZ[i];
        }
        return data;
    }

    /**
     * Build interleaved ray data for bulkStepRay.
     * Each ray: 6 doubles (startX, startY, startZ, targetX, targetY, targetZ).
     */
    public static double[] buildRayData(
            double[] startX, double[] startY, double[] startZ,
            double[] targetX, double[] targetY, double[] targetZ
    ) {
        int n = startX.length;
        double[] data = new double[n * 6];
        for (int i = 0; i < n; i++) {
            int b = i * 6;
            data[b]     = startX[i];
            data[b + 1] = startY[i];
            data[b + 2] = startZ[i];
            data[b + 3] = targetX[i];
            data[b + 4] = targetY[i];
            data[b + 5] = targetZ[i];
        }
        return data;
    }

    /**
     * Build interleaved section data for bulkCullChunkSections.
     * Each section: 6 doubles (minX, minY, minZ, maxX, maxY, maxZ).
     */
    public static double[] buildSectionData(
            double[] minX, double[] minY, double[] minZ,
            double[] maxX, double[] maxY, double[] maxZ
    ) {
        int n = minX.length;
        double[] data = new double[n * 6];
        for (int i = 0; i < n; i++) {
            int b = i * 6;
            data[b]     = minX[i];
            data[b + 1] = minY[i];
            data[b + 2] = minZ[i];
            data[b + 3] = maxX[i];
            data[b + 4] = maxY[i];
            data[b + 5] = maxZ[i];
        }
        return data;
    }

    /**
     * Build frustum planes array from 6 planes.
     * Each plane: [nx, ny, nz, d].
     */
    public static double[] buildFrustumPlanes(
            double[] left, double[] right,
            double[] bottom, double[] top,
            double[] near, double[] far
    ) {
        double[] planes = new double[24];
        System.arraycopy(left, 0, planes, 0, 4);
        System.arraycopy(right, 0, planes, 4, 4);
        System.arraycopy(bottom, 0, planes, 8, 4);
        System.arraycopy(top, 0, planes, 12, 4);
        System.arraycopy(near, 0, planes, 16, 4);
        System.arraycopy(far, 0, planes, 20, 4);
        return planes;
    }

    /**
     * Pack light value into a single byte: (sky << 4) | block.
     */
    public static byte packLight(int sky, int block) {
        return (byte) (((sky & 0x0F) << 4) | (block & 0x0F));
    }

    /**
     * Unpack sky light from packed value.
     */
    public static int unpackSky(byte packed) {
        return (packed & 0xFF) >> 4;
    }

    /**
     * Unpack block light from packed value.
     */
    public static int unpackBlock(byte packed) {
        return packed & 0x0F;
    }
}