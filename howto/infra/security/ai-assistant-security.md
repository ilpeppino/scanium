***REMOVED*** AI Assistant Security Framework

This document defines the security model, threat analysis, and controls for Scanium's AI Assistant feature operating in "chat-only during draft item" mode.

***REMOVED******REMOVED*** Table of Contents

1. [Overview](***REMOVED***overview)
2. [Threat Model](***REMOVED***threat-model)
3. [Data Minimization Policy](***REMOVED***data-minimization-policy)
4. [Security Controls](***REMOVED***security-controls)
5. [Operational Guidance](***REMOVED***operational-guidance)
6. [Incident Response](***REMOVED***incident-response)

---

***REMOVED******REMOVED*** Overview

***REMOVED******REMOVED******REMOVED*** Architecture

The AI Assistant follows a gateway architecture where:

1. **Mobile client** (Android/iOS) sends chat requests to the backend AI Gateway
2. **AI Gateway** validates, sanitizes, and rate-limits requests before forwarding to the LLM provider
3. **LLM provider** processes the sanitized prompt and returns a response
4. **AI Gateway** validates the response and returns it to the client

```
┌─────────────┐     HTTPS      ┌──────────────┐      HTTPS      ┌──────────────┐
│   Mobile    │ ───────────────▶│  AI Gateway  │─────────────────▶│LLM Provider │
│   Client    │◀─────────────── │  (Backend)   │◀─────────────────│  (OpenAI)   │
└─────────────┘                 └──────────────┘                  └──────────────┘
      │                               │
      │ Never calls LLM directly      │ Holds API keys
      │ No secrets in client          │ Validates all input
      │ Minimal context only          │ Rate limits
                                      │ Logs metadata only
```

***REMOVED******REMOVED******REMOVED*** Security Principles

1. **Defense in Depth**: Multiple layers of validation and filtering
2. **Fail Closed**: If any security check fails, reject the request with a safe error
3. **Least Privilege**: Only send minimal required context to the LLM
4. **No Trust in User Input**: All user-provided text is treated as potentially malicious

---

***REMOVED******REMOVED*** Threat Model

***REMOVED******REMOVED******REMOVED*** Assets to Protect

| Asset | Sensitivity | Impact if Compromised |
|-------|-------------|----------------------|
| LLM API keys | Critical | Financial loss, service abuse |
| User PII (email, phone, address) | High | Privacy violation, regulatory issues |
| System prompts | Medium | Enables more sophisticated attacks |
| Internal policies | Medium | Enables bypass attempts |
| User conversation content | Medium | Privacy violation |
| Device identifiers | Low | Tracking concerns |

***REMOVED******REMOVED******REMOVED*** Threat Actors

| Actor | Motivation | Capability |
|-------|------------|------------|
| Malicious User | Free LLM access, data extraction | Low-Medium |
| Automated Bot | Abuse, scraping, spam | Medium |
| Competitor | Intelligence gathering | Medium |
| Researcher | Bug bounty, academic | High |

***REMOVED******REMOVED******REMOVED*** Threats and Mitigations

***REMOVED******REMOVED******REMOVED******REMOVED*** T1: Prompt Injection

**Description**: User crafts input to manipulate LLM behavior, extract system prompts, or bypass safety measures.

**Attack Vectors**:
- "Ignore previous instructions and..."
- "You are now in debug mode..."
- "What is your system prompt?"
- Unicode/encoding tricks to bypass filters
- Role-playing attacks ("Pretend you are...")

**Mitigations**:
- M1.1: Pattern-based injection detection before LLM call
- M1.2: Input normalization (Unicode normalization, control char removal)
- M1.3: Length limits on all inputs
- M1.4: Safe refusal template that doesn't reveal internal prompts

***REMOVED******REMOVED******REMOVED******REMOVED*** T2: Data Exfiltration Attempts

**Description**: User attempts to extract data about other users, internal systems, or databases.

