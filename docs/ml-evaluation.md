# Module 13 — Optional statistical / ML anomaly assistance

## Task definition
Highlight land areas that are unusual relative to comparable records (same land-type category and district)
so that a person checks for data-entry problems such as a unit mix-up or a misplaced decimal point.
It does **not** detect fraud, invalid ownership, encumbrances or legal status, and it never changes a record.

## What is in the application
* `STAT_AREA_IQR` (`StatisticalAnomalyService`, Java, deterministic): Tukey fences on log10(area m²) per group
  (land-type category × district), `k = 3`, groups with fewer than 8 records are skipped. Triggered on demand
  (*Data quality → Run statistical check*). Issues have `source = STATISTICAL`, severity INFO, version
  `stat-iqr-1.0(k=3.0,log10,min=8)` and a plain-language reason (typical range X–Y m² for N records).
* No Python is needed at runtime; the rule works when the processing service is down.

## Evaluation (offline)
`processing-service/ml/evaluate_area_anomaly.py` compares the in-app baseline with scikit-learn's
IsolationForest on a **synthetic, seeded** dataset (5 land types × 6 districts × 120 rows = 3,600 rows),
in which 5% of rows are corrupted with known entry errors (acre entered as m², sq ft as m², ×10, ×100, ÷10).
Group-stratified 60/40 train/test split; both methods are fitted on train rows only; IsolationForest uses
`contamination='auto'` so it is not told the label rate. Reported: precision, recall, F1, confusion counts,
per-error recall and runtime.

```bash
pip install -r processing-service/requirements-ml.txt
python processing-service/ml/evaluate_area_anomaly.py --out docs/ml-evaluation-results.md
```
**Results:** not yet generated in the build environment (numpy/scikit-learn could not be installed). Run the
command above; it writes `docs/ml-evaluation-results.md`. No performance figures are claimed until then.

## Adoption rule
IsolationForest would be added to the application only if it improves F1 over the log-IQR baseline by more than
0.05 on this evaluation **and** its flags can be explained to a reviewer. Otherwise the feature remains the
deterministic rule above.

## Limitations & responsible use
Synthetic labels describe the generator, not real-world accuracy. Real distributions may be multi-modal.
Small groups are skipped. ×10 slips inside wide groups are often missed. Flags are advisory and subject to
human review.
