#!/usr/bin/env python3
"""
Safe brands bundle append script.

Appends brands to the brands_catalog_bundle_v1.json file for specific subtypes.

Usage:
  append_brands_bundle.py --payload-stdin [--dry-run] < payload.json
  append_brands_bundle.py --payload-file payload.json [--dry-run]

Payload format:
  {
    "brandsBySubtype": {
      "electronics_laptop": ["Apple", "Dell", "Lenovo", ...],
      "electronics_phone": ["Apple", "Samsung", ...],
      ...
    }
  }

Features:
  - Deterministic ordering of subtype keys and brands
  - Case-insensitive deduplication (preserves first occurrence casing)
  - Whitespace normalization
  - Dry-run mode with diff summary
  - Schema validation
  - Only modifies brands_catalog_bundle_v1.json
"""

import sys
import json
import argparse
from pathlib import Path
from typing import Dict, List, Set
import difflib


CATALOG_PATH = Path(__file__).parent.parent.parent / "core-domainpack" / "src" / "main" / "res" / "raw" / "brands_catalog_bundle_v1.json"


def load_existing_catalog() -> Dict:
    """Load existing brands catalog or create empty structure."""
    if CATALOG_PATH.exists():
        with open(CATALOG_PATH, "r", encoding="utf-8") as f:
            return json.load(f)
    return {
        "id": "brands_catalog",
        "version": "1.0.0",
        "description": "Brand catalog for Scanium domain pack subtypes",
        "brandsBySubtype": {}
    }


def normalize_brand(brand: str) -> str:
    """Normalize brand string: trim whitespace."""
    return brand.strip()


def dedupe_brands(brands: List[str]) -> List[str]:
    """
    Deduplicate brands case-insensitively while preserving first occurrence casing.

    Returns list sorted alphabetically (case-insensitive key, original casing).
    """
    seen_lower: Dict[str, str] = {}
    for brand in brands:
        normalized = normalize_brand(brand)
        if not normalized:
            continue
        lower = normalized.lower()
        if lower not in seen_lower:
            seen_lower[lower] = normalized

    # Sort by lowercase key, return original casing
    return [seen_lower[k] for k in sorted(seen_lower.keys())]


def validate_payload(payload: Dict) -> tuple[bool, str]:
    """Validate payload structure and content."""
    if not isinstance(payload, dict):
        return False, "Payload must be a JSON object"

    if "brandsBySubtype" not in payload:
        return False, "Payload must contain 'brandsBySubtype' key"

    brands_by_subtype = payload["brandsBySubtype"]
    if not isinstance(brands_by_subtype, dict):
        return False, "'brandsBySubtype' must be an object"

    for subtype_id, brands in brands_by_subtype.items():
        if not isinstance(subtype_id, str):
            return False, f"Subtype ID must be string, got {type(subtype_id)}"
        if not isinstance(brands, list):
            return False, f"Brands for {subtype_id} must be a list, got {type(brands)}"
        for brand in brands:
            if not isinstance(brand, str):
                return False, f"Brand in {subtype_id} must be string, got {type(brand)}"
            if not normalize_brand(brand):
                return False, f"Empty brand in {subtype_id}"

    return True, ""


def merge_brands(existing: List[str], new: List[str]) -> List[str]:
    """Merge existing and new brands, dedupe, and sort."""
    all_brands = existing + new
    return dedupe_brands(all_brands)


def apply_update(catalog: Dict, payload: Dict, dry_run: bool = False) -> tuple[bool, str]:
    """
    Apply brand update to catalog.

    Returns (success, message/diff).
    """
    brands_by_subtype = payload["brandsBySubtype"]

    # Build new catalog with deterministic ordering
    new_brands_by_subtype = {}
    for subtype_id in sorted(brands_by_subtype.keys()):
        new_brands = brands_by_subtype[subtype_id]
        existing_brands = catalog["brandsBySubtype"].get(subtype_id, [])
        merged = merge_brands(existing_brands, new_brands)
        new_brands_by_subtype[subtype_id] = merged

    # Preserve existing subtypes not in payload
    for subtype_id in sorted(catalog["brandsBySubtype"].keys()):
        if subtype_id not in new_brands_by_subtype:
            new_brands_by_subtype[subtype_id] = catalog["brandsBySubtype"][subtype_id]

    # Create updated catalog
    updated_catalog = catalog.copy()
    updated_catalog["brandsBySubtype"] = new_brands_by_subtype

    if dry_run:
        # Generate diff summary
        old_json = json.dumps(catalog, indent=2, sort_keys=True, ensure_ascii=False)
        new_json = json.dumps(updated_catalog, indent=2, sort_keys=True, ensure_ascii=False)

        diff = list(difflib.unified_diff(
            old_json.splitlines(keepends=True),
            new_json.splitlines(keepends=True),
            fromfile="current brands_catalog_bundle_v1.json",
            tofile="updated brands_catalog_bundle_v1.json"
        ))

        if not diff:
            return True, "No changes to apply (all brands already exist)."

        diff_str = "".join(diff)
        summary = f"DRY RUN: Would update {len(new_brands_by_subtype)} subtypes\n\n{diff_str}"
        return True, summary
    else:
        # Write to file
        with open(CATALOG_PATH, "w", encoding="utf-8") as f:
            json.dump(updated_catalog, f, indent=2, sort_keys=True, ensure_ascii=False)
            f.write("\n")

        # Count changes
        added_subtypes = len(set(new_brands_by_subtype.keys()) - set(catalog["brandsBySubtype"].keys()))
        updated_subtypes = sum(
            1 for subtype_id in brands_by_subtype.keys()
            if catalog["brandsBySubtype"].get(subtype_id, []) != new_brands_by_subtype[subtype_id]
        )

        msg = f"Updated brands catalog: {added_subtypes} new subtypes, {updated_subtypes} updated"
        return True, msg


def main():
    parser = argparse.ArgumentParser(
        description="Append brands to brands_catalog_bundle_v1.json"
    )
    input_group = parser.add_mutually_exclusive_group(required=True)
    input_group.add_argument(
        "--payload-stdin",
        action="store_true",
        help="Read payload from stdin"
    )
    input_group.add_argument(
        "--payload-file",
        type=str,
        help="Read payload from file"
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Preview changes without writing"
    )

    args = parser.parse_args()

    # Load payload
    try:
        if args.payload_stdin:
            payload_str = sys.stdin.read()
        else:
            with open(args.payload_file, "r", encoding="utf-8") as f:
                payload_str = f.read()

        payload = json.loads(payload_str)
    except json.JSONDecodeError as e:
        print(f"ERROR: Invalid JSON in payload: {e}", file=sys.stderr)
        return 1
    except FileNotFoundError as e:
        print(f"ERROR: {e}", file=sys.stderr)
        return 1

    # Validate payload
    valid, error_msg = validate_payload(payload)
    if not valid:
        print(f"ERROR: Validation failed: {error_msg}", file=sys.stderr)
        return 1

    # Load existing catalog
    catalog = load_existing_catalog()

    # Apply update
    success, message = apply_update(catalog, payload, dry_run=args.dry_run)

    if not success:
        print(f"ERROR: {message}", file=sys.stderr)
        return 1

    print(message)

    if args.dry_run:
        # Exit with non-zero to indicate changes pending
        return 1

    return 0


if __name__ == "__main__":
    sys.exit(main())
