/// JNI bridge — exposes Rust optimization functions to Java via JNI.
///
/// Design: **bulk processing only** for hot paths. Java collects per-frame data
/// into flat arrays, Rust processes everything in one call, returns results.
use jni::JNIEnv;
use jni::objects::{JClass, JString, JByteArray, JDoubleArray};
use jni::sys::{jboolean, jdouble, jint, jlong, jbyteArray, jdoubleArray, jsize};

use crate::{chunk, entity_cull, frustum, lighting, mesh, protocol, scheduler, util, varint, occlusion, parse_number_list};

// ============================================================================
// Native init
// ============================================================================

#[no_mangle]
pub extern "system" fn Java_fun_bm_mili_rust_RustBridge_nativeInit(_env: JNIEnv, _class: JClass) {}

// ============================================================================
// Chunk / Region utilities (cheap, fine as single calls)
// ============================================================================

#[no_mangle]
pub extern "system" fn Java_fun_bm_mili_rust_RustBridge_chunkToRegion(_: JNIEnv, _: JClass, cx: jint, cz: jint) -> jlong {
    let (rx, rz) = chunk::chunk_to_region(cx, cz);
    pack_ints(rx, rz)
}
#[no_mangle]
pub extern "system" fn Java_fun_bm_mili_rust_RustBridge_chunkToLocal(_: JNIEnv, _: JClass, cx: jint, cz: jint) -> jlong {
    let (lx, lz) = chunk::chunk_to_local(cx, cz);
    pack_ints(lx, lz)
}
#[no_mangle] pub extern "system" fn Java_fun_bm_mili_rust_RustBridge_chunkIndex(_: JNIEnv, _: JClass, cx: jint, cz: jint) -> jint { chunk::chunk_index(cx, cz) as jint }
#[no_mangle] pub extern "system" fn Java_fun_bm_mili_rust_RustBridge_regionKey(_: JNIEnv, _: JClass, rx: jint, rz: jint) -> jlong { chunk::region_key(rx, rz) }
#[no_mangle] pub extern "system" fn Java_fun_bm_mili_rust_RustBridge_decodeHeaderEntry(_: JNIEnv, _: JClass, e: jint) -> jlong {
    let (o, c) = chunk::decode_header_entry(e as u32);
    ((o as i64) << 32) | (c as i64)
}
#[no_mangle] pub extern "system" fn Java_fun_bm_mili_rust_RustBridge_encodeHeaderEntry(_: JNIEnv, _: JClass, o: jint, c: jint) -> jint { chunk::encode_header_entry(o as u32, c as u8) as jint }

// ============================================================================
// VarInt
// ============================================================================

#[no_mangle] pub extern "system" fn Java_fun_bm_mili_rust_RustBridge_varintSize(_: JNIEnv, _: JClass, v: jint) -> jint { varint::varint_size(v) as jint }
#[no_mangle] pub extern "system" fn Java_fun_bm_mili_rust_RustBridge_varlongSize(_: JNIEnv, _: JClass, v: jlong) -> jint { varint::varlong_size(v) as jint }

// ============================================================================
// Hashing
// ============================================================================

#[no_mangle]
pub extern "system" fn Java_fun_bm_mili_rust_RustBridge_fnv1aHash(mut env: JNIEnv, _: JClass, input: JString) -> jlong {
    let s: String = match env.get_string(&input) { Ok(s) => s.into(), Err(_) => return 0 };
    const BASIS: u64 = 0xcbf29ce484222325; const PRIME: u64 = 0x100000001b3;
    s.as_bytes().iter().fold(BASIS, |a, &b| (a ^ u64::from(b)).wrapping_mul(PRIME)) as jlong
}

#[no_mangle]
pub extern "system" fn Java_fun_bm_mili_rust_RustBridge_murmur3_32(mut env: JNIEnv, _: JClass, data: jbyteArray, seed: jint) -> jint {
    let jba = unsafe { JByteArray::from_raw(data) };
    let arr = match unsafe { env.get_array_elements(&jba, jni::objects::ReleaseMode::NoCopyBack) } {
        Ok(a) => a, Err(_) => return 0,
    };
    let slice = unsafe { std::slice::from_raw_parts(arr.as_ptr() as *const u8, arr.len()) };
    util::murmur3_32(slice, seed as u32) as jint
}

// ============================================================================
// Protocol optimization
// ============================================================================