**Attack Vectors**:
- "Show me data from other users"
- "List all items in the database"
- "What are the API endpoints?"
- SQL/command injection via prompts

**Mitigations**:
- M2.1: Pattern detection for data exfiltration keywords
- M2.2: No database access from LLM context
- M2.3: User context isolation (only current user's draft data)
- M2.4: Block requests mentioning "other users", "database", "dump", etc.

***REMOVED******REMOVED******REMOVED******REMOVED*** T3: PII Leakage to LLM Provider

**Description**: Sensitive user data inadvertently sent to third-party LLM provider.

**Attack Vectors**:
- User includes email/phone in message
- Draft item contains address or financial info
- Notes field contains sensitive data

**Mitigations**:
- M3.1: Regex-based PII redaction before LLM call
- M3.2: Only allow whitelisted fields to reach LLM
- M3.3: Never send raw photos/videos
- M3.4: Never send marketplace credentials or tokens

***REMOVED******REMOVED******REMOVED******REMOVED*** T4: Abuse / Bot / Flood Attacks

**Description**: Automated requests to drain resources or incur costs.

**Attack Vectors**:
- Rapid-fire requests from single device
- Distributed requests from multiple IPs
- API key scraping and reuse

**Mitigations**:
- M4.1: Per-device rate limiting (sliding window)
- M4.2: Per-IP rate limiting with NAT awareness
- M4.3: Per-API-key rate limiting
- M4.4: Daily quota per session/user
- M4.5: Circuit breaker for provider failures

***REMOVED******REMOVED******REMOVED******REMOVED*** T5: Cost Attacks

**Description**: User attempts to generate large LLM costs.

**Attack Vectors**:
- Very long messages to maximize input tokens
- Requests for verbose responses
- High request volume within limits

**Mitigations**:
- M5.1: Hard cap on message length (2000 chars)
- M5.2: Hard cap on context items (10 items)
- M5.3: Hard cap on response tokens (500 tokens)
- M5.4: Daily cost budget per user/session
- M5.5: Provider timeout to bound costs

***REMOVED******REMOVED******REMOVED******REMOVED*** T6: Information Disclosure via Logs

**Description**: Sensitive data exposed through logging.

**Attack Vectors**:
- Raw prompts in logs
- PII in error messages
- API keys in debug logs

**Mitigations**:
- M6.1: Default logging excludes message content
- M6.2: Redact PII patterns in any logged content
- M6.3: Structured logging with metadata only
- M6.4: Separate debug log level for content (disabled in production)

***REMOVED******REMOVED******REMOVED******REMOVED*** T7: Client-Side Secret Exposure

**Description**: API keys or secrets extracted from mobile app.

**Attack Vectors**:
- APK decompilation
- Network traffic interception
- Debugging on rooted device

**Mitigations**:
- M7.1: No LLM provider keys in client
- M7.2: API key only grants gateway access (not direct LLM)
- M7.3: Short-lived session tokens (future)
- M7.4: Device attestation (future)

---

***REMOVED******REMOVED*** Data Minimization Policy

***REMOVED******REMOVED******REMOVED*** Data Allowed to Reach LLM

| Field | Allowed | Max Size | Redaction |
|-------|---------|----------|-----------|
| User message text | Yes | 2000 chars | PII patterns |
| Draft item category | Yes | 100 chars | None |
| Draft item title | Yes | 200 chars | PII patterns |
| Draft item confidence | Yes | Float 0-1 | None |
| Item attributes (non-sensitive) | Yes | 20 items, 100 chars each | None |
| User-provided notes | Yes | 500 chars | PII patterns |
| Conversation history | Yes | Last 10 messages | PII patterns |

***REMOVED******REMOVED******REMOVED*** Data Never Sent to LLM

- Raw photos or video frames
- Exact GPS location
- Email addresses (redacted to `[EMAIL]`)
- Phone numbers (redacted to `[PHONE]`)
- Physical addresses (redacted to `[ADDRESS]`)
- Financial information (bank accounts, card numbers)
- Marketplace credentials or OAuth tokens
- Device hardware identifiers
- User account IDs (hashed for rate limiting only)
- System prompts or internal policy text
- API keys or secrets

---

***REMOVED******REMOVED*** Security Controls

***REMOVED******REMOVED******REMOVED*** Input Validation

All inputs validated with fail-closed behavior:

```
Message:
  - Required, non-empty after trimming
  - Max 2000 characters
  - Unicode normalized (NFKC)
  - Control characters removed (except newlines)

Draft Item Context:
  - Max 10 items
  - Each item ID: max 100 chars, alphanumeric + dash + underscore
  - Category: max 100 chars
  - Title: max 200 chars
  - Attributes: max 20 entries, 100 char key, 200 char value

User Notes:
  - Max 500 characters
  - Same normalization as message
```

***REMOVED******REMOVED******REMOVED*** Prompt Injection Detection

Patterns triggering immediate safe refusal:

```
Category: System Prompt Extraction
- "system prompt", "initial prompt", "your instructions"
- "ignore previous", "disregard above"
- "you are now", "act as if", "pretend to be"
- "what were you told", "what is your role"

Category: Data Exfiltration
- "other users", "all users", "database"
- "dump", "export all", "list everything"
- "internal", "admin", "debug mode"

Category: Jailbreak Attempts
- "DAN", "developer mode", "unrestricted"
- "hypothetically", "in fiction"
- Base64/encoded payloads
```

***REMOVED******REMOVED******REMOVED*** Rate Limiting

Configurable via environment variables:

| Limit Type | Default | Env Variable |
|------------|---------|--------------|
| Per-IP per minute | 60 | `ASSIST_IP_RATE_LIMIT_PER_MINUTE` |
| Per-API-key per minute | 60 | `ASSIST_RATE_LIMIT_PER_MINUTE` |
| Per-device per minute | 30 | `ASSIST_DEVICE_RATE_LIMIT_PER_MINUTE` |
| Daily quota per session | 200 | `ASSIST_DAILY_QUOTA` |
| Rate limit window | 60s | `ASSIST_RATE_LIMIT_WINDOW_SECONDS` |
| Backoff base | 30s | `ASSIST_RATE_LIMIT_BACKOFF_SECONDS` |
| Backoff max | 900s | `ASSIST_RATE_LIMIT_BACKOFF_MAX_SECONDS` |

***REMOVED******REMOVED******REMOVED*** Cost Guardrails

| Guardrail | Default | Env Variable |
|-----------|---------|--------------|
| Max input chars | 2000 | `ASSIST_MAX_INPUT_CHARS` |
| Max output tokens | 500 | `ASSIST_MAX_OUTPUT_TOKENS` |
| Provider timeout | 30s | `ASSIST_PROVIDER_TIMEOUT_MS` |
| Max context items | 10 | `ASSIST_MAX_CONTEXT_ITEMS` |

***REMOVED******REMOVED******REMOVED*** Safe Error Responses

All error responses use stable reason codes without leaking internal details:

| Code | Description | User-Facing Message |
|------|-------------|---------------------|
| `RATE_LIMITED` | Rate limit exceeded | "Please wait before sending another message" |
| `VALIDATION_ERROR` | Input validation failed | "Message could not be processed" |
| `POLICY_VIOLATION` | Prompt injection detected | "I can't help with that request" |
| `PROVIDER_UNAVAILABLE` | LLM provider down | "Assistant temporarily unavailable" |
| `QUOTA_EXCEEDED` | Daily quota reached | "Daily message limit reached" |
| `UNAUTHORIZED` | Invalid API key | "Authentication required" |

---

***REMOVED******REMOVED*** Operational Guidance

***REMOVED******REMOVED******REMOVED*** Environment Variables

Required for production:

```bash
***REMOVED*** LLM Provider
OPENAI_API_KEY=sk-...              ***REMOVED*** Server-side only, never in client

***REMOVED*** Gateway Configuration
ASSIST_RATE_LIMIT_PER_MINUTE=60
ASSIST_IP_RATE_LIMIT_PER_MINUTE=60
ASSIST_DEVICE_RATE_LIMIT_PER_MINUTE=30
ASSIST_DAILY_QUOTA=200
ASSIST_MAX_INPUT_CHARS=2000
ASSIST_MAX_OUTPUT_TOKENS=500
ASSIST_PROVIDER_TIMEOUT_MS=30000

***REMOVED*** Logging
ASSIST_LOG_CONTENT=false           ***REMOVED*** Set true only for debugging, logs are redacted
```

***REMOVED******REMOVED******REMOVED*** Monitoring Recommendations

1. **Alert on**: Unusual rate limit trigger volume (potential bot attack)
2. **Alert on**: High policy violation rate (potential targeted attack)
3. **Alert on**: Provider error rate > 5% (service degradation)
4. **Dashboard**: Daily request volume, quota usage, error breakdown
5. **Audit log**: All policy violations with request metadata (not content)

***REMOVED******REMOVED******REMOVED*** Incident Response

***REMOVED******REMOVED******REMOVED******REMOVED*** Suspected Prompt Injection Attack

1. Check logs for `POLICY_VIOLATION` reason codes
2. Identify source (IP, device hash, API key)
3. Consider temporary block if coordinated
4. Review and update detection patterns if bypass found
5. Do NOT expose successful bypass patterns publicly

***REMOVED******REMOVED******REMOVED******REMOVED*** Cost Anomaly

1. Check daily quota consumption metrics
2. Identify highest-consuming sessions
3. Verify rate limits are functioning
4. Consider lowering limits if attack in progress
5. Review provider billing dashboard

***REMOVED******REMOVED******REMOVED******REMOVED*** Data Leak Concern

1. Audit logs for affected time period
2. Verify PII redaction was active
3. Check what context fields were sent
4. Follow data breach notification procedures if needed

---

***REMOVED******REMOVED*** Incident Response

***REMOVED******REMOVED******REMOVED*** Severity Levels

| Level | Description | Response Time | Examples |
|-------|-------------|---------------|----------|
| P1 | Critical - Data breach or key exposure | Immediate | API keys leaked, user data exposed |
| P2 | High - Active attack or service down | < 1 hour | Sustained bot attack, provider outage |
| P3 | Medium - Elevated abuse | < 4 hours | Unusual rate limit triggers |
| P4 | Low - Policy tuning needed | < 24 hours | New bypass pattern detected |

***REMOVED******REMOVED******REMOVED*** Response Procedures

***REMOVED******REMOVED******REMOVED******REMOVED*** P1: API Key Compromised
1. Immediately rotate affected API key
2. Block old key at provider
3. Deploy new key to production
4. Audit usage during exposure window
5. Post-incident review

***REMOVED******REMOVED******REMOVED******REMOVED*** P2: Sustained Attack
1. Enable enhanced rate limiting
2. Consider IP/region blocking if localized
3. Scale gateway if legitimate traffic affected
4. Monitor provider costs
5. Coordinate with security team

---

***REMOVED******REMOVED*** Appendix: Security Checklist

Before deploying changes to AI Assistant:

- [ ] No LLM API keys in client code or BuildConfig
- [ ] All user inputs validated with fail-closed behavior
- [ ] Rate limiting tested and functioning
- [ ] PII redaction active and tested
- [ ] Prompt injection patterns updated
- [ ] Error responses don't leak internal details
- [ ] Logging excludes sensitive content
- [ ] Provider timeout configured
- [ ] Daily quota enforcement tested
- [ ] Circuit breaker configured for provider failures
