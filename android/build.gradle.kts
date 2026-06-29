buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.7.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.0")
        classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.0.0")
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}

// Rust Cargo NDK build task integration
tasks.register<Exec>("buildRustCore") {
    workingDir = file("../rust")
    commandLine("cargo", "ndk", "-t", "arm64-v8a", "-t", "x86_64", "-o", "../android/app/src/main/jniLibs", "build", "--release")
}
