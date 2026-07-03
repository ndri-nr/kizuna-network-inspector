//! JVM <-> Rust bridge. Every entry point catches unwinds and returns a
//! structured code / empty buffer instead of panicking across the FFI boundary
//! (docs/00_ENGINEERING_RULES §7). Complex data crosses as CBOR.

use capture_core::{ProtectFn, VpnEngine};
use jni::objects::{JByteArray, JClass, JObject, JString, JValue};
use jni::sys::{jbyteArray, jint, jlong};
use jni::{JNIEnv, JavaVM};
use parser_core::HttpEngine;
use search_core::SearchEngine;
use std::panic::{catch_unwind, AssertUnwindSafe};
use storage_core::{HttpExchange, StorageEngine};
use tls_core::TlsEngine;

fn empty_byte_array(env: &mut JNIEnv) -> jbyteArray {
    env.byte_array_from_slice(&[])
        .map(|a| a.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

// ==========================================
// VpnEngine
// ==========================================

#[no_mangle]
pub extern "C" fn Java_com_kni_platform_vpn_NativeVpnEngine_vpn_1engine_1init(
    mut env: JNIEnv,
    _class: JClass,
    tun_fd: jint,
    db_path: JString,
    ca_dir: JString,
    decrypt: jni::sys::jboolean,
    service: JObject,
) -> jlong {
    let result = catch_unwind(AssertUnwindSafe(|| {
        let path: String = env.get_string(&db_path).ok()?.into();
        let ca: String = env.get_string(&ca_dir).ok()?.into();
        let jvm: JavaVM = env.get_java_vm().ok()?;
        let service_ref = env.new_global_ref(service).ok()?;

        // protect(fd) -> VpnService.protect(int): Boolean, called on the capture thread.
        let protect: ProtectFn = Box::new(move |fd: i32| -> bool {
            let mut env = match jvm.attach_current_thread() {
                Ok(e) => e,
                Err(_) => return false,
            };
            match env.call_method(&service_ref, "protect", "(I)Z", &[JValue::Int(fd)]) {
                Ok(v) => v.z().unwrap_or(false),
                Err(_) => false,
            }
        });

        let engine = Box::new(VpnEngine::new(tun_fd, &path, &ca, decrypt != 0, protect));
        Some(Box::into_raw(engine) as jlong)
    }));
    result.ok().flatten().unwrap_or(0)
}

#[no_mangle]
pub extern "C" fn Java_com_kni_platform_vpn_NativeVpnEngine_vpn_1engine_1set_1decrypt(
    _env: JNIEnv,
    _class: JClass,
    engine_ptr: jlong,
    decrypt: jni::sys::jboolean,
) {
    if engine_ptr == 0 {
        return;
    }
    let _ = catch_unwind(AssertUnwindSafe(|| {
        let engine = unsafe { &*(engine_ptr as *const VpnEngine) };
        engine.set_decrypt(decrypt != 0);
    }));
}

#[no_mangle]
pub extern "C" fn Java_com_kni_platform_vpn_NativeVpnEngine_vpn_1engine_1run(
    _env: JNIEnv,
    _class: JClass,
    engine_ptr: jlong,
) -> jint {
    if engine_ptr == 0 {
        return -1;
    }
    let r = catch_unwind(AssertUnwindSafe(|| {
        let engine = unsafe { &*(engine_ptr as *const VpnEngine) };
        engine.run()
    }));
    r.unwrap_or(-1)
}

#[no_mangle]
pub extern "C" fn Java_com_kni_platform_vpn_NativeVpnEngine_vpn_1engine_1stop(
    _env: JNIEnv,
    _class: JClass,
    engine_ptr: jlong,
) {
    if engine_ptr == 0 {
        return;
    }
    let _ = catch_unwind(AssertUnwindSafe(|| {
        let engine = unsafe { &*(engine_ptr as *const VpnEngine) };
        engine.stop();
    }));
}

#[no_mangle]
pub extern "C" fn Java_com_kni_platform_vpn_NativeVpnEngine_vpn_1engine_1set_1paused(
    _env: JNIEnv,
    _class: JClass,
    engine_ptr: jlong,
    paused: jni::sys::jboolean,
) {
    if engine_ptr == 0 {
        return;
    }
    let _ = catch_unwind(AssertUnwindSafe(|| {
        let engine = unsafe { &*(engine_ptr as *const VpnEngine) };
        engine.set_paused(paused != 0);
    }));
}

/// Returns packets processed since start (for Diagnostics).
#[no_mangle]
pub extern "C" fn Java_com_kni_platform_vpn_NativeVpnEngine_vpn_1engine_1stats(
    _env: JNIEnv,
    _class: JClass,
    engine_ptr: jlong,
) -> jlong {
    if engine_ptr == 0 {
        return 0;
    }
    catch_unwind(AssertUnwindSafe(|| {
        let engine = unsafe { &*(engine_ptr as *const VpnEngine) };
        engine.stats().0 as jlong
    }))
    .unwrap_or(0)
}

#[no_mangle]
pub extern "C" fn Java_com_kni_platform_vpn_NativeVpnEngine_vpn_1engine_1free(
    _env: JNIEnv,
    _class: JClass,
    engine_ptr: jlong,
) {
    if engine_ptr != 0 {
        unsafe {
            let _ = Box::from_raw(engine_ptr as *mut VpnEngine);
        }
    }
}

// ==========================================
// TlsEngine (Root CA)
// ==========================================

#[no_mangle]
pub extern "C" fn Java_com_kni_platform_security_NativeTlsEngine_tls_1engine_1new(
    mut env: JNIEnv,
    _class: JClass,
    ca_dir: JString,
) -> jlong {
    let result = catch_unwind(AssertUnwindSafe(|| {
        let dir: String = env.get_string(&ca_dir).ok()?.into();
        let engine = TlsEngine::new(&dir).ok()?;
        Some(Box::into_raw(Box::new(engine)) as jlong)
    }));
    result.ok().flatten().unwrap_or(0)
}

#[no_mangle]
pub extern "C" fn Java_com_kni_platform_security_NativeTlsEngine_tls_1engine_1get_1ca_1pem(
    mut env: JNIEnv,
    _class: JClass,
    engine_ptr: jlong,
) -> jbyteArray {
    if engine_ptr == 0 {
        return empty_byte_array(&mut env);
    }
    let r = catch_unwind(AssertUnwindSafe(|| {
        let engine = unsafe { &*(engine_ptr as *const TlsEngine) };
        env.byte_array_from_slice(engine.ca_cert_pem().as_bytes())
            .map(|a| a.into_raw())
            .unwrap_or(std::ptr::null_mut())
    }));
    match r {
        Ok(a) => a,
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "C" fn Java_com_kni_platform_security_NativeTlsEngine_tls_1engine_1free(
    _env: JNIEnv,
    _class: JClass,
    engine_ptr: jlong,
) {
    if engine_ptr != 0 {
        unsafe {
            let _ = Box::from_raw(engine_ptr as *mut TlsEngine);
        }
    }
}

// ==========================================
// HttpEngine (kept for symbol compatibility)
// ==========================================

#[no_mangle]
pub extern "C" fn Java_com_kni_platform_parser_NativeHttpEngine_http_1engine_1new(
    _env: JNIEnv,
    _class: JClass,
) -> jlong {
    Box::into_raw(Box::new(HttpEngine::new())) as jlong
}

#[no_mangle]
pub extern "C" fn Java_com_kni_platform_parser_NativeHttpEngine_http_1engine_1free(
    _env: JNIEnv,
    _class: JClass,
    engine_ptr: jlong,
) {
    if engine_ptr != 0 {
        unsafe {
            let _ = Box::from_raw(engine_ptr as *mut HttpEngine);
        }
    }
}

#[no_mangle]
pub extern "C" fn Java_com_kni_platform_parser_NativeHttpEngine_http_1engine_1parse_1stream(
    mut env: JNIEnv,
    _class: JClass,
    engine_ptr: jlong,
    stream_data: JByteArray,
) -> jbyteArray {
    if engine_ptr == 0 {
        return empty_byte_array(&mut env);
    }
    let r = catch_unwind(AssertUnwindSafe(|| {
        let engine = unsafe { &mut *(engine_ptr as *mut HttpEngine) };
        let bytes = env.convert_byte_array(stream_data).unwrap_or_default();
        let result = engine.parse_stream(&bytes);
        env.byte_array_from_slice(&result)
            .map(|a| a.into_raw())
            .unwrap_or(std::ptr::null_mut())
    }));
    r.unwrap_or(std::ptr::null_mut())
}

// ==========================================
// StorageEngine
// ==========================================

#[no_mangle]
pub extern "C" fn Java_com_kni_platform_storage_NativeStorageEngine_storage_1engine_1init(
    mut env: JNIEnv,
    _class: JClass,
    db_path: JString,
) -> jlong {
    let result = catch_unwind(AssertUnwindSafe(|| {
        let path: String = env.get_string(&db_path).ok()?.into();
        Some(Box::into_raw(Box::new(StorageEngine::new(&path))) as jlong)
    }));
    result.ok().flatten().unwrap_or(0)
}

#[no_mangle]
pub extern "C" fn Java_com_kni_platform_storage_NativeStorageEngine_storage_1engine_1free(
    _env: JNIEnv,
    _class: JClass,
    engine_ptr: jlong,
) {
    if engine_ptr != 0 {
        unsafe {
            let _ = Box::from_raw(engine_ptr as *mut StorageEngine);
        }
    }
}

/// Decode a CBOR `HttpExchange` and persist it. Returns 0 on success, -1 on error.
#[no_mangle]
pub extern "C" fn Java_com_kni_platform_storage_NativeStorageEngine_storage_1engine_1write_1exchange(
    env: JNIEnv,
    _class: JClass,
    engine_ptr: jlong,
    exchange_cbor: JByteArray,
) -> jint {
    if engine_ptr == 0 {
        return -1;
    }
    let r = catch_unwind(AssertUnwindSafe(|| {
        let engine = unsafe { &*(engine_ptr as *const StorageEngine) };
        let bytes = env.convert_byte_array(exchange_cbor).ok()?;
        let ex: HttpExchange = serde_cbor::from_slice(&bytes).ok()?;
        engine.write_exchange(&ex).ok()?;
        Some(0)
    }));
    r.ok().flatten().unwrap_or(-1)
}

fn json_string(env: &mut JNIEnv, json: &str) -> jni::sys::jstring {
    env.new_string(json)
        .map(|s| s.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

/// Return exchanges with timestamp > `since` as a JSON array string.
#[no_mangle]
pub extern "C" fn Java_com_kni_platform_storage_NativeStorageEngine_storage_1engine_1read_1since(
    mut env: JNIEnv,
    _class: JClass,
    engine_ptr: jlong,
    since: jlong,
) -> jni::sys::jstring {
    if engine_ptr == 0 {
        return json_string(&mut env, "[]");
    }
    let r = catch_unwind(AssertUnwindSafe(|| {
        let engine = unsafe { &*(engine_ptr as *const StorageEngine) };
        let rows = engine.read_since(since).unwrap_or_default();
        let json = serde_json::to_string(&rows).unwrap_or_else(|_| "[]".to_string());
        json_string(&mut env, &json)
    }));
    r.unwrap_or_else(|_| std::ptr::null_mut())
}

/// Return a single exchange by id as a JSON object string, or "null" if absent.
#[no_mangle]
pub extern "C" fn Java_com_kni_platform_storage_NativeStorageEngine_storage_1engine_1read_1by_1id(
    mut env: JNIEnv,
    _class: JClass,
    engine_ptr: jlong,
    id: JString,
) -> jni::sys::jstring {
    if engine_ptr == 0 {
        return json_string(&mut env, "null");
    }
    let r = catch_unwind(AssertUnwindSafe(|| {
        let engine = unsafe { &*(engine_ptr as *const StorageEngine) };
        let id_str: String = match env.get_string(&id) {
            Ok(s) => s.into(),
            Err(_) => return json_string(&mut env, "null"),
        };
        let row = engine.read_by_id(&id_str).ok().flatten();
        let json = serde_json::to_string(&row).unwrap_or_else(|_| "null".to_string());
        json_string(&mut env, &json)
    }));
    r.unwrap_or_else(|_| std::ptr::null_mut())
}

#[no_mangle]
pub extern "C" fn Java_com_kni_platform_storage_NativeStorageEngine_storage_1engine_1count(
    _env: JNIEnv,
    _class: JClass,
    engine_ptr: jlong,
) -> jlong {
    if engine_ptr == 0 {
        return 0;
    }
    catch_unwind(AssertUnwindSafe(|| {
        let engine = unsafe { &*(engine_ptr as *const StorageEngine) };
        engine.count().unwrap_or(0) as jlong
    }))
    .unwrap_or(0)
}

// ==========================================
// SearchEngine
// ==========================================

#[no_mangle]
pub extern "C" fn Java_com_kni_platform_search_NativeSearchEngine_search_1engine_1new(
    mut env: JNIEnv,
    _class: JClass,
    db_path: JString,
) -> jlong {
    let result = catch_unwind(AssertUnwindSafe(|| {
        let path: String = env.get_string(&db_path).ok()?.into();
        Some(Box::into_raw(Box::new(SearchEngine::new(&path))) as jlong)
    }));
    result.ok().flatten().unwrap_or(0)
}

#[no_mangle]
pub extern "C" fn Java_com_kni_platform_search_NativeSearchEngine_search_1engine_1free(
    _env: JNIEnv,
    _class: JClass,
    engine_ptr: jlong,
) {
    if engine_ptr != 0 {
        unsafe {
            let _ = Box::from_raw(engine_ptr as *mut SearchEngine);
        }
    }
}

#[no_mangle]
pub extern "C" fn Java_com_kni_platform_search_NativeSearchEngine_search_1engine_1query(
    mut env: JNIEnv,
    _class: JClass,
    engine_ptr: jlong,
    query_str: JString,
) -> jbyteArray {
    if engine_ptr == 0 {
        return empty_byte_array(&mut env);
    }
    let r = catch_unwind(AssertUnwindSafe(|| {
        let engine = unsafe { &*(engine_ptr as *const SearchEngine) };
        let query: String = match env.get_string(&query_str) {
            Ok(s) => s.into(),
            Err(_) => return std::ptr::null_mut(),
        };
        let results = engine.query(&query);
        env.byte_array_from_slice(&results)
            .map(|a| a.into_raw())
            .unwrap_or(std::ptr::null_mut())
    }));
    r.unwrap_or(std::ptr::null_mut())
}
