/// Frustum culling — optimized AABB vs frustum tests for chunk and entity rendering.
///
/// This module provides SIMD-friendly frustum plane representations and
/// fast AABB rejection tests used by both chunk section culling and entity culling.
use rayon::prelude::*;

/// A frustum defined by 6 clip-space planes.
/// Each plane is [nx, ny, nz, d] where the positive half-space is inside.
#[derive(Debug, Clone, Copy)]
pub struct Frustum {
    pub planes: [[f32; 4]; 6],
}

/// Frustum plane indices.
pub const PLANE_LEFT: usize = 0;
pub const PLANE_RIGHT: usize = 1;
pub const PLANE_BOTTOM: usize = 2;
pub const PLANE_TOP: usize = 3;
pub const PLANE_NEAR: usize = 4;
pub const PLANE_FAR: usize = 5;

impl Frustum {
    /// Build a frustum from a projection-view matrix.
    ///
    /// Extracts the 6 clip-space planes from a 4x4 matrix.
    /// Matrix is in column-major order: m[col * 4 + row].
    pub fn from_matrix(m: &[f32; 16]) -> Self {
        let mut planes = [[0.0f32; 4]; 6];

        // Left plane: row 3 + row 0
        planes[PLANE_LEFT] = normalize_plane([
            m[3] + m[0],
            m[7] + m[4],
            m[11] + m[8],
            m[15] + m[12],
        ]);

        // Right plane: row 3 - row 0
        planes[PLANE_RIGHT] = normalize_plane([
            m[3] - m[0],
            m[7] - m[4],
            m[11] - m[8],
            m[15] - m[12],
        ]);

        // Bottom plane: row 3 + row 1
        planes[PLANE_BOTTOM] = normalize_plane([
            m[3] + m[1],
            m[7] + m[5],
            m[11] + m[9],
            m[15] + m[13],
        ]);

        // Top plane: row 3 - row 1
        planes[PLANE_TOP] = normalize_plane([
            m[3] - m[1],
            m[7] - m[5],
            m[11] - m[9],
            m[15] - m[13],
        ]);

        // Near plane: row 3 + row 2
        planes[PLANE_NEAR] = normalize_plane([
            m[3] + m[2],
            m[7] + m[6],
            m[11] + m[10],
            m[15] + m[14],
        ]);

        // Far plane: row 3 - row 2
        planes[PLANE_FAR] = normalize_plane([
            m[3] - m[2],
            m[7] - m[6],
            m[11] - m[10],
            m[15] - m[14],
        ]);

        Frustum { planes }
    }

    /// Test if a sphere is inside the frustum.
    #[inline(always)]
    pub fn test_sphere(&self, cx: f32, cy: f32, cz: f32, radius: f32) -> bool {
        for plane in &self.planes {
            let dist = cx * plane[0] + cy * plane[1] + cz * plane[2] + plane[3];
            if dist < -radius {
                return false;
            }
        }
        true
    }

    /// Test if an AABB is inside the frustum.
    ///
    /// Uses the "corner selection" method: for each plane, pick the AABB corner
    /// that is most opposite to the plane normal. If that corner is outside,
    /// the entire AABB is outside.
    #[inline(always)]
    pub fn test_aabb(&self, min_x: f32, min_y: f32, min_z: f32, max_x: f32, max_y: f32, max_z: f32) -> bool {
        for plane in &self.planes {
            let px = if plane[0] > 0.0 { min_x } else { max_x };
            let py = if plane[1] > 0.0 { min_y } else { max_y };
            let pz = if plane[2] > 0.0 { min_z } else { max_z };

            let dist = px * plane[0] + py * plane[1] + pz * plane[2] + plane[3];
            if dist < 0.0 {
                return false;
            }
        }
        true
    }

    /// Test if a point is inside the frustum.
    #[inline(always)]
    pub fn test_point(&self, x: f32, y: f32, z: f32) -> bool {
        for plane in &self.planes {
            let dist = x * plane[0] + y * plane[1] + z * plane[2] + plane[3];
            if dist < 0.0 {
                return false;
            }
        }
        true
    }
}

#[inline(always)]
fn normalize_plane(plane: [f32; 4]) -> [f32; 4] {
    let len = (plane[0] * plane[0] + plane[1] * plane[1] + plane[2] * plane[2]).sqrt();
    if len > 0.0 {
        [plane[0] / len, plane[1] / len, plane[2] / len, plane[3] / len]
    } else {
        plane
    }
}

