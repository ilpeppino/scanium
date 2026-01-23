# Project Documentation

Repository conventions, workflows, and contributor documentation.

## Subfolders

| Folder                   | Contents                                     |
|--------------------------|----------------------------------------------|
| [reference/](reference/) | Architecture, dev guides, coding conventions |
| [reports/](reports/)     | Review reports, cleanup summaries            |
| [scripts/](scripts/)     | Build, CI, and development scripts           |
| [workflows/](workflows/) | CI/CD, GitHub Actions, release workflows     |

## Quick Links

### Reference

- [Architecture](reference/ARCHITECTURE.md)
- [Dev Guide](reference/DEV_GUIDE.md)
- [Product Overview](reference/PRODUCT.md)
- [Decisions Log](reference/DECISIONS.md)
- [Agents Configuration](reference/AGENTS.md)

### Workflows

- [CI/CD](workflows/CI_CD.md)
- [GitHub Issue Templates](workflows/GITHUB_ISSUES_TEMPLATES.md)

### Scripts

- `build.sh` - Main build script
- `ci/local-ci.sh` - Run CI locally
- `dev/run_tests.sh` - Run test suite
- `dev/install-hooks.sh` - Install git hooks
