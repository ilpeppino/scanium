# Migration Map

Documentation reorganization performed on 2026-01-10.

## Legend

| Action  | Meaning                                      |
|---------|----------------------------------------------|
| MOVE    | File moved with `git mv` (history preserved) |
| MERGE   | Content merged into canonical document       |
| ARCHIVE | Moved to howto/archive/                      |
| KEEP    | Stays in original location (code-level doc)  |
| STUB    | Stub left at old location pointing to new    |

---

## Root Level Files

| Old Path                           | New Path                                               | Action |
|------------------------------------|--------------------------------------------------------|--------|
| `AGENTS.md`                        | `howto/project/reference/AGENTS.md`                    | MOVE   |
| `GEMINI.md`                        | `howto/project/reference/GEMINI.md`                    | MOVE   |
| `MANUAL_VERIFICATION_NOTES_FIX.md` | `howto/app/debugging/MANUAL_VERIFICATION_NOTES_FIX.md` | MOVE   |
| `README.md`                        | `README.md`                                            | KEEP   |
| `PRIVACY.MD`                       | `PRIVACY.MD`                                           | KEEP   |

---

## backend/docs/ → howto/backend/

| Old Path                                         | New Path                                                    | Action |
|--------------------------------------------------|-------------------------------------------------------------|--------|
| `backend/docs/ASSISTANT_PROVIDERS.md`            | `howto/backend/reference/ASSISTANT_PROVIDERS.md`            | MOVE   |
| `backend/docs/ASSISTANT_WARMUP.md`               | `howto/backend/reference/ASSISTANT_WARMUP.md`               | MOVE   |
| `backend/docs/MARKETPLACE_LISTING_GENERATION.md` | `howto/backend/reference/MARKETPLACE_LISTING_GENERATION.md` | MOVE   |
| `backend/docs/OBSERVABILITY.md`                  | `howto/backend/reference/OBSERVABILITY.md`                  | MOVE   |
| `backend/docs/REDEPLOY.md`                       | `howto/backend/deploy/REDEPLOY.md`                          | MOVE   |
| `backend/docs/VISION_ENRICHMENT.md`              | `howto/backend/reference/VISION_ENRICHMENT.md`              | MOVE   |
| `backend/HANDOFF-REPORT-2026-01-09.md`           | `howto/backend/reports/HANDOFF-REPORT-2026-01-09.md`        | MOVE   |

---

## docs/ → Various howto/ locations

### App-related docs → howto/app/

