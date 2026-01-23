# Golden Tests Contract

This document defines the contract between Scanium and the external dataset repo
`scanium-golden-tests`.

## Dataset root

In the current dataset repo, the dataset root is `tests/golden_images`. All layout requirements
below are relative to the dataset root.

## Required directory layout

```
<dataset_root>/
  by_subtype/
    <subtype_slug>/
      positive/
      negative/
      expected.json
      <image_name>.source.json
```

Each image must have a matching sidecar metadata file:

```
<image_name>.jpg
<image_name>.source.json
```

## Image constraints

- Longest edge <= 512px
- File size <= 150KB
- JPG preferred (PNG allowed if needed)

## Provenance schema (required fields)

Each `*.source.json` file must include:

- `source_url` (string)
- `source_site` (one of: `wikimedia`, `manufacturer_presskit`, `unsplash`, `pexels`, `other`)
- `author_or_uploader` (string, optional)
- `license` (string)
- `license_url` (string)
- `retrieval_date` (YYYY-MM-DD)
- `notes` (string, optional)
- `sha256` (string, computed from the final image)

## Naming conventions

- Subtype slugs: lowercase snake_case (e.g., `electronics_headphones`)
- Image names: lowercase snake_case, no spaces
- Sidecar metadata must match the image basename

### Allowed subtype slugs (current core pack)

- `electronics_headphones`
- `electronics_speaker`
- `clothing_tshirt`

New subtype slugs must align with Scanium taxonomy and be added intentionally in a separate change.

## Core pack vs extended pack

- Core pack: small curated set under `<dataset_root>/by_subtype` used for fast local and CI
  validation.
- Extended pack: larger optional set (future expansion). Not currently wired into Scanium CI.

## Versioning policy

The dataset repo is tagged (e.g., `v0.1.0`). Scanium pins a specific commit hash and updates it
intentionally via PR.
