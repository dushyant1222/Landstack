# LandStack — Import Workflow & State Machine (as implemented)

## 1. States

| State | Meaning | Terminal |
|---|---|:-:|
| RECEIVED | Bytes stored, SourceFile + ImportJob rows created | |
| VALIDATING | Signature/size/readability checks | |
| PARSING | CSV/XLSX parse (Java) or PDF extraction (Python) running | |
| AWAITING_SHEET_SELECTION | XLSX with >1 sheet, or uncertain header row | |
| AWAITING_MAPPING | Preview rows staged; operator must map columns | |
| PREVIEW_READY | Mapping applied, rows normalized and validated; operator reviewing/correcting | |
| CONFIRMING | Confirmation transaction running | |
| COMPLETED | All selected rows persisted, no warnings | ✔ |
| COMPLETED_WITH_WARNINGS | Persisted; some rows excluded or flagged | ✔ |
| FAILED | Validation/parse/confirm failed; `status_message` safe & actionable; `retryable` flag | ✔* |
| EXPIRED | Abandoned before confirmation (7 days); staging purged | ✔ |
| CANCELLED | Operator discarded the import before confirmation | ✔ |
| REMOVED | Administrator removed a (demo) import: its records and issues are deleted, the job row and audit trail remain | ✔ |

\* A retryable FAILED job (for example, processing service unavailable) may go back to PARSING through `POST
/imports/{id}/retry`. A FAILED job that was never confirmed has no land records.

`AWAITING_CONFIRMATION` from the pack is merged into PREVIEW_READY: the operator always reviews the
preview and confirms from the same state, so a separate state would add no information.

## 2. Transitions

```
RECEIVED → VALIDATING → PARSING → AWAITING_SHEET_SELECTION ⇄ (select sheet/header) → PARSING
                 │           │                                       
                 ▼           ├──────────────→ AWAITING_MAPPING → PREVIEW_READY ⇄ (remap/correct)
               FAILED      FAILED(retryable)                         │
                                                                     ▼  explicit confirm
                                                                CONFIRMING → COMPLETED | COMPLETED_WITH_WARNINGS
                                                                     └──→ PREVIEW_READY (tx rolled back; error shown)
any non-terminal → CANCELLED (operator) | EXPIRED (scheduler)
COMPLETED* | FAILED | CANCELLED | EXPIRED → REMOVED (administrator)
```

`ImportStateMachine` (Module 4) owns transitions. Illegal transitions throw `409 IMPORT_STATE_CONFLICT`.
`import_job.version` (optimistic lock) prevents two operators from racing on the same job.

## 3. Pipeline stages

1. **Upload** `POST /api/v1/imports` (ADMIN/OPERATOR). Multipart, size checked before buffering.
2. **Validate**: the extension must be in the allow-list and the magic bytes must match it. CSV must
   decode as text, XLSX must open as a zip with `[Content_Types].xml`, and PDF must start with `%PDF-`
   and must not be encrypted.
3. **Checksum + metadata**: SHA-256 is computed while streaming. A duplicate checksum adds a warning
   and `duplicate_of_job_id`.
4. **Job** created (RECEIVED → VALIDATING).
5. **Parse/extract** creates a `processing_attempt`. Rows are staged in `import_row.raw_values` exactly
   as read: strings, leading zeros kept, cached formula results only.
6. **Preview + diagnostics** are returned paginated, with row/column warnings.
7. **Sheet/header selection** (XLSX) starts a new attempt. Older attempts' rows are superseded.
8. **Mapping**: the operator maps canonical fields to source columns. Alias suggestions are flagged
   `suggested=true` and do nothing until the operator saves them.
9. **Normalize** using deterministic `norm-1.0` rules (data-dictionary §4–5). The same input and
   mapping always give the same output.
10. **Validate** with the preview-stage quality rules. Each row ends up VALID, WARNING or BLOCKED.
11. **Review**: raw and normalized values side by side. The operator can correct values (stored in
    `corrected_values`, which re-normalizes the row) and exclude rows.
12. **Confirm** `POST /imports/{id}/confirm` with body `{confirmationKey, rowSelection}` and the
    `Idempotency-Key` header. BLOCKED rows are rejected.
13. **Persist** in **one transaction** (`ConfirmationService.confirm`, pessimistic lock on the job): land_record rows, source values, the import_mapping snapshot,
    post-confirm quality issues, final counts and the job status. If any statement fails, the whole
    transaction rolls back. The job then returns to PREVIEW_READY with a safe error message, and no
    records exist.
14. **Finalize**: the status is set from actual inserted row counts, never from the planned counts.
15. **Audit** events: IMPORT_UPLOADED, IMPORT_PARSED, MAPPING_SAVED, ROW_CORRECTED, IMPORT_CONFIRMED,
    IMPORT_FAILED and IMPORT_CANCELLED.

**Records become confirmed `land_record` rows only at step 13.** Earlier stages write only staging data.

## 4. Idempotency

- `import_job.confirmation_key` is UNIQUE. A repeat confirm with the same key returns the stored
  result (`200`, same body) and inserts nothing.
- A confirm with a different key on a job that is already COMPLETED returns `409 IMPORT_ALREADY_CONFIRMED`.
- `UNIQUE(import_job_id, source_row_number)` on `land_record` blocks duplicates at the database level.

## 5. PDF path specifics

The Python service receives the file bytes and returns, per page, the text blocks, the tables, and
candidate `label: value` pairs with page number, method and confidence. If a page has fewer than 20
extractable characters, it is marked `LIKELY_SCANNED`. If every page is like that, the job FAILS with
`PDF_OCR_REQUIRED` (not retryable), because OCR is not in v1. Each extracted table becomes staged rows,
and key/value candidates become a single-row preview the operator can edit. All PDF-derived values
carry `EXTRACTION_LOW_CONFIDENCE` until the operator confirms or corrects them.

If the service is unreachable or times out, the job fails with `PROCESSING_UNAVAILABLE` and
`retryable=true`. CSV/XLSX imports are unaffected.
