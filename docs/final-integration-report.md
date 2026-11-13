# LandStack — Final Integration Report (Module 16)

Date: 2026-11-13 · Scope: SRS v1.0 / prompt-pack Modules 0–16.

## Verification status (read first)
All modules are **implemented as source code**. The authoring environment could not install
dependencies (Maven artefacts, npm packages, pip packages), so **no build or test has been executed yet**.
Checks that were possible without installing anything are listed in `test-report.md`. Before the
demonstration, run the commands in the README "Tests" section, fix anything that fails, and record the
real results in `test-report.md` and in the "Evidence" column below.

## Implemented features
Authentication (sessions, Argon2id, CSRF, throttling, revocation) · admin user management · role
enforcement (3 roles) · secure upload + signature detection + checksum + duplicate detection · import job
state machine, timeline, cancel/retry/remove · CSV/XLSX parsing with sheet/header selection and diagnostics ·
optional PDF extraction service with scanned/encrypted/corrupt handling · alias-based mapping suggestions,
templates, dataset options · deterministic normalization `norm-1.0` incl. state-specific regional units ·
preview with raw vs normalized, corrections, exclusions · transactional idempotent confirmation with lineage ·
record catalogue (search/filter/sort/paginate, provenance, corrections, archive) · Leaflet map of valid
coordinates only · 24 deterministic quality rules + review workflow + re-run · advisory statistical check ·
dashboard aggregates · audit trail · safe CSV export · retention/recovery jobs · Docker Compose · CI ·
documentation.

## Partially implemented / not implemented
| Item | Status | Reason |
|---|---|---|
| OCR for scanned PDFs | Not implemented | Optional in SRS; reported honestly as unsupported |
| IsolationForest in-app | Not adopted (offline evaluation only) | Adoption rule in `ml-evaluation.md`; results pending a run |
| PostGIS / clustering | Not used | Not needed at ≤ 5,000 records; bounded canvas rendering instead |
| Genuine screenshots | Pending | Capture from the running system for the report |
| Executed test evidence | Pending | See verification status |

## Architecture summary
React/TS SPA → same-origin `/api` → Spring Boot 3.3 (Java 21) → PostgreSQL 16 (Flyway V1–V8); optional
FastAPI PDF service called with a shared token. Details in `architecture.md`.

## Database / migrations
V1 users+audit · V2 Spring Session · V3 source_file+import_job · V4 processing_attempt+import_row ·
V5 mapping templates+import_mapping · V6 land_record+land_record_source_value · V7 quality_issue · V8 pg_trgm
indexes. `SecurityIT.flywayMigratedAllVersions` checks a clean database; the Testcontainers run also
exercises a second context start on the same database (no re-migration).

## Security summary
See `security-test-report.md` (27 controls; critical/high implemented; evidence pending a test run).

## SRS / prompt-pack traceability matrix
| Requirement | Implementation | Test evidence (file) |
|---|---|---|
| 3 roles enforced server-side | `SecurityConfig`, `Perms`, `@PreAuthorize`, `ActiveUserFilter` | `SecurityIT.reviewerCannotMutateAnything`, `onlyAdministratorsManageUsers…` |
| Secure login/logout, hashing, session revocation | `AuthController`, `PasswordConfig`, `SessionRevoker` | `SecurityIT.loginRequiresCsrf…`, `deactivatedUserLosesAccess…`, `repeatedFailuresAreRateLimited` |
| Upload PDF/CSV/XLSX with validation | `ImportService.upload`, `FileTypeDetector`, `FileStorageService` | `UploadIT.*`, `SafetyTest.detects…/rejects…` |
| Import job lifecycle & status | `ImportStatus`, `ImportJob.transitionTo`, `ParsingService` | `SafetyTest.importStateMachine…`, `UploadIT.validCsv…` |
| CSV/Excel parsing, sheet selection, preview | `CsvParser`, `XlsxParser`, `TabularBuilder`, `PreviewController` | `ParsingTest.*`, `UploadIT.multiSheetWorkbook…` |
| PDF extraction, scanned detection | `processing-service/app/pdf_extract.py`, `PdfTabularMapper` | `tests/test_extract.py`, `PdfTabularMapperTest` |
| Field mapping with confirmed suggestions, templates | `MappingSuggestionService`, `MappingService`, `MappingController`, `MappingEditor.tsx` | `SafetyTest.mappingSuggestions…`, `WorkflowIT` |
| Deterministic normalization, lineage of raw values | `Normalizer`, `NormalizationDictionaries`, `land_record_source_value` | `NormalizerTest.*`, `WorkflowIT` (source values, corrections) |
| Human review before persistence | `ReviewPanel.tsx`, `RowInspector.tsx`, `MappingService.correctRow` | `WorkflowIT` (0 records before confirm) |
| Transactional, idempotent confirmation | `ConfirmationService`, `ConfirmationController` | `WorkflowIT` (replay), `RollbackIT` |
| Searchable catalogue & details | `LandRecordService`, `LandRecordFilter`, `LandRecordsPage.tsx`, `RecordDetailPage.tsx` | `WorkflowIT` (search, detail) |
| Map of valid coordinates only, non-mappable count | `MapController`, `MapPage.tsx`, `CoordinateValidator` | `WorkflowIT` (map counts), `NormalizerTest.invalidCoordinates…` |
| Rule-based quality checks, duplicates not merged | `Normalizer` (preview rules), `QualityEngine`, `QualityService` | `WorkflowIT` (dup flags, 5 records, rerun 0 new) |
| Human issue review with notes, audited | `QualityService.review`, `QualityPage.tsx` | `WorkflowIT` (note required, audit) |
| Optional ML, advisory | `StatisticalAnomalyService`, `ml/evaluate_area_anomaly.py` | `ml-evaluation.md` (pending run) |
| Dashboard from DB | `DashboardService` | `WorkflowIT` (dashboard = DB) |
| Audit trail | `AuditService`, `AuditController`, `AuditPage.tsx` | `WorkflowIT` (EXPORT_CREATED, ISSUE_DISMISSED, IMPORT_CONFIRMED) |
| Safe CSV export | `ExportService`, `CsvSafe` | `SafetyTest.csvFormulaInjection…`, `WorkflowIT` export |
| Python optional / failure handled | `ProcessingServiceClient` | `UploadIT.pdfFailsHonestly…` |
| No live government APIs, no geocoding, no legal claims | code + UI copy | review (grep for external calls: only OSM tiles and the internal service) |
| Docker / CI / docs | `docker-compose.yml`, Dockerfiles, `ci.yml`, `docs/` | `docker compose config -q` passed |

## Demo instructions
README → "Quick start with Docker" and "Demo workflow".

## Known limitations
`limitations-and-future-work.md`.
