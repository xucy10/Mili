/// Lighting computation — fast light level calculations for chunk rendering.
///
/// Minecraft's lighting system uses two light values per block:
/// - Sky light (0-15): light from the sky
/// - Block light (0-15): light from torches, glowstone, etc.
///
/// The final light level is max(sky_light, block_light).
///
/// This module provides:
/// - Bulk light level computation
/// - Lightmap texture generation
/// - AO (ambient occlusion) precomputation
/// - Smooth lighting interpolation
use rayon::prelude::*;

/// Maximum light level in Minecraft.
pub const MAX_LIGHT: u8 = 15;

/// Light level per block: packed as (sky << 4) | block
pub type PackedLight = u8;

/// Pack sky and block light into a single byte.
#[inline(always)]
pub fn pack_light(sky: u8, block: u8) -> PackedLight {
    ((sky & 0x0F) << 4) | (block & 0x0F)
}

/// Unpack sky light from packed value.
#[inline(always)]
pub fn unpack_sky(packed: PackedLight) -> u8 {
    packed >> 4
}

/// Unpack block light from packed value.
#[inline(always)]
pub fn unpack_block(packed: PackedLight) -> u8 {
    packed & 0x0F
}

/// Get the maximum light level (sky or block).
#[inline(always)]
pub fn max_light(packed: PackedLight) -> u8 {
    unpack_sky(packed).max(unpack_block(packed))
}

/// Compute final light levels for a set of blocks.
///
/// # Arguments
/// * `packed_lights` — array of packed (sky << 4 | block) values
///
/// Returns Vec<u8> with the max light level per block.
pub fn compute_light_levels(packed_lights: &[PackedLight]) -> Vec<u8> {
    packed_lights.iter().map(|&p| max_light(p)).collect()
}

/// Compute light levels in parallel for large arrays.
pub fn compute_light_levels_par(packed_lights: &[PackedLight]) -> Vec<u8> {
    if packed_lights.len() <= 1024 {
        return compute_light_levels(packed_lights);
    }

    packed_lights.par_iter().map(|&p| max_light(p)).collect()
}

/// Generate a lightmap lookup table.
///
/// Minecraft uses a 16x16 lightmap texture where:
/// - U coordinate = block light (0-15)
/// - V coordinate = sky light (0-15)
///
/// Each entry is an RGB color based on the light levels.
///
/// # Arguments
/// * `gamma` — gamma correction value (typically 0.0)
/// * `sky_brightness` — sky brightness factor (0.0-1.0, based on time of day)
///
/// Returns a 256-entry RGBA array (16x16, row-major).
pub fn generate_lightmap(gamma: f32, sky_brightness: f32) -> Vec<[u8; 4]> {
    let mut result = Vec::with_capacity(256);

    for sky in 0..=MAX_LIGHT {
        for block in 0..=MAX_LIGHT {
            let light = max_light(pack_light(sky, block));
            let brightness = light_level_to_brightness(light, gamma);

            // Sky light contributes based on time of day
            let sky_contrib = if sky > 0 {
                sky_brightness * (sky as f32 / MAX_LIGHT as f32)
            } else {
                0.0
            };

            // Block light is always full strength
            let block_contrib = if block > 0 {
                block as f32 / MAX_LIGHT as f32
            } else {
                0.0
            };

            let total = (brightness + sky_contrib * 0.5 + block_contrib * 0.5).min(1.0);

            let r = (total * 255.0) as u8;
            let g = (total * 255.0) as u8;
            let b = (total * 255.0) as u8;

            result.push([r, g, b, 255]);
        }
    }

    result
}

/// Convert a light level (0-15) to brightness (0.0-1.0) with optional gamma correction.
#[inline(always)]
pub fn light_level_to_brightness(level: u8, gamma: f32) -> f32 {
    let base = level as f32 / MAX_LIGHT as f32;
    if gamma <= 0.0 {
        return base;
    }
    // Simple gamma correction
    base.powf(1.0 / (1.0 + gamma))
}

