***REMOVED*** 📘 OBJECTA — COMPLETE NEXT-PHASE DOCUMENT SET

Below you will find **ALL requested documents**, generated **one by one**, all inside this single Markdown block:

1. **Roadmap (3–6 months)**
2. **MVP Launch Checklist**
3. **Codex CLI Prompt for Next-Phase Features**
4. **Full Product Requirements Document (PRD)**

---

***REMOVED*** 1️⃣ **OBJECTA ROADMAP — NEXT 3–6 MONTHS**

***REMOVED******REMOVED*** 🎯 **Goal**
Turn Objecta from a strong PoC into a production-grade, scalable, multi-mode AI scanning engine suitable for public release.

---

***REMOVED******REMOVED*** **Phase 1 — Polishing the Core (Weeks 1–4)**
***REMOVED******REMOVED******REMOVED*** ✔ ML + Tracking Enhancements
- Improve multi-frame candidate → confirmed promotion thresholds.
- Tune confidence scoring pipeline using real-world data logs.
- Add dynamic frame-rate throttling based on device performance.
- Reduce false positives through improved spatial heuristics (IoU thresholds, motion filtering).

***REMOVED******REMOVED******REMOVED*** ✔ UX & Interaction
- Refine slider animations and transitions.
- Add optional confidence indicators (UI-safe color coding).
- Implement gentle haptic feedback on confirmed detections.

***REMOVED******REMOVED******REMOVED*** ✔ Stability Improvements
- Strict ImageProxy closing audit.
- Stabilize analyzer threading.
- Add ML inference time watchdog / fallback logic.

---

***REMOVED******REMOVED*** **Phase 2 — New Capabilities (Weeks 5–10)**
***REMOVED******REMOVED******REMOVED*** ✔ Barcode Mode Expansion
- Batch barcode scanning with auto-confirmation.
- Add semantic barcode type icons (EAN13, QR, etc.)
- Add optional vibration/beep feedback for barcode hits.

***REMOVED******REMOVED******REMOVED*** ✔ Text Recognition Mode (OCR)
- Document boundary detection.
- Auto-enhance text regions.
- Auto-extracted metadata (brand name, model, product title).

***REMOVED******REMOVED******REMOVED*** ✔ Pricing System Enhancement
- Add a pluggable pricing provider interface.
- Allow multiple pricing providers (internal mock + external API).
- Add EUR → GBP → USD currency conversion support.

---

***REMOVED******REMOVED*** **Phase 3 — Persistence + Cloud (Weeks 11–16)**
***REMOVED******REMOVED******REMOVED*** ✔ Data Layer
- Add Room local DB for:
    - Scanned items
    - Sessions
    - Metadata and analytics
- Add DataStore for:
    - User preferences
    - Threshold tuning
    - Last used scan mode

***REMOVED******REMOVED******REMOVED*** ✔ Cloud Enhancements
- Add optional Objecta Cloud Sync (users log in).
- Save item history across devices.
- Add analytics pipeline (Firebase Analytics + Crashlytics).

---

***REMOVED******REMOVED*** **Phase 4 — Release Readiness (Weeks 17–24)**
***REMOVED******REMOVED******REMOVED*** ✔ Architecture Modernization
- Introduce multi-module structure:
    - :core:ml
    - :core:tracking
    - :core:data
    - :feature:camera
    - :feature:items
    - :feature:pricing
    - :app
- Add DI using Hilt.

***REMOVED******REMOVED******REMOVED*** ✔ Testing & CI/CD
- Raise test coverage to 80%.
- Add full end-to-end UI tests.
- GitHub Actions CI:
    - Build → Test → Lint → Assemble → Deploy internal release.
- Deploy Alpha & Beta builds to Play Store.

***REMOVED******REMOVED******REMOVED*** ✔ UX Polish
- Icons, animations, error states, internationalization.
- Performance budget monitoring (<=150ms ML latency target).
- Accessibility pass (TalkBack, large text, high contrast).

---

***REMOVED*** 2️⃣ **OBJECTA MVP LAUNCH CHECKLIST**

***REMOVED******REMOVED*** ✅ **Core Detection**
- [ ] Object detection stable at 2–5 fps analysis rate
- [ ] Multi-frame tracking fully operational
- [ ] Confidence scoring consistent
- [ ] Bounding box rendering smooth and aligned
- [ ] No analyzer bottlenecks or frame leaks

***REMOVED******REMOVED*** ✅ **Scanning Modes**
- [ ] Item Recognition Mode (default)
- [ ] Barcode Mode fully functional
- [ ] OCR Mode (if included) detects text reliably
- [ ] Slider UI polished with camera-like transitions

***REMOVED******REMOVED*** ✅ **Pricing**
- [ ] Category → EUR price range mapping validated
- [ ] Barcode → SKU → Price provider integrated (if planned)
- [ ] Prices formatted with localization

***REMOVED******REMOVED*** ✅ **UX**
- [ ] Smooth animation between modes
- [ ] Camera permission flow stable
- [ ] Items List UI polished and responsive
- [ ] Confidence indicators optionally visible

***REMOVED******REMOVED*** ✅ **Data & Persistence**
- [ ] Session tracking works
- [ ] Items saved to local database
- [ ] Config saved in DataStore

***REMOVED******REMOVED*** ✅ **Testing**
- [ ] All unit tests passing
- [ ] All integration tests passing
- [ ] Camera pipeline tested with fakes
- [ ] Slider interaction tested
- [ ] CI pipeline green

***REMOVED******REMOVED*** ✅ **Performance**
- [ ] ML inference time consistently below threshold
- [ ] Memory footprint stable over 10+ minutes scanning
- [ ] No frame queue build-up
- [ ] No thermal throttling observed in common devices

***REMOVED******REMOVED*** ✅ **Release**
- [ ] Crash-free startup rate ≥99%
- [ ] Google Play signing configured
- [ ] Play Store listing prepared
- [ ] Beta tester onboarding complete

