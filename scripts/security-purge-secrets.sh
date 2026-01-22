***REMOVED***!/bin/bash
***REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED***
***REMOVED*** Git History Secret Purge Script for Scanium
***REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED***
***REMOVED***
***REMOVED*** WARNING: This script REWRITES GIT HISTORY and will BREAK existing clones/forks
***REMOVED***
***REMOVED*** BEFORE RUNNING:
***REMOVED***   1. Coordinate with all team members
***REMOVED***   2. Ensure all local changes are committed and pushed
***REMOVED***   3. Create a full backup of the repository
***REMOVED***   4. Rotate all secrets being purged (they'll remain in old clones!)
***REMOVED***
***REMOVED*** AFTER RUNNING:
***REMOVED***   1. Force push to remote: git push origin --force --all
***REMOVED***   2. All team members must re-clone the repository (do NOT pull/rebase)
***REMOVED***   3. Delete old backups/clones containing secrets
***REMOVED***
***REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED***

set -euo pipefail

***REMOVED*** Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' ***REMOVED*** No Color

***REMOVED*** Configuration
REPO_DIR="/Users/family/dev/scanium"
BACKUP_DIR="${REPO_DIR}/../scanium-backup-$(date +%Y%m%d-%H%M%S)"
DRY_RUN="${DRY_RUN:-true}"  ***REMOVED*** Set DRY_RUN=false to actually run

echo -e "${BLUE}╔════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║    Scanium Git History Secret Purge Script                    ║${NC}"
echo -e "${BLUE}╔════════════════════════════════════════════════════════════════╗${NC}"
echo ""

***REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED***
***REMOVED*** Step 1: Pre-flight Checks
***REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED***

echo -e "${YELLOW}[1/6] Running pre-flight checks...${NC}"

***REMOVED*** Check if git-filter-repo is installed
if ! command -v git-filter-repo &> /dev/null; then
    echo -e "${RED}ERROR: git-filter-repo is not installed${NC}"
    echo "Install with: brew install git-filter-repo"
    exit 1
fi

***REMOVED*** Check if we're in a git repository
if [ ! -d "${REPO_DIR}/.git" ]; then
    echo -e "${RED}ERROR: ${REPO_DIR} is not a git repository${NC}"
    exit 1
fi

cd "${REPO_DIR}"

***REMOVED*** Check for uncommitted changes
if ! git diff-index --quiet HEAD -- 2>/dev/null; then
    echo -e "${RED}ERROR: You have uncommitted changes${NC}"
    echo "Commit or stash your changes before running this script"
    git status --short
    exit 1
fi

echo -e "${GREEN}✓ Pre-flight checks passed${NC}"
echo ""

***REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED***
***REMOVED*** Step 2: Create Backup
***REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED***

echo -e "${YELLOW}[2/6] Creating backup...${NC}"

if [ "${DRY_RUN}" = "true" ]; then
    echo -e "${BLUE}[DRY RUN] Would create backup at: ${BACKUP_DIR}${NC}"
else
    echo "Creating backup at: ${BACKUP_DIR}"
    cp -R "${REPO_DIR}" "${BACKUP_DIR}"
    echo -e "${GREEN}✓ Backup created successfully${NC}"
    echo "If anything goes wrong, restore with:"
    echo "  rm -rf ${REPO_DIR}"
    echo "  mv ${BACKUP_DIR} ${REPO_DIR}"
fi

echo ""

***REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED***
***REMOVED*** Step 3: Define Secrets to Purge
***REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED***

echo -e "${YELLOW}[3/6] Defining secrets to purge...${NC}"

***REMOVED*** Define all secrets found in the security audit
***REMOVED*** These are the actual secrets that need to be removed from history
SECRETS_TO_PURGE=(
    ***REMOVED*** PostgreSQL Password (CRITICAL - in git history)
    "REDACTED_POSTGRES_PASSWORD"

    ***REMOVED*** Scanium API Keys (CRITICAL - in git history)
    "REDACTED_SCANIUM_API_KEY_1"
    "REDACTED_SCANIUM_API_KEY_2"

    ***REMOVED*** OpenAI API Keys (partial patterns - full keys are very long)
    "sk-proj-YgZ9s3U_Dp1hM8iDOB8LeA"  ***REMOVED*** Prefix of first key
    "sk-svcacct-_IluPxAjhHyPqQzoOoeEsrLfyMCM"  ***REMOVED*** Prefix of service account key

    ***REMOVED*** eBay OAuth Credentials
    "REDACTED_EBAY_CLIENT_ID"  ***REMOVED*** Client ID
    "REDACTED_EBAY_CLIENT_SECRET"  ***REMOVED*** Client secret
    "REDACTED_EBAY_ENCRYPTION_KEY"  ***REMOVED*** Encryption key

    ***REMOVED*** Session Signing Secrets
    "REDACTED_BACKEND_SESSION_SECRET"  ***REMOVED*** Backend session secret
    "REDACTED_NAS_SESSION_SECRET"  ***REMOVED*** NAS session secret

    ***REMOVED*** Cloudflare Tunnel Token (decoded components)
    "REDACTED_CLOUDFLARE_ACCOUNT_ID"  ***REMOVED*** Account ID from token
    "REDACTED_CLOUDFLARE_TUNNEL_ID"  ***REMOVED*** Tunnel ID from token

    ***REMOVED*** Google OAuth Client ID (less sensitive but good to obscure)
    "REDACTED_GOOGLE_OAUTH_CLIENT_ID"

    ***REMOVED*** Internal IP address (informational)
    "REDACTED_INTERNAL_IP"

    ***REMOVED*** Production domain (informational - consider keeping this)
    ***REMOVED*** "scanium.gtemp1.com"  ***REMOVED*** Commented out - this is needed for documentation
)

echo "Found ${***REMOVED***SECRETS_TO_PURGE[@]} secret patterns to purge"
echo ""

***REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED***
***REMOVED*** Step 4: Search Git History for Secrets
***REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED***

echo -e "${YELLOW}[4/6] Searching git history for secrets...${NC}"

FOUND_SECRETS=()

for secret in "${SECRETS_TO_PURGE[@]}"; do
    ***REMOVED*** Skip comments
    [[ "$secret" =~ ^***REMOVED*** ]] && continue

    ***REMOVED*** Search for the secret in git history
    if git log --all -S"$secret" --pretty=format:"%H %s" | head -1 &>/dev/null; then
        FOUND_SECRETS+=("$secret")
        echo -e "${RED}✗ Found in history:${NC} ${secret:0:20}..."

        ***REMOVED*** Show which commits contain this secret
        git log --all -S"$secret" --pretty=format:"  └─ %h %s" | head -5
    fi
done

echo ""
echo "Summary: ${***REMOVED***FOUND_SECRETS[@]} secrets found in git history"
echo ""

if [ ${***REMOVED***FOUND_SECRETS[@]} -eq 0 ]; then
    echo -e "${GREEN}No secrets found in git history. Nothing to purge!${NC}"
    exit 0
fi

***REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED***
***REMOVED*** Step 5: Create git-filter-repo Expressions File
***REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED***

echo -e "${YELLOW}[5/6] Creating filter expressions...${NC}"

EXPRESSIONS_FILE="${REPO_DIR}/.git-filter-repo-expressions.txt"

***REMOVED*** Create the expressions file for git-filter-repo
cat > "${EXPRESSIONS_FILE}" << 'EXPRESSIONS_EOF'
***REMOVED***
***REMOVED***
***REMOVED***
***REMOVED*** Format: literal:<old-value>==>replacement:<new-value>
***REMOVED*** OR: regex:<pattern>==>replacement:<new-value>

***REMOVED*** PostgreSQL Password
literal:REDACTED_POSTGRES_PASSWORD==>REDACTED_POSTGRES_PASSWORD

***REMOVED*** Scanium API Keys
literal:REDACTED_SCANIUM_API_KEY_1==>REDACTED_SCANIUM_API_KEY_1
literal:REDACTED_SCANIUM_API_KEY_2==>REDACTED_SCANIUM_API_KEY_2

***REMOVED*** OpenAI API Keys (use regex to catch full keys)
regex:sk-proj-YgZ9s3U_[A-Za-z0-9_-]{80,}==>REDACTED_OPENAI_PROJECT_KEY
regex:sk-svcacct-_IluPxAjhHyPqQzoOoeEsrLfyMCM[A-Za-z0-9_-]{80,}==>REDACTED_OPENAI_SERVICE_ACCOUNT_KEY

***REMOVED*** eBay Credentials
literal:REDACTED_EBAY_CLIENT_ID==>REDACTED_EBAY_CLIENT_ID
literal:REDACTED_EBAY_CLIENT_SECRET==>REDACTED_EBAY_CLIENT_SECRET
literal:REDACTED_EBAY_ENCRYPTION_KEY==>REDACTED_EBAY_ENCRYPTION_KEY

***REMOVED*** Session Signing Secrets
literal:REDACTED_BACKEND_SESSION_SECRET==>REDACTED_BACKEND_SESSION_SECRET
literal:REDACTED_NAS_SESSION_SECRET==>REDACTED_NAS_SESSION_SECRET

***REMOVED*** Cloudflare Tunnel Token Components
literal:REDACTED_CLOUDFLARE_ACCOUNT_ID==>REDACTED_CLOUDFLARE_ACCOUNT_ID
literal:REDACTED_CLOUDFLARE_TUNNEL_ID==>REDACTED_CLOUDFLARE_TUNNEL_ID
regex:CLOUDFLARED_TOKEN\s*=\s*eyJ[A-Za-z0-9+/=]+===>CLOUDFLARED_TOKEN=REDACTED_TOKEN

***REMOVED*** Google OAuth Client ID
literal:REDACTED_GOOGLE_OAUTH_CLIENT_ID==>REDACTED_GOOGLE_OAUTH_CLIENT_ID

***REMOVED*** Internal network info (optional - less sensitive)
literal:REDACTED_INTERNAL_IP==>REDACTED_INTERNAL_IP
EXPRESSIONS_EOF

echo -e "${GREEN}✓ Created filter expressions file: ${EXPRESSIONS_FILE}${NC}"
echo ""

***REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED***
***REMOVED*** Step 6: Run git-filter-repo
***REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED***

echo -e "${YELLOW}[6/6] Running git-filter-repo...${NC}"
echo ""

if [ "${DRY_RUN}" = "true" ]; then
    echo -e "${BLUE}╔════════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║                      DRY RUN MODE                              ║${NC}"
    echo -e "${BLUE}╔════════════════════════════════════════════════════════════════╗${NC}"
    echo ""
    echo "This is a DRY RUN. No changes will be made to git history."
    echo ""
    echo "To actually purge secrets, run:"
    echo -e "${GREEN}  DRY_RUN=false $0${NC}"
    echo ""
    echo "After running the purge, you MUST:"
    echo "  1. Verify the changes: git log --all --oneline | head -20"
    echo "  2. Force push to remote: git push origin --force --all"
    echo "  3. Force push tags: git push origin --force --tags"
    echo "  4. Notify all team members to re-clone (NOT pull!)"
    echo ""
    echo "Example command that would be run:"
    echo -e "${YELLOW}git-filter-repo --replace-text ${EXPRESSIONS_FILE} --force${NC}"

else
    echo -e "${RED}╔════════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${RED}║                   ⚠️  WARNING ⚠️                              ║${NC}"
    echo -e "${RED}║                                                                ║${NC}"
    echo -e "${RED}║  This will PERMANENTLY REWRITE git history!                   ║${NC}"
    echo -e "${RED}║  All team members will need to RE-CLONE the repository!       ║${NC}"
    echo -e "${RED}║                                                                ║${NC}"
    echo -e "${RED}╚════════════════════════════════════════════════════════════════╝${NC}"
    echo ""
    echo -e "${YELLOW}Backup location: ${BACKUP_DIR}${NC}"
    echo ""
    echo "Press ENTER to continue, or Ctrl+C to abort..."
    read -r

    echo ""
    echo "Running git-filter-repo..."
    echo ""

    ***REMOVED*** Run git-filter-repo with the expressions file
    git-filter-repo --replace-text "${EXPRESSIONS_FILE}" --force

    echo ""
    echo -e "${GREEN}╔════════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║              Git History Purge Complete!                      ║${NC}"
    echo -e "${GREEN}╚════════════════════════════════════════════════════════════════╝${NC}"
    echo ""
    echo -e "${YELLOW}NEXT STEPS (CRITICAL):${NC}"
    echo ""
    echo "1. Verify the changes:"
    echo "   git log --all --oneline | head -20"
    echo "   git log --all -p -S'REDACTED' | head -50"
    echo ""
    echo "2. Check repository size reduction:"
    echo "   du -sh .git"
    echo ""
    echo "3. Force push to remote (THIS WILL REWRITE REMOTE HISTORY):"
    echo "   git push origin --force --all"
    echo "   git push origin --force --tags"
    echo ""
    echo "4. Notify all team members:"
    echo "   - Delete their local clones"
    echo "   - Re-clone from remote (do NOT pull/rebase)"
    echo "   - Example: rm -rf scanium && git clone <repo-url>"
    echo ""
    echo "5. Rotate all purged secrets (they still exist in old clones!):"
    echo "   - PostgreSQL password"
    echo "   - Scanium API keys (2 keys)"
    echo "   - OpenAI API keys (2 keys)"
    echo "   - eBay OAuth credentials"
    echo "   - Session signing secrets (2 secrets)"
    echo "   - Cloudflare tunnel token"
    echo ""
    echo -e "${GREEN}Backup preserved at: ${BACKUP_DIR}${NC}"
    echo ""
fi

***REMOVED*** Cleanup
rm -f "${EXPRESSIONS_FILE}"

echo -e "${GREEN}Done!${NC}"
