package fun.bm.mili.rust;

import org.bukkit.util.Vector;
import fun.bm.mili.rust.RustBridge;

/**
 * Java-side helper for Rust rendering optimizations.
 * <p>
 * Provides convenience methods for:
 * <ul>
 *   <li>Chunk section frustum culling</li>
 *   <li>Entity frustum culling</li>
 *   <li>Light level computation</li>
 *   <li>Lightmap generation</li>
 * </ul>
 * <p>
 * All methods gracefully fall back to Java implementations if the Rust native
 * library is not available.
 */
public final class RenderHelper {

    private static final boolean RUST_AVAILABLE;
    private static final double[] TEMP_PLANES = new double[24];

    static {
        boolean available = false;
        try {
            RustBridge.load();
            available = true;
        } catch (UnsatisfiedLinkError | Exception ignored) {
        }
        RUST_AVAILABLE = available;
    }

    private RenderHelper() {}

    /**
     * Check if the Rust native library is loaded and available.
     */
    public static boolean isRustAvailable() {
        return RUST_AVAILABLE;
    }

    // ========================================================================
    // Frustum Culling
    // ========================================================================

    /**
     * Cull chunk sections against a view frustum.
     *
     * @param sections array of section AABBs, each as [minX, minY, minZ, maxX, maxY, maxZ]
     * @param frustumPlanes 6 frustum planes, each as [nx, ny, nz, d]
     * @return boolean array where true = visible
     */
    public static boolean[] cullChunkSections(double[][] sections, double[][] frustumPlanes) {
        if (sections == null || sections.length == 0) {
            return new boolean[0];
        }

        if (RUST_AVAILABLE) {
            return cullChunkSectionsRust(sections, frustumPlanes);
        }

        return cullChunkSectionsJava(sections, frustumPlanes);
    }

    private static boolean[] cullChunkSectionsRust(double[][] sections, double[][] frustumPlanes) {
        double[] sectionData = new double[sections.length * 6];
        for (int i = 0; i < sections.length; i++) {
            System.arraycopy(sections[i], 0, sectionData, i * 6, 6);
        }

        double[] planes = new double[24];
        for (int i = 0; i < 6; i++) {
            System.arraycopy(frustumPlanes[i], 0, planes, i * 4, 4);
        }

        byte[] results = RustBridge.bulkCullChunkSections(sectionData, planes);
        boolean[] visible = new boolean[results.length];
        for (int i = 0; i < results.length; i++) {
            visible[i] = results[i] != 0;
        }
        return visible;
    }

    private static boolean[] cullChunkSectionsJava(double[][] sections, double[][] frustumPlanes) {
        boolean[] visible = new boolean[sections.length];
        for (int i = 0; i < sections.length; i++) {
            visible[i] = isAABBInFrustum(sections[i], frustumPlanes);
        }
        return visible;
    }

    /**
     * Cull entities (spheres) against a view frustum.
     *
     * @param centers entity centers
     * @param radii entity radii
     * @param frustumPlanes 6 frustum planes
     * @return boolean array where true = visible
     */
    public static boolean[] cullSpheres(Vector[] centers, double[] radii, double[][] frustumPlanes) {
        if (centers == null || centers.length == 0) {
            return new boolean[0];
        }

        if (RUST_AVAILABLE) {
            return cullSpheresRust(centers, radii, frustumPlanes);
        }

        return cullSpheresJava(centers, radii, frustumPlanes);
    }

    private static boolean[] cullSpheresRust(Vector[] centers, double[] radii, double[][] frustumPlanes) {
        double[] centerData = new double[centers.length * 3];
        for (int i = 0; i < centers.length; i++) {
            centerData[i * 3] = centers[i].getX();
            centerData[i * 3 + 1] = centers[i].getY();
            centerData[i * 3 + 2] = centers[i].getZ();
        }

        double[] planes = new double[24];
        for (int i = 0; i < 6; i++) {
            System.arraycopy(frustumPlanes[i], 0, planes, i * 4, 4);
        }

        byte[] results = RustBridge.bulkCullSpheres(centerData, radii, planes);
        boolean[] visible = new boolean[results.length];
        for (int i = 0; i < results.length; i++) {
            visible[i] = results[i] != 0;
        }
        return visible;
    }

    private static boolean[] cullSpheresJava(Vector[] centers, double[] radii, double[][] frustumPlanes) {
        boolean[] visible = new boolean[centers.length];
        for (int i = 0; i < centers.length; i++) {
            visible[i] = isSphereInFrustum(centers[i], radii[i], frustumPlanes);
        }
        return visible;
    }

