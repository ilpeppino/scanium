Here’s a concrete way to shape Scanium so it works great for consumers now, but is clearly extensible to B2B later.

⸻

1. High-level architecture for Scanium

Think of Scanium as 4 main layers running on the device, plus optional cloud:
1.	UI Layer
2.	Scanning Engine
3.	Domain Intelligence (categories + attributes)
4.	Integration Layer (pricing / business systems)

1.1. Component overview

A. UI Layer (Jetpack Compose)
•	Camera screen (preview, overlays, threshold slider, mode toggles).
•	Items list (scanned items with category, price, confidence).
•	Settings (ON-DEVICE vs CLOUD, debug toggles).
•	Future: business views (bulk scan mode, export to CSV/ERP, etc.).

⸻

B. Scanning Engine

This is the always-on “engine” behind the UI.
1.	Camera & Detection
•	CameraX + ML Kit Object Detection & Tracking.
•	Outputs: bounding boxes, tracking IDs, coarse ML Kit labels (optional).
2.	Aggregation & Tracking (already in place)
•	Aggregates frame-level detections into stable Items (ScannedItem / AggregatedItem).
•	Ensures you classify once per physical object, not per frame.
3.	Classification Orchestrator
•	Receives “confirmed” items from the aggregation layer.
•	Decides when & how to classify:
•	ON-DEVICE CLIP model (default).
•	CLOUD classifier (optional, if user enabled & network OK).
•	Does:
•	Item crop preparation (from latest frame + bounding box).
•	Dispatch to the correct classifier.
•	Caching (each item classified once per session).
•	Merges:
•	ML Kit label
•	CLIP category
•	(optional) CLOUD result
→ final semantic label + confidence.
4.	Attribute Extractors
•	Pluggable extractors that run on specific items:
•	OCR / barcode (ML Kit Text + Barcode).
•	Simple visual heuristics (color, aspect ratio).
•	Future: on-device models for condition, material, etc.
•	Attribute extractors are configured by the Domain Layer (see below),
not hard-coded to “TV” or “plant”.

⸻

C. Domain Intelligence Layer

This is what makes Scanium adaptable to consumer vs business use.
1.	Domain Packs
•	Config objects that define:
•	Category taxonomy (tree/hierarchy).
•	Text prompts for CLIP per category (for zero-shot classification).
•	Attributes per category (e.g. brand, size, color, SKU, material).
•	Extraction methods per attribute (OCR, CLIP, barcode, heuristic, cloud).
•	Examples:
•	HomeResalePack
•	OfficeInventoryPack
•	WarehousePack
•	Loaded at startup (local JSON initially, future: from backend).
2.	Category Engine
•	Receives CLIP/CLOUD labels and similarities.
•	Uses the active Domain Pack to:
•	Find best matching category.
•	Map to internal ItemCategory (your existing enum) for pricing.
•	Can do coarse → fine:
•	Coarse categories first (electronics / furniture / clothing / tools / etc.).
•	Then fine-level categories within that group.
3.	Attribute Engine
•	For each item:
•	Looks up its category in the Domain Pack.
•	Reads attribute definitions:
•	Name (brand, color, material, sku, etc.).
•	Extraction strategy (OCR, barcode, CLIP, cloud, or “none”).
•	Orchestrates extraction using the Scanning Engine’s OCR / heuristics / classifiers.
•	Outputs an ItemDetails object (category + attributes) the rest of the app can use.
4.	Pricing Engine (consumer-first)
•	Uses:
•	Category (ItemCategory)
•	attributes (brand, type, etc.)
→ to derive price ranges.
•	Initially: simple heuristics or local lookup.
•	Future: eBay / other marketplace integration.

⸻

D. Integration Layer
1.	Pricing Integrations (future)
•	eBay APIs:
•	Map Scanium category + attributes → eBay category + search query.
•	Pull sold/completed listings for price estimation.
•	Other consumer marketplaces.
2.	Business Integrations (future)
•	Inventory systems / ERP:
•	Map Scanium Item → client’s SKU / internal category.
•	Push items via REST/GraphQL.
•	Company-specific “Domain Packs” loaded from backend per tenant.

⸻

1.2. Data flow sketch

Conceptual flow from camera to UI:

