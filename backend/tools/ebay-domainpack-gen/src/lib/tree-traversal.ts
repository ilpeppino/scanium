/**
 * Category Tree Traversal
 * Extracts leaf nodes and computes metadata from eBay category tree
 */

import type { CategoryTreeNode } from '../types/ebay.js';

export interface ExtractedCategory {
  categoryId: string;
  categoryName: string;
  categoryPath: string[];
  depth: number;
  isLeaf: boolean;
  parentId?: string;
}

export class TreeTraversal {
  /**
   * Extract all categories from a tree, with optional leaf-only filtering
   */
  extractCategories(
    rootNode: CategoryTreeNode,
    options?: { leafOnly?: boolean }
  ): ExtractedCategory[] {
    const categories: ExtractedCategory[] = [];
    this.traverse(rootNode, [], categories, options?.leafOnly || false);
    return categories;
  }

  /**
   * Recursively traverse the category tree
   */
  private traverse(
    node: CategoryTreeNode,
    parentPath: string[],
    results: ExtractedCategory[],
    leafOnly: boolean
  ): void {
    const currentPath = [...parentPath, node.category.categoryName];
    const isLeaf = node.leafCategoryTreeNode === true || !node.childCategoryTreeNodes?.length;

    // Add this category if:
    // - we want all categories (leafOnly=false), OR
    // - this is a leaf node
    if (!leafOnly || isLeaf) {
      results.push({
        categoryId: node.category.categoryId,
        categoryName: node.category.categoryName,
        categoryPath: currentPath,
        depth: node.categoryTreeNodeLevel,
        isLeaf,
        parentId: parentPath.length > 0 ? undefined : undefined, // eBay doesn't provide parent ID directly
      });
    }

    // Recursively process children
    if (node.childCategoryTreeNodes) {
      for (const child of node.childCategoryTreeNodes) {
        this.traverse(child, currentPath, results, leafOnly);
      }
    }
  }

  /**
   * Group categories by top-level branch for the "by-branch" strategy
   */
  groupByTopLevelBranch(categories: ExtractedCategory[]): Map<string, ExtractedCategory[]> {
    const groups = new Map<string, ExtractedCategory[]>();

    for (const category of categories) {
      const topLevelBranch = category.categoryPath[0] || 'unknown';
      const existing = groups.get(topLevelBranch) || [];
      existing.push(category);
      groups.set(topLevelBranch, existing);
    }

    return groups;
  }

  /**
   * Calculate priority score based on depth and specificity
   * Higher depth = more specific = higher priority
   */
  calculatePriority(category: ExtractedCategory): number {
    // Base priority on depth (deeper = more specific)
    let priority = category.depth * 10;

    // Bonus for leaf nodes (most specific)
    if (category.isLeaf) {
      priority += 20;
    }

    // Small bonus for longer category names (more descriptive)
    priority += Math.min(category.categoryName.length / 10, 5);

    return Math.round(priority);
  }
}
