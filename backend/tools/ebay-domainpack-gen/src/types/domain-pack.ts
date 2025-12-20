/**
 * Scanium Domain Pack type definitions
 * For eBay-generated category mappings
 */

export type ScaniumItemCategory =
  | 'HOME_GOOD'
  | 'ELECTRONICS'
  | 'FASHION'
  | 'TOOLS'
  | 'TOYS'
  | 'BOOKS'
  | 'SPORTS'
  | 'COLLECTIBLES'
  | 'AUTOMOTIVE'
  | 'GARDEN'
  | 'PET_SUPPLIES'
  | 'HEALTH_BEAUTY'
  | 'JEWELRY'
  | 'MUSIC'
  | 'ART'
  | 'OTHER';

export interface DomainPackCategory {
  /** Stable slug derived from eBay category path + id */
  id: string;

  /** Human-readable label from eBay category name */
  label: string;

  /** Scanium internal category classification */
  itemCategoryName: ScaniumItemCategory;

  /** Priority for tie-breaking (higher = more specific/preferred) */
  priority: number;

  /** Keyword tokens for text-based mapping */
  tokens: string[];

  /** Additional metadata */
  attributes: {
    /** eBay category ID */
    ebayCategoryId: string;

    /** eBay category tree ID */
    ebayTreeId: string;

    /** Marketplace ID */
    marketplaceId: string;

    /** Full category path (breadcrumb) */
    categoryPath: string[];

    /** Category depth in tree */
    depth: number;

    /** Whether this is a leaf category */
    isLeaf: boolean;

    /** Optional: eBay item aspects (for listing assistance) */
    aspects?: EBayAspectInfo[];
  };
}

export interface EBayAspectInfo {
  name: string;
  required: boolean;
  recommended: boolean;
  dataType?: string;
  mode?: string;
}

export interface DomainPack {
  /** Unique identifier for this pack */
  id: string;

  /** Human-readable name */
  name: string;

  /** Classification confidence threshold */
  threshold: number;

  /** Categories in this pack */
  categories: DomainPackCategory[];
}

export interface GeneratorConfig {
  /** Marketplace to generate for */
  marketplace: string;

  /** Generation strategy */
  strategy: 'by-branch' | 'subtree' | 'full';

  /** Root category ID for subtree strategy */
  rootCategoryId?: string;

  /** Output directory */
  outputDir: string;

  /** Whether to fetch and include item aspects */
  enableAspects: boolean;

  /** Cache directory for API responses */
  cacheDir?: string;

  /** Dry run mode (don't write files) */
  dryRun: boolean;
}
