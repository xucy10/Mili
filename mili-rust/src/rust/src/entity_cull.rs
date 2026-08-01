/// Entity Culling — optimized batch processing for Minecraft entity visibility.
///
/// Design goals:
/// - One JNI call per tick, not per entity
/// - Full 6-plane frustum AABB test (replaces simplified dot-product)
/// - Zero-copy via DirectByteBuffer
/// - Parallel processing for large batches
use rayon::prelude::*;

use crate::frustum::Frustum;

/// Packed entity data: each entity = 8 f32 values.
/// [min_x, min_y, min_z, max_x, max_y, max_z, entity_x, entity_z]
pub const ENTITY_STRIDE: usize = 8;

/// Result flags per entity.
pub const RESULT_VISIBLE: u8 = 0;
pub const RESULT_CULLED: u8 = 1;
pub const RESULT_TOO_FAR: u8 = 2;
pub const RESULT_TOO_BIG: u8 = 3;
pub const RESULT_BEHIND: u8 = 4;

/// Batch cull entities using flat packed data with full 6-plane frustum test.
///
/// # Arguments
/// * `entities` — flat array of entity AABBs, stride = 8 f32
/// * `num_entities` — number of entities
/// * `viewer_x/y/z` — viewer position (f64 for precision)
/// * `reach_sq` — squared reach distance
/// * `hitbox_limit` — max AABB dimension before marking "too big"
/// * `frustum` — 6-plane frustum for full AABB-vs-frustum test
pub fn batch_cull_entities(
    entities: &[f32],
    num_entities: usize,
    viewer_x: f64,
    viewer_y: f64,
    viewer_z: f64,
    reach_sq: f64,
    hitbox_limit: f32,
    frustum: &Frustum,
) -> Vec<u8> {
    if num_entities == 0 {
        return Vec::new();
    }

    if num_entities <= 64 {
        let mut results = vec![RESULT_VISIBLE; num_entities];
        for i in 0..num_entities {
            let base = i * ENTITY_STRIDE;
            results[i] = cull_single_entity(
                &entities[base..base + ENTITY_STRIDE],
                viewer_x,
                viewer_y,
                viewer_z,
                reach_sq,
                hitbox_limit,
                frustum,
            );
        }
        return results;
    }

    (0..num_entities)
        .into_par_iter()
        .map(|i| {
            let base = i * ENTITY_STRIDE;
            cull_single_entity(
                &entities[base..base + ENTITY_STRIDE],
                viewer_x,
                viewer_y,
                viewer_z,
                reach_sq,
                hitbox_limit,
                frustum,
            )
        })
        .collect()
}

#[inline(always)]
fn cull_single_entity(
    data: &[f32],
    viewer_x: f64,
    _viewer_y: f64,
    viewer_z: f64,
    reach_sq: f64,
    hitbox_limit: f32,
    frustum: &Frustum,
) -> u8 {
    let min_x = data[0];
    let min_y = data[1];
    let min_z = data[2];
    let max_x = data[3];
    let max_y = data[4];
    let max_z = data[5];
    let entity_x = data[6];
    let entity_z = data[7];

    // 1. Distance check — fast reject
    let dx = entity_x as f64 - viewer_x;
    let dz = entity_z as f64 - viewer_z;
    if dx * dx + dz * dz > reach_sq {
        return RESULT_TOO_FAR;
    }

    // 2. Hitbox size check
    let sx = max_x - min_x;
    let sy = max_y - min_y;
    let sz = max_z - min_z;
    if sx > hitbox_limit || sy > hitbox_limit || sz > hitbox_limit {
        return RESULT_TOO_BIG;
    }

    // 3. Full 6-plane frustum test — AABB vs frustum
    if !frustum.test_aabb(min_x, min_y, min_z, max_x, max_y, max_z) {
        return RESULT_BEHIND;
    }

    RESULT_VISIBLE
}

