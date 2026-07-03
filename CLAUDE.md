# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Kizuna Network Inspector (KNI) — an Android network traffic inspection tool (MITM proxy via `VpnService`) whose analysis logic lives in a shared Rust core. Explicit design intent: Android is "the first client"; the Rust core is meant to be reused by future iOS/desktop/CLI clients, so heavy logic belongs in Rust, not Kotlin.

The `docs/` directory (30 numbered specs, `00`–`29`) is normative — architecture, engine contracts, and requirements are defined there and code is expected to conform. Consult the relevant spec before changing an engine's behavior. Current state is early (v0.1 "Capture" phase); several engines are skeletons/stubs (e.g. `storage_engine_write_exchange` ignores its CBOR arg and writes mock data), so treat much of the code as scaffolding against the spec.

## Two build systems, one artifact

The Rust workspace compiles to a single native lib `libkni_rust_core.so` that the Android app loads via `System.loadLibrary("kni_rust_core")`. **The Rust core must be built first** — its `.so` outputs land in `android/app/src/main/jniLibs/` and are consumed by the Gradle build.

### 1. Rust core → jniLibs (do this first)
```bash
cd rust
# Local build (needs ANDROID_NDK_HOME set, cargo-ndk, and android targets installed):
cargo ndk -t arm64-v8a -t x86_64 -o ../android/app/src/main/jniLibs build --release
# One-time setup:
cargo install cargo-ndk
rustup target add aarch64-linux-android x86_64-linux-android
```
Docker alternative (clean builds): `docker build -t kni-rust-builder .` then run the `cargo ndk ... build --release` command inside it (see README).

### 2. Android client
```bash
cd android
./gradlew installDebug     # build + install to connected device/emulator (API 29+)
```

### Tests
```bash
cd rust && cargo test --all              # Rust core; single crate: cargo test -p <crate>
cd android && ./gradlew test             # Android unit tests; single module: ./gradlew :ui:compose:test
```

## Architecture

**Rust core** (`rust/crates/`) — capability crates, one concern each: `capture-core`, `transport-core` (TCP reassembly), `tls-core` (MITM handshake), `parser-core` (HTTP), `storage-core` (SQLite via `rusqlite` bundled), `search-core` (FTS). `jni-bindings` is the *only* crate the JVM sees — it re-exports the others as `extern "C"` symbols and is the sole `cdylib`. Dependency direction is strictly downward (`capture → transport → tls → parser → storage`, `search → storage`); no cycles, no crate depends on a UI/platform module.

**Android** (`android/`) — Gradle multi-module (see `settings.gradle.kts`): `:app`, `:platform:vpn`, `:platform:security`, `:shared:database`, `:shared:search`, `:ui:compose`. Layering (UI → Application → Domain → Capability → Infra) is enforced; UI never parses packets or touches the DB directly, platform modules hold no business logic.

**Data flow** (one direction, no reverse mutation): `VpnService` tun fd → Rust reads packets → TCP reassembly → TLS → HTTP parse → session build → storage → search index → UI observes via Flow/StateFlow.

## JNI boundary — read before touching native code

The JVM↔Rust bridge is name-mangled by package path, and this bites in two non-obvious ways:

- **Kotlin package name IS part of the native symbol.** Each `external fun` maps to a `Java_<package>_<Class>_<method>` function in `rust/crates/jni-bindings/src/lib.rs`. Renaming/moving a Kotlin package or class silently breaks the link at runtime — update both sides together.
- **Gradle module ≠ Kotlin package.** The `:shared:database` module hosts `com.kni.platform.parser.NativeHttpEngine` *and* `com.kni.platform.storage.NativeStorageEngine`; `:platform:vpn` hosts `com.kni.platform.vpn.*`. Don't assume the directory/module name predicts the package the JNI symbol expects — check `lib.rs`.

Native-side rules (from `docs/00_ENGINEERING_RULES.md` §7): never panic across FFI (use catch-unwind, return structured error codes — convention here is `0` ok / `-1` err, or a byte array); every pointer allocated in Rust (`Box::into_raw`) must have a matching `*_free`/`destroy()`; pass complex data as CBOR byte arrays, not many JNI fields.

## Conventions

- Naming: types `PascalCase`, funcs/vars `camelCase`, **native bindings & DB columns `snake_case`**.
- Errors: Rust `Result<T,E>`, Kotlin `Result<T>`; no empty catch blocks.
- Coroutines: inject `Dispatchers`, never hardcode; no `GlobalScope` — use `viewModelScope` / lifecycle-scoped scopes.
- Package by capability (`capture`, `storage`, `search`), not by technical type (`helpers`, `adapters`).
- Security: bind MITM listeners to `127.0.0.1` only; CA cert lives in KeyStore; never transmit decrypted payloads off-device.
