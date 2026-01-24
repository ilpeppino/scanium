# Contributing to Scanium

Thank you for your interest in contributing to Scanium! This document provides guidelines and instructions for contributing to the project.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [How to Contribute](#how-to-contribute)
- [Coding Standards](#coding-standards)
- [Commit Guidelines](#commit-guidelines)
- [Pull Request Process](#pull-request-process)
- [Testing](#testing)
- [Documentation](#documentation)
- [Questions and Support](#questions-and-support)

## Code of Conduct

### Our Pledge

We are committed to providing a welcoming and inclusive environment for all contributors. We expect everyone to:

- **Be respectful** and considerate in communication
- **Be collaborative** and help others learn
- **Accept constructive criticism** gracefully
- **Focus on what's best** for the community
- **Show empathy** towards other community members

### Unacceptable Behavior

- Harassment, discrimination, or offensive comments
- Trolling, insulting/derogatory comments, or personal attacks
- Public or private harassment
- Publishing others' private information without permission
- Other conduct that could reasonably be considered inappropriate

### Enforcement

Violations of the Code of Conduct should be reported to scanium@gtemp1.com. All complaints will be reviewed and investigated promptly and fairly.

## Getting Started

### Prerequisites

**For Android Development:**
- **JDK 17** (required by Kotlin and Android)
- **Android Studio** (latest stable version)
- **Android SDK** (API 24+)

**For Backend Development:**
- **Node.js 20+**
- **PostgreSQL 14+**
- **Docker** (optional, for running services)

### Fork and Clone

1. **Fork the repository** on GitHub
2. **Clone your fork** locally:
   ```bash
   git clone scanium@gtemp1.com:YOUR_USERNAME/scanium.git
   cd scanium
   ```
3. **Add upstream remote**:
   ```bash
   git remote add upstream scanium@gtemp1.com:ilpeppino/scanium.git
   ```

## Development Setup

### Android App Setup

1. **Open project** in Android Studio
2. **Sync Gradle** files
3. **Create `local.properties`** (see `local.properties.example`):
   ```properties
   sdk.dir=/path/to/Android/sdk
   scanium.api.base.url=http://localhost:3000
   scanium.api.key=your-dev-api-key
   ```
4. **Build the app**:
   ```bash
   ./gradlew :androidApp:assembleDevDebug
   ```

### Backend Setup

1. **Install dependencies**:
   ```bash
   cd backend
   npm install
   ```
2. **Set up environment variables** (see `backend/.env.example`):
   ```bash
   cp .env.example .env
   # Edit .env with your configuration
   ```
3. **Run database migrations**:
   ```bash
   npm run prisma:migrate
   ```
4. **Start development server**:
   ```bash
   npm run dev
   ```

See [`CLAUDE.md`](CLAUDE.md) for comprehensive build instructions.

## How to Contribute

### Types of Contributions

We welcome various types of contributions:

- 🐛 **Bug fixes**
- ✨ **New features**
- 📝 **Documentation improvements**
- 🧪 **Tests**
- 🎨 **UI/UX enhancements**
- ♻️ **Code refactoring**
- 🌐 **Translations/i18n**
- 🔧 **Build/CI improvements**

### Finding Issues to Work On

- Check the [Issues](https://github.com/ilpeppino/scanium/issues) tab
- Look for issues labeled `good first issue` or `help wanted`
- Comment on an issue to claim it before starting work

### Proposing New Features

Before implementing a major new feature:

1. **Open an issue** describing the feature
2. **Discuss the approach** with maintainers
3. **Get approval** before writing code
4. **Reference the issue** in your pull request

This prevents duplicate work and ensures your contribution aligns with the project's goals.

## Coding Standards

### Kotlin (Android)

- **Follow** [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- **Use** 4 spaces for indentation
- **Maximum line length**: 120 characters
- **Naming**:
  - Classes: `PascalCase`
  - Functions/variables: `camelCase`
  - Constants: `SCREAMING_SNAKE_CASE`
  - Private properties: prefix with `_` (e.g., `_viewModel`)

**Example:**
```kotlin
class ObjectDetectorClient @Inject constructor(
    private val context: Context,
    private val logger: Logger
) {
    private val _detectionResults = MutableStateFlow<List<DetectedObject>>(emptyList())
    val detectionResults: StateFlow<List<DetectedObject>> = _detectionResults.asStateFlow()

    fun startDetection() {
        // Implementation
    }
}
```

### TypeScript (Backend)

- **Follow** [TypeScript Best Practices](https://www.typescriptlang.org/docs/handbook/declaration-files/do-s-and-don-ts.html)
- **Use** 2 spaces for indentation
- **Maximum line length**: 100 characters
- **Prefer** `const` over `let`, avoid `var`
- **Use** arrow functions for callbacks
- **Enable strict mode** in TypeScript config

**Example:**
```typescript
export class ItemService {
  constructor(
    private readonly prisma: PrismaClient,
    private readonly logger: Logger
  ) {}

  async enrichItem(itemId: string, options: EnrichOptions): Promise<EnrichedItem> {
    // Implementation
  }
}
```

### General Guidelines

- **Write self-documenting code** with clear variable/function names
- **Add comments** only when the "why" isn't obvious from the code
- **Keep functions small** (< 30 lines ideally)
- **Avoid premature optimization** - prioritize readability
- **Handle errors** gracefully with proper error messages
- **Avoid magic numbers** - use named constants
- **Don't commit commented-out code** - use git history instead

## Commit Guidelines

We follow [Conventional Commits](https://www.conventionalcommits.org/) for clear and structured commit history.

### Commit Message Format

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Types

- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `style`: Code style changes (formatting, no logic change)
- `refactor`: Code refactoring
- `test`: Adding or updating tests
- `chore`: Build process, dependencies, tooling
- `perf`: Performance improvements
- `ci`: CI/CD changes

### Scope

Optional, indicates what part of the codebase:
- `android`: Android app
- `backend`: Backend service
- `monitoring`: Observability stack
- `docs`: Documentation
- `ci`: Continuous integration

### Examples

```
feat(android): add barcode scanning support

Implement QR code and barcode detection using ML Kit's barcode
scanner API. Supports all common barcode formats.

Closes #123
```

```
fix(backend): prevent SQL injection in item search

Sanitize user input in search queries using parameterized statements.

Security issue reported by @security-researcher
```

```
docs: update installation instructions for macOS

Add troubleshooting section for common setup issues on Apple Silicon.
```

### Pre-commit Hooks

**IMPORTANT**: This repository uses pre-commit hooks to prevent committing secrets:

- **Gitleaks** scans for API keys, passwords, tokens
- **Commits will be blocked** if secrets are detected
- See [`.gitleaks.toml`](.gitleaks.toml) for configuration

If you need to bypass the hook (only for false positives):
```bash
git commit --no-verify
```

**Better approach**: Add false positives to `.gitleaks.toml` allowlist.

## Pull Request Process

### Before Submitting

1. **Update your fork** with the latest upstream changes:
   ```bash
   git fetch upstream
   git rebase upstream/main
   ```

2. **Run tests** and ensure they pass:
   ```bash
   # Android
   ./gradlew test

   # Backend
   cd backend && npm test
   ```

3. **Run linters**:
   ```bash
   # Android
   ./gradlew lint

   # Backend
   cd backend && npm run lint
   ```

4. **Test locally** - verify your changes work as expected

### Creating a Pull Request

1. **Push to your fork**:
   ```bash
   git push origin feature/your-feature-name
   ```

2. **Open a pull request** on GitHub

3. **Fill out the PR template** (if available) with:
   - **Description** of changes
   - **Related issue** number (e.g., "Closes #123")
   - **Testing done** (manual tests, automated tests)
   - **Screenshots** (for UI changes)
   - **Breaking changes** (if any)

4. **Request review** from maintainers

### PR Title Format

Follow the same format as commit messages:
```
feat(android): add dark mode support
```

### Review Process

- **Automated checks** must pass (CI/CD, linting, tests)
- **At least one maintainer** must approve
- **Address feedback** by pushing new commits (don't force-push during review)
- **Squash commits** before merging (maintainer will do this)

### After Approval

- Maintainer will **merge** your PR
- Your contribution will be included in the **next release**
- You'll be credited in the **release notes**

## Testing

### Android Testing

**Unit Tests** (required for new features):
```bash
./gradlew test
```

**Instrumented Tests** (optional but recommended):
```bash
./gradlew connectedAndroidTest
```

**Code Coverage**:
```bash
./gradlew koverVerify
```

Aim for **>80% coverage** on new code.

### Backend Testing

**Unit Tests**:
```bash
cd backend
npm test
```

**Type Checking**:
```bash
npm run typecheck
```

**Coverage**:
```bash
npm run test:coverage
```

### Writing Tests

- **Test behavior**, not implementation
- **Use descriptive test names**: `it('should return 404 when item not found')`
- **Follow AAA pattern**: Arrange, Act, Assert
- **Mock external dependencies** (APIs, databases)
- **Test edge cases** and error scenarios

**Example (Kotlin):**
```kotlin
@Test
fun `detectObjects should return empty list when image is blank`() {
    // Arrange
    val blankImage = createBlankBitmap()

    // Act
    val results = detector.detectObjects(blankImage)

    // Assert
    assertThat(results).isEmpty()
}
```

## Documentation

### Code Documentation

- **Document public APIs** with KDoc (Kotlin) or JSDoc (TypeScript)
- **Explain complex algorithms** with comments
- **Update README** if you add new features
- **Add examples** for non-obvious usage

**Example (Kotlin):**
```kotlin
/**
 * Detects objects in the provided image using ML Kit.
 *
 * @param image The bitmap to analyze
 * @param confidence Minimum confidence threshold (0.0-1.0)
 * @return List of detected objects with bounding boxes
 * @throws IllegalArgumentException if confidence is out of range
 */
fun detectObjects(image: Bitmap, confidence: Float = 0.5f): List<DetectedObject>
```

### Documentation Updates

If your PR changes:
- **User-facing behavior**: Update README.md
- **API endpoints**: Update API documentation
- **Configuration**: Update example config files
- **Build process**: Update CLAUDE.md

## Questions and Support

### Where to Ask

- **Questions**: Open a [Discussion](https://github.com/ilpeppino/scanium/discussions)
- **Bugs**: Open an [Issue](https://github.com/ilpeppino/scanium/issues)
- **Security**: Email scanium@gtemp1.com (see [SECURITY.md](SECURITY.md))

### Getting Help

If you're stuck:
1. **Check existing issues** and discussions
2. **Read the documentation** (README, CLAUDE.md, howto/)
3. **Ask in a discussion** with specific details
4. **Be patient** - maintainers are volunteers

## Recognition

Contributors are recognized in:
- **Release notes** for each version
- **GitHub contributors graph**
- **Special mentions** for significant contributions

Thank you for contributing to Scanium! 🎉

---

**Last Updated**: 2026-01-23
**Questions?** Open a [Discussion](https://github.com/ilpeppino/scanium/discussions) or email scanium@gtemp1.com
