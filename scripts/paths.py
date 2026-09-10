"""Where each mod's resources live.

The build is two Gradle projects -- `HytechCore`, the logistics library, and `HytechPlugin`, the
content built on it -- so a generator has to say which tree it writes into. It used to be the one
`src/main/resources` at the repo root.

A generator writes into the project it belongs to, so it names one of these rather than taking a
path. The exception is check-asset-refs.py, which checks every root at once and takes `--resources`
for the cases where you want one on its own.
"""

from __future__ import annotations

from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent

#: The logistics library: pipe geometry, hitboxes, face overlays, the UI documents, the wrench.
CORE = "HytechCore"

#: Hytech itself: pipes, blocks, machines, materials, recipes and icons.
CONTENT = "HytechPlugin"


def resources(project: str) -> Path:
    """The resource root of one mod project."""
    return REPO_ROOT / project / "src" / "main" / "resources"
