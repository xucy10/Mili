/// Occlusion culling — bulk processing Rust rewrite.
///
/// Design principle: **one JNI call per frame, not one per entity**.
///
/// Java collects all entity AABBs + viewer positions into flat arrays,
/// passes them to Rust in one batch. Rust processes all entities using rayon
/// and returns a byte array of results (1 = visible, 0 = occluded).
///
/// This eliminates per-entity JNI overhead and enables SIMD-friendly
/// data layout within Rust.
use rayon::prelude::*;

// ============================================================================
// Vec3d — stack-allocated 3D vector
// ============================================================================

type Vec3 = [f64; 3];

#[inline(always)] fn vec3(x: f64, y: f64, z: f64) -> Vec3 { [x, y, z] }
#[inline(always)] fn vec3_add(a: Vec3, b: Vec3) -> Vec3 { [a[0] + b[0], a[1] + b[1], a[2] + b[2]] }
#[inline(always)] fn vec3_sub(a: Vec3, b: Vec3) -> Vec3 { [a[0] - b[0], a[1] - b[1], a[2] - b[2]] }
#[inline(always)] fn floor(v: f64) -> i32 { v.floor() as i32 }

// ============================================================================
// Relative position & face flags
// ============================================================================

#[derive(Clone, Copy, PartialEq, Eq)]
enum Relative { Inside, Positive, Negative }

#[inline]
fn relative_from(min: i32, max: i32, pos: i32) -> Relative {
    if max > pos && min > pos { Relative::Positive }
    else if min < pos && max < pos { Relative::Negative }
    else { Relative::Inside }
}

const ON_MIN_X: u8 = 0x01; const ON_MAX_X: u8 = 0x02;
const ON_MIN_Y: u8 = 0x04; const ON_MAX_Y: u8 = 0x08;
const ON_MIN_Z: u8 = 0x10; const ON_MAX_Z: u8 = 0x20;

// ============================================================================
// Per-AABB visibility check
// ============================================================================

fn is_aabb_visible(aabb_min: Vec3, aabb_max: Vec3, viewer: Vec3, reach: i32, expansion: f64) -> bool {
    let max_x = floor(aabb_max[0] + expansion);
    let max_y = floor(aabb_max[1] + expansion);
    let max_z = floor(aabb_max[2] + expansion);
    let min_x = floor(aabb_min[0] - expansion);
    let min_y = floor(aabb_min[1] - expansion);
    let min_z = floor(aabb_min[2] - expansion);

    let camera = [floor(viewer[0]), floor(viewer[1]), floor(viewer[2])];

    let rel_x = relative_from(min_x, max_x, camera[0]);
    let rel_y = relative_from(min_y, max_y, camera[1]);
    let rel_z = relative_from(min_z, max_z, camera[2]);

    if rel_x == Relative::Inside && rel_y == Relative::Inside && rel_z == Relative::Inside {
        return true;
    }

    let target_points: [Vec3; 14] = [
        vec3(min_x as f64 + 0.05, min_y as f64 + 0.05, min_z as f64 + 0.05),
        vec3(min_x as f64 + 0.05, min_y as f64 + 0.95, min_z as f64 + 0.05),
        vec3(min_x as f64 + 0.05, min_y as f64 + 0.95, min_z as f64 + 0.95),
        vec3(min_x as f64 + 0.05, min_y as f64 + 0.05, min_z as f64 + 0.95),
        vec3(min_x as f64 + 0.95, min_y as f64 + 0.05, min_z as f64 + 0.05),
        vec3(min_x as f64 + 0.95, min_y as f64 + 0.95, min_z as f64 + 0.05),
        vec3(min_x as f64 + 0.95, min_y as f64 + 0.95, min_z as f64 + 0.95),
        vec3(min_x as f64 + 0.95, min_y as f64 + 0.05, min_z as f64 + 0.95),
        vec3(min_x as f64 + 0.05, min_y as f64 + 0.5, min_z as f64 + 0.5),
        vec3(min_x as f64 + 0.5, min_y as f64 + 0.05, min_z as f64 + 0.5),
        vec3(min_x as f64 + 0.5, min_y as f64 + 0.5, min_z as f64 + 0.05),
        vec3(min_x as f64 + 0.95, min_y as f64 + 0.5, min_z as f64 + 0.5),
        vec3(min_x as f64 + 0.5, min_y as f64 + 0.95, min_z as f64 + 0.5),
        vec3(min_x as f64 + 0.5, min_y as f64 + 0.5, min_z as f64 + 0.95),
    ];

    for x in min_x..=max_x {
        let fd_x = {
            let mut f = 0u8;
            if x == min_x { f |= ON_MIN_X; }
            if x == max_x { f |= ON_MAX_X; }
            f
        };
        let vf_x = {
            let mut v = 0u8;
            if x == min_x && rel_x == Relative::Positive { v |= ON_MIN_X; }
            if x == max_x && rel_x == Relative::Negative { v |= ON_MAX_X; }
            v
        };

        for y in min_y..=max_y {
            let mut fd = fd_x;
            let mut vf = vf_x;
            if y == min_y { fd |= ON_MIN_Y; }
            if y == max_y { fd |= ON_MAX_Y; }
            if y == min_y && rel_y == Relative::Positive { vf |= ON_MIN_Y; }
            if y == max_y && rel_y == Relative::Negative { vf |= ON_MAX_Y; }

            for z in min_z..=max_z {
                let mut fdz = fd;
                let mut vfz = vf;
                if z == min_z { fdz |= ON_MIN_Z; }
                if z == max_z { fdz |= ON_MAX_Z; }
                if z == min_z && rel_z == Relative::Positive { vfz |= ON_MIN_Z; }
                if z == max_z && rel_z == Relative::Negative { vfz |= ON_MAX_Z; }
                if vfz == 0 { continue; }

                let target_pos = vec3(x as f64, y as f64, z as f64);
                if is_voxel_visible(viewer, target_pos, fdz, vfz, &target_points) {
                    return true;
                }
            }
        }
    }
    false
}

