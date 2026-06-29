# 08_NETWORK_OBSERVATION_MODEL.md

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
> Canonical Observation Model Specification

---

# 1. Purpose

The Canonical Observation Model (COM) defines the single authoritative representation of all network activity within the Kizuna Network Inspector platform.

Every producer of network data MUST convert into COM.

Every consumer MUST consume COM.

No subsystem may directly depend on another subsystem's internal representation.

Examples

VPN

↓

COM

↓

Search

Export

Replay

Statistics

UI

HAR Import

↓

COM

PCAP Import

↓

COM

---

# 2. Design Goals

The model MUST be

- Immutable
- Platform independent
- Protocol independent
- Serializable
- Versioned
- Extensible
- Thread-safe
- Deterministic

---

# 3. Core Hierarchy

```
CaptureSession
        │
        ▼
Observation
        │
        ▼
TransportConnection
        │
        ▼
ProtocolConversation
        │
        ▼
ProtocolExchange
        │
        ▼
ProtocolMessage
        │
        ▼
Payload
```

Every object has exactly one parent except CaptureSession.

---

# 4. CaptureSession

Definition

Represents one capture lifecycle.

Created when capture begins.

Closed when capture stops.

Contains

- Metadata
- Observations
- Statistics
- Configuration Snapshot

Properties

```
id

name

startTime

endTime

captureSource

device

runtimeVersion

notes

tags
```

---

# 5. Observation

Definition

A top-level unit of captured network activity.

An Observation represents a normalized event originating from any supported source.

Sources

- VPN
- Proxy
- HAR
- PCAP
- Remote Agent
- Plugin

Properties

```
id

captureSessionId

timestamp

source

protocol

connectionId

exchangeId

severity

bookmarked

favorite

annotations

labels
```

---

# 6. TransportConnection

Definition

Represents a transport-layer connection.

Examples

TCP

UDP

QUIC

Contains

```
connectionId

sourceEndpoint

destinationEndpoint

transportProtocol

startTime

endTime

state
```

---

# 7. SecurityContext

Definition

Security information associated with a connection.

Contains

```
tlsVersion

cipherSuite

certificateChain

alpn

hostname

trusted

handshakeTime
```

Optional.

Only exists when applicable.

---

# 8. ProtocolConversation

Definition

A logical conversation within one transport connection.

Examples

HTTP/2 Stream

WebSocket Session

MQTT Session

gRPC Stream

Contains

```
conversationId

protocol

connectionId

metadata

state
```

---

# 9. ProtocolExchange

Definition

A complete protocol interaction.

Examples

HTTP Request → Response

DNS Query → Response

GraphQL Operation → Result

Contains

```
exchangeId

conversationId

request

response

timing

status

securityContext

errors
```

This is the primary inspection object presented in the UI.

---

# 10. ProtocolMessage

Definition

One protocol message.

HTTP

Request

Response

WebSocket

Frame

Frame

Frame

Contains

```
messageId

direction

headers

payload

trailers

size

encoding
```

---

# 11. Payload

Definition

Raw or decoded message content.

Contains

```
mimeType

encoding

charset

size

checksum

compressed

body
```

Payload may be lazy-loaded.

---

# 12. Metadata

Every object may contain metadata.

```
createdAt

updatedAt

producer

version

labels

notes

favorite

bookmarked
```

---

# 13. Timing

Timing information is normalized.

```
queued

dns

connect

tls

requestStart

requestEnd

responseStart

responseEnd

completed
```

Missing values remain null.

---

# 14. Address

```
hostname

ip

port

scheme
```

---

# 15. Endpoint

```
local

remote
```

Each endpoint references Address.

---

# 16. Certificate

```
subject

issuer

serialNumber

fingerprint

validFrom

validUntil

signatureAlgorithm

publicKeyAlgorithm
```

---

# 17. Error

```
code

category

description

recoverable

timestamp
```

Errors never throw away observations.

---

# 18. Annotation

User-created information.

```
title

comment

color

createdBy

createdAt
```

Annotations never modify captured data.

---

# 19. Attachment

Future capability.

Examples

Images

HAR

PCAP

Certificate

Logs

---

# 20. Identity

Every object MUST have

```
UUID

Version

CreatedAt
```

Identifiers are immutable.

---

# 21. Immutability

Observation objects MUST be immutable.

Updates create

```
Revision
```

Never in-place mutation.

---

# 22. Versioning

Every serialized COM object contains

```
schemaVersion
```

Older versions MUST remain readable whenever practical.

---

# 23. Serialization

Supported formats

JSON

CBOR

MessagePack

Protocol Buffers (future)

SQLite persistence

All serializers MUST produce equivalent semantic content.

---

# 24. Lifecycle

```
Observed

↓

Parsed

↓

Normalized

↓

Validated

↓

Indexed

↓

Stored

↓

Exported

↓

Archived
```

---

# 25. Relationships

```
CaptureSession

    │

    ├──────── Observation

    │          │

    │          ▼

    │     TransportConnection

    │          │

    │          ▼

    │   ProtocolConversation

    │          │

    │          ▼

    │    ProtocolExchange

    │       ├──────── Request

    │       └──────── Response

    │

    └──────── Statistics
```

---

# 26. Mapping Rules

Every producer MUST map into COM.

Examples

VPN Packet

↓

Observation

HAR Entry

↓

ProtocolExchange

PCAP Packet

↓

Observation

HTTP Request

↓

ProtocolMessage

No producer may bypass normalization.

---

# 27. Ownership Rules

| Object | Owner |
|---------|-------|
| CaptureSession | Runtime |
| Observation | Capture Engine |
| TransportConnection | Transport Engine |
| SecurityContext | TLS Engine |
| ProtocolConversation | Protocol Engine |
| ProtocolExchange | Protocol Engine |
| Payload | Protocol Engine |

---

# 28. Canonical Invariants

The following MUST always hold:

- Every Observation belongs to exactly one CaptureSession.
- Every TransportConnection belongs to exactly one Observation.
- Every ProtocolConversation belongs to exactly one TransportConnection.
- Every ProtocolExchange belongs to exactly one ProtocolConversation.
- ProtocolMessages belong to exactly one ProtocolExchange.
- Payload belongs to exactly one ProtocolMessage.

Violation of these invariants indicates data corruption.

---

# 29. Extension Points

Future extensions MAY introduce:

- HTTP/3 exchanges
- MQTT conversations
- SIP dialogs
- FTP sessions
- SSH channels
- Custom protocol metadata

Extensions MUST preserve existing invariants.

---

# 30. References

MASTER_SPEC.md

02_ARCHITECTURE.md

04_PLATFORM_KERNEL.md

07_UBIQUITOUS_LANGUAGE.md