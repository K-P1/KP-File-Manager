"""desktop/src/kp_file_manager/paths.py: Central desktop paths; created for the repository reorganization."""

from pathlib import Path


DESKTOP_ROOT = Path(__file__).resolve().parents[2]
CONFIG_FILE = DESKTOP_ROOT / "config" / "categories.json"
RUNTIME_DIR = DESKTOP_ROOT / "runtime"
LOG_FILE = RUNTIME_DIR / "operation_logs.txt"


def ensure_runtime_dir() -> Path:
    """Return the runtime directory after ensuring it exists."""
    RUNTIME_DIR.mkdir(parents=True, exist_ok=True)
    return RUNTIME_DIR