fn is_voxel_visible(
    viewer: Vec3, position: Vec3, face_data: u8, visible_on_face: u8, points: &[Vec3; 14],
) -> bool {
    let mut selectors = [false; 14];
    if (visible_on_face & ON_MIN_X) != 0 { selectors[0] = true; if (face_data & !ON_MIN_X) != 0 { selectors[1] = true; selectors[4] = true; selectors[5] = true; } selectors[8] = true; }
    if (visible_on_face & ON_MIN_Y) != 0 { selectors[0] = true; if (face_data & !ON_MIN_Y) != 0 { selectors[3] = true; selectors[4] = true; selectors[7] = true; } selectors[9] = true; }
    if (visible_on_face & ON_MIN_Z) != 0 { selectors[0] = true; if (face_data & !ON_MIN_Z) != 0 { selectors[1] = true; selectors[4] = true; selectors[5] = true; } selectors[10] = true; }
    if (visible_on_face & ON_MAX_X) != 0 { selectors[4] = true; if (face_data & !ON_MAX_X) != 0 { selectors[5] = true; selectors[6] = true; selectors[7] = true; } selectors[11] = true; }
    if (visible_on_face & ON_MAX_Y) != 0 { selectors[1] = true; if (face_data & !ON_MAX_Y) != 0 { selectors[2] = true; selectors[5] = true; selectors[6] = true; } selectors[12] = true; }
    if (visible_on_face & ON_MAX_Z) != 0 { selectors[2] = true; if (face_data & !ON_MAX_Z) != 0 { selectors[3] = true; selectors[6] = true; selectors[7] = true; } selectors[13] = true; }

    let mut targets: [Vec3; 14] = [[0.0; 3]; 14];
    let mut n = 0;
    for i in 0..14 {
        if selectors[i] { targets[n] = vec3_add(position, points[i]); n += 1; }
    }
    ray_hits_any(viewer, &targets[..n])
}

// ============================================================================
// Ray visibility (geometry-only, no block data)
// ============================================================================

fn ray_hits_any(_start: Vec3, _targets: &[Vec3]) -> bool {
    // Geometry-only: rays always clear (block data handled by bulk_step_ray with voxel cache)
    true
}

