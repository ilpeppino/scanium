***REMOVED***!/usr/bin/env python3
"""
Map eBay categories to Scanium subtypes.
Inputs:
  - scripts/output/ebay/uk/ebay_flat_categories.json (eBay taxonomy)
  - scripts/output/ebay/scanium_subtypes_all.json (Scanium subtypes)

Outputs:
  - scripts/output/ebay/uk/mapping_suggestions.json (mapped categories with confidence)
  - scripts/output/ebay/uk/unmapped_categories.json (categories not mapped)

Logic: Rule-based token matching + synonym expansion
"""

import json
import sys
import re
from pathlib import Path
from typing import Dict, List, Tuple, Optional

***REMOVED*** Synonym mappings for matching eBay categories to Scanium subtypes
SYNONYMS = {
    "laptop": ["notebook", "computer", "macbook", "chromebook"],
    "monitor": ["display", "screen", "lcd"],
    "television": ["tv", "telly", "screen"],
    "smartphone": ["phone", "mobile", "iphone", "android"],
    "tablet": ["ipad", "pad"],
    "headphones": ["earbuds", "earphones", "headset", "airpods"],
    "speaker": ["bluetooth speaker", "audio"],
    "camera": ["dslr", "mirrorless", "camcorder"],
    "printer": ["inkjet", "laser"],
    "router": ["wifi", "wireless"],
    "smartwatch": ["watch", "wearable"],
    "game console": ["gaming", "playstation", "xbox", "switch"],
    "sofa": ["couch", "settee", "lounge"],
    "chair": ["seat", "seating"],
    "table": ["desk", "dining"],
    "microwave": ["oven", "microwave oven"],
    "vacuum": ["vacuum cleaner", "hoover"],
    "coffee machine": ["coffee maker", "espresso"],
    "drill": ["power drill"],
    "saw": ["circular saw", "jigsaw"],
    "lawn mower": ["mower", "grass"],
    "blender": ["mixer", "food processor"],
    "gaming laptop": ["gaming pc", "gaming computer"],
    "gaming desktop": ["gaming pc", "gaming tower"],
    "mechanical keyboard": ["gaming keyboard", "keyboard"],
}

***REMOVED*** Build reverse mapping for faster lookup
REVERSE_SYNONYMS = {}
for primary, synonyms in SYNONYMS.items():
    for syn in synonyms:
        if syn not in REVERSE_SYNONYMS:
            REVERSE_SYNONYMS[syn] = primary

***REMOVED*** Confidence levels
CONFIDENCE_HIGH = "high"
CONFIDENCE_MEDIUM = "medium"
CONFIDENCE_LOW = "low"

def tokenize(text: str) -> List[str]:
    """Tokenize text into words."""
    ***REMOVED*** Convert to lowercase and split on whitespace/special chars
    tokens = re.split(r'[\s\-_]+', text.lower())
    return [t for t in tokens if t]

def match_confidence(ebay_name: str, scanium_id: str, scanium_name: str) -> Tuple[bool, str, str]:
    """
    Determine if eBay category matches Scanium subtype.
    Returns: (match_found, confidence_level, reason)
    """
    ebay_tokens = tokenize(ebay_name)
    scanium_tokens = tokenize(scanium_name)
    scanium_tokens_from_id = tokenize(scanium_id.replace("_", " "))

    ***REMOVED*** Exact match
    if ebay_name.lower() == scanium_name.lower():
        return True, CONFIDENCE_HIGH, "Exact name match"

    ***REMOVED*** Check if primary keywords match
    for ebay_token in ebay_tokens:
        ***REMOVED*** Direct token match
        if ebay_token in scanium_tokens or ebay_token in scanium_tokens_from_id:
            return True, CONFIDENCE_HIGH, f"Direct token match: '{ebay_token}'"

        ***REMOVED*** Synonym match
        if ebay_token in REVERSE_SYNONYMS:
            primary = REVERSE_SYNONYMS[ebay_token]
            if primary in scanium_id.lower():
                return True, CONFIDENCE_MEDIUM, f"Synonym match: '{ebay_token}' -> '{primary}'"

    ***REMOVED*** Partial token match (at least 2 consecutive tokens match)
    for i in range(len(ebay_tokens) - 1):
        two_token = f"{ebay_tokens[i]} {ebay_tokens[i+1]}"
        if two_token in scanium_name.lower() or two_token in scanium_id.lower():
            return True, CONFIDENCE_MEDIUM, f"Multi-token match: '{two_token}'"

    ***REMOVED*** Weak match: significant overlap in tokens
    matching_tokens = sum(1 for t in ebay_tokens if t in scanium_tokens or t in scanium_tokens_from_id)
    if matching_tokens >= 1 and len(ebay_tokens) <= 3:
        return True, CONFIDENCE_LOW, f"Weak token overlap: {matching_tokens}/{len(ebay_tokens)} tokens"

    return False, "", ""

