# 27_CI_CD.md

> Project: Kizuna Network Inspector
>
> Parent:
> [00_MASTER_SPEC.md](file:///Users/andri/Documents/development.nosync/kizuna-network-inspector/docs/00_MASTER_SPEC.md)
>
> Version: 1.0
>
> Status: Draft
>
> Document Type:
> CI/CD Architecture Specification

---

## 1. Document Metadata

| Field | Value |
|---|---|
| Document | [27_CI_CD.md](file:///Users/andri/Documents/development.nosync/kizuna-network-inspector/docs/27_CI_CD.md) |
| Author | Kizuna Network Inspector DevOps Team |
| Version | 1.0.0-draft |
| Status | Draft |
| Target Platform | CI/CD Infrastructure |
| Last Updated | 2026-06-27 |

---

## 2. Purpose

This document specifies the Continuous Integration (CI) and Continuous Deployment (CD) workflows, build validation pipelines, release versioning logic, and deployment targets for KNI.

---

## 3. Scope

### In-Scope
- GitHub Actions pipeline configurations.
- Multi-platform compiler rules (Rust target compilation for Android `aarch64`/`x86_64` and iOS platforms).
- Automated static analysis checks (Detekt, Clippy, SwiftLint).
- Release tagging procedures.

### Out-of-Scope
- App Store or Play Store review submission strategies.

---

## 4. Definitions

- **JNI (Java Native Interface)**: Native code binding bridging Java/Kotlin to Rust binaries (`.so`).
- **XCFramework**: Package format by Apple containing library binaries across iOS architectures (`.a`).

---

## 5. Requirements

### Pipeline Definitions

| Workflow | Trigger Event | Tasks Executed | Pass/Fail Criteria |
|---|---|---|---|
| **Pull Request Validation** | Pull Request to `develop` or `main` | Linting, cargo tests, Android unit tests, iOS unit tests. | Must pass all tasks before merging. |
| **Nightly Build** | Cron schedule (Daily 02:00 UTC) | Heavy property testing, coverage report generation, dependencies security scanning. | Report anomalies to Slack/Email. |
| **Release Deployment** | Git tag push (`v*`) | Compile production Rust core, bundle Android AAR/APKs, generate iOS XCFramework, publish to GitHub Releases. | Automated release note generation must complete. |

---

## 6. Architecture (Compilation Pipeline)

```mermaid
graph TD
    Trigger[GitHub Actions Runner] --> RustBuild[Compile Rust Core]
    
    RustBuild -->|cargo lipo / target iOS| iOSLib[XCFramework]
    RustBuild -->|cargo apk / target Android| AndroidLib[JNI .so Libraries]
    
    AndroidLib --> AndroidPack[Gradle Build APK / AAR]
    iOSLib --> iOSPack[Xcode Archive IPA]
    
    AndroidPack --> OutputArtifacts[Production Build Outputs]
    iOSPack --> OutputArtifacts
```

---

## 7. Static Analysis & Quality Gates

Every build must pass the following checks:
- **Rust**: `cargo fmt --check` and `cargo clippy -- -D warnings`.
- **Android**: `./gradlew lint` and `detekt`.
- **iOS**: `swiftlint`.

---

## 8. Sequence Diagrams

### Production Release Automation

```mermaid
sequenceDiagram
    participant Dev as Core Developer
    participant Git as GitHub Repo
    participant CI as Actions Workflow
    participant GH as GitHub Releases

    Dev->>Git: Push Tag v1.0.0
    Git->>CI: Trigger Release Pipeline
    CI->>CI: Run Tests & Quality Gates
    CI->>CI: Compile cross-platform binaries (Rust JNI & XCFramework)
    CI->>CI: Build Android APK (Release) & iOS IPA
    CI->>GH: Upload signed release packages
    CI->>GH: Generate Release Notes (from git log)
    GH-->>Dev: Release v1.0.0 live
```

---

## 9. State Diagrams

### Workflow Run Lifecycle

```mermaid
stateDiagram-v2
    [*] --> Triggered
    Triggered --> Linting : Setup Environment
    Linting --> RustCompilation : Lint checks pass
    RustCompilation --> PlatformBuild : Core library compiled
    PlatformBuild --> Testing : Build success
    Testing --> Publishing : All test suites pass
    Publishing --> Completed : Artifacts uploaded
    Testing --> Failed : Any test fail
    PlatformBuild --> Failed : Compiler error
    Linting --> Failed : Code style mismatch
    Failed --> [*]
    Completed --> [*]
```

---

## 10. Implementation Notes

- **Docker Environment**: Android compilations run inside standard Ubuntu environments preloaded with NDK and SDK toolchains.
- **macOS Runners**: Apple builds (iOS XCFramework compilation) execute on macOS GitHub Actions runners.

---

## 11. Acceptance Criteria

- [ ] Every Pull Request requires a green pipeline pass before merging is unlocked.
- [ ] Compiling the Rust shared core supports all target mobile processor architectures.
- [ ] Build logs are organized and clear, facilitating troubleshooting of compile issues.
- [ ] Build artifacts (APKs/IPAs) are automatically cryptographically signed before release uploading.

---

## 12. Future Improvements

- **Self-Hosted Runner Infrastructure**: Migrating compilation steps to dedicated team servers to speed up build runs.
- **Auto-Submit to Stores**: Automatically push successful release builds to Google Play Internal Sharing and Apple TestFlight.
