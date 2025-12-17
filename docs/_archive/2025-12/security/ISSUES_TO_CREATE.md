
---

***REMOVED******REMOVED*** P2 - Medium/Low Priority Issues

***REMOVED******REMOVED******REMOVED*** Remaining Issues (SEC-009 through SEC-020)

The following issues cover:
- SEC-009: Certificate pinning guidance
- SEC-010: FLAG_SECURE for screenshots
- SEC-011, SEC-019: Image cleanup policies  
- SEC-012: Privacy policy requirement
- SEC-004, SEC-020: Auth and crypto documentation

**Note:** For brevity, P2 issues can be created with shorter templates. Key points:

- **SEC-009**: Document cert pinning tradeoffs (not recommended per Android guidance)
- **SEC-010**: Add WindowManager.LayoutParams.FLAG_SECURE to sensitive screens
- **SEC-011/019**: Implement periodic cache cleanup (24h retention)
- **SEC-012**: Create privacy policy before Play Store submission
- **SEC-004**: Document OAuth implementation strategy for eBay
- **SEC-020**: Document cryptography guidelines (use Jetpack Security)

---

***REMOVED******REMOVED*** Issue Creation Script

Create \`docs/security/create_issues.sh\`:

\`\`\`bash
***REMOVED***!/bin/bash
set -e

echo "Creating security issues via gh CLI..."

***REMOVED*** Check gh authentication
if ! gh auth status >/dev/null 2>&1; then
    echo "Error: gh CLI not authenticated"
    echo "Run: gh auth login"
    exit 1
fi

***REMOVED*** Source each issue command from this file
***REMOVED*** Extract gh commands and execute them

echo "✅ All 18 security issues created successfully"
echo "View issues: gh issue list --label 'severity:critical,severity:high,severity:medium'"
\`\`\`

---

***REMOVED******REMOVED*** Manual Creation

If gh CLI is not available, create issues manually via GitHub web interface using the bodies above.

