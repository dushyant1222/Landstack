# LandStack

**GIS-Based Land Information Integration & Governance Dashboard** — an academic, software-only project
(BS in Computer Science & Data Analytics, IIT Patna · SRS v1.0, 14 Oct 2026).

LandStack lets authorised users import heterogeneous land datasets (CSV, Excel `.xlsx`, text-based PDF),
map their columns to one canonical schema, normalise values while keeping full source lineage, review
the preview and confirm it, then search the records, see valid coordinates on a map and review
rule-based data-quality flags. Dashboard figures and CSV exports come from PostgreSQL.

> **Scope boundary.** LandStack is not a legal authority, cadastral instrument or title-verification
> service. It does not connect to live government systems, does not geocode, and never invents values,
> coordinates or boundaries. Every quality flag is a *potential issue for human review*. Use synthetic or
> authorised sample data only.

## Project status (as of 2026-11-13)

| Part | Code written | Compiled / built | Tests run |
|---|:-:|:-:|:-:|
| Database (Flyway migrations V1–V8) | ✅ | ⏳ not yet | ⏳ not yet |
| Backend, Spring Boot (115 source files, Modules 1–14) | ✅ | ⏳ not yet | ⏳ not yet (8 test classes written, plus 2 helpers) |
| Frontend, React + TypeScript (all pages) | ✅ | ⏳ not yet | ⏳ not yet (5 unit tests + 1 Playwright E2E written) |
| PDF processing service, Python (optional) | ✅ | ✅ syntax check only | ⏳ not yet (pytest written) |
| Sample data (synthetic) | ✅ | ✅ generated | – |
| Docker Compose / CI / docs | ✅ | ✅ `docker compose config` valid | – |

**What this means:** every module (0–16) and every feature listed below is implemented in code. The code
was written on a server where Maven, npm and pip dependencies could not be installed, so **it has not
yet been compiled, run or tested**. Expect a few small compile errors on the first build; they are
normally quick to fix. Before calling the project finished:

