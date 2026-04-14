# CSOB CA Module — Development Journal

A chronological record of design decisions, contract lock-ins, and
implementation progress. Append-only. Newest entries at the top.

---

## 2026-04-14 · Session 1 (initial scaffold through v1 validation chain)

### Outcome at end of session
- Multi-module Maven backend scaffolded at `backend/` (8 modules).
- Contracts locked: DTOs, enums, AI output JSON Schema.
- Pipeline wiring real; 6 of 10 validation checks fully implemented.
- Repo ready for initial private remote push.

### Module boundaries (locked)

```
ca-shared          (DTOs + enums only; zero deps)
ca-checklist       → ca-shared                deterministic rules
ca-tools           → ca-shared                read-only tool adapters
ca-ai-client       → ca-shared                ModelClient + prompt machinery
ca-validation      → ca-shared                deterministic AI-output gate
ca-persistence     → ca-shared                JPA entities + repos + audit
ca-orchestration   → all of the above         sole wiring/composer
ca-api             → ca-orchestration + ca-shared + Spring Boot starters
                     (ca-persistence pulled in transitively for Spring scan;
                      controllers MUST NOT import com.csob.ca.persistence.*)
```

Invariants locked:
- `ca-shared` carries no business logic; no Spring imports; no external deps.
- `ca-checklist` cannot import `ca-ai-client` or `ca-validation`.
- `ca-ai-client` cannot import `ca-checklist`, `ca-tools`, or `ca-persistence`.
- `ca-orchestration` is the only layer permitted to wire the others.
- `ca-api` is the only module permitted to declare Spring Boot starters.
- Enforcement today = Maven dep graph + code review; ArchUnit tests TODO.

### Architectural principles (load-bearing)

1. **Deterministic layer is the system of record.** Checklist findings are
   authoritative. AI output is advisory and cannot populate any
   system-of-record field.
2. **AI is summarisation only.** No risk rating, no decisions, no tool
   selection, no agentic loops, no retries beyond an explicit policy.
3. **Every AI sentence must cite and ground** to a frozen ToolOutput or a
   Checklist finding. Uncited / ungrounded sentences are rejected.
4. **Human review is mandatory** and gated by the backend state machine.
5. **Audit is append-only** and hash-chained per pack.
6. **Single, bounded AI call per pack** (`InvokeAiStep` only).
7. **Validation is deterministic and never throws out of `check(...)`.**

### Key contracts

- **PackStatus flow:** `INITIATED → DATA_GATHERED → CHECKED → SUMMARISED →
  VALIDATED → REVIEWED → (APPROVED_FOR_FILE | REJECTED_BY_REVIEWER)`.
  Guarded by `PackLifecycle.requireAllowed(...)`; no backwards transitions.
- **ValidationReport status:** `VALID` iff every check returns `PASS`;
  otherwise `REJECTED`.
- **AI output JSON Schema** (authoritative): `ca-validation/.../schemas/
  ai-output.schema.json`. Mirror: `ca-ai-client/.../prompts/v1/output_schema.json`.
  Both SHA-256 = `c596aadf851baf8605605ff5ef2e648392e2f15c003ac35a452150cd1b872035`.
  CI gate (TODO) must assert byte-equality.
- **ValidationFailureDto** fields are `code`, `locator` (JSON Pointer),
  `detail`. User specs sometimes named them `path`/`message` — kept as-is
  to avoid ca-shared churn; will do a naming cleanup pass once the chain
  is complete.

### Validation chain — per-check status

| # | CheckId | Class | Status | Notes |
|---|---------|-------|--------|-------|
| 1 | `SCHEMA` | `SchemaValidator` | ✅ | networknt 1.5.4 / draft 2020-12, JSON_POINTER paths, binds parsedPayload on PASS |
| 2 | `PACK_BINDING` | `PackBindingChecker` | ⏳ skeleton | schema already pins `packId`/`checklistVersion` patterns |
| 3 | `SECTION_WHITELIST` | `SectionWhitelistChecker` | ⏳ skeleton | needs heading-uniqueness check |
| 4 | `LENGTH_GUARD` | `LengthGuardChecker` | ⏳ skeleton | defence-in-depth over schema caps |
| 5 | `FORMAT_GUARD` | `FormatGuardChecker` | ⏳ skeleton | imperative / first-person heuristics |
| 6 | `CITATION_PRESENCE` | `CitationPresenceChecker` | ✅ | iterates parsedPayload; CITATION_MISSING per uncited sentence |
| 7 | `CITATION_RESOLVABILITY` | `CitationResolvabilityChecker` | ✅ | per-sourceType exhaustive lookup; inline (not via resolver) |
| 8 | `FACT_GROUNDING` | `FactGroundingChecker` | ✅ | via `CitationResolver` + per-FactKind normalisation |
| 9 | `COVERAGE` | `CoverageChecker` | ✅ (v1) | every FAIL/MISSING finding must be cited; advanced coverage deferred to v2 |
| 10 | `BANNED_VOCABULARY` | `BannedVocabularyChecker` | ✅ | precompiled case-insensitive word/phrase patterns from policy file |

