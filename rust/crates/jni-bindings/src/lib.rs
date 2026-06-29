use capture_core::VpnEngine;
use jni::objects::{JByteArray, JClass, JString};
use jni::sys::{jbyteArray, jint, jlong};
use jni::JNIEnv;
use parser_core::HttpEngine;
use search_core::SearchEngine;
use storage_core::StorageEngine;
use tls_core::TlsEngine;

// ==========================================
// VpnEngine Bindings
// ==========================================

#[no_mangle]
pub unsafe extern "C" fn Java_com_kni_platform_vpn_NativeVpnEngine_vpn_1engine_1init(
    _env: JNIEnv,
    _class: JClass,
    tun_fd: jint,
) -> jlong {
    let engine = Box::into_raw(Box::new(VpnEngine::new(tun_fd)));
    engine as jlong
}

#[no_mangle]
pub unsafe extern "C" fn Java_com_kni_platform_vpn_NativeVpnEngine_vpn_1engine_1free(
    _env: JNIEnv,
    _class: JClass,
    engine_ptr: jlong,
) {
    if engine_ptr != 0 {
        let _ = Box::from_raw(engine_ptr as *mut VpnEngine);
    }
}

#[no_mangle]
pub unsafe extern "C" fn Java_com_kni_platform_vpn_NativeVpnEngine_vpn_1engine_1read_1packets(
    _env: JNIEnv,
    _class: JClass,
    engine_ptr: jlong,
) -> jint {
    let engine = &mut *(engine_ptr as *mut VpnEngine);
    engine.read_packets()
}

// ==========================================
// TlsEngine Bindings
// ==========================================

#[no_mangle]
pub unsafe extern "C" fn Java_com_kni_platform_security_NativeTlsEngine_tls_1engine_1new(
    env: JNIEnv,
    _class: JClass,
    root_ca_pem: JByteArray,
) -> jlong {
    let bytes = env.convert_byte_array(root_ca_pem).unwrap();
    let engine = Box::into_raw(Box::new(TlsEngine::new(&bytes)));
    engine as jlong
}

#[no_mangle]
pub unsafe extern "C" fn Java_com_kni_platform_security_NativeTlsEngine_tls_1engine_1free(
    _env: JNIEnv,
    _class: JClass,
    engine_ptr: jlong,
) {
    if engine_ptr != 0 {
        let _ = Box::from_raw(engine_ptr as *mut TlsEngine);
    }
}

#[no_mangle]
pub unsafe extern "C" fn Java_com_kni_platform_security_NativeTlsEngine_tls_1engine_1intercept_1handshake(
    mut env: JNIEnv,
    _class: JClass,
    engine_ptr: jlong,
    connection_id: jlong,
    sni: JString,
) -> jint {
    let engine = &mut *(engine_ptr as *mut TlsEngine);
    let sni_str: String = env.get_string(&sni).unwrap().into();
    match engine.intercept_handshake(connection_id as u64, &sni_str) {
        Ok(_) => 0,
        Err(_) => -1,
    }
}

// ==========================================
// HttpEngine Bindings
// ==========================================

#[no_mangle]
pub unsafe extern "C" fn Java_com_kni_platform_parser_NativeHttpEngine_http_1engine_1new(
    _env: JNIEnv,
    _class: JClass,
) -> jlong {
    let engine = Box::into_raw(Box::new(HttpEngine::new()));
    engine as jlong
}

#[no_mangle]
pub unsafe extern "C" fn Java_com_kni_platform_parser_NativeHttpEngine_http_1engine_1free(
    _env: JNIEnv,
    _class: JClass,
    engine_ptr: jlong,
) {
    if engine_ptr != 0 {
        let _ = Box::from_raw(engine_ptr as *mut HttpEngine);
    }
}

#[no_mangle]
pub unsafe extern "C" fn Java_com_kni_platform_parser_NativeHttpEngine_http_1engine_1parse_1stream(
    env: JNIEnv,
    _class: JClass,
    engine_ptr: jlong,
    stream_data: JByteArray,
) -> jbyteArray {
    let engine = &mut *(engine_ptr as *mut HttpEngine);
    let bytes = env.convert_byte_array(stream_data).unwrap();
    let result = engine.parse_stream(&bytes);
    let array = env.byte_array_from_slice(&result).unwrap();
    array.into_raw()
}

// ==========================================
// StorageEngine Bindings
// ==========================================

#[no_mangle]
pub unsafe extern "C" fn Java_com_kni_platform_storage_NativeStorageEngine_storage_1engine_1init(
    mut env: JNIEnv,
    _class: JClass,
    db_path: JString,
) -> jlong {
    let path_str: String = env.get_string(&db_path).unwrap().into();
    let engine = Box::into_raw(Box::new(StorageEngine::new(&path_str)));
    engine as jlong
}

#[no_mangle]
pub unsafe extern "C" fn Java_com_kni_platform_storage_NativeStorageEngine_storage_1engine_1free(
    _env: JNIEnv,
    _class: JClass,
    engine_ptr: jlong,
) {
    if engine_ptr != 0 {
        let _ = Box::from_raw(engine_ptr as *mut StorageEngine);
    }
}

#[no_mangle]
pub unsafe extern "C" fn Java_com_kni_platform_storage_NativeStorageEngine_storage_1engine_1write_1exchange(
    env: JNIEnv,
    _class: JClass,
    engine_ptr: jlong,
    exchange_cbor: JByteArray,
) -> jint {
    let engine = &mut *(engine_ptr as *mut StorageEngine);
    let _bytes = env.convert_byte_array(exchange_cbor).unwrap();
    match engine.write_exchange(
        "mock_id",
        "mock_session",
        "GET",
        "https://example.com",
        Some(200),
        123456789,
        Some(15),
        "{}",
        "{}",
        "",
        "",
    ) {
        Ok(_) => 0,
        Err(_) => -1,
    }
}

// ==========================================
// SearchEngine Bindings
// ==========================================

#[no_mangle]
pub unsafe extern "C" fn Java_com_kni_platform_search_NativeSearchEngine_search_1engine_1new(
    mut env: JNIEnv,
    _class: JClass,
    db_path: JString,
) -> jlong {
    let path_str: String = env.get_string(&db_path).unwrap().into();
    let engine = Box::into_raw(Box::new(SearchEngine::new(&path_str)));
    engine as jlong
}

#[no_mangle]
pub unsafe extern "C" fn Java_com_kni_platform_search_NativeSearchEngine_search_1engine_1free(
    _env: JNIEnv,
    _class: JClass,
    engine_ptr: jlong,
) {
    if engine_ptr != 0 {
        let _ = Box::from_raw(engine_ptr as *mut SearchEngine);
    }
}

#[no_mangle]
pub unsafe extern "C" fn Java_com_kni_platform_search_NativeSearchEngine_search_1engine_1query(
    mut env: JNIEnv,
    _class: JClass,
    engine_ptr: jlong,
    query_str: JString,
) -> jbyteArray {
    let engine = &mut *(engine_ptr as *mut SearchEngine);
    let query: String = env.get_string(&query_str).unwrap().into();
    let results = engine.query(&query);
    let array = env.byte_array_from_slice(&results).unwrap();
    array.into_raw()
}