| Old Path                                     | New Path                                                    | Action |
|----------------------------------------------|-------------------------------------------------------------|--------|
| `docs/BETA_VALIDATION.md`                    | `howto/app/releases/BETA_VALIDATION.md`                     | MOVE   |
| `docs/RELEASE_CHECKLIST.md`                  | `howto/app/releases/RELEASE_CHECKLIST.md`                   | MOVE   |
| `docs/RELEASE_AND_ROLLBACK.md`               | `howto/app/releases/RELEASE_AND_ROLLBACK.md`                | MOVE   |
| `docs/DEV_GUIDE.md`                          | `howto/project/reference/DEV_GUIDE.md`                      | MOVE   |
| `docs/DEV_SCRIPTS.md`                        | `howto/project/reference/DEV_SCRIPTS.md`                    | MOVE   |
| `docs/SCANNING_GUIDANCE.md`                  | `howto/app/reference/SCANNING_GUIDANCE.md`                  | MOVE   |
| `docs/SCAN_VS_PICTURE_ASSESSMENT.md`         | `howto/app/reference/SCAN_VS_PICTURE_ASSESSMENT.md`         | MOVE   |
| `docs/BUG_PORTRAIT_BBOX_CROP.md`             | `howto/app/debugging/BUG_PORTRAIT_BBOX_CROP.md`             | MOVE   |
| `docs/BBOX_LIFECYCLE_ROOT_CAUSE.md`          | `howto/app/debugging/BBOX_LIFECYCLE_ROOT_CAUSE.md`          | MOVE   |
| `docs/CAM_NO_FRAMES_STALL.md`                | `howto/app/debugging/CAM_NO_FRAMES_STALL.md`                | MOVE   |
| `docs/LIVE_SCAN_CENTERING_BUG.md`            | `howto/app/debugging/LIVE_SCAN_CENTERING_BUG.md`            | MOVE   |
| `docs/PORTRAIT_MAPPING_FIX.md`               | `howto/app/debugging/PORTRAIT_MAPPING_FIX.md`               | MOVE   |
| `docs/THUMBNAIL_PERSISTENCE_ANALYSIS.md`     | `howto/app/debugging/THUMBNAIL_PERSISTENCE_ANALYSIS.md`     | MOVE   |
| `docs/KNOWN_GOOD_LANDSCAPE_BBOX_SNAPSHOT.md` | `howto/app/debugging/KNOWN_GOOD_LANDSCAPE_BBOX_SNAPSHOT.md` | MOVE   |
| `docs/VISION_ENRICHMENT_UI.md`               | `howto/app/reference/VISION_ENRICHMENT_UI.md`               | MOVE   |
| `docs/ITEM_EDITING.md`                       | `howto/app/reference/ITEM_EDITING.md`                       | MOVE   |
| `docs/ITEM_EDITING_FIELDS.md`                | `howto/app/reference/ITEM_EDITING_FIELDS.md`                | MOVE   |
| `docs/SETTINGS_IA.md`                        | `howto/app/reference/SETTINGS_IA.md`                        | MOVE   |
| `docs/UI_REDESIGN_SETTINGS_CAMERA.md`        | `howto/app/reference/UI_REDESIGN_SETTINGS_CAMERA.md`        | MOVE   |
| `docs/EXPORT_FORMATS.md`                     | `howto/app/reference/EXPORT_FORMATS.md`                     | MOVE   |
| `docs/SOUNDS.md`                             | `howto/app/reference/SOUNDS.md`                             | MOVE   |
| `docs/MOTION_LANGUAGE.md`                    | `howto/app/reference/MOTION_LANGUAGE.md`                    | MOVE   |
| `docs/FLAVOR_GATING.md`                      | `howto/app/reference/FLAVOR_GATING.md`                      | MOVE   |
| `docs/LOCALIZATION_PLAN.md`                  | `howto/app/reference/LOCALIZATION_PLAN.md`                  | MOVE   |
| `docs/WIP_scan_enrichment_flow.md`           | `howto/app/reference/WIP_scan_enrichment_flow.md`           | MOVE   |

### Backend-related docs → howto/backend/

| Old Path                                                       | New Path                                                   | Action |
|----------------------------------------------------------------|------------------------------------------------------------|--------|
| `docs/BACKEND_CONNECTIVITY.md`                                 | `howto/backend/reference/BACKEND_CONNECTIVITY.md`          | MOVE   |
| `docs/DEV_BACKEND_CONNECTIVITY.md`                             | `howto/backend/reference/DEV_BACKEND_CONNECTIVITY.md`      | MOVE   |
| `docs/ASSISTANT_AVAILABILITY.md`                               | `howto/backend/reference/ASSISTANT_AVAILABILITY.md`        | MOVE   |
| `docs/AI_ASSISTANT_IMAGES_VERIFY.md`                           | `howto/backend/reference/AI_ASSISTANT_IMAGES_VERIFY.md`    | MOVE   |
| `docs/AI_ASSISTANT_ITEM_SIGNALS_MAP.md`                        | `howto/backend/reference/AI_ASSISTANT_ITEM_SIGNALS_MAP.md` | MOVE   |
| `docs/ENRICH_OPERATOR_RUNBOOK.md`                              | `howto/backend/runbooks/ENRICH_OPERATOR_RUNBOOK.md`        | MOVE   |
| `docs/PIPELINE_ALIGNMENT_ANALYSIS.md`                          | `howto/backend/reference/PIPELINE_ALIGNMENT_ANALYSIS.md`   | MOVE   |
| `docs/PLAN_ATTRIBUTE_EXTRACTION_AND_DESCRIPTION_GENERATION.md` | `howto/backend/reference/PLAN_ATTRIBUTE_EXTRACTION.md`     | MOVE   |
| `docs/PRICING_PHASE_2_DESIGN.md`                               | `howto/backend/reference/PRICING_PHASE_2_DESIGN.md`        | MOVE   |
| `docs/DEV_GUIDE_MULTILINGUAL_AI.md`                            | `howto/backend/reference/DEV_GUIDE_MULTILINGUAL_AI.md`     | MOVE   |
| `docs/MANUAL_GOLDEN_RUNBOOK.md`                                | `howto/backend/runbooks/MANUAL_GOLDEN_RUNBOOK.md`          | MOVE   |
| `docs/RUNBOOK_REAL_LIFE_SCAN_TESTS.md`                         | `howto/backend/runbooks/RUNBOOK_REAL_LIFE_SCAN_TESTS.md`   | MOVE   |
| `docs/timeout-policy-verification.md`                          | `howto/backend/reference/timeout-policy-verification.md`   | MOVE   |

