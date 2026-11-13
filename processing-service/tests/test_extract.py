"""Extraction tests use PDFs generated on the fly with PyMuPDF (synthetic content only)."""
import fitz

from app.pdf_extract import extract


def _pdf(pages: list[str]) -> bytes:
    doc = fitz.open()
    for text in pages:
        page = doc.new_page()
        if text:
            page.insert_text((72, 72), text, fontsize=11)
    data = doc.tobytes()
    doc.close()
    return data


def _table_pdf() -> bytes:
    doc = fitz.open()
    page = doc.new_page()
    rows = [["Khasra No", "Area", "Unit"], ["0012", "1.5", "acre"], ["0013", "2", "bigha"]]
    x0, y0, cw, rh = 72, 100, 120, 24
    for r, row in enumerate(rows):
        for c, val in enumerate(row):
            rect = fitz.Rect(x0 + c * cw, y0 + r * rh, x0 + (c + 1) * cw, y0 + (r + 1) * rh)
            page.draw_rect(rect, color=(0, 0, 0), width=0.8)
            page.insert_text((rect.x0 + 4, rect.y0 + 16), val, fontsize=10)
    data = doc.tobytes()
    doc.close()
    return data


def test_text_pdf_label_values_keep_page_reference():
    r = extract(_pdf(["Khasra No: 0012\nVillage: Rampur\nArea: 1.5 acre", "Khasra No: 0013\nVillage: Sitapur"]), 10)
    assert r["status"] == "OK"
    assert r["pageCount"] == 2
    labels = [(c["label"], c["value"], c["page"]) for c in r["candidates"]]
    assert ("Khasra No", "0012", 1) in labels
    assert ("Khasra No", "0013", 2) in labels
    assert all(c["confidence"] < 1 for c in r["candidates"])


def test_table_pdf_produces_header_and_rows():
    r = extract(_table_pdf(), 10)
    assert r["status"] == "OK"
    assert r["tables"], "expected a ruled table to be detected"
    t = r["tables"][0]
    assert t["header"] == ["Khasra No", "Area", "Unit"]
    assert ["0012", "1.5", "acre"] in t["rows"]
    assert t["page"] == 1


def test_image_only_pdf_is_reported_as_scanned_not_parsed():
    r = extract(_pdf(["", ""]), 10)
    assert r["status"] == "LIKELY_SCANNED"
    assert r["candidates"] == []
    assert r["tables"] == []


def test_corrupt_pdf():
    assert extract(b"%PDF-1.4 garbage", 10)["status"] == "CORRUPT"


def test_encrypted_pdf():
    doc = fitz.open()
    doc.new_page().insert_text((72, 72), "Secret: 1")
    data = doc.tobytes(encryption=fitz.PDF_ENCRYPT_AES_256, owner_pw="owner-pw", user_pw="user-pw")
    doc.close()
    assert extract(data, 10)["status"] == "ENCRYPTED"


def test_page_limit_is_enforced():
    assert extract(_pdf(["a: 1"] * 5), 3)["status"] == "TOO_MANY_PAGES"


def test_no_fabrication_on_plain_prose():
    r = extract(_pdf(["This page describes the survey process in general terms without any fields."]), 10)
    assert r["status"] == "OK"
    assert r["candidates"] == []
    assert r["tables"] == []
