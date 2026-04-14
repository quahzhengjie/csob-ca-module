# CSOB Customer Assessment (CA) Module

AI-assisted preparation and review system for KYC Customer Assessment packs.

**This is not a decision system.** All risk ratings and onboarding decisions
are made by human reviewers. The AI layer produces a non-authoritative
summary only, which is rejected by a deterministic validation gate if it
fails any check.

## Module map and dependency direction

```
ca-shared         (DTOs + enums only; zero dependencies)
   ▲   ▲   ▲   ▲   ▲
   │   │   │   │   │
ca-checklist  ca-tools  ca-ai-client  ca-validation  ca-persistence
   ▲   ▲   ▲   ▲   ▲
   └───┴───┴───┴───┘
           │
    ca-orchestration  (uses all of the above; the ONLY composer)
           ▲
           │
        ca-api        (Spring Boot launcher; compile-depends only on
                       ca-orchestration + ca-shared. Persistence reaches
                       the classpath transitively for bean scanning but
                       MUST NOT be imported from controllers.)
```

### Enforced rules

1. `ca-shared` holds **only** DTOs and enums. Zero external dependencies.
2. `ca-checklist`, `ca-tools`, `ca-ai-client`, `ca-validation`, `ca-persistence`
   each depend **only on `ca-shared`** (plus narrowly-scoped externals).
3. `ca-checklist` must not import `ca-ai-client` or `ca-validation`.
4. `ca-ai-client` must not import `ca-checklist`, `ca-tools`, or persistence.
5. `ca-orchestration` is the **only** module permitted to wire others.
6. `ca-api` declares Spring Boot starters; no other module may.
7. Controllers must not import `com.csob.ca.persistence.*` types.

Enforcement today: code review + the Maven module dependency graph.
Enforcement goal: an ArchUnit test module that fails CI on violation (TODO).

## Prerequisites

- Java 21
- Maven 3.9+ (or install the wrapper once via `mvn wrapper:wrapper`)

## Build

```bash
cd backend
mvn -T 1C clean install
```

## Import into IntelliJ IDEA

1. `File → Open…` and select `csob-ca-module/backend/pom.xml`.
2. Choose **Open as Project** (IDEA detects the multi-module structure
   automatically and registers all eight modules).
3. Set the project SDK to **Java 21** (`File → Project Structure → Project`).
4. Set the language level to **21 — Record patterns, pattern matching for switch**.
5. Enable annotation processing:
   `Settings → Build, Execution, Deployment → Compiler → Annotation Processors`
   → check **Enable annotation processing**.
6. Configure the Spring Boot run configuration against
   `com.csob.ca.api.CaApplication`, working directory `backend/ca-api`,
   active profile `local`.
7. (Optional) Install the **SonarLint** and **Checkstyle-IDEA** plugins —
   CI will use the same rulesets.

## What is scaffolded

- All DTO records in `ca-shared.dto` with immutability enforced via
  defensive `List.copyOf` in compact constructors.
- All closed enums in `ca-shared.enums`.
- Interfaces for every layer boundary: `ChecklistEngine`, `ToolInvoker`,
  `ModelClient`, `PromptLoader`, `PromptAssembler`, `ValidationPipeline`,
  `ValidationCheck`, `CitationResolver`, `Tokeniser`, `PackRepository`,
  `AuditWriter`.
- Skeleton classes for the pipeline steps, the state machine, and all
  ten validation checks — every method body throws
  `UnsupportedOperationException` with a specific implementation note.
- Prompt and schema resource placeholders under `ca-ai-client/resources/prompts/v1/`
  and `ca-validation/resources/schemas/`.
- A Flyway V1 placeholder under `ca-persistence/resources/db/migration/`.
- `application.yml` with profile-ready keys; no secrets committed.

## What is NOT scaffolded (intentional)

- Frontend (sibling `../frontend/` Next.js app).
- Real rule implementations — each goes under `ca-checklist/rules/v1/`.
- Real tool adapter implementations — each under `ca-tools/adapter/`.
- The real JSON Schema body — tracked against the design doc §5.
- Secrets, vault wiring, and the real model provider class.
- ArchUnit tests — planned as a follow-up `ca-architecture-tests` module.

## Invariants (load-bearing)

- The deterministic layer is the system of record.
- AI is summarisation only. No risk rating, no decisions.
- No agentic AI in v1. Single, stateless model call.
- Every AI sentence must cite and ground to a `ToolOutput` / finding.
- Human review is mandatory and gated by the backend state machine.
- The audit log is append-only and hash-chained per pack.
