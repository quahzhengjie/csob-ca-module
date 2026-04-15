# CSOB CA Module — Development Journal

A chronological record of design decisions, contract lock-ins, and
implementation progress. Append-only. Newest entries at the top.

---

## 2026-04-15 · Session 4 (real coordinator + minimal persistence + replay)

### Outcome at end of session

End-to-end live HTTP path is real:

```
POST /api/ca/packs?partyId=…&checklistVersion=v1.0&promptVersion=v1
  → CaPackController.createPack
  → PipelineCoordinator.run
      → InvokeToolsStep        (filesystem-backed DocumentMetadataSource via DefaultToolInvoker)
      → EvaluateChecklistStep  (DocumentExpiryRule against fixed Clock — was system clock at runtime)
      → InvokeAiStep           (PromptAssembler → StubModelClient; graceful-failure fallback)
      → ValidateAiStep         (10-check ValidationPipeline)
      → PersistStep            (writes CaPackEntity row + 5 hash-chained audit events)
  ← 201 Created with full CaPackDto

GET  /api/ca/packs/{packId}            → 200, full CaPackDto round-tripped from H2
POST /api/ca/packs/{packId}/replay     → 200, fresh ValidationReportDto (no model, no tools)
```

`mvn clean verify` green; `mvn -pl ca-api spring-boot:run` boots cleanly with H2 + Flyway V1; all three endpoints exercised live during the session. Replay verdict matched the originally-stored verdict deterministically (REJECTED == REJECTED on the demonstration pack).

### Commits landing this session

| Hash       | Title                                                                                       |
|------------|---------------------------------------------------------------------------------------------|
| `5fc0d8a`  | feat(orchestration): real PipelineCoordinator end-to-end (replaces manual smoke wiring)     |
| _pending_  | feat(persistence): minimal H2-backed persistence + audit chain + replay endpoint            |

### Phase 2E — PipelineCoordinator real wiring (`5fc0d8a`)

Five files real, two skeletons renamed and implemented, two skeletons deleted from the path:

- **`PipelineCoordinator.run(...)`** — fixed-order step iteration with one try/catch per step. Returns a fully-populated `CaPackDto`. Caller-supplied `partyFacts` and `createdBy` (no PartyTool yet).
- **`InvokeToolsStep`** (was `GatherDataStep`) — fixed `[DOCUMENT_METADATA]` ToolId list today. Other ToolIds added to the list as their adapters land; ordering stable per commit.
- **`EvaluateChecklistStep`** (was `RunChecklistStep`) — calls `ChecklistEngine.evaluate`.
- **`InvokeAiStep`** — assembler → model client → Jackson best-effort parse → `RawAiOutputDto`. **NEVER throws**; on RuntimeException synthesises a MALFORMED `RawAiOutputDto` carrying the error so `ValidateAiStep` still runs.
- **`ValidateAiStep`** — runs `ValidationPipeline.validate`. Always-runs invariant.
- **`StepContext`** — the explicit data conduit. No hidden static state.
- **`OrchestrationConfig.pipelineCoordinator`** — bean parameters now match what's actually wired (no PackRepository / AuditWriter at this commit; that came in Phase 2F).
- **`CaPackController.createPack`** — calls coordinator end-to-end, returns 201 + `CaPackDto`.
- **`PipelineSmokeRunner`** — kept as regression baseline. Adds two coordinator-driven scenarios alongside the manual ones; final equivalence summary printed at the end of every run.

PipelineSmokeRunner output (this session): **manual PASS == coord PASS == VALID, manual FAIL == coord FAIL == REJECTED** with identical `FACT_NOT_GROUNDED` failure detail.

### Phase 2F — Minimal persistence + audit + replay (this milestone)

Decisions taken at the start of the milestone (locked):
- **H2 in-memory** for this milestone; MSSQL deferred to Phase 3.
- **Step-level audit**: one event per pipeline step + `PACK_CREATED` (5 events per pack).
- **Replay = validation-only**: `POST /{packId}/replay` re-runs the 10 checks against the stored `RawAiOutput` + frozen `ToolOutputs`. Never re-calls the model or tools.

Schema (Flyway `V1__init_ca_schema.sql`):
- **`csob_ca_packs`** — one row per pack. Scalar columns for the queryable surface (packId, partyId, status, versions, modelId, hash root). Nested DTOs serialise to CLOB columns via Jackson at the repository boundary (`party_facts_json`, `checklist_result_json`, `tool_outputs_json`, `raw_ai_output_json`, `validation_report_json`, `final_summary_json`, `reviewer_actions_json`, `sign_off_chain_json`).
- **`csob_ca_audit_log`** — append-only: `event_id, pack_id, event_type, event_json, prev_hash, hash, occurred_at`. Indexed on `pack_id` and on `(pack_id, occurred_at)`.