flowchart LR
Camera(CameraX Preview) --> Det(ML Kit Detection & Tracking)
Det --> Agg(Aggregation & Tracking)
Agg -->|New stable item| ClassOrch(Classification Orchestrator)

    ClassOrch -->|ON-DEVICE CLIP| CLIPModel[On-Device CLIP Model]
    ClassOrch -->|CLOUD (optional)| Cloud(Cloud Vision API)
    CLIPModel --> ClassOrch
    Cloud --> ClassOrch

    ClassOrch --> DomPack(Domain Pack)
    DomPack --> CatEngine(Category Engine)
    ClassOrch --> CatEngine
    CatEngine --> AttrEngine(Attribute Engine)

    AttrEngine --> OCR(ML Kit Text/Barcode)
    AttrEngine --> Heur(Visual Heuristics)

    AttrEngine --> Item(ItemDetails: category + attributes)
    Item --> Pricing(Pricing Engine)
    Pricing --> UI(Items List & Overlays)


⸻

2. Plan: independent tasks that can run in parallel

Now let’s turn this into parallelizable workstreams.
I’ll label them Track A–G, with tasks inside each. Where there are dependencies, I’ll note them.

Track A – Domain Pack & Category Model (config-driven intelligence)

Goal: Make categories & attributes data-driven, not hard-coded.
1.	A1 – Define Domain Pack schema (consumer/HomeResale)
•	Design a JSON/YAML schema for a Domain Pack:
•	categories[] with:
•	id, displayName, parentId
•	prompts[] (for CLIP text)
•	itemCategory (maps into your existing enum)
•	attributes[] with:
•	name, type, extractionMethod (OCR/CLIP/barcode/cloud/none)
•	appliesToCategoryIds[]
•	This can be done purely as design + sample JSON file.
2.	A2 – Implement DomainPack loader
•	Write a Kotlin loader that:
•	Reads a Domain Pack JSON from assets/raw.
•	Exposes a DomainPack object via a DomainRepository.
•	For now, hard-code using HomeResalePack.json.
3.	A3 – Category Engine scaffolding
•	Implement CategoryEngine with simple API:
•	fun selectCategory(clipResult, mlKitLabel): DomainCategory
•	Initially, stub logic or simple lookup.
•	Real logic will be added after CLIP integration (Track C).

✅ A1–A3 can be done independently of CLIP, CLOUD, or UI changes.

⸻

Track B – Classification Mode & Orchestrator Core

Goal: Prepare the plumbing for ON-DEVICE vs CLOUD classification, without implementing models yet.
4.	B1 – Define ClassificationMode and ItemClassifier
•	Enum: ON_DEVICE, CLOUD.
•	Interface ItemClassifier:
•	suspend fun classify(item: ItemCrop): ClassificationResult.
5.	B2 – Create ClassificationOrchestrator
•	Knows:
•	active ClassificationMode (later from settings).
•	references to:
•	onDeviceClassifier: ItemClassifier
•	cloudClassifier: ItemClassifier
•	Exposes:
•	classifyIfNeeded(itemId, crop): ClassificationResult
•	Implements:
•	caching (don’t reclassify same itemId).
6.	B3 – Wire orchestrator into ItemsViewModel
•	When an AggregatedItem is promoted/confirmed:
•	ItemsViewModel calls orchestrator.
•	The result is stored in Item’s semantic fields (e.g., semanticLabel, semanticConfidence).

✅ B1–B3 can run in parallel with Track A and don’t require the CLIP model yet (you can stub ItemClassifier).

⸻

Track C – ON-DEVICE CLIP integration

Goal: Implement the ON-DEVICE classifier for the default consumer experience.
7.	C1 – Add CLIP model asset
•	Add chosen MobileCLIP/CLIP model (e.g. .tflite) in app/src/main/ml or assets.
•	Document:
•	input size, normalization, embedding dimension.
8.	C2 – Implement ClipModelClient
•	Functions:
•	fun encodeImage(bitmap: Bitmap): FloatArray.
•	(Optional) fun encodeText(text: String): FloatArray if you do text on-device.
•	Handles:
•	model loading,
•	bitmap preprocessing,
•	running TFLite,
•	runs off main thread.
9.	C3 – Precompute / load category text embeddings
•	Option 1: precompute embeddings offline → bundle file; load into memory at startup.
•	Option 2: compute them once on first launch; cache them locally.
•	Results live in DomainPack or a CategoryEmbeddings holder.
10.	C4 – Implement OnDeviceClipClassifier : ItemClassifier
•	For a given item crop:
•	Calls ClipModelClient.encodeImage.
•	Computes cosine similarity with category embeddings from Domain Pack.
•	Picks best category + similarity.
•	Returns ClassificationResult, including candidate DomainCategory.

