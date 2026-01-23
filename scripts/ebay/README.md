# eBay Domain Pack Generator

This directory contains Python scripts for generating eBay-derived candidate domain packs for
Scanium.

## Overview

The eBay domain pack generator produces **candidate** domain packs from eBay UK (EBAY_GB) taxonomy
and listing data. All outputs are **read-only** - they do NOT modify Scanium's localization files or
existing domain pack configurations.

**Key Principle:** This is a data generation tool, not a deployment tool. All outputs must be
manually reviewed and curated before integration into the app.

## Prerequisites

- Python 3.8+
- eBay Developer Account (for real API usage, optional for mock data)

## eBay API Credentials (Optional)

To fetch real data from eBay (instead of mock data), set these environment variables:

```bash
export EBAY_CLIENT_ID="your_app_key"
export EBAY_CLIENT_SECRET="your_app_secret"
```

Get these from https://developer.ebay.com/

## Scripts

### 1. `extract_subtypes.py`

Extracts Scanium subtypes from `home_resale_domain_pack.json`.

**Input:** `core-domainpack/src/main/res/raw/home_resale_domain_pack.json`
**Output:** `scripts/output/ebay/scanium_subtypes_all.json`

```bash
python3 scripts/ebay/extract_subtypes.py
```

### 2. `fetch_taxonomy.py`

Fetches eBay UK category tree structure.

**Input:** eBay Taxonomy API (or mock data if credentials not configured)
**Output:** `scripts/output/ebay/uk/ebay_category_tree.json`

```bash
python3 scripts/ebay/fetch_taxonomy.py
```

**Note:** Uses mock data by default. With eBay credentials, will fetch real data.

### 3. `flatten_taxonomy.py`

Flattens hierarchical eBay category tree into flat list with full paths.

**Input:** `scripts/output/ebay/uk/ebay_category_tree.json`
**Output:** `scripts/output/ebay/uk/ebay_flat_categories.json`

```bash
python3 scripts/ebay/flatten_taxonomy.py
```

### 4. `map_to_scanium.py`

Maps eBay categories to Scanium subtypes using rule-based token matching + synonyms.

**Inputs:**

- `scripts/output/ebay/uk/ebay_flat_categories.json`
- `scripts/output/ebay/scanium_subtypes_all.json`

**Outputs:**

- `scripts/output/ebay/uk/mapping_suggestions.json` (mapped categories with confidence)
- `scripts/output/ebay/uk/unmapped_categories.json` (categories not mapped)

```bash
python3 scripts/ebay/map_to_scanium.py
```

### 5. `generate_strings_pack.py`

Generates English-only strings catalog for mapped eBay categories.

**Inputs:** `scripts/output/ebay/uk/mapping_suggestions.json`
**Output:** `scripts/output/ebay/uk/strings_keys_catalog.ebay_uk.en.json`

```bash
python3 scripts/ebay/generate_strings_pack.py
```

**Important:** This is a **candidate pack only**. It is NOT wired into the app and localization
files remain untouched.

### 6. `fetch_brand_candidates.py`

Generates brand candidate lists from eBay listing data (or mock data).

**Input:** `scripts/output/ebay/uk/mapping_suggestions.json`
**Outputs:**

- `scripts/output/ebay/uk/brand_candidates_by_subtype.json` (brands ranked by frequency)
- `scripts/output/ebay/uk/brand_frequency_report.md` (human-readable frequency report)

```bash
python3 scripts/ebay/fetch_brand_candidates.py
```

## Running the Full Pipeline

Execute all scripts in sequence:

```bash
python3 scripts/ebay/extract_subtypes.py && \
python3 scripts/ebay/fetch_taxonomy.py && \
python3 scripts/ebay/flatten_taxonomy.py && \
python3 scripts/ebay/map_to_scanium.py && \
python3 scripts/ebay/generate_strings_pack.py && \
python3 scripts/ebay/fetch_brand_candidates.py
```

Or run the one-liner:

```bash
./scripts/ebay/run_all.sh  # (if created)
```

## Output Files

All outputs are written to `scripts/output/ebay/uk/`:

| File                                   | Purpose                   | Format   |
|----------------------------------------|---------------------------|----------|
| `scanium_subtypes_all.json`            | All Scanium subtypes      | JSON     |
| `ebay_category_tree.json`              | eBay category hierarchy   | JSON     |
| `ebay_flat_categories.json`            | Flattened eBay categories | JSON     |
| `mapping_suggestions.json`             | eBay → Scanium mappings   | JSON     |
| `unmapped_categories.json`             | Unmapped eBay categories  | JSON     |
| `strings_keys_catalog.ebay_uk.en.json` | Candidate EN strings      | JSON     |
| `brand_candidates_by_subtype.json`     | Brands by subtype         | JSON     |
| `brand_frequency_report.md`            | Brand frequency analysis  | Markdown |

## Localization Guardrails

All outputs are protected by `scripts/checks/verify_no_localization_changes.sh`. This script
verifies:

✅ **Allowed modifications:**

- `scripts/ebay/**` (this directory)
- `scripts/output/ebay/**` (output files)
- `scripts/checks/**` (guardrail scripts)

❌ **Forbidden modifications:**

- `core-domainpack/at_home_inventory/*.json` (localized domain packs)
- `androidApp/src/main/res/values-*/strings.xml` (Android localization)

**Run the guardrail check before committing:**

```bash
bash scripts/checks/verify_no_localization_changes.sh
```

## Next Steps: Integration

Once candidate files are generated, the manual next steps are:

1. **Review mappings:** Check `mapping_suggestions.json` for accuracy
2. **Curate strings:** Filter/edit `strings_keys_catalog.ebay_uk.en.json` for production
3. **Validate brands:** Review `brand_frequency_report.md` for brand relevance
4. **Integrate:** Move approved files to production catalogs (manual step, not automated)

## Real API Integration

Currently, the scripts use mock data for demonstration. To integrate real eBay API:

1. **Auth API:** Implement OAuth token fetching in `fetch_taxonomy.py`
2. **Taxonomy API:** Call eBay Category Tree API
3. **Browse API:** Call eBay Search/Browse API for brand extraction
4. **Caching:** Consider caching API responses to avoid rate limits

See eBay developer docs:

- https://developer.ebay.com/api-docs/static/categories-api.html
- https://developer.ebay.com/api-docs/static/browse-api.html

## Troubleshooting

**Q: Mock data is being used instead of real eBay data**
A: Set `EBAY_CLIENT_ID` and `EBAY_CLIENT_SECRET` environment variables.

**Q: "Input file not found" error**
A: Run the scripts in order. Each script depends on previous outputs.

**Q: Mapping confidence is always low**
A: Adjust synonym mappings in `map_to_scanium.py` if needed.

## Files Not Modified

✅ The following files are **NEVER** modified by this tool:

- Android localization files
- Domain pack localization catalogs
- Existing brand catalogs
- App UI or runtime code

All outputs are **candidates only** and require manual integration.
