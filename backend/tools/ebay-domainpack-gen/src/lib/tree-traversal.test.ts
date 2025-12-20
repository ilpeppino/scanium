/**
 * Unit tests for TreeTraversal
 */

import { describe, it, expect } from 'vitest';
import { TreeTraversal } from './tree-traversal.js';
import type { CategoryTreeNode } from '../types/ebay.js';

describe('TreeTraversal', () => {
  const traversal = new TreeTraversal();

  // Mock category tree
  const mockTree: CategoryTreeNode = {
    category: {
      categoryId: '1',
      categoryName: 'Electronics',
    },
    categoryTreeNodeLevel: 1,
    leafCategoryTreeNode: false,
    childCategoryTreeNodes: [
      {
        category: {
          categoryId: '2',
          categoryName: 'Computers',
        },
        categoryTreeNodeLevel: 2,
        leafCategoryTreeNode: false,
        childCategoryTreeNodes: [
          {
            category: {
              categoryId: '3',
              categoryName: 'Laptops',
            },
            categoryTreeNodeLevel: 3,
            leafCategoryTreeNode: true,
          },
          {
            category: {
              categoryId: '4',
              categoryName: 'Desktops',
            },
            categoryTreeNodeLevel: 3,
            leafCategoryTreeNode: true,
          },
        ],
      },
      {
        category: {
          categoryId: '5',
          categoryName: 'Phones',
        },
        categoryTreeNodeLevel: 2,
        leafCategoryTreeNode: true,
      },
    ],
  };

  describe('extractCategories', () => {
    it('should extract all categories when leafOnly is false', () => {
      const categories = traversal.extractCategories(mockTree, { leafOnly: false });

      expect(categories).toHaveLength(5);
      expect(categories.map((c) => c.categoryId)).toEqual(['1', '2', '3', '4', '5']);
    });

    it('should extract only leaf categories when leafOnly is true', () => {
      const categories = traversal.extractCategories(mockTree, { leafOnly: true });

      expect(categories).toHaveLength(3);
      expect(categories.map((c) => c.categoryId)).toEqual(['3', '4', '5']);
    });

    it('should build correct category paths', () => {
      const categories = traversal.extractCategories(mockTree, { leafOnly: true });

      const laptop = categories.find((c) => c.categoryId === '3');
      expect(laptop?.categoryPath).toEqual(['Electronics', 'Computers', 'Laptops']);

      const phone = categories.find((c) => c.categoryId === '5');
      expect(phone?.categoryPath).toEqual(['Electronics', 'Phones']);
    });

    it('should track depth correctly', () => {
      const categories = traversal.extractCategories(mockTree, { leafOnly: true });

      const laptop = categories.find((c) => c.categoryId === '3');
      expect(laptop?.depth).toBe(3);

      const phone = categories.find((c) => c.categoryId === '5');
      expect(phone?.depth).toBe(2);
    });

    it('should mark leaf categories correctly', () => {
      const categories = traversal.extractCategories(mockTree, { leafOnly: true });

      categories.forEach((cat) => {
        expect(cat.isLeaf).toBe(true);
      });
    });
  });

  describe('groupByTopLevelBranch', () => {
    it('should group categories by top-level branch', () => {
      const categories = traversal.extractCategories(mockTree, { leafOnly: true });
      const grouped = traversal.groupByTopLevelBranch(categories);

      expect(grouped.size).toBe(1);
      expect(grouped.has('Electronics')).toBe(true);
      expect(grouped.get('Electronics')).toHaveLength(3);
    });

    it('should handle multiple branches', () => {
      const categories = [
        {
          categoryId: '1',
          categoryName: 'Laptop',
          categoryPath: ['Electronics', 'Computers', 'Laptop'],
          depth: 3,
          isLeaf: true,
        },
        {
          categoryId: '2',
          categoryName: 'T-Shirt',
          categoryPath: ['Fashion', 'Clothing', 'T-Shirt'],
          depth: 3,
          isLeaf: true,
        },
        {
          categoryId: '3',
          categoryName: 'Sofa',
          categoryPath: ['Home', 'Furniture', 'Sofa'],
          depth: 3,
          isLeaf: true,
        },
      ];

      const grouped = traversal.groupByTopLevelBranch(categories);

      expect(grouped.size).toBe(3);
      expect(grouped.has('Electronics')).toBe(true);
      expect(grouped.has('Fashion')).toBe(true);
      expect(grouped.has('Home')).toBe(true);
    });
  });

  describe('calculatePriority', () => {
    it('should give higher priority to deeper categories', () => {
      const shallow = {
        categoryId: '1',
        categoryName: 'Category',
        categoryPath: ['Root', 'Category'],
        depth: 2,
        isLeaf: false,
      };

      const deep = {
        categoryId: '2',
        categoryName: 'Category',
        categoryPath: ['Root', 'Branch', 'Sub', 'Category'],
        depth: 4,
        isLeaf: false,
      };

      const shallowPriority = traversal.calculatePriority(shallow);
      const deepPriority = traversal.calculatePriority(deep);

      expect(deepPriority).toBeGreaterThan(shallowPriority);
    });

    it('should give bonus to leaf categories', () => {
      const nonLeaf = {
        categoryId: '1',
        categoryName: 'Category',
        categoryPath: ['Root', 'Category'],
        depth: 2,
        isLeaf: false,
      };

      const leaf = {
        categoryId: '2',
        categoryName: 'Category',
        categoryPath: ['Root', 'Category'],
        depth: 2,
        isLeaf: true,
      };

      const nonLeafPriority = traversal.calculatePriority(nonLeaf);
      const leafPriority = traversal.calculatePriority(leaf);

      expect(leafPriority).toBeGreaterThan(nonLeafPriority);
    });

    it('should give small bonus for longer names', () => {
      const short = {
        categoryId: '1',
        categoryName: 'TV',
        categoryPath: ['Root', 'TV'],
        depth: 2,
        isLeaf: true,
      };

      const long = {
        categoryId: '2',
        categoryName: 'Coffee Maker with Built-in Grinder',
        categoryPath: ['Root', 'Coffee Maker with Built-in Grinder'],
        depth: 2,
        isLeaf: true,
      };

      const shortPriority = traversal.calculatePriority(short);
      const longPriority = traversal.calculatePriority(long);

      expect(longPriority).toBeGreaterThanOrEqual(shortPriority);
    });

    it('should return deterministic results', () => {
      const category = {
        categoryId: '1',
        categoryName: 'Laptop',
        categoryPath: ['Electronics', 'Computers', 'Laptop'],
        depth: 3,
        isLeaf: true,
      };

      const priority1 = traversal.calculatePriority(category);
      const priority2 = traversal.calculatePriority(category);

      expect(priority1).toBe(priority2);
    });
  });
});