✅ C1–C4 only depend lightly on Track A (to know categories/prompts) and B1 (ItemClassifier interface). They can be developed largely in parallel.

⸻

Track D – CLOUD classifier (optional, later, independent)

Goal: Prepare the cloud path without blocking ON-DEVICE work.
11.	D1 – Define cloud classification API contract (internal)
•	Decide:
•	endpoint URL (placeholder),
•	request shape (base64 or multipart image),
•	response shape (labels + confidence).
12.	D2 – Implement CloudClassifier : ItemClassifier
•	Stub version:
•	Could return “unavailable” or some fake result for now.
•	Real version later:
•	Uses Retrofit or Ktor.
•	Takes item crop → encodes → call API → maps to ClassificationResult.

✅ D1–D2 can be done independently and don’t block consumer initial release (just keep CLOUD mode behind a feature flag).

⸻

Track E – Attribute & Pricing Engine

Goal: Turn categories into richer item descriptions & prices, in a generic way.
13.	E1 – Attribute Engine skeleton
•	AttributeEngine that:
•	Given a DomainCategory and access to:
•	OCR,
•	barcode reader,
•	CLIP result,
•	Returns a map or typed object: ItemAttributes.
•	Attributes defined by the Domain Pack (Track A1).
14.	E2 – Basic attribute extractors
•	Implement simple strategies:
•	BRAND via OCR (where text is visible).
•	COLOR via simple histogram / average color of crop.
•	BARCODE via ML Kit Barcode scanning.
•	Link: extraction methods declared in Domain Pack → calls the right extractor.
15.	E3 – Pricing Engine v1
•	Very simple price estimation:
•	base price range per ItemCategory.
•	multiplier/adjustment if brand is known (e.g. premium vs unknown).
•	This is consumer-focused but architected so that later:
•	eBay API integration just plugs into this price engine.

✅ E1–E3 can run in parallel to B & C; they only need category info and a place to plug results.

⸻

Track F – UI, Settings & Debugging

Goal: Expose new functionality (mode toggle, richer info) without blocking engine work.
16.	F1 – Settings: classification mode toggle
•	New setting: “Classification mode: ON-DEVICE / CLOUD”.
•	Stored in DataStore.
•	Exposed via SettingsViewModel → consumed by ClassificationOrchestrator (Track B2).
17.	F2 – Items list & detail updates
•	Show:
•	category name,
•	key attributes (e.g. brand, color),
•	estimated price,
•	classification confidence (optional).
•	This is primarily presentation; can be wired as soon as ItemDetails/ItemAttributes are available.
18.	F3 – Debug overlay
•	For dev build:
•	Overlays CLIP category vs ML Kit vs final chosen category.
•	Shows classification mode in use.
•	Shows similarity/confidence.

✅ F1–F3 can be developed in parallel with everything else, as long as core types (ClassificationMode, Category/Item models) are agreed.

⸻

Track G – Performance, QA & Logging

Goal: Make sure all of this remains fast and future-proof.
19.	G1 – Performance guardrails
•	Implement:
•	“classify once per item” policy (already natural in orchestrator).
•	cap simultaneous classification jobs.
•	Add simple metrics:
•	average CLIP latency per item,
•	number of items classified per session.
20.	G2 – Logging & telemetry schema
•	Decide minimal logs:
•	item ID,
•	ML Kit label,
•	CLIP category + similarity,
•	final DomainCategory,
•	timing info (for latency).
•	Implement local logging (file/DB) with dev-only export.
21.	G3 – QA scenarios
•	Define test scenarios:
•	cluttered room with many objects,
•	single item, close-up,
•	low light,
•	high-motion panning.
•	Manual testing scripts to ensure:
•	UX smoothness,
•	classification stable,
•	no ANR / crashes.

✅ G1–G3 can start as soon as a basic vertical slice exists (detected item → CLIP classification → category displayed).

⸻

How this plays out in practice

For a consumer MVP with future B2B support:
•	Start with Tracks A, B, C, F in parallel:
•	A: Domain Pack config.
•	B: classification orchestrator.
•	C: ON-DEVICE CLIP integration.
•	F: mode toggle + UI for categories/attributes.
•	In parallel, start E (attributes + simple pricing) and G (perf/logging) as soon as minimal classification works.
•	Keep D (CLOUD) as an optional track that you can light up later without touching the core design.

If you want, next step I can:
•	Turn this plan into a Codex CLI project prompt that guides an agent through implementing the architecture piece by piece in your actual Scanium repo.