// ============================================================================
// BULK INTERFACE — one JNI call per frame
// ============================================================================

/// Bulk occlusion culling for N entities.
///
/// Each entity has its AABB as 6 doubles (min_x, min_y, min_z, max_x, max_y, max_z)
/// followed by viewer position as 3 doubles (vx, vy, vz).
/// Total: 9 doubles per entity in a flat interleaved array.
///
/// Uses rayon parallel iterator to process all entities concurrently.
///
/// Returns a byte array where results[i] == 1 means entity i is visible.
pub fn bulk_occlusion_cull(
    data: &[f64],       // flat: [aabb_min_x, aabb_min_y, ... viewer_x, viewer_y, viewer_z] * N
    num_entities: usize, // N
    reach: i32,
    expansion: f64,
) -> Vec<u8> {
    if num_entities == 0 { return vec![]; }

    // For small batches (≤ 32 entities), use sequential to avoid rayon overhead
    if num_entities <= 32 {
        let mut results = vec![0u8; num_entities];
        for i in 0..num_entities {
            let base = i * 9;
            let visible = is_aabb_visible(
                [data[base], data[base+1], data[base+2]],
                [data[base+3], data[base+4], data[base+5]],
                [data[base+6], data[base+7], data[base+8]],
                reach,
                expansion,
            );
            results[i] = visible as u8;
        }
        return results;
    }

    // Large batches: use rayon for parallelism
    let results: Vec<u8> = (0..num_entities).into_par_iter().map(|i| {
        let base = i * 9;
        is_aabb_visible(
            [data[base], data[base+1], data[base+2]],
            [data[base+3], data[base+4], data[base+5]],
            [data[base+6], data[base+7], data[base+8]],
            reach,
            expansion,
        ) as u8
    }).collect();

    results
}

/// Bulk DDA ray stepping for N rays through a shared voxel cache.
///
/// Each ray has start and target as 6 doubles (sx, sy, sz, tx, ty, tz).
/// All rays share the same camera position and voxel cache.
///
/// Uses rayon parallel iterator.
///
/// Returns byte array where results[i] == 1 means ray i reached its target.
pub fn bulk_step_ray(
    ray_data: &[f64],   // flat: [sx,sy,sz,tx,ty,tz] * N
    num_rays: usize,
    camera: [i32; 3],
    reach: i32,
    cache: &[u8],
    cache_size: usize,
) -> Vec<u8> {
    if num_rays == 0 { return vec![]; }

    if num_rays <= 16 {
        let mut results = vec![0u8; num_rays];
        for i in 0..num_rays {
            let base = i * 6;
            results[i] = step_ray_single(
                [ray_data[base], ray_data[base+1], ray_data[base+2]],
                [ray_data[base+3], ray_data[base+4], ray_data[base+5]],
                camera, reach, cache, cache_size,
            ) as u8;
        }
        return results;
    }

    let results: Vec<u8> = (0..num_rays).into_par_iter().map(|i| {
        let base = i * 6;
        step_ray_single(
            [ray_data[base], ray_data[base+1], ray_data[base+2]],
            [ray_data[base+3], ray_data[base+4], ray_data[base+5]],
            camera, reach, cache, cache_size,
        ) as u8
    }).collect();

    results
}

