> Archived on 2025-12-20: backend notes kept for reference; see docs/ARCHITECTURE.md for current state.
# eBay Domain Pack Generator

Auto-generate Scanium Domain Packs from eBay marketplace category trees using the eBay Taxonomy API.

## Overview

This tool downloads eBay category hierarchies and transforms them into Scanium Domain Pack JSON files that can be used for:
- Classification mapping (text-to-category matching)
- Listing title generation
- Future eBay integration (category selection + item aspects)

## Features

- ✅ OAuth client credentials flow for eBay API authentication
- ✅ Downloads complete category trees from eBay Taxonomy API
- ✅ Extracts leaf categories with full paths
- ✅ Generates searchable keyword tokens with denylist
- ✅ Maps eBay categories to Scanium ItemCategory enums (multilingual support)
- ✅ Calculates priority scores for tie-breaking
- ✅ Optional: Fetches item aspects (required/recommended attributes)
- ✅ Caching layer to reduce API calls
- ✅ Multiple generation strategies (by-branch, subtree, full)
- ✅ Dry-run mode for previewing output
- ✅ Deterministic output for meaningful diffs

## Prerequisites

### 1. eBay Developer Account

Create an eBay Developer account and application:

1. Visit [eBay Developers Program](https://developer.ebay.com/)
2. Sign up or log in
3. Create a new application (select "Production" or "Sandbox")
4. Note your **Client ID** and **Client Secret**

### 2. Required API Access

The generator uses the [eBay Taxonomy API](https://developer.ebay.com/api-docs/commerce/taxonomy/) which requires:
- **OAuth Scope**: `https://api.ebay.com/oauth/api_scope`
- **Authentication**: Application token (client credentials flow)

### 3. Environment Variables

Set the following environment variables:

```bash
export EBAY_CLIENT_ID="your-client-id"
export EBAY_CLIENT_SECRET="your-client-secret"
export EBAY_ENV="production"  # or "sandbox"
```

Or create a `.env` file in the tool directory:

```env
EBAY_CLIENT_ID=your-client-id
EBAY_CLIENT_SECRET=your-client-secret
EBAY_ENV=production
```

## Installation

```bash
cd backend/tools/ebay-domainpack-gen
npm install
```

## Usage

### Basic Commands

```bash
# Generate domain packs for Netherlands marketplace (by-branch strategy)
npm run gen -- generate --marketplace EBAY_NL

# List supported marketplaces
npm run gen -- list-marketplaces

# Show help
npm run gen -- --help
```

### Generation Strategies

#### 1. By-Branch (Recommended)

Generates multiple domain packs, one per top-level eBay category branch:

```bash
npm run gen -- generate \
  --marketplace EBAY_DE \
  --strategy by-branch \
  --output ../../domainpacks/ebay/de
```

**Output:**
- `electronics.json`
- `fashion.json`
- `home_garden.json`
- `sporting_goods.json`
- etc.

**Pros:**
- Manageable pack sizes (typically 100-500 categories each)
- Easier to maintain and update specific verticals
- Faster to load in the app

#### 2. Subtree

Generates a single pack from a specific category subtree:

```bash
npm run gen -- generate \
  --marketplace EBAY_FR \
  --strategy subtree \
  --root-category 58058 \
  --output ../../domainpacks/ebay/fr
```

**Use case:** Generate packs for specific product categories only

#### 3. Full

Generates a single mega-pack with all leaf categories:

```bash
npm run gen -- generate \
  --marketplace EBAY_IT \
  --strategy full \
  --output ../../domainpacks/ebay/it
```

**Warning:** May produce very large files (5,000+ categories)

### Advanced Options

```bash
npm run gen -- generate \
  --marketplace EBAY_ES \
  --strategy by-branch \
  --output ../../domainpacks/ebay/es \
  --enable-aspects \              # Fetch eBay item aspects
  --cache .cache/ebay \            # Enable caching
  --env production                 # Use production API
```

#### Options Reference

| Option | Description | Default |
|--------|-------------|---------|
| `-m, --marketplace <id>` | Marketplace ID (EBAY_DE, EBAY_FR, etc.) | EBAY_NL |
| `-s, --strategy <type>` | Generation strategy (by-branch, subtree, full) | by-branch |
| `-r, --root-category <id>` | Root category ID for subtree strategy | - |
| `-o, --output <dir>` | Output directory | domainpacks/ebay |
| `--enable-aspects` | Fetch and include eBay item aspects | false |
| `--cache <dir>` | Cache directory for API responses | .cache/ebay |
| `--env <env>` | eBay environment (sandbox, production) | production |
| `--dry-run` | Preview generation without writing files | false |

### Dry Run Mode

Preview what would be generated without writing files:

```bash
npm run gen -- generate \
  --marketplace EBAY_NL \
  --dry-run
```

## Supported Marketplaces

### EU Focus

- `EBAY_DE` - Germany 🇩🇪
- `EBAY_FR` - France 🇫🇷
- `EBAY_IT` - Italy 🇮🇹
- `EBAY_ES` - Spain 🇪🇸
- `EBAY_NL` - Netherlands 🇳🇱
- `EBAY_BE` - Belgium 🇧🇪
- `EBAY_GB` - United Kingdom 🇬🇧
- `EBAY_AT` - Austria 🇦🇹
- `EBAY_CH` - Switzerland 🇨🇭

### Other

- `EBAY_US` - United States 🇺🇸

[Full list of supported marketplaces](https://developer.ebay.com/api-docs/commerce/taxonomy/static/supportedmarketplaces.html)

## Output Format

Generated domain packs follow this schema:

```json
{
  "id": "ebay_ebay_nl_electronics",
  "name": "EBAY_NL - Electronics",
  "threshold": 0.5,
  "categories": [
    {
      "id": "computers_laptops_123456",
      "label": "Laptops",
      "itemCategoryName": "ELECTRONICS",
      "priority": 55,
      "tokens": [
        "laptop",
        "laptops",
        "computer",
        "computers laptops",
        "notebook"
      ],
      "attributes": {
        "ebayCategoryId": "123456",
        "ebayTreeId": "0",
        "marketplaceId": "EBAY_NL",
        "categoryPath": ["Electronics", "Computers", "Laptops"],
        "depth": 3,
        "isLeaf": true,
        "aspects": [
          {
            "name": "Brand",
            "required": true,
            "recommended": false,
            "dataType": "STRING"
          }
        ]
      }
    }
  ]
}
```

### Field Descriptions

| Field | Type | Description |
|-------|------|-------------|
| `id` | string | Unique pack identifier |
| `name` | string | Human-readable pack name |
| `threshold` | number | Classification confidence threshold |
| `categories[]` | array | Array of category definitions |
| `categories[].id` | string | Stable category slug (deterministic) |
| `categories[].label` | string | Human-readable category name |
| `categories[].itemCategoryName` | enum | Scanium internal category |
| `categories[].priority` | number | Priority for tie-breaking (higher = more specific) |
| `categories[].tokens` | string[] | Searchable keyword tokens |
| `categories[].attributes` | object | Metadata and eBay-specific info |

## Category Mapping Rules

eBay categories are mapped to Scanium `ItemCategory` enums using pattern-based rules defined in `category-mapping-rules.yaml`.

### Supported Scanium Categories

- `ELECTRONICS` - Electronic devices and accessories
- `FASHION` - Clothing, shoes, and fashion accessories
- `HOME_GOOD` - Home goods, furniture, and garden items
- `TOOLS` - Tools and DIY equipment
- `TOYS` - Toys, games, and hobby items
- `BOOKS` - Books, music, and media
- `SPORTS` - Sports equipment and outdoor gear
- `COLLECTIBLES` - Collectibles, art, and antiques
- `AUTOMOTIVE` - Automotive and vehicle-related items
- `PET_SUPPLIES` - Pet supplies and accessories
- `HEALTH_BEAUTY` - Health and beauty products
- `JEWELRY` - Jewelry and precious items
- `MUSIC` - Musical instruments
- `ART` - Art and artistic items
- `OTHER` - Other items (fallback)

### Multilingual Support

Rules support multiple languages (German, French, Italian, Spanish, Dutch):

```yaml
- pattern: ^(Kleidung|Vêtements|Abbigliamento|Ropa|Fashion)
  itemCategoryName: FASHION
```

### Customizing Mapping Rules

Edit `category-mapping-rules.yaml` to adjust mappings:

```yaml
rules:
  - pattern: ^(Computers|Electronics)
    itemCategoryName: ELECTRONICS
    description: Electronic devices

  - pattern: .*
    itemCategoryName: OTHER
    description: Fallback
```

Rules are evaluated in order; first match wins.

## Token Generation

Tokens are generated from category names and paths using these rules:

1. **Normalization:**
   - Convert to lowercase
   - Replace `&` with "and"
   - Remove punctuation
   - Split on whitespace and special chars

2. **N-grams:**
   - Single words: `"laptop"`, `"computer"`
   - Bigrams: `"laptop computer"`
   - Trigrams: `"gaming laptop computer"`

3. **Denylist:**
   - Generic words are excluded: `"other"`, `"misc"`, `"parts"`, `"accessories"`, `"home"`, `"general"`

4. **Limits:**
   - Max 15 tokens per category (configurable)
   - Min 2 characters per token

5. **Deterministic:**
   - Tokens are sorted alphabetically for stable output

## Caching

The generator caches API responses to reduce network calls:

- **Cache location:** `.cache/ebay/` (configurable)
- **Cache duration:** 24 hours (daily refresh)
- **Cached data:**
  - Category tree IDs
  - Category trees
  - Item aspects (if enabled)

Clear cache:

```bash
rm -rf .cache/ebay
```

## Testing

Run unit tests:

```bash
npm test
```

Run tests in watch mode:

```bash
npm run test:watch
```

### Test Coverage

- ✅ Token generation and normalization
- ✅ Denylist behavior
- ✅ Category mapping (multilingual)
- ✅ Tree traversal and leaf extraction
- ✅ Priority calculation
- ✅ Deterministic output ordering

## Regenerating/Updating Packs

eBay category trees change over time. To update packs:

1. **Clear cache** to fetch fresh data:
   ```bash
   rm -rf .cache/ebay
   ```

2. **Regenerate** the packs:
   ```bash
   npm run gen -- generate --marketplace EBAY_DE --strategy by-branch
   ```

3. **Review diffs** to see what changed:
   ```bash
   git diff ../../domainpacks/ebay/de/
   ```

4. **Commit** updated packs if changes look good

## Troubleshooting

### Authentication Errors

**Error:** `eBay OAuth failed (401)`

**Solution:**
- Verify `EBAY_CLIENT_ID` and `EBAY_CLIENT_SECRET` are correct
- Check that your app has the required scope: `https://api.ebay.com/oauth/api_scope`
- Ensure you're using the correct environment (sandbox vs production)

### Rate Limiting

**Error:** `Failed to get item aspects (429)`

**Solution:**
- The generator includes built-in rate limiting (100ms delay between requests)
- Use `--cache` to avoid re-fetching data
- Consider disabling `--enable-aspects` if not needed

### Empty Categories

**Issue:** Generated pack has 0 categories

**Solution:**
- Check that the marketplace is supported
- Verify you have network connectivity
- Try with `--env sandbox` to test with sandbox data
- Check eBay API status: [eBay Status Page](https://developer.ebay.com/support/api-status)

### Invalid Category Tree ID

**Error:** `Failed to get category tree`

**Solution:**
- eBay may have changed the tree ID for the marketplace
- Clear cache: `rm -rf .cache/ebay`
- Try again

## API Documentation

- [eBay Taxonomy API Overview](https://developer.ebay.com/api-docs/commerce/taxonomy/)
- [getDefaultCategoryTreeId](https://developer.ebay.com/api-docs/commerce/taxonomy/resources/category_tree/methods/getDefaultCategoryTreeId)
- [getCategoryTree](https://developer.ebay.com/api-docs/commerce/taxonomy/resources/category_tree/methods/getCategoryTree)
- [getItemAspectsForCategory](https://developer.ebay.com/api-docs/commerce/taxonomy/resources/category_tree/methods/getItemAspectsForCategory)
- [Supported Marketplaces](https://developer.ebay.com/api-docs/commerce/taxonomy/static/supportedmarketplaces.html)

## Examples

### Generate for All EU Marketplaces

```bash
for marketplace in EBAY_DE EBAY_FR EBAY_IT EBAY_ES EBAY_NL EBAY_BE; do
  npm run gen -- generate \
    --marketplace $marketplace \
    --strategy by-branch \
    --output ../../domainpacks/ebay/${marketplace,,} \
    --cache .cache/ebay
done
```

### Generate with Aspects for Listing Assistance

```bash
npm run gen -- generate \
  --marketplace EBAY_DE \
  --strategy by-branch \
  --enable-aspects \
  --cache .cache/ebay \
  --output ../../domainpacks/ebay/de
```

### Preview Output (Dry Run)

```bash
npm run gen -- generate \
  --marketplace EBAY_NL \
  --strategy by-branch \
  --dry-run
```

## License

UNLICENSED - Internal Scanium tool

## Support

For issues or questions:
- File an issue in the Scanium repository
- Contact the backend team