#[no_mangle]
pub extern "system" fn Java_fun_bm_mili_rust_RustBridge_optimizePacketBatch(mut env: JNIEnv, _: JClass, input: JString) -> jlong {
    let s: String = match env.get_string(&input) { Ok(s) => s.into(), Err(_) => return 0 };
    let sizes = parse_number_list(&s);
    protocol::optimize_packet_batch(&sizes) as jlong
}

// ============================================================================
// Scheduler
// ============================================================================

#[no_mangle]
pub extern "system" fn Java_fun_bm_mili_rust_RustBridge_runLightweightTasks(_: JNIEnv, _: JClass, jobs: jint, work: jint) -> jlong {
    let result = scheduler::run_lightweight_tasks(jobs as usize, work as usize);
    result.rsplit(':').next().and_then(|s| s.parse::<u64>().ok()).unwrap_or(0) as jlong
}

// ============================================================================
// Bitmap operations
// ============================================================================

#[no_mangle] pub extern "system" fn Java_fun_bm_mili_rust_RustBridge_bitmapFromHex(mut env: JNIEnv, _: JClass, hex: JString) -> jlong {
    let s: String = match env.get_string(&hex) { Ok(s) => s.into(), Err(_) => return 0 };
    match util::Bitmap::from_hex(&s) { Ok(bm) => Box::into_raw(Box::new(bm)) as jlong, Err(_) => 0 }
}
#[no_mangle] pub extern "system" fn Java_fun_bm_mili_rust_RustBridge_bitmapFree(_: JNIEnv, _: JClass, ptr: jlong) { if ptr != 0 { unsafe { drop(Box::from_raw(ptr as *mut util::Bitmap)) } } }
#[no_mangle] pub extern "system" fn Java_fun_bm_mili_rust_RustBridge_bitmapSet(_: JNIEnv, _: JClass, ptr: jlong, idx: jint) { if ptr != 0 { unsafe { &mut *(ptr as *mut util::Bitmap) }.set(idx as usize) } }
#[no_mangle] pub extern "system" fn Java_fun_bm_mili_rust_RustBridge_bitmapGet(_: JNIEnv, _: JClass, ptr: jlong, idx: jint) -> jboolean { if ptr == 0 { 0 } else { unsafe { &*(ptr as *const util::Bitmap) }.get(idx as usize) as jboolean } }
#[no_mangle] pub extern "system" fn Java_fun_bm_mili_rust_RustBridge_bitmapCount(_: JNIEnv, _: JClass, ptr: jlong) -> jint { if ptr == 0 { 0 } else { unsafe { &*(ptr as *const util::Bitmap) }.count() as jint } }
#[no_mangle] pub extern "system" fn Java_fun_bm_mili_rust_RustBridge_bitmapToHex(env: JNIEnv, _: JClass, ptr: jlong) -> jni::sys::jstring {
    if ptr == 0 { return env.new_string("").unwrap().into_raw(); }
    env.new_string(unsafe { &*(ptr as *const util::Bitmap) }.to_hex()).unwrap().into_raw()
}

// ============================================================================
// BULK Entity Culling — fast path for entity visibility
// ============================================================================

/// Batch cull entities using flat f32 arrays.
///
/// `entity_data`: flat array of [minX, minY, minZ, maxX, maxY, maxZ, posX, posZ] × N (f32)
/// Returns: byte array where each byte is a result flag (0=visible, 1=culled, 2=too_far, 3=too_big, 4=behind)
#[no_mangle]
pub extern "system" fn Java_fun_bm_mili_rust_RustBridge_bulkCullEntities(
    mut env: JNIEnv,
    _class: JClass,
    entity_data: jdoubleArray,
    num_entities: jint,
    viewer_x: jdouble,
    viewer_y: jdouble,
    viewer_z: jdouble,
    reach: jdouble,
    hitbox_limit: jdouble,
    camera_fwd_x: jdouble,
    camera_fwd_y: jdouble,
    camera_fwd_z: jdouble,
    fov_cos: jdouble,
) -> jbyteArray {
    let jda = unsafe { JDoubleArray::from_raw(entity_data) };
    let data = match unsafe { env.get_array_elements(&jda, jni::objects::ReleaseMode::NoCopyBack) } {
        Ok(d) => d,
        Err(_) => return std::ptr::null_mut(),
    };

    let expected_len = (num_entities as usize) * entity_cull::ENTITY_STRIDE;
    if data.len() < expected_len {
        return std::ptr::null_mut();
    }

    // Convert f64 array to f32 slice for processing
    let f32_data: Vec<f32> = data.iter().map(|&v| v as f32).collect();
    let results = entity_cull::batch_cull_entities(
        &f32_data,
        num_entities as usize,
        viewer_x, viewer_y, viewer_z,
        reach * reach,
        hitbox_limit as f32,
        camera_fwd_x as f32,
        camera_fwd_y as f32,
        camera_fwd_z as f32,
        fov_cos as f32,
    );

    let result_bytes: Vec<i8> = results.iter().map(|&b| b as i8).collect();
    let result_array = match env.new_byte_array(result_bytes.len() as jsize) {
        Ok(a) => a,
        Err(_) => return std::ptr::null_mut(),
    };
    let _ = env.set_byte_array_region(&result_array, 0, &result_bytes);
    result_array.into_raw()
}