/// Zero-copy batch cull: reads entity data from a raw pointer (DirectByteBuffer).
///
/// # Safety
/// Caller must ensure `entity_ptr` points to at least `num_entities * ENTITY_STRIDE` f32 values.
pub fn batch_cull_entities_zero_copy(
    entity_ptr: *const f32,
    num_entities: usize,
    viewer_x: f64,
    viewer_y: f64,
    viewer_z: f64,
    reach_sq: f64,
    hitbox_limit: f32,
    frustum: &Frustum,
) -> Vec<u8> {
    if num_entities == 0 || entity_ptr.is_null() {
        return Vec::new();
    }
    let entities: &[f32] =
        unsafe { std::slice::from_raw_parts(entity_ptr, num_entities * ENTITY_STRIDE) };
    batch_cull_entities(
        entities,
        num_entities,
        viewer_x,
        viewer_y,
        viewer_z,
        reach_sq,
        hitbox_limit,
        frustum,
    )
}

#[cfg(test)]
mod tests {
    use super::*;

    fn make_frustum() -> Frustum {
        let mut planes = [[0.0f32; 4]; 6];
        planes[0] = [1.0, 0.0, 0.0, 1000.0]; // Left
        planes[1] = [-1.0, 0.0, 0.0, 1000.0]; // Right
        planes[2] = [0.0, 1.0, 0.0, 1000.0]; // Bottom
        planes[3] = [0.0, -1.0, 0.0, 1000.0]; // Top
        planes[4] = [0.0, 0.0, 1.0, 1000.0]; // Near
        planes[5] = [0.0, 0.0, -1.0, 1000.0]; // Far
        Frustum { planes }
    }

    #[test]
    fn test_visible() {
        let f = make_frustum();
        let e = [-1.0, -1.0, -1.0, 1.0, 1.0, 1.0, 0.0, 0.0];
        assert_eq!(
            batch_cull_entities(&e, 1, 0.0, 0.0, 0.0, 10000.0, 100.0, &f)[0],
            RESULT_VISIBLE
        );
    }

    #[test]
    fn test_too_far() {
        let f = make_frustum();
        let e = [999.0, 0.0, 999.0, 1001.0, 2.0, 1001.0, 1000.0, 1000.0];
        assert_eq!(
            batch_cull_entities(&e, 1, 0.0, 0.0, 0.0, 100.0, 100.0, &f)[0],
            RESULT_TOO_FAR
        );
    }

    #[test]
    fn test_too_big() {
        let f = make_frustum();
        let e = [-100.0, 0.0, 0.0, 100.0, 200.0, 0.0, 0.0, 0.0];
        assert_eq!(
            batch_cull_entities(&e, 1, 0.0, 0.0, 0.0, 100000.0, 50.0, &f)[0],
            RESULT_TOO_BIG
        );
    }

    #[test]
    fn test_behind_frustum() {
        let mut planes = [[0.0f32; 4]; 6];
        planes[0] = [1.0, 0.0, 0.0, 0.0]; // Left: x >= 0
        planes[1] = [-1.0, 0.0, 0.0, 1000.0];
        planes[2] = [0.0, 1.0, 0.0, 1000.0];
        planes[3] = [0.0, -1.0, 0.0, 1000.0];
        planes[4] = [0.0, 0.0, 1.0, 1000.0];
        planes[5] = [0.0, 0.0, -1.0, 1000.0];
        let f = Frustum { planes };
        let e = [-11.0, 0.0, 0.0, -9.0, 2.0, 0.0, -10.0, 0.0];
        assert_eq!(
            batch_cull_entities(&e, 1, 0.0, 0.0, 0.0, 100000.0, 100.0, &f)[0],
            RESULT_BEHIND
        );
    }

    #[test]
    fn test_empty() {
        let f = make_frustum();
        assert!(batch_cull_entities(&[], 0, 0.0, 0.0, 0.0, 100.0, 100.0, &f).is_empty());
    }
}
