> Archived on 2025-12-20: backend notes kept for reference; see docs/ARCHITECTURE.md for current state.
# eBay Domain Pack Generator

Auto-generate Scanium Domain Packs from eBay marketplace category trees.

## Quick Start

```bash
# Install dependencies
npm install

# Set eBay credentials
export EBAY_CLIENT_ID="your-client-id"
export EBAY_CLIENT_SECRET="your-client-secret"

# Generate domain packs for Netherlands
npm run gen -- generate --marketplace EBAY_NL

# Run tests
npm test
```

## Documentation

See [EBAY_GENERATOR.md](./EBAY_GENERATOR.md) for comprehensive documentation.

## Commands

```bash
# Development
npm run dev              # Run in dev mode with tsx watch
npm run build            # Build TypeScript to dist/

# Generation
npm run gen              # Run the generator CLI

# Testing
npm test                 # Run unit tests
npm run test:watch       # Run tests in watch mode
```

## Examples

```bash
# Generate for Germany with aspects
npm run gen -- generate --marketplace EBAY_DE --enable-aspects

# List supported marketplaces
npm run gen -- list-marketplaces

# Dry run (preview without writing files)
npm run gen -- generate --marketplace EBAY_FR --dry-run
```

## Architecture

```
src/
├── cli/                    # CLI interface (Commander.js)
├── lib/                    # Core library
│   ├── ebay-auth.ts       # OAuth client credentials
│   ├── ebay-taxonomy-client.ts  # Taxonomy API client
│   ├── category-mapper.ts # eBay → Scanium category mapping
│   ├── token-generator.ts # Keyword token generation
│   ├── tree-traversal.ts  # Category tree extraction
│   ├── cache.ts           # File-based caching
│   └── domain-pack-generator.ts  # Main orchestrator
└── types/                 # TypeScript type definitions
```

## License

UNLICENSED