// ============================================================================
// BULK Occlusion Culling — one JNI call per frame
// ============================================================================

#[no_mangle]
pub extern "system" fn Java_fun_bm_mili_rust_RustBridge_bulkOcclusionCull(
    mut env: JNIEnv,
    _class: JClass,
    aabb_data: jdoubleArray,
    reach: jint,
    expansion: jdouble,
) -> jbyteArray {
    let jda = unsafe { JDoubleArray::from_raw(aabb_data) };
    let data = match unsafe { env.get_array_elements(&jda, jni::objects::ReleaseMode::NoCopyBack) } {
        Ok(d) => d,
        Err(_) => return std::ptr::null_mut(),
    };
    let len = data.len();
    if len % 9 != 0 { return std::ptr::null_mut(); }
    let num_entities = len / 9;
    let slice = unsafe { std::slice::from_raw_parts(data.as_ptr(), len) };
    let results = occlusion::bulk_occlusion_cull(slice, num_entities, reach, expansion);

    // Convert Vec<u8> to Java byte[]
    let result_bytes: Vec<i8> = results.iter().map(|&b| b as i8).collect();
    let result_array = match env.new_byte_array(result_bytes.len() as jsize) {
        Ok(a) => a,
        Err(_) => return std::ptr::null_mut(),
    };
    let _ = env.set_byte_array_region(&result_array, 0, &result_bytes);
    result_array.into_raw()
}

/// Bulk DDA ray stepping for N rays through a shared voxel cache.
#[no_mangle]
pub extern "system" fn Java_fun_bm_mili_rust_RustBridge_bulkStepRay(
    mut env: JNIEnv,
    _class: JClass,
    ray_data: jdoubleArray,
    camera_x: jint, camera_y: jint, camera_z: jint,
    reach: jint,
    voxel_cache: jbyteArray,
    cache_size: jint,
) -> jbyteArray {
    let jda = unsafe { JDoubleArray::from_raw(ray_data) };
    let data = match unsafe { env.get_array_elements(&jda, jni::objects::ReleaseMode::NoCopyBack) } {
        Ok(d) => d,
        Err(_) => return std::ptr::null_mut(),
    };
    let len = data.len();
    if len % 6 != 0 { return std::ptr::null_mut(); }
    let num_rays = len / 6;

    let jba = unsafe { JByteArray::from_raw(voxel_cache) };
    let cache = match unsafe { env.get_array_elements(&jba, jni::objects::ReleaseMode::NoCopyBack) } {
        Ok(c) => c,
        Err(_) => return std::ptr::null_mut(),
    };

    let slice = unsafe { std::slice::from_raw_parts(data.as_ptr(), len) };
    let cache_slice = unsafe { std::slice::from_raw_parts(cache.as_ptr() as *const u8, cache.len()) };
    let camera = [camera_x, camera_y, camera_z];

    let results = occlusion::bulk_step_ray(slice, num_rays, camera, reach, cache_slice, cache_size as usize);

    let result_bytes: Vec<i8> = results.iter().map(|&b| b as i8).collect();
    let result_array = match env.new_byte_array(result_bytes.len() as jsize) {
        Ok(a) => a,
        Err(_) => return std::ptr::null_mut(),
    };
    let _ = env.set_byte_array_region(&result_array, 0, &result_bytes);
    result_array.into_raw()
}

// ============================================================================
// BULK Mesh / Frustum Culling — chunk section visibility
// ============================================================================

