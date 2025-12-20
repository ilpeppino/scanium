#!/bin/bash
# Install git hooks for Scanium development
# Run this script once after cloning the repository

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GIT_HOOKS_DIR="$(git rev-parse --git-dir)/hooks"

echo "Installing git hooks..."

# Install pre-push hook
if [ -f "$SCRIPT_DIR/pre-push" ]; then
    cp "$SCRIPT_DIR/pre-push" "$GIT_HOOKS_DIR/pre-push"
    chmod +x "$GIT_HOOKS_DIR/pre-push"
    echo "✓ Installed pre-push hook"
else
    echo "✗ pre-push hook not found in $SCRIPT_DIR"
    exit 1
fi

echo ""
echo "✅ Git hooks installed successfully!"
echo ""
echo "The pre-push hook will run JVM-only validation before each push."
echo "This includes:"
echo "  - JVM tests for shared modules (no Android SDK required)"
echo "  - Portability checks"
echo "  - Legacy import checks"
echo ""
echo "To bypass the hook (not recommended), use: git push --no-verify"
