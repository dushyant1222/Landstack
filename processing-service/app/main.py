"""
FastAPI entry point. Internal service: called only by the LandStack backend with X-Service-Token.
Each extraction runs in a separate process that is terminated on timeout, isolating parser crashes,
hangs and memory blow-ups from the service itself.
"""
from __future__ import annotations

import hmac
import multiprocessing as mp
import queue
from typing import Any

from fastapi import FastAPI, File, Form, Header, HTTPException, UploadFile

from .config import load_settings
from .pdf_extract import processor_name, worker

settings = load_settings()
app = FastAPI(title="LandStack processing service", version="1.0.0", docs_url=None, redoc_url=None, openapi_url=None)


def _check_token(token: str | None) -> None:
    if not token or not hmac.compare_digest(token.encode(), settings.token.encode()):
        raise HTTPException(status_code=401, detail="invalid service token")


def run_isolated(data: bytes, max_pages: int, timeout: float) -> dict[str, Any]:
    # "spawn" gives the parser a clean process (no inherited threads/locks); it is killed on timeout.
    ctx = mp.get_context("spawn")
    out: "mp.Queue[Any]" = ctx.Queue()
    proc = ctx.Process(target=worker, args=(data, max_pages, settings.max_candidates, settings.max_tables, out), daemon=True)
    proc.start()
    try:
        kind, payload = out.get(timeout=timeout)
    except queue.Empty:
        proc.terminate()
        proc.join(5)
        raise HTTPException(status_code=504, detail="extraction timed out")
    finally:
        if proc.is_alive():
            proc.join(1)
            if proc.is_alive():
                proc.terminate()
    if kind != "ok":
        raise HTTPException(status_code=500, detail="extraction failed")
    return payload


@app.get("/v1/health")
def health() -> dict[str, Any]:
    return {"status": "UP", "contractVersion": "1", "processor": processor_name()}


@app.post("/v1/pdf/extract")
async def pdf_extract(file: UploadFile = File(...), maxPages: int = Form(50),
                      x_service_token: str | None = Header(default=None)) -> dict[str, Any]:
    _check_token(x_service_token)
    data = await file.read(settings.max_bytes + 1)
    if len(data) > settings.max_bytes:
        raise HTTPException(status_code=413, detail="file too large")
    if b"%PDF-" not in data[:1024]:
        raise HTTPException(status_code=422, detail="not a PDF")
    pages = max(1, min(maxPages, settings.max_pages))
    return run_isolated(data, pages, settings.timeout_seconds)
