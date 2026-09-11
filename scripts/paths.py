"""Which Gradle project's resource tree a generator writes into (HytechCore vs HytechPlugin)."""

from __future__ import annotations

from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent

CORE = "HytechCore"
CONTENT = "HytechPlugin"


def resources(project: str) -> Path:
    return REPO_ROOT / project / "src" / "main" / "resources"
