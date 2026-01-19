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

    def traverse_node(node: Dict[str, Any], path: List[str], parent_id: str = None) -> None:
        """Recursively traverse category tree node building paths."""
        ***REMOVED*** Extract category info from node structure
        category = node.get("category", {})
        cat_id = category.get("categoryId")
        cat_name = category.get("categoryName", "")

        ***REMOVED*** Skip root node (categoryId "0")
        if cat_id == "0":
            ***REMOVED*** Process children without adding root to path
            for child_node in node.get("childCategoryTreeNodes", []):
                traverse_node(child_node, [], cat_id)
            return

        ***REMOVED*** Build current path
        current_path = path + [cat_name] if cat_name else path

        ***REMOVED*** Check if this is a leaf node
        child_nodes = node.get("childCategoryTreeNodes", [])
        is_leaf = node.get("leafCategoryTreeNode", False) or len(child_nodes) == 0

        ***REMOVED*** Create flat entry
        flat_entry = {
            "ebayCategoryId": cat_id,
            "name": cat_name,
            "path": current_path,
            "parentId": parent_id,
            "leafFlag": is_leaf,
            "level": node.get("categoryTreeNodeLevel", len(current_path)),
        }
        flat_categories.append(flat_entry)

        ***REMOVED*** Recursively process child nodes
        for child_node in child_nodes:
            traverse_node(child_node, current_path, cat_id)

    ***REMOVED*** Handle real eBay API structure with rootCategoryNode
    category_tree = tree_data.get("categoryTree", {})
    root_node = category_tree.get("rootCategoryNode")

    if root_node:
        ***REMOVED*** Real eBay API structure
        traverse_node(root_node, [])
    else:
        ***REMOVED*** Fallback to mock data structure
        def traverse_old(category: Dict[str, Any], path: List[str]) -> None:
            """Legacy traversal for mock data structure."""
            cat_id = category.get("categoryId")
            cat_name = category.get("categoryName", "")
            current_path = path + [cat_name] if cat_name else path
            flat_entry = {
                "ebayCategoryId": cat_id,
                "name": cat_name,
                "path": current_path,
                "parentId": category.get("parentCategoryId"),
                "leafFlag": len(category.get("subcategories", [])) == 0,
            }
            flat_categories.append(flat_entry)
            for sub_cat in category.get("subcategories", []):
                traverse_old(sub_cat, current_path)

        categories = category_tree.get("categories", [])
        if not categories:
            categories = tree_data.get("categories", [])
        for cat in categories:
            traverse_old(cat, [])

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
