#!/usr/bin/env python3
"""
Build aggressive brand promotion payload from eBay UK brand candidates.
Filters obvious junk, normalizes, dedupes, and limits to top 20 per subtype.
"""

import json
import re
from pathlib import Path
from typing import Dict, List, Set
from collections import defaultdict

# Junk patterns to exclude
JUNK_KEYWORDS = {
    "unbranded", "unknown", "generic", "does not apply",
    "n/a", "na", "none", "not applicable", "no brand"
}

def is_junk_brand(brand: str) -> bool:
    """Check if brand should be filtered out as junk."""
    brand_lower = brand.lower().strip()

    # Empty or too short
    if len(brand_lower) < 2:
        return True

    # Check junk keywords
    for keyword in JUNK_KEYWORDS:
        if keyword in brand_lower:
            return True

    # URL-like (contains http, www, .com, etc.)
    if any(pattern in brand_lower for pattern in ['http', 'www.', '.com', '.co.uk', '@']):
        return True

    # Pure model codes (mostly digits/symbols, not brand-like)
    # Allow brands with letters, but filter pure numeric codes like "1234-AB"
    if re.match(r'^[\d\-_/]+$', brand_lower):
        return True

    return False

def normalize_brand(brand: str) -> str:
    """Normalize brand name: trim, collapse spaces, preserve casing."""
    # Trim
    normalized = brand.strip()
    # Collapse multiple spaces
    normalized = re.sub(r'\s+', ' ', normalized)
    return normalized

def process_brands(brands: List[str], max_brands: int = 20) -> List[str]:
    """
    Process list of brands: filter junk, normalize, dedupe, limit.
    Returns list of unique, clean brands (preserves order of first occurrence).
    """
    seen_lower: Set[str] = set()
    result: List[str] = []

    for brand in brands:
        # Skip junk
        if is_junk_brand(brand):
            continue

        # Normalize
        normalized = normalize_brand(brand)
        normalized_lower = normalized.lower()

        # Dedupe case-insensitively
        if normalized_lower in seen_lower:
            continue

        seen_lower.add(normalized_lower)
        result.append(normalized)

        # Limit to max
        if len(result) >= max_brands:
            break

    return result

def build_promotion_payload(input_path: Path, output_path: Path, metrics_path: Path):
    """Build aggressive promotion payload and metrics."""

    # Load input
    with open(input_path) as f:
        data = json.load(f)

    candidates = data.get("candidatesBySubtype", {})

    # Process each subtype
    promotion_payload = {}
    stats = {
        "total_subtypes": 0,
        "total_brands_added": 0,
        "subtypes_by_brand_count": []
    }

    for subtype_key, subtype_data in candidates.items():
        brands = subtype_data.get("brands", [])

        # Process brands
        processed = process_brands(brands, max_brands=20)

        if processed:  # Only include if we have valid brands
            promotion_payload[subtype_key] = processed
            stats["total_subtypes"] += 1
            stats["total_brands_added"] += len(processed)
            stats["subtypes_by_brand_count"].append({
                "subtype": subtype_key,
                "display_name": subtype_data.get("displayName", subtype_key),
                "brand_count": len(processed)
            })

    # Sort by brand count descending
    stats["subtypes_by_brand_count"].sort(key=lambda x: x["brand_count"], reverse=True)

    # Write promotion payload
    with open(output_path, 'w') as f:
        json.dump(promotion_payload, f, indent=2)

    # Write metrics
    with open(metrics_path, 'w') as f:
        f.write("# eBay UK Brand Promotion Metrics (Aggressive)\n\n")
        f.write(f"## Summary\n\n")
        f.write(f"- **Total subtypes with brands**: {stats['total_subtypes']}\n")
        f.write(f"- **Total brands to add**: {stats['total_brands_added']}\n")
        f.write(f"- **Average brands per subtype**: {stats['total_brands_added'] / max(stats['total_subtypes'], 1):.1f}\n\n")

        f.write(f"## Top 10 Subtypes by Brand Count\n\n")
        f.write("| Rank | Subtype | Display Name | Brands |\n")
        f.write("|------|---------|--------------|--------|\n")
        for i, item in enumerate(stats["subtypes_by_brand_count"][:10], 1):
            f.write(f"| {i} | `{item['subtype']}` | {item['display_name']} | {item['brand_count']} |\n")

        f.write(f"\n## All Subtypes (sorted by brand count)\n\n")
        f.write("| Subtype | Display Name | Brands |\n")
        f.write("|---------|--------------|--------|\n")
        for item in stats["subtypes_by_brand_count"]:
            f.write(f"| `{item['subtype']}` | {item['display_name']} | {item['brand_count']} |\n")

    return stats, promotion_payload

if __name__ == "__main__":
    base_dir = Path(__file__).parent.parent
    input_file = base_dir / "output/ebay/uk/brand_candidates_by_subtype.json"
    output_file = base_dir / "output/ebay/uk/brands_promotion_payload.aggressive.json"
    metrics_file = base_dir / "output/ebay/uk/brands_promotion_metrics.md"

    print(f"Reading input from: {input_file}")
    stats, payload = build_promotion_payload(input_file, output_file, metrics_file)

    print(f"\n✅ Promotion payload created:")
    print(f"   - Subtypes: {stats['total_subtypes']}")
    print(f"   - Total brands: {stats['total_brands_added']}")
    print(f"   - Output: {output_file}")
    print(f"   - Metrics: {metrics_file}")