Six unused entity skeletons (`ChecklistFindingEntity`, `FinalSummaryEntity`, `RawAiOutputEntity`, `ReviewerActionEntity`, `ToolOutputEntity`, `ValidationReportEntity`) **deleted** so Hibernate doesn't try to validate non-existent tables. JSON-on-CLOB scaling decision: when normalisation is needed for queryability, those entities can be re-introduced and a Flyway V2 migration backfills.

Audit hash chain (`DbAuditWriter`):
```
prev_hash = last persisted event.hash for this packId  (null in row, ZERO_64 in SHA input for first event)
hash      = sha256_lower_hex( prev_hash || event.detailsJson )   // UTF-8 byte concat
```
Append-only in application code: `JpaPackRepository.persistPack` throws `IllegalStateException` if a row already exists for `packId`. Operator tooling can still patch under authority — DB does not hard-lock.

Audit events emitted by PersistStep (deterministic detailsJson per event; ordering is contract):
1. `INVOKE_TOOLS_COMPLETED` — `{"toolOutputCount":1}`
2. `EVALUATE_CHECKLIST_COMPLETED` — `{"checklistVersion":"v1.0","total":1,"passed":0,"failed":1,"missing":0,"notApplicable":0}`
3. `INVOKE_AI_COMPLETED` — `{"modelId":"…","modelVersion":"…","parseStatus":"PARSED|MALFORMED"}`
4. `VALIDATE_AI_COMPLETED` — `{"status":"VALID|REJECTED","totalChecks":10,"failedChecks":n}`
5. `PACK_CREATED` — `{"packId":"…","partyId":"…","status":"VALIDATED"}`

API surface added / completed:
- `POST /api/ca/packs` — runs pipeline + persists + returns 201 with `CaPackDto`.
- `GET  /api/ca/packs/{packId}` — reads from H2, deserialises full DTO via Jackson.
- `POST /api/ca/packs/{packId}/replay` — validation-only re-run; loads `rawAiOutput` + `toolOutputs` from H2; returns fresh `ValidationReportDto`. Does NOT touch `ModelClient` or `ToolInvoker`.

### Decisions made / problems found during integration

- **`@Transactional` impls cannot be `final`.** Spring's CGLIB AOP needs to subclass to proxy. Removed `final` from `JpaPackRepository` and `DbAuditWriter`.
- **Sealed `ToolPayload` did not round-trip through Jackson.** Resolved with `@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)` + `@JsonSubTypes` on the sealed interface — no wire-format type tag (Jackson infers from the unique top-level field per subtype). `ca-shared` now declares `jackson-annotations` (annotation-only). DTO contract unchanged structurally.
- **`server.servlet.context-path=/api` is stripped before Spring matchers run.** Two consequences caught and fixed:
  - `SecurityConfig.requestMatchers("/api/ca/**")` matched nothing → fixed to `/ca/**`.
  - `CaPackController` and `ReviewController` had `@RequestMapping("/api/ca/...")` → double-prefixed URLs → fixed to `/ca/...`.
- **`spring-boot-maven-plugin` runs from the module dir (`ca-api/`), not the reactor root.** `ca.tools.documents.root` defaulted to `./sample-data/documents` resolved to a non-existent path → fixed default to `../sample-data/documents`.
- **`GlobalExceptionHandler` was still skeleton** (throwing `UnsupportedOperationException`). Replaced with real RFC-7807 `ProblemDetail` bodies for `PipelineException`, `IllegalStateException`, `IllegalArgumentException`. Stack traces go to logs only; codes (`PIPELINE_STEP_FAILED`, `ILLEGAL_STATE`, `BAD_REQUEST`) on the response body.
- **`SecurityConfig` is dev-permit-all for `/ca/**`, `/actuator/**`, `/error`.** Loud `// DEV/LOCAL ONLY` callouts in code. `@EnableWebSecurity` added explicitly. Production must replace with: authenticated principal, role/permission gating, CSRF, 4-eyes for sign-off.

### v1 stub limitation surfaced by the live demo

`POST /api/ca/packs` produces a freshly-generated `packId` (e.g. `pack-1d229133`), but `StubModelClient.DEFAULT_CANNED_JSON` is hardcoded with `pack-test-0001`. The validation pipeline correctly catches this with `PACK_BINDING / PACK_ID_MISMATCH` — proof that the chain works. Replay reproduces the exact same finding deterministically. To get a clean live PASS today, the stub would need to template `packId` from the request — flagged for the next stub-templating refinement.

