use jni::objects::{JByteBuffer, JClass, JString};
use jni::sys::{
    jboolean, jbyteArray, jdouble, jdoubleArray, jfloatArray, jint, jintArray, jlongArray,
    jsize, jstring,
};
/// JNI bridge — exposes Rust optimization functions to Java via JNI.
///
/// Design: **bulk processing only** for hot paths. Java collects per-tick data
/// into flat arrays or DirectByteBuffers, Rust processes everything in one call.
///
/// Zero-copy path: Java passes a DirectByteBuffer; Rust reads via
/// GetDirectBufferAddress — no array copy across the JNI boundary.
///
/// ## Panic safety
///
/// Every JNI entry point wraps its body in `std::panic::catch_unwind` to
/// prevent Rust panics from crossing the JNI boundary (which would abort
/// the JVM). On panic the function returns a null/false sentinel so the
/// Java caller can detect the failure.
use jni::JNIEnv;
use std::panic::AssertUnwindSafe;

use crate::{analytics, config, entity_cull, frustum};

// ============================================================================
// Native init
// ============================================================================

#[unsafe(no_mangle)]
pub extern "system" fn Java_fun_bm_mili_rust_RustBridge_nativeInit(_env: JNIEnv, _class: JClass) {}

// ============================================================================
// Entity culling — zero-copy via DirectByteBuffer
// ============================================================================

#[unsafe(no_mangle)]
pub extern "system" fn Java_fun_bm_mili_rust_RustBridge_batchCullEntitiesDirect(
    env: JNIEnv,
    _: JClass,
    entity_buffer: JByteBuffer,
    num_entities: jint,
    viewer_x: jdouble,
    viewer_y: jdouble,
    viewer_z: jdouble,
    reach_sq: jdouble,
    hitbox_limit: jdouble,
    planes_buffer: JByteBuffer,
) -> jbyteArray {
    let result = std::panic::catch_unwind(AssertUnwindSafe(|| {
        batch_cull_entities_direct_inner(
            &env,
            entity_buffer,
            num_entities,
            viewer_x,
            viewer_y,
            viewer_z,
            reach_sq,
            hitbox_limit,
            planes_buffer,
        )
    }));
    result.unwrap_or(std::ptr::null_mut())
}

#[allow(clippy::too_many_arguments)]
fn batch_cull_entities_direct_inner(
    env: &JNIEnv,
    entity_buffer: JByteBuffer,
    num_entities: jint,
    viewer_x: jdouble,
    viewer_y: jdouble,
    viewer_z: jdouble,
    reach_sq: jdouble,
    hitbox_limit: jdouble,
    planes_buffer: JByteBuffer,
) -> jbyteArray {
    // Mili start - fix: validate num_entities >= 0 to prevent negative jint → huge usize cast
    if num_entities < 0 {
        return std::ptr::null_mut();
    }
    let num_entities = num_entities as usize;
    // Mili end

    // Mili start - fix: check direct buffer address is not null
    let entity_addr = match env.get_direct_buffer_address(&entity_buffer) {
        Ok(addr) if !addr.is_null() => addr as *const f32,
        _ => return std::ptr::null_mut(),
    };

    let planes_addr = match env.get_direct_buffer_address(&planes_buffer) {
        Ok(addr) if !addr.is_null() => addr as *const f32,
        _ => return std::ptr::null_mut(),
    };
    // Mili end

    // Mili start - fix: validate DirectByteBuffer capacity before creating slices
    let entity_capacity = env.get_direct_buffer_capacity(&entity_buffer).unwrap_or(0);
    let required_entity_bytes = num_entities
        .checked_mul(entity_cull::ENTITY_STRIDE)
        .and_then(|n| n.checked_mul(std::mem::size_of::<f32>()));
    if num_entities > 0
        && (required_entity_bytes.is_none() || entity_capacity < required_entity_bytes.unwrap_or(0))
    {
        return std::ptr::null_mut();
    }

    let planes_capacity = env.get_direct_buffer_capacity(&planes_buffer).unwrap_or(0);
    if planes_capacity < 24 * std::mem::size_of::<f32>() {
        return std::ptr::null_mut();
    }
    // Mili end

    // Safety: Java guarantees the DirectByteBuffer contains at least 24 f32 values.
    let planes_slice = unsafe { std::slice::from_raw_parts(planes_addr, 24) };
    let mut planes = [[0.0f32; 4]; 6];
    for (i, plane) in planes.iter_mut().enumerate() {
        *plane = [
            planes_slice[i * 4],
            planes_slice[i * 4 + 1],
            planes_slice[i * 4 + 2],
            planes_slice[i * 4 + 3],
        ];
    }
    let frustum = frustum::Frustum { planes };

    // Safety: batch_cull_entities_zero_copy requires unsafe because it
    // dereferences entity_addr, which Java guarantees is a valid DirectByteBuffer.
    let results = unsafe {
        entity_cull::batch_cull_entities_zero_copy(
            entity_addr,
            num_entities,
            viewer_x,
            viewer_y,
            viewer_z,
            reach_sq,
            hitbox_limit as f32,
            &frustum,
        )
    };

    let len = results.len() as jsize;
    let byte_array = match env.new_byte_array(len) {
        Ok(arr) => arr,
        Err(_) => return std::ptr::null_mut(),
    };
    let results_bytes: Vec<i8> = results.iter().map(|&b| b as i8).collect();
    let _ = env.set_byte_array_region(&byte_array, 0, &results_bytes);
    byte_array.into_raw()
}

