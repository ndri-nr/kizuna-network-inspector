# 07_UBIQUITOUS_LANGUAGE.md

> Project: Kizuna Network Inspector
>
> Parent:
> MASTER_SPEC.md
>
> Version: 1.0
>
> Status: Draft
>
> Document Type:
> Domain Language Specification

---

# 1. Purpose

This document defines the official ubiquitous language of the Kizuna Network Inspector platform.

Every technical term appearing in documentation, source code, architecture, database schemas, APIs, ADRs, RFCs, issue trackers, and pull requests shall use the definitions contained in this document.

The objective is to establish a single, unambiguous vocabulary shared by developers, reviewers, maintainers, and contributors.

---

# 2. Design Principles

Every domain concept shall:

- Have exactly one meaning.
- Have exactly one canonical name.
- Avoid overloaded terminology.
- Be implementation independent.
- Be platform independent.
- Be stable over time.

---

# 3. Naming Rules

General principles:

- Prefer explicit names over short names.
- Avoid abbreviations unless universally accepted (HTTP, TLS, DNS).
- Avoid generic names such as Manager, Helper, Util, Data, Common.
- Every type should represent one domain concept.

Examples:

Good

```
HttpExchange
CaptureSession
TransportConnection
```

Bad

```
Session
Manager
Util
Helper
Data
```

---

# 4. Core Domain Concepts

## NetworkPacket

Definition

The smallest unit of captured network data received from the operating system.

Characteristics

- Immutable
- Timestamped
- Direction aware
- Transport specific

Never contains parsed HTTP information.

---

## TransportConnection

Definition

A bidirectional transport-layer connection identified by source and destination endpoints.

Examples

TCP

UDP

QUIC

Responsibilities

- Packet ordering
- Connection state
- Lifetime tracking

---

## TlsConnection

Definition

A secure transport connection established after a successful TLS handshake.

Contains

- TLS version
- Cipher suite
- Certificate chain
- ALPN protocol

---

## ProtocolParser

Definition

A component responsible for interpreting transport payloads into protocol-specific structures.

Examples

HttpParser

WebSocketParser

DnsParser

GrpcParser

---

## HttpExchange

Definition

A single HTTP request and its corresponding HTTP response.

Contains

- Request
- Response
- Timing
- Metadata

This is the primary inspection unit presented to users.

---

## HttpRequest

Definition

A parsed outbound HTTP request.

Contains

- Method
- URL
- Headers
- Query Parameters
- Path Parameters
- Cookies
- Body

---

## HttpResponse

Definition

A parsed inbound HTTP response.

Contains

- Status
- Headers
- Cookies
- Body
- Compression
- Content Type

---

## CaptureSession

Definition

A runtime capture period beginning when capture starts and ending when capture stops.

Contains

Many

HttpExchanges

TransportConnections

Statistics

Configuration

---

## InspectionSession

Definition

A user viewing context for exploring captured data.

Inspection sessions do not own captured traffic.

They represent UI state only.

---

## Runtime

Definition

The orchestrator responsible for lifecycle, service coordination, scheduling, and event dispatch.

The Runtime is not responsible for protocol parsing.

---

## RuntimeService

Definition

A stable public capability exposed by the Runtime.

Examples

CaptureService

SearchService

StorageService

ReplayService

---

## Engine

Definition

A specialized component that performs domain-specific processing.

Examples

CaptureEngine

SearchEngine

ParserEngine

ExportEngine

Engines are implementation details behind RuntimeServices.

---

# 5. Relationship Model

```
CaptureSession
        │
        ├──────── HttpExchange
        │              │
        │              ├──── HttpRequest
        │              └──── HttpResponse
        │
        ├──────── TransportConnection
        │
        └──────── Statistics
```

---

# 6. Event Terminology

Every event shall use the past tense.

Examples

```
CaptureStarted

CaptureStopped

PacketCaptured

ExchangeParsed

SessionStored

SearchCompleted

ExportFinished
```

Never

```
StartCapture

DoSearch

RunExport
```

---

# 7. Error Terminology

Errors shall describe what happened.

Examples

```
TlsHandshakeFailed

CertificateRejected

StorageUnavailable

CapturePermissionDenied

ParserFailure
```

Avoid generic names such as

```
UnknownError

GeneralException

Failure
```

unless unavoidable.

---

# 8. Identifier Naming

Identifiers shall be globally unique within their scope.

Examples

```
CaptureSessionId

ExchangeId

ConnectionId

PacketId

CertificateId
```

---

# 9. State Naming

States are nouns or adjectives.

Examples

```
Running

Paused

Stopped

Idle

Initializing

Failed
```

Transitions are verbs.

```
Start

Pause

Resume

Stop

Dispose
```

---

# 10. Forbidden Terms

The following terms are prohibited in code and documentation unless explicitly justified:

```
Manager

Helper

Utils

Misc

Common

Data

Thing

Object

Entity (unless referring to persistence)

Model (unless qualified)

Info

Temp

```

These names do not communicate responsibility.

---

# 11. Canonical Vocabulary

| Canonical Term | Meaning |
|----------------|---------|
| NetworkPacket | Raw packet |
| TransportConnection | TCP/UDP/QUIC connection |
| TlsConnection | Secure transport |
| HttpExchange | One request-response pair |
| CaptureSession | Entire capture lifecycle |
| Runtime | Platform orchestrator |
| RuntimeService | Stable public service |
| Engine | Internal processing component |
| ProtocolParser | Protocol decoder |
| InspectionSession | UI viewing context |

---

# 12. Governance

New domain terms require:

- Architecture review
- Documentation update
- Approval before implementation

Renaming an existing canonical term requires an ADR.

---

# 13. References

MASTER_SPEC.md

02_ARCHITECTURE.md

03_PROJECT_STRUCTURE.md

04_PLATFORM_KERNEL.md

06_SYSTEM_REQUIREMENTS.md