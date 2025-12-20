> Archived on 2025-12-20: superseded by docs/INDEX.md.
# iOS-Android Parity Documentation

**Last Updated:** 2025-12-19
**Purpose:** Comprehensive analysis and plan to bring iOS to feature parity with Android

---

## 📋 Document Index

This directory contains the complete parity assessment and implementation plan for Scanium's iOS app:

### 1. **ANDROID_BASELINE.md**
The source of truth. Complete enumeration of Android capabilities, organized by:
- Camera capture
- ML/object detection
- Classification
- Tracking & aggregation
- UI (items list, details)
- Storage & gallery export
- eBay selling integration
- Navigation, theming, build config, testing, permissions

**Use this to:** Understand what iOS must achieve

---

### 2. **IOS_CURRENT.md**
Current state of the iOS implementation with evidence-based assessment:
- What exists (partial ML services, basic list view)
- What's missing (camera UI, tracking integration, selling flow, storage, navigation)
- Status breakdown: ✅ Complete / 🟡 Partial / ❌ Not Implemented

**Use this to:** Understand where iOS is today (~15% parity)

---

### 3. **GAP_MATRIX.md** ⭐ (Main Artifact)
The core gap analysis in table format. 57 identified gaps across 15 capability areas:

| Capability | Android Status | iOS Status | Gap | Root Cause | Risk | Solution | Estimation |
|------------|---------------|------------|-----|------------|------|----------|------------|

**Includes:**
- Detailed gap descriptions with file path evidence
- Root cause type (Missing UI / Missing Adapter / Missing Wiring / etc.)
- Risk assessment (HIGH / MED / LOW)
- Dependencies (what must be done first)
- Proposed solution approach
- Estimation bucket (S/M/L)

**Use this to:** Prioritize work and understand technical blockers

---

### 4. **PARITY_PLAN.md**
Phased implementation plan optimized for Android stability:

**6 Phases:**
- **Phase 0:** Validation & Guardrails (Week 1)
- **Phase 1:** Shared Brain Readiness (Week 2)
- **Phase 2:** iOS Platform Adapters (Weeks 3-4)
- **Phase 3:** iOS UI Parity (Weeks 5-7)
- **Phase 4:** Storage & Export Parity (Week 8)
- **Phase 5:** Selling Flow (Week 9, optional)
- **Phase 6:** Observability & Final Polish (Week 10)

Each phase includes:
- Objectives
- Concrete tasks
- Acceptance criteria
- Definition of done
- Dependencies
- "Do not touch" constraints

**Use this to:** Understand the high-level roadmap and dependencies

---

### 5. **PR_ROADMAP.md**
Granular PR-by-PR roadmap with 42 pull requests across 5 parallelization tracks:

**Tracks:**
- **Track A:** Camera & ML (18 PRs)
- **Track B:** Items List & UI (5 PRs)
- **Track C:** Shared Integration (9 PRs)
- **Track D:** Navigation & Settings (8 PRs)
- **Track E:** Storage & Selling (7 PRs)

**Each PR includes:**
- Title, scope (files/modules), acceptance criteria
- Risk level, estimation, dependencies
- "Do not touch" constraints
- Parallelization guidance

**Use this to:** Assign work to engineers and track progress

---

## 🎯 Executive Summary

### Current State
- **Android:** 100% complete, production-ready baseline (58 Kotlin files, 24 tests)
- **iOS:** ~15% complete, scaffolding only (11 Swift files, 0 tests)

### Gap Summary
- **Total Gaps:** 57
- **Critical (HIGH risk):** 12 gaps - Must fix for MVP
- **Important (MED risk):** 27 gaps - Needed for full parity
- **Nice-to-have (LOW risk):** 18 gaps - Polish and optimization

### Timeline
- **Estimated Effort:** 230 developer-days (serial)
- **Optimized Timeline:** 8-10 weeks (with 2-3 engineers working in parallel)
- **Critical Path:** 8-9 weeks (cannot be parallelized)

