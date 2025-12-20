***REMOVED***!/bin/bash

***REMOVED*** Script to create GitHub issues from markdown files
***REMOVED*** Usage: ./scripts/tools/create-github-issues.sh

set -euo pipefail
shopt -s nullglob

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "$REPO_ROOT"

REPO="ilpeppino/scanium"
ISSUES_DIR="docs/issues"

***REMOVED*** Check if gh is installed
if ! command -v gh &> /dev/null; then
    echo "Error: GitHub CLI (gh) is not installed."
    echo "Install it from: https://cli.github.com/"
    exit 1
fi

***REMOVED*** Check if authenticated
if ! gh auth status &> /dev/null; then
    echo "Error: Not authenticated with GitHub."
    echo "Run: gh auth login"
    exit 1
fi

ISSUE_FILES=($ISSUES_DIR/*.md)

if [ ${***REMOVED***ISSUE_FILES[@]} -eq 0 ]; then
    echo "No issue templates found under $ISSUES_DIR"
    exit 0
fi

echo "Creating GitHub issues for $REPO..."
echo "Found ${***REMOVED***ISSUE_FILES[@]} issue files"
echo ""

CREATED=0
FAILED=0

for issue_file in "${ISSUE_FILES[@]}"; do
    echo "Processing: $(basename $issue_file)"

    ***REMOVED*** Extract title (first line without the ***REMOVED*** prefix)
    TITLE=$(head -1 "$issue_file" | sed 's/^***REMOVED*** //')

    ***REMOVED*** Extract labels from the second line
    LABELS=$(grep -m1 "^\*\*Labels:\*\*" "$issue_file" | sed 's/.*`\(.*\)`.*/\1/' | sed 's/`, `/ /g' | sed 's/`//g')

    ***REMOVED*** Get the body (everything after the title)
    BODY=$(tail -n +2 "$issue_file")

    echo "  Title: $TITLE"
    echo "  Labels: $LABELS"

    ***REMOVED*** Create the issue
    if ISSUE_URL=$(gh issue create \
        --repo "$REPO" \
        --title "$TITLE" \
        --body "$BODY" \
        --label "$LABELS" 2>&1); then

        echo "  ✅ Created: $ISSUE_URL"
        ((CREATED++))
    else
        echo "  ❌ Failed: $ISSUE_URL"
        ((FAILED++))
    fi

    echo ""

    ***REMOVED*** Rate limiting: wait 1 second between requests
    sleep 1
done

echo "================================================"
echo "Summary:"
echo "  Created: $CREATED issues"
echo "  Failed:  $FAILED issues"
echo "================================================"

if [ $CREATED -gt 0 ]; then
    echo ""
    echo "View all issues: https://github.com/$REPO/issues"
fi