// ============================================================================
// Entity culling — array fallback (non-zero-copy)
// ============================================================================

#[unsafe(no_mangle)]
pub extern "system" fn Java_fun_bm_mili_rust_RustBridge_batchCullEntities(
    mut env: JNIEnv,
    _: JClass,
    entities: jni::objects::JFloatArray,
    num_entities: jint,
    viewer_x: jdouble,
    viewer_y: jdouble,
    viewer_z: jdouble,
    reach_sq: jdouble,
    hitbox_limit: jdouble,
    planes_arr: jni::objects::JFloatArray,
) -> jbyteArray {
    let result = std::panic::catch_unwind(AssertUnwindSafe(|| {
        batch_cull_entities_array_inner(
            &mut env,
            entities,
            num_entities,
            viewer_x,
            viewer_y,
            viewer_z,
            reach_sq,
            hitbox_limit,
            planes_arr,
        )
    }));
    result.unwrap_or(std::ptr::null_mut())
}

#[allow(clippy::too_many_arguments)]
fn batch_cull_entities_array_inner(
    env: &mut JNIEnv,
    entities: jni::objects::JFloatArray,
    num_entities: jint,
    viewer_x: jdouble,
    viewer_y: jdouble,
    viewer_z: jdouble,
    reach_sq: jdouble,
    hitbox_limit: jdouble,
    planes_arr: jni::objects::JFloatArray,
) -> jbyteArray {
    // Mili start - fix: validate num_entities >= 0
    if num_entities < 0 {
        return std::ptr::null_mut();
    }
    let num_entities = num_entities as usize;
    // Mili end

    let entity_data: Vec<f32> =
        match unsafe { env.get_array_elements(&entities, jni::objects::ReleaseMode::CopyBack) } {
            Ok(slice) => slice.iter().copied().collect(),
            Err(_) => return std::ptr::null_mut(),
        };

    let planes_data: Vec<f32> =
        match unsafe { env.get_array_elements(&planes_arr, jni::objects::ReleaseMode::CopyBack) } {
            Ok(slice) => slice.iter().copied().collect(),
            Err(_) => return std::ptr::null_mut(),
        };

    if planes_data.len() < 24 {
        return std::ptr::null_mut();
    }

    // Mili start - fix: validate entity_data has enough elements for num_entities
    if entity_data.len() < num_entities * entity_cull::ENTITY_STRIDE {
        return std::ptr::null_mut();
    }
    // Mili end

    let mut planes = [[0.0f32; 4]; 6];
    for (i, plane) in planes.iter_mut().enumerate() {
        *plane = [
            planes_data[i * 4],
            planes_data[i * 4 + 1],
            planes_data[i * 4 + 2],
            planes_data[i * 4 + 3],
        ];
    }
    let frustum = frustum::Frustum { planes };

    let results = entity_cull::batch_cull_entities(
        &entity_data,
        num_entities,
        viewer_x,
        viewer_y,
        viewer_z,
        reach_sq,
        hitbox_limit as f32,
        &frustum,
    );

    let len = results.len() as jsize;
    let byte_array = match env.new_byte_array(len) {
        Ok(arr) => arr,
        Err(_) => return std::ptr::null_mut(),
    };
    let results_bytes: Vec<i8> = results.iter().map(|&b| b as i8).collect();
    let _ = env.set_byte_array_region(&byte_array, 0, &results_bytes);
    byte_array.into_raw()
}

