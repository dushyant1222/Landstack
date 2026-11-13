# LandStack processing service (optional)

Python 3.12 + FastAPI service that extracts text, tables and `Label: value` candidates from **text-based**
PDFs for human review. It is optional: the LandStack backend parses CSV/XLSX itself and keeps working when
this service is down (PDF imports then fail with a retryable `PROCESSING_UNAVAILABLE` status).

* OCR is **not** implemented. Image-only pages are reported as `likelyScanned`; an all-scanned PDF returns
  status `LIKELY_SCANNED` and LandStack shows "OCR required — unsupported".
* Every extraction runs in a separate process that is killed after `PROCESSING_TIMEOUT_SECONDS`.
* Authentication: header `X-Service-Token` must equal `PROCESSING_TOKEN` (constant-time compare).
* No docs/OpenAPI endpoints are exposed; the service should only be reachable on the internal network.

## Run locally
```bash
cd processing-service
python3.12 -m venv .venv && . .venv/bin/activate
pip install -r requirements-dev.txt
export PROCESSING_TOKEN=$(python -c "import secrets;print(secrets.token_hex(24))")
uvicorn app.main:app --port 8090
```
Set the same value as `LANDSTACK_PROCESSING_TOKEN` for the backend.

## Test
```bash
pytest -q
```

## Contract (v1)
`POST /v1/pdf/extract` (multipart `file`, `maxPages`) → `{contractVersion, processor, pageCount, status, pages[], tables[], candidates[], warnings[]}`;
`status` ∈ `OK | LIKELY_SCANNED | ENCRYPTED | CORRUPT | TOO_MANY_PAGES`. See `docs/api-contract.md`.

## Module 13 evaluation (offline, optional)
```bash
pip install -r requirements-ml.txt
python ml/evaluate_area_anomaly.py --out ../docs/ml-evaluation-results.md
```