### docs/assistant/ → howto/backend/reference/assistant/

| Old Path                                       | New Path                                                          | Action |
|------------------------------------------------|-------------------------------------------------------------------|--------|
| `docs/assistant/ASSISTANT_PROGRESS_UX_SPEC.md` | `howto/backend/reference/assistant/ASSISTANT_PROGRESS_UX_SPEC.md` | MOVE   |
| `docs/assistant/CURL_VERIFICATION_EXAMPLE.md`  | `howto/backend/reference/assistant/CURL_VERIFICATION_EXAMPLE.md`  | MOVE   |
| `docs/assistant/EDIT_ITEM_V3_TESTING_GUIDE.md` | `howto/backend/reference/assistant/EDIT_ITEM_V3_TESTING_GUIDE.md` | MOVE   |
| `docs/assistant/NETWORKING_POLICY.md`          | `howto/backend/reference/assistant/NETWORKING_POLICY.md`          | MOVE   |
| `docs/assistant/PREFLIGHT_IMPLEMENTATION.md`   | `howto/backend/reference/assistant/PREFLIGHT_IMPLEMENTATION.md`   | MOVE   |
| `docs/assistant/VISION_INSIGHTS.md`            | `howto/backend/reference/assistant/VISION_INSIGHTS.md`            | MOVE   |

### Monitoring-related docs → howto/monitoring/

| Old Path                                    | New Path                                                          | Action |
|---------------------------------------------|-------------------------------------------------------------------|--------|
| `docs/observability/SENTRY_ALERTING.md`     | `howto/monitoring/reference/SENTRY_ALERTING.md`                   | MOVE   |
| `docs/observability/TRIAGE_RUNBOOK.md`      | `howto/monitoring/runbooks/TRIAGE_RUNBOOK.md`                     | MOVE   |
| `docs/telemetry/CONTRACT.md`                | `howto/monitoring/reference/telemetry/CONTRACT.md`                | MOVE   |
| `docs/telemetry/DIAGNOSTICS.md`             | `howto/monitoring/reference/telemetry/DIAGNOSTICS.md`             | MOVE   |
| `docs/telemetry/FACADE.md`                  | `howto/monitoring/reference/telemetry/FACADE.md`                  | MOVE   |
| `docs/telemetry/MOBILE_TELEMETRY_SCHEMA.md` | `howto/monitoring/reference/telemetry/MOBILE_TELEMETRY_SCHEMA.md` | MOVE   |

### Infrastructure/Ops docs → howto/infra/

| Old Path                                        | New Path                                                    | Action |
|-------------------------------------------------|-------------------------------------------------------------|--------|
| `docs/NAS_DEPLOYMENT_SECURITY_CHECKLIST.md`     | `howto/infra/security/NAS_DEPLOYMENT_SECURITY_CHECKLIST.md` | MOVE   |
| `docs/security/SEC-001-remediation.md`          | `howto/infra/security/SEC-001-remediation.md`               | MOVE   |
| `docs/security/SEC-002-database-credentials.md` | `howto/infra/security/SEC-002-database-credentials.md`      | MOVE   |
| `docs/security/ai-assistant-security.md`        | `howto/infra/security/ai-assistant-security.md`             | MOVE   |
| `docs/ops/README.md`                            | `howto/infra/runbooks/ops-README.md`                        | MOVE   |
| `docs/ops/INCIDENT_502_2026-01-06.md`           | `howto/infra/incidents/2026-01-06-incident-502.md`          | MOVE   |
| `docs/ops/STARTUP_CRASH_RC.md`                  | `howto/infra/incidents/STARTUP_CRASH_RC.md`                 | MOVE   |
| `docs/SECURITY.md`                              | `howto/infra/security/SECURITY.md`                          | MOVE   |