### Invariants re-verified (still intact)

- Deterministic layer is system of record. ✓
- AI is summarisation only; cannot populate any system-of-record field. ✓
- Every validation check is deterministic and never throws. ✓
- Single bounded AI call per pack. ✓
- No agentic AI, no autonomous tool selection, no OCR, no BLOB retrieval. ✓
- Tool adapters read-only and deterministic. ✓
- Audit log append-only and hash-chained. ✓ (now real)
- DTO contracts unchanged structurally. ✓ (`ToolPayload` got annotation-only metadata for Jackson DEDUCTION — no field, no wire format change)

### Open items going into next session

1. **Stub packId templating** — `StubModelClient` should echo `request.packId()` in its canned JSON so live demos produce VALID. Trivial change.
2. **MSSQL switch** — H2 was the milestone scope; MSSQL is a connection-string + Flyway-dialect change, plus a Testcontainers/integration test for confidence.
3. **Reviewer flow + 4-eyes** — `ReviewController` is still skeleton; `reviewerActions` and `signOffChain` lists are always empty.
4. **`SecurityConfig` hardening** — replace dev-permit-all with authenticated principal extraction and per-endpoint role gating before any non-local deployment.
5. **CSOB-backed `DocumentMetadataSource`** (Phase 3) — single-class swap behind the existing port.
6. **More checklist rules** — engine + version resolver shape support it; `R-DOC-EXPIRED` is the only real rule today.
7. **ArchUnit module-boundary tests** — the dependency graph is right; nothing yet enforces "controllers don't import `com.csob.ca.persistence.*`" beyond code review.
8. **JSON Schema byte-equality CI gate** — `ca-validation/schemas/ai-output.schema.json` ↔ `ca-ai-client/prompts/v1/output_schema.json`. Still drift-prone.
9. **`networknt` deprecation migration** — `SchemaValidatorsConfig` builder API.

---

## 2026-04-15 · Session 3 (end-to-end; real prompts, real HTTP client, real tool, first rule)

### Outcome at end of session
System is now a fully working end-to-end AI-assisted pipeline:

```
filesystem JSON fixtures
  → FilesystemDocumentMetadataSource
  → DocumentMetadataToolAdapter
  → DefaultToolInvoker                          (ToolOutputDto, frozen + hashed)
  → ChecklistEngineImpl + DocumentExpiryRule    (authoritative ChecklistResultDto)
  → TemplatePromptAssembler                     (schema-grounded AiRequest)
  → ModelClient  (StubModelClient default, HttpModelClient available)
  → RawAiOutputDto
  → DefaultValidationPipeline (10/10 real checks)
  → ValidationReportDto (VALID | REJECTED)
```

The smoke runner exercises this full slice today against filesystem fixtures
and a deterministic fixed clock. PASS scenario is fully VALID; FAIL scenario
is REJECTED with a single targeted check failure.

### Commits landed this session (8 ahead of initial scaffold)

| Hash       | Title                                                                        |
|------------|------------------------------------------------------------------------------|
| `4e4f9e3`  | feat(smoke): end-to-end stub-driven pipeline execution (PASS + FAIL scenarios verified) |
| `74e373e`  | refactor(validation): rename `*Checker` to `*Check` (no behavior change)     |
| `fad2706`  | feat(ai-client): implement PromptAssembler with schema-driven structured prompt (stub model retained) |
| `c7cbe7b`  | feat(ai-client): add HttpModelClient (provider-agnostic, retry-safe, stub retained) |
| `b49161b`  | feat(tools): filesystem-backed DocumentMetadataSource + adapter (CSOB-ready seam) |
| _pending_  | feat(checklist): first deterministic rule (DocumentExpiryRule) + engine wiring |

### Phase completions

- **Phase 1** (Architecture + Validation) — closed last session.
- **Phase 2A** (PromptAssembler) — `TemplatePromptAssembler`, `ClasspathPromptLoader`,
  six `{{placeholder}}` substitutions, `system.md` hardened. Prompt is
  model-agnostic (schema embedded twice — once inline in the user prompt,
  once as a standalone `outputSchemaJson` field for structured-output APIs).
- **Phase 2B** (ModelClient) — `HttpModelClient` on the JDK built-in
  `java.net.http.HttpClient`: retries on `HttpTimeoutException`, other
  `IOException`, and HTTP 5xx only; fails fast on 4xx. `StubModelClient`
  stays the default via `ca.ai.provider=STUB|HTTP` property switch.
