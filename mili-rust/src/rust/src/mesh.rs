/// Mesh optimization — vertex data processing for chunk rendering.
///
/// Minecraft's chunk meshing produces large vertex buffers with redundant data.
/// This module provides fast paths for:
/// - Vertex deduplication (merge identical vertices)
/// - Face culling preprocessing
/// - Buffer compaction
/// - Normal/tangent recomputation
use rayon::prelude::*;

/// Packed vertex format used by Minecraft's chunk renderer.
/// Each vertex is 28 bytes (7 floats):
///   [x, y, z, u, v, color, light] — all as f32 for GPU consumption
pub const VERTEX_STRIDE: usize = 7;

/// Packed face format: 4 vertices + normal + material id
pub const FACE_STRIDE_VERTICES: usize = 4 * VERTEX_STRIDE; // 28 floats per face

/// Result of mesh optimization.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct MeshStats {
    pub original_vertices: usize,
    pub deduplicated_vertices: usize,
    pub culled_faces: usize,
    pub remaining_faces: usize,
}

/// Deduplicate vertices in a chunk mesh using a spatial hash.
///
/// # Arguments
/// * `vertices` — flat array of vertex data, stride = VERTEX_STRIDE (7 f32)
/// * `num_vertices` — number of vertices
///
/// Returns `(deduplicated_vertices, index_remap)` where `index_remap[old_idx] = new_idx`.
pub fn deduplicate_vertices(vertices: &[f32], num_vertices: usize) -> (Vec<f32>, Vec<u32>) {
    if num_vertices == 0 {
        return (Vec::new(), Vec::new());
    }

    // For small meshes, sequential is faster
    if num_vertices <= 256 {
        return deduplicate_vertices_sequential(vertices, num_vertices);
    }

    deduplicate_vertices_parallel(vertices, num_vertices)
}

fn deduplicate_vertices_sequential(vertices: &[f32], num_vertices: usize) -> (Vec<f32>, Vec<u32>) {
    let mut remap = Vec::with_capacity(num_vertices);
    let mut unique = Vec::with_capacity(num_vertices * VERTEX_STRIDE / 2);
    let mut seen = std::collections::HashMap::with_capacity(num_vertices);

    for i in 0..num_vertices {
        let base = i * VERTEX_STRIDE;
        let key = vertex_hash(&vertices[base..base + VERTEX_STRIDE]);

        let new_idx = *seen.entry(key).or_insert_with(|| {
            let idx = unique.len() / VERTEX_STRIDE;
            unique.extend_from_slice(&vertices[base..base + VERTEX_STRIDE]);
            idx as u32
        });
        remap.push(new_idx);
    }

    (unique, remap)
}

fn deduplicate_vertices_parallel(vertices: &[f32], num_vertices: usize) -> (Vec<f32>, Vec<u32>) {
    // Phase 1: compute hashes in parallel
    let hashes: Vec<u64> = (0..num_vertices)
        .into_par_iter()
        .map(|i| {
            let base = i * VERTEX_STRIDE;
            vertex_hash(&vertices[base..base + VERTEX_STRIDE])
        })
        .collect();

    // Phase 2: sequential deduplication (hash map is not thread-safe)
    let mut remap = Vec::with_capacity(num_vertices);
    let mut unique = Vec::with_capacity(num_vertices * VERTEX_STRIDE / 2);
    let mut seen = std::collections::HashMap::with_capacity(num_vertices);

    for i in 0..num_vertices {
        let new_idx = *seen.entry(hashes[i]).or_insert_with(|| {
            let idx = unique.len() / VERTEX_STRIDE;
            let base = i * VERTEX_STRIDE;
            unique.extend_from_slice(&vertices[base..base + VERTEX_STRIDE]);
            idx as u32
        });
        remap.push(new_idx);
    }

    (unique, remap)
}

