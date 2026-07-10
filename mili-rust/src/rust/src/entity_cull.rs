/// Entity Culling — optimized batch processing for Minecraft entity visibility.
///
/// This module provides a specialized fast path for the entity culling task
/// that processes hundreds of entities per player per frame.
///
/// Design goals:
/// - Minimize JNI call overhead (one call per frame, not per entity)
/// - Use SIMD-friendly flat arrays
/// - Fast path for common cases (entities behind camera, too far, etc.)
/// - Parallel processing for large entity counts
use rayon::prelude::*;

/// Packed entity data for batch culling.
/// Each entity occupies 8 f32 values:
///   [min_x, min_y, min_z, max_x, max_y, max_z, entity_x, entity_z]
/// The viewer position is passed separately.
///
/// Using f32 instead of f64 halves memory bandwidth and is sufficient
/// for culling decisions (Minecraft coordinates are block-aligned).
pub const ENTITY_STRIDE: usize = 8;

/// Result flags per entity.
pub const RESULT_VISIBLE: u8 = 0;
pub const RESULT_CULLED: u8 = 1;
pub const RESULT_TOO_FAR: u8 = 2;   // beyond distance threshold
pub const RESULT_TOO_BIG: u8 = 3;   // hitbox exceeds limit
pub const RESULT_BEHIND: u8 = 4;    // behind camera (dot product)

/// Batch cull entities using flat packed data.
///
/// # Arguments
/// * `entities` — flat array of entity AABBs, stride = 8 f32
/// * `num_entities` — number of entities
/// * `viewer_x`, `viewer_y`, `viewer_z` — viewer position (f64 for precision)
/// * `reach_sq` — squared reach distance (entities beyond this are "too far")
/// * `hitbox_limit` — max AABB dimension before marking "too big"
/// * `camera_fwd_x`, `camera_fwd_y`, `camera_fwd_z` — camera forward vector (normalized)
/// * `fov_cos` — cosine of half-FOV for frustum culling
///
/// Returns a Vec<u8> where each byte is a RESULT_* flag.
pub fn batch_cull_entities(
    entities: &[f32],
    num_entities: usize,
    viewer_x: f64,
    viewer_y: f64,
    viewer_z: f64,
    reach_sq: f64,
    hitbox_limit: f32,
    camera_fwd_x: f32,
    camera_fwd_y: f32,
    camera_fwd_z: f32,
    fov_cos: f32,
) -> Vec<u8> {
    if num_entities == 0 {
        return Vec::new();
    }

    // For small batches, sequential is faster (avoids rayon overhead)
    if num_entities <= 64 {
        let mut results = vec![RESULT_VISIBLE; num_entities];
        for i in 0..num_entities {
            let base = i * ENTITY_STRIDE;
            results[i] = cull_single_entity(
                &entities[base..base + ENTITY_STRIDE],
                viewer_x, viewer_y, viewer_z,
                reach_sq, hitbox_limit,
                camera_fwd_x, camera_fwd_y, camera_fwd_z,
                fov_cos,
            );
        }
        return results;
    }

    // Large batches: parallel processing
    (0..num_entities)
        .into_par_iter()
        .map(|i| {
            let base = i * ENTITY_STRIDE;
            cull_single_entity(
                &entities[base..base + ENTITY_STRIDE],
                viewer_x, viewer_y, viewer_z,
                reach_sq, hitbox_limit,
                camera_fwd_x, camera_fwd_y, camera_fwd_z,
                fov_cos,
            )
        })
        .collect()
}

#[inline(always)]
fn cull_single_entity(
    data: &[f32],
    viewer_x: f64,
    viewer_y: f64,
    viewer_z: f64,
    reach_sq: f64,
    hitbox_limit: f32,
    camera_fwd_x: f32,
    camera_fwd_y: f32,
    camera_fwd_z: f32,
    fov_cos: f32,
) -> u8 {
    let min_x = data[0];
    let min_y = data[1];
    let min_z = data[2];
    let max_x = data[3];
    let max_y = data[4];
    let max_z = data[5];
    let entity_x = data[6];
    let entity_z = data[7];

    // 1. Distance check (cheap, eliminates most entities)
    let dx = entity_x as f64 - viewer_x;
    let dz = entity_z as f64 - viewer_z;
    let dist_sq = dx * dx + dz * dz;
    if dist_sq > reach_sq {
        return RESULT_TOO_FAR;
    }

    // 2. Hitbox size check
    let sx = max_x - min_x;
    let sy = max_y - min_y;
    let sz = max_z - min_z;
    if sx > hitbox_limit || sy > hitbox_limit || sz > hitbox_limit {
        return RESULT_TOO_BIG;
    }

    // 3. Frustum culling (dot product with camera forward)
    // Use entity center for the test
    let center_x = (min_x + max_x) * 0.5;
    let center_y = (min_y + max_y) * 0.5;
    let center_z = (min_z + max_z) * 0.5;

    let to_entity_x = center_x - viewer_x as f32;
    let to_entity_y = center_y - viewer_y as f32;
    let to_entity_z = center_z - viewer_z as f32;

    // Normalize
    let len_sq = to_entity_x * to_entity_x + to_entity_y * to_entity_y + to_entity_z * to_entity_z;
    if len_sq > 0.0 {
        let len = len_sq.sqrt();
        let dot = (to_entity_x * camera_fwd_x + to_entity_y * camera_fwd_y + to_entity_z * camera_fwd_z) / len;
        if dot < fov_cos {
            return RESULT_BEHIND;
        }
    }

    // 4. Entity is potentially visible — caller should do raycast if needed
    RESULT_VISIBLE
}

