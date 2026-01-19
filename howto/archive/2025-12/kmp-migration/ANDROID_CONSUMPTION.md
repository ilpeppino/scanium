***REMOVED*** Android consumption hardening

***REMOVED******REMOVED*** Goal

Keep the Android app wired to shared KMP modules through a single path to prevent duplicate class
definitions when legacy wrappers coexist with shared packages.

***REMOVED******REMOVED*** Dependency graph cleanup

- Android app should depend on `:core-models` and `:core-tracking` only; these wrappers already
  `api`-re-export the shared KMP modules.
- Avoid adding direct `:shared:core-models` or `:shared:core-tracking` dependencies to the app
  alongside the wrappers—doing both can surface duplicate class/package errors later when the
  wrappers gain Android-only helpers.
- Library shells (`android-ml-mlkit`, `android-camera-camerax`, `android-platform-adapters`) stay
  Android-specific and should not reach into shared modules.

***REMOVED******REMOVED*** Wrapper package hygiene

- Keep wrapper namespaces distinct from shared packages (e.g., wrappers under `com.scanium.core.*`,
  shared code under `com.scanium.shared.*`).
- Prefer re-export/typealias patterns inside wrappers rather than copying shared classes; do not
  introduce new classes in wrapper packages that mirror shared package names.
