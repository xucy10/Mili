use jni::objects::{JByteBuffer, JClass, JString};
use jni::sys::{jboolean, jbyteArray, jdouble, jfloatArray, jint, jsize, jstring};
/// JNI bridge — exposes Rust optimization functions to Java via JNI.
///
/// Design: **bulk processing only** for hot paths. Java collects per-tick data
/// into flat arrays or DirectByteBuffers, Rust processes everything in one call.
///
/// Zero-copy path: Java passes a DirectByteBuffer; Rust reads via
/// GetDirectBufferAddress — no array copy across the JNI boundary.
use jni::JNIEnv;

use crate::{config, entity_cull, frustum};

// ============================================================================
// Native init
// ============================================================================

#[no_mangle]
pub extern "system" fn Java_fun_bm_mili_rust_RustBridge_nativeInit(_env: JNIEnv, _class: JClass) {}

// ============================================================================
// Entity culling — zero-copy via DirectByteBuffer
// ============================================================================

#[no_mangle]
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
    let entity_addr = match env.get_direct_buffer_address(&entity_buffer) {
        Ok(addr) => addr as *const f32,
        Err(_) => return std::ptr::null_mut(),
    };

    let planes_addr = match env.get_direct_buffer_address(&planes_buffer) {
        Ok(addr) => addr as *const f32,
        Err(_) => return std::ptr::null_mut(),
    };

    let planes_slice = unsafe { std::slice::from_raw_parts(planes_addr, 24) };
    let mut planes = [[0.0f32; 4]; 6];
    for i in 0..6 {
        planes[i] = [
            planes_slice[i * 4],
            planes_slice[i * 4 + 1],
            planes_slice[i * 4 + 2],
            planes_slice[i * 4 + 3],
        ];
    }
    let frustum = frustum::Frustum { planes };

    let results = entity_cull::batch_cull_entities_zero_copy(
        entity_addr,
        num_entities as usize,
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
// Entity culling — array fallback (non-zero-copy)
// ============================================================================

#[no_mangle]
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

    let mut planes = [[0.0f32; 4]; 6];
    for i in 0..6 {
        planes[i] = [
            planes_data[i * 4],
            planes_data[i * 4 + 1],
            planes_data[i * 4 + 2],
            planes_data[i * 4 + 3],
        ];
    }
    let frustum = frustum::Frustum { planes };

    let results = entity_cull::batch_cull_entities(
        &entity_data,
        num_entities as usize,
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

#[no_mangle]
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

#[no_mangle]
pub extern "system" fn Java_fun_bm_mili_rust_RustBridge_configLoad(
    mut env: JNIEnv,
    _: JClass,
    path: JString,
) -> jstring {
    let path_str: String = match env.get_string(&path) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("").unwrap().into_raw(),
    };

    match config::load_config(&path_str) {
        Some(json) => env.new_string(&json).unwrap().into_raw(),
        None => env.new_string("").unwrap().into_raw(),
    }
}

#[no_mangle]
pub extern "system" fn Java_fun_bm_mili_rust_RustBridge_configSave(
    mut env: JNIEnv,
    _: JClass,
    path: JString,
    json: JString,
) -> jboolean {
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

#[no_mangle]
pub extern "system" fn Java_fun_bm_mili_rust_RustBridge_configSaveMerge(
    mut env: JNIEnv,
    _: JClass,
    path: JString,
    json: JString,
) -> jboolean {
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

#[no_mangle]
pub extern "system" fn Java_fun_bm_mili_rust_RustBridge_configContains(
    mut env: JNIEnv,
    _: JClass,
    path: JString,
    key: JString,
) -> jboolean {
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

#[no_mangle]
pub extern "system" fn Java_fun_bm_mili_rust_RustBridge_configGetValue(
    mut env: JNIEnv,
    _: JClass,
    path: JString,
    key: JString,
) -> jstring {
    let path_str: String = match env.get_string(&path) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("null").unwrap().into_raw(),
    };
    let key_str: String = match env.get_string(&key) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("null").unwrap().into_raw(),
    };

    let val = config::get_value(&path_str, &key_str);
    env.new_string(&val).unwrap().into_raw()
}

#[no_mangle]
pub extern "system" fn Java_fun_bm_mili_rust_RustBridge_configRemove(
    mut env: JNIEnv,
    _: JClass,
    path: JString,
    key: JString,
) -> jboolean {
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

#[no_mangle]
pub extern "system" fn Java_fun_bm_mili_rust_RustBridge_configClear(
    mut env: JNIEnv,
    _: JClass,
    path: JString,
) -> jboolean {
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
