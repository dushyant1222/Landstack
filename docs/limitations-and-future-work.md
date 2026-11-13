# Limitations and future work

## Limitations (current version)
* **Not compiled/tested in the authoring environment.** See `test-report.md`; run the suites before the demo.
* **OCR** is not implemented; scanned PDFs are rejected honestly. PDF support is limited to ruled tables and
  `Label: value` lines; other layouts fail with `PDF_NO_STRUCTURED_DATA`.
* **Regional area units** use customary state-level factors; actual sizes vary by district/village. Rajasthan plain
  "bigha" is not converted. Factors must be verified with revenue authorities before any official use.
* **Legacy `.xls`**, macro-enabled workbooks and UTF-16 CSV are not accepted. Merged Excel cells carry the value only
  in the first cell. Formula cells use the last saved value.
* **Dates**: only `yyyy-MM-dd`, `dd-MM-yyyy`, `dd/MM/yyyy`, `dd.MM.yyyy`; month-first and two-digit years are flagged.
* **Coordinates**: decimal degrees (optionally with N/S/E/W); DMS strings are flagged, not converted. Coordinates are
  assumed WGS84.
* **Geometry**: GeoJSON Point/Polygon/MultiPolygon only; no PostGIS spatial queries; map limited to 5,000 points per view.
* **Access model**: one organisation; all roles can read all records (owner names masked for reviewers).
* **Login throttling** is in memory (per backend instance).
* **Dashboard** import counts are not district-filtered.
* **Exports** are synchronous and limited to 10,000 rows.

## Future work
OCR path (Tesseract) with mandatory review · district-level unit factor tables · PostGIS for polygon queries and
viewport loading · marker clustering · scoped access (per district/project) · asynchronous exports · DB-level
append-only audit grants · i18n (Hindi UI) · approved government data integrations only if formally authorised.
