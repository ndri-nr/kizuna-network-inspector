# Base build image containing Rust toolchain
FROM rust:1.70-slim-bookworm

# Install build essential toolchain, curl, and compilation utilities
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    curl \
    unzip \
    libssl-dev \
    pkg-config \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Set environment variables for Android NDK and SDK
ENV ANDROID_NDK_VERSION=r25c
ENV ANDROID_NDK_HOME=/opt/android-ndk-${ANDROID_NDK_VERSION}
ENV PATH=${PATH}:${ANDROID_NDK_HOME}

# Download and extract Android NDK for native cross-compilation
WORKDIR /opt
RUN curl -L https://dl.google.com/android/repository/android-ndk-${ANDROID_NDK_VERSION}-linux.zip -o ndk.zip \
    && unzip ndk.zip \
    && rm ndk.zip

# Install cargo-ndk for automated target compilations
RUN cargo install cargo-ndk

# Register target architectures for target execution
RUN rustup target add aarch64-linux-android x86_64-linux-android

# Setup workspace directory
WORKDIR /usr/src/kni-rust-core
COPY . .

# Run cargo compilation checks by default
CMD ["cargo", "check", "--all"]