/// Compute ambient occlusion for a grid of blocks.
///
/// AO is computed by checking the 8 neighboring blocks around each corner
/// of a face. For each vertex, we check 3 adjacent blocks and average their
/// occlusion values.
///
/// # Arguments
/// * `blocks` — 3D array of block opacity (0 = transparent, 1 = opaque)
///   Layout: blocks[x + y * size + z * size * size]
/// * `size` — dimension of the cubic grid (typically 16 for chunk section)
///
/// Returns Vec<f32> with AO factor (0.0-1.0) for each block corner.
pub fn compute_ambient_occlusion(blocks: &[u8], size: usize) -> Vec<f32> {
    let total_blocks = size * size * size;
    let mut ao = vec![1.0f32; total_blocks * 8]; // 8 corners per block

    for z in 0..size {
        for y in 0..size {
            for x in 0..size {
                let idx = x + y * size + z * size * size;

                // Check 8 corners
                for corner in 0..8 {
                    let cx = x + if corner & 1 != 0 { 1 } else { 0 };
                    let cy = y + if corner & 2 != 0 { 1 } else { 0 };
                    let cz = z + if corner & 4 != 0 { 1 } else { 0 };

                    // Count opaque neighbors for this corner
                    let mut opaque_count = 0u8;
                    let mut total_neighbors = 0u8;

                    for dz in 0..=1 {
                        for dy in 0..=1 {
                            for dx in 0..=1 {
                                let nx = cx + dx - 1;
                                let ny = cy + dy - 1;
                                let nz = cz + dz - 1;

                                if nx < size && ny < size && nz < size {
                                    let nidx = nx + ny * size + nz * size * size;
                                    if blocks[nidx] > 0 {
                                        opaque_count += 1;
                                    }
                                    total_neighbors += 1;
                                }
                            }
                        }
                    }

                    // AO factor: more opaque neighbors = darker
                    if total_neighbors > 0 {
                        ao[idx * 8 + corner] = 1.0 - (opaque_count as f32 / total_neighbors as f32) * 0.5;
                    }
                }
            }
        }
    }

    ao
}

/// Compute ambient occlusion in parallel for large grids.
pub fn compute_ambient_occlusion_par(blocks: &[u8], size: usize) -> Vec<f32> {
    if size <= 8 {
        return compute_ambient_occlusion(blocks, size);
    }

    let total_blocks = size * size * size;
    let mut ao = vec![1.0f32; total_blocks * 8];

    // Process each Y slice in parallel
    ao.par_chunks_mut(size * size * 8)
        .enumerate()
        .for_each(|(z, slice)| {
            for y in 0..size {
                for x in 0..size {
                    let idx = x + y * size;

                    for corner in 0..8 {
                        let cx = x + if corner & 1 != 0 { 1 } else { 0 };
                        let cy = y + if corner & 2 != 0 { 1 } else { 0 };
                        let cz = z + if corner & 4 != 0 { 1 } else { 0 };

                        let mut opaque_count = 0u8;
                        let mut total_neighbors = 0u8;

                        for dz in 0..=1 {
                            for dy in 0..=1 {
                                for dx in 0..=1 {
                                    let nx = cx + dx - 1;
                                    let ny = cy + dy - 1;
                                    let nz = cz + dz - 1;

                                    if nx < size && ny < size && nz < size {
                                        let nidx = nx + ny * size + nz * size * size;
                                        if blocks[nidx] > 0 {
                                            opaque_count += 1;
                                        }
                                        total_neighbors += 1;
                                    }
                                }
                            }
                        }

                        if total_neighbors > 0 {
                            slice[idx * 8 + corner] = 1.0 - (opaque_count as f32 / total_neighbors as f32) * 0.5;
                        }
                    }
                }
            }
        });

    ao
}