### Critical Gaps (Must Fix)
1. Camera preview UI
2. ML pipeline integration (real-time detection)
3. Object tracking integration (shared KMP)
4. Item aggregation (deduplication)
5. Items list state management
6. Navigation architecture
7. Photo library save
8. Camera permission (Info.plist)
9. XCFramework linking validation

---

## 📊 Key Metrics

### Android Baseline (Source of Truth)
```
Total Files: 58 source + 24 tests
Modules: androidApp, android-camera-camerax, android-ml-mlkit, android-platform-adapters
Shared: core-tracking, core-models (KMP)
Features: ✅ Camera, ✅ ML, ✅ Tracking, ✅ Classification, ✅ UI, ✅ Storage, ✅ Selling, ✅ Navigation
Tests: 30 (unit + integration)
```

### iOS Current State
```
Total Files: 11 source + 0 tests
Modules: iosApp/ScaniumiOS
Shared: Partial integration (can import, not used live)
Features: 🟡 Frame source, 🟡 ML services (isolated), ❌ Camera UI, ❌ Tracking, ❌ Classification, ❌ Storage, ❌ Selling, ❌ Navigation
Tests: 0
```

### Gap Distribution by Root Cause
```
Missing UI:          22 gaps (39%)
Missing Adapter:     15 gaps (26%)
Missing Wiring:      10 gaps (18%)
Missing State Mgmt:   6 gaps (11%)
Missing Config:       4 gaps (7%)
```

---

## 🚀 How to Use This Parity Assessment

### For Engineering Leadership:
1. Read **GAP_MATRIX.md** to understand scope and risk
2. Review **PARITY_PLAN.md** for phased approach
3. Use **PR_ROADMAP.md** to staff and track progress
4. Set timeline expectations: 8-10 weeks minimum

### For Engineers:
1. Start with **ANDROID_BASELINE.md** to understand reference implementation
2. Reference **IOS_CURRENT.md** to see what exists
3. Pick a PR from **PR_ROADMAP.md** matching your track
4. Use **GAP_MATRIX.md** to find file paths and acceptance criteria
5. Follow "Do not touch" constraints (keep Android stable)

### For Product/PM:
1. Review **Executive Summary** above for timeline and scope
2. Prioritize based on **Critical Gaps** (HIGH risk items)
3. Decide if Phase 5 (Selling Flow) is required for MVP
4. Track progress via PR count (42 total PRs)

---

## ⚠️ Non-Negotiables (Constraints)

1. **DO NOT modify Android code** - Android is the stable baseline
2. **DO NOT rewrite shared KMP modules** - Shared is the "brain", both platforms must use it
3. **DO NOT skip testing** - Every PR should include tests (unit or integration)
4. **DO NOT merge without acceptance criteria** - Each PR has clear DoD
5. **DO follow the critical path** - Some work cannot be parallelized

---

## 📈 Progress Tracking

Track parity completion with this formula:

```
Parity % = (Merged PRs / 42 Total PRs) × 100
```

**Milestones:**
- **25% (PR-001 to PR-009):** Shared integration validated
- **50% (PR-001 to PR-024):** Camera + ML working
- **75% (PR-001 to PR-033):** Full UI + Storage working
- **100% (PR-001 to PR-042):** Feature parity achieved

---

## 🔗 Related Documentation

- **Main README:** `/README.md` - Project overview
- **Architecture Docs:** `/docs/` - System architecture
- **Android Docs:** `/androidApp/` - Android-specific docs

---

## 📞 Questions?

If you have questions about this parity assessment:
1. Check the relevant document (ANDROID_BASELINE, GAP_MATRIX, etc.)
2. Look up the specific Android implementation file referenced
3. Consult the "Do not touch" constraints in PR_ROADMAP
4. Ask in the #ios-parity Slack channel (or equivalent)

---

## 🎓 Lessons Learned (Post-Parity)

*This section will be updated after iOS parity is achieved with key takeaways, gotchas, and recommendations for future cross-platform work.*

---

**Last Review:** 2025-12-19
**Status:** Documentation Complete, Implementation Pending
**Next Action:** Begin Phase 0 (Validation & Guardrails)