/// Batch frustum cull for spheres.
///
/// # Arguments
/// * `centers` — flat array of [x, y, z] per sphere
/// * `radii` — radius per sphere
/// * `num_spheres` — number of spheres
///
/// Returns Vec<u8> where 1 = visible, 0 = culled.
pub fn batch_cull_spheres(
    centers: &[f32],
    radii: &[f32],
    num_spheres: usize,
    frustum: &Frustum,
) -> Vec<u8> {
    if num_spheres == 0 {
        return Vec::new();
    }

    if num_spheres <= 128 {
        let mut results = vec![0u8; num_spheres];
        for i in 0..num_spheres {
            let base = i * 3;
            results[i] = frustum.test_sphere(centers[base], centers[base + 1], centers[base + 2], radii[i]) as u8;
        }
        return results;
    }

    (0..num_spheres)
        .into_par_iter()
        .map(|i| {
            let base = i * 3;
            frustum.test_sphere(centers[base], centers[base + 1], centers[base + 2], radii[i]) as u8
        })
        .collect()
}

/// Batch frustum cull for AABBs.
///
/// # Arguments
/// * `aabbs` — flat array of [min_x, min_y, min_z, max_x, max_y, max_z] per AABB
/// * `num_aabbs` — number of AABBs
///
/// Returns Vec<u8> where 1 = visible, 0 = culled.
pub fn batch_cull_aabbs(
    aabbs: &[f32],
    num_aabbs: usize,
    frustum: &Frustum,
) -> Vec<u8> {
    if num_aabbs == 0 {
        return Vec::new();
    }

    const AABB_STRIDE: usize = 6;

    if num_aabbs <= 128 {
        let mut results = vec![0u8; num_aabbs];
        for i in 0..num_aabbs {
            let base = i * AABB_STRIDE;
            results[i] = frustum.test_aabb(
                aabbs[base],
                aabbs[base + 1],
                aabbs[base + 2],
                aabbs[base + 3],
                aabbs[base + 4],
                aabbs[base + 5],
            ) as u8;
        }
        return results;
    }

    (0..num_aabbs)
        .into_par_iter()
        .map(|i| {
            let base = i * AABB_STRIDE;
            frustum.test_aabb(
                aabbs[base],
                aabbs[base + 1],
                aabbs[base + 2],
                aabbs[base + 3],
                aabbs[base + 4],
                aabbs[base + 5],
            ) as u8
        })
        .collect()
}

/// Build a frustum from camera parameters.
///
/// # Arguments
/// * `fov_y` — vertical field of view in radians
/// * `aspect` — aspect ratio (width / height)
/// * `near` — near clip plane distance
/// * `far` — far clip plane distance
/// * `camera_pos` — camera position [x, y, z]
/// * `camera_fwd` — camera forward vector (normalized) [x, y, z]
/// * `camera_up` — camera up vector (normalized) [x, y, z]
///
/// Returns a Frustum in world space.
pub fn frustum_from_camera(
    fov_y: f32,
    aspect: f32,
    near: f32,
    far: f32,
    camera_pos: [f32; 3],
    camera_fwd: [f32; 3],
    camera_up: [f32; 3],
) -> Frustum {
    let half_v = fov_y * 0.5;
    let sin_v = half_v.sin();
    let cos_v = half_v.cos();

    let tan_v = sin_v / cos_v;
    let nh = near * tan_v;
    let nw = nh * aspect;

    // Camera right vector
    let right = [
        camera_fwd[1] * camera_up[2] - camera_fwd[2] * camera_up[1],
        camera_fwd[2] * camera_up[0] - camera_fwd[0] * camera_up[2],
        camera_fwd[0] * camera_up[1] - camera_fwd[1] * camera_up[0],
    ];

    // Normalize right
    let rlen = (right[0] * right[0] + right[1] * right[1] + right[2] * right[2]).sqrt();
    let right = [right[0] / rlen, right[1] / rlen, right[2] / rlen];

    // Frustum corners on near plane
    let nc = [
        camera_pos[0] + camera_fwd[0] * near,
        camera_pos[1] + camera_fwd[1] * near,
        camera_pos[2] + camera_fwd[2] * near,
    ];

    let ntl = [
        nc[0] + camera_up[0] * nh - right[0] * nw,
        nc[1] + camera_up[1] * nh - right[1] * nw,
        nc[2] + camera_up[2] * nh - right[2] * nw,
    ];
    let ntr = [
        nc[0] + camera_up[0] * nh + right[0] * nw,
        nc[1] + camera_up[1] * nh + right[1] * nw,
        nc[2] + camera_up[2] * nh + right[2] * nw,
    ];
    let nbl = [
        nc[0] - camera_up[0] * nh - right[0] * nw,
        nc[1] - camera_up[1] * nh - right[1] * nw,
        nc[2] - camera_up[2] * nh - right[2] * nw,
    ];
    let nbr = [
        nc[0] - camera_up[0] * nh + right[0] * nw,
        nc[1] - camera_up[1] * nh + right[1] * nw,
        nc[2] - camera_up[2] * nh + right[2] * nw,
    ];

    let fc = [
        camera_pos[0] + camera_fwd[0] * far,
        camera_pos[1] + camera_fwd[1] * far,
        camera_pos[2] + camera_fwd[2] * far,
    ];

    // Build planes from 3 points each
    let mut planes = [[0.0f32; 4]; 6];

    // Near plane
    planes[PLANE_NEAR] = plane_from_points(camera_pos, ntl, ntr);
    // Far plane
    planes[PLANE_FAR] = plane_from_points(fc, nbr, nbl);
    // Left plane
    planes[PLANE_LEFT] = plane_from_points(camera_pos, nbl, ntl);
    // Right plane
    planes[PLANE_RIGHT] = plane_from_points(camera_pos, ntr, nbr);
    // Top plane
    planes[PLANE_TOP] = plane_from_points(camera_pos, ntl, nbl);
    // Bottom plane
    planes[PLANE_BOTTOM] = plane_from_points(camera_pos, nbr, ntr);

    Frustum { planes }
}

