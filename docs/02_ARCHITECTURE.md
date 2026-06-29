# 02_ARCHITECTURE.md

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

This document defines the official architecture of Kizuna Network Inspector.

It establishes the architectural principles, dependency rules, capability boundaries, communication patterns, and module interactions that every implementation must follow.

This document is normative.

All code must conform to this specification.

---

# 2. Architecture Philosophy

KNI is not designed as a mobile application.

KNI is designed as a platform.

Android is only the first client.

Future clients include

• iOS

• Desktop

• CLI

• Remote Debugger

• Plugin SDK

Therefore every architectural decision must maximize reuse.

---

# 3. Architectural Goals

AG-001

Independent modules.

---

AG-002

Shared business logic.

---

AG-003

Platform-specific code isolated.

---

AG-004

No cyclic dependencies.

---

AG-005

Capability-based modules.

---

AG-006

Testable components.

---

AG-007

Replaceable implementations.

---

AG-008

Minimal coupling.

---

AG-009

High cohesion.

---

AG-010

Scalable architecture.

---

# 4. Architecture Style

The platform combines multiple architectural patterns.

• Clean Architecture

• Hexagonal Architecture

• Capability-Based Modularization

• Domain Driven Design

• Event Driven Communication

• Repository Pattern

• Strategy Pattern

• Factory Pattern

No single architecture is sufficient.

Each pattern is applied where appropriate.

---

# 5. System Layers

```text
┌────────────────────────────┐
│      UI Applications       │
├────────────────────────────┤
│     Platform Adapters      │
├────────────────────────────┤
│      Application Layer     │
├────────────────────────────┤
│       Domain Layer         │
├────────────────────────────┤
│     Capability Engines     │
├────────────────────────────┤
│     Infrastructure Layer   │
└────────────────────────────┘
```

---

# 6. Layer Responsibilities

## UI

Compose

SwiftUI

Desktop

Responsibilities

• Rendering

• User Interaction

• Navigation

Never

• Parse packets

• Read database directly

• Process TLS

---

## Platform

Responsibilities

Android VPN

iOS Network Extension

Permissions

Notifications

Foreground Service

Never

Business Logic

---

## Application

Responsibilities

Use Cases

Workflows

Coordinators

Transactions

---

## Domain

Responsibilities

Business Rules

Models

Interfaces

Validation

Pure Kotlin / Rust

---

## Capability Engines

Contains

Capture

TLS

Storage

Search

Export

Replay

Parser

Statistics

---

## Infrastructure

SQLite

Files

Certificates

OS APIs

Networking

Logging

---

# 7. Capability Architecture

```text
capture-core
│
├── transport-core
│
├── tls-core
│
├── parser-core
│
├── session-core
│
├── storage-core
│
├── search-core
│
├── export-core
│
├── replay-core
│
└── statistics-core
```

Capabilities own behavior.

Screens consume capabilities.

Never the opposite.

---

# 8. Dependency Rules

Allowed

```text
UI

↓

Application

↓

Domain

↓

Capability

↓

Infrastructure
```

Forbidden

Infrastructure → UI

Storage → Capture

Search → VPN

Replay → UI

Parser → Compose

---

# 9. Capability Dependency Matrix

| Capability | Allowed Dependencies |
|------------|----------------------|
| capture-core | transport-core |
| transport-core | tls-core |
| tls-core | parser-core |
| parser-core | session-core |
| session-core | storage-core |
| storage-core | shared-model |
| search-core | storage-core |
| export-core | session-core |
| replay-core | session-core |
| statistics-core | storage-core |

No capability may depend on UI modules.

---

# 10. Shared Core Strategy

The Rust engine owns

Packet decoding

HTTP parsing

TLS parsing

Search indexing

HAR generation

Session reconstruction

Platform code owns

Permissions

VPN lifecycle

Notifications

UI

Navigation

This separation minimizes duplicated logic.

---

# 11. Communication Patterns

Modules communicate using contracts.

Never concrete implementations.

```text
Interface

↓

Implementation

↓

Dependency Injection
```

Communication mechanisms

• Flow

• StateFlow

• Channels (where appropriate)

• Repository interfaces

No global mutable state.

---

# 12. Threading Model

Main Thread

UI

IO Thread

Database

Worker Threads

Packet Parsing

TLS

Compression

Export

Search Indexing

Heavy operations never block the UI.

---

# 13. Data Flow

```text
Application

↓

VPN

↓

Packet

↓

TCP Stream

↓

TLS

↓

HTTP Parser

↓

Session Builder

↓

Storage

↓

Search Index

↓

UI
```

Data flows in one direction.

No reverse mutations.

---

# 14. Error Handling Strategy

Recoverable

Retry

Log

Continue

Fatal

Stop capture

Notify user

Preserve session

Errors never crash the platform.

---

# 15. Module Boundaries

Each module exposes only

Public API

Internal implementation remains hidden.

No module accesses another module's database directly.

---

# 16. Extension Strategy

Future capabilities must

Declare dependencies

Define contracts

Provide tests

Update ADR if architecture changes

---

# 17. Performance Principles

Packet processing must be streaming.

Avoid loading entire sessions into memory.

Large payloads should be lazy-loaded.

Indexes must support

100,000+

requests.

---

# 18. Security Principles

Certificates stored securely.

Sensitive headers masked.

Optional encryption for local database.

No automatic uploads.

---

# 19. Architecture Decision Records

Every major architectural change requires an ADR.

Examples

ADR-001

Rust Shared Core

ADR-002

Capability Modules

ADR-003

Repository Pattern

ADR-004

Packet Streaming

ADR-005

Storage Strategy

---

# 20. Architecture Quality Checklist

Every module must satisfy

✓ Single responsibility

✓ Independent testing

✓ Explicit dependencies

✓ No circular references

✓ Public API documented

✓ Internal implementation hidden

✓ Thread-safe

✓ Memory efficient

✓ Observable

✓ Replaceable

---

# 21. Future Evolution

The architecture must support future additions without requiring redesign.

Examples

Desktop

CLI

Remote Debugger

Plugin SDK

Browser Extension

Cloud Synchronization

AI Analysis

Protocol Plugins

---

# 22. References

MASTER_SPEC.md

01_PRODUCT_VISION.md