1. Run every command in [Tests](#tests).
2. Fix any failures.
3. Record the real results in `docs/test-report.md`. It is deliberately empty, so it never shows
   invented results.

Checks already done here: `docker compose config -q`, `bash -n mvnw`, `py_compile` on all Python files,
JSON validation, and a static scan for unused or missing imports, whose findings were fixed.

---

## Features
| Area | What it does |
|---|---|
| Accounts & roles | Administrator-provisioned accounts; roles `ADMINISTRATOR`, `DATA_OPERATOR`, `READ_ONLY_REVIEWER` enforced in the backend (URL rules + `@PreAuthorize` + per-request DB re-check). Argon2id passwords, server-side sessions (HttpOnly cookie), CSRF protection, login throttling, immediate revocation on deactivation. |
| Upload | Drag-and-drop with real progress. The server checks size, extension **and content signature**, rejects macros and executables, stores bytes under server-generated keys outside any web root, and records SHA-256 (duplicate uploads are flagged, never overwritten). |
| Parsing | CSV (encoding + delimiter detection, BOM, bounded rows/columns) and XLSX (streaming, sheet selection, cached formula values only, ISO dates, zip-bomb limits) in Java. PDF text/tables/`Label: value` candidates via the optional Python service. Scanned PDFs are reported as *OCR required*. |
| Mapping & normalisation | Exact-alias suggestions that must be confirmed, reusable versioned templates, deterministic `norm-1.0` rules: whitespace/placeholder policy, identifiers kept as text, area parsing (Indian digit grouping, embedded units) and conversion to m², including **state-specific regional units** (bigha, biswa, katha, dhur, kanal, marla…), land-type category dictionary (with Hindi revenue terms), unambiguous dates only, coordinate validation, GeoJSON validation. |
| Review & confirm | Raw vs normalised side by side, blocking errors vs warnings, per-row corrections, exclusions, "mark reviewed" for PDF rows, then **one all-or-nothing, idempotent transaction** with lineage (source file, import job, row, page, per-field raw value + transformation). |
| Catalogue | Server-side search, filters, sorting, pagination; record detail with provenance and the full transformation history; audited corrections (lineage immutable); archive/restore. Owner names are masked for reviewers. |
| Map | Leaflet map of records with valid source coordinates only, legend by land type, optional source-supplied boundaries, mappable / not-mappable counts, OSM attribution, tile-failure banner. |
| Data quality | 24 deterministic rules (missing IDs, negative/extreme area, unit problems, dates, coordinates, in-file and cross-import duplicates, conflicting duplicates, unreviewed extraction…), dedup keys, review workflow (note required to resolve/dismiss), optional advisory log-IQR statistical check. |
| Dashboard, audit, export | SQL aggregates (records, imports by status, mappable split, open issues by severity/category, records per day, top districts, recent activity); append-only audit trail; CSV export with formula-injection protection and row limit. |

## Architecture
```
React 18 + TypeScript (Vite, Tailwind, TanStack Query, React Hook Form + Zod, Leaflet, Recharts)
        │  same-origin /api (Vite proxy in dev, nginx in Docker) · session cookie + X-XSRF-TOKEN
        ▼
Spring Boot 3.3 / Java 21  — auth · users · imports · files · parsing(CSV/XLSX) · mapping · landrecords
        │                     geospatial · quality · audit · dashboard · exports
        ├── PostgreSQL 16 (Flyway V1–V8, pg_trgm)
        └── processing-service (Python 3.12 FastAPI, optional) — PDF extraction only
```
Details: [docs/architecture.md](docs/architecture.md).

## Repository layout
```
backend/             Spring Boot API (Maven wrapper, Flyway migrations, tests)
frontend/            React app (Vitest unit tests, Playwright E2E)
processing-service/  Optional FastAPI PDF extraction service (+ ml/ offline evaluation)
sample-data/         Synthetic demo datasets + generator (no real persons or parcels)
docs/                Architecture, data dictionary, workflow, API, security, tests, guides, reports
docker-compose.yml   PostgreSQL + processing + backend + frontend
.github/workflows/   CI: backend verify, frontend lint/typecheck/test/build, pytest, compose check
```

## Prerequisites
* JDK 21 · Node.js 22 LTS + npm · PostgreSQL 16 (or Docker) · Python 3.12 (PDF service only)
* Docker (for Testcontainers integration tests, or set `LANDSTACK_TEST_DB_URL`)

## Quick start with Docker (recommended for the demo)
```bash
cp .env.example .env
# set LANDSTACK_DB_PASSWORD, LANDSTACK_PROCESSING_TOKEN (≥16 chars), LANDSTACK_DEMO_PASSWORD (≥10 chars, letter+digit)
docker compose up --build
```
Open **http://localhost:8088** and sign in as `admin@landstack.demo`, `operator@landstack.demo` or
`reviewer@landstack.demo` with `LANDSTACK_DEMO_PASSWORD`. These demo accounts exist **only** in the `dev` profile.
Swagger UI (dev profile): http://localhost:8080/swagger-ui.html.

Stop: `docker compose down` (keeps data) · Reset demo data: `docker compose down -v` (**deletes** the database and uploads volumes).

## Running without Docker
**Database**
```bash
sudo -u postgres psql -c "CREATE ROLE landstack LOGIN PASSWORD 'choose-a-password';"
sudo -u postgres psql -c "CREATE DATABASE landstack OWNER landstack;"
```
**Backend** (Flyway migrates automatically on startup)
```bash
cd backend
export LANDSTACK_DB_URL=jdbc:postgresql://localhost:5432/landstack LANDSTACK_DB_USER=landstack LANDSTACK_DB_PASSWORD=choose-a-password
export LANDSTACK_STORAGE_DIR=$HOME/landstack-uploads LANDSTACK_DEMO_PASSWORD='Demo-Passw0rd'
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```
**Processing service (optional, for PDFs)**
```bash
cd processing-service && python3.12 -m venv .venv && . .venv/bin/activate && pip install -r requirements.txt
export PROCESSING_TOKEN=<same value as LANDSTACK_PROCESSING_TOKEN>
uvicorn app.main:app --port 8090
```
**Frontend**
```bash
cd frontend && npm install && npm run dev     # http://localhost:5173 (proxies /api to :8080)
```

## Demo workflow (global acceptance scenario)
1. Admin → *User management* → add an operator and a reviewer.
2. Operator → *Import datasets* → upload `sample-data/bihar_parcels_sample.csv`.
3. *Field mapping*: check suggestions, tick "Dates are day-first", *Save mapping & preview*.
4. *Review & confirm*: inspect a blocked row (negative area), correct it or leave it excluded; confirm.
5. Upload `bihar_parcels_revised.csv` and confirm → cross-import duplicate candidates appear in *Data quality*.
6. Reviewer → *Land records* → search `BR`, open a record → provenance; *Map view* → mappable vs not-mappable counts.
7. Reviewer has no upload/edit/review/export controls; the API also returns 403.
8. Operator → *Data quality* → dismiss an issue with a note; *Land records* → *Export CSV*.
9. Upload `sample-data/invalid/pdf_renamed_as.csv` → rejected ("content does not match its extension").
10. Try `rajasthan_khasra_register.xlsx` (sheet selection, pucca/kaccha bigha) and `mutation_extract_sample.pdf`
    (PDF candidates; needs the processing service) and `scanned_like_sample.pdf` (OCR-required state).

## Tests
```bash
cd backend && ./mvnw verify                      # unit + PostgreSQL integration (Testcontainers needs Docker)
#   or against a disposable database: LANDSTACK_TEST_DB_URL=jdbc:postgresql://localhost:5432/landstack_test ./mvnw verify
cd frontend && npm run lint && npm run typecheck && npm test && npm run build
cd frontend && E2E_PASSWORD=<demo password> npx playwright install chromium && npm run e2e   # needs the running stack
cd processing-service && pip install -r requirements-dev.txt && pytest -q
python processing-service/ml/evaluate_area_anomaly.py --out docs/ml-evaluation-results.md     # optional (Module 13)
```
See [docs/test-plan.md](docs/test-plan.md).

## Environment variables
All variables are listed with comments in [.env.example](.env.example). Required: `LANDSTACK_DB_*`,
`LANDSTACK_STORAGE_DIR` (the backend refuses to start without it). Never commit `.env`.

## Known limitations
OCR is not implemented; PDF layouts other than ruled tables or `Label: value` lines are not supported;
regional unit factors are customary values that vary locally; login throttling is per instance; see
[docs/limitations-and-future-work.md](docs/limitations-and-future-work.md).

## Documentation
[Architecture](docs/architecture.md) · [Data dictionary](docs/data-dictionary.md) · [Import workflow](docs/import-workflow.md) ·
[API contract](docs/api-contract.md) · [Security test report](docs/security-test-report.md) · [Test plan](docs/test-plan.md) ·
[Test report](docs/test-report.md) · [Deployment](docs/deployment.md) · [User guide](docs/user-guide.md) ·
[ML evaluation](docs/ml-evaluation.md) · [Limitations](docs/limitations-and-future-work.md) ·
[Implementation log](docs/implementation-log.md) · [Final integration report](docs/final-integration-report.md)