### Project/Architecture docs → howto/project/

| Old Path                             | New Path                                              | Action |
|--------------------------------------|-------------------------------------------------------|--------|
| `docs/ARCHITECTURE.md`               | `howto/project/reference/ARCHITECTURE.md`             | MOVE   |
| `docs/ARCH_EYE_VS_FOCUS.md`          | `howto/project/reference/ARCH_EYE_VS_FOCUS.md`        | MOVE   |
| `docs/PRODUCT.md`                    | `howto/project/reference/PRODUCT.md`                  | MOVE   |
| `docs/DECISIONS.md`                  | `howto/project/reference/DECISIONS.md`                | MOVE   |
| `docs/CI_CD.md`                      | `howto/project/workflows/CI_CD.md`                    | MOVE   |
| `docs/GITHUB_ISSUES_TEMPLATES.md`    | `howto/project/workflows/GITHUB_ISSUES_TEMPLATES.md`  | MOVE   |
| `docs/CODEX_CONTEXT.md`              | `howto/project/reference/CODEX_CONTEXT.md`            | MOVE   |
| `docs/INDEX.md`                      | `howto/project/reference/INDEX.md`                    | MOVE   |
| `docs/CLEANUP_REPORT.md`             | `howto/project/reports/CLEANUP_REPORT.md`             | MOVE   |
| `docs/REPO_REVIEW_ACTION_BACKLOG.md` | `howto/project/reports/REPO_REVIEW_ACTION_BACKLOG.md` | MOVE   |
| `docs/REPO_REVIEW_REPORT.md`         | `howto/project/reports/REPO_REVIEW_REPORT.md`         | MOVE   |
| `docs/REVIEW_REPORT.md`              | `howto/project/reports/REVIEW_REPORT.md`              | MOVE   |
| `docs/dev/DEV_FLAVOR_SETTINGS.md`    | `howto/project/reference/DEV_FLAVOR_SETTINGS.md`      | MOVE   |
| `docs/dev/DEV_HEALTH_MONITOR.md`     | `howto/project/reference/DEV_HEALTH_MONITOR.md`       | MOVE   |

---

## monitoring/ → howto/monitoring/