/// Build flat entity data from individual arrays.
///
/// This is a helper that Java can call via JNI to avoid building the flat
/// array on the Java side.
pub fn pack_entity_data(
    min_x: &[f32],
    min_y: &[f32],
    min_z: &[f32],
    max_x: &[f32],
    max_y: &[f32],
    max_z: &[f32],
    pos_x: &[f32],
    pos_z: &[f32],
) -> Vec<f32> {
    let n = min_x.len().min(min_y.len()).min(min_z.len())
        .min(max_x.len()).min(max_y.len()).min(max_z.len())
        .min(pos_x.len()).min(pos_z.len());

    let mut packed = Vec::with_capacity(n * ENTITY_STRIDE);
    for i in 0..n {
        packed.push(min_x[i]);
        packed.push(min_y[i]);
        packed.push(min_z[i]);
        packed.push(max_x[i]);
        packed.push(max_y[i]);
        packed.push(max_z[i]);
        packed.push(pos_x[i]);
        packed.push(pos_z[i]);
    }
    packed
}

/// Count results by category.
pub fn count_results(results: &[u8]) -> [usize; 5] {
    let mut counts = [0usize; 5];
    for &r in results {
        if (r as usize) < counts.len() {
            counts[r as usize] += 1;
        }
    }
    counts
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_empty_batch() {
        let results = batch_cull_entities(&[], 0, 0.0, 0.0, 0.0, 1000.0, 10.0, 0.0, 0.0, 1.0, -1.0);
        assert!(results.is_empty());
    }

    #[test]
    fn test_too_far_culling() {
        // Entity at (100, 0, 0), viewer at (0, 0, 0), reach = 50
        let entities = vec![
            99.0, 0.0, -0.5, 101.0, 2.0, 0.5,  // AABB
            100.0, 0.0,                          // pos_x, pos_z
        ];
        let results = batch_cull_entities(&entities, 1, 0.0, 0.0, 0.0, 50.0 * 50.0, 10.0, 0.0, 0.0, 1.0, -1.0);
        assert_eq!(results[0], RESULT_TOO_FAR);
    }

    #[test]
    fn test_visible_entity() {
        // Entity right in front of viewer
        let entities = vec![
            -0.5, 0.0, -0.5, 0.5, 2.0, 0.5,
            0.0, 5.0,
        ];
        let results = batch_cull_entities(&entities, 1, 0.0, 0.0, 0.0, 1000.0, 10.0, 0.0, 0.0, 1.0, 0.0);
        assert_eq!(results[0], RESULT_VISIBLE);
    }

    #[test]
    fn test_parallel_batch() {
        // 128 entities, all too far — test parallel path
        let mut entities = Vec::with_capacity(128 * ENTITY_STRIDE);
        for i in 0..128 {
            let x = (i as f32) * 10.0;
            entities.extend_from_slice(&[
                x, 0.0, 0.0, x + 1.0, 2.0, 1.0,
                x + 0.5, 0.5,
            ]);
        }
        let results = batch_cull_entities(&entities, 128, 0.0, 0.0, 0.0, 5.0, 10.0, 0.0, 0.0, 1.0, -1.0);
        assert_eq!(results.len(), 128);
        // First entity at x=0.5 is within reach=5, rest are too far
        assert_eq!(results[0], RESULT_VISIBLE);
        for i in 1..128 {
            assert_eq!(results[i], RESULT_TOO_FAR, "entity {} should be too far", i);
        }
    }

    #[test]
    fn test_count_results() {
        let results = vec![RESULT_VISIBLE, RESULT_CULLED, RESULT_TOO_FAR, RESULT_TOO_BIG, RESULT_BEHIND];
        let counts = count_results(&results);
        assert_eq!(counts[RESULT_VISIBLE as usize], 1);
        assert_eq!(counts[RESULT_CULLED as usize], 1);
        assert_eq!(counts[RESULT_TOO_FAR as usize], 1);
        assert_eq!(counts[RESULT_TOO_BIG as usize], 1);
        assert_eq!(counts[RESULT_BEHIND as usize], 1);
    }
}