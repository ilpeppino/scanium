/**
 * eBay Taxonomy API type definitions
 * Based on: https://developer.ebay.com/api-docs/commerce/taxonomy/
 */

export interface EBayAuthConfig {
  clientId: string;
  clientSecret: string;
  environment: 'sandbox' | 'production';
}

export interface EBayTokenResponse {
  access_token: string;
  token_type: 'Application Access Token';
  expires_in: number;
}

export interface CategoryTreeIdResponse {
  categoryTreeId: string;
  categoryTreeVersion: string;
}

export interface CategoryTree {
  categoryTreeId: string;
  categoryTreeVersion: string;
  rootCategoryNode?: CategoryTreeNode;
}

export interface CategoryTreeNode {
  category: Category;
  categoryTreeNodeLevel: number;
  leafCategoryTreeNode?: boolean;
  parentCategoryTreeNodeHref?: string;
  childCategoryTreeNodes?: CategoryTreeNode[];
}

export interface Category {
  categoryId: string;
  categoryName: string;
}

export interface CategorySubtreeResponse {
  categorySubtreeNode: CategoryTreeNode;
  categoryTreeId: string;
  categoryTreeVersion: string;
}

export interface AspectMetadata {
  aspects: Aspect[];
  categoryTreeId: string;
  categoryTreeVersion: string;
}

export interface Aspect {
  localizedAspectName: string;
  aspectConstraint?: AspectConstraint;
  aspectValues?: AspectValue[];
  relevanceIndicator?: RelevanceIndicator;
  aspectMode?: 'SELECTION_ONLY' | 'FREE_TEXT';
  aspectDataType?: 'STRING' | 'NUMBER' | 'DATE';
}

export interface AspectConstraint {
  aspectRequired?: boolean;
  aspectApplicableTo?: string[];
  itemToAspectCardinality?: 'SINGLE' | 'MULTI';
  aspectEnabledForVariations?: boolean;
  aspectUsage?: 'RECOMMENDED' | 'OPTIONAL';
}

export interface AspectValue {
  localizedValue: string;
  valueConstraints?: ValueConstraint[];
}

export interface ValueConstraint {
  applicableForLocalizedAspectName?: string;
  applicableForLocalizedAspectValues?: string[];
}

export interface RelevanceIndicator {
  searchCount: number;
}

export type MarketplaceId =
  | 'EBAY_DE'
  | 'EBAY_FR'
  | 'EBAY_IT'
  | 'EBAY_ES'
  | 'EBAY_NL'
  | 'EBAY_BE'
  | 'EBAY_GB'
  | 'EBAY_US'
  | 'EBAY_AT'
  | 'EBAY_CH';