// ============================================================================
// Batch analytics — chunk hotness / dynamic view distance / entity density
// ============================================================================

#[unsafe(no_mangle)]
pub extern "system" fn Java_fun_bm_mili_rust_RustBridge_analyzeChunkHotnessDirect(
    env: JNIEnv,
    _: JClass,
    chunk_buffer: JByteBuffer,
    num_chunks: jint,
    player_buffer: JByteBuffer,
    num_players: jint,
    radius_sq: jdouble,
) -> jdoubleArray {
    let result = std::panic::catch_unwind(AssertUnwindSafe(|| {
        analyze_chunk_hotness_direct_inner(
            &env,
            chunk_buffer,
            num_chunks,
            player_buffer,
            num_players,
            radius_sq,
        )
    }));
    result.unwrap_or(std::ptr::null_mut())
}

fn analyze_chunk_hotness_direct_inner(
    env: &JNIEnv,
    chunk_buffer: JByteBuffer,
    num_chunks: jint,
    player_buffer: JByteBuffer,
    num_players: jint,
    radius_sq: jdouble,
) -> jdoubleArray {
    if num_chunks < 0 || num_players < 0 {
        return std::ptr::null_mut();
    }
    let num_chunks = num_chunks as usize;
    let num_players = num_players as usize;

    let chunk_addr = match env.get_direct_buffer_address(&chunk_buffer) {
        Ok(addr) if !addr.is_null() => addr as *const i32,
        _ => return std::ptr::null_mut(),
    };
    let player_addr = match env.get_direct_buffer_address(&player_buffer) {
        Ok(addr) if !addr.is_null() => addr as *const i32,
        _ => return std::ptr::null_mut(),
    };

    let chunk_capacity = env.get_direct_buffer_capacity(&chunk_buffer).unwrap_or(0);
    let required_chunk_bytes = num_chunks
        .checked_mul(analytics::CHUNK_COORD_STRIDE)
        .and_then(|n| n.checked_mul(std::mem::size_of::<i32>()));
    if num_chunks > 0
        && (required_chunk_bytes.is_none() || chunk_capacity < required_chunk_bytes.unwrap_or(0))
    {
        return std::ptr::null_mut();
    }

    let player_capacity = env.get_direct_buffer_capacity(&player_buffer).unwrap_or(0);
    let required_player_bytes = num_players
        .checked_mul(analytics::PLAYER_CHUNK_COORD_STRIDE)
        .and_then(|n| n.checked_mul(std::mem::size_of::<i32>()));
    if num_players > 0
        && (required_player_bytes.is_none() || player_capacity < required_player_bytes.unwrap_or(0))
    {
        return std::ptr::null_mut();
    }

    let chunk_slice = unsafe {
        std::slice::from_raw_parts(chunk_addr, num_chunks * analytics::CHUNK_COORD_STRIDE)
    };
    let player_slice = unsafe {
        std::slice::from_raw_parts(player_addr, num_players * analytics::PLAYER_CHUNK_COORD_STRIDE)
    };

    let result = analytics::analyze_chunk_hotness(
        chunk_slice,
        num_chunks,
        player_slice,
        num_players,
        radius_sq,
    );

    let len = result.len() as jsize;
    let double_array = match env.new_double_array(len) {
        Ok(arr) => arr,
        Err(_) => return std::ptr::null_mut(),
    };
    let _ = env.set_double_array_region(&double_array, 0, &result);
    double_array.into_raw()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_fun_bm_mili_rust_RustBridge_computeDynamicViewDistancesDirect(
    env: JNIEnv,
    _: JClass,
    player_buffer: JByteBuffer,
    num_players: jint,
    current_tps: jdouble,
    tps_high_threshold: jdouble,
    tps_low_threshold: jdouble,
    min_view_distance: jint,
    max_view_distance: jint,
    player_density_weight: jdouble,
) -> jintArray {
    let result = std::panic::catch_unwind(AssertUnwindSafe(|| {
        compute_dynamic_view_distances_direct_inner(
            &env,
            player_buffer,
            num_players,
            current_tps,
            tps_high_threshold,
            tps_low_threshold,
            min_view_distance,
            max_view_distance,
            player_density_weight,
        )
    }));
    result.unwrap_or(std::ptr::null_mut())
}

#[allow(clippy::too_many_arguments)]
fn compute_dynamic_view_distances_direct_inner(
    env: &JNIEnv,
    player_buffer: JByteBuffer,
    num_players: jint,
    current_tps: jdouble,
    tps_high_threshold: jdouble,
    tps_low_threshold: jdouble,
    min_view_distance: jint,
    max_view_distance: jint,
    player_density_weight: jdouble,
) -> jintArray {
    if num_players < 0 {
        return std::ptr::null_mut();
    }
    let num_players = num_players as usize;

    let player_addr = match env.get_direct_buffer_address(&player_buffer) {
        Ok(addr) if !addr.is_null() => addr as *const i32,
        _ => return std::ptr::null_mut(),
    };

    let player_capacity = env.get_direct_buffer_capacity(&player_buffer).unwrap_or(0);
    let required_player_bytes = num_players
        .checked_mul(analytics::DYNAMIC_PLAYER_STRIDE)
        .and_then(|n| n.checked_mul(std::mem::size_of::<i32>()));
    if num_players > 0
        && (required_player_bytes.is_none() || player_capacity < required_player_bytes.unwrap_or(0))
    {
        return std::ptr::null_mut();
    }

    let player_slice = unsafe {
        std::slice::from_raw_parts(player_addr, num_players * analytics::DYNAMIC_PLAYER_STRIDE)
    };

    let result = analytics::compute_dynamic_view_distances(
        player_slice,
        num_players,
        current_tps,
        tps_high_threshold,
        tps_low_threshold,
        min_view_distance,
        max_view_distance,
        player_density_weight,
    );

    let len = result.len() as jsize;
    let int_array = match env.new_int_array(len) {
        Ok(arr) => arr,
        Err(_) => return std::ptr::null_mut(),
    };
    let _ = env.set_int_array_region(&int_array, 0, &result);
    int_array.into_raw()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_fun_bm_mili_rust_RustBridge_analyzeEntityDensityDirect(
    env: JNIEnv,
    _: JClass,
    entity_buffer: JByteBuffer,
    num_entities: jint,
    cell_size: jint,
    threshold: jint,
) -> jlongArray {
    let result = std::panic::catch_unwind(AssertUnwindSafe(|| {
        analyze_entity_density_direct_inner(
            &env,
            entity_buffer,
            num_entities,
            cell_size,
            threshold,
        )
    }));
    result.unwrap_or(std::ptr::null_mut())
}

fn analyze_entity_density_direct_inner(
    env: &JNIEnv,
    entity_buffer: JByteBuffer,
    num_entities: jint,
    cell_size: jint,
    threshold: jint,
) -> jlongArray {
    if num_entities < 0 {
        return std::ptr::null_mut();
    }
    let num_entities = num_entities as usize;

    let entity_addr = match env.get_direct_buffer_address(&entity_buffer) {
        Ok(addr) if !addr.is_null() => addr as *const i32,
        _ => return std::ptr::null_mut(),
    };

    let entity_capacity = env.get_direct_buffer_capacity(&entity_buffer).unwrap_or(0);
    let required_entity_bytes = num_entities
        .checked_mul(analytics::DENSITY_ENTITY_STRIDE)
        .and_then(|n| n.checked_mul(std::mem::size_of::<i32>()));
    if num_entities > 0
        && (required_entity_bytes.is_none() || entity_capacity < required_entity_bytes.unwrap_or(0))
    {
        return std::ptr::null_mut();
    }

    let entity_slice = unsafe {
        std::slice::from_raw_parts(entity_addr, num_entities * analytics::DENSITY_ENTITY_STRIDE)
    };

    let result = match analytics::analyze_entity_density(entity_slice, num_entities, cell_size, threshold)
    {
        Some(result) => analytics::flatten_density_result(result),
        None => return std::ptr::null_mut(),
    };

    let len = result.len() as jsize;
    let long_array = match env.new_long_array(len) {
        Ok(arr) => arr,
        Err(_) => return std::ptr::null_mut(),
    };
    let _ = env.set_long_array_region(&long_array, 0, &result);
    long_array.into_raw()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_fun_bm_mili_rust_RustBridge_evaluateVillagerActivityDirect(
    env: JNIEnv,
    _: JClass,
    state_buffer: JByteBuffer,
    block_buffer: JByteBuffer,
    num_villagers: jint,
    config_flags: jint,
) -> jbyteArray {
    let result = std::panic::catch_unwind(AssertUnwindSafe(|| {
        evaluate_villager_activity_direct_inner(
            &env,
            state_buffer,
            block_buffer,
            num_villagers,
            config_flags,
        )
    }));
    result.unwrap_or(std::ptr::null_mut())
}

fn evaluate_villager_activity_direct_inner(
    env: &JNIEnv,
    state_buffer: JByteBuffer,
    block_buffer: JByteBuffer,
    num_villagers: jint,
    config_flags: jint,
) -> jbyteArray {
    if num_villagers < 0 {
        return std::ptr::null_mut();
    }
    let num_villagers = num_villagers as usize;

    let state_addr = match env.get_direct_buffer_address(&state_buffer) {
        Ok(addr) if !addr.is_null() => addr as *const i32,
        _ => return std::ptr::null_mut(),
    };
    let block_addr = match env.get_direct_buffer_address(&block_buffer) {
        Ok(addr) if !addr.is_null() => addr as *const i32,
        _ => return std::ptr::null_mut(),
    };

    let state_capacity = env.get_direct_buffer_capacity(&state_buffer).unwrap_or(0);
    let required_state_bytes = num_villagers
        .checked_mul(analytics::VILLAGER_STATE_STRIDE)
        .and_then(|n| n.checked_mul(std::mem::size_of::<i32>()));
    if num_villagers > 0
        && (required_state_bytes.is_none() || state_capacity < required_state_bytes.unwrap_or(0))
    {
        return std::ptr::null_mut();
    }

    let block_capacity = env.get_direct_buffer_capacity(&block_buffer).unwrap_or(0);
    let required_block_bytes = num_villagers
        .checked_mul(analytics::VILLAGER_BLOCK_STRIDE)
        .and_then(|n| n.checked_mul(std::mem::size_of::<i32>()));
    if num_villagers > 0
        && (required_block_bytes.is_none() || block_capacity < required_block_bytes.unwrap_or(0))
    {
        return std::ptr::null_mut();
    }

    let state_slice = unsafe {
        std::slice::from_raw_parts(state_addr, num_villagers * analytics::VILLAGER_STATE_STRIDE)
    };
    let block_slice = unsafe {
        std::slice::from_raw_parts(block_addr, num_villagers * analytics::VILLAGER_BLOCK_STRIDE)
    };

    let result = analytics::evaluate_villager_activity(
        state_slice,
        block_slice,
        num_villagers,
        config_flags,
    );

    let len = result.len() as jsize;
    let byte_array = match env.new_byte_array(len) {
        Ok(arr) => arr,
        Err(_) => return std::ptr::null_mut(),
    };
    let result_bytes: Vec<i8> = result.iter().map(|&b| b as i8).collect();
    let _ = env.set_byte_array_region(&byte_array, 0, &result_bytes);
    byte_array.into_raw()
}

// ============================================================================
// Frustum — build from camera params
// ============================================================================

#[unsafe(no_mangle)]
pub extern "system" fn Java_fun_bm_mili_rust_RustBridge_buildFrustumFromCamera(
    mut env: JNIEnv,
    _: JClass,
    fov_y: jdouble,
    aspect: jdouble,
    near: jdouble,
    far: jdouble,
    pos_arr: jni::objects::JFloatArray,
    fwd_arr: jni::objects::JFloatArray,
    up_arr: jni::objects::JFloatArray,
) -> jfloatArray {
    let result = std::panic::catch_unwind(AssertUnwindSafe(|| {
        build_frustum_from_camera_inner(
            &mut env, fov_y, aspect, near, far, pos_arr, fwd_arr, up_arr,
        )
    }));
    result.unwrap_or(std::ptr::null_mut())
}

#[allow(clippy::too_many_arguments)]
fn build_frustum_from_camera_inner(
    env: &mut JNIEnv,
    fov_y: jdouble,
    aspect: jdouble,
    near: jdouble,
    far: jdouble,
    pos_arr: jni::objects::JFloatArray,
    fwd_arr: jni::objects::JFloatArray,
    up_arr: jni::objects::JFloatArray,
) -> jfloatArray {
    let pos: Vec<f32> =
        match unsafe { env.get_array_elements(&pos_arr, jni::objects::ReleaseMode::CopyBack) } {
            Ok(s) => s.iter().copied().collect(),
            Err(_) => return std::ptr::null_mut(),
        };
    let fwd: Vec<f32> =
        match unsafe { env.get_array_elements(&fwd_arr, jni::objects::ReleaseMode::CopyBack) } {
            Ok(s) => s.iter().copied().collect(),
            Err(_) => return std::ptr::null_mut(),
        };
    let up: Vec<f32> =
        match unsafe { env.get_array_elements(&up_arr, jni::objects::ReleaseMode::CopyBack) } {
            Ok(s) => s.iter().copied().collect(),
            Err(_) => return std::ptr::null_mut(),
        };

    if pos.len() < 3 || fwd.len() < 3 || up.len() < 3 {
        return std::ptr::null_mut();
    }

    let frustum = frustum::frustum_from_camera(
        fov_y as f32,
        aspect as f32,
        near as f32,
        far as f32,
        [pos[0], pos[1], pos[2]],
        [fwd[0], fwd[1], fwd[2]],
        [up[0], up[1], up[2]],
    );

    let flat: Vec<f32> = frustum
        .planes
        .iter()
        .flat_map(|p| p.iter())
        .copied()
        .collect();
    let len = flat.len() as jsize;
    let float_array = match env.new_float_array(len) {
        Ok(arr) => arr,
        Err(_) => return std::ptr::null_mut(),
    };
    let _ = env.set_float_array_region(&float_array, 0, &flat);
    float_array.into_raw()
}

// ============================================================================
// Config — TOML load/save
// ============================================================================

#[unsafe(no_mangle)]
pub extern "system" fn Java_fun_bm_mili_rust_RustBridge_configLoad(
    mut env: JNIEnv,
    _: JClass,
    path: JString,
) -> jstring {
    let result = std::panic::catch_unwind(AssertUnwindSafe(|| config_load_inner(&mut env, path)));
    match result {
        Ok(s) => s,
        Err(_) => jni_string_or_empty(&mut env),
    }
}

fn config_load_inner(env: &mut JNIEnv, path: JString) -> jstring {
    let path_str: String = match env.get_string(&path) {
        Ok(s) => s.into(),
        Err(_) => return jni_string_or_empty(env),
    };

    match config::load_config(&path_str) {
        Some(json) => match env.new_string(&json) {
            Ok(s) => s.into_raw(),
            Err(_) => jni_string_or_empty(env),
        },
        None => jni_string_or_empty(env),
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_fun_bm_mili_rust_RustBridge_configSave(
    mut env: JNIEnv,
    _: JClass,
    path: JString,
    json: JString,
) -> jboolean {
    let result =
        std::panic::catch_unwind(AssertUnwindSafe(|| config_save_inner(&mut env, path, json)));
    result.unwrap_or(0)
}

fn config_save_inner(env: &mut JNIEnv, path: JString, json: JString) -> jboolean {
    let path_str: String = match env.get_string(&path) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };
    let json_str: String = match env.get_string(&json) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };

    if config::save_config(&path_str, &json_str) {
        1
    } else {
        0
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_fun_bm_mili_rust_RustBridge_configSaveMerge(
    mut env: JNIEnv,
    _: JClass,
    path: JString,
    json: JString,
) -> jboolean {
    let result = std::panic::catch_unwind(AssertUnwindSafe(|| {
        config_save_merge_inner(&mut env, path, json)
    }));
    result.unwrap_or(0)
}

fn config_save_merge_inner(env: &mut JNIEnv, path: JString, json: JString) -> jboolean {
    let path_str: String = match env.get_string(&path) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };
    let json_str: String = match env.get_string(&json) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };

    if config::save_config_merge(&path_str, &json_str) {
        1
    } else {
        0
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_fun_bm_mili_rust_RustBridge_configContains(
    mut env: JNIEnv,
    _: JClass,
    path: JString,
    key: JString,
) -> jboolean {
    let result = std::panic::catch_unwind(AssertUnwindSafe(|| {
        config_contains_inner(&mut env, path, key)
    }));
    result.unwrap_or(0)
}

fn config_contains_inner(env: &mut JNIEnv, path: JString, key: JString) -> jboolean {
    let path_str: String = match env.get_string(&path) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };
    let key_str: String = match env.get_string(&key) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };

    if config::contains_key(&path_str, &key_str) {
        1
    } else {
        0
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_fun_bm_mili_rust_RustBridge_configGetValue(
    mut env: JNIEnv,
    _: JClass,
    path: JString,
    key: JString,
) -> jstring {
    let result = std::panic::catch_unwind(AssertUnwindSafe(|| {
        config_get_value_inner(&mut env, path, key)
    }));
    match result {
        Ok(s) => s,
        Err(_) => jni_string(&mut env, "null"),
    }
}

fn config_get_value_inner(env: &mut JNIEnv, path: JString, key: JString) -> jstring {
    let path_str: String = match env.get_string(&path) {
        Ok(s) => s.into(),
        Err(_) => return jni_string(env, "null"),
    };
    let key_str: String = match env.get_string(&key) {
        Ok(s) => s.into(),
        Err(_) => return jni_string(env, "null"),
    };

    let val = config::get_value(&path_str, &key_str);
    match env.new_string(&val) {
        Ok(s) => s.into_raw(),
        Err(_) => jni_string(env, "null"),
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_fun_bm_mili_rust_RustBridge_configRemove(
    mut env: JNIEnv,
    _: JClass,
    path: JString,
    key: JString,
) -> jboolean {
    let result = std::panic::catch_unwind(AssertUnwindSafe(|| {
        config_remove_inner(&mut env, path, key)
    }));
    result.unwrap_or(0)
}

fn config_remove_inner(env: &mut JNIEnv, path: JString, key: JString) -> jboolean {
    let path_str: String = match env.get_string(&path) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };
    let key_str: String = match env.get_string(&key) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };

    if config::remove_key(&path_str, &key_str) {
        1
    } else {
        0
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_fun_bm_mili_rust_RustBridge_configClear(
    mut env: JNIEnv,
    _: JClass,
    path: JString,
) -> jboolean {
    let result = std::panic::catch_unwind(AssertUnwindSafe(|| config_clear_inner(&mut env, path)));
    result.unwrap_or(0)
}

fn config_clear_inner(env: &mut JNIEnv, path: JString) -> jboolean {
    let path_str: String = match env.get_string(&path) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };

    if config::clear_config(&path_str) {
        1
    } else {
        0
    }
}

// ============================================================================
// JNI helper — safe string creation without unwrap()
// ============================================================================

/// Create a JNI string from a Rust &str, returning a raw pointer.
/// On failure returns a null pointer (should not happen in practice).
fn jni_string(env: &mut JNIEnv, s: &str) -> jstring {
    match env.new_string(s) {
        Ok(js) => js.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

/// Convenience for creating an empty JNI string.
fn jni_string_or_empty(env: &mut JNIEnv) -> jstring {
    jni_string(env, "")
}
