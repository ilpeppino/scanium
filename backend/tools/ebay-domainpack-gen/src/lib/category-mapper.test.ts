/**
 * Unit tests for CategoryMapper
 */

import { describe, it, expect } from 'vitest';
import { CategoryMapper } from './category-mapper.js';

describe('CategoryMapper', () => {
  const mapper = new CategoryMapper();

  describe('mapCategoryPath', () => {
    it('should map electronics categories', () => {
      expect(mapper.mapCategoryPath(['Computers', 'Laptops'])).toBe('ELECTRONICS');
      expect(mapper.mapCategoryPath(['Cell Phones & Accessories'])).toBe('ELECTRONICS');
      expect(mapper.mapCategoryPath(['Consumer Electronics', 'TV'])).toBe('ELECTRONICS');
    });

    it('should map fashion categories', () => {
      expect(mapper.mapCategoryPath(['Clothing', 'Men'])).toBe('FASHION');
      expect(mapper.mapCategoryPath(['Shoes'])).toBe('FASHION');
      expect(mapper.mapCategoryPath(['Fashion', 'Accessories'])).toBe('FASHION');
    });

    it('should map home goods categories', () => {
      expect(mapper.mapCategoryPath(['Home & Garden', 'Furniture'])).toBe('HOME_GOOD');
      expect(mapper.mapCategoryPath(['Haus & Garten'])).toBe('HOME_GOOD');
      expect(mapper.mapCategoryPath(['Maison', 'Jardin'])).toBe('HOME_GOOD');
    });

    it('should map tools categories', () => {
      expect(mapper.mapCategoryPath(['Tools', 'Power Tools'])).toBe('TOOLS');
      expect(mapper.mapCategoryPath(['DIY', 'Hand Tools'])).toBe('TOOLS');
      expect(mapper.mapCategoryPath(['Heimwerker'])).toBe('TOOLS');
    });

    it('should map toys categories', () => {
      expect(mapper.mapCategoryPath(['Toys', 'Action Figures'])).toBe('TOYS');
      expect(mapper.mapCategoryPath(['Spielzeug'])).toBe('TOYS');
      expect(mapper.mapCategoryPath(['Baby', 'Toys'])).toBe('TOYS');
    });

    it('should map books categories', () => {
      expect(mapper.mapCategoryPath(['Books', 'Fiction'])).toBe('BOOKS');
      expect(mapper.mapCategoryPath(['Music', 'CDs'])).toBe('BOOKS');
      expect(mapper.mapCategoryPath(['Bücher'])).toBe('BOOKS');
    });

    it('should map sports categories', () => {
      expect(mapper.mapCategoryPath(['Sporting Goods', 'Fitness'])).toBe('SPORTS');
      expect(mapper.mapCategoryPath(['Sport', 'Fahrräder'])).toBe('SPORTS');
      expect(mapper.mapCategoryPath(['Outdoor', 'Camping'])).toBe('SPORTS');
    });

    it('should map collectibles categories', () => {
      expect(mapper.mapCategoryPath(['Collectibles', 'Coins'])).toBe('COLLECTIBLES');
      expect(mapper.mapCategoryPath(['Art', 'Paintings'])).toBe('COLLECTIBLES');
      expect(mapper.mapCategoryPath(['Antiques'])).toBe('COLLECTIBLES');
    });

    it('should map automotive categories', () => {
      expect(mapper.mapCategoryPath(['eBay Motors', 'Parts'])).toBe('AUTOMOTIVE');
      expect(mapper.mapCategoryPath(['Auto & Motorrad'])).toBe('AUTOMOTIVE');
    });

    it('should map pet supplies categories', () => {
      expect(mapper.mapCategoryPath(['Pet Supplies', 'Dog'])).toBe('PET_SUPPLIES');
      expect(mapper.mapCategoryPath(['Tierartikel'])).toBe('PET_SUPPLIES');
    });

    it('should handle multilingual categories (German)', () => {
      expect(mapper.mapCategoryPath(['Kleidung & Accessoires'])).toBe('FASHION');
      expect(mapper.mapCategoryPath(['Möbel & Wohnen'])).toBe('HOME_GOOD');
    });

    it('should handle multilingual categories (French)', () => {
      expect(mapper.mapCategoryPath(['Vêtements'])).toBe('FASHION');
      expect(mapper.mapCategoryPath(['Maison'])).toBe('HOME_GOOD');
    });

    it('should handle multilingual categories (Italian)', () => {
      expect(mapper.mapCategoryPath(['Abbigliamento'])).toBe('FASHION');
      expect(mapper.mapCategoryPath(['Casa'])).toBe('HOME_GOOD');
    });

    it('should handle multilingual categories (Spanish)', () => {
      expect(mapper.mapCategoryPath(['Ropa'])).toBe('FASHION');
      expect(mapper.mapCategoryPath(['Hogar'])).toBe('HOME_GOOD');
    });

    it('should default to OTHER for unknown categories', () => {
      expect(mapper.mapCategoryPath(['Unknown Category', 'Subcategory'])).toBe('OTHER');
    });

    it('should be case-insensitive', () => {
      expect(mapper.mapCategoryPath(['COMPUTERS'])).toBe('ELECTRONICS');
      expect(mapper.mapCategoryPath(['computers'])).toBe('ELECTRONICS');
      expect(mapper.mapCategoryPath(['Computers'])).toBe('ELECTRONICS');
    });
  });

  describe('getRules', () => {
    it('should return all mapping rules', () => {
      const rules = mapper.getRules();

      expect(Array.isArray(rules)).toBe(true);
      expect(rules.length).toBeGreaterThan(0);
      expect(rules[0]).toHaveProperty('pattern');
      expect(rules[0]).toHaveProperty('itemCategoryName');
    });
  });
});
