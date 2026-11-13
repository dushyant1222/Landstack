"""
Module 13 - offline evaluation of statistical vs ML anomaly assistance for land-area values.

Task (and what it is NOT): flag land areas that are unusual relative to comparable records (same
land-type category and district) so a human can check for data-entry problems such as unit mix-ups
or misplaced decimals. It does NOT detect fraud, invalid ownership, encumbrances or legal status.

Ground truth: there is no real labelled land-quality dataset, so this script builds a SYNTHETIC,
seeded dataset: "normal" areas are drawn from log-normal distributions per group, then a known
fraction of rows is corrupted with typical entry errors (acre entered as m^2, sq ft entered as m^2,
x10 / x100 / /10 decimal slips). Those injected rows are the positive labels. Metrics therefore
describe performance on this synthetic generator only and must not be reported as real-world accuracy.

Methods compared on the same held-out test split (group-stratified, no row appears in both splits):
  1. Baseline used in LandStack: Tukey fences on log10(area) per group, k = 3 (fit on train rows).
  2. IsolationForest (scikit-learn) on [log10(area), group mean log-area, group std] (fit on train rows,
     contamination='auto' - the label rate is NOT given to the model).

Usage:  pip install -r requirements-ml.txt && python ml/evaluate_area_anomaly.py [--out ../docs/ml-evaluation-results.md]
"""
from __future__ import annotations

import argparse
import math
import time
from dataclasses import dataclass

import numpy as np

SEED = 20261113
GROUPS = [("AGRICULTURAL", 4.0, 0.45), ("RESIDENTIAL", 2.4, 0.35), ("COMMERCIAL", 2.8, 0.40),
          ("BARREN", 4.3, 0.60), ("PASTURE", 4.5, 0.50)]
DISTRICTS = ["Patna", "Gaya", "Nalanda", "Bhojpur", "Muzaffarpur", "Darbhanga"]
ROWS_PER_GROUP = 120
ANOMALY_RATE = 0.05
ERRORS = {"acre_as_sqm": 1 / 4046.8564224, "sqft_as_sqm": 1 / 0.09290304, "x10": 10.0, "x100": 100.0, "div10": 0.1}
K = 3.0


@dataclass
class Dataset:
    group: np.ndarray
    area: np.ndarray
    label: np.ndarray
    error: np.ndarray


def generate(rng: np.random.Generator) -> Dataset:
    groups, areas, labels, errors = [], [], [], []
    for cat, mu, sigma in GROUPS:
        for d in DISTRICTS:
            g = f"{cat}|{d}"
            n = ROWS_PER_GROUP
            base = 10 ** rng.normal(mu + rng.normal(0, 0.15), sigma, n)
            lab = rng.random(n) < ANOMALY_RATE
            err = np.array([""] * n, dtype=object)
            names = list(ERRORS)
            for i in np.where(lab)[0]:
                e = names[rng.integers(len(names))]
                base[i] *= ERRORS[e]
                err[i] = e
            groups += [g] * n
            areas.append(base)
            labels.append(lab)
            errors.append(err)
    return Dataset(np.array(groups), np.concatenate(areas), np.concatenate(labels), np.concatenate(errors))


def split(ds: Dataset, rng: np.random.Generator, test_frac: float = 0.4):
    train = np.zeros(len(ds.area), bool)
    for g in np.unique(ds.group):
        idx = np.where(ds.group == g)[0]
        rng.shuffle(idx)
        cut = int(len(idx) * (1 - test_frac))
        train[idx[:cut]] = True
    return train, ~train


def iqr_predict(ds: Dataset, train: np.ndarray, test: np.ndarray) -> np.ndarray:
    pred = np.zeros(len(ds.area), bool)
    logs = np.log10(ds.area)
    for g in np.unique(ds.group):
        tr = train & (ds.group == g)
        te = test & (ds.group == g)
        q1, q3 = np.quantile(logs[tr], [0.25, 0.75])
        lo, hi = q1 - K * (q3 - q1), q3 + K * (q3 - q1)
        pred[te] = (logs[te] < lo) | (logs[te] > hi)
    return pred[test]


def iforest_predict(ds: Dataset, train: np.ndarray, test: np.ndarray, rng_seed: int) -> np.ndarray:
    from sklearn.ensemble import IsolationForest
    logs = np.log10(ds.area)
    feats = np.zeros((len(logs), 3))
    for g in np.unique(ds.group):
        tr = train & (ds.group == g)
        m, s = logs[tr].mean(), logs[tr].std() or 1.0
        rows = ds.group == g
        feats[rows, 0] = (logs[rows] - m) / s
        feats[rows, 1] = m
        feats[rows, 2] = s
    model = IsolationForest(n_estimators=300, contamination="auto", random_state=rng_seed)
    model.fit(feats[train])
    return model.predict(feats[test]) == -1


