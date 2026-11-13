"""Configuration from environment variables. The service refuses to start without a token."""
import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Settings:
    token: str
    max_bytes: int
    max_pages: int
    timeout_seconds: float
    max_candidates: int = 500
    max_tables: int = 200


def load_settings() -> Settings:
    token = os.environ.get("PROCESSING_TOKEN", "")
    if len(token) < 16:
        raise RuntimeError("PROCESSING_TOKEN must be set to a random secret of at least 16 characters")
    return Settings(
        token=token,
        max_bytes=int(os.environ.get("PROCESSING_MAX_BYTES", str(20 * 1024 * 1024))),
        max_pages=int(os.environ.get("PROCESSING_MAX_PAGES", "50")),
        timeout_seconds=float(os.environ.get("PROCESSING_TIMEOUT_SECONDS", "60")),
    )
