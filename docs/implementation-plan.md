# LandStack — Implementation Plan

Modules are built in order. Each module ends with: build passes, tests pass (output recorded),
`implementation-log.md` gets a Module Completion Report, and a git commit is made.

| # | Module | Key deliverables | Main tables (Flyway) | Exit evidence |
|---|---|---|---|---|
| 0 | Audit & architecture | docs/*, README, CLAUDE.md | – | docs reviewed |
| 1 | Backend foundation | Maven wrapper, Spring Boot 3.x/Java 21, Flyway, error envelope, actuator, OpenAPI, Testcontainers/local-PG tests | V1 `app_user`, `audit_event` | context + migration tests green |
| 2 | Auth & roles | session login/logout, Argon2id, CSRF, rate limit, user admin API, dev seeder, role tests | V2 spring_session | ≥1 test per role boundary |
| 3 | Frontend shell | Vite/React/TS strict, Tailwind tokens, router, API client, guards, shell, UI kit, login | – | `npm run build`, vitest green |
| 4 | Upload & jobs | multipart upload, signature check, SHA-256, storage, state machine, history UI | V3 `source_file`, `import_job` | malicious-upload tests green |
| 5 | CSV/XLSX parsing | Commons CSV + POI streaming, sheet/header select, staged preview, diagnostics | V4 `processing_attempt`, `import_row` | parser fixture tests |
| 6 | PDF extraction | FastAPI service, PyMuPDF/pdfplumber, scanned detection, Java client, preview UI | – | pytest + unavailable-service test |
| 7 | Mapping & normalization | alias suggestions, mapping UI, norm-1.0 engine, preview-stage rules, templates | V5 `field_mapping_template`, `import_mapping` | determinism + conversion tests |
| 8 | Confirmation | review screen, transactional idempotent confirm, lineage, timeline, demo-import deletion | V6 `land_record`, `land_record_source_value` | rollback + replay tests |
| 9 | Catalogue & search | filtered/paged search, detail with provenance, corrections, owner masking | V7 indexes | search/permission tests |
| 10 | Map view | `/map/points`, Leaflet + clustering, legend, not-mappable list | – | coordinate tests |
| 11 | Quality engine | post-confirm rules, dedup keys, issue review UI | V8 `quality_issue` | positive/negative per rule |
| 12 | Dashboard/audit/export | aggregation endpoints, Recharts, audit viewer, safe CSV export | – | fixture-matched metrics |
| 13 | Optional ML | IQR baseline vs IsolationForest on labelled synthetic set, or documented non-adoption | – | evaluation report |
| 14 | Security review | adversarial tests (A/B/C users, UUID probing), headers, dep scan | grants | security-test-report.md |
| 15 | Testing & perf | Playwright E2E for the global scenario, 5,000-record perf run | – | test-report.md |
| 16 | Docker/CI/docs | Dockerfiles, compose, GitHub Actions, user guide, final report | – | fresh-clone run |

Test DB: CI and Docker use Testcontainers (`postgres:16`). This machine also runs a native
PostgreSQL 16, so integration tests may point at a disposable `landstack_test` database via
`LANDSTACK_TEST_DB_URL`. H2 is not used.

## Risks

| Risk | Mitigation |
|---|---|
| PDF layouts vary widely; extraction quality may be poor | Treat PDF as assisted entry with mandatory review. Demo with synthetic text PDFs. Disclose the limits. |
| Scope creep (OCR, PostGIS, ML) | Each one is gated as optional in the plan and needs guide approval. |
| Local land units (bigha etc.) differ by state | No default conversion; flag instead of guessing. |
| Owner names are personal data | Synthetic data only; reviewer masking; never logged. |
| Single developer timeline | Modules 1–12 are the demo-critical path. 13 is optional; 14–16 are hardening. |