/// Smooth lighting: interpolate light levels at block corners.
///
/// For each face vertex, sample the 4 surrounding blocks and average
/// their light levels.
///
/// # Arguments
/// * `light_grid` — 3D array of packed light values
/// * `size` — dimension of the grid
/// * `face_normal` — normal of the face being lit [nx, ny, nz]
///
/// Returns Vec<u8> with smoothed light level per vertex.
pub fn smooth_lighting(
    light_grid: &[PackedLight],
    size: usize,
    face_normal: [i8; 3],
) -> Vec<u8> {
    let total = size * size;
    let mut result = Vec::with_capacity(total * 4); // 4 vertices per face

    for z in 0..size {
        for x in 0..size {
            // For each position on the face, sample surrounding blocks
            let mut vertex_lights = [0u8; 4];

            for v in 0..4 {
                let dx = if v & 1 != 0 { 1 } else { 0 };
                let dz = if v & 2 != 0 { 1 } else { 0 };

                // Sample the 4 blocks around this corner
                let mut total_light = 0u16;
                let mut samples = 0u8;

                for sy in 0..=1 {
                    for sz in 0..=1 {
                        for sx in 0..=1 {
                            let nx = (x as i32 + dx as i32 + sx as i32 - 1 + face_normal[0] as i32) as usize;
                            let ny = (sy as i32 + face_normal[1] as i32) as usize;
                            let nz = (z as i32 + dz as i32 + sz as i32 - 1 + face_normal[2] as i32) as usize;

                            if nx < size && ny < size && nz < size {
                                let idx = nx + ny * size + nz * size * size;
                                total_light += max_light(light_grid[idx]) as u16;
                                samples += 1;
                            }
                        }
                    }
                }

                vertex_lights[v] = if samples > 0 {
                    (total_light / samples as u16) as u8
                } else {
                    0
                };
            }

            result.extend_from_slice(&vertex_lights);
        }
    }

    result
}

/// Batch compute light levels for multiple chunk sections.
///
/// # Arguments
/// * `sections` — array of packed light data, one per section
/// * `section_size` — size of each section (typically 16x16x16 = 4096 blocks)
///
/// Returns Vec<Vec<u8>> with light levels per section.
pub fn batch_compute_section_lights(
    sections: &[&[PackedLight]],
    section_size: usize,
) -> Vec<Vec<u8>> {
    if sections.len() <= 4 {
        sections
            .iter()
            .map(|&section| compute_light_levels_par(&section[..section_size]))
            .collect()
    } else {
        sections
            .par_iter()
            .map(|&section| compute_light_levels_par(&section[..section_size]))
            .collect()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_pack_unpack_light() {
        let packed = pack_light(10, 5);
        assert_eq!(unpack_sky(packed), 10);
        assert_eq!(unpack_block(packed), 5);
        assert_eq!(max_light(packed), 10);
    }

    #[test]
    fn test_light_levels() {
        let lights = vec![
            pack_light(15, 0),
            pack_light(0, 15),
            pack_light(7, 7),
            pack_light(0, 0),
        ];
        let levels = compute_light_levels(&lights);
        assert_eq!(levels, vec![15, 15, 7, 0]);
    }

    #[test]
    fn test_lightmap_generation() {
        let lightmap = generate_lightmap(0.0, 1.0);
        assert_eq!(lightmap.len(), 256);
        // Full sky + full block should be bright
        let full = lightmap[15 * 16 + 15];
        assert!(full[0] > 200);
    }

    #[test]
    fn test_ambient_occlusion() {
        // 2x2x2 grid, all opaque
        let blocks = vec![1u8; 8];
        let ao = compute_ambient_occlusion(&blocks, 2);
        assert_eq!(ao.len(), 64); // 8 blocks * 8 corners
        // Corners should have some occlusion
        assert!(ao.iter().any(|&v| v < 1.0));
    }

    #[test]
    fn test_smooth_lighting() {
        // 2x2x2 grid
        let lights = vec![
            pack_light(15, 0), pack_light(15, 0),
            pack_light(15, 0), pack_light(15, 0),
            pack_light(0, 0), pack_light(0, 0),
            pack_light(0, 0), pack_light(0, 0),
        ];
        let smoothed = smooth_lighting(&lights, 2, [0, 1, 0]);
        assert!(!smoothed.is_empty());
    }

    #[test]
    fn test_batch_section_lights() {
        let section1 = vec![pack_light(15, 0); 4096];
        let section2 = vec![pack_light(0, 15); 4096];
        let sections: Vec<&[PackedLight]> = vec![&section1, &section2];

        let results = batch_compute_section_lights(&sections, 4096);
        assert_eq!(results.len(), 2);
        assert!(results[0].iter().all(|&v| v == 15));
        assert!(results[1].iter().all(|&v| v == 15));
    }
}