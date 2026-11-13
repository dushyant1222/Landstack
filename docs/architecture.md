# LandStack — Architecture

Status: **Implemented (Modules 0–16).** The decisions in §11 were taken by the implementer as instructed by the student (2026-11-13) and can be revisited with the guide.

## 1. Repository assessment (Module 0 audit, 2026-11-13)

| Item | Finding |
|---|---|
| Existing code | None. The workspace (`/root/dushyant`) was empty; greenfield build. |
| Existing docs/config/migrations | None. |
| Toolchain present | OpenJDK 21.0.7, Node 22 / npm 10.9, Python 3.12.7, Docker 27.5, PostgreSQL 16.9 server (local, running), git 2.45. |
| Toolchain absent | Maven (→ use Maven Wrapper), PostGIS extension, Python libs (fastapi, pandas, openpyxl, PyMuPDF, pdfplumber, scikit-learn, pytest) → installed per-module into a venv. |
| Network | Maven Central reachable. |

No working implementation existed, so nothing was overwritten.

## 2. Component architecture

```
 Browser (React 18 + TS, Vite, Tailwind, TanStack Query, Leaflet)
     │  HTTPS, JSON + multipart; session cookie (HttpOnly) + CSRF header
     ▼
 Spring Boot 3 / Java 21  ── "core" (always required)
   security · auth · users · imports · files · parsing(CSV/XLSX) · mapping/normalization
   landrecords · geospatial · quality · audit · dashboard · exports
     │ JDBC (Flyway-managed)                │ HTTP, internal only, shared-secret header
     ▼                                       ▼
 PostgreSQL 16                         processing-service (Python 3.12, FastAPI) — OPTIONAL
   relational schema, JSONB staging       PDF text/table extraction (PyMuPDF / pdfplumber)
                                          optional anomaly analysis (Module 13)
 Upload storage: local directory outside any web root (LANDSTACK_STORAGE_DIR), volume in Docker.
```

## 3. Decision: where parsing happens

**CSV and XLSX are parsed inside Spring Boot** (Apache Commons CSV; Apache POI streaming reader for
XLSX). **PDF extraction is a separate FastAPI service.**

Why this split:
- The core workflow (upload → map → confirm → search → map → quality) must keep working when Python
  is down (pack rule A/E5). If CSV/XLSX went through Python, losing that service would stop all imports.
- Java has mature, safe, bounded readers for CSV/XLSX. POI's event/streaming API reads cached formula
  *results* and never evaluates formulas or macros.
- PDF extraction is much stronger in Python (PyMuPDF, pdfplumber) than in Java, so it justifies a
  service boundary. When the service is unavailable, PDF jobs fail with a retryable error and nothing
  else is affected.
- An HTTP service is easier to test, time-limit and isolate than spawning Python subprocesses from
  Java.

Trade-off: two runtimes to run. The cost is limited because only PDF and Module 13 need Python, and
Docker Compose starts both. Rejected alternative: pandas for all parsing, which would make Python
mandatory.

## 4. Authentication design

- **Server-side sessions** (Spring Session JDBC in PostgreSQL), not JWT. The cookie is `LANDSTACK_SESSION`
  with `HttpOnly`, `SameSite=Lax`, and `Secure` when deployed. Idle timeout is 30 min (configurable).
  Logout and account deactivation delete the session rows, so revocation takes effect immediately.
  No tokens are kept in localStorage.
- **CSRF**: Spring Security `CookieCsrfTokenRepository`. The SPA sends the `X-XSRF-TOKEN` header on
  every mutating request.
- **Passwords**: Argon2id (`Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()`), with BCrypt(12)
  as the documented fallback.
- **Login protection**: per-account and per-IP attempt throttling (in-memory bucket; documented
  limitation: it does not cluster). Every failure returns the same generic "Invalid credentials" message.
- **Accounts**: provisioned by an administrator; there is no public self-registration. Demo users are
  created only by the `dev` profile seeder (Module 2).

## 5. Roles and permissions (enforced in the backend)

| Capability | ADMIN | OPERATOR | REVIEWER |
|---|:-:|:-:|:-:|
| Manage users, activate/deactivate, assign roles | ✔ | ✘ | ✘ |
| Upload, parse, map, preview, confirm imports | ✔ | ✔ | ✘ |
| View import jobs and source-file metadata | ✔ | ✔ (all; see §11-Q3) | ✔ metadata only |
| Download/preview source file bytes | ✔ | ✔ | ✘ |
| Search/view land records | ✔ | ✔ | ✔ (owner name masked, §11-Q4) |
| Correct record fields (not lineage) | ✔ | ✔ | ✘ |
| View quality issues | ✔ | ✔ | ✔ |
| Change issue status | ✔ | ✔ | ✘ |
| Export CSV | ✔ | ✔ | ✘ (§11-Q5) |
| View audit trail | ✔ | own actions | ✘ |
| Delete demo imports | ✔ | ✘ | ✘ |

Enforcement happens in three layers: URL rules in `SecurityFilterChain`, `@PreAuthorize` on service
methods, and owner/scope predicates in repository queries. Roles always come from the database user,
never from the request.

## 6. GIS strategy

- The baseline feature is points: `latitude NUMERIC(9,6)` in [-90,90] and `longitude NUMERIC(9,6)`
  in [-180,180]. Both components must be present and valid for a record to be mappable.