/// Batch cull chunk sections using frustum planes.
///
/// `section_data`: flat array of [minX, minY, minZ, maxX, maxY, maxZ] × N (f32 as double)
/// `frustum_planes`: 24 doubles (6 planes × 4 components)
/// Returns: byte array where 1 = visible, 0 = culled
#[no_mangle]
pub extern "system" fn Java_fun_bm_mili_rust_RustBridge_bulkCullChunkSections(
    mut env: JNIEnv,
    _class: JClass,
    section_data: jdoubleArray,
    frustum_planes: jdoubleArray,
) -> jbyteArray {
    let jda = unsafe { JDoubleArray::from_raw(section_data) };
    let data = match unsafe { env.get_array_elements(&jda, jni::objects::ReleaseMode::NoCopyBack) } {
        Ok(d) => d,
        Err(_) => return std::ptr::null_mut(),
    };

    let jfp = unsafe { JDoubleArray::from_raw(frustum_planes) };
    let planes_arr = match unsafe { env.get_array_elements(&jfp, jni::objects::ReleaseMode::NoCopyBack) } {
        Ok(p) => p,
        Err(_) => return std::ptr::null_mut(),
    };

    if planes_arr.len() < 24 {
        return std::ptr::null_mut();
    }

    let mut planes = [[0.0f32; 4]; 6];
    for i in 0..6 {
        for j in 0..4 {
            planes[i][j] = planes_arr[i * 4 + j] as f32;
        }
    }

    let len = data.len();
    if len % 6 != 0 { return std::ptr::null_mut(); }
    let num_sections = len / 6;

    let f32_data: Vec<f32> = data.iter().map(|&v| v as f32).collect();
    let results = mesh::batch_cull_chunk_sections(&f32_data, num_sections, &planes);

    let result_bytes: Vec<i8> = results.iter().map(|&b| b as i8).collect();
    let result_array = match env.new_byte_array(result_bytes.len() as jsize) {
        Ok(a) => a,
        Err(_) => return std::ptr::null_mut(),
    };
    let _ = env.set_byte_array_region(&result_array, 0, &result_bytes);
    result_array.into_raw()
}

/// Batch cull spheres using frustum.
///
/// `centers`: flat array of [x, y, z] × N (double)
/// `radii`: array of radii (double)
/// `frustum_planes`: 24 doubles (6 planes × 4 components)
/// Returns: byte array where 1 = visible, 0 = culled
#[no_mangle]
pub extern "system" fn Java_fun_bm_mili_rust_RustBridge_bulkCullSpheres(
    mut env: JNIEnv,
    _class: JClass,
    centers: jdoubleArray,
    radii: jdoubleArray,
    frustum_planes: jdoubleArray,
) -> jbyteArray {
    let jda = unsafe { JDoubleArray::from_raw(centers) };
    let center_data = match unsafe { env.get_array_elements(&jda, jni::objects::ReleaseMode::NoCopyBack) } {
        Ok(d) => d,
        Err(_) => return std::ptr::null_mut(),
    };

    let jr = unsafe { JDoubleArray::from_raw(radii) };
    let radii_data = match unsafe { env.get_array_elements(&jr, jni::objects::ReleaseMode::NoCopyBack) } {
        Ok(r) => r,
        Err(_) => return std::ptr::null_mut(),
    };

    let jfp = unsafe { JDoubleArray::from_raw(frustum_planes) };
    let planes_arr = match unsafe { env.get_array_elements(&jfp, jni::objects::ReleaseMode::NoCopyBack) } {
        Ok(p) => p,
        Err(_) => return std::ptr::null_mut(),
    };

    if planes_arr.len() < 24 || center_data.len() % 3 != 0 {
        return std::ptr::null_mut();
    }

    let num_spheres = center_data.len() / 3;
    if radii_data.len() < num_spheres {
        return std::ptr::null_mut();
    }

    let mut planes = [[0.0f32; 4]; 6];
    for i in 0..6 {
        for j in 0..4 {
            planes[i][j] = planes_arr[i * 4 + j] as f32;
        }
    }

    let f32_centers: Vec<f32> = center_data.iter().map(|&v| v as f32).collect();
    let f32_radii: Vec<f32> = radii_data.iter().map(|&v| v as f32).collect();
    let frustum = frustum::Frustum { planes };

    let results = frustum::batch_cull_spheres(&f32_centers, &f32_radii, num_spheres, &frustum);

    let result_bytes: Vec<i8> = results.iter().map(|&b| b as i8).collect();
    let result_array = match env.new_byte_array(result_bytes.len() as jsize) {
        Ok(a) => a,
        Err(_) => return std::ptr::null_mut(),
    };
    let _ = env.set_byte_array_region(&result_array, 0, &result_bytes);
    result_array.into_raw()
}

