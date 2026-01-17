***REMOVED***!/usr/bin/env python3
"""
Generate English-only strings pack from eBay categories.
Output: scripts/output/ebay/uk/strings_keys_catalog.ebay_uk.en.json

This generates CANDIDATE strings only - NOT wired into the app.
Uses Scanium-style keys: "subtype.<id>"
"""

import json
import sys
from pathlib import Path
from typing import Dict, List, Optional

def generate_label_from_scanium_name(scanium_name: str) -> str:
    """Generate clean English label from Scanium display name."""
    ***REMOVED*** Take the display name as-is (already clean English labels)
    return scanium_name.strip()

def generate_label_from_ebay_category(ebay_name: str) -> str:
    """Generate clean English label from eBay category name."""
    ***REMOVED*** Clean up common eBay category name patterns
    label = ebay_name.strip()

    ***REMOVED*** Remove common suffixes
    for suffix in [" - Buy", " - Sell", " & Accessories", "& More", "New & Used"]:
        if label.endswith(suffix):
            label = label[:-len(suffix)].strip()

    ***REMOVED*** Remove common prefixes
    for prefix in ["Find great deals on ", "Get the best deals on "]:
        if label.startswith(prefix):
            label = label[len(prefix):].strip()

    return label

def generate_strings_pack() -> int:
    """Generate English strings catalog for eBay-mapped subtypes."""
    repo_root = Path(__file__).parent.parent.parent
    mappings_path = repo_root / "scripts/output/ebay/uk/mapping_suggestions.json"
    scanium_subtypes_path = repo_root / "scripts/output/ebay/scanium_subtypes_all.json"
    output_dir = repo_root / "scripts/output/ebay/uk"

    if not mappings_path.exists():
        print(f"❌ ERROR: {mappings_path} not found", file=sys.stderr)
        print(f"   Run 'python3 scripts/ebay/map_to_scanium.py' first.", file=sys.stderr)
        return 1

    if not scanium_subtypes_path.exists():
        print(f"❌ ERROR: {scanium_subtypes_path} not found", file=sys.stderr)
        return 1

    try:
        with open(mappings_path) as f:
            mappings_data = json.load(f)
        with open(scanium_subtypes_path) as f:
            scanium_data = json.load(f)
    except json.JSONDecodeError as e:
        print(f"❌ ERROR: Failed to parse JSON: {e}", file=sys.stderr)
        return 1

    mappings = mappings_data.get("mappings", [])
    scanium_subtypes = scanium_data.get("subtypes", [])

    ***REMOVED*** Build Scanium lookup
    scanium_by_id = {s["id"]: s for s in scanium_subtypes}

    ***REMOVED*** Generate strings catalog
    strings_catalog = {
        "id": "ebay_uk_candidate",
        "locale": "en",
        "version": "1.0.0",
        "description": "Candidate English strings from eBay UK taxonomy. NOT wired into app.",
        "source": "mapping_suggestions.json (eBay categories mapped to Scanium subtypes)",
        "metadata": {
            "totalStrings": len(mappings),
            "generatedFrom": "eBay UK taxonomy",
            "note": "These are candidate strings only. For production use, manual curation is required.",
        },
        "strings": {},
    }

    ***REMOVED*** Build strings from mappings
    for mapping in mappings:
        subtype_id = mapping.get("scaniumSubtypeId")
        scanium_display_name = mapping.get("scaniumDisplayName")

        if not subtype_id or not scanium_display_name:
            continue

        ***REMOVED*** Use Scanium's displayName as the label (already clean English)
        label = generate_label_from_scanium_name(scanium_display_name)

        ***REMOVED*** Create key following Scanium convention
        key = f"subtype.{subtype_id}"

        strings_catalog["strings"][key] = {
            "value": label,
            "description": f"Category: {mapping.get('ebayName')} → Scanium: {subtype_id}",
            "confidence": mapping.get("confidence"),
        }

    ***REMOVED*** Write output
    output_path = output_dir / "strings_keys_catalog.ebay_uk.en.json"
    with open(output_path, 'w') as f:
        json.dump(strings_catalog, f, indent=2)

    print(f"✅ Generated strings catalog:")
    print(f"   Total strings: {len(strings_catalog['strings'])}")
    print(f"   Output: {output_path}")
    print(f"\n⚠️  IMPORTANT: This is a CANDIDATE strings pack only.")
    print(f"   It is NOT wired into the app and localization files remain untouched.")
    print(f"   For production use, manual review and curation is required.")

    return 0

if __name__ == "__main__":
    sys.exit(generate_strings_pack())
