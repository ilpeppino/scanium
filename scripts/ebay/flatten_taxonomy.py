***REMOVED***!/usr/bin/env python3
"""
Flatten eBay category tree from ebay_category_tree.json
Output: scripts/output/ebay/uk/ebay_flat_categories.json

Flattens hierarchical category tree into a flat list with full paths.
Each row includes: ebayCategoryId, path (list), name, leafFlag, parentId
"""

import json
import sys
from pathlib import Path
from typing import List, Dict, Any

def flatten_category_tree(tree_data: Dict[str, Any]) -> List[Dict[str, Any]]:
    """Flatten category tree recursively."""
    flat_categories = []

    def traverse(category: Dict[str, Any], path: List[str]) -> None:
        """Recursively traverse category tree building paths."""
        cat_id = category.get("categoryId")
        cat_name = category.get("categoryName", "")

        ***REMOVED*** Build current path
        current_path = path + [cat_name] if cat_name else path

        ***REMOVED*** Create flat entry
        flat_entry = {
            "ebayCategoryId": cat_id,
            "name": cat_name,
            "path": current_path,
            "parentId": category.get("parentCategoryId"),
            "leafFlag": len(category.get("subcategories", [])) == 0,
        }
        flat_categories.append(flat_entry)

        ***REMOVED*** Recursively process subcategories
        for sub_cat in category.get("subcategories", []):
            traverse(sub_cat, current_path)

    ***REMOVED*** Handle both direct categories and nested structure
    categories = tree_data.get("categoryTree", {}).get("categories", [])
    if not categories:
        ***REMOVED*** Try alternate structure (if tree_data is directly the categories list)
        categories = tree_data.get("categories", [])

    for cat in categories:
        traverse(cat, [])

    return flat_categories

def flatten_taxonomy() -> int:
    """Flatten eBay taxonomy tree."""
    repo_root = Path(__file__).parent.parent.parent
    input_path = repo_root / "scripts/output/ebay/uk/ebay_category_tree.json"
    output_path = repo_root / "scripts/output/ebay/uk/ebay_flat_categories.json"

    if not input_path.exists():
        print(f"❌ ERROR: Input file not found: {input_path}", file=sys.stderr)
        print(f"   Run 'python3 scripts/ebay/fetch_taxonomy.py' first.", file=sys.stderr)
        return 1

    try:
        with open(input_path, 'r') as f:
            tree_data = json.load(f)
    except json.JSONDecodeError as e:
        print(f"❌ ERROR: Failed to parse {input_path}: {e}", file=sys.stderr)
        return 1

    ***REMOVED*** Flatten the tree
    flat_categories = flatten_category_tree(tree_data)

    ***REMOVED*** Write output
    output = {
        "source": "ebay_category_tree.json",
        "marketplace": tree_data.get("marketplace", "EBAY_GB"),
        "totalCategories": len(flat_categories),
        "categories": flat_categories,
    }

    with open(output_path, 'w') as f:
        json.dump(output, f, indent=2)

    print(f"✅ Flattened {len(flat_categories)} categories to {output_path}")

    ***REMOVED*** Print summary
    leaf_cats = [c for c in flat_categories if c.get("leafFlag")]
    print(f"   Leaf categories: {len(leaf_cats)}")
    print(f"   Internal categories: {len(flat_categories) - len(leaf_cats)}")

    return 0

if __name__ == "__main__":
    sys.exit(flatten_taxonomy())
