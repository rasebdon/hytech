import json
import itertools
from pathlib import Path

# ---------------- CONFIG ----------------
BASE_MODEL_PATH = Path("Example_Cable_Base.blockymodel")

OUTPUT_MODEL_DIR = Path("Generated")
STATE_OUTPUT_PATH = Path("Example_Cable_State.json")

BASE_NAME = "Example_Cable"
STATE_PREFIX = "Cable"

DIRECTIONS = ["Up", "Down", "North", "South", "East", "West"]

MODEL_PATH_PREFIX = "Blocks/Example_Cable/Generated"
BASE_MODEL_STATE_PATH = "Blocks/Example_Cable/Example_Cable.blockymodel"
# ----------------------------------------

OUTPUT_MODEL_DIR.mkdir(exist_ok=True)

# Load base model
with BASE_MODEL_PATH.open("r", encoding="utf-8") as f:
    base_model = json.load(f)

# Index nodes
nodes_by_name = {node["name"]: node for node in base_model["nodes"]}

center_node = nodes_by_name["Center"]
direction_nodes = {d: nodes_by_name[d] for d in DIRECTIONS}

# Prepare State JSON structure
state_json = {
    "State": {
        "Definitions": {
            STATE_PREFIX: {
                "CustomModel": BASE_MODEL_STATE_PATH
            }
        }
    }
}

# Generate permutations
for mask in itertools.product([False, True], repeat=len(DIRECTIONS)):
    enabled_dirs = [DIRECTIONS[i] for i, enabled in enumerate(mask) if enabled]

    # Skip center-only variant
    if not enabled_dirs:
        continue

    # Build blockymodel
    new_model = {
        "nodes": [center_node] + [direction_nodes[d] for d in enabled_dirs],
        "format": base_model.get("format", "prop"),
        "lod": base_model.get("lod", "auto")
    }

    suffix = "_".join(enabled_dirs)
    model_filename = f"{BASE_NAME}_{suffix}.blockymodel"
    model_path = OUTPUT_MODEL_DIR / model_filename

    with model_path.open("w", encoding="utf-8") as f:
        json.dump(new_model, f, indent=2)

    # Add State definition
    state_key = f"{STATE_PREFIX}_{suffix}"
    state_json["State"]["Definitions"][state_key] = {
        "CustomModel": f"{MODEL_PATH_PREFIX}/{model_filename}"
    }

# Write State JSON
with STATE_OUTPUT_PATH.open("w", encoding="utf-8") as f:
    json.dump(state_json, f, indent=2)

print("✔ Cable models and state file generated successfully.")