#[inline(always)]
fn vertex_hash(v: &[f32]) -> u64 {
    // Hash based on position (x, y, z) and UV (u, v) only
    // Color and light can vary for the same geometry
    let x = v[0].to_bits() as u64;
    let y = v[1].to_bits() as u64;
    let z = v[2].to_bits() as u64;
    let u = v[3].to_bits() as u64;
    let v_coord = v[4].to_bits() as u64;

    // FNV-1a-like hash
    let mut h = 0xcbf29ce484222325u64;
    h ^= x;
    h = h.wrapping_mul(0x100000001b3);
    h ^= y;
    h = h.wrapping_mul(0x100000001b3);
    h ^= z;
    h = h.wrapping_mul(0x100000001b3);
    h ^= u;
    h = h.wrapping_mul(0x100000001b3);
    h ^= v_coord;
    h = h.wrapping_mul(0x100000001b3);
    h
}

/// Precompute face visibility for a set of axis-aligned faces.
///
/// Each face is defined by 4 vertices (quad). Faces with all vertices
/// outside the view frustum are marked for culling.
///
/// # Arguments
/// * `face_data` — flat array of face vertices, 4 * VERTEX_STRIDE per face
/// * `num_faces` — number of faces
/// * `frustum_planes` — 6 frustum planes, each as [nx, ny, nz, d] (f32)
///
/// Returns a Vec<bool> where true means the face is visible.
pub fn cull_faces_frustum(
    face_data: &[f32],
    num_faces: usize,
    frustum_planes: &[[f32; 4]; 6],
) -> Vec<bool> {
    if num_faces == 0 {
        return Vec::new();
    }

    if num_faces <= 128 {
        let mut results = Vec::with_capacity(num_faces);
        for i in 0..num_faces {
            let base = i * 4 * VERTEX_STRIDE;
            results.push(is_face_visible(&face_data[base..base + 4 * VERTEX_STRIDE], frustum_planes));
        }
        return results;
    }

    (0..num_faces)
        .into_par_iter()
        .map(|i| {
            let base = i * 4 * VERTEX_STRIDE;
            is_face_visible(&face_data[base..base + 4 * VERTEX_STRIDE], frustum_planes)
        })
        .collect()
}

#[inline(always)]
fn is_face_visible(face: &[f32], planes: &[[f32; 4]; 6]) -> bool {
    // A face is visible if ANY of its vertices is inside ALL frustum planes
    for v_idx in 0..4 {
        let vx = face[v_idx * VERTEX_STRIDE];
        let vy = face[v_idx * VERTEX_STRIDE + 1];
        let vz = face[v_idx * VERTEX_STRIDE + 2];

        let mut inside_all = true;
        for plane in planes {
            let dist = vx * plane[0] + vy * plane[1] + vz * plane[2] + plane[3];
            if dist < 0.0 {
                inside_all = false;
                break;
            }
        }

        if inside_all {
            return true;
        }
    }

    false
}

/// Compact a mesh by removing culled faces and remapping indices.
///
/// # Arguments
/// * `vertices` — flat vertex array
/// * `indices` — index buffer (triangles, 3 indices per face)
/// * `face_visible` — bool per face
///
/// Returns `(compact_vertices, compact_indices, stats)`.
pub fn compact_mesh(
    vertices: &[f32],
    indices: &[u32],
    face_visible: &[bool],
) -> (Vec<f32>, Vec<u32>, MeshStats) {
    let num_faces = face_visible.len();
    let num_vertices = vertices.len() / VERTEX_STRIDE;

    if num_faces == 0 {
        return (
            vertices.to_vec(),
            indices.to_vec(),
            MeshStats {
                original_vertices: num_vertices,
                deduplicated_vertices: num_vertices,
                culled_faces: 0,
                remaining_faces: 0,
            },
        );
    }

    // Count remaining faces
    let remaining_faces = face_visible.iter().filter(|&&v| v).count();
    let culled_faces = num_faces - remaining_faces;

    // Build new index buffer with only visible faces
    let mut new_indices = Vec::with_capacity(remaining_faces * 3);
    let mut vertex_used = vec![false; num_vertices];

    for (face_idx, &visible) in face_visible.iter().enumerate() {
        if !visible {
            continue;
        }
        let idx_base = face_idx * 3;
        for j in 0..3 {
            let vi = indices[idx_base + j] as usize;
            vertex_used[vi] = true;
            new_indices.push(vi as u32);
        }
    }

    // Remap vertices: only keep used ones
    let mut remap = vec![u32::MAX; num_vertices];
    let mut new_vertices = Vec::with_capacity(num_vertices * VERTEX_STRIDE);
    let mut new_idx = 0u32;

    for (old_idx, &used) in vertex_used.iter().enumerate() {
        if used {
            remap[old_idx] = new_idx;
            let base = old_idx * VERTEX_STRIDE;
            new_vertices.extend_from_slice(&vertices[base..base + VERTEX_STRIDE]);
            new_idx += 1;
        }
    }

    // Remap indices
    for idx in &mut new_indices {
        *idx = remap[*idx as usize];
    }

    let stats = MeshStats {
        original_vertices: num_vertices,
        deduplicated_vertices: new_idx as usize,
        culled_faces,
        remaining_faces,
    };

    (new_vertices, new_indices, stats)
}

