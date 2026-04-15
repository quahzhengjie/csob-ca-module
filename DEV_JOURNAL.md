# CSOB CA Module — Development Journal

A chronological record of design decisions, contract lock-ins, and
implementation progress. Append-only. Newest entries at the top.

---

## 2026-04-15 · Session 2 (build green; v1 validation chain complete)

### Outcome at end of session
- `mvn clean verify` green across all 9 modules (commit `1f4c239`).
- v1 validation chain **complete**: 10 of 10 checks implemented
  (commit `5645ac3`).
- No contract, DTO, enum, schema, dependency, or wiring changes this
  session — every edit is inside `ca-validation/check/*`.

### Commits

| Hash       | Title                                                                  |
|------------|------------------------------------------------------------------------|
| `1f4c239`  | build: align compiler target to Java 17; scaffold now builds clean     |
| `5645ac3`  | feat(validation): complete v1 validation chain (10/10 checks implemented) |

### Build decision
- Lowered `<release>` 21 → 17 in parent POM (source/target props plus
  the compiler-plugin config). **Why:** wrapper runs on Microsoft JDK 17
  via `JAVA_HOME`; `<release>21</release>` failed with
  `error: release version 21 not supported`. All source is Java 17-safe
  (records, sealed interfaces, arrow-form switch expressions, `yield`,
  `instanceof` pattern, `List.copyOf`). No code changes; matches the
  original "Java 17+" brief; no environment edits.

### Remaining validation checks — now implemented

| #   | CheckId                  | Class                         | Key behaviour                                                                                                            |
|-----|--------------------------|-------------------------------|--------------------------------------------------------------------------------------------------------------------------|
| 2   | `PACK_BINDING`           | `PackBindingChecker`          | Compares `parsedPayload.packId` / `checklistVersion` to pipeline values; distinct codes `PACK_ID_MISMATCH` / `CHECKLIST_VERSION_MISMATCH`; detail carries expected + actual. |
| 3   | `SECTION_WHITELIST`      | `SectionWhitelistChecker`     | EnumMap-based dedup; codes `INVALID_SECTION_HEADING` (null) and `DUPLICATE_SECTION_HEADING` (detail notes first-seen index). |
| 4   | `LENGTH_GUARD`           | `LengthGuardChecker`          | Per-level caps (sections ≤ 5, sentences ≤ 8, text ≤ 320, citations ≤ 6, factMentions ≤ 16). Distinct code per cap; each detail reports `actual=…, allowed=…`. |
| 5   | `FORMAT_GUARD`           | `FormatGuardChecker`          | Single `INVALID_FORMAT` code with varied details: `empty text`, `leading or trailing whitespace`, `contains newline`, `contains markdown '*'/'_/'`'`. Short-circuits null/whitespace-only text. |

All four are **defence in depth**: the JSON Schema already catches most
of these shapes. Explicit checks are kept so the validation report names
exactly which rule tripped, and so any future weakening of the schema
or DTO contracts surfaces at the validation surface.

### Invariants preserved (unchanged this session)

- Deterministic layer = system of record.
- AI output is advisory; cannot populate any system-of-record field.
- Every check is deterministic and never throws out of `check(...)`.
- ValidationFailureDto fields remain `code`, `locator` (JSON Pointer),
  `detail` (message) — user spec drifted twice to `path`/`message`;
  kept as-is per earlier agreement to defer a naming-cleanup pass.
- Class names retain `Checker` suffix until the naming cleanup pass.
- Spring Boot starters only in `ca-api`.

### Validation chain — final state

| #   | CheckId                  | Status |
|-----|--------------------------|--------|
| 1   | `SCHEMA`                 | ✅     |
| 2   | `PACK_BINDING`           | ✅     |
| 3   | `SECTION_WHITELIST`      | ✅     |
| 4   | `LENGTH_GUARD`           | ✅     |
| 5   | `FORMAT_GUARD`           | ✅     |
| 6   | `CITATION_PRESENCE`      | ✅     |
| 7   | `CITATION_RESOLVABILITY` | ✅     |
| 8   | `FACT_GROUNDING`         | ✅     |
| 9   | `COVERAGE` (v1)          | ✅     |
| 10  | `BANNED_VOCABULARY`      | ✅     |

`DefaultValidationPipeline` no longer surfaces any `NOT_IMPLEMENTED`
failures — every check has a real body.

### Open items going into next session

1. **Naming cleanup pass** — rename `*Checker` classes to `*Check` to
   match user-facing spec naming; optionally rename
   `ValidationFailureDto.locator`/`detail` to `path`/`message`. Queued
   as an independent refactor commit. Will touch `OrchestrationConfig`
   wiring lines and (for DTO rename) `ca-shared` + every call site.
2. **networknt deprecation migration** — `SchemaValidatorsConfig` +
   `setPathType(...)` are deprecated in favour of the builder API.
   Behaviour-preserving refactor inside `SchemaValidator` only.
3. **ArchUnit module-boundary tests** — formalise the rules in
   `README.md` so CI blocks forbidden imports.
4. **JSON Schema byte-equality CI gate** between `ca-validation/schemas`
   and `ca-ai-client/prompts/v1/output_schema.json`.
5. Still untouched: `ChecklistEngineImpl`, tool adapters,
   `PromptLoader` / `PromptAssembler` / real `ModelClient`,
   `JpaPackRepository`, `DbAuditWriter`, Flyway `V1` DDL, `SecurityConfig`,
   frontend.

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