    /**
     * Cull AABBs against a view frustum.
     *
     * @param aabbs array of AABBs, each as [minX, minY, minZ, maxX, maxY, maxZ]
     * @param frustumPlanes 6 frustum planes
     * @return boolean array where true = visible
     */
    public static boolean[] cullAABBs(double[][] aabbs, double[][] frustumPlanes) {
        if (aabbs == null || aabbs.length == 0) {
            return new boolean[0];
        }

        if (RUST_AVAILABLE) {
            return cullAABBsRust(aabbs, frustumPlanes);
        }

        return cullAABBsJava(aabbs, frustumPlanes);
    }

    private static boolean[] cullAABBsRust(double[][] aabbs, double[][] frustumPlanes) {
        double[] aabbData = new double[aabbs.length * 6];
        for (int i = 0; i < aabbs.length; i++) {
            System.arraycopy(aabbs[i], 0, aabbData, i * 6, 6);
        }

        double[] planes = new double[24];
        for (int i = 0; i < 6; i++) {
            System.arraycopy(frustumPlanes[i], 0, planes, i * 4, 4);
        }

        byte[] results = RustBridge.bulkCullAABBs(aabbData, planes);
        boolean[] visible = new boolean[results.length];
        for (int i = 0; i < results.length; i++) {
            visible[i] = results[i] != 0;
        }
        return visible;
    }

    private static boolean[] cullAABBsJava(double[][] aabbs, double[][] frustumPlanes) {
        boolean[] visible = new boolean[aabbs.length];
        for (int i = 0; i < aabbs.length; i++) {
            visible[i] = isAABBInFrustum(aabbs[i], frustumPlanes);
        }
        return visible;
    }

    // ========================================================================
    // Lighting
    // ========================================================================

    /**
     * Compute light levels from packed light data.
     *
     * @param packedLights byte array where each byte is (sky << 4) | block
     * @return byte array with max(sky, block) per block
     */
    public static byte[] computeLightLevels(byte[] packedLights) {
        if (packedLights == null || packedLights.length == 0) {
            return new byte[0];
        }

        if (RUST_AVAILABLE) {
            return RustBridge.bulkComputeLightLevels(packedLights);
        }

        return computeLightLevelsJava(packedLights);
    }

    private static byte[] computeLightLevelsJava(byte[] packedLights) {
        byte[] results = new byte[packedLights.length];
        for (int i = 0; i < packedLights.length; i++) {
            int packed = packedLights[i] & 0xFF;
            int sky = packed >> 4;
            int block = packed & 0x0F;
            results[i] = (byte) Math.max(sky, block);
        }
        return results;
    }

    /**
     * Generate a lightmap texture.
     *
     * @param gamma gamma correction value
     * @param skyBrightness sky brightness factor 0.0-1.0
     * @return int array of 256 RGBA values
     */
    public static int[] generateLightmap(double gamma, double skyBrightness) {
        if (RUST_AVAILABLE) {
            return RustBridge.generateLightmap(gamma, skyBrightness);
        }

        return generateLightmapJava(gamma, skyBrightness);
    }

    private static int[] generateLightmapJava(double gamma, double skyBrightness) {
        int[] result = new int[256];
        for (int sky = 0; sky <= 15; sky++) {
            for (int block = 0; block <= 15; block++) {
                int light = Math.max(sky, block);
                double brightness = light / 15.0;
                if (gamma > 0) {
                    brightness = Math.pow(brightness, 1.0 / (1.0 + gamma));
                }

                double skyContrib = sky > 0 ? skyBrightness * (sky / 15.0) : 0.0;
                double blockContrib = block > 0 ? block / 15.0 : 0.0;
                double total = Math.min(brightness + skyContrib * 0.5 + blockContrib * 0.5, 1.0);

                int r = (int) (total * 255);
                int g = (int) (total * 255);
                int b = (int) (total * 255);
                result[sky * 16 + block] = (0xFF << 24) | (r << 16) | (g << 8) | b;
            }
        }
        return result;
    }

    // ========================================================================
    // Java fallback implementations
    // ========================================================================

    private static boolean isAABBInFrustum(double[] aabb, double[][] planes) {
        for (double[] plane : planes) {
            double px = plane[0] > 0 ? aabb[0] : aabb[3];
            double py = plane[1] > 0 ? aabb[1] : aabb[4];
            double pz = plane[2] > 0 ? aabb[2] : aabb[5];

            double dist = px * plane[0] + py * plane[1] + pz * plane[2] + plane[3];
            if (dist < 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSphereInFrustum(Vector center, double radius, double[][] planes) {
        for (double[] plane : planes) {
            double dist = center.getX() * plane[0] + center.getY() * plane[1]
                    + center.getZ() * plane[2] + plane[3];
            if (dist < -radius) {
                return false;
            }
        }
        return true;
    }
}