/// Batch process multiple chunk meshes for visibility.
///
/// This is the main entry point for Java: pass all chunk section meshes
/// at once, get back visibility flags for each.
///
/// # Arguments
/// * `mesh_data` — flat array of all chunk section bounding boxes
///   Each section: [min_x, min_y, min_z, max_x, max_y, max_z] as f32
/// * `num_sections` — number of chunk sections
/// * `frustum_planes` — 6 frustum planes [nx, ny, nz, d]
///
/// Returns Vec<u8> where 1 = visible, 0 = culled.
pub fn batch_cull_chunk_sections(
    mesh_data: &[f32],
    num_sections: usize,
    frustum_planes: &[[f32; 4]; 6],
) -> Vec<u8> {
    if num_sections == 0 {
        return Vec::new();
    }

    const SECTION_STRIDE: usize = 6; // min_x, min_y, min_z, max_x, max_y, max_z

    if num_sections <= 64 {
        let mut results = vec![0u8; num_sections];
        for i in 0..num_sections {
            let base = i * SECTION_STRIDE;
            results[i] = is_aabb_in_frustum(
                mesh_data[base],
                mesh_data[base + 1],
                mesh_data[base + 2],
                mesh_data[base + 3],
                mesh_data[base + 4],
                mesh_data[base + 5],
                frustum_planes,
            ) as u8;
        }
        return results;
    }

    (0..num_sections)
        .into_par_iter()
        .map(|i| {
            let base = i * SECTION_STRIDE;
            is_aabb_in_frustum(
                mesh_data[base],
                mesh_data[base + 1],
                mesh_data[base + 2],
                mesh_data[base + 3],
                mesh_data[base + 4],
                mesh_data[base + 5],
                frustum_planes,
            ) as u8
        })
        .collect()
}

