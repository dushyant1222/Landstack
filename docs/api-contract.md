# LandStack — API Contract (v1, as implemented)

Base path `/api/v1`. JSON except upload (`multipart/form-data`, part `file`) and export (`text/csv`).
Authentication: `LANDSTACK_SESSION` cookie (HttpOnly, SameSite=Lax, Secure when `LANDSTACK_COOKIE_SECURE=true`).
CSRF: every POST/PUT/PATCH/DELETE (including login) needs header `X-XSRF-TOKEN` equal to the `XSRF-TOKEN`
cookie; obtain it with `GET /auth/csrf`. The generated OpenAPI document (`/v3/api-docs`, Swagger UI
`/swagger-ui.html`) is public only in the `dev` profile and administrator-only otherwise.

## Error envelope
```json
{ "timestamp": "2026-11-13T10:15:30Z", "status": 422, "code": "VALIDATION_FAILED",
  "message": "One or more fields are invalid.", "path": "/api/v1/users",
  "fieldErrors": [{"field": "email", "message": "must be a well-formed email address"}], "traceId": "8f3c1a2b" }
```
Never contains stack traces, SQL, storage paths or parser internals. Codes: `AUTH_REQUIRED 401`,
`INVALID_CREDENTIALS 401`, `ACCESS_DENIED 403`, `NOT_FOUND 404`, `BAD_REQUEST 400`, `VALIDATION_FAILED 422`,
`CONFLICT 409`, `VERSION_CONFLICT 409`, `IMPORT_STATE_CONFLICT 409`, `IMPORT_ALREADY_CONFIRMED 409`,
`CONFIRMATION_FAILED 409`, `FILE_TOO_LARGE 413`, `UNSUPPORTED_FILE_TYPE 415`, `FILE_SIGNATURE_MISMATCH 415`,
`FILE_UNREADABLE 422`, `EXPORT_TOO_LARGE 422`, `RATE_LIMITED 429`, `PROCESSING_UNAVAILABLE 503`, `INTERNAL_ERROR 500`.

## Pagination & sorting
`?page=0&size=25&sort=field,asc|desc` (size 1–100). Sort fields are whitelisted per endpoint; others → 422.
Response `{content, page, size, totalElements, totalPages}`.