/// Batch cull AABBs using frustum.
///
/// `aabbs`: flat array of [minX, minY, minZ, maxX, maxY, maxZ] × N (double)
/// `frustum_planes`: 24 doubles (6 planes × 4 components)
/// Returns: byte array where 1 = visible, 0 = culled
#[no_mangle]
pub extern "system" fn Java_fun_bm_mili_rust_RustBridge_bulkCullAABBs(
    mut env: JNIEnv,
    _class: JClass,
    aabbs: jdoubleArray,
    frustum_planes: jdoubleArray,
) -> jbyteArray {
    let jda = unsafe { JDoubleArray::from_raw(aabbs) };
    let data = match unsafe { env.get_array_elements(&jda, jni::objects::ReleaseMode::NoCopyBack) } {
        Ok(d) => d,
        Err(_) => return std::ptr::null_mut(),
    };

    let jfp = unsafe { JDoubleArray::from_raw(frustum_planes) };
    let planes_arr = match unsafe { env.get_array_elements(&jfp, jni::objects::ReleaseMode::NoCopyBack) } {
        Ok(p) => p,
        Err(_) => return std::ptr::null_mut(),
    };

    if planes_arr.len() < 24 || data.len() % 6 != 0 {
        return std::ptr::null_mut();
    }

    let num_aabbs = data.len() / 6;

    let mut planes = [[0.0f32; 4]; 6];
    for i in 0..6 {
        for j in 0..4 {
            planes[i][j] = planes_arr[i * 4 + j] as f32;
        }
    }

    let f32_data: Vec<f32> = data.iter().map(|&v| v as f32).collect();
    let frustum = frustum::Frustum { planes };

    let results = frustum::batch_cull_aabbs(&f32_data, num_aabbs, &frustum);

    let result_bytes: Vec<i8> = results.iter().map(|&b| b as i8).collect();
    let result_array = match env.new_byte_array(result_bytes.len() as jsize) {
        Ok(a) => a,
        Err(_) => return std::ptr::null_mut(),
    };
    let _ = env.set_byte_array_region(&result_array, 0, &result_bytes);
    result_array.into_raw()
}

// ============================================================================
// BULK Lighting — light level computation
// ============================================================================

/// Compute light levels from packed light data.
///
/// `packedLights`: byte array where each byte is (sky << 4) | block
/// Returns: byte array with max(sky, block) per block
#[no_mangle]
pub extern "system" fn Java_fun_bm_mili_rust_RustBridge_bulkComputeLightLevels(
    mut env: JNIEnv,
    _class: JClass,
    packed_lights: jbyteArray,
) -> jbyteArray {
    let jba = unsafe { JByteArray::from_raw(packed_lights) };
    let data = match unsafe { env.get_array_elements(&jba, jni::objects::ReleaseMode::NoCopyBack) } {
        Ok(d) => d,
        Err(_) => return std::ptr::null_mut(),
    };

    let slice = unsafe { std::slice::from_raw_parts(data.as_ptr() as *const u8, data.len()) };
    let results = lighting::compute_light_levels_par(slice);

    let result_bytes: Vec<i8> = results.iter().map(|&b| b as i8).collect();
    let result_array = match env.new_byte_array(result_bytes.len() as jsize) {
        Ok(a) => a,
        Err(_) => return std::ptr::null_mut(),
    };
    let _ = env.set_byte_array_region(&result_array, 0, &result_bytes);
    result_array.into_raw()
}

/// Generate a lightmap texture.
///
/// `gamma`: gamma correction value (double)
/// `skyBrightness`: sky brightness factor 0.0-1.0 (double)
/// Returns: int array of 256 RGBA values
#[no_mangle]
pub extern "system" fn Java_fun_bm_mili_rust_RustBridge_generateLightmap(
    mut env: JNIEnv,
    _class: JClass,
    gamma: jdouble,
    sky_brightness: jdouble,
) -> jni::sys::jintArray {
    let lightmap = lighting::generate_lightmap(gamma as f32, sky_brightness as f32);
    let mut result: Vec<i32> = lightmap.iter().map(|&[r, g, b, a]| {
        ((a as i32) << 24) | ((r as i32) << 16) | ((g as i32) << 8) | (b as i32)
    }).collect();

    let result_array = match env.new_int_array(result.len() as jsize) {
        Ok(a) => a,
        Err(_) => return std::ptr::null_mut(),
    };
    let _ = env.set_int_array_region(&result_array, 0, &result);
    result_array.into_raw()
}

// ============================================================================
// Utility
// ============================================================================

fn pack_ints(a: i32, b: i32) -> jlong {
    ((a as i64) << 32) | ((b as i64) & 0xFFFF_FFFF)
}