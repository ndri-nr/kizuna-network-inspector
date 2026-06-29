# 09_OBSERVATION_PIPELINE.md

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
> Observation Pipeline Specification



### 1. Purpose

This document defines the official Observation Pipeline Architecture of Kizuna Network Inspector.

The pipeline standardizes how network activity flows through the platform, from capture or import to search, replay, export, analysis, and UI presentation.

Its primary goal is to ensure that all features operate on the same Canonical Observation Model (COM), regardless of where the data originated.

### 2. Architectural Principle

Everything in KNI is one of three things:

| Type        | Responsibility                     |
| ----------- | ---------------------------------- |
| Producer    | Creates COM objects                |
| Transformer | Enriches or normalizes COM objects |
| Consumer    | Reads COM objects                  |

### 3. High-Level Pipeline

### Observation Pipeline

Producers

Transformers

COM

Canonical Observation Model

Consumers

### 4. Producers

Producers generate raw observations.

### Supported Producers

| Producer        | Purpose                                 |
| --------------- | --------------------------------------- |
| VPN Capture     | Live device traffic                     |
| Proxy Capture   | Proxy-based traffic                     |
| HAR Import      | Import browser sessions                 |
| PCAP Import     | Import packet captures                  |
| Remote Agent    | Receive observations from other devices |
| Test Generator  | Create synthetic traffic for testing    |
| Plugin Producer | Future extension point                  |

### 5. Producer Contract

### Every producer MUST

* Generate valid COM objects.

* Attach timestamps.

* Provide source metadata.

* Preserve original ordering when possible.

* Never bypass normalization.

### 6. Transformers

Transformers enrich observations without changing their identity.

### Examples

| Transformer           | Responsibility      |
| --------------------- | ------------------- |
| TLS Enricher          | Add TLS metadata    |
| GeoIP Enricher        | Add geographic info |
| Content-Type Detector | Infer payload types |
| Compression Analyzer  | Detect compression  |
| Timing Calculator     | Compute durations   |
| AI Analyzer (Future)  | Add insights        |

### 7. Transformer Rules

### Transformers MUST

* Be deterministic.

* Be idempotent.

* Preserve original observations.

* Produce a new revision if modifications occur.

* Never delete data.

### 8. Canonical Observation Model (COM)

### COM is the single source of truth

Everything entering the platform becomes COM.

Everything leaving the platform reads COM.

Core objects

* CaptureSession

* Observation

* TransportConnection

* ProtocolConversation

* ProtocolExchange

* ProtocolMessage

* Payload

### 9. Consumers

Consumers read COM but do not own it.

### Examples

| Consumer        | Purpose                  |
| --------------- | ------------------------ |
| Search          | Query observations       |
| Replay          | Re-send exchanges        |
| Export          | Generate HAR, cURL, JSON |
| Statistics      | Compute metrics          |
| UI              | Display observations     |
| AI Analysis     | Generate insights        |
| Plugin Consumer | Future extension point   |

### 10. Consumer Rules

### Consumers MUST

* Treat COM objects as immutable.

* Avoid protocol-specific assumptions when possible.

* Use canonical identifiers.

* Support future protocol extensions gracefully.

### 11. Pipeline Flow

### End-to-End Flow

VPN Capture

Packet Producer

TLS Transformer

HTTP Transformer

COM Exchange

Canonical Observation Model

Search / Replay / Export / UI

### 12. Why This Architecture Matters

### Adding HAR Import

* Implement HAR Producer.

* Convert HAR → COM.

* Everything else works automatically.

### Adding HTTP/3

* Implement QUIC Producer.

* Implement HTTP/3 Transformer.

* Output COM exchanges.

### Adding AI Analysis

* Implement AI Consumer.

* Read COM.

* Generate insights.

### Adding Desktop

* Reuse the same Runtime.

* Reuse the same COM.

* Build a new UI only.

### 13. Invariants

### The following MUST always hold

* All observations are represented as COM.

* COM objects are immutable.

* Producers never write directly to consumers.

* Consumers never mutate COM.

* Transformers preserve observation identity.

* The Runtime orchestrates the pipeline.

### 14. References

MASTER_SPEC.md

04_PLATFORM_KERNEL.md

07_UBIQUITOUS_LANGUAGE.md

08_NETWORK_OBSERVATION_MODEL.md
