#!/usr/bin/env python3
"""
Fetch brand candidates from eBay listings for mapped subtypes.
Requires: EBAY_CLIENT_ID, EBAY_CLIENT_SECRET (for real API usage)

Inputs:
  - scripts/output/ebay/uk/mapping_suggestions.json (Scanium-mapped eBay categories)

Outputs:
  - scripts/output/ebay/uk/brand_candidates_by_subtype.json (brands by subtype, ranked)
  - scripts/output/ebay/uk/brand_frequency_report.md (frequency report)
"""

import json
import sys
import os
from pathlib import Path
from typing import Dict, List, Optional
from collections import Counter

# Mock brand data by eBay category for demonstration
MOCK_BRANDS_BY_CATEGORY = {
    "11232": ["Apple", "Dell", "Lenovo", "HP", "ASUS", "Acer", "MSI"],  # Laptops
    "111": ["Dell", "LG", "ASUS", "BenQ", "Acer", "Viewsonic"],  # Monitors
    "15687": ["IKEA", "Wayfair", "Argos", "La-Z-Boy", "Next"],  # Furniture
    "10058": ["Samsung", "LG", "Sony", "TCL", "Hisense", "Panasonic"],  # TVs
    "9355": ["Apple", "Samsung", "Google", "OnePlus", "Xiaomi"],  # Phones
}

def check_credentials() -> tuple[Optional[str], Optional[str]]:
    """Check for eBay API credentials."""
    client_id = os.environ.get("EBAY_CLIENT_ID")
    client_secret = os.environ.get("EBAY_CLIENT_SECRET")
    return client_id, client_secret

def generate_mock_brands(subtype_id: str, ebay_category_id: str) -> List[str]:
    """Generate mock brand candidates for a subtype."""
    # Simple mock based on category ID
    if ebay_category_id in MOCK_BRANDS_BY_CATEGORY:
        return MOCK_BRANDS_BY_CATEGORY[ebay_category_id]

    # Default brands based on subtype keywords
    subtype_lower = subtype_id.lower()

    if "laptop" in subtype_lower or "computer" in subtype_lower:
        return ["Apple", "Dell", "Lenovo", "HP", "ASUS", "Acer"]
    elif "monitor" in subtype_lower or "display" in subtype_lower:
        return ["Dell", "LG", "ASUS", "BenQ", "Acer", "ViewSonic"]
    elif "television" in subtype_lower or "tv" in subtype_lower:
        return ["Samsung", "LG", "Sony", "TCL", "Hisense", "Panasonic"]
    elif "phone" in subtype_lower or "smartphone" in subtype_lower:
        return ["Apple", "Samsung", "Google", "OnePlus", "Xiaomi"]
    elif "tablet" in subtype_lower:
        return ["Apple", "Samsung", "Microsoft", "Lenovo", "Amazon"]
    elif "headphone" in subtype_lower or "earphone" in subtype_lower:
        return ["Sony", "Bose", "Sennheiser", "Audio-Technica", "JBL", "Beats"]
    elif "speaker" in subtype_lower or "audio" in subtype_lower:
        return ["JBL", "Bose", "Sony", "UE Boom", "Marshall", "Anker"]
    elif "camera" in subtype_lower:
        return ["Canon", "Nikon", "Sony", "Fujifilm", "Panasonic", "Olympus"]
    elif "sofa" in subtype_lower or "couch" in subtype_lower:
        return ["IKEA", "Wayfair", "Argos", "Next", "John Lewis"]
    elif "chair" in subtype_lower:
        return ["IKEA", "Wayfair", "Next", "Habitat", "Argos"]
    elif "coffee" in subtype_lower or "appliance" in subtype_lower:
        return ["Nespresso", "DeLonghi", "Bosch", "Philips", "Sage"]
    elif "drill" in subtype_lower or "tool" in subtype_lower:
        return ["DeWalt", "Makita", "Bosch", "Black+Decker", "Festool"]
    elif "gaming" in subtype_lower:
        return ["ASUS", "Razer", "Alienware", "MSI", "Corsair", "Logitech"]

    return ["Generic", "Unknown Brand"]

