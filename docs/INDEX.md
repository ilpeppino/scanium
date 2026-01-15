***REMOVED*** Scanium Documentation Index

This is the canonical entry point for all Scanium documentation. Documentation is organized by topic and component.

***REMOVED******REMOVED*** Quick Links

| Topic | Description |
|-------|-------------|
| [README](../README.md) | Project overview, features, quick start |
| [Architecture](***REMOVED***architecture) | System design and component structure |
| [Development](***REMOVED***development) | Setup, workflow, testing |
| [Operations](***REMOVED***operations) | Releases, deployment, monitoring |
| [Security](***REMOVED***security) | Security posture and guidelines |

---

***REMOVED******REMOVED*** Architecture

***REMOVED******REMOVED******REMOVED*** System Overview
- **[Camera Pipeline](./architecture-camera-pipeline.md)** - CameraX integration, frame analysis, detection routing
- **[Item Aggregation](./architecture-item-aggregation.md)** - Deduplication strategy, similarity scoring, session management

***REMOVED******REMOVED******REMOVED*** Platform Parity (iOS)
- **[Android Baseline](./parity/ANDROID_BASELINE.md)** - Current Android feature inventory
- **[iOS Current State](./parity/IOS_CURRENT.md)** - iOS implementation status
- **[Gap Matrix](./parity/GAP_MATRIX.md)** - Feature gaps between platforms
- **[Parity Plan](./parity/PARITY_PLAN.md)** - Path to feature parity
- **[PR Roadmap](./parity/PR_ROADMAP.md)** - Pull request roadmap

***REMOVED******REMOVED******REMOVED*** Pricing System
- **[Pricing Insights](./PRICING_INSIGHTS.md)** - Price estimation logic and data sources

***REMOVED******REMOVED******REMOVED*** Vision & Enrichment
- **[Phase 1 State](./vision/PHASE1_current_state.md)** - Vision enrichment current status

---

***REMOVED******REMOVED*** Development

***REMOVED******REMOVED******REMOVED*** Getting Started
- **[README](../README.md)** - Prerequisites, setup, building, testing

***REMOVED******REMOVED******REMOVED*** KMP Migration
- **[Migration Map](../howto/MIGRATION_MAP.md)** - Kotlin Multiplatform migration strategy

***REMOVED******REMOVED******REMOVED*** Reference Guides
- **[Export Formats](../howto/app/reference/EXPORT_FORMATS.md)** - CSV/ZIP export specifications
- **[Flavor Gating](../howto/app/reference/FLAVOR_GATING.md)** - Build variant feature flags
- **[Item Editing](../howto/app/reference/ITEM_EDITING.md)** - Item edit flow
- **[Settings IA](../howto/app/reference/SETTINGS_IA.md)** - Settings information architecture
- **[Scanning Guidance](../howto/app/reference/SCANNING_GUIDANCE.md)** - User guidance system
- **[Motion Language](../howto/app/reference/MOTION_LANGUAGE.md)** - Animation specifications

***REMOVED******REMOVED******REMOVED*** Backend Development
- **[Items Sync API](../backend/howto/backend/items-sync.md)** - Multi-device sync implementation

***REMOVED******REMOVED******REMOVED*** Debugging Guides
Located in `howto/app/debugging/`:
- Portrait bbox issues, camera stalls, thumbnail persistence, etc.

---

***REMOVED******REMOVED*** Operations

***REMOVED******REMOVED******REMOVED*** Releases
- **[Release Checklist](../howto/app/releases/RELEASE_CHECKLIST.md)** - Pre-release verification steps
- **[Release & Rollback](../howto/app/releases/RELEASE_AND_ROLLBACK.md)** - Deployment and rollback procedures
- **[Beta Validation](../howto/app/releases/BETA_VALIDATION.md)** - Beta testing workflow

***REMOVED******REMOVED******REMOVED*** QA & Testing
- **[Regression Tests](../howto/app/runbooks/REGRESSION_TESTS_CLOUD.md)** - Cloud regression test runbook

***REMOVED******REMOVED******REMOVED*** Infrastructure
- **[Infrastructure Overview](../howto/infra/README.md)** - Deployment infrastructure
- **[NAS Deployment](../howto/infra/deploy/nas-README.md)** - NAS-based deployment guide
- **[Ops Runbook](../howto/infra/runbooks/ops-README.md)** - Operations procedures

***REMOVED******REMOVED******REMOVED*** Monitoring
- **[Monitoring Stack](../monitoring/CHANGELOG.md)** - LGTM stack changelog
- **[Grafana Access](../monitoring/GRAFANA_ACCESS_GUIDE.md)** - Accessing Grafana dashboards
- **[Cloudflare Tunnel](../monitoring/CLOUDFLARE_TUNNEL_SETUP.md)** - Tunnel configuration
- **[Tunnel Troubleshooting](./cloudflared-tunnel-troubleshooting.md)** - Common tunnel issues

***REMOVED******REMOVED******REMOVED*** Incidents
Located in `howto/infra/incidents/`:
- **[2026-01-06 502 Incident](../howto/infra/incidents/2026-01-06-incident-502.md)**
- **[Startup Crash RC](../howto/infra/incidents/STARTUP_CRASH_RC.md)**

---

***REMOVED******REMOVED*** Security

- **[Security Guidelines](../howto/infra/security/SECURITY.md)** - Security policies and best practices
- **[NAS Security Checklist](../howto/infra/security/NAS_DEPLOYMENT_SECURITY_CHECKLIST.md)** - Deployment hardening
- **[AI Assistant Security](../howto/infra/security/ai-assistant-security.md)** - LLM integration security
- **[SEC-001 Remediation](../howto/infra/security/SEC-001-remediation.md)** - Token encryption
- **[SEC-002 Database Credentials](../howto/infra/security/SEC-002-database-credentials.md)** - Secrets management

---

***REMOVED******REMOVED*** Archive

Historical documentation from December 2025 is archived in `howto/archive/2025-12/`. Key archived docs:

- **Architecture Decision Records (ADRs)** - `howto/archive/2025-12/ADR/`
- **Comprehensive Audit Report** - `howto/archive/2025-12/COMPREHENSIVE_AUDIT_REPORT.md`
- **Review Report** - `howto/archive/2025-12/REVIEW_REPORT.md`
- **KMP Migration Plans** - `howto/archive/2025-12/kmp-migration/`

---

***REMOVED******REMOVED*** Contributing to Docs

1. **New docs**: Add to appropriate section in `docs/` or `howto/`
2. **Update this index**: When adding new docs, update INDEX.md
3. **Cross-references**: Use relative paths from the doc's location
4. **Archive old docs**: Move superseded docs to `howto/archive/YYYY-MM/`

---

*Last updated: 2026-01-15*
