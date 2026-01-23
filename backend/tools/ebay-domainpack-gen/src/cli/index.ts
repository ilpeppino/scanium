#!/usr/bin/env node

/**
 * eBay Domain Pack Generator CLI
 * Generate Scanium Domain Packs from eBay category trees
 */

import { Command } from 'commander';
import type { GeneratorConfig } from '../types/domain-pack.js';
import { DomainPackGenerator } from '../lib/domain-pack-generator.js';

const program = new Command();

program
  .name('ebay-domainpack-gen')
  .description('Generate Scanium Domain Packs from eBay category trees')
  .version('1.0.0');

program
  .command('generate')
  .description('Generate domain pack(s) for a marketplace')
  .requiredOption(
    '-m, --marketplace <id>',
    'Marketplace ID (e.g., EBAY_DE, EBAY_FR, EBAY_NL)',
    'EBAY_NL'
  )
  .option(
    '-s, --strategy <type>',
    'Generation strategy: by-branch, subtree, or full',
    'by-branch'
  )
  .option('-r, --root-category <id>', 'Root category ID for subtree strategy')
  .option(
    '-o, --output <dir>',
    'Output directory',
    'domainpacks/ebay'
  )
  .option('--enable-aspects', 'Fetch and include eBay item aspects', false)
  .option('--cache <dir>', 'Cache directory for API responses', '.cache/ebay')
  .option('--dry-run', 'Preview generation without writing files', false)
  .option(
    '--env <env>',
    'eBay environment: sandbox or production',
    'production'
  )
  .action(async (options) => {
    try {
      // Validate options
      if (options.strategy === 'subtree' && !options.rootCategory) {
        console.error('Error: --root-category is required for subtree strategy');
        process.exit(1);
      }

      const validStrategies = ['by-branch', 'subtree', 'full'];
      if (!validStrategies.includes(options.strategy)) {
        console.error(
          `Error: Invalid strategy "${options.strategy}". Must be one of: ${validStrategies.join(', ')}`
        );
        process.exit(1);
      }

      const validEnvs = ['sandbox', 'production'];
      if (!validEnvs.includes(options.env)) {
        console.error(
          `Error: Invalid environment "${options.env}". Must be one of: ${validEnvs.join(', ')}`
        );
        process.exit(1);
      }

      // Build configuration
      const config: GeneratorConfig = {
        marketplace: options.marketplace,
        strategy: options.strategy,
        rootCategoryId: options.rootCategory,
        outputDir: options.output,
        enableAspects: options.enableAspects,
        cacheDir: options.cache,
        dryRun: options.dryRun,
      };

      // Display configuration
      console.log('Configuration:');
      console.log(`  Marketplace:     ${config.marketplace}`);
      console.log(`  Strategy:        ${config.strategy}`);
      if (config.rootCategoryId) {
        console.log(`  Root Category:   ${config.rootCategoryId}`);
      }
      console.log(`  Output Dir:      ${config.outputDir}`);
      console.log(`  Enable Aspects:  ${config.enableAspects}`);
      console.log(`  Cache Dir:       ${config.cacheDir || 'none'}`);
      console.log(`  Environment:     ${options.env}`);
      console.log(`  Dry Run:         ${config.dryRun}`);

      // Generate
      const generator = new DomainPackGenerator(config, options.env);
      await generator.generate();
    } catch (error) {
      console.error('\n❌ Error:', error instanceof Error ? error.message : String(error));
      process.exit(1);
    }
  });

program
  .command('list-marketplaces')
  .description('List supported eBay marketplaces')
  .action(() => {
    console.log('\nSupported eBay Marketplaces (EU-focused):\n');
    console.log('  EBAY_DE - Germany');
    console.log('  EBAY_FR - France');
    console.log('  EBAY_IT - Italy');
    console.log('  EBAY_ES - Spain');
    console.log('  EBAY_NL - Netherlands');
    console.log('  EBAY_BE - Belgium');
    console.log('  EBAY_GB - United Kingdom');
    console.log('  EBAY_AT - Austria');
    console.log('  EBAY_CH - Switzerland');
    console.log('  EBAY_US - United States');
    console.log('\nFor full list, see:');
    console.log('https://developer.ebay.com/api-docs/commerce/taxonomy/static/supportedmarketplaces.html\n');
  });

program.parse();
