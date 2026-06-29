# 05_TECH_STACK.md

> Project: Kizuna Network Inspector
>
> Parent:
> MASTER_SPEC.md
>
> Version: 1.0
>
> Status: Draft

---

# 1. Purpose

This document defines the official technology stack of Kizuna Network Inspector.

Its purpose is to standardize implementation technologies, reduce architectural drift, and ensure long-term maintainability across Android, iOS, Desktop, and future platforms.

All technology choices must align with the principles defined in `MASTER_SPEC.md`.

---

# 2. Technology Selection Principles

Every technology adopted by KNI should satisfy as many of the following criteria as possible:

- Open source
- Actively maintained
- Well documented
- Cross-platform friendly
- High performance
- Production proven
- Minimal dependencies
- Strong testing ecosystem
- Long-term viability
- Permissive licensing

Technology adoption should be conservative. Stability is preferred over novelty.

---

# 3. Platform Overview

| Platform | Language | UI Framework |
|----------|----------|--------------|
| Android | Kotlin | Jetpack Compose |
| iOS | Swift | SwiftUI |
| Shared Runtime | Rust | None |
| Desktop (Future) | Rust + Tauri | Tauri UI |
| CLI (Future) | Rust | Terminal |

---

# 4. Android Stack

## Language

Kotlin

Reason

- Official Android language
- Excellent coroutine support
- Strong interoperability
- Mature ecosystem

Alternatives Considered

- Java
- Flutter
- React Native

Decision

Kotlin provides the best integration with Android platform APIs.

---

## UI

Jetpack Compose

Reason

- Declarative UI
- Official toolkit
- Excellent state handling
- High productivity

Alternatives

- XML Views

Decision

Compose is mandatory for all new UI.

---

## Architecture

Clean Architecture

Capability-Based Modularization

Repository Pattern

Use Case Pattern

MVVM (Presentation only)

---

## Dependency Injection

Koin

Reason

- Lightweight
- Kotlin-first
- No annotation processing
- Faster build times

Alternatives

- Hilt
- Dagger

Decision

Koin is the standard DI framework.

---

## Concurrency

Kotlin Coroutines

Flow

StateFlow

Channels (only where appropriate)

Reason

Unified asynchronous programming model.

---

## Navigation

Navigation Compose

Reason

Official solution.

---

## Local Storage

Room

SQLite

Reason

Reliable local persistence.

---

## Serialization

kotlinx.serialization

Reason

Fast

Multiplatform support

Compile-time safety

---

## Logging

Timber

Reason

Simple

Flexible

Widely adopted

---

## Preferences

DataStore

Reason

Type-safe

Coroutine friendly

Replaces SharedPreferences

---

# 5. Rust Runtime Stack

## Language

Rust (stable)

Reason

- Memory safety
- High performance
- Excellent concurrency
- Cross-platform
- No garbage collector

Rust is the implementation language for the Runtime.

---

## Async Runtime

Tokio

Reason

Industry standard

Excellent ecosystem

---

## Serialization

Serde

Reason

Fast

Widely supported

---

## Error Handling

thiserror

anyhow (application layer only)

Reason

Clear separation between library and application errors.

---

## Logging

tracing

Reason

Structured logging

Excellent diagnostics

---

## Database

rusqlite

Reason

Lightweight

Reliable

SQLite native

---

## Compression

zstd

Reason

Excellent compression ratio

Fast decompression

---

## Cryptography

ring

Reason

Well audited

Performance focused

---

# 6. iOS Stack

## Language

Swift

## UI

SwiftUI

## Networking

Network Extension

Reason

Required for VPN-based traffic interception.

---

# 7. Desktop Stack (Future)

Framework

Tauri

Reason

- Rust integration
- Lightweight
- Native performance

Alternatives

- Electron

Decision

Tauri preferred.

---

# 8. Testing Stack

Android

JUnit 5

MockK

Turbine

Truth

Compose UI Test

Rust

cargo test

criterion (benchmarks)

proptest (property testing)

Swift

XCTest

---

# 9. Build Tools

Android

Gradle

Kotlin DSL

Rust

Cargo

Desktop

Cargo

Tauri CLI

---

# 10. Static Analysis

Android

Detekt

ktlint

Rust

clippy

rustfmt

Swift

SwiftLint

---

# 11. CI/CD

GitHub Actions

Required Checks

- Build
- Unit Tests
- Static Analysis
- Formatting
- Documentation Validation

---

# 12. Documentation

Markdown

Mermaid

PlantUML (optional)

Architecture Decision Records (ADR)

Request for Comments (RFC)

---

# 13. Dependency Policy

Every third-party dependency must satisfy:

- Active maintenance
- Compatible license
- Security review
- Justified usage
- Minimal footprint

Dependencies should not duplicate existing functionality.

---

# 14. Versioning Strategy

Semantic Versioning (SemVer)

Examples

0.1.0

0.5.0

1.0.0

2.0.0

Breaking changes require a major version increment.

---

# 15. Branch Strategy

main

Production-ready code.

develop

Integration branch.

feature/<name>

New features.

bugfix/<name>

Bug fixes.

release/<version>

Release preparation.

hotfix/<version>

Critical production fixes.

---

# 16. Release Targets

Milestone 1

Android MVP

Milestone 2

HTTPS Inspection

Milestone 3

Replay

Milestone 4

Export

Milestone 5

Cross-Platform Runtime

Milestone 6

Desktop Companion

---

# 17. Future Technologies

Potential future additions:

- HTTP/3 (QUIC)
- gRPC
- GraphQL
- MQTT
- OpenTelemetry exporters
- WASM-based plugins
- AI-assisted analysis
- Remote debugging protocol

Adoption requires an ADR.

---

# 18. Technology Constraints

The following are intentionally avoided unless an ADR approves them:

- Reflection-heavy frameworks
- Runtime code generation
- Global mutable state
- Platform-specific business logic
- Closed-source core dependencies

---

# 19. Success Criteria

The technology stack is considered successful if:

- Shared Runtime is reusable across platforms.
- Android and iOS remain thin presentation layers.
- Build times remain reasonable.
- Dependencies remain manageable.
- New capabilities can be added with minimal architectural impact.

---

# 20. References

MASTER_SPEC.md

02_ARCHITECTURE.md

03_PROJECT_STRUCTURE.md

04_PLATFORM_KERNEL.md