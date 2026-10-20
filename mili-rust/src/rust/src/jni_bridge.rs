use jni::objects::{JByteBuffer, JClass, JString};
use jni::sys::{jboolean, jbyteArray, jdouble, jfloatArray, jint, jsize, jstring};
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

use crate::{config, entity_cull, frustum};

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
