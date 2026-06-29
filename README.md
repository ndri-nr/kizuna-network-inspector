# Kizuna Network Inspector (KNI)

Kizuna Network Inspector is a high-performance network analysis and traffic inspection tool for Android, powered by a shared Rust core and a Jetpack Compose frontend.

## Project Structure

- `/rust`: Shared cross-platform core written in Rust.
- `/android`: Android client implementation.
- `/docs`: Comprehensive engine specifications and rules.

---

## How to Build and Run KNI

### 1. Compile the Rust Shared Core

You can compile the native libraries either using Docker (recommended for clean builds) or directly on your host machine.

#### Option A: Building with Docker (Recommended)

1. Build the Docker builder image:
   ```bash
   cd rust
   docker build -t kni-rust-builder .
   ```

2. Run the compiler task to compile architectures:
   ```bash
   docker run --rm -v "$(pwd)/..:/usr/src/kni-rust-core" kni-rust-builder cargo ndk -t arm64-v8a -t x86_64 -o /usr/src/kni-rust-core/android/app/src/main/jniLibs build --release
   ```

#### Option B: Building Locally

1. Install the Android NDK (r25+) and configure your local environmental variable:
   ```bash
   export ANDROID_NDK_HOME=~/Library/Android/sdk/ndk/30.0.14904198
   ```

2. Install `cargo-ndk` and add target architectures:
   ```bash
   cargo install cargo-ndk
   rustup target add aarch64-linux-android x86_64-linux-android
   ```

3. Compile and output to the Android `jniLibs` directory:
   ```bash
   cd rust
   cargo ndk -t arm64-v8a -t x86_64 -o ../android/app/src/main/jniLibs build --release
   ```

---

### 2. Run the Android Client Application

1. Connect an Android device or launch an emulator (API level 29+ recommended).
2. From the `android` directory, compile and install the debug app build:
   ```bash
   cd android
   ./gradlew installDebug
   ```

3. Launch the application, grant VPN permissions when prompted, and monitor intercepted network traffic.

---

## Verification & Testing

### Running Rust Core Tests
```bash
cd rust
cargo test --all
```

### Running Android Unit Tests
```bash
cd android
./gradlew test
```
