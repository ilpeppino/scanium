***REMOVED*** Security Cleanup Guide for Scanium

This document provides a complete guide for securing the Scanium repository before making it public.

***REMOVED******REMOVED*** Table of Contents

1. [Overview](***REMOVED***overview)
2. [Pre-commit Hook Setup](***REMOVED***pre-commit-hook-setup)
3. [Git History Purge](***REMOVED***git-history-purge)
4. [Credential Rotation Checklist](***REMOVED***credential-rotation-checklist)
5. [Post-Cleanup Verification](***REMOVED***post-cleanup-verification)
6. [Team Coordination](***REMOVED***team-coordination)

---

***REMOVED******REMOVED*** Overview

The Scanium repository contains sensitive credentials in git history that must be addressed before making it public:

- **8 HIGH severity credentials** exposed in git history
- **3 MEDIUM severity** informational exposures
- Credentials span: PostgreSQL, OpenAI, Scanium API, eBay OAuth, Cloudflare, and session secrets

**Two-pronged approach:**
1. **Prevention (Pre-commit Hook)**: Prevent future secrets from being committed
2. **Remediation (Git History Purge)**: Remove existing secrets from git history

---

***REMOVED******REMOVED*** Pre-commit Hook Setup

***REMOVED******REMOVED******REMOVED*** What It Does

