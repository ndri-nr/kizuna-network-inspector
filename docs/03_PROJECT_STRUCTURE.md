# 03_PROJECT_STRUCTURE.md

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

This document defines the official repository structure, module boundaries, naming conventions, ownership rules, dependency graph, and package organization of the Kizuna Network Inspector platform.

Every source file in the repository shall belong to exactly one module.

Every module shall have a clearly defined responsibility.

---

# 2. Design Philosophy

The repository is organized around **capabilities**, not screens.

Bad

```

feature-home

feature-network

feature-detail

```

Good

```

capture-core

tls-core

parser-core

storage-core

search-core

```

Capabilities are permanent.

Screens change.

---

# 3. Repository Layout

```

kizuna-network-inspector/

├── android/
├── ios/
├── desktop/
├── rust/
├── shared-model/
│
├── docs/
├── adr/
├── rfc/
├── diagrams/
│
├── scripts/
├── tools/
├── assets/
│
├── .github/
│
├── LICENSE
├── README.md
├── CHANGELOG.md
├── CONTRIBUTING.md
└── MASTER_SPEC.md

```

---

# 4. Android Project

```

android/

├── app/
│
├── platform/
│ ├── vpn
│ ├── notification
│ ├── permissions
│ ├── certificate
│ └── lifecycle
│
├── ui/
│ ├── compose
│ ├── navigation
│ ├── theme
│ └── components
│
├── capability/
│
│ ├── capture
│ ├── inspection
│ ├── replay
│ ├── export
│ ├── statistics
│ ├── search
│ ├── filter
│ ├── settings
│ └── diagnostics
│
├── shared/
│
│ ├── common
│ ├── coroutine
│ ├── logger
│ ├── serialization
│ ├── database
│ └── testing

```

Notice

Capabilities

NOT

Features.

---

# 5. Rust Workspace

```

rust/

Cargo.toml

crates/

capture-core/

transport-core/

tcp-core/

udp-core/

tls-core/

http-core/

http2-core/

websocket-core/

parser-core/

session-core/

storage-core/

search-core/

filter-core/

statistics-core/

export-core/

replay-core/

shared-model/

compression/

crypto/

utils/

```

Every crate owns exactly one responsibility.

---

# 6. Capability Ownership

| Capability | Owner |
|------------|-------|
| Capture | capture-core |
| TCP | transport-core |
| TLS | tls-core |
| HTTP | http-core |
| HTTP2 | http2-core |
| Parser | parser-core |
| Session | session-core |
| Search | search-core |
| Storage | storage-core |
| Replay | replay-core |
| Export | export-core |
| Statistics | statistics-core |

---

# 7. Shared Model

The shared model contains only:

Domain objects.

Enums.

Identifiers.

Contracts.

Value Objects.

No business logic.

No Android dependencies.

No iOS dependencies.

---

# 8. Android Packages

```

com.kni

```

Top level packages

```

app

platform

ui

capability

shared

```

Never create packages such as

```

utils

helpers

misc

manager

common

```

These become junk drawers.

---

# 9. Naming Rules

Modules

```

capture-core

tls-core

```

Packages

```

capture

session

storage

```

Classes

```

CaptureSession

PacketDecoder

TlsHandshake

HttpRequest

SearchEngine

```

Interfaces

```

CaptureRepository

StorageRepository

PacketParser

```

Implementations

```

DefaultPacketParser

RoomStorageRepository

RustCaptureEngine

```

---

# 10. Visibility Rules

Default visibility

internal

Expose only contracts.

Hide implementations.

Never expose

Room

SQLite

Compose

VpnService

Outside their modules.

---

# 11. Dependency Rules

Allowed

```

UI

↓

Capability

↓

Shared

↓

Rust

```

Forbidden

```

Capture

↓

Search

```

```

Storage

↓

VPN

```

```

Compose

↓

Rust

```

Everything goes through contracts.

---

# 12. Gradle Modules

```

:app

:platform:vpn

:platform:certificate

:platform:notification

:platform:lifecycle

:ui:compose

:ui:navigation

:ui:theme

:ui:components

:capability:capture

:capability:inspection

:capability:search

:capability:filter

:capability:statistics

:capability:settings

:capability:diagnostics

:capability:replay

:capability:export

:shared:common

:shared:database

:shared:logger

:shared:testing

```

---

# 13. Package Ownership

Every package has exactly one owner.

One owner

One responsibility.

Never share ownership.

---

# 14. Dependency Matrix

```

Capture

↓

Session

↓

Storage

↓

Search

↓

Export

```

Reverse dependency

Forbidden.

---

# 15. Folder Rules

Every module

```

src/

main/

kotlin/

resources/

test/

androidTest/

README.md

```

Every module documents itself.

---

# 16. Module Documentation

Every module includes

Purpose

Responsibilities

Dependencies

Public API

Examples

Limitations

Future Work

---

# 17. Code Ownership

Every major capability has

Owner

Reviewer

Backup reviewer

Future contributors know who owns the module.

---

# 18. Repository Standards

No generated files committed.

No binaries.

No APKs.

No IDE files.

Everything reproducible.

---

# 19. Build Philosophy

Small modules.

Incremental compilation.

Independent testing.

Minimal coupling.

---

# 20. Future Expansion

Future modules

```

grpc-core

graphql-core

dns-core

quic-core

plugin-sdk

desktop-client

browser-extension

ai-analysis

```

No restructuring required.

---

# 21. Success Criteria

The repository succeeds if

• New capabilities can be added without modifying unrelated modules.

• Every module has one responsibility.

• Dependencies remain acyclic.

• Platform code remains isolated.

• Shared logic exceeds 80%.

---

# 22. References

MASTER_SPEC.md

02_ARCHITECTURE.md