#[inline(always)]
fn is_aabb_in_frustum(
    min_x: f32,
    min_y: f32,
    min_z: f32,
    max_x: f32,
    max_y: f32,
    max_z: f32,
    planes: &[[f32; 4]; 6],
) -> bool {
    // Test AABB against each frustum plane
    // If the AABB is entirely outside ANY plane, it's culled
    for plane in planes {
        // Find the vertex of the AABB that is most opposite to the plane normal
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

/// Compute face normals for a set of quads.
///
/// # Arguments
/// * `face_data` — flat array of face vertices, 4 * VERTEX_STRIDE per face
/// * `num_faces` — number of faces
///
/// Returns Vec<[f32; 3]> with one normal per face.
pub fn compute_face_normals(face_data: &[f32], num_faces: usize) -> Vec<[f32; 3]> {
    let mut normals = Vec::with_capacity(num_faces);

    for i in 0..num_faces {
        let base = i * 4 * VERTEX_STRIDE;
        let v0 = [face_data[base], face_data[base + 1], face_data[base + 2]];
        let v1 = [face_data[base + VERTEX_STRIDE], face_data[base + VERTEX_STRIDE + 1], face_data[base + VERTEX_STRIDE + 2]];
        let v2 = [face_data[base + 2 * VERTEX_STRIDE], face_data[base + 2 * VERTEX_STRIDE + 1], face_data[base + 2 * VERTEX_STRIDE + 2]];

        let e1 = [v1[0] - v0[0], v1[1] - v0[1], v1[2] - v0[2]];
        let e2 = [v2[0] - v0[0], v2[1] - v0[1], v2[2] - v0[2]];

        let nx = e1[1] * e2[2] - e1[2] * e2[1];
        let ny = e1[2] * e2[0] - e1[0] * e2[2];
        let nz = e1[0] * e2[1] - e1[1] * e2[0];

        let len = (nx * nx + ny * ny + nz * nz).sqrt();
        if len > 0.0 {
            normals.push([nx / len, ny / len, nz / len]);
        } else {
            normals.push([0.0, 1.0, 0.0]);
        }
    }

    normals
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_empty_mesh() {
        let (verts, remap) = deduplicate_vertices(&[], 0);
        assert!(verts.is_empty());
        assert!(remap.is_empty());
    }

    #[test]
    fn test_deduplicate_simple() {
        // 2 identical vertices
        let verts = vec![
            0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 1.0, // v0
            1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 1.0, // v1
            0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 1.0, // v2 = v0
        ];
        let (unique, remap) = deduplicate_vertices(&verts, 3);
        assert_eq!(unique.len() / VERTEX_STRIDE, 2); // v0 and v1
        assert_eq!(remap, vec![0, 1, 0]);
    }

    #[test]
    fn test_frustum_cull_aabb() {
        // Simple frustum: everything in front of z=0 plane
        let planes = [
            [0.0, 0.0, 1.0, 0.0], // near plane at z=0
            [0.0, 0.0, -1.0, 100.0], // far plane at z=100
            [1.0, 0.0, 0.0, 50.0], // left plane
            [-1.0, 0.0, 0.0, 50.0], // right plane
            [0.0, 1.0, 0.0, 50.0], // bottom plane
            [0.0, -1.0, 0.0, 50.0], // top plane
        ];

        // AABB at z=10 (visible)
        assert!(is_aabb_in_frustum(0.0, 0.0, 10.0, 1.0, 1.0, 11.0, &planes));
        // AABB at z=-10 (culled)
        assert!(!is_aabb_in_frustum(0.0, 0.0, -10.0, 1.0, 1.0, -9.0, &planes));
    }

    #[test]
    fn test_batch_cull_sections() {
        let sections = vec![
            0.0, 0.0, 0.0, 16.0, 16.0, 16.0,   // section 0: visible
            0.0, 0.0, -100.0, 16.0, 16.0, -84.0, // section 1: behind
        ];
        let planes = [
            [0.0, 0.0, 1.0, 0.0],
            [0.0, 0.0, -1.0, 100.0],
            [1.0, 0.0, 0.0, 50.0],
            [-1.0, 0.0, 0.0, 50.0],
            [0.0, 1.0, 0.0, 50.0],
            [0.0, -1.0, 0.0, 50.0],
        ];
        let results = batch_cull_chunk_sections(&sections, 2, &planes);
        assert_eq!(results[0], 1);
        assert_eq!(results[1], 0);
    }

    #[test]
    fn test_compact_mesh() {
        let vertices = vec![
            0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 1.0, // v0
            1.0, 0.0, 0.0, 1.0, 0.0, 1.0, 1.0, // v1
            1.0, 1.0, 0.0, 1.0, 1.0, 1.0, 1.0, // v2
            0.0, 1.0, 0.0, 0.0, 1.0, 1.0, 1.0, // v3
        ];
        let indices = vec![0, 1, 2, 0, 2, 3]; // 2 triangles = 1 quad
        let face_visible = vec![true, false]; // first face visible, second culled

        let (new_verts, new_indices, stats) = compact_mesh(&vertices, &indices, &face_visible);
        assert_eq!(stats.remaining_faces, 1);
        assert_eq!(stats.culled_faces, 1);
        assert_eq!(new_indices.len(), 3); // 1 face = 3 indices
    }

    #[test]
    fn test_compute_normals() {
        let face = vec![
            0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 1.0,
            1.0, 0.0, 0.0, 1.0, 0.0, 1.0, 1.0,
            1.0, 0.0, 1.0, 1.0, 1.0, 1.0, 1.0,
            0.0, 0.0, 1.0, 0.0, 1.0, 1.0, 1.0,
        ];
        let normals = compute_face_normals(&face, 1);
        assert_eq!(normals.len(), 1);
        // This face is in the XZ plane, normal should point in +Y or -Y
        assert!((normals[0][1]).abs() > 0.9);
    }
}