The pre-commit hook uses [gitleaks](https://github.com/gitleaks/gitleaks) to scan staged changes for secrets before allowing a commit.

***REMOVED******REMOVED******REMOVED*** Installation Status

✅ **Already installed and configured!**

- **Gitleaks binary**: `/opt/homebrew/bin/gitleaks`
- **Pre-commit hook**: `.git/hooks/pre-commit`
- **Configuration**: `.gitleaks.toml`

***REMOVED******REMOVED******REMOVED*** How It Works

1. You stage files: `git add file.txt`
2. You attempt commit: `git commit -m "message"`
3. Hook runs: Scans staged files for secrets
4. **If secrets found**: Commit is BLOCKED with details
5. **If no secrets**: Commit proceeds normally

***REMOVED******REMOVED******REMOVED*** Configuration Details

The `.gitleaks.toml` file includes:

- **Default rules**: Detects common secrets (AWS keys, private keys, passwords, etc.)
- **Custom rules**: Scanium-specific patterns (API keys, database passwords, etc.)
- **Allowlist**: Excludes example files, documentation, and test fixtures

***REMOVED******REMOVED******REMOVED*** Testing the Hook

```bash
***REMOVED*** This should be BLOCKED
echo "POSTGRES_PASSWORD=SuperSecret123" > test.env
git add test.env
git commit -m "test"
***REMOVED*** ❌ COMMIT BLOCKED: Secrets detected!

***REMOVED*** This should pass (allowlisted pattern)
echo "POSTGRES_PASSWORD=your-password-here" > .env.example
git add .env.example
git commit -m "add example"
***REMOVED*** ✅ No secrets detected. Proceeding with commit.
```

***REMOVED******REMOVED******REMOVED*** Bypassing the Hook

**⚠️ NOT RECOMMENDED** - Only for false positives:

```bash
git commit --no-verify -m "message"
```

**Better approach**: Add false positives to `.gitleaks.toml` allowlist

***REMOVED******REMOVED******REMOVED*** Updating the Configuration

Edit `.gitleaks.toml` to:
- Add new custom rules for Scanium-specific secrets
- Update allowlist patterns for false positives
- Adjust sensitivity for specific file types

Example:
```toml
[[rules]]
id = "my-custom-secret"
description = "My Custom Secret Pattern"
regex = '''MY_SECRET_KEY\s*=\s*[A-Za-z0-9]+'''
tags = ["custom", "high-severity"]

[allowlist]
paths = [
    '''my-safe-file\.txt$''',
]
```

***REMOVED******REMOVED******REMOVED*** Maintenance

Gitleaks updates regularly. Update with:
```bash
brew upgrade gitleaks
```

---

***REMOVED******REMOVED*** Git History Purge

***REMOVED******REMOVED******REMOVED*** ⚠️ CRITICAL WARNINGS

**READ THIS BEFORE PROCEEDING:**

1. **Rewrites git history** - All commit SHAs will change
2. **Breaks existing clones** - All team members must RE-CLONE (not pull!)
3. **Cannot be easily undone** - Creates backup but coordination is critical
4. **Old secrets remain** in old clones until they're deleted
5. **Requires force push** - Overwrites remote repository

***REMOVED******REMOVED******REMOVED*** When to Run

Run the purge script **ONLY** when ALL these conditions are met:

- ✅ All team members are coordinated and aware
- ✅ No one has uncommitted work
- ✅ You've rotated all secrets (or will immediately after)
- ✅ You have a verified backup strategy
- ✅ You're prepared to notify all collaborators

***REMOVED******REMOVED******REMOVED*** Script Location

```
scripts/security-purge-secrets.sh
```

***REMOVED******REMOVED******REMOVED*** What It Purges

The script removes these secrets from git history:

**High Priority:**
- PostgreSQL password: `REDACTED_POSTGRES_PASSWORD`
- Scanium API keys (2 keys)
- OpenAI API keys (2 keys)
- eBay OAuth credentials (3 items)
- Session signing secrets (2 secrets)
- Cloudflare tunnel token

**Medium Priority:**
- Google OAuth client ID
- Internal IP address

All secrets are replaced with `REDACTED_<TYPE>` placeholders.

***REMOVED******REMOVED******REMOVED*** Running the Script

***REMOVED******REMOVED******REMOVED******REMOVED*** Step 1: Dry Run (Safe to Test)

```bash
***REMOVED*** Default is dry run - safe to execute
./scripts/security-purge-secrets.sh
```

This will:
- Show what would be purged
- Display affected commits
- Create no changes

***REMOVED******REMOVED******REMOVED******REMOVED*** Step 2: Review the Output

Check:
- Number of secrets found
- Which commits contain secrets
- Estimated impact

***REMOVED******REMOVED******REMOVED******REMOVED*** Step 3: Coordinate with Team

**BEFORE running for real:**

1. **Notify all team members**
   - Send email/message with this document
   - Schedule a time when no one is actively working

2. **Ensure clean state**
   - All work committed and pushed
   - No pending pull requests

3. **Create external backup** (beyond the script's backup)
   ```bash
   cd /Users/family/dev
   tar -czf scanium-backup-$(date +%Y%m%d).tar.gz scanium/
   ```

***REMOVED******REMOVED******REMOVED******REMOVED*** Step 4: Run the Purge (Point of No Return)

```bash
DRY_RUN=false ./scripts/security-purge-secrets.sh
```

**What happens:**
1. Pre-flight checks (uncommitted changes, tool availability)
2. Creates backup at `../scanium-backup-<timestamp>/`
3. Searches git history for secrets
4. **⚠️ PAUSE**: Prompts for confirmation
5. Runs `git-filter-repo` to rewrite history
6. Shows next steps

**This takes 1-5 minutes depending on repo size.**

***REMOVED******REMOVED******REMOVED******REMOVED*** Step 5: Verify the Changes

```bash
***REMOVED*** Check recent commits
git log --all --oneline | head -20

***REMOVED*** Search for REDACTED placeholders
git log --all -p -S'REDACTED' | less

***REMOVED*** Check repository size (should be smaller)
du -sh .git

***REMOVED*** Verify a specific secret is gone
git log --all -S'REDACTED_POSTGRES_PASSWORD' --oneline
***REMOVED*** (should show nothing)
```

***REMOVED******REMOVED******REMOVED******REMOVED*** Step 6: Force Push to Remote

**⚠️ THIS OVERWRITES REMOTE HISTORY**

```bash
***REMOVED*** Push all branches
git push origin --force --all

***REMOVED*** Push all tags
git push origin --force --tags
```

**GitHub may require:**
- Temporarily disable branch protection rules
- Confirm force push with admin permissions

***REMOVED******REMOVED******REMOVED******REMOVED*** Step 7: Notify Team to Re-clone

**Send this message to all collaborators:**

```
📢 URGENT: Scanium Repository History Rewritten

The Scanium git repository history has been rewritten to remove exposed secrets.

ACTION REQUIRED:
1. Commit and push any outstanding work NOW (before seeing this message)
2. Delete your local clone:
   cd /Users/family/dev  ***REMOVED*** or wherever your clone is
   rm -rf scanium

3. Re-clone the repository:
   git clone git@github.com:yourusername/scanium.git

4. DO NOT attempt to pull, rebase, or merge your old clone!

5. Delete any other clones (on servers, other machines, backups)

WHY: Git history was rewritten to remove credentials. Your old clone contains
     the old history with exposed secrets.

Questions? See: SECURITY-CLEANUP.md
```

***REMOVED******REMOVED******REMOVED*** Rollback (If Something Goes Wrong)

If you need to rollback **before force pushing**:

```bash
***REMOVED*** Restore from backup
cd /Users/family/dev
rm -rf scanium
mv scanium-backup-<timestamp> scanium
cd scanium
```

If you already force pushed, you'll need to:
1. Force push the backup back to remote
2. Notify team that the purge is being reverted

---

***REMOVED******REMOVED*** Credential Rotation Checklist

**⚠️ ROTATE ALL SECRETS BEFORE OR IMMEDIATELY AFTER PURGING**

Even after purging git history, old secrets exist in:
- Old clones (until deleted)
- GitHub's cache (for a period)
- Local backups
- Any forks

***REMOVED******REMOVED******REMOVED*** Priority 1 - Rotate Today

- [ ] **PostgreSQL Password**
  - Current: `REDACTED_POSTGRES_PASSWORD`
  - Update in: `backend/.env`, `deploy/nas/compose/.env`
  - Test connectivity before deploying

- [ ] **Scanium API Key 1**
  - Current: `Cr3UnvP9ubNBxSiKaJA7LWA...`
  - Update in: `backend/.env`, `local.properties`
  - Update mobile app configuration

- [ ] **Scanium API Key 2**
  - Current: `LPWOkPQ7IBzegqVpxSg98iafC8FVfRf...`
  - Update in: `backend/.env`

- [ ] **OpenAI Project API Key**
  - Current: `sk-proj-YgZ9s3U_Dp1hM8i...`
  - Update in: `backend/.env`
  - Check OpenAI billing for unauthorized usage

- [ ] **OpenAI Service Account Key**
  - Current: `sk-svcacct-_IluPxAjhHyPqQzoOoeEsrLfyMCM...`
  - Update in: `deploy/nas/compose/.env`
  - Check OpenAI billing for unauthorized usage

***REMOVED******REMOVED******REMOVED*** Priority 2 - Rotate This Week

- [ ] **Cloudflare Tunnel Token**
  - Rotate in Cloudflare dashboard
  - Update in: `backend/.env`
  - Verify tunnel still works

- [ ] **eBay Client Secret**
  - Current: `REDACTED_EBAY_CLIENT_SECRET`
  - Update in: `backend/.env`
  - Check eBay developer console

- [ ] **eBay Token Encryption Key**
  - Current: `a66a0307079595a1e0398b709515ecd03910998b...`
  - Generate new: `openssl rand -hex 32`
  - Re-encrypt stored eBay tokens

- [ ] **Backend Session Secret**
  - Current: `f7d831f0e0ca83c27e88346b1567e01931cee5d05...`
  - Generate new: `openssl rand -hex 32`
  - Update in: `backend/.env`
  - Invalidates all active sessions

- [ ] **NAS Session Secret**
  - Current: `REDACTED_NAS_SESSION_SECRET`
  - Generate new: `openssl rand -base64 32`
  - Update in: `deploy/nas/compose/.env`

***REMOVED******REMOVED******REMOVED*** Generating New Secrets

```bash
***REMOVED*** Hex-encoded secret (64 chars)
openssl rand -hex 32

***REMOVED*** Base64-encoded secret
openssl rand -base64 32

***REMOVED*** Alphanumeric secret (for API keys)
openssl rand -base64 48 | tr -d '/+=' | head -c 64
```

***REMOVED******REMOVED******REMOVED*** Verification After Rotation

For each rotated secret:
1. ✅ Update in all locations (dev, staging, prod)
2. ✅ Test the service still works
3. ✅ Check logs for auth failures
4. ✅ Verify old secret no longer works (if possible)

---

***REMOVED******REMOVED*** Post-Cleanup Verification

***REMOVED******REMOVED******REMOVED*** 1. Verify Secrets Are Gone

```bash
***REMOVED*** Search for specific secrets
git log --all -S'REDACTED_POSTGRES_PASSWORD' --oneline
git log --all -S'Cr3UnvP9ubNBxSiKaJA7LWAaKEwl4WNdpVP' --oneline

***REMOVED*** Should return nothing or only REDACTED commits
```

***REMOVED******REMOVED******REMOVED*** 2. Verify REDACTED Placeholders

```bash
***REMOVED*** Should see REDACTED placeholders in old commits
git log --all -p -S'REDACTED_POSTGRES_PASSWORD' | head -100
```

***REMOVED******REMOVED******REMOVED*** 3. Run Gitleaks Scan

```bash
***REMOVED*** Scan entire history
gitleaks detect --verbose

***REMOVED*** Should report: "no leaks found"
```

***REMOVED******REMOVED******REMOVED*** 4. Check Repository Size

```bash
***REMOVED*** Before purge
du -sh .git

***REMOVED*** After purge (should be smaller)
du -sh .git

***REMOVED*** Garbage collect to reclaim space
git reflog expire --expire=now --all
git gc --prune=now --aggressive
```

***REMOVED******REMOVED******REMOVED*** 5. Verify Services Still Work

After rotating credentials:
- [ ] Backend connects to PostgreSQL
- [ ] OpenAI API calls work
- [ ] eBay OAuth flow works
- [ ] Cloudflare tunnel is accessible
- [ ] Mobile app connects to backend
- [ ] Session authentication works

---

***REMOVED******REMOVED*** Team Coordination

***REMOVED******REMOVED******REMOVED*** Pre-Purge Checklist

- [ ] All team members notified (email/Slack/etc.)
- [ ] Scheduled downtime window communicated
- [ ] All pending PRs merged or closed
- [ ] All team members have pushed their work
- [ ] External backup created and verified
- [ ] Credentials ready to rotate

***REMOVED******REMOVED******REMOVED*** During Purge

- [ ] Run script: `DRY_RUN=false ./scripts/security-purge-secrets.sh`
- [ ] Verify changes locally
- [ ] Force push to remote
- [ ] Immediately notify team

***REMOVED******REMOVED******REMOVED*** Post-Purge

- [ ] All team members re-cloned successfully
- [ ] All rotated credentials verified working
- [ ] Services back online and tested
- [ ] Old backups deleted (or securely stored offline)
- [ ] Update repository README if needed

---

***REMOVED******REMOVED*** FAQ

***REMOVED******REMOVED******REMOVED*** Q: Can I skip the git history purge?

**A:** If the repository will be public, NO. The secrets are accessible in git history and can be extracted easily. Even if private, it's a security risk.

***REMOVED******REMOVED******REMOVED*** Q: Can I just delete and re-create the repository?

**A:** Yes, but you lose all git history and issue/PR numbers. The purge preserves history while removing secrets.

***REMOVED******REMOVED******REMOVED*** Q: What if I find more secrets after purging?

**A:** Run the purge script again. You'll need to:
1. Add the new secrets to the script
2. Re-run the purge
3. Force push again
4. Rotate those secrets too

***REMOVED******REMOVED******REMOVED*** Q: Why not just delete the commits with secrets?

**A:** Git doesn't work that way. Commits are linked in a chain. Removing one requires rewriting all descendants, which is what `git-filter-repo` does.

***REMOVED******REMOVED******REMOVED*** Q: Can I use `git filter-branch` instead?

**A:** No. `git filter-branch` is deprecated and much slower. Use `git-filter-repo`.

***REMOVED******REMOVED******REMOVED*** Q: How long are old secrets cached on GitHub?

**A:** GitHub caches git objects for some time. Rotate secrets immediately to minimize risk.

***REMOVED******REMOVED******REMOVED*** Q: What about forks of the repository?

**A:** Forks will still contain the old history. You cannot force push to forks you don't own. This is why rotation is critical.

---

***REMOVED******REMOVED*** Additional Resources

- **Gitleaks Documentation**: https://github.com/gitleaks/gitleaks
- **git-filter-repo Guide**: https://github.com/newren/git-filter-repo
- **GitHub: Removing Sensitive Data**: https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository

---

***REMOVED******REMOVED*** Quick Reference

***REMOVED******REMOVED******REMOVED*** Files Created/Modified

```
.gitleaks.toml                          ***REMOVED*** Gitleaks configuration
.git/hooks/pre-commit                   ***REMOVED*** Pre-commit hook script
scripts/security-purge-secrets.sh       ***REMOVED*** Git history purge script
SECURITY-CLEANUP.md                     ***REMOVED*** This document
```

***REMOVED******REMOVED******REMOVED*** Commands

```bash
***REMOVED*** Test pre-commit hook
git add <file> && git commit -m "test"

***REMOVED*** Dry run purge
./scripts/security-purge-secrets.sh

***REMOVED*** Run purge
DRY_RUN=false ./scripts/security-purge-secrets.sh

***REMOVED*** Verify purge
gitleaks detect --verbose

***REMOVED*** Generate new secret
openssl rand -hex 32
```

---

**Last Updated**: 2026-01-23
**Scanium Version**: 1.7.0
**Security Audit Reference**: See initial security audit report