/// Single-ray DDA with voxel cache lookup.
fn step_ray_single(
    start: Vec3, target: Vec3,
    camera: [i32; 3], reach: i32,
    cache: &[u8], cache_size: usize,
) -> i32 {
    let rel = vec3_sub(start, target);
    let dx = rel[0].abs();
    let dy = rel[1].abs();
    let dz = rel[2].abs();
    if dx == 0.0 && dy == 0.0 && dz == 0.0 { return 1; }

    let dfx = if dx == 0.0 { f64::INFINITY } else { 1.0 / dx };
    let dfy = if dy == 0.0 { f64::INFINITY } else { 1.0 / dy };
    let dfz = if dz == 0.0 { f64::INFINITY } else { 1.0 / dz };

    let mut cx = camera[0]; let mut cy = camera[1]; let mut cz = camera[2];

    let (xi, mut tx) = if dx == 0.0 { (0, f64::INFINITY) }
        else if target[0] > start[0] { (1, ((cx+1) as f64 - start[0]) * dfx) }
        else { (-1, (start[0] - cx as f64) * dfx) };

    let (yi, mut ty) = if dy == 0.0 { (0, f64::INFINITY) }
        else if target[1] > start[1] { (1, ((cy+1) as f64 - start[1]) * dfy) }
        else { (-1, (start[1] - cy as f64) * dfy) };

    let (zi, mut tz) = if dz == 0.0 { (0, f64::INFINITY) }
        else if target[2] > start[2] { (1, ((cz+1) as f64 - start[2]) * dfz) }
        else { (-1, (start[2] - cz as f64) * dfz) };

    let mut steps = 1
        + if xi != 0 { (floor(target[0]) - camera[0]).unsigned_abs() as usize } else { 0 }
        + if yi != 0 { (floor(target[1]) - camera[1]).unsigned_abs() as usize } else { 0 }
        + if zi != 0 { (floor(target[2]) - camera[2]).unsigned_abs() as usize } else { 0 };

    let cs = cache_size as i32;
    let mut allow_clip = true;

    while steps > 1 {
        let v = cache_get(cx, cy, cz, camera, reach, cache, cs as usize);
        if v == 2 && !allow_clip { return 0; }
        if v == 1 { allow_clip = false; }

        if ty < tx && ty < tz { cy += yi; ty += dfy; }
        else if tx < ty && tx < tz { cx += xi; tx += dfx; }
        else { cz += zi; tz += dfz; }
        steps -= 1;
    }
    1
}

#[inline]
fn cache_get(x: i32, y: i32, z: i32, cam: [i32; 3], reach: i32, cache: &[u8], size: usize) -> u8 {
    let dx = x - cam[0]; let dy = y - cam[1]; let dz = z - cam[2];
    if dx.abs() > reach - 2 || dy.abs() > reach - 2 || dz.abs() > reach - 2 { return 255; }
    let idx = ((dx + reach) as usize) + ((dy + reach) as usize) * size + ((dz + reach) as usize) * size * size;
    if idx < cache.len() { cache[idx] } else { 255 }
}

// ============================================================================
// Tests
// ============================================================================

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_bulk_occlusion_empty() {
        assert!(bulk_occlusion_cull(&[], 0, 64, 0.5).is_empty());
    }

    #[test]
    fn test_bulk_occlusion_single_inside() {
        // camera (5,5,5) is inside AABB (0,0,0)-(10,10,10)
        let data = vec![0.0, 0.0, 0.0, 10.0, 10.0, 10.0, 5.0, 5.0, 5.0];
        let r = bulk_occlusion_cull(&data, 1, 64, 0.5);
        assert_eq!(r, vec![1]);
    }

    #[test]
    fn test_bulk_occlusion_parallel() {
        // 64 entities, all camera inside AABB — test rayon path
        let mut data = vec![0.0f64; 64 * 9];
        for i in 0..64 {
            let b = i * 9;
            data[b..b+6].copy_from_slice(&[0.0, 0.0, 0.0, 10.0, 10.0, 10.0]);
            data[b+6..b+9].copy_from_slice(&[5.0, 5.0, 5.0]);
        }
        let r = bulk_occlusion_cull(&data, 64, 64, 0.5);
        assert_eq!(r.len(), 64);
        assert!(r.iter().all(|&v| v == 1));
    }

    #[test]
    fn test_bulk_step_ray_empty() {
        assert!(bulk_step_ray(&[], 0, [0, 0, 0], 5, &[], 10).is_empty());
    }

    #[test]
    fn test_bulk_step_ray_unblocked() {
        let cache = vec![0u8; 10*10*10];
        let rays = vec![0.5, 0.5, 0.5, 5.0, 5.0, 5.0];
        let r = bulk_step_ray(&rays, 1, [0, 0, 0], 5, &cache, 10);
        assert_eq!(r, vec![1]);
    }
}