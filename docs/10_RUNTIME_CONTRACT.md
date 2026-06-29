# 10_RUNTIME_CONTRACT.md

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
> Runtime Contract Specification

---

# 1. Purpose

The Runtime Contract defines the stable interface between platform applications and the Kizuna Runtime.

Platform applications MUST communicate exclusively through this contract.

The Runtime remains free to evolve internally without affecting clients.

---

# 2. Design Goals

The Runtime Contract MUST be:

- Stable
- Platform independent
- Thread safe
- Versioned
- Observable
- Backward compatible
- Testable
- Extensible

---

# 3. Runtime Responsibilities

The Runtime owns:

- lifecycle
- service discovery
- event dispatch
- scheduling
- observation pipeline
- configuration
- diagnostics

The Runtime does NOT own:

- UI
- navigation
- permissions
- notifications
- platform storage

---

# 4. Runtime Facade

Every platform interacts with a single entry point.

```
Application

↓

Runtime

↓

Services

↓

Engines
```

No platform may instantiate engines directly.

---

# 5. Lifecycle Contract

The Runtime lifecycle SHALL be:

```
Created

↓

Initializing

↓

Ready

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

Transitions MUST be deterministic.

---

# 6. Runtime Services

The Runtime exposes the following services.

| Service | Responsibility |
|----------|----------------|
| CaptureService | Capture lifecycle |
| SearchService | Search observations |
| ReplayService | Replay exchanges |
| ExportService | Export observations |
| StatisticsService | Compute metrics |
| StorageService | Persist COM |
| DiagnosticsService | Runtime health |
| CertificateService | HTTPS inspection |
| SettingsService | Runtime configuration |

Applications communicate only through services.

---

# 7. Service Discovery

Services are obtained from the Runtime.

Example

```
Runtime

↓

CaptureService

↓

SearchService

↓

ExportService
```

No global singleton services.

---

# 8. Event Contract

The Runtime publishes events.

Examples

```
RuntimeStarted

RuntimeStopped

CaptureStarted

CaptureStopped

PacketCaptured

ObservationCreated

ExchangeParsed

ExportCompleted

ReplayFinished

SearchCompleted
```

Events are immutable.

---

# 9. Event Ordering

The Runtime MUST preserve causal ordering.

Example

```
CaptureStarted

↓

PacketCaptured

↓

ObservationCreated

↓

ExchangeParsed

↓

Stored

↓

Indexed
```

Consumers MUST never observe impossible sequences.

---

# 10. Observation Streams

The Runtime exposes observable streams.

Examples

- Observation Stream
- Statistics Stream
- Diagnostics Stream
- Search Result Stream

Streams are read-only.

---

# 11. Configuration Contract

Configuration changes are explicit.

Rules

- Configuration is versioned.
- Validation occurs before activation.
- Invalid configuration is rejected.
- Running services MAY require restart.

---

# 12. Search Contract

Search SHALL support:

- Full-text
- Structured filters
- Incremental search
- Pagination
- Sorting

Search MUST operate on COM.

---

# 13. Replay Contract

Replay SHALL:

- Preserve HTTP method
- Preserve headers
- Preserve payload
- Preserve protocol metadata where possible

Replay MUST NOT mutate stored observations.

---

# 14. Export Contract

Supported formats:

- HAR
- JSON
- cURL
- Markdown

Future exporters register through the Runtime.

---

# 15. Diagnostics Contract

The Runtime exposes:

- Memory usage
- Queue depth
- Packet rate
- Observation rate
- Storage usage
- Runtime version
- Active services
- Health status

---

# 16. Threading Contract

Rules

- UI never blocks Runtime.
- Runtime never blocks UI.
- Heavy work executes on worker threads.
- Events are thread-safe.
- COM objects are immutable.

---

# 17. Error Contract

Errors include:

- Identifier
- Category
- Description
- Severity
- Recovery suggestion

Errors MUST NOT expose platform internals.

---

# 18. Compatibility Contract

Minor releases MUST preserve backward compatibility.

Breaking changes require:

- Major version
- Migration guide
- ADR

---

# 19. Extension Contract

Future extensions include:

- Protocol plugins
- Importers
- Exporters
- AI analyzers
- Remote agents

Extensions register through the Runtime Registry.

---

# 20. Security Contract

The Runtime MUST:

- Keep observations local by default.
- Protect certificates.
- Validate plugin registration.
- Prevent unauthorized Runtime access.

---

# 21. Performance Contract

Target metrics

| Metric | Target |
|----------|---------|
| Startup | < 2 s |
| Search | < 100 ms |
| Event dispatch | < 5 ms |
| Export initialization | < 200 ms |
| Replay startup | < 100 ms |

---

# 22. Versioning Contract

The Runtime Contract follows Semantic Versioning.

Example

```
1.0

1.1

2.0
```

Deprecated services remain available until the next major release unless a documented exception exists.

---

# 23. Runtime Invariants

The following MUST always hold:

- Runtime owns lifecycle.
- Runtime owns service registry.
- Services own business capabilities.
- Engines own processing.
- COM remains immutable.
- Platform code never bypasses Runtime.
- Runtime never depends on UI.

---

# 24. Compliance Checklist

Every Runtime implementation MUST satisfy:

✓ Lifecycle compliance

✓ Thread safety

✓ Event ordering

✓ COM compatibility

✓ Backward compatibility

✓ Diagnostics support

✓ Error reporting

✓ Deterministic behavior

---

# 25. References

MASTER_SPEC.md

04_PLATFORM_KERNEL.md

08_NETWORK_OBSERVATION_MODEL.md

09_OBSERVATION_PIPELINE.md