- `geometry` is optional, stored as validated GeoJSON (`JSONB`), and accepted only when the source
  supplies it. Allowed types are Polygon, MultiPolygon and Point. Validation checks that coordinates
  are within range and that rings are closed. Point coordinates and parcel boundary geometry are shown
  differently on the map.
- **No PostGIS initially.** At ≤ 5,000 records, a bounding-box filter on indexed numeric columns is
  enough. Add PostGIS later only if polygon spatial queries are needed (decision recorded in Module 10).
- No geocoding and no village-centroid substitution. A record without valid coordinates is shown as
  "Not mappable" in the table and counted separately.
- Tiles come from OpenStreetMap with the required attribution and normal usage. The tile URL is
  configurable so an approved provider can be swapped in.

## 7. Quality checks

Deterministic rules run in Java (`quality` package). They run at preview time, giving
validation feedback, and again after confirmation, creating persisted `quality_issue` rows. Every rule
has a stable `rule_id` and `rule_version`, and thresholds live in versioned configuration. Issues are
deduplicated with
`dedup_key = hash(rule_id, rule_version, record_id, field, observed_value)`. The rule list is in
`data-dictionary.md` §6.

ML/statistics (Module 13) is optional and advisory. It runs in the Python service, produces issues
with `source = 'STATISTICAL'` and a model version, and the core system ignores its absence.

## 8. Storage, limits and cleanup (defaults, all configurable)

| Setting | Default |
|---|---|
| Max upload size | 20 MB (`LANDSTACK_MAX_UPLOAD_BYTES`; multipart limit `LANDSTACK_MAX_UPLOAD_MB`) |
| Allowed types | `.csv` (text sniff, UTF-8/UTF-8-BOM/Windows-1252), `.xlsx` (ZIP signature + `[Content_Types].xml`), `.pdf` (`%PDF-` signature). `.xls` rejected in v1. |
| Max rows parsed | 10,000 per job (exceeding it fails the job; nothing is silently truncated); max columns 100; max cell length 4,000 chars |
| XLSX zip-bomb guard | POI `ZipSecureFile` min inflate ratio 0.01, max entry size 100 MB |
| Max PDF pages | 50; extraction timeout 60 s |
| Storage name | `{yyyy}/{mm}/{uuid}` (no extension, no user input in the path) |
| Original filename | sanitized and stored as metadata only |
| Staging rows (`import_row`) | deleted 7 days after job reaches a terminal state or is abandoned |
| Abandoned jobs | jobs that stay non-terminal for 7 days are marked `EXPIRED` and their staging is purged; jobs stuck in `PARSING` > 1 h (or at startup) become `FAILED` + retryable |
| Source files | kept while any confirmed record references them. An admin can delete demo imports, which archives the records and keeps the audit trail. |

## 9. Backend package layout

`edu.iitp.landstack` (base package): `config, security, common (errors, paging, time), auth, users, imports,
files, parsing, mapping (+ normalization), landrecords, geospatial, quality, audit, dashboard, exports,
processing (client for Python service)`.

Each feature uses Controller → Service → Repository, with DTO records at the API edge, Bean
Validation, `@Transactional` services, and a global `@RestControllerAdvice`.

## 10. Frontend layout

Uses the `src/features/*` structure from the pack. Server state goes through TanStack Query with one
axios client (`withCredentials`, CSRF header, error-envelope parsing). Route guards only improve
usability; the backend decides what is allowed.

## 11. Decisions taken (review with the guide)

| # | Question | Decision |
|---|---|---|
| Q1 | CSV/XLSX parsed in Java, PDF in Python service (§3) | Adopted |
| Q2 | Session cookies instead of JWT (§4) | Adopted |
| Q3 | Can operators see/act on imports uploaded by other operators? | Yes (shared team workspace); imports carry `uploaded_by` for audit. Change to "own only" if required. |
| Q4 | Reviewer sees owner name? | Masked (`R**** K****`) by default, configurable flag `landstack.privacy.mask-owner-for-reviewer`. |
| Q5 | Reviewer may export CSV? | No. |
| Q6 | Partial-import policy | Confirm selected valid rows in one all-or-nothing transaction; rows with blocking errors are excluded and stored as rejected diagnostics. |
| Q7 | OCR for scanned PDFs | Not in v1. Scanned PDFs get an explicit "OCR required — unsupported" status. |
| Q8 | `.xls` (legacy Excel) | Rejected in v1 with message "save as .xlsx". |
| Q9 | Duplicate upload (same SHA-256) | Allowed as a new job with a visible warning that links to the earlier job. The earlier file is never overwritten. |
| Q10 | Area normalized unit | Square metres. Universal units and uniform Indian units (cent, guntha, ankanam, ground) always convert. Regional units (bigha, biswa, katha, dhur, kanal, marla) convert **only** with a state factor from `normalization/area-units.json`; otherwise `UNIT_NOT_CONVERTIBLE`. Plain "bigha" in Rajasthan is deliberately ambiguous (pucca vs kaccha). |
| Q11 | Removal of demo imports | Administrator-only `DELETE /imports/{id}?confirm=true` → status `REMOVED`, records/issues deleted, audit retained. |
| Q12 | Statistical anomaly check (Module 13) | Deterministic log-IQR rule in Java (`STAT_AREA_IQR`, advisory, on demand). IsolationForest only in the offline evaluation script; adopted in-app only if the evaluation shows a clear gain (see `ml-evaluation.md`). |
| Q13 | Map clustering | Not used: canvas rendering + 5,000-point cap + filters (bounded loading). |