def map_categories() -> int:
    """Map eBay categories to Scanium subtypes."""
    repo_root = Path(__file__).parent.parent.parent
    ebay_cats_path = repo_root / "scripts/output/ebay/uk/ebay_flat_categories.json"
    scanium_subtypes_path = repo_root / "scripts/output/ebay/scanium_subtypes_all.json"
    output_dir = repo_root / "scripts/output/ebay/uk"

    ***REMOVED*** Load data
    if not ebay_cats_path.exists():
        print(f"❌ ERROR: {ebay_cats_path} not found", file=sys.stderr)
        print(f"   Run 'python3 scripts/ebay/flatten_taxonomy.py' first.", file=sys.stderr)
        return 1

    if not scanium_subtypes_path.exists():
        print(f"❌ ERROR: {scanium_subtypes_path} not found", file=sys.stderr)
        print(f"   Run 'python3 scripts/ebay/extract_subtypes.py' first.", file=sys.stderr)
        return 1

    try:
        with open(ebay_cats_path) as f:
            ebay_data = json.load(f)
        with open(scanium_subtypes_path) as f:
            scanium_data = json.load(f)
    except json.JSONDecodeError as e:
        print(f"❌ ERROR: Failed to parse JSON: {e}", file=sys.stderr)
        return 1

    ebay_categories = ebay_data.get("categories", [])
    scanium_subtypes = scanium_data.get("subtypes", [])

    ***REMOVED*** Build lookup for Scanium subtypes
    scanium_by_id = {s["id"]: s for s in scanium_subtypes}

    ***REMOVED*** Perform mapping
    mappings = []
    unmapped = []

    for ebay_cat in ebay_categories:
        ebay_id = ebay_cat.get("ebayCategoryId")
        ebay_name = ebay_cat.get("name")
        ebay_path = ebay_cat.get("path", [])

        ***REMOVED*** Try to find matching Scanium subtype
        best_match = None
        best_confidence = None

        for scanium_subtype in scanium_subtypes:
            match_found, confidence, reason = match_confidence(
                ebay_name,
                scanium_subtype["id"],
                scanium_subtype["displayName"]
            )

            if match_found:
                ***REMOVED*** Prefer higher confidence or more specific matches
                if best_match is None or (
                    confidence in [CONFIDENCE_HIGH] and
                    (best_confidence is None or best_confidence == CONFIDENCE_LOW)
                ):
                    best_match = scanium_subtype
                    best_confidence = confidence

        if best_match:
            mappings.append({
                "ebayCategoryId": ebay_id,
                "ebayName": ebay_name,
                "ebayPath": ebay_path,
                "scaniumSubtypeId": best_match["id"],
                "scaniumDisplayName": best_match["displayName"],
                "confidence": best_confidence,
            })
        else:
            unmapped.append({
                "ebayCategoryId": ebay_id,
                "ebayName": ebay_name,
                "ebayPath": ebay_path,
            })

    ***REMOVED*** Write mappings
    mappings_output = {
        "source": "eBay categories + Scanium subtypes",
        "totalMapped": len(mappings),
        "totalUnmapped": len(unmapped),
        "mappings": mappings,
    }

    with open(output_dir / "mapping_suggestions.json", 'w') as f:
        json.dump(mappings_output, f, indent=2)

    ***REMOVED*** Write unmapped
    unmapped_output = {
        "source": "eBay categories not matched to Scanium subtypes",
        "totalUnmapped": len(unmapped),
        "categories": unmapped,
    }

    with open(output_dir / "unmapped_categories.json", 'w') as f:
        json.dump(unmapped_output, f, indent=2)

    ***REMOVED*** Print summary
    high_conf = sum(1 for m in mappings if m["confidence"] == CONFIDENCE_HIGH)
    med_conf = sum(1 for m in mappings if m["confidence"] == CONFIDENCE_MEDIUM)
    low_conf = sum(1 for m in mappings if m["confidence"] == CONFIDENCE_LOW)

    print(f"✅ Mapping complete:")
    print(f"   Total eBay categories: {len(ebay_categories)}")
    print(f"   Mapped: {len(mappings)} ({len(mappings)*100//len(ebay_categories)}%)")
    print(f"     - High confidence: {high_conf}")
    print(f"     - Medium confidence: {med_conf}")
    print(f"     - Low confidence: {low_conf}")
    print(f"   Unmapped: {len(unmapped)}")
    print(f"\n📍 Outputs:")
    print(f"   {output_dir / 'mapping_suggestions.json'}")
    print(f"   {output_dir / 'unmapped_categories.json'}")

    return 0

if __name__ == "__main__":
    sys.exit(map_categories())
