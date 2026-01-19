***REMOVED*** Pull Request: AI Gateway Security Framework

***REMOVED******REMOVED*** Title

Security: AI Gateway with abuse controls and prompt-injection defenses

***REMOVED******REMOVED*** Branch

`claude/security-framework-chat-Fk3Ln`

***REMOVED******REMOVED*** Description

***REMOVED******REMOVED******REMOVED*** Summary

This PR implements a comprehensive security framework for the AI Assistant feature, ensuring all LLM
interactions go through a secure backend gateway with multiple layers of protection.

***REMOVED******REMOVED******REMOVED*** Changes

**Backend AI Gateway (`backend/src/modules/assistant/`)**

- Enhanced `safety.ts` with:
    - Prompt injection detection (6 categories: system prompt extraction, data exfiltration,
      jailbreak attempts, credential access, automation abuse, encoded payloads)
    - PII redaction before LLM calls (email, phone, credit cards, IBAN, SSN, IP addresses, postal
      codes)
    - Input validation with configurable limits
    - Action sanitization for LLM responses
- New `quota-store.ts`: Daily quota enforcement with midnight UTC reset
- Updated `routes.ts` with:
    - Multi-layer rate limiting (per-IP, per-API-key, per-device)
    - Daily quota checks
    - Security metadata logging (no content by default)
    - Safe error responses with stable reason codes

**Android Client (`androidApp/`)**

- Updated `AssistantRepository.kt`:
    - Client-side throttling (1 second minimum between requests)
    - Hashed device ID for rate limiting
    - User-friendly error messages for all error codes
- Updated `AssistantViewModel.kt` for new response format
- Updated shared models with `SafetyResponse` type

**Documentation**

- `docs/security/ai-assistant-security.md`: Threat model and security controls
- `docs/AI_GATEWAY.md`: API contract and local dev setup
- Updated `backend/.env.example` with new environment variables

**Tests**

- `safety.test.ts`: 37 tests for injection detection, PII redaction, validation
- `quota-store.test.ts`: 12 tests for quota enforcement

***REMOVED******REMOVED******REMOVED*** Security Controls Implemented

| Control                    | Description                                              |
|----------------------------|----------------------------------------------------------|
| Prompt Injection Detection | Pattern-based detection with 6 threat categories         |
| PII Redaction              | Automatic redaction of sensitive data before LLM calls   |
| Rate Limiting              | Sliding window per IP, API key, and device               |
| Daily Quota                | Configurable per-session limit with automatic reset      |
| Input Validation           | Hard limits on message length, context items, attributes |
| Output Sanitization        | Whitelist-based action type filtering, URL validation    |
| Safe Error Handling        | Stable reason codes, no internal details leaked          |
| Secure Logging             | Metadata only by default, content logging opt-in         |

***REMOVED******REMOVED******REMOVED*** Test Results

All 49 new security tests pass:

- 37 tests in `safety.test.ts`
- 12 tests in `quota-store.test.ts`

***REMOVED******REMOVED******REMOVED*** Environment Variables

New configuration options (all have sensible defaults):

```bash
SCANIUM_ASSISTANT_PROVIDER=mock|openai|disabled
ASSIST_RATE_LIMIT_PER_MINUTE=60
ASSIST_IP_RATE_LIMIT_PER_MINUTE=60
ASSIST_DEVICE_RATE_LIMIT_PER_MINUTE=30
ASSIST_DAILY_QUOTA=200
ASSIST_MAX_INPUT_CHARS=2000
ASSIST_MAX_OUTPUT_TOKENS=500
ASSIST_MAX_CONTEXT_ITEMS=10
ASSIST_PROVIDER_TIMEOUT_MS=30000
ASSIST_LOG_CONTENT=false
```

***REMOVED******REMOVED******REMOVED*** Breaking Changes

None. The response format is backwards compatible (`content` field still works, `reply` is the new
canonical field).

***REMOVED******REMOVED*** Manual PR Creation Commands

```bash
***REMOVED*** Push branch if not already pushed
git push -u origin claude/security-framework-chat-Fk3Ln

***REMOVED*** Create PR using GitHub CLI
gh pr create \
  --title "Security: AI Gateway with abuse controls and prompt-injection defenses" \
  --body "$(cat docs/pr/ai-gateway-security-pr.md)" \
  --base main

***REMOVED*** Or create via GitHub web UI:
***REMOVED*** 1. Go to https://github.com/[org]/scanium/compare/main...claude/security-framework-chat-Fk3Ln
***REMOVED*** 2. Click "Create pull request"
***REMOVED*** 3. Use the title and description from this document
```

***REMOVED******REMOVED*** Checklist

- [x] Backend security controls implemented
- [x] Android client updated
- [x] Documentation created
- [x] Unit tests added (49 tests)
- [x] All tests passing
- [x] No secrets committed
- [x] Environment variables documented
