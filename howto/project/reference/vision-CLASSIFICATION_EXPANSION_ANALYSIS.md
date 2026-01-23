# Scanium Object Classification Expansion Analysis

> **Document Type**: Analysis & Design (No Code Changes)
> **Date**: 2025-01-15
> **Status**: Ready for Review

---

## Table of Contents

1. [Current State Analysis](#1-current-state-analysis)
2. [Expanded "At-Home" Object Scope](#2-expanded-at-home-object-scope)
3. [Subtype-First Strategy](#3-subtype-first-strategy)
4. [Attributes: Direct vs Derived](#4-attributes-direct-vs-derived)
5. [Prioritization Roadmap](#5-prioritization-roadmap)
6. [Validation Strategy](#6-validation-strategy)
7. [Risks & Tradeoffs](#7-risks--tradeoffs)
8. [Summary Decision Matrix](#summary-decision-matrix)

---

## 1) Current State Analysis

### What's Currently Working Well

Scanium has a reasonably solid foundation based on the codebase architecture:

**9 High-Level Categories** (`ItemCategory.kt`):

- FASHION, HOME_GOOD, FOOD, PLACE, PLANT, ELECTRONICS, DOCUMENT, BARCODE, QR_CODE, UNKNOWN

**21 Domain Pack Subtypes** (`home_resale_domain_pack.json`):

| Domain      | Subtypes                                               |
|-------------|--------------------------------------------------------|
| Furniture   | sofa, chair, table, bookshelf                          |
| Electronics | laptop, monitor, tv, phone, tablet, speaker            |
| Clothing    | shoes, jacket, bag                                     |
| Kitchenware | pan, pot, blender                                      |
| Appliances  | microwave, vacuum                                      |
| Other       | action_figure, board_game, plant_indoor, book, bicycle |

**Attribute Extraction Pipeline**:

- OCR: brand, model, year, sku, isbn
- CLIP: color, material, plant_type
- CLOUD: condition
- BARCODE: sku, isbn

### Where the System Likely Fails

#### Problem 1: Coarse Fashion Classification

The `FASHION` category maps to a single label but covers wildly different item types. A t-shirt, a
designer handbag, and sneakers are all "Fashion" - but their resale value, marketplace categories,
and buyer expectations differ enormously.

**Impact**: Users see "Fashion" in their item list when they expect "Sneakers" or "Jacket". This
erodes trust and increases manual editing.

#### Problem 2: Thin Subtype Coverage

The domain pack has only 21 subtypes for the entire home inventory universe. Missing coverage
includes:

- **Fashion**: No t-shirts, hoodies, dresses, jeans, hats, watches, jewelry
- **Electronics**: No headphones, keyboards, mice, gaming consoles, cameras, drones
- **Home**: No lamps, rugs, mirrors, picture frames, bedding, towels
- **Kitchen**: No coffee machines, toasters, mixers, knife sets
- **Cleaning**: Only "vacuum" - no distinction between stick/robot/canister

#### Problem 3: OCR Underutilization

The `LocalVisionExtractor.kt` has sophisticated brand scoring heuristics (ALL CAPS bonus, trademark
symbols, penalty for recycling text), but the system treats OCR as supplementary rather than primary
for many categories.

**Specific issue**: Model codes (e.g., "RTX 4080", "iPhone 15 Pro Max") are often visible but not
parsed into structured data that would dramatically improve search and pricing accuracy.

#### Problem 4: "UNKNOWN" Category Over-Assignment

When ML Kit confidence is below 0.3 (threshold in `DetectionMapping.kt`), items fall to UNKNOWN. The
backend taxonomy (`home-resale.json`) has fallback categories like "decor" (priority: 5) that could
catch these items but apparently aren't being leveraged effectively.

**Likely causes**:

- ML Kit's generic model doesn't recognize home-specific items (decorative objects, organizational
  items)
- Cloud classifier may not have training data for lower-value items
- Fallback categories have low priority scores, so they rarely "win"

#### Problem 5: Missing "Common Home Objects"

The taxonomy is optimized for high-value resale (electronics, furniture) but misses high-frequency,
moderate-value items:

- Storage containers
- Small appliances (iron, hair dryer)
- Cleaning tools (mops, brooms)
- Sporting goods (weights, yoga mats)
- Baby/kids items (strollers, car seats, toys)
- Musical instruments
- Tools (drills, screwdrivers)

These items are commonly found in homes and frequently listed on classifieds platforms.

---

## 2) Expanded "At-Home" Object Scope

### Proposed Coverage by Room/Context

#### LIVING ROOM

| Object Type   | Examples              | Why Common          | Why Valuable            | CV Distinctiveness |
|---------------|-----------------------|---------------------|-------------------------|--------------------|
| Sofa          | ✓ exists              | Universal           | High resale ($100-2000) | High               |
| Armchair      | Missing               | 60%+ homes          | Medium resale ($50-500) | High               |
| Coffee table  | Covered under "table" | Universal           | Medium resale           | High               |
| TV stand      | Missing               | 80%+ homes with TVs | Low-medium              | Medium             |
| Lamp (floor)  | Missing               | 2-5 per home        | Low-medium ($20-200)    | High               |
| Lamp (table)  | Missing               | 3-8 per home        | Low ($10-100)           | High               |
| Rug           | Missing               | 70%+ homes          | Medium ($30-500)        | Medium             |
| Picture frame | Missing               | 5+ per home         | Low ($5-50)             | High               |
| Mirror        | Missing               | 2+ per home         | Medium ($20-300)        | High               |
| Curtains      | Missing               | Every window        | Low ($10-100)           | Medium             |
| Bookshelf     | ✓ exists              | Common              | Medium                  | High               |

#### BEDROOM

| Object Type | Examples | Why Common        | Why Valuable           | CV Distinctiveness     |
|-------------|----------|-------------------|------------------------|------------------------|
| Bed frame   | Missing  | Every bedroom     | High ($100-2000)       | High                   |
| Mattress    | Missing  | Every bedroom     | Medium-high ($50-1000) | Medium (often covered) |
| Nightstand  | Missing  | 80%+ bedrooms     | Low-medium ($20-200)   | High                   |
| Dresser     | Missing  | 70%+ bedrooms     | Medium ($50-500)       | High                   |
| Wardrobe    | Missing  | Common in EU      | Medium-high ($100-800) | High                   |
| Bedding set | Missing  | Multiple per home | Low ($20-150)          | Medium                 |
| Alarm clock | Missing  | Still common      | Very low ($5-30)       | High                   |

#### KITCHEN

| Object Type             | Examples                 | Why Common      | Why Valuable          | CV Distinctiveness |
|-------------------------|--------------------------|-----------------|-----------------------|--------------------|
| Coffee machine          | Missing                  | 70%+ homes      | High ($30-500)        | High               |
| Toaster                 | Missing                  | 60%+ homes      | Low ($10-50)          | High               |
| Kettle                  | Missing                  | 80%+ EU homes   | Low ($10-80)          | High               |
| Mixer/Stand mixer       | Missing                  | 40%+ homes      | Medium-high ($50-400) | High               |
| Air fryer               | Missing                  | Growing rapidly | Medium ($30-200)      | High               |
| Blender                 | ✓ exists                 | 50%+ homes      | Low-medium            | High               |
| Microwave               | ✓ exists                 | 80%+ homes      | Low-medium            | High               |
| Knife set               | Missing                  | Universal       | Medium ($20-300)      | Medium             |
| Pot set                 | Partial (individual pot) | Universal       | Medium                | High               |
| Pan                     | ✓ exists                 | Universal       | Low-medium            | High               |
| Food storage containers | Missing                  | Every kitchen   | Very low ($5-30)      | Medium             |
| Cutting board           | Missing                  | Universal       | Very low              | High               |

#### BATHROOM

| Object Type         | Examples             | Why Common | Why Valuable         | CV Distinctiveness |
|---------------------|----------------------|------------|----------------------|--------------------|
| Hair dryer          | Missing              | 80%+ homes | Low ($10-100)        | High               |
| Hair straightener   | Missing              | 40%+ homes | Low-medium ($20-150) | High               |
| Electric toothbrush | Missing              | 30%+ homes | Low ($10-100)        | High               |
| Scale (body)        | Missing              | 60%+ homes | Low ($10-80)         | High               |
| Towel set           | Missing              | Universal  | Very low ($10-50)    | Medium             |
| Mirror (bathroom)   | Covered under mirror | Universal  | Medium               | High               |

#### HOME OFFICE

| Object Type   | Examples                   | Why Common           | Why Valuable          | CV Distinctiveness |
|---------------|----------------------------|----------------------|-----------------------|--------------------|
| Monitor       | ✓ exists                   | 40%+ homes           | High ($50-800)        | High               |
| Keyboard      | Missing                    | With every PC        | Medium ($10-200)      | High               |
| Mouse         | Missing                    | With every PC        | Low-medium ($5-100)   | High               |
| Webcam        | Missing                    | 30%+ homes post-2020 | Low-medium ($20-150)  | High               |
| Headphones    | Missing                    | 60%+ homes           | Medium-high ($20-400) | High               |
| Speakers      | ✓ exists                   | 50%+ homes           | Medium ($30-300)      | High               |
| Desk          | Covered under "table"      | 40%+ homes           | Medium                | High               |
| Office chair  | Missing (chair is generic) | 40%+ homes           | Medium ($50-500)      | High               |
| Desk lamp     | Covered under lamp?        | Common               | Low                   | High               |
| Monitor stand | Missing                    | Growing              | Low ($20-100)         | High               |
| USB hub       | Missing                    | Very common          | Very low ($5-50)      | Medium             |

#### KIDS ROOM / NURSERY

| Object Type     | Examples                            | Why Common             | Why Valuable          | CV Distinctiveness |
|-----------------|-------------------------------------|------------------------|-----------------------|--------------------|
| Stroller        | Missing                             | Every family with kids | High ($50-1000)       | High               |
| Car seat        | Missing                             | Every family with kids | High ($30-400)        | High               |
| Crib            | Missing                             | Baby essentials        | Medium-high ($50-500) | High               |
| High chair      | Missing                             | Baby essentials        | Medium ($30-200)      | High               |
| Toys (generic)  | Partial (action figure, board game) | Universal              | Low-medium            | Medium             |
| LEGO sets       | Missing                             | Extremely common       | Medium-high ($20-500) | High               |
| Stuffed animals | Missing                             | Universal              | Very low ($5-20)      | Low                |
| Baby monitor    | Missing                             | 80%+ families          | Low-medium ($20-150)  | High               |

#### GARAGE / UTILITY

| Object Type    | Examples           | Why Common          | Why Valuable          | CV Distinctiveness |
|----------------|--------------------|---------------------|-----------------------|--------------------|
| Bicycle        | ✓ exists           | 50%+ homes          | High ($50-2000)       | High               |
| Power drill    | Missing            | 60%+ homes          | Medium ($20-200)      | High               |
| Tool set       | Missing            | 80%+ homes          | Medium ($30-300)      | Medium             |
| Ladder         | Missing            | 50%+ homes          | Low-medium ($30-200)  | High               |
| Lawn mower     | Missing            | 40%+ homes (houses) | Medium-high ($50-500) | High               |
| Vacuum cleaner | ✓ exists (generic) | 95%+ homes          | Medium ($30-400)      | High               |
| Iron           | Missing            | 80%+ homes          | Low ($10-80)          | High               |
| Sewing machine | Missing            | 15%+ homes          | Medium ($30-400)      | High               |

#### SPORTS / FITNESS

| Object Type   | Examples | Why Common | Why Valuable           | CV Distinctiveness |
|---------------|----------|------------|------------------------|--------------------|
| Dumbbells     | Missing  | 20%+ homes | Low-medium ($10-200)   | High               |
| Yoga mat      | Missing  | 30%+ homes | Very low ($10-50)      | High               |
| Exercise bike | Missing  | 10%+ homes | Medium-high ($50-1000) | High               |
| Treadmill     | Missing  | 5%+ homes  | High ($100-2000)       | High               |
| Tennis racket | Missing  | 15%+ homes | Low-medium ($10-200)   | High               |
| Golf clubs    | Missing  | 10%+ homes | Medium-high ($50-1000) | High               |
| Ski equipment | Missing  | Regional   | Medium-high            | High               |

#### ELECTRONICS (Expanded)

| Object Type         | Current Status | Why Critical                      | CV Distinctiveness |
|---------------------|----------------|-----------------------------------|--------------------|
| Laptop              | ✓ exists       | High value                        | High               |
| Smartphone          | ✓ exists       | High value                        | High               |
| Tablet              | ✓ exists       | High value                        | High               |
| Monitor             | ✓ exists       | Medium-high value                 | High               |
| TV                  | ✓ exists       | Medium-high value                 | High               |
| Speaker             | ✓ exists       | Medium value                      | High               |
| **Headphones**      | Missing        | High frequency, medium-high value | High               |
| **Earbuds**         | Missing        | Very high frequency               | High               |
| **Gaming console**  | Missing        | High value ($100-500)             | High               |
| **Game controller** | Missing        | Medium value                      | High               |
| **Smartwatch**      | Missing        | High value ($50-400)              | High               |
| **E-reader**        | Missing        | Medium value ($30-200)            | High               |
| **Drone**           | Missing        | High value ($50-1500)             | High               |
| **Camera (DSLR)**   | Missing        | High value ($100-2000)            | High               |
| **Camera (action)** | Missing        | Medium-high value ($50-400)       | High               |
| **Router**          | Missing        | Low value but common              | High               |
| **Power bank**      | Missing        | Very common                       | Low                |
| **Charger**         | Missing        | Universal                         | Very low           |

#### FASHION (Expanded Subtypes)

| Subtype        | Current Status       | Resale Value          | CV Distinctiveness |
|----------------|----------------------|-----------------------|--------------------|
| Shoes          | ✓ exists             | High                  | High               |
| Jacket         | ✓ exists             | Medium-high           | High               |
| Bag            | ✓ exists             | Medium-high           | High               |
| **T-shirt**    | Missing              | Low ($5-50)           | High               |
| **Hoodie**     | Missing              | Medium ($20-100)      | High               |
| **Jeans**      | Missing              | Medium ($10-80)       | High               |
| **Dress**      | Missing              | Medium ($15-200)      | High               |
| **Sweater**    | Missing              | Medium ($15-100)      | High               |
| **Coat**       | Missing              | Medium-high ($30-500) | High               |
| **Shorts**     | Missing              | Low ($5-40)           | High               |
| **Skirt**      | Missing              | Low-medium ($10-80)   | High               |
| **Hat/Cap**    | Missing              | Low ($5-50)           | High               |
| **Scarf**      | Missing              | Low ($5-50)           | Medium             |
| **Watch**      | Missing              | High ($20-5000)       | High               |
| **Sunglasses** | Missing              | Medium ($10-300)      | High               |
| **Jewelry**    | Missing              | Variable ($10-10000)  | Medium             |
| **Sneakers**   | Covered under shoes? | High ($20-500+)       | High               |
| **Boots**      | Covered under shoes? | Medium-high           | High               |
| **Sandals**    | Covered under shoes? | Low                   | High               |

---

## 3) Subtype-First Strategy

### Why Subtypes > New Categories

Expanding subtypes within existing categories delivers more value than adding new top-level
categories because:

#### 1. Better Item List Labels

Current experience:

```
📱 Electronics  ← User scanned AirPods Pro
📱 Electronics  ← User scanned mechanical keyboard
📱 Electronics  ← User scanned Dell monitor
```

With subtypes:

```
🎧 Headphones   ← Immediately clear
⌨️ Keyboard     ← Immediately clear
🖥️ Monitor      ← Immediately clear
```

**User trust**: When users see accurate labels, they trust the app's intelligence. When they see
generic labels, they assume the app is dumb and manually edit everything.

#### 2. Attribute Prefill Accuracy

Generic "Electronics" can't intelligently prefill attributes:

- Headphones need: driver size, wireless/wired, ANC, connectivity
- Keyboards need: layout, switch type, RGB, connectivity
- Monitors need: size (inches), resolution, refresh rate, panel type

**With subtypes**, the domain pack can define category-specific attribute schemas that make sense.

#### 3. Marketplace Export Alignment

eBay, Facebook Marketplace, and classifieds sites have category taxonomies. "Electronics" maps
poorly:

| Scanium Category      | eBay Category         | Facebook Category        |
|-----------------------|-----------------------|--------------------------|
| Electronics (generic) | ???                   | ???                      |
| Headphones            | Sound > Headphones    | Electronics > Headphones |
| Keyboard              | Computers > Keyboards | Electronics > Computers  |
| Monitor               | Computers > Monitors  | Electronics > Computers  |

**Subtype-first** enables direct mapping to marketplace categories, reducing user friction during
export.

#### 4. Pricing Accuracy

The `PricingEngine.kt` generates price ranges by category. Generic categories produce useless
ranges:

- "Electronics": €10-€2000 (unhelpful)
- "Headphones": €15-€400 (actionable)
- "Gaming Console": €100-€600 (actionable)

#### 5. Search and Filtering

If users can filter by subtype ("Show me all my headphones"), they can quickly find items. Generic
categories make this impossible.

### Concrete Expansion Examples

#### Fashion → Subtypes

```
FASHION (current)
├── clothing_shoes (exists)
├── clothing_jacket (exists)
├── clothing_bag (exists)
│
├── clothing_tshirt (ADD)
├── clothing_hoodie (ADD)
├── clothing_jeans (ADD)
├── clothing_dress (ADD)
├── clothing_sweater (ADD)
├── clothing_coat (ADD)
├── clothing_sneakers (ADD - distinct from generic shoes)
├── clothing_watch (ADD)
├── clothing_sunglasses (ADD)
```

#### Electronics → Subtypes

```
ELECTRONICS (current)
├── electronics_laptop (exists)
├── electronics_monitor (exists)
├── electronics_tv (exists)
├── electronics_phone (exists)
├── electronics_tablet (exists)
├── electronics_speaker (exists)
│
├── electronics_headphones (ADD)
├── electronics_earbuds (ADD)
├── electronics_keyboard (ADD)
├── electronics_mouse (ADD)
├── electronics_webcam (ADD)
├── electronics_gaming_console (ADD)
├── electronics_controller (ADD)
├── electronics_smartwatch (ADD)
├── electronics_camera_dslr (ADD)
├── electronics_drone (ADD)
├── electronics_ereader (ADD)
```

#### HOME_GOOD → Subtypes

```
HOME_GOOD (current)
├── furniture_sofa (exists)
├── furniture_chair (exists)
├── furniture_table (exists)
├── furniture_bookshelf (exists)
├── appliance_microwave (exists)
├── appliance_vacuum (exists)
├── kitchenware_pan (exists)
├── kitchenware_pot (exists)
├── kitchenware_blender (exists)
│
├── appliance_coffee_machine (ADD)
├── appliance_air_fryer (ADD)
├── appliance_toaster (ADD)
├── appliance_kettle (ADD)
├── appliance_hair_dryer (ADD)
├── appliance_iron (ADD)
├── cleaning_robot_vacuum (ADD - distinct from generic vacuum)
├── cleaning_stick_vacuum (ADD)
├── furniture_desk (ADD - distinct from table)
├── furniture_office_chair (ADD)
├── furniture_bed_frame (ADD)
├── furniture_nightstand (ADD)
├── decor_lamp_floor (ADD)
├── decor_lamp_table (ADD)
├── decor_mirror (ADD)
├── decor_rug (ADD)
```

---

## 4) Attributes: Direct vs Derived

### A) Direct Attributes (Extractable from Images)

| Attribute                        | Extraction Method           | Current Status | Accuracy Level        | Cost                     |
|----------------------------------|-----------------------------|----------------|-----------------------|--------------------------|
| **brand**                        | Logo detection, OCR         | ✓ Implemented  | High for major brands | Low (on-device possible) |
| **model**                        | OCR (model codes)           | ✓ Implemented  | Medium                | Low                      |
| **color**                        | Palette analysis, CLIP      | ✓ Implemented  | High                  | Very low (on-device)     |
| **secondary_color**              | Palette analysis            | ✓ Implemented  | Medium                | Very low                 |
| **visible_text**                 | OCR                         | ✓ Implemented  | High                  | Low                      |
| **size_marking**                 | OCR (e.g., "XL", "42")      | Partial        | Medium                | Low                      |
| **barcode_value**                | Barcode scanning            | ✓ Implemented  | Very high             | Very low                 |
| **logo_candidates**              | Logo detection              | ✓ Implemented  | Medium-high           | Medium (cloud)           |
| **dominant_material_appearance** | CLIP                        | ✓ Implemented  | Low-medium            | Medium                   |
| **product_line_text**            | OCR (e.g., "Pro", "Ultra")  | Partial        | Medium                | Low                      |
| **year_marking**                 | OCR (e.g., "2023", "©2024") | Partial        | Medium                | Low                      |

### B) Derived Attributes (Computed from A + Heuristics/LLM)

| Attribute             | Derivation Method              | LLM Required? | Confidence Risk | Implementation Notes                                               |
|-----------------------|--------------------------------|---------------|-----------------|--------------------------------------------------------------------|
| **condition**         | Currently CLOUD                | Yes (likely)  | Medium          | Could use heuristic rules for obvious cases (scratches, packaging) |
| **gender**            | Heuristic from subtype + color | No            | Medium          | Pink dress → Women's; "Men's" in OCR → Men's                       |
| **age_group**         | Heuristic from size + context  | No            | High            | "Kids" in OCR, small sizes, cartoon patterns                       |
| **material**          | CLIP + heuristic               | Partial       | Medium          | "Leather" detectable, specific fabrics harder                      |
| **size_category**     | OCR + heuristic                | No            | Medium          | "XL" → size, but EU vs US sizing is complex                        |
| **connectivity**      | OCR + heuristic                | No            | Low             | "Bluetooth", "WiFi", "USB-C" in OCR                                |
| **compatibility**     | LLM + product database         | Yes           | High            | "Works with iPhone 15" requires reasoning                          |
| **use_case**          | LLM from context               | Yes           | High            | "Gaming keyboard" vs "office keyboard"                             |
| **estimated_msrp**    | Product database lookup        | No            | Medium          | Requires barcode/model match                                       |
| **release_year**      | Product database lookup        | No            | Medium          | Requires model identification                                      |
| **authenticity_risk** | Heuristic from brand + signals | Partial       | Very high       | Luxury brands with low price = warning                             |

### Derivation Details

**Condition (Current: CLOUD)**

- Current: Likely uses LLM to assess wear, damage, packaging presence
- Alternative: Rule-based for obvious cases:
    - Sealed packaging detected → "New"
    - No visible damage + clean → "Like New" / "Good"
    - Visible scratches/wear → "Used" / "Fair"
- Risk: Over-optimistic condition estimates hurt seller reputation

**Gender (Proposed: HEURISTIC)**

```python
if "Men's" in ocr_text or "Homme" in ocr_text:
    return "Men"
if "Women's" in ocr_text or "Femme" in ocr_text:
    return "Women"
if subtype in ["dress", "skirt", "bra"]:
    return "Women"
if color == "pink" and subtype in ["tshirt", "hoodie"]:
    confidence = 0.6  # Weak signal
```

- No LLM required
- Risk: Gender assumptions from color are culturally biased

**Material (Current: CLIP)**

- CLIP can detect visual texture (leather, fabric, metal, plastic, wood)
- OCR can confirm ("100% Cotton", "Genuine Leather")
- LLM useful for ambiguous cases
- Risk: Faux leather vs real leather is very hard visually

**Compatibility (Would Require: LLM)**

- Example: "MagSafe" → "Compatible with iPhone 12+"
- Example: "Type-C" → "Compatible with USB-C devices"
- Requires product knowledge reasoning
- Risk: Compatibility claims can be wrong and frustrate buyers

---

## 5) Prioritization Roadmap

### Top 5 Object Expansions (Highest ROI)

#### 1. Headphones / Earbuds (`electronics_headphones`, `electronics_earbuds`)

**Why first**:

- Extremely high frequency in homes (60%+ households)
- High resale value ($20-400)
- Very visually distinct (easy for CV)
- Strong brand signals (Apple, Sony, Bose, Beats visible)
- Clean marketplace mapping (dedicated categories everywhere)

**Expected impact**: 10-15% reduction in "Electronics" generic labels

#### 2. Fashion Subtypes: T-Shirt, Hoodie, Jeans (`clothing_tshirt`, `clothing_hoodie`,
`clothing_jeans`)

**Why second**:

- Highest volume category for resale apps
- Currently "Fashion" is nearly useless as a label
- Very visually distinct from each other
- Brand/size often visible on labels
- Direct marketplace category mapping

**Expected impact**: 40-50% reduction in generic "Fashion" labels

#### 3. Keyboard + Mouse (`electronics_keyboard`, `electronics_mouse`)

**Why third**:

- Universal with every computer setup
- Medium-high resale value for quality items
- Extremely visually distinct
- Brand clearly visible (Logitech, Razer, Apple logos)
- OCR can extract model codes

**Expected impact**: 5-8% reduction in generic "Electronics" labels

#### 4. Coffee Machine + Air Fryer (`appliance_coffee_machine`, `appliance_air_fryer`)

**Why fourth**:

- Coffee machines in 70%+ homes, air fryers rapidly growing
- High resale value ($30-500)
- Visually distinct silhouettes
- Brand text prominent (Nespresso, DeLonghi, Philips, Ninja)
- Strong user intent (these are items people actively list)

**Expected impact**: 5-10% reduction in generic "Home Good" labels, significant improvement in
attribute prefill

#### 5. Robot Vacuum vs Stick Vacuum (`cleaning_robot_vacuum`, `cleaning_stick_vacuum`)

**Why fifth**:

- Currently one generic "vacuum" category
- Robot vacuums have high resale value ($100-800)
- Completely different form factors (easy CV)
- Strong brands (iRobot, Roborock, Dyson)
- Demonstrates "subtype differentiation" value

**Expected impact**: Better pricing, better marketplace mapping for cleaning appliances

### What NOT to Do Yet (And Why)

#### ❌ Jewelry / Watches (Too Complex)

- Extremely high variance in value ($10 to $100,000+)
- Authenticity verification is critical and hard
- Requires gemological knowledge
- False positives have serious trust implications
- **Wait until**: Core categories are solid, consider specialized mode later

#### ❌ Art / Antiques (Out of Scope)

- No standardized taxonomy
- Value is entirely subjective
- Requires expert appraisal
- Not a "home inventory" item in the resale sense
- **Recommendation**: Explicitly exclude or mark as "requires manual entry"

#### ❌ Food Items (Low Value, High Complexity)

- Already have FOOD category
- Per-item value is very low
- Expiration dates matter but hard to extract
- Not typical resale app use case
- **Recommendation**: Keep as-is, don't expand

#### ❌ Musical Instruments (Niche)

- Only 10-15% of homes have them
- High value when present, but low frequency
- Requires specialized knowledge (guitar types, piano brands)
- **Wait until**: Core categories are mature

#### ❌ Firearms / Weapons (Legal/Safety)

- Extreme legal complexity across jurisdictions
- Liability concerns
- Most marketplaces prohibit
- **Recommendation**: Explicitly unsupported, detect and warn

#### ❌ Live Animals / Plants (Already Have PLANT)

- PLANT category exists
- Animal listing is legally complex
- Not a resale app use case
- **Recommendation**: Keep PLANT as-is

---

## 6) Validation Strategy

### Golden Images Strategy

#### Dataset Structure

```
golden_images/
├── electronics_headphones/
│   ├── positive/           # Items that ARE headphones
│   │   ├── over_ear/       # 20 images
│   │   ├── on_ear/         # 20 images
│   │   ├── earbuds/        # 20 images (to test distinction)
│   │   └── with_brands/    # 20 images (Sony, Bose, Beats, etc.)
│   └── negative/           # Items that are NOT headphones
│       ├── speakers/       # 10 images (common confusion)
│       ├── ear_protection/ # 10 images (industrial, not audio)
│       └── stethoscopes/   # 5 images (shape similarity)
├── clothing_tshirt/
│   ├── positive/
│   │   ├── plain/          # 20 images
│   │   ├── graphic/        # 20 images
│   │   ├── with_visible_brand/  # 15 images
│   │   └── folded/         # 10 images (different presentation)
│   └── negative/
│       ├── polo_shirts/    # 10 images (collar distinction)
│       ├── tank_tops/      # 10 images
│       └── long_sleeves/   # 10 images
...
```

#### Sample Counts Per Subtype

| Priority | Subtype                  | Positive Samples | Negative Samples | Total |
|----------|--------------------------|------------------|------------------|-------|
| 1        | electronics_headphones   | 80               | 25               | 105   |
| 1        | electronics_earbuds      | 60               | 20               | 80    |
| 2        | clothing_tshirt          | 65               | 30               | 95    |
| 2        | clothing_hoodie          | 50               | 20               | 70    |
| 2        | clothing_jeans           | 50               | 20               | 70    |
| 3        | electronics_keyboard     | 60               | 20               | 80    |
| 3        | electronics_mouse        | 50               | 15               | 65    |
| 4        | appliance_coffee_machine | 50               | 15               | 65    |
| 4        | appliance_air_fryer      | 40               | 15               | 55    |
| 5        | cleaning_robot_vacuum    | 40               | 15               | 55    |
| 5        | cleaning_stick_vacuum    | 40               | 15               | 55    |

**Total minimum**: ~800 curated images for top 5 expansions

#### Image Sourcing

- Internal test devices photographed in varied conditions
- User-submitted images (anonymized, with consent)
- Marketplace listings (public domain fair use for ML testing)
- Stock photo datasets with appropriate licensing

### Success Metrics

#### Classification Accuracy

| Metric                                 | Target | Measurement              |
|----------------------------------------|--------|--------------------------|
| **Precision** (correct when predicted) | ≥ 90%  | TP / (TP + FP)           |
| **Recall** (found when present)        | ≥ 85%  | TP / (TP + FN)           |
| **F1 Score**                           | ≥ 0.87 | 2 × (P × R) / (P + R)    |
| **UNKNOWN rate**                       | ≤ 10%  | Items falling to UNKNOWN |

#### Attribute Accuracy

| Metric                              | Target | Measurement                         |
|-------------------------------------|--------|-------------------------------------|
| **Brand extraction** (when visible) | ≥ 80%  | Correct brand / visible logos       |
| **Color accuracy**                  | ≥ 95%  | Correct primary color / total       |
| **Model code extraction**           | ≥ 70%  | Correct model / visible model codes |

#### User-Facing Metrics

| Metric                  | Target | Measurement                             |
|-------------------------|--------|-----------------------------------------|
| **Manual edit rate**    | ≤ 30%  | Users editing category/type             |
| **Time to list**        | -20%   | Average time from scan to export        |
| **Export success rate** | ≥ 95%  | Exports with valid marketplace category |

### Regression Detection

#### Automated Tests

- Run golden image suite on every model/taxonomy change
- Alert if any subtype drops below 85% recall
- Alert if UNKNOWN rate increases by >2%
- Alert if any existing category regresses by >3%

#### A/B Testing (Production)

- Ship to 5% of users first
- Monitor manual edit rate vs control
- Monitor "report incorrect" feedback
- Monitor export completion rate

---

## 7) Risks & Tradeoffs

### False Positives vs False Negatives

**False Positive** (Says "Headphones" when it's not):

- User sees wrong label, edits it, mild annoyance
- If exported to marketplace with wrong category, listing may be rejected
- Erodes trust in "smart" features
- **Mitigation**: Higher confidence threshold (0.5+) for new subtypes initially

**False Negative** (Says "Electronics" when it's headphones):

- User sees generic label, no worse than today
- User manually selects correct category
- Missed opportunity for attribute prefill
- **Mitigation**: Acceptable during rollout, improve over time

**Recommendation**: Bias toward false negatives initially. Generic labels are annoying but not
harmful. Wrong specific labels damage trust.

### OCR Over-Reliance Risks

**Risk 1: OCR Hallucination**

- Partial text recognition: "ASUS" → "SUS", "Razer" → "Razr"
- Solution: Require high OCR confidence + pattern matching against known brands

**Risk 2: Misleading Text**

- T-shirt with "NIKE" graphic but not Nike brand
- Counterfeit items with brand text
- Solution: Cross-validate OCR brand with logo detection; flag discrepancies

**Risk 3: Non-Brand Text Promoted**

- "100% COTTON" extracted as brand
- "MADE IN CHINA" extracted as brand
- Solution: Already implemented in `LocalVisionExtractor.kt` with penalty scores for such patterns.
  Maintain and expand blocklist.

**Risk 4: Model Code Parsing Errors**

- "RTX4080" vs "RTX 4080" vs "RTX4080TI"
- "iPhone15Pro" vs "iPhone 15 Pro Max"
- Solution: Fuzzy matching, canonical model database lookup

### Brand Hallucination Risks

**Risk**: LLM or rules infer brand from context that isn't there

- Example: Blue headphones → "Beats" (because Beats has blue models)
- Example: Round robot vacuum → "Roomba" (when it's actually Roborock)

**Impact**:

- User corrects, loses trust
- Exported listing has wrong brand, could be flagged as deceptive

**Mitigation**:

1. Only assert brand when logo OR OCR confirms it
2. Use "suggested brand" with low confidence when inferred
3. Never auto-fill brand for marketplace export without user confirmation
4. Track brand accuracy as explicit metric

### User Trust Implications

**Trust Builders**:

- Correct subtype labels ("This app knows it's a hoodie!")
- Accurate brand detection ("It found the Nike logo!")
- Good price estimates ("That's actually about right")
- Seamless marketplace export ("The eBay category was perfect")

**Trust Destroyers**:

- Wrong subtype labels that require correction
- Confident wrong brand ("Why does it say Adidas? This is clearly Nike")
- Hallucinated attributes ("It says leather but it's fabric")
- Rejected marketplace listings due to category mismatch

**Trust Neutral**:

- Generic labels ("Electronics" is boring but not wrong)
- Missing attributes ("No brand detected" is honest)
- UNKNOWN category for genuinely ambiguous items

**Recommendation**: Ship new subtypes with honest confidence indicators. "Headphones (High
confidence)" vs "Electronics (Low confidence)" communicates the system's limitations transparently.

### Cost Explosion Risks

**Scenario 1: Per-Image LLM Calls**

- Current architecture already supports cloud classification
- Adding more subtypes doesn't inherently increase cost
- **Risk**: If LLM is called for every new attribute (gender, compatibility, use-case)
- **Mitigation**: Keep LLM calls optional (user-triggered "Enrich with AI"), expand
  rule-based/heuristic first

**Scenario 2: Larger Model Needed**

- More subtypes might require more sophisticated classifier
- Could increase cloud classification latency/cost
- **Mitigation**: Hierarchical classification (coarse on-device → fine cloud) already in place

**Scenario 3: OCR/Vision API Scaling**

- More reliance on OCR for model codes, sizes
- Already using ML Kit on-device for OCR, low marginal cost
- Cloud vision insights already batched efficiently
- **Mitigation**: Continue on-device-first strategy

**Cost-Conscious Expansion Strategy**:

1. Expand domain pack subtypes (configuration change, zero inference cost)
2. Train/fine-tune existing classifier to recognize new subtypes (one-time cost)
3. Add attribute extraction rules (heuristic, zero inference cost)
4. Reserve LLM for user-requested enrichment only

---

## Summary Decision Matrix

| Decision                                                                                  | Recommendation   | Confidence | Risk Level   |
|-------------------------------------------------------------------------------------------|------------------|------------|--------------|
| Expand subtypes before categories                                                         | **Yes**          | Very High  | Low          |
| Start with top 5 (headphones, fashion subtypes, keyboard/mouse, coffee/airfryer, vacuums) | **Yes**          | High       | Low          |
| Add jewelry/watches now                                                                   | **No**           | High       | High if done |
| Rely more on OCR for model codes                                                          | **Yes**          | Medium     | Medium       |
| Add derived gender attribute                                                              | **Cautious Yes** | Medium     | Medium       |
| Add derived compatibility attribute                                                       | **No**           | Low        | High         |
| Ship with confidence indicators                                                           | **Yes**          | Very High  | Very Low     |
| Use LLM for all new attributes                                                            | **No**           | High       | High if done |
| Golden image validation requirement                                                       | **Yes**          | Very High  | None         |
| A/B test before full rollout                                                              | **Yes**          | Very High  | None         |

---

## Appendix: Key File References

| Component              | File Path                                                                                           |
|------------------------|-----------------------------------------------------------------------------------------------------|
| ItemCategory enum      | `shared/core-models/src/commonMain/kotlin/com/scanium/shared/core/models/ml/ItemCategory.kt`        |
| Domain Pack            | `core-domainpack/src/main/res/raw/home_resale_domain_pack.json`                                     |
| Backend Taxonomy       | `backend/src/modules/classifier/domain/home-resale.json`                                            |
| Detection Mapping      | `androidApp/src/main/java/com/scanium/app/ml/detector/DetectionMapping.kt`                          |
| Local Vision Extractor | `androidApp/src/main/java/com/scanium/app/ml/LocalVisionExtractor.kt`                               |
| Cloud Classifier       | `androidApp/src/main/java/com/scanium/app/ml/classification/CloudClassifier.kt`                     |
| Vision Attributes      | `shared/core-models/src/commonMain/kotlin/com/scanium/shared/core/models/items/VisionAttributes.kt` |
| Pricing Engine         | `androidApp/src/main/java/com/scanium/app/ml/PricingEngine.kt`                                      |
| Export Profiles        | `shared/core-models/src/commonMain/kotlin/com/scanium/shared/core/models/listing/ExportProfiles.kt` |

---

*This analysis is based on review of the actual Scanium codebase architecture. Strategic
recommendations are assessments based on typical resale app patterns and the architecture observed.*
