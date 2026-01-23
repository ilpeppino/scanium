# Decisions (ADR-lite)

- **2025-12** – Adopt Java 17 toolchain across builds to match Gradle config and CI setup (
  `settings.gradle.kts`, Android Debug APK workflow). Reason: align with modern Android requirements
  and CI runners.
- **2025-12** – Consolidate documentation into the canonical set under `docs/` with archives in
  `docs/_archive/2025-12/` and tracking in `docs/CLEANUP_REPORT.md`. Reason: reduce duplication and
  keep a single source of truth for agents/developers.
- **2025-12** – Centralize shared scripts under `scripts/` with `scripts/README.md` describing entry
  points. Reason: avoid scattered automation across the repo and make maintenance easier.
- **2025-12** – Keep default detection/classification on-device; enable `CloudClassifier` only with
  explicit backend config. Reason: privacy and offline-first behavior; prevents crashes when
  credentials are absent.
- **2025-12** – Maintain platform-neutral tracking/domain logic in `core-*` and `shared:*` modules,
  adapting Android types via `android-platform-adapters`. Reason: prepares KMP/iOS reuse without
  Android dependencies.
- **2025-12** – Use GitHub Actions artifact pipeline for APK distribution instead of container
  builds. Reason: Codex container lacks Android SDK; APKs are produced by `android-debug-apk`
  workflow.
- **2025-12** – Run OWASP Dependency-Check on Gradle file changes and weekly via
  `security-cve-scan.yml`. Reason: baseline dependency hygiene with SARIF reporting.
- **2024-12** – Implement WYSIWYG viewport alignment using CameraX `ViewPort` + `UseCaseGroup` to
  ensure ImageAnalysis sees only what the user sees in Preview. Add geometry-based edge gating (
  configurable inset margin, default 10%) to filter partial/cut-off objects at screen edges. Add
  lightweight spatial-temporal merge policy in `shared/core-tracking` as fallback for tracker ID
  churn (IoU/distance matching within time window, default 800ms). Reason: eliminate duplicate item
  detections caused by off-screen phantom objects and tracker ID instability, while maintaining zero
  per-frame bitmap cropping and minimal CPU/memory overhead.
