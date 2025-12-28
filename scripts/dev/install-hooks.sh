#!/bin/bash
# Install git hooks for Scanium development
# Run this script once after cloning the repository

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HOOK_TEMPLATE_DIR="$(cd "${SCRIPT_DIR}/../../hooks" && pwd)"
GIT_HOOKS_DIR="$(git rev-parse --git-dir)/hooks"

echo "Installing git hooks..."

# Install pre-commit hook (ktlint - DX-002)
if [ -f "$HOOK_TEMPLATE_DIR/pre-commit" ]; then
    cp "$HOOK_TEMPLATE_DIR/pre-commit" "$GIT_HOOKS_DIR/pre-commit"
    chmod +x "$GIT_HOOKS_DIR/pre-commit"
    echo "Installed pre-commit hook (ktlint)"
else
    echo "pre-commit hook not found in $HOOK_TEMPLATE_DIR"
    exit 1
fi

# Install pre-push hook
if [ -f "$HOOK_TEMPLATE_DIR/pre-push" ]; then
    cp "$HOOK_TEMPLATE_DIR/pre-push" "$GIT_HOOKS_DIR/pre-push"
    chmod +x "$GIT_HOOKS_DIR/pre-push"
    echo "Installed pre-push hook (JVM checks)"
else
    echo "pre-push hook not found in $HOOK_TEMPLATE_DIR"
    exit 1
fi

echo ""
echo "Git hooks installed successfully!"
echo ""
echo "Hooks installed:"
echo ""
echo "  pre-commit: Runs ktlint on staged Kotlin files"
echo "    - Auto-fix: ./gradlew ktlintFormat"
echo "    - Bypass: git commit --no-verify"
echo ""
echo "  pre-push: Runs JVM-only validation before push"
echo "    - JVM tests for shared modules (no Android SDK required)"
echo "    - Portability checks"
echo "    - Legacy import checks"
echo "    - Bypass: git push --no-verify"
