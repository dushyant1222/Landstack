# LandStack — User Guide

> Screenshots: _[placeholder — capture real screenshots of each page after running the demo; do not use mockups]_

## Roles
| Role | Can |
|---|---|
| Administrator | everything below + user management, removal of demo imports, full audit trail |
| Data operator | upload, map, review, correct, confirm, correct records, review quality issues, export, own audit events |
| Read-only reviewer | search and view records (owner names masked), map, quality issues, import history; no changes |

## Signing in
Accounts are created by an administrator. Sessions end after 30 minutes of inactivity, on sign-out, or when
an administrator deactivates the account or changes its role. Change your password under *Profile & settings*.

## Importing a dataset (operators)
1. **Import datasets** → drag a `.csv`, `.xlsx` or text-based `.pdf` (≤ 20 MB) → *Upload*. The progress bar
   shows the real upload; the job then appears with its status.
2. **Worksheet** (Excel with several sheets): choose the sheet; optionally a header row number.
3. **Source preview**: rows exactly as read, with diagnostics (encoding, delimiter, duplicate headers, blank
   rows…). If the header is wrong, enter the correct row number and *Use as header*.
4. **Field mapping**: for each LandStack field choose a source column. Amber "Suggested · not confirmed"
   items come from known column names — check them. Options: *dates are day-first*, *require identifier*,
   *require district*, a unit for rows without one, a state for the whole file (needed for bigha/katha/kanal
   conversion when rows have no state). Save as a template to reuse for the same source format.
5. **Review & confirm**: counts of valid / warning / blocked / excluded rows; *Inspect* shows raw vs normalized
   values and the transformation applied to every field. Correct values (by source column), exclude rows, or
   mark PDF rows reviewed. Blocked rows are never imported. *Confirm import…* saves the selected rows in one
   transaction; if anything fails, nothing is saved and you can try again.
6. **Result**: imported and rejected counts, rejected row numbers with reasons, links to the records and to
   the quality issues raised.

PDFs: only text-based documents with ruled tables or `Label: value` lines are supported. Scanned PDFs are
reported as "OCR required — unsupported". Every PDF value is machine-extracted and must be reviewed.

## Land records
Search by parcel ID/ULPIN, khasra/survey number, locality, tehsil, district or registration reference; filter
by district, state, land type, mappable, open issues and status. A record shows normalized values, its
provenance (file, row, page, import, mapping, normalization version), every source value and transformation,
and its quality issues. Operators can *Correct* (with a reason; lineage cannot change) or *Archive*.

## Map view
Only records with valid coordinates from their own source are drawn, coloured by land type. The counts show
how many matching records are mappable and not mappable; *Non-mappable records* opens them in the table.
Points are supplied coordinates, not surveyed boundaries.

## Data quality
Issues are potential problems for review, never legal findings. Filter by status, severity, category or source;
open an issue to see the reason, the observed value, the record and any possible duplicates. Operators can
mark reviewed, resolve or dismiss (a note is required for resolve/dismiss) or reopen. *Re-run rules* re-checks
all records without creating duplicate flags; *Run statistical check* adds advisory outlier flags.

## Export
*Land records → Export CSV* downloads the currently filtered records (≤ 10,000). Cells that could be read as
spreadsheet formulas are prefixed with `'`.
