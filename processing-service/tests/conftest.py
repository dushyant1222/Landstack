import os
import sys
from pathlib import Path

os.environ.setdefault("PROCESSING_TOKEN", "test-token-0123456789")
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
