"""
PDF extraction (contract version 1).

Honest by design:
  * nothing is inferred beyond what the PDF text contains;
  * pages with (almost) no extractable text are reported as likely scanned - OCR is NOT performed;
  * label/value candidates carry page number, method and a fixed heuristic confidence;
  * embedded JavaScript, links and attachments are never executed or followed.
"""
from __future__ import annotations

import io
import re
from typing import Any

import fitz  # PyMuPDF

try:  # pdfplumber is used for table detection; extraction still works (text only) without it
    import pdfplumber
except ImportError:  # pragma: no cover
    pdfplumber = None

CONTRACT_VERSION = "1"
SCANNED_CHAR_THRESHOLD = 20
LABEL_VALUE = re.compile(r"^\s*([A-Za-z][A-Za-z0-9 ./()&'\-]{1,48}?)\s*[:：]\s*(\S.{0,300}?)\s*$")
CANDIDATE_CONFIDENCE = 0.6  # fixed heuristic: label:value parsing is not verified extraction


def processor_name() -> str:
    plumber = getattr(pdfplumber, "__version__", "absent") if pdfplumber else "absent"
    return f"pymupdf-{fitz.VersionBind}+pdfplumber-{plumber}"


def _clean(cell: Any) -> str:
    if cell is None:
        return ""
    return re.sub(r"\s+", " ", str(cell)).strip()


def extract(data: bytes, max_pages: int, max_candidates: int = 500, max_tables: int = 200) -> dict[str, Any]:
    result: dict[str, Any] = {
        "contractVersion": CONTRACT_VERSION,
        "processor": processor_name(),
        "pageCount": 0,
        "status": "OK",
        "pages": [],
        "tables": [],
        "candidates": [],
        "warnings": [],
    }
    try:
        doc = fitz.open(stream=data, filetype="pdf")
    except Exception:  # noqa: BLE001 - any parser failure means the file is unusable
        result["status"] = "CORRUPT"
        return result
    try:
        if doc.needs_pass or doc.is_encrypted:
            result["status"] = "ENCRYPTED"
            return result
        result["pageCount"] = doc.page_count
        if doc.page_count == 0:
            result["status"] = "CORRUPT"
            return result
        if doc.page_count > max_pages:
            result["status"] = "TOO_MANY_PAGES"
            return result

        text_pages = 0
        for index in range(doc.page_count):
            page = doc.load_page(index)
            text = page.get_text("text") or ""
            chars = len(re.sub(r"\s", "", text))
            scanned = chars < SCANNED_CHAR_THRESHOLD
            result["pages"].append({"page": index + 1, "charCount": chars, "likelyScanned": scanned, "text": text})
            if scanned:
                continue
            text_pages += 1
            for line in text.splitlines():
                m = LABEL_VALUE.match(line)
                if m and len(result["candidates"]) < max_candidates:
                    result["candidates"].append({
                        "label": _clean(m.group(1)),
                        "value": _clean(m.group(2)),
                        "page": index + 1,
                        "method": "regex-label",
                        "confidence": CANDIDATE_CONFIDENCE,
                    })
        if text_pages == 0:
            result["status"] = "LIKELY_SCANNED"
            result["candidates"] = []
            return result
        if len(result["candidates"]) >= max_candidates:
            result["warnings"].append(f"Only the first {max_candidates} label/value candidates were kept.")
    finally:
        doc.close()

    if pdfplumber is None:
        result["warnings"].append("Table detection unavailable (pdfplumber not installed); only label/value text was extracted.")
        return result
    try:
        with pdfplumber.open(io.BytesIO(data)) as pdf:
            for pno, page in enumerate(pdf.pages, start=1):
                if result["pages"][pno - 1]["likelyScanned"]:
                    continue
                for tindex, table in enumerate(page.extract_tables() or []):
                    rows = [[_clean(c) for c in row] for row in table if row and any(_clean(c) for c in row)]
                    if len(rows) < 2:
                        continue
                    if len(result["tables"]) >= max_tables:
                        result["warnings"].append(f"Only the first {max_tables} tables were kept.")
                        return result
                    header, body = rows[0], rows[1:]
                    width = len(header)
                    body = [(r + [""] * width)[:width] for r in body]
                    result["tables"].append({"page": pno, "index": tindex, "header": header, "rows": body})
    except Exception:  # noqa: BLE001
        result["warnings"].append("Table detection failed on this document; label/value text is still available.")
    return result


def worker(data: bytes, max_pages: int, max_candidates: int, max_tables: int, out) -> None:
    """Child-process entry point (kept free of web-framework imports)."""
    try:
        out.put(("ok", extract(data, max_pages, max_candidates, max_tables)))
    except Exception as exc:  # noqa: BLE001
        out.put(("error", type(exc).__name__))
