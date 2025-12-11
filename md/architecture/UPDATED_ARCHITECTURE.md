***REMOVED*** Scanium — High-Level Architecture

This architecture is designed for **consumer use first**, while remaining **fully extensible** for future **B2B use cases** such as inventory scanning, product cataloging, and enterprise integrations.

---

***REMOVED******REMOVED*** 1. Architecture Overview

Scanium consists of **four main layers**, each responsible for a distinct part of the scanning and intelligence pipeline:

1. **UI Layer**
2. **Scanning Engine**
3. **Domain Intelligence Layer**
4. **Integration Layer**

A cloud intelligence path can optionally supplement the on-device pipeline.

---

***REMOVED******REMOVED*** 2. Layers & Components

---

***REMOVED******REMOVED*** 2.1. UI Layer (Jetpack Compose)

The presentation layer responsible for all user interactions:

- **Camera Screen**
    - Live preview
    - Overlays (circles, labels, attributes)
    - Threshold slider
    - Classification mode toggle (ON-DEVICE / CLOUD)
- **Scanned Items Panel**
    - Shows detected items, category, price estimate, attributes
- **Settings**
    - Classification mode
    - Developer options
- **Debug Views (dev mode only)**
    - ML Kit vs CLIP vs Final category
    - Similarities/confidences

---

***REMOVED******REMOVED*** 2.2. Scanning Engine

Responsible for real-time visual processing, tracking, and classification orchestration.

***REMOVED******REMOVED******REMOVED*** A. Object Detection & Tracking
- **CameraX** for real-time frames.
- **ML Kit Object Detection & Tracking**
    - Produces bounding boxes, tracking IDs.
    - Lightweight, fast, on-device.

***REMOVED******REMOVED******REMOVED*** B. Item Aggregation
- Converts per-frame detections into **stable, unique items**.
- Deduplicates objects during camera movement.
- Emits “confirmed” items to Classification Orchestrator.

***REMOVED******REMOVED******REMOVED*** C. Classification Orchestrator
Central controller for classification logic.

- Receives confirmed items.
- Chooses classification mode:
    - **ON-DEVICE (default)** – CLIP-like model.
    - **CLOUD (optional)** – remote Vision API.
- Runs:
    - Crop extraction
    - Classification (ON-DEVICE or CLOUD)
    - Confidence merging with ML Kit signals
- Caches classification to avoid repetition.
- Outputs semantic labels → Domain Intelligence Layer.

***REMOVED******REMOVED******REMOVED*** D. Attribute Extractors
Modular extraction tools:

- **OCR** (brand text, model identifiers)
- **Barcode/QR** (product codes)
- **Visual heuristics**
    - color
    - aspect ratios
    - shape hints
- Future:
    - On-device quality/condition classifiers
    - Texture/material recognition

---

***REMOVED******REMOVED*** 2.3. Domain Intelligence Layer

This layer makes the app adaptable to different consumer/business scenarios.

***REMOVED******REMOVED******REMOVED*** A. Domain Packs
Configurable intelligence bundles defining:

- Category taxonomy
- CLIP prompts per category
- Attribute schemas (brand, color, size, SKU, etc.)
- Recommended extraction methods for each attribute
- Mapping to internal `ItemCategory` enum

Example Domain Packs:
- `HomeResalePack` (consumer)
- `OfficeInventoryPack` (business)
- `WarehousePack` (enterprise SKU workflows)

***REMOVED******REMOVED******REMOVED*** B. Category Engine
- Takes classification result + Domain Pack rules.
- Performs coarse-to-fine category selection.
- Maps semantic category → app-level `ItemCategory`.

***REMOVED******REMOVED******REMOVED*** C. Attribute Engine
For each classified item:

1. Reads attribute list from Domain Pack.
2. Runs applicable extraction methods:
    - OCR → brand, SKU, text hints
    - Barcode → product ID
    - CLIP → subcategory refinement
    - Cloud → detailed metadata
3. Produces a structured `ItemDetails` object.

***REMOVED******REMOVED******REMOVED*** D. Pricing Engine (consumer-first)
- Uses:
    - `ItemCategory`
    - extracted attributes (such as brand)
- Generates price estimates.
- Future:
    - eBay integration for real market pricing.
    - Business pricing (inventory valuation).

---

***REMOVED******REMOVED*** 2.4. Integration Layer

Handles external interactions depending on user type.

***REMOVED******REMOVED******REMOVED*** A. Consumer Integrations
- **Marketplace APIs**
    - eBay: category mapping & price lookups.
    - Future: Facebook Marketplace, Vinted, etc.

***REMOVED******REMOVED******REMOVED*** B. Business Integrations
- Inventory/ERP systems
- SKU databases
- Custom domain packs fetched from backend
- Batch scanning/export workflows

---

***REMOVED******REMOVED*** 3. Architectural Principles
- Device-first intelligence: works offline, fast, privacy-friendly.
- Cloud-optional enhancements: Businesses or advanced users can enable richer interpretation.
- Config-driven categories & attributes: Domain Packs allow Scanium to adapt to consumer and enterprise use.
- Modular, pluggable classifiers & extractors: Future models can be swapped without rewriting the app.
- One classification per item: Aggregation ensures smooth performance even in cluttered scenes.
- Separation of concerns
  - Dection ≠ Classification
  - Classification ≠ Attributes
  - Attributes ≠ Pricing
  - Domain Packs decide behavior, not hard-coded logic.