#[inline(always)]
fn plane_from_points(a: [f32; 3], b: [f32; 3], c: [f32; 3]) -> [f32; 4] {
    let ab = [b[0] - a[0], b[1] - a[1], b[2] - a[2]];
    let ac = [c[0] - a[0], c[1] - a[1], c[2] - a[2]];

    let nx = ab[1] * ac[2] - ab[2] * ac[1];
    let ny = ab[2] * ac[0] - ab[0] * ac[2];
    let nz = ab[0] * ac[1] - ab[1] * ac[0];

    let len = (nx * nx + ny * ny + nz * nz).sqrt();
    if len == 0.0 {
        return [0.0, 1.0, 0.0, 0.0];
    }

    let nx = nx / len;
    let ny = ny / len;
    let nz = nz / len;
    let d = -(nx * a[0] + ny * a[1] + nz * a[2]);

    [nx, ny, nz, d]
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_frustum_from_matrix() {
        // Simple orthographic matrix
        let m = [
            1.0, 0.0, 0.0, 0.0,
            0.0, 1.0, 0.0, 0.0,
            0.0, 0.0, 1.0, 0.0,
            0.0, 0.0, 0.0, 1.0,
        ];
        let frustum = Frustum::from_matrix(&m);
        // Origin should be inside
        assert!(frustum.test_point(0.0, 0.0, 0.0));
    }

    #[test]
    fn test_sphere_culling() {
        let frustum = Frustum {
            planes: [
                [1.0, 0.0, 0.0, 0.0],  // x >= 0
                [-1.0, 0.0, 0.0, 10.0], // x <= 10
                [0.0, 1.0, 0.0, 0.0],  // y >= 0
                [0.0, -1.0, 0.0, 10.0], // y <= 10
                [0.0, 0.0, 1.0, 0.0],  // z >= 0
                [0.0, 0.0, -1.0, 10.0], // z <= 10
            ],
        };

        // Sphere at center, radius 1
        assert!(frustum.test_sphere(5.0, 5.0, 5.0, 1.0));
        // Sphere outside
        assert!(!frustum.test_sphere(-5.0, 5.0, 5.0, 1.0));
        // Sphere touching boundary
        assert!(frustum.test_sphere(0.5, 5.0, 5.0, 1.0));
    }

    #[test]
    fn test_aabb_culling() {
        let frustum = Frustum {
            planes: [
                [1.0, 0.0, 0.0, 0.0],
                [-1.0, 0.0, 0.0, 10.0],
                [0.0, 1.0, 0.0, 0.0],
                [0.0, -1.0, 0.0, 10.0],
                [0.0, 0.0, 1.0, 0.0],
                [0.0, 0.0, -1.0, 10.0],
            ],
        };

        assert!(frustum.test_aabb(1.0, 1.0, 1.0, 2.0, 2.0, 2.0));
        assert!(!frustum.test_aabb(-5.0, 1.0, 1.0, -1.0, 2.0, 2.0));
    }

    #[test]
    fn test_batch_cull_spheres() {
        let centers = vec![
            5.0, 5.0, 5.0,
            -5.0, 5.0, 5.0,
        ];
        let radii = vec![1.0, 1.0];
        let frustum = Frustum {
            planes: [
                [1.0, 0.0, 0.0, 0.0],
                [-1.0, 0.0, 0.0, 10.0],
                [0.0, 1.0, 0.0, 0.0],
                [0.0, -1.0, 0.0, 10.0],
                [0.0, 0.0, 1.0, 0.0],
                [0.0, 0.0, -1.0, 10.0],
            ],
        };

        let results = batch_cull_spheres(&centers, &radii, 2, &frustum);
        assert_eq!(results[0], 1);
        assert_eq!(results[1], 0);
    }

    #[test]
    fn test_frustum_from_camera() {
        let frustum = frustum_from_camera(
            std::f32::consts::PI / 4.0, // 45 degree FOV
            16.0 / 9.0, // aspect ratio
            0.1,
            1000.0,
            [0.0, 0.0, 0.0],
            [0.0, 0.0, -1.0],
            [0.0, 1.0, 0.0],
        );

        // Something in front of camera should be visible
        assert!(frustum.test_point(0.0, 0.0, -10.0));
        // Something behind camera should be culled
        assert!(!frustum.test_point(0.0, 0.0, 10.0));
    }
}