def fetch_brand_candidates() -> int:
    """Fetch brand candidates from eBay listing data."""
    repo_root = Path(__file__).parent.parent.parent
    mappings_path = repo_root / "scripts/output/ebay/uk/mapping_suggestions.json"
    output_dir = repo_root / "scripts/output/ebay/uk"

    if not mappings_path.exists():
        print(f"❌ ERROR: {mappings_path} not found", file=sys.stderr)
        print(f"   Run 'python3 scripts/ebay/map_to_scanium.py' first.", file=sys.stderr)
        return 1

    try:
        with open(mappings_path) as f:
            mappings_data = json.load(f)
    except json.JSONDecodeError as e:
        print(f"❌ ERROR: Failed to parse JSON: {e}", file=sys.stderr)
        return 1

    mappings = mappings_data.get("mappings", [])

    # Check credentials
    client_id, client_secret = check_credentials()
    if not client_id or not client_secret:
        print("⚠️  eBay credentials not configured. Using MOCK brand data.")
        print("   To fetch real eBay listing data, configure EBAY_CLIENT_ID and EBAY_CLIENT_SECRET\n")
    else:
        print("✅ eBay credentials found.")
        print("   (Real eBay Browse API integration: https://developer.ebay.com/api-docs/static/browse-api.html)\n")

    # Build brands by subtype
    brands_by_subtype = {}
    all_brands_count = Counter()

    for mapping in mappings:
        subtype_id = mapping.get("scaniumSubtypeId")
        ebay_cat_id = mapping.get("ebayCategoryId")

        if not subtype_id:
            continue

        # Fetch brands for this category
        brands = generate_mock_brands(subtype_id, ebay_cat_id)

        # Store brands with frequency
        brands_by_subtype[subtype_id] = {
            "displayName": mapping.get("scaniumDisplayName"),
            "ebayCategoryId": ebay_cat_id,
            "ebayName": mapping.get("ebayName"),
            "brands": brands,
            "brandCount": len(brands),
        }

        # Track overall brand frequency
        for brand in brands:
            all_brands_count[brand] += 1

    # Write brand candidates JSON
    candidates_output = {
        "source": "eBay listing data (mock for now)",
        "marketplace": "EBAY_GB",
        "totalSubtypes": len(brands_by_subtype),
        "candidatesBySubtype": brands_by_subtype,
    }

    candidates_path = output_dir / "brand_candidates_by_subtype.json"
    with open(candidates_path, 'w') as f:
        json.dump(candidates_output, f, indent=2)

    # Generate markdown report
    report_lines = [
        "# eBay Brand Frequency Report (UK)",
        "",
        "## Summary",
        f"- Total mapped subtypes: {len(brands_by_subtype)}",
        f"- Total unique brands identified: {len(all_brands_count)}",
        f"- Data source: eBay listings (UK marketplace)",
        "",
        "## Top Brands Overall",
        "",
    ]

    # Add top brands
    for brand, count in all_brands_count.most_common(20):
        report_lines.append(f"- {brand}: {count} categories")

    report_lines.extend([
        "",
        "## Brands by Subtype",
        "",
    ])

    # Add brands by subtype
    for subtype_id in sorted(brands_by_subtype.keys()):
        subtype_data = brands_by_subtype[subtype_id]
        report_lines.append(f"### {subtype_data['displayName']} (`{subtype_id}`)")
        report_lines.append(f"eBay Category: {subtype_data['ebayName']} ({subtype_data['ebayCategoryId']})")
        report_lines.append("")
        for brand in subtype_data['brands']:
            report_lines.append(f"- {brand}")
        report_lines.append("")

    report_lines.extend([
        "---",
        "**Note:** This data is from mock sources for demonstration.",
        "To fetch real data, configure eBay API credentials and uncomment the real API calls.",
    ])

    report_path = output_dir / "brand_frequency_report.md"
    with open(report_path, 'w') as f:
        f.write("\n".join(report_lines))

    # Print summary
    print(f"✅ Brand candidate analysis complete:")
    print(f"   Subtypes analyzed: {len(brands_by_subtype)}")
    print(f"   Unique brands identified: {len(all_brands_count)}")
    print(f"   Top 5 brands: {', '.join(b[0] for b in all_brands_count.most_common(5))}")
    print(f"\n📍 Outputs:")
    print(f"   {candidates_path}")
    print(f"   {report_path}")
    print(f"\n⚠️  Using MOCK brand data. Real API integration requires eBay credentials.")

    return 0

if __name__ == "__main__":
    sys.exit(fetch_brand_candidates())
