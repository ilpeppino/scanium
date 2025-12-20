/**
 * Domain Pack Generator
 * Orchestrates the generation of Scanium Domain Packs from eBay category trees
 */

import { mkdirSync, writeFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import type { MarketplaceId } from '../types/ebay.js';
import type {
  DomainPack,
  DomainPackCategory,
  GeneratorConfig,
  EBayAspectInfo,
} from '../types/domain-pack.js';
import { EBayAuthClient } from './ebay-auth.js';
import { EBayTaxonomyClient } from './ebay-taxonomy-client.js';
import { CategoryMapper } from './category-mapper.js';
import { TokenGenerator } from './token-generator.js';
import { TreeTraversal } from './tree-traversal.js';
import { CacheManager } from './cache.js';

export class DomainPackGenerator {
  private config: GeneratorConfig;
  private authClient: EBayAuthClient;
  private taxonomyClient: EBayTaxonomyClient;
  private categoryMapper: CategoryMapper;
  private tokenGenerator: TokenGenerator;
  private treeTraversal: TreeTraversal;
  private cache: CacheManager;

  constructor(config: GeneratorConfig, ebayEnv: 'sandbox' | 'production') {
    this.config = config;

    // Initialize eBay clients
    const clientId = process.env.EBAY_CLIENT_ID;
    const clientSecret = process.env.EBAY_CLIENT_SECRET;

    if (!clientId || !clientSecret) {
      throw new Error(
        'eBay credentials not found. Set EBAY_CLIENT_ID and EBAY_CLIENT_SECRET environment variables.'
      );
    }

    this.authClient = new EBayAuthClient({
      clientId,
      clientSecret,
      environment: ebayEnv,
    });

    this.taxonomyClient = new EBayTaxonomyClient(this.authClient, ebayEnv);
    this.categoryMapper = new CategoryMapper();
    this.tokenGenerator = new TokenGenerator();
    this.treeTraversal = new TreeTraversal();
    this.cache = new CacheManager(config.cacheDir);
  }

  /**
   * Generate domain pack(s) based on the configured strategy
   */
  async generate(): Promise<void> {
    console.log(`\n🚀 Starting domain pack generation for ${this.config.marketplace}\n`);

    // Step 1: Get category tree ID
    console.log('📋 Getting category tree ID...');
    const treeId = await this.getCachedCategoryTreeId(
      this.config.marketplace as MarketplaceId
    );
    console.log(`   Tree ID: ${treeId}\n`);

    // Step 2: Fetch category tree
    console.log('🌳 Fetching category tree...');
    const tree = await this.getCachedCategoryTree(treeId);
    if (!tree.rootCategoryNode) {
      throw new Error('Category tree has no root node');
    }
    console.log(`   ✓ Tree fetched\n`);

    // Step 3: Extract categories
    console.log('🔍 Extracting categories...');
    const extractedCategories = this.treeTraversal.extractCategories(tree.rootCategoryNode, {
      leafOnly: true, // Only leaf nodes for now
    });
    console.log(`   Found ${extractedCategories.length} leaf categories\n`);

    // Step 4: Fetch aspects (if enabled)
    let aspectsMap: Map<string, EBayAspectInfo[]> | undefined;
    if (this.config.enableAspects) {
      console.log('📦 Fetching item aspects (this may take a while)...');
      aspectsMap = await this.fetchAspects(
        treeId,
        extractedCategories.map((c) => c.categoryId)
      );
      console.log(`   ✓ Fetched aspects for ${aspectsMap.size} categories\n`);
    }

    // Step 5: Generate domain packs based on strategy
    console.log(`📝 Generating domain packs (strategy: ${this.config.strategy})...\n`);

    if (this.config.strategy === 'by-branch') {
      await this.generateByBranch(
        extractedCategories,
        treeId,
        this.config.marketplace,
        aspectsMap
      );
    } else if (this.config.strategy === 'subtree') {
      await this.generateSubtree(
        extractedCategories,
        treeId,
        this.config.marketplace,
        aspectsMap
      );
    } else {
      await this.generateFull(
        extractedCategories,
        treeId,
        this.config.marketplace,
        aspectsMap
      );
    }

    console.log('\n✅ Domain pack generation complete!\n');
  }

  /**
   * Generate multiple packs by top-level branch
   */
  private async generateByBranch(
    categories: any[],
    treeId: string,
    marketplaceId: string,
    aspectsMap?: Map<string, EBayAspectInfo[]>
  ): Promise<void> {
    const grouped = this.treeTraversal.groupByTopLevelBranch(categories);

    for (const [branchName, branchCategories] of grouped.entries()) {
      const packId = this.createPackId(marketplaceId, branchName);
      const pack = this.buildDomainPack(
        packId,
        `${marketplaceId} - ${branchName}`,
        branchCategories,
        treeId,
        marketplaceId,
        aspectsMap
      );

      await this.writePack(pack, branchName);
    }
  }

  /**
   * Generate a single pack from a subtree
   */
  private async generateSubtree(
    categories: any[],
    treeId: string,
    marketplaceId: string,
    aspectsMap?: Map<string, EBayAspectInfo[]>
  ): Promise<void> {
    // Filter categories that are under the specified root
    // For now, generate all (subtree filtering would require additional API call)
    const packId = this.createPackId(marketplaceId, 'subtree');
    const pack = this.buildDomainPack(
      packId,
      `${marketplaceId} - Subtree`,
      categories,
      treeId,
      marketplaceId,
      aspectsMap
    );

    await this.writePack(pack, 'subtree');
  }

  /**
   * Generate a single pack with all categories
   */
  private async generateFull(
    categories: any[],
    treeId: string,
    marketplaceId: string,
    aspectsMap?: Map<string, EBayAspectInfo[]>
  ): Promise<void> {
    const packId = this.createPackId(marketplaceId, 'full');
    const pack = this.buildDomainPack(
      packId,
      `${marketplaceId} - Full`,
      categories,
      treeId,
      marketplaceId,
      aspectsMap
    );

    await this.writePack(pack, 'full');
  }

  /**
   * Build a DomainPack from extracted categories
   */
  private buildDomainPack(
    id: string,
    name: string,
    categories: any[],
    treeId: string,
    marketplaceId: string,
    aspectsMap?: Map<string, EBayAspectInfo[]>
  ): DomainPack {
    const domainCategories: DomainPackCategory[] = categories.map((cat) => {
      const itemCategoryName = this.categoryMapper.mapCategoryPath(cat.categoryPath);
      const tokens = this.tokenGenerator.generateTokens(cat.categoryName, cat.categoryPath);
      const priority = this.treeTraversal.calculatePriority(cat);

      const category: DomainPackCategory = {
        id: this.createCategoryId(cat.categoryPath, cat.categoryId),
        label: cat.categoryName,
        itemCategoryName,
        priority,
        tokens,
        attributes: {
          ebayCategoryId: cat.categoryId,
          ebayTreeId: treeId,
          marketplaceId,
          categoryPath: cat.categoryPath,
          depth: cat.depth,
          isLeaf: cat.isLeaf,
        },
      };

      // Add aspects if available
      if (aspectsMap?.has(cat.categoryId)) {
        category.attributes.aspects = aspectsMap.get(cat.categoryId);
      }

      return category;
    });

    // Sort categories by priority (descending) then by id (ascending) for stability
    domainCategories.sort((a, b) => {
      if (a.priority !== b.priority) {
        return b.priority - a.priority;
      }
      return a.id.localeCompare(b.id);
    });

    return {
      id,
      name,
      threshold: 0.5, // Default threshold
      categories: domainCategories,
    };
  }

  /**
   * Write a domain pack to disk
   */
  private async writePack(pack: DomainPack, suffix: string): Promise<void> {
    const fileName = this.sanitizeFilename(suffix) + '.json';
    const filePath = join(this.config.outputDir, fileName);

    if (this.config.dryRun) {
      console.log(`   [DRY RUN] Would write: ${filePath}`);
      console.log(`   Categories: ${pack.categories.length}`);
      return;
    }

    // Ensure output directory exists
    mkdirSync(dirname(filePath), { recursive: true });

    // Write with pretty formatting
    const json = JSON.stringify(pack, null, 2);
    writeFileSync(filePath, json, 'utf-8');

    console.log(`   ✓ Written: ${filePath} (${pack.categories.length} categories)`);
  }

  /**
   * Create a stable category ID from path and eBay ID
   */
  private createCategoryId(categoryPath: string[], ebayId: string): string {
    // Use the last 2 path elements + eBay ID for uniqueness
    const pathPart = categoryPath
      .slice(-2)
      .map((p) => this.slugify(p))
      .join('_');

    return `${pathPart}_${ebayId}`;
  }

  /**
   * Create a pack ID
   */
  private createPackId(marketplace: string, suffix: string): string {
    return `ebay_${marketplace.toLowerCase()}_${this.slugify(suffix)}`;
  }

  /**
   * Slugify a string for use in IDs
   */
  private slugify(text: string): string {
    return text
      .toLowerCase()
      .replace(/[^\w\s-]/g, '')
      .replace(/\s+/g, '_')
      .replace(/_+/g, '_')
      .replace(/^_|_$/g, '');
  }

  /**
   * Sanitize a filename
   */
  private sanitizeFilename(name: string): string {
    return this.slugify(name);
  }

  /**
   * Get category tree ID with caching
   */
  private async getCachedCategoryTreeId(marketplaceId: MarketplaceId): Promise<string> {
    const cacheKey = `tree-id-${marketplaceId}`;
    const cached = this.cache.get<string>(cacheKey);

    if (cached) {
      console.log('   (using cached tree ID)');
      return cached;
    }

    const treeId = await this.taxonomyClient.getDefaultCategoryTreeId(marketplaceId);
    this.cache.set(cacheKey, treeId);
    return treeId;
  }

  /**
   * Get category tree with caching
   */
  private async getCachedCategoryTree(treeId: string): Promise<any> {
    const cacheKey = `tree-${treeId}`;
    const cached = this.cache.get<any>(cacheKey);

    if (cached) {
      console.log('   (using cached tree)');
      return cached;
    }

    const tree = await this.taxonomyClient.getCategoryTree(treeId);
    this.cache.set(cacheKey, tree);
    return tree;
  }

  /**
   * Fetch aspects for multiple categories with caching
   */
  private async fetchAspects(
    treeId: string,
    categoryIds: string[]
  ): Promise<Map<string, EBayAspectInfo[]>> {
    const result = new Map<string, EBayAspectInfo[]>();

    for (const categoryId of categoryIds) {
      const cacheKey = `aspects-${treeId}-${categoryId}`;
      const cached = this.cache.get<EBayAspectInfo[]>(cacheKey);

      if (cached) {
        result.set(categoryId, cached);
        continue;
      }

      try {
        const aspectMetadata = await this.taxonomyClient.getItemAspectsForCategory(
          treeId,
          categoryId
        );

        const aspects: EBayAspectInfo[] = aspectMetadata.aspects.map((aspect) => ({
          name: aspect.localizedAspectName,
          required: aspect.aspectConstraint?.aspectRequired || false,
          recommended: aspect.aspectConstraint?.aspectUsage === 'RECOMMENDED',
          dataType: aspect.aspectDataType,
          mode: aspect.aspectMode,
        }));

        result.set(categoryId, aspects);
        this.cache.set(cacheKey, aspects);

        // Rate limiting
        await new Promise((resolve) => setTimeout(resolve, 100));
      } catch (error) {
        console.warn(`   Warning: Failed to fetch aspects for ${categoryId}`);
      }
    }

    return result;
  }
}