| Old Path                                                                      | New Path                                                                 | Action |
|-------------------------------------------------------------------------------|--------------------------------------------------------------------------|--------|
| `monitoring/README.md`                                                        | `howto/monitoring/README.md`                                             | MOVE   |
| `monitoring/INCIDENT_502_GRAFANA_TUNNEL.md`                                   | `howto/monitoring/incidents/INCIDENT_502_GRAFANA_TUNNEL.md`              | MOVE   |
| `monitoring/TELEMETRY_STATUS.md`                                              | `howto/monitoring/reference/TELEMETRY_STATUS.md`                         | MOVE   |
| `monitoring/grafana/ALERTING_RECONCILE.md`                                    | `howto/monitoring/dashboards/ALERTING_RECONCILE.md`                      | MOVE   |
| `monitoring/grafana/BACKEND_DASHBOARD_TROUBLESHOOTING.md`                     | `howto/monitoring/dashboards/BACKEND_DASHBOARD_TROUBLESHOOTING.md`       | MOVE   |
| `monitoring/grafana/DASHBOARDS.md`                                            | `howto/monitoring/dashboards/DASHBOARDS.md`                              | MOVE   |
| `monitoring/grafana/DASHBOARD_DATA_AUDIT.md`                                  | `howto/monitoring/dashboards/DASHBOARD_DATA_AUDIT.md`                    | MOVE   |
| `monitoring/grafana/MOBILE_TELEMETRY.md`                                      | `howto/monitoring/dashboards/MOBILE_TELEMETRY.md`                        | MOVE   |
| `monitoring/grafana/NO_DATA_ROOTCAUSE.md`                                     | `howto/monitoring/dashboards/NO_DATA_ROOTCAUSE.md`                       | MOVE   |
| `monitoring/grafana/OPENAI_MONITORING.md`                                     | `howto/monitoring/dashboards/OPENAI_MONITORING.md`                       | MOVE   |
| `monitoring/grafana/PERSISTENCE_AUDIT.md`                                     | `howto/monitoring/dashboards/PERSISTENCE_AUDIT.md`                       | MOVE   |
| `monitoring/grafana/PROVISIONING_FIX.md`                                      | `howto/monitoring/dashboards/PROVISIONING_FIX.md`                        | MOVE   |
| `monitoring/grafana/REGRESSION_20260109_DASHBOARDS_NO_DATA.md`                | `howto/monitoring/incidents/2026-01-09-dashboards-no-data.md`            | MOVE   |
| `monitoring/grafana/telemetry-inventory.md`                                   | `howto/monitoring/reference/telemetry-inventory.md`                      | MOVE   |
| `monitoring/grafana/telemetry-truth.md`                                       | `howto/monitoring/reference/telemetry-truth.md`                          | MOVE   |
| `monitoring/incident_data/INCIDENT_DASHBOARDS_NO_DATA_20260110.md`            | `howto/monitoring/incidents/2026-01-10-dashboards-no-data.md`            | MOVE   |
| `monitoring/incident_data/INCIDENT_NO_DATA_20260109.md`                       | `howto/monitoring/incidents/2026-01-09-no-data.md`                       | MOVE   |
| `monitoring/incident_data/INCIDENT_alloy_persistence_healthcheck_20260110.md` | `howto/monitoring/incidents/2026-01-10-alloy-persistence-healthcheck.md` | MOVE   |
| `monitoring/incident_data/RESTART_TEST_full_stack_20260110.md`                | `howto/monitoring/incidents/2026-01-10-restart-test-full-stack.md`       | MOVE   |

---

## deploy/ → howto/infra/

| Old Path                       | New Path                                   | Action |
|--------------------------------|--------------------------------------------|--------|
| `deploy/nas/README.md`         | `howto/infra/deploy/nas-README.md`         | MOVE   |
| `deploy/nas/compose/README.md` | `howto/infra/deploy/nas-compose-README.md` | MOVE   |

---

## Other scattered docs

| Old Path                                                     | New Path                                                     | Action            |
|--------------------------------------------------------------|--------------------------------------------------------------|-------------------|
| `assets/ICON_ASSETS.md`                                      | `howto/app/reference/ICON_ASSETS.md`                         | MOVE              |
| `hooks/README.md`                                            | `howto/project/reference/hooks-README.md`                    | MOVE              |
| `scripts/README.md`                                          | `howto/project/scripts/README.md`                            | MOVE              |
| `md/fixes/PORTRAIT_BBOX_PREVIEW_MISALIGNMENT.md`             | `howto/app/debugging/PORTRAIT_BBOX_PREVIEW_MISALIGNMENT.md`  | MOVE              |
| `md/testing/REGRESSION_TESTS_CLOUD.md`                       | `howto/app/runbooks/REGRESSION_TESTS_CLOUD.md`               | MOVE              |
| `iosApp/Frameworks/README.md`                                | `howto/app/reference/ios-frameworks-README.md`               | MOVE              |
| `androidApp/src/main/java/com/scanium/app/selling/README.md` | `androidApp/src/main/java/com/scanium/app/selling/README.md` | KEEP (code-level) |

---

## Archived docs (docs/_archive/2025-12/)

All files under `docs/_archive/2025-12/` → `howto/archive/2025-12/`

These files are already marked as archived and will be moved in bulk.

---

## Operational Scripts

### Monitoring scripts → howto/monitoring/scripts/