## Endpoints
| Method & path | Roles | Notes |
|---|---|---|
| `GET /auth/csrf` | public | `{headerName, token}` + cookie |
| `POST /auth/login` `{email,password}` | public | `UserDto`; generic 401 message; 429 after 5 failures / 15 min per account |
| `POST /auth/logout` | any | 204, session deleted |
| `GET /auth/me` | any | `UserDto` (never the hash) |
| `POST /auth/password` `{currentPassword,newPassword}` | any | revokes all own sessions |
| `GET /users?q&role&active&page&size&sort` | ADMIN | |
| `POST /users` `{email,displayName,password,role}` | ADMIN | 201 |
| `GET/PATCH /users/{id}` `{displayName}` | ADMIN | unknown fields ignored (explicit DTO) |
| `PUT /users/{id}/role` `{role}` | ADMIN | not own role; not the last admin; sessions revoked |
| `POST /users/{id}/activate`, `/deactivate` | ADMIN | deactivation revokes sessions immediately |
| `POST /users/{id}/password` `{newPassword}` | ADMIN | |
| `POST /imports` (multipart `file`) | ADMIN, OPERATOR | 202 `ImportJobDto` (status PARSING); parsing continues asynchronously |
| `GET /imports?status&createdBy&q&from&to&page&size&sort` | all | |
| `GET /imports/{id}`, `GET /imports/{id}/timeline` | all | poll while status is RECEIVED/VALIDATING/PARSING/CONFIRMING |
| `POST /imports/{id}/sheet-selection` `{sheetName?, headerRow?}` | ADMIN, OPERATOR | re-parses (also for CSV header row) |
| `POST /imports/{id}/cancel` | ADMIN, OPERATOR | |
| `POST /imports/{id}/retry` | ADMIN, OPERATOR | only FAILED + retryable |
| `DELETE /imports/{id}?confirm=true` | ADMIN | terminal jobs only → REMOVED |
| `GET /imports/{id}/rows?state=VALID,WARNING,BLOCKED,EXCLUDED&page&size` | ADMIN, OPERATOR | staged rows: raw, corrected, normalized traces, validation |
| `GET /imports/{id}/diagnostics` | ADMIN, OPERATOR | processing attempts (newest first) |
| `GET /imports/{id}/pdf-text` | ADMIN, OPERATOR | per-page extracted text |
| `GET /canonical-fields` | all | |
| `GET /imports/{id}/mapping/suggestions` | ADMIN, OPERATOR | columns, sample values, SUGGESTED/AMBIGUOUS/NONE per field, current mapping |
| `PUT /imports/{id}/mapping` `MappingRequest` | ADMIN, OPERATOR | normalizes + validates every row → `PreviewSummary`; status PREVIEW_READY |
| `GET /imports/{id}/preview-summary` | ADMIN, OPERATOR | |
| `PATCH /imports/{id}/rows/{rowId}` `{values?, excluded?, reviewed?}` | ADMIN, OPERATOR | corrections keyed by source column |
| `GET/POST /mapping-templates`, `GET/DELETE /mapping-templates/{id}` | ADMIN, OPERATOR | same name → new version |
| `POST /imports/{id}/confirm` `{confirmationKey, includeWarningRows}` + header `Idempotency-Key` | ADMIN, OPERATOR | `ConfirmResult`; same key → replay (`replayed:true`); other key after success → 409 |
| `GET /source-files/{id}` | all | metadata only |
| `GET /source-files/{id}/content` | ADMIN, OPERATOR | attachment, `nosniff`, `no-store` |
| `GET /land-records?q&parcelId&surveyKhasraNo&village&tehsil&district&state&landType&importJobId&sourceFileId&status&mappable&hasOpenIssues&minLat&minLon&maxLat&maxLon&page&size&sort` | all | reviewers: owner masked, `q` does not search owners |
| `GET /land-records/facets` | all | distinct districts, states, land-type categories |
| `GET /land-records/{id}` | all | record + provenance + source values + issues |
| `PATCH /land-records/{id}` `{version, reason, changes:{canonical_key: value}}` | ADMIN, OPERATOR | lineage keys rejected; stale version → 409 |
| `POST /land-records/{id}/archive` `{reason}`, `/restore` | ADMIN, OPERATOR | |
| `GET /map/points?<same filters>&includeGeometry` | all | `{mappableCount, notMappableCount, returned, truncated, points[]}` ≤ 5,000, no owner names |
| `GET /quality/issues?status&severity&ruleId&category&district&importJobId&landRecordId&source&page&size&sort` | all | |
| `GET /quality/issues/{id}` | all | issue + related (duplicate) record summaries |
| `POST /quality/issues/{id}/review` `{status, note}` | ADMIN, OPERATOR | note required for RESOLVED/DISMISSED |
| `POST /quality/runs` `{importJobId?}` | ADMIN, OPERATOR | re-evaluate rules (dedup → no duplicate flags) |
| `POST /quality/statistical-runs` | ADMIN, OPERATOR | advisory log-IQR check |
| `GET /quality/rules` | all | rule catalogue |
| `GET /dashboard/summary?from&to&district` | all | review activity only for ADMIN/OPERATOR |
| `GET /system/processing-status` | ADMIN, OPERATOR | `{status: UP|DOWN|NOT_CONFIGURED}` |
| `GET /audit-events?action&targetType&targetId&actorId&from&to&page&size` | ADMIN (all), OPERATOR (own) | |
| `GET /exports/land-records.csv?<same filters>` | ADMIN, OPERATOR | ≤ 10,000 rows else 422; formula-escaped; UTF-8 BOM |
| `GET /actuator/health` | public | no details unless admin |

A resource the caller may not access is reported as 403 (role) or 404 (missing). All records are visible to
all roles in this single-organisation deployment (decision Q3); data minimisation is applied to owner names.

## Dashboard metric definitions
`confirmedRecords` = `land_record.record_status='CONFIRMED'`; `mappableRecords` = confirmed with both valid
coordinates; `openIssues` = `quality_issue.status='OPEN'`; `recordsAddedPerDay` = confirmed-or-archived records by
`created_at` (UTC date) in `[from, to]` (default last 30 days, max 366); district filter applies to record and
issue metrics, not to import counts.

## Processing-service contract (internal, v1)
`POST {LANDSTACK_PROCESSING_URL}/v1/pdf/extract` · header `X-Service-Token` · multipart `file`, `maxPages`.
Response:
```json
{ "contractVersion": "1", "processor": "pymupdf-1.24.14+pdfplumber-0.11.4", "pageCount": 2, "status": "OK",
  "pages": [{"page": 1, "charCount": 812, "likelyScanned": false, "text": "..."}],
  "tables": [{"page": 1, "index": 0, "header": ["Khasra No","Area"], "rows": [["0012","1.5"]]}],
  "candidates": [{"label": "Khasra No", "value": "0012", "page": 1, "method": "regex-label", "confidence": 0.6}],
  "warnings": [] }
```
`status` ∈ `OK | LIKELY_SCANNED | ENCRYPTED | CORRUPT | TOO_MANY_PAGES`. Errors: 401 bad token, 413 too large,
422 not a PDF, 504 timeout. `GET /v1/health` → `{status:"UP"}`. Backend timeouts: connect 5 s, read 70 s;
unreachable/timeout → job FAILED `PROCESSING_UNAVAILABLE`/`PROCESSING_TIMEOUT`, `retryable=true`.
