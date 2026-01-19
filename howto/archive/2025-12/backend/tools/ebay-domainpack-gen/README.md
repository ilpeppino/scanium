> Archived on 2025-12-20: backend notes kept for reference; see docs/ARCHITECTURE.md for current
> state.

***REMOVED*** eBay Domain Pack Generator

Auto-generate Scanium Domain Packs from eBay marketplace category trees.

***REMOVED******REMOVED*** Quick Start

```bash
***REMOVED*** Install dependencies
npm install

***REMOVED*** Set eBay credentials
export EBAY_CLIENT_ID="your-client-id"
export EBAY_CLIENT_SECRET="your-client-secret"

***REMOVED*** Generate domain packs for Netherlands
npm run gen -- generate --marketplace EBAY_NL

***REMOVED*** Run tests
npm test
```

***REMOVED******REMOVED*** Documentation

See [EBAY_GENERATOR.md](./EBAY_GENERATOR.md) for comprehensive documentation.

***REMOVED******REMOVED*** Commands

```bash
***REMOVED*** Development
npm run dev              ***REMOVED*** Run in dev mode with tsx watch
npm run build            ***REMOVED*** Build TypeScript to dist/

***REMOVED*** Generation
npm run gen              ***REMOVED*** Run the generator CLI

***REMOVED*** Testing
npm test                 ***REMOVED*** Run unit tests
npm run test:watch       ***REMOVED*** Run tests in watch mode
```

***REMOVED******REMOVED*** Examples

```bash
***REMOVED*** Generate for Germany with aspects
npm run gen -- generate --marketplace EBAY_DE --enable-aspects

***REMOVED*** List supported marketplaces
npm run gen -- list-marketplaces

***REMOVED*** Dry run (preview without writing files)
npm run gen -- generate --marketplace EBAY_FR --dry-run
```

***REMOVED******REMOVED*** Architecture

```
src/
├── cli/                    ***REMOVED*** CLI interface (Commander.js)
├── lib/                    ***REMOVED*** Core library
│   ├── ebay-auth.ts       ***REMOVED*** OAuth client credentials
│   ├── ebay-taxonomy-client.ts  ***REMOVED*** Taxonomy API client
│   ├── category-mapper.ts ***REMOVED*** eBay → Scanium category mapping
│   ├── token-generator.ts ***REMOVED*** Keyword token generation
│   ├── tree-traversal.ts  ***REMOVED*** Category tree extraction
│   ├── cache.ts           ***REMOVED*** File-based caching
│   └── domain-pack-generator.ts  ***REMOVED*** Main orchestrator
└── types/                 ***REMOVED*** TypeScript type definitions
```

***REMOVED******REMOVED*** License

UNLICENSED