| Old Path                                         | New Path                                              | Action    |
|--------------------------------------------------|-------------------------------------------------------|-----------|
| `deploy/monitoring/scripts/monitoring-dev.sh`    | `howto/monitoring/scripts/monitoring-dev.sh`          | MOVE+STUB |
| `deploy/monitoring/scripts/monitoring-down.sh`   | `howto/monitoring/scripts/monitoring-down.sh`         | MOVE+STUB |
| `deploy/monitoring/scripts/monitoring-start.sh`  | `howto/monitoring/scripts/monitoring-start.sh`        | MOVE+STUB |
| `deploy/monitoring/scripts/monitoring-status.sh` | `howto/monitoring/scripts/monitoring-status.sh`       | MOVE+STUB |
| `deploy/monitoring/scripts/monitoring-stop.sh`   | `howto/monitoring/scripts/monitoring-stop.sh`         | MOVE+STUB |
| `deploy/monitoring/scripts/monitoring-up.sh`     | `howto/monitoring/scripts/monitoring-up.sh`           | MOVE+STUB |
| `scripts/monitoring/deploy-monitoring-nas.sh`    | `howto/monitoring/scripts/deploy-monitoring-nas.sh`   | MOVE+STUB |
| `scripts/monitoring/rollback-monitoring-nas.sh`  | `howto/monitoring/scripts/rollback-monitoring-nas.sh` | MOVE+STUB |
| `scripts/monitoring/verify-monitoring.sh`        | `howto/monitoring/scripts/verify-monitoring.sh`       | MOVE+STUB |

### Infrastructure scripts → howto/infra/scripts/

| Old Path                                | New Path                                        | Action    |
|-----------------------------------------|-------------------------------------------------|-----------|
| `deploy/nas/scripts/smoke-api-gate.sh`  | `howto/infra/scripts/smoke-api-gate.sh`         | MOVE+STUB |
| `scripts/ops/collect_support_bundle.sh` | `howto/infra/scripts/collect_support_bundle.sh` | MOVE+STUB |
| `scripts/ops/docker_status.sh`          | `howto/infra/scripts/docker_status.sh`          | MOVE+STUB |
| `scripts/ops/nas_vision_preflight.sh`   | `howto/infra/scripts/nas_vision_preflight.sh`   | MOVE+STUB |
| `scripts/ops/smoke-loop.sh`             | `howto/infra/scripts/smoke-loop.sh`             | MOVE+STUB |
| `scripts/ops/smoke.sh`                  | `howto/infra/scripts/smoke.sh`                  | MOVE+STUB |
| `scripts/ops/lib/common.sh`             | `howto/infra/scripts/lib/common.sh`             | MOVE+STUB |
| `scripts/app/deploy-backend-nas.sh`     | `howto/infra/scripts/deploy-backend-nas.sh`     | MOVE+STUB |
| `scripts/app/rollback-backend-nas.sh`   | `howto/infra/scripts/rollback-backend-nas.sh`   | MOVE+STUB |

### Backend scripts → howto/backend/scripts/

| Old Path                          | New Path                                | Action    |
|-----------------------------------|-----------------------------------------|-----------|
| `scripts/backend/check-status.sh` | `howto/backend/scripts/check-status.sh` | MOVE+STUB |
| `scripts/backend/start-dev.sh`    | `howto/backend/scripts/start-dev.sh`    | MOVE+STUB |
| `scripts/backend/stop-dev.sh`     | `howto/backend/scripts/stop-dev.sh`     | MOVE+STUB |
| `scripts/backend/verify-setup.sh` | `howto/backend/scripts/verify-setup.sh` | MOVE+STUB |

### App scripts → howto/app/scripts/

| Old Path                                        | New Path                                             | Action    |
|-------------------------------------------------|------------------------------------------------------|-----------|
| `scripts/android-build-install-dev.sh`          | `howto/app/scripts/android-build-install-dev.sh`     | MOVE+STUB |
| `scripts/android-configure-backend-dev.sh`      | `howto/app/scripts/android-configure-backend-dev.sh` | MOVE+STUB |
| `scripts/android/build-install-devdebug.sh`     | `howto/app/scripts/build-install-devdebug.sh`        | MOVE+STUB |
| `scripts/android/set-backend-cloudflare-dev.sh` | `howto/app/scripts/set-backend-cloudflare-dev.sh`    | MOVE+STUB |
| `scripts/termux/build_debug_to_downloads.sh`    | `howto/app/scripts/build_debug_to_downloads.sh`      | MOVE+STUB |
| `scripts/termux/remote_autofix_tests.sh`        | `howto/app/scripts/remote_autofix_tests.sh`          | MOVE+STUB |
| `scripts/termux/remote_build_pull_apk.sh`       | `howto/app/scripts/remote_build_pull_apk.sh`         | MOVE+STUB |
| `scripts/termux/termux-storage-setup.sh`        | `howto/app/scripts/termux-storage-setup.sh`          | MOVE+STUB |
| `scripts/dev/capture_startup_crash.sh`          | `howto/app/scripts/capture_startup_crash.sh`         | MOVE+STUB |
| `scripts/dev/test_ml_kit_detection.sh`          | `howto/app/scripts/test_ml_kit_detection.sh`         | MOVE+STUB |

