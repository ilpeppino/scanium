# Verifiable Pricing Assistant — Architecture & UX

## Status
Planned feature

## Owner
Scanium Product / Engineering

## Motivation

User feedback shows Scanium is currently perceived as a “nice-to-have” catalog app rather than a must-have resale tool.
The primary missing value is **trustworthy pricing**.

This feature introduces a **Verifiable Pricing Assistant** that:
- avoids premature AI commitment
- uses AI only where it adds value (normalization, not guessing)
- produces explainable, verifiable price ranges based on real marketplace data

---

## Core Principles

1. AI must NEVER invent prices
2. AI must NEVER invent sources or URLs
3. Prices must be explainable and traceable to real marketplaces
4. Pricing intent must be explicit (user-triggered)
5. Pricing attributes are collected progressively, not upfront

---

## UX Design Overview

### Default Edit Item Screen (No pricing intent)

The Edit Item screen remains lightweight and non-committal.

Visible fields:
- Photos
- Product / Type
- Brand
- Model (free text)
- Condition
- Notes

Hidden:
- Variant specifications
- Completeness / included items
- Identifiers (EAN / UPC / ISBN)

Rationale:
Avoid premature commitment and wrong assumptions before the user explicitly asks for pricing.

---

### Pricing Intent Trigger

Pricing mode is entered **only** when the user taps the ✨ AI button.

This opens the **Pricing Assistant Sheet**.

---

## Pricing Assistant Sheet (Progressive Disclosure)

### Step 1 — Context & Trust

Explain why additional data is needed:

> “To calculate a realistic resale price based on real marketplace data, I need a few more details.”

---

### Step 2 — Pricing-Critical Attributes (Dynamic)

Fields shown depend on Product / Type.

#### Variant schemas (examples)

**Laptop**
- Storage (dropdown)
- RAM (dropdown)
- Screen size (dropdown)
- Year (auto-filled, editable)

**Smartphone**
- Storage
- Network (4G / 5G)
- Dual SIM
- Year

**Console**
- Storage
- Edition (Standard / Slim / Pro / OLED)
- Generation

**Clothing**
- Size
- Gender
- Fit

**Toys / Collectibles**
- Edition
- Series
- Age range

---

### Step 3 — Completeness

Multi-select:
- Charger included
- Original box
- Accessories
- Missing parts

Default: unknown

---

### Step 4 — Identifier (Optional)

Only shown if detected:
- EAN / UPC / ISBN / MPN

---

### Step 5 — Pricing Snapshot Confirmation

Once confirmed:
- pricing attributes are frozen
- request becomes deterministic
- pricing can be explained

CTA:
> Calculate verifiable price

---

## Backend Architecture

### Data Flow

1. Fetch marketplace listings (NL first)
   - Marktplaats
   - eBay
2. Apply deterministic filters
   - exclude damaged / parts-only listings
3. AI-assisted normalization
   - normalize model names
   - tag condition & completeness
   - exclude unusable listings
4. Statistical aggregation
   - median
   - p25 / p75
   - outlier removal
5. Response with verifiable sources

---

## AI Usage (Strictly Scoped)

AI responsibilities:
- normalize titles and specs
- cluster listings
- tag completeness / exclusions

AI must NOT:
- invent prices
- invent URLs
- invent marketplaces

---

## Pricing Output Requirements

Response must include:
- price range (min / median / max or p25 / p75)
- sample size
- time window
- marketplaces used (name + base URL)
- exclusion notes (if applicable)

---

## Deployment Notes

Backend is Docker-based and deployed on NAS.

If backend changes are required:
- connect via `ssh nas`
- rebuild using `docker` / `docker-compose`
- ensure no repo divergence:
  - Mac: clean tree, push branch
  - NAS: git fetch --all --prune
  - git pull --ff-only
  - verify commit SHAs match

---

## Acceptance Criteria

- Pricing is only computed after explicit user action
- Price ranges are verifiable
- Users understand why prices differ
- AI cost remains low and cacheable