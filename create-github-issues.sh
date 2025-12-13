***REMOVED***!/bin/bash

***REMOVED*** Script to create GitHub issues from markdown files
***REMOVED*** Usage: ./create-github-issues.sh

set -e

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

echo "Creating GitHub issues for $REPO..."
echo "Found $(ls -1 $ISSUES_DIR/*.md | wc -l) issue files"
echo ""

CREATED=0
FAILED=0

for issue_file in $ISSUES_DIR/*.md; do
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