### Project/CI scripts → howto/project/scripts/

| Old Path                                | New Path                                              | Action    |
|-----------------------------------------|-------------------------------------------------------|-----------|
| `scripts/build.sh`                      | `howto/project/scripts/build.sh`                      | MOVE+STUB |
| `scripts/ci/doctor.sh`                  | `howto/project/scripts/ci/doctor.sh`                  | MOVE+STUB |
| `scripts/ci/gradle_sanity.sh`           | `howto/project/scripts/ci/gradle_sanity.sh`           | MOVE+STUB |
| `scripts/ci/local-ci.sh`                | `howto/project/scripts/ci/local-ci.sh`                | MOVE+STUB |
| `scripts/ci/run_coverage.sh`            | `howto/project/scripts/ci/run_coverage.sh`            | MOVE+STUB |
| `scripts/ci/run_security.sh`            | `howto/project/scripts/ci/run_security.sh`            | MOVE+STUB |
| `scripts/dev/autofix_tests.sh`          | `howto/project/scripts/dev/autofix_tests.sh`          | MOVE+STUB |
| `scripts/dev/extract_failures.sh`       | `howto/project/scripts/dev/extract_failures.sh`       | MOVE+STUB |
| `scripts/dev/gradle17.sh`               | `howto/project/scripts/dev/gradle17.sh`               | MOVE+STUB |
| `scripts/dev/install-hooks.sh`          | `howto/project/scripts/dev/install-hooks.sh`          | MOVE+STUB |
| `scripts/dev/run_tests.sh`              | `howto/project/scripts/dev/run_tests.sh`              | MOVE+STUB |
| `scripts/dev/verify-backend-config.sh`  | `howto/project/scripts/dev/verify-backend-config.sh`  | MOVE+STUB |
| `scripts/dev/verify_scripts.sh`         | `howto/project/scripts/dev/verify_scripts.sh`         | MOVE+STUB |
| `scripts/lib/common.sh`                 | `howto/project/scripts/lib/common.sh`                 | MOVE+STUB |
| `scripts/tools/create-github-issues.sh` | `howto/project/scripts/tools/create-github-issues.sh` | MOVE+STUB |

### Archived scripts

| Old Path                                                | New Path                                                | Action |
|---------------------------------------------------------|---------------------------------------------------------|--------|
| `docs/_archive/2025-12/go-live/CREATE_ISSUES.sh`        | `howto/archive/2025-12/go-live/CREATE_ISSUES.sh`        | MOVE   |
| `docs/_archive/2025-12/go-live/CREATE_ISSUES_PHASE2.sh` | `howto/archive/2025-12/go-live/CREATE_ISSUES_PHASE2.sh` | MOVE   |
| `docs/_archive/2025-12/go-live/CREATE_LABELS.sh`        | `howto/archive/2025-12/go-live/CREATE_LABELS.sh`        | MOVE   |

---

## Files to KEEP in place

These files stay in their original locations (code-level documentation):

| Path                                                         | Reason                    |
|--------------------------------------------------------------|---------------------------|
| `README.md`                                                  | Root README for repo      |
| `PRIVACY.MD`                                                 | Legal document            |
| `.claude/agents/*.md`                                        | Agent configuration files |
| `androidApp/src/main/java/com/scanium/app/selling/README.md` | Code-level module doc     |

---

## Summary Statistics

- **Total MD files inventoried**: 228
- **Files to MOVE**: ~190
- **Files to KEEP in place**: ~5
- **Files already archived**: ~95 (moving to howto/archive/2025-12/)
- **Scripts to MOVE**: ~47
- **Scripts to STUB**: ~47 (all moved scripts get stubs)