Skeleton checks return a FAIL with code `NOT_IMPLEMENTED` via
`DefaultValidationPipeline`'s `UnsupportedOperationException` catch — so the
pipeline is runnable end-to-end and each missing check is visible in the
report.

### Support classes implemented

- **`DefaultCitationResolver`** — two-stage resolution (entity lookup by
  `(sourceType, sourceId)`, then dotted `fieldPath` walk via record
  accessor reflection). Supports `[i]` index segments on list-typed fields.
  Returns `Optional.empty()` on any failure; never throws.
- **`DefaultTokeniser`** — still a skeleton. Reserved for v2 advanced
  coverage (DATE/NUMBER/STATUS token enforcement inside sentence.text).

### Decisions made (with reasoning)

- **Defensive copies in DTO compact constructors.** `List.copyOf(...)` +
  `Objects.requireNonNull(...)` in every record that takes lists — so
  immutability is enforced at construction, not by convention. Compatible
  with the "records only" ask; does not introduce business logic.
- **Sealed `ToolPayload`.** Each `ToolId` maps to exactly one payload type.
  Keeps orchestration's type-safe dispatch simple. Note flagged: Jackson
  polymorphic serialisation not yet configured — relevant only when
  persistence starts serialising `ToolOutputDto.payload` as JSON.
- **`CitationResolver` interface kept narrow.** Implementation
  (`DefaultCitationResolver`) was expanded for `FACT_GROUNDING`; the
  interface signature is unchanged. `CitationResolvabilityChecker` is
  intentionally not refactored to use the resolver to avoid modifying
  already-implemented checks — duplicated sourceId logic is known tech
  debt for the naming-cleanup pass.
- **Banned vocabulary stored as plain text, not YAML.** Zero-dependency
  loader. Two categories (speculative / decision) kept in one file; the
  checker treats them uniformly.
- **`ObjectMapper` injected into `SchemaValidator`.** Lets `ca-validation`
  stay Spring-free; `ca-api` provides Spring Boot's auto-configured
  mapper (JSR-310 registered via `spring-boot-starter-json`).
- **`jackson-datatype-jsr310` added to `ca-validation`.** Needed because
  `AiSummaryPayloadDto.generatedAt` is `Instant` and the module may run
  standalone in tests without Spring's mapper.
- **`ca-persistence` as transitive-only for `ca-api`.** Keeps controllers
  from accidentally importing entities at compile time. Spring Boot still
  scans repositories/entities at boot. ArchUnit will formalise this.
- **DefaultValidationPipeline swallows `UnsupportedOperationException`.**
  The pipeline is usable as soon as any one check is implemented; the
  rest surface as `NOT_IMPLEMENTED` failures in the report.

### Known gaps / follow-ups

- Checklist rules v1 — `ca-checklist/rules/v1/` has only the `Rule`
  interface; real rule classes + regulatory references TODO.
- Tool adapter implementations — interfaces only today; no ACRA / internal
  store wiring yet.
- `PromptLoader` + `PromptAssembler` — skeleton; prompt assets exist but
  loader reads nothing yet.
- `JpaPackRepository` + `DbAuditWriter` — skeleton; Flyway V1 migration is
  a comment-only placeholder.
- `SecurityConfig` — empty; Spring Security default-locks everything
  until filled in.
- Frontend (`../frontend/`) — not started.
- Naming cleanup pass (checker/check, locator/path) — queued.
- `mvn clean verify` — not yet run in this session.

### Files of interest

- `README.md` — module map, dep diagram, IntelliJ import steps.
- `backend/pom.xml` — parent, Spring Boot 3.4 BOM, Java 21.
- `backend/ca-api/src/main/java/com/csob/ca/api/config/OrchestrationConfig.java`
  — the one place where POJOs are wired into Spring beans.
- `backend/ca-validation/src/main/resources/schemas/ai-output.schema.json`
  — authoritative copy of the AI output contract.