def metrics(y: np.ndarray, p: np.ndarray) -> dict[str, float]:
    tp = int((y & p).sum()); fp = int((~y & p).sum()); fn = int((y & ~p).sum()); tn = int((~y & ~p).sum())
    prec = tp / (tp + fp) if tp + fp else 0.0
    rec = tp / (tp + fn) if tp + fn else 0.0
    f1 = 2 * prec * rec / (prec + rec) if prec + rec else 0.0
    return {"tp": tp, "fp": fp, "fn": fn, "tn": tn, "precision": prec, "recall": rec, "f1": f1}


def per_error_recall(ds: Dataset, test: np.ndarray, pred: np.ndarray) -> dict[str, str]:
    out = {}
    err = ds.error[test]
    for e in ERRORS:
        mask = err == e
        out[e] = f"{int((pred & mask).sum())}/{int(mask.sum())}" if mask.sum() else "0/0"
    return out


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=None, help="write a Markdown report to this path")
    args = ap.parse_args()
    rng = np.random.default_rng(SEED)
    ds = generate(rng)
    train, test = split(ds, rng)
    y = ds.label[test]

    t0 = time.perf_counter(); p_iqr = iqr_predict(ds, train, test); t_iqr = time.perf_counter() - t0
    try:
        t0 = time.perf_counter(); p_if = iforest_predict(ds, train, test, SEED); t_if = time.perf_counter() - t0
    except ImportError:
        p_if, t_if = None, math.nan

    m_iqr = metrics(y, p_iqr)
    lines = [
        "# Module 13 - anomaly assistance evaluation (synthetic data)",
        "",
        f"Generated by `processing-service/ml/evaluate_area_anomaly.py` (seed {SEED}).",
        "**Synthetic generator only - not real-world accuracy.** Positive labels are injected entry errors.",
        "",
        f"* Rows: {len(ds.area)} in {len(np.unique(ds.group))} groups (land type x district); "
        f"train {int(train.sum())}, test {int(test.sum())} (group-stratified, disjoint).",
        f"* Test positives: {int(y.sum())} ({y.mean():.1%}); injected error types: {', '.join(ERRORS)}.",
        "",
        "| Method | Precision | Recall | F1 | TP | FP | FN | TN | Runtime (s) |",
        "|---|---|---|---|---|---|---|---|---|",
        f"| Log-IQR k={K} (LandStack baseline) | {m_iqr['precision']:.3f} | {m_iqr['recall']:.3f} | {m_iqr['f1']:.3f} | "
        f"{m_iqr['tp']} | {m_iqr['fp']} | {m_iqr['fn']} | {m_iqr['tn']} | {t_iqr:.3f} |",
    ]
    if p_if is not None:
        m_if = metrics(y, p_if)
        lines.append(f"| IsolationForest (auto contamination) | {m_if['precision']:.3f} | {m_if['recall']:.3f} | {m_if['f1']:.3f} | "
                     f"{m_if['tp']} | {m_if['fp']} | {m_if['fn']} | {m_if['tn']} | {t_if:.3f} |")
    else:
        lines.append("| IsolationForest | scikit-learn not installed | | | | | | | |")
    lines += ["", "Recall by injected error type (detected/total):", ""]
    lines.append("| Error | Log-IQR | IsolationForest |")
    lines.append("|---|---|---|")
    r_iqr = per_error_recall(ds, test, p_iqr)
    r_if = per_error_recall(ds, test, p_if) if p_if is not None else {e: "n/a" for e in ERRORS}
    for e in ERRORS:
        lines.append(f"| {e} | {r_iqr[e]} | {r_if[e]} |")
    lines += ["", "## Decision rule", "",
              "LandStack adopts the IsolationForest only if it improves F1 over the log-IQR baseline by more than 0.05 "
              "on this evaluation *and* its flags can be explained to a reviewer. Otherwise the in-app check stays the "
              "deterministic log-IQR rule (`STAT_AREA_IQR`), which is explainable (\"outside the typical range X-Y m^2 "
              "for N comparable records\") and needs no Python at runtime.",
              "", "## Limitations", "",
              "* Synthetic distributions; real land-area distributions may be multi-modal (e.g. mixed plot types).",
              "* Small groups (< 8 records) are skipped in the app; decimal slips of x10 within wide groups are often missed.",
              "* A flag is advisory: unusual is not wrong, and nothing is inferred about ownership or legality."]
    report = "\n".join(lines) + "\n"
    print(report)
    if args.out:
        with open(args.out, "w", encoding="utf-8") as fh:
            fh.write(report)


if __name__ == "__main__":
    main()
