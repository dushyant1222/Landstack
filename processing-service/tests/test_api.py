import fitz
from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)
TOKEN = {"X-Service-Token": "test-token-0123456789"}


def _pdf() -> bytes:
    doc = fitz.open()
    doc.new_page().insert_text((72, 72), "Khasra No: 0012")
    data = doc.tobytes()
    doc.close()
    return data


def test_health():
    assert client.get("/v1/health").json()["status"] == "UP"


def test_token_required():
    r = client.post("/v1/pdf/extract", files={"file": ("d.pdf", _pdf(), "application/pdf")})
    assert r.status_code == 401
    r = client.post("/v1/pdf/extract", files={"file": ("d.pdf", _pdf(), "application/pdf")}, headers={"X-Service-Token": "wrong"})
    assert r.status_code == 401


def test_extract_contract():
    r = client.post("/v1/pdf/extract", files={"file": ("d.pdf", _pdf(), "application/pdf")}, data={"maxPages": "5"}, headers=TOKEN)
    assert r.status_code == 200
    body = r.json()
    assert body["contractVersion"] == "1"
    assert set(body) >= {"processor", "pageCount", "status", "pages", "tables", "candidates", "warnings"}
    assert body["candidates"][0]["value"] == "0012"


def test_non_pdf_rejected():
    r = client.post("/v1/pdf/extract", files={"file": ("d.pdf", b"hello", "application/pdf")}, headers=TOKEN)
    assert r.status_code == 422


def test_docs_not_exposed():
    assert client.get("/docs").status_code == 404
