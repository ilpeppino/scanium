# 📘 OBJECTA — COMPLETE NEXT-PHASE DOCUMENT SET

Below you will find **ALL requested documents**, generated **one by one**, all inside this single
Markdown block:

1. **Roadmap (3–6 months)**
2. **MVP Launch Checklist**
3. **Codex CLI Prompt for Next-Phase Features**
4. **Full Product Requirements Document (PRD)**

---

# 1️⃣ **OBJECTA ROADMAP — NEXT 3–6 MONTHS**

## 🎯 **Goal**

Turn Scanium from a strong PoC into a production-grade, scalable, multi-mode AI scanning engine
suitable for public release.

---

## **Phase 1 — Polishing the Core (Weeks 1–4)**

### ✔ ML + Tracking Enhancements

- Improve multi-frame candidate → confirmed promotion thresholds.
- Tune confidence scoring pipeline using real-world data logs.
- Add dynamic frame-rate throttling based on device performance.
- Reduce false positives through improved spatial heuristics (IoU thresholds, motion filtering).

### ✔ UX & Interaction

- Refine slider animations and transitions.
- Add optional confidence indicators (UI-safe color coding).
- Implement gentle haptic feedback on confirmed detections.

### ✔ Stability Improvements

- Strict ImageProxy closing audit.
- Stabilize analyzer threading.
- Add ML inference time watchdog / fallback logic.

---

## **Phase 2 — New Capabilities (Weeks 5–10)**

### ✔ Barcode Mode Expansion

- Batch barcode scanning with auto-confirmation.
- Add semantic barcode type icons (EAN13, QR, etc.)
- Add optional vibration/beep feedback for barcode hits.

### ✔ Text Recognition Mode (OCR)

- Document boundary detection.
- Auto-enhance text regions.
- Auto-extracted metadata (brand name, model, product title).

### ✔ Pricing System Enhancement

- Add a pluggable pricing provider interface.
- Allow multiple pricing providers (internal mock + external API).
- Add EUR → GBP → USD currency conversion support.

---

## **Phase 3 — Persistence + Cloud (Weeks 11–16)**

### ✔ Data Layer

- Add Room local DB for:
    - Scanned items
    - Sessions
    - Metadata and analytics
- Add DataStore for:
    - User preferences
    - Threshold tuning
    - Last used scan mode

### ✔ Cloud Enhancements

- Add optional Scanium Cloud Sync (users log in).
- Save item history across devices.
- Add analytics pipeline (Firebase Analytics + Crashlytics).

---

## **Phase 4 — Release Readiness (Weeks 17–24)**

### ✔ Architecture Modernization

- Introduce multi-module structure:
    - :core:ml
    - :core:tracking
    - :core:data
    - :feature:camera
    - :feature:items
    - :feature:pricing
    - :app
- Add DI using Hilt.

### ✔ Testing & CI/CD

- Raise test coverage to 80%.
- Add full end-to-end UI tests.
- GitHub Actions CI:
    - Build → Test → Lint → Assemble → Deploy internal release.
- Deploy Alpha & Beta builds to Play Store.

### ✔ UX Polish

- Icons, animations, error states, internationalization.
- Performance budget monitoring (<=150ms ML latency target).
- Accessibility pass (TalkBack, large text, high contrast).

---

# 2️⃣ **OBJECTA MVP LAUNCH CHECKLIST**

## ✅ **Core Detection**

- [ ] Object detection stable at 2–5 fps analysis rate
- [ ] Multi-frame tracking fully operational
- [ ] Confidence scoring consistent
- [ ] Bounding box rendering smooth and aligned
- [ ] No analyzer bottlenecks or frame leaks

## ✅ **Scanning Modes**

- [ ] Item Recognition Mode (default)
- [ ] Barcode Mode fully functional
- [ ] OCR Mode (if included) detects text reliably
- [ ] Slider UI polished with camera-like transitions

## ✅ **Pricing**

- [ ] Category → EUR price range mapping validated
- [ ] Barcode → SKU → Price provider integrated (if planned)
- [ ] Prices formatted with localization

## ✅ **UX**

- [ ] Smooth animation between modes
- [ ] Camera permission flow stable
- [ ] Items List UI polished and responsive
- [ ] Confidence indicators optionally visible

## ✅ **Data & Persistence**

- [ ] Session tracking works
- [ ] Items saved to local database
- [ ] Config saved in DataStore

## ✅ **Testing**

- [ ] All unit tests passing
- [ ] All integration tests passing
- [ ] Camera pipeline tested with fakes
- [ ] Slider interaction tested
- [ ] CI pipeline green

## ✅ **Performance**

- [ ] ML inference time consistently below threshold
- [ ] Memory footprint stable over 10+ minutes scanning
- [ ] No frame queue build-up
- [ ] No thermal throttling observed in common devices

## ✅ **Release**

- [ ] Crash-free startup rate ≥99%
- [ ] Google Play signing configured
- [ ] Play Store listing prepared
- [ ] Beta tester onboarding complete

