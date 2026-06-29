# 04_PLATFORM_KERNEL.md

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

This document defines the Platform Kernel of Kizuna Network Inspector.

The Platform Kernel represents the heart of the platform.

Every platform implementation
(Android, iOS, Desktop, CLI)
communicates with the Runtime instead of directly interacting with protocol parsers,
storage, search, or exporters.

The Runtime provides a stable programming model while allowing individual
capabilities to evolve independently.

---

# 2. Philosophy

The Runtime exists so that
every platform becomes only a presentation layer.

```
Android
iOS
Desktop
CLI

↓

Runtime

↓

Protocol Stack

↓

Network
```

Business logic never belongs inside platform code.

---

# 3. Runtime Responsibilities

The Runtime owns

- Lifecycle
- Service Registry
- Event Bus
- Scheduler
- Capability Discovery
- Protocol Registry
- Shared Models
- Configuration
- Diagnostics
- Metrics

The Runtime does NOT

- Render UI
- Display notifications
- Handle navigation
- Own platform permissions

---

# 4. Runtime Overview

```
                     Platform

          Android
          iOS
          Desktop
          CLI

                │

                ▼

         Runtime Facade

                │

────────────────────────────────────

 Runtime Services

 Capture

 Session

 Storage

 Search

 Replay

 Export

 Statistics

 Certificates

 Diagnostics

────────────────────────────────────

 Shared Runtime

 Event Bus

 Scheduler

 Registry

 Configuration

 Metrics

────────────────────────────────────

 Protocol Stack

 HTTP

 HTTP2

 WebSocket

 DNS

 QUIC

 GraphQL

 gRPC

────────────────────────────────────

 Infrastructure

 Files

 SQLite

 Compression

 Crypto

```

---

# 5. Runtime Design Principles

The Runtime shall be

Platform Independent

Thread Safe

Observable

Extensible

Deterministic

Replaceable

Testable

Composable

---

# 6. Runtime Layers

```
Facade

↓

Services

↓

Capability Managers

↓

Protocol Stack

↓

Infrastructure
```

Every layer only communicates downward.

---

# 7. Runtime Facade

The Facade is the only public entry point.

Example

```
Runtime.start()

Runtime.stop()

Runtime.capture()

Runtime.search()

Runtime.export()
```

Applications never instantiate services directly.

---

# 8. Runtime Lifecycle

```
Created

↓

Initializing

↓

Starting

↓

Running

↓

Paused

↓

Stopping

↓

Stopped

↓

Disposed
```

Every service follows this lifecycle.

---

# 9. Runtime Services

## CaptureService

Owns packet capture lifecycle.

---

## SessionService

Owns reconstructed sessions.

---

## SearchService

Owns indexing and searching.

---

## FilterService

Evaluates filters.

---

## ExportService

Creates HAR

JSON

cURL

Markdown

---

## ReplayService

Replays stored requests.

---

## StatisticsService

Produces metrics.

---

## CertificateService

Owns

CA

Trust Store

Certificate Validation

---

## StorageService

Owns persistence.

---

## DiagnosticsService

Provides health monitoring.

---

# 10. Service Registry

The Runtime maintains a Service Registry.

```
Runtime

↓

Registry

↓

Capture

↓

Storage

↓

Search

↓

Export

...
```

Services are resolved through interfaces.

Never concrete classes.

---

# 11. Event Bus

Services communicate through events.

Example

```
PacketCaptured

↓

EventBus

↓

SessionService

↓

StorageService

↓

StatisticsService

↓

SearchService
```

No service directly calls another service unless required by contract.

---

# 12. Scheduler

The Runtime owns task scheduling.

Queues

Capture Queue

Parser Queue

Storage Queue

Search Queue

Export Queue

Compression Queue

Every queue is independently configurable.

---

# 13. Protocol Registry

The Runtime supports protocol plugins.

```
Protocol

↓

HTTP

↓

HTTP2

↓

WebSocket

↓

DNS

↓

GraphQL

↓

gRPC

↓

QUIC
```

Every protocol implements the same interface.

Future protocols require only registration.

---

# 14. Packet Processing Pipeline

```
Packet

↓

Capture

↓

Transport

↓

TLS

↓

Protocol Detection

↓

Parser

↓

Session Builder

↓

Storage

↓

Search Index

↓

UI
```

Each stage owns exactly one responsibility.

---

# 15. Memory Strategy

The Runtime must

avoid unnecessary copies

stream large payloads

lazy-load bodies

compress archived sessions

minimize allocations

Large binary payloads should never remain entirely in memory unless explicitly requested.

---

# 16. Thread Model

UI Thread

Platform only.

Worker Threads

Packet processing

Parsing

Compression

Search

Storage

Export

No heavy work executes on the UI thread.

---

# 17. Runtime Configuration

Configuration is immutable while running.

Changing configuration requires controlled restart of affected services.

---

# 18. Public API Principles

Public APIs must

be stable

be documented

be versioned

avoid platform-specific types

prefer immutable models

---

# 19. Extension Model

New functionality must be added through

Capability

Service

Protocol

Exporter

Importer

Analyzer

No modification of existing services should be required for new protocols.

---

# 20. Error Model

Errors are classified as

Recoverable

Transient

Fatal

User Action Required

Every error includes

Code

Category

Description

Recovery Action

Telemetry remains local unless explicitly exported.

---

# 21. Diagnostics

The Runtime continuously exposes

Active Services

Memory Usage

Queue Depth

Capture Rate

Dropped Packets

Storage Usage

Search Index Status

Certificate Status

Protocol Statistics

These metrics power the Diagnostics screen.

---

# 22. Observability

Every Runtime service publishes

Lifecycle Events

Performance Metrics

Errors

Warnings

Health Status

This enables debugging of the debugger itself.

---

# 23. Plugin Readiness

Future plugin types

Protocol

Exporter

Analyzer

Theme

Decoder

Formatter

AI Assistant

The Runtime must support loading additional capabilities without redesign.

---

# 24. Quality Attributes

Startup

< 2 seconds

Capture latency

Minimal overhead

Search

<100 ms for indexed queries

Memory

Bounded

Thread Safety

Required

Crash Recovery

Automatic

---

# 25. Runtime Invariants

The following rules must always hold.

- Services communicate through contracts.
- Services own their own state.
- Runtime owns lifecycle.
- Platform code never bypasses Runtime.
- Protocols never access UI.
- Storage never owns business logic.
- Event delivery is deterministic.
- Dependencies remain acyclic.

Violation of these invariants requires an Architecture Decision Record (ADR).

---

# 26. References

MASTER_SPEC.md

02_ARCHITECTURE.md

03_PROJECT_STRUCTURE.md