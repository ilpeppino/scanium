#!/usr/bin/env python3
"""
Extract Scanium subtypes from home_resale_domain_pack.json
Output: scripts/output/ebay/scanium_subtypes_all.json
"""

import json
import sys
from pathlib import Path

def extract_subtypes():
    """Extract subtypes from domain pack JSON."""
    repo_root = Path(__file__).parent.parent.parent
    domain_pack_path = repo_root / "core-domainpack/src/main/res/raw/home_resale_domain_pack.json"
    output_dir = repo_root / "scripts/output/ebay"
    output_dir.mkdir(parents=True, exist_ok=True)
    output_path = output_dir / "scanium_subtypes_all.json"

    if not domain_pack_path.exists():
        print(f"ERROR: Domain pack not found at {domain_pack_path}", file=sys.stderr)
        return 1

    try:
        with open(domain_pack_path, 'r') as f:
            domain_pack = json.load(f)
    except json.JSONDecodeError as e:
        print(f"ERROR: Failed to parse domain pack JSON: {e}", file=sys.stderr)
        return 1

    # Extract category/subtype information
    subtypes = []
    for category in domain_pack.get("categories", []):
        subtype = {
            "id": category["id"],
            "displayName": category["displayName"],
            "parentId": category.get("parentId"),
            "itemCategoryName": category["itemCategoryName"],
            "priority": category.get("priority", 0),
            "enabled": category.get("enabled", True),
        }
        subtypes.append(subtype)

    # Write output
    output = {
        "source": "home_resale_domain_pack.json",
        "totalSubtypes": len(subtypes),
        "subtypes": subtypes,
    }

    with open(output_path, 'w') as f:
        json.dump(output, f, indent=2)

    print(f"✅ Extracted {len(subtypes)} subtypes to {output_path}")
    return 0

if __name__ == "__main__":
    sys.exit(extract_subtypes())