- **Phase 2C** (first tool integration) — `DocumentMetadataSource` port,
  `FilesystemDocumentMetadataSource` impl (path-traversal guarded),
  per-party JSON fixtures at `backend/sample-data/documents/`,
  `DocumentMetadataToolAdapter` delegates to the source, `DefaultToolInvoker`
  dispatches by `ToolId`. Null other-tool slots throw
  `UnsupportedOperationException` with a clear message.
- **Phase 2D** (first deterministic rule) — `DocumentExpiryRule` with
  `Clock` injection, `DefaultChecklistVersionResolver` pinning `v1.0` to
  `[DocumentExpiryRule]`, `ChecklistEngineImpl` with tally + SHA-256
  `toolOutputsHashRoot`. Smoke runner replaces the hand-rolled
  `mockChecklistResult(...)` with `engine.evaluate(...)` against a pinned
  `Clock.fixed(2026-04-15Z)`.

### Decisions made this session

- **Renaming cleanup deferred to its own commit (`74e373e`)** — all ten
  validation classes went from `*Checker` to `*Check` with `git mv` +
  `sed`; git detected every one as a ≥95% rename, so `git log --follow`
  still traces history. Support-class Javadocs touched in the same sweep.
- **Field-name drift accepted** — `ValidationFailureDto` still uses
  `locator`/`detail`. Later task specs drifted to `path`/`message`; same
  semantics, kept the DTO to avoid `ca-shared` churn.
- **Stub model client kept as default** — every new layer (prompt, HTTP,
  tools, rules) landed as an additive change with `StubModelClient`
  remaining the default bean, so the smoke runner stayed runnable at
  every step.
- **FAIL > MISSING > PASS > NOT_APPLICABLE** in `DocumentExpiryRule`:
  expired doc (FAIL) takes precedence over doc-with-no-expiresOn (MISSING),
  which takes precedence over all-valid (PASS). No docs at all →
  NOT_APPLICABLE. Matches user guidance to keep findings minimal-noise
  while satisfying the DTO's "evidence required for PASS/FAIL/MISSING"
  invariant (one minimal evidence entry on PASS, per-doc evidence
  otherwise).
- **Fixed `Clock.fixed(2026-04-15Z)` in the smoke runner** so the
  filesystem fixtures stay meaningful regardless of wall-clock drift.
  Production beans use `Clock.systemUTC()`.
- **`toolOutputsHashRoot` is a real SHA-256** over concatenated
  `payloadHash|` strings. Still a placeholder (`0000…`) at the
  `ToolOutputDto` level pending a canonical-JSON hasher, but at least the
  pack-level root is now content-derived.

### Invariants re-verified (still intact)

- Deterministic layer is the system of record. ✓
- AI is summarisation only; never populates a system-of-record field. ✓
- Every validation check is deterministic and never throws. ✓
- Single bounded AI call per pack. ✓
- No agentic AI, no tool selection by AI, no OCR, no BLOB retrieval. ✓
- Schema + DTOs unchanged. ✓
- Tool adapters are read-only and deterministic. ✓

### Pipeline state proof (this-session smoke output)

```
[fixtures] sample-data dir: backend/sample-data/documents
[checklist] version=v1.0 evaluatedAt=2026-04-15T00:00:00Z
            totalRules=1 pass=0 fail=1 missing=0 n/a=0
[checklist]   R-DOC-EXPIRED  FAIL (1 evidence)
[checklist]     - DOCUMENT_META/doc-001.expiresOn = 2026-04-01

[PASS-SCENARIO] report.status = VALID     (10/10 checks PASS)
[FAIL-SCENARIO] report.status = REJECTED  (9 PASS, 1 FACT_NOT_GROUNDED @
                                            /sections/0/sentences/0/factMentions/2)
```

### Next up (Phase 3)

1. **CSOB-backed `DocumentMetadataSource`** — single new class behind the
   same `DocumentMetadataSource` interface; bean swap in
   `OrchestrationConfig` keyed off a new `ca.tools.documents.provider`
   property. Nothing else in the codebase changes.
2. Additional checklist rules (e.g. document-type completeness,
   screening-hit thresholds, UBO completeness).
3. Further tool adapters (`ScreeningTool`, `RelatedPartiesTool`, `PartyTool`).
4. ArchUnit module-boundary tests — promised at the v1 contract lock-in
   but not yet in CI.
5. `networknt` deprecation migration (`SchemaValidatorsConfig` builder API).
6. JSON Schema byte-equality CI gate between
   `ca-validation/resources/schemas/ai-output.schema.json` and the
   `ca-ai-client` prompt mirror.

### Not yet in scope (Phases 4–5)

- `DocumentBlobSource` + OCR/extraction.
- Richer AI prompt context (post-OCR).
- Bounded agentic layer — tool orchestration only, still deterministic
  gates before and after.

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
