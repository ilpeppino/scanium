# Assistant Providers

This document explains how to configure different AI providers for the Scanium assistant.

## Available Providers

| Provider   | Description                     | Status           |
|------------|---------------------------------|------------------|
| `openai`   | OpenAI GPT models (recommended) | Production-ready |
| `claude`   | Anthropic Claude models         | Production-ready |
| `mock`     | Grounded mock responses         | For testing only |
| `disabled` | Disable assistant completely    | -                |

## OpenAI Provider Configuration

### Required Environment Variables

```bash
# Set the provider type
SCANIUM_ASSISTANT_PROVIDER=openai

# Your OpenAI API key (from platform.openai.com)
OPENAI_API_KEY=sk-your-api-key-here

# Model to use (default: gpt-4o-mini)
OPENAI_MODEL=gpt-4o-mini
```

### Optional Configuration

```bash
# Max output tokens (default: 500)
ASSIST_MAX_OUTPUT_TOKENS=500

# Provider timeout in ms (default: 30000)
ASSIST_PROVIDER_TIMEOUT_MS=30000
```

### Supported OpenAI Models

| Model         | Description                        | Notes                   |
|---------------|------------------------------------|-------------------------|
| `gpt-4o-mini` | Fast, cost-effective (recommended) | Best for most use cases |
| `gpt-4o`      | More capable, higher cost          | For complex listings    |
| `gpt-4-turbo` | Previous generation                | Still supported         |

## Claude Provider Configuration

### Required Environment Variables

```bash
SCANIUM_ASSISTANT_PROVIDER=claude
CLAUDE_API_KEY=your-claude-api-key
CLAUDE_MODEL=claude-sonnet-4-20250514
```

## Security Best Practices

### API Keys

1. **Never commit API keys** to version control
2. Use environment files (`.env`) that are gitignored
3. Rotate keys periodically
4. Use project-specific API keys when possible

### Logging

API keys are **never logged**. The provider only logs:

- Request ID / Correlation ID
- Provider type and model name
- Response timing and status
- Error types (without sensitive details)

Enable verbose logging only in development:

```bash
ASSIST_LOG_CONTENT=true  # Only for debugging - do not use in production
```

## Error Handling

The OpenAI provider maps API errors to Scanium's standard error structure:

| OpenAI Status | Error Type             | Category    | Retryable |
|---------------|------------------------|-------------|-----------|
| 401           | `unauthorized`         | `auth`      | No        |
| 429           | `rate_limited`         | `policy`    | Yes (60s) |
| 500/502/503   | `provider_unavailable` | `temporary` | Yes       |
| Timeout       | `network_timeout`      | `temporary` | Yes       |

Example error response:

```json
{
  "reply": "I encountered an issue generating the listing...",
  "assistantError": {
    "type": "rate_limited",
    "category": "policy",
    "retryable": true,
    "retryAfterSeconds": 60,
    "reasonCode": "RATE_LIMITED"
  }
}
```

## Deployment

### Docker Compose

Add to your `.env` file in the compose directory:

```bash
SCANIUM_ASSISTANT_PROVIDER=openai
OPENAI_API_KEY=sk-your-api-key-here
OPENAI_MODEL=gpt-4o-mini
```

### Verification Commands

After deployment, verify the provider is working:

```bash
# Check no mock fallback messages in compiled code
docker exec scanium-backend sh -c 'grep -r "falling back to mock provider" /app/dist || echo "OK: No fallback message found"'

# Check container logs for provider initialization
docker logs scanium-backend 2>&1 | grep -i "provider initialized"

# Test the warmup endpoint
curl -s -X POST https://your-domain.com/v1/assist/warmup \
  -H "X-API-Key: YOUR_SCANIUM_API_KEY" | jq .

# Expected response:
# {
#   "status": "ok",
#   "provider": "openai",
#   "model": "gpt-4o-mini",
#   ...
# }
```

### Test a Real Request

```bash
curl -X POST https://your-domain.com/v1/assist/chat \
  -H "X-API-Key: YOUR_SCANIUM_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "items": [{
      "itemId": "test-1",
      "title": "Test Item",
      "category": "Electronics"
    }],
    "message": "Generate a listing"
  }'
```

## Troubleshooting

### Provider Not Configured Error

**Error:** `OPENAI_API_KEY is required when assistant.provider is "openai"`

**Fix:** Set the `OPENAI_API_KEY` environment variable

### 401 Unauthorized

**Cause:** Invalid or expired API key

**Fix:**

1. Verify key at platform.openai.com
2. Ensure key has correct permissions
3. Regenerate if expired

### 429 Rate Limited

**Cause:** Too many requests to OpenAI

**Fix:**

1. Implement backoff in client
2. Check OpenAI usage limits
3. Consider upgrading OpenAI plan

### Timeout Errors

**Cause:** Request took too long

**Fix:**

1. Increase `ASSIST_PROVIDER_TIMEOUT_MS`
2. Use a faster model (`gpt-4o-mini`)
3. Check network connectivity

## Rolling Back

To quickly revert to mock provider:

```bash
# In your .env file:
SCANIUM_ASSISTANT_PROVIDER=mock

# Restart the container
docker-compose restart backend
```

## Rate Limits

The Scanium backend has its own rate limiting independent of OpenAI:

| Limit Type         | Default | Env Variable                          |
|--------------------|---------|---------------------------------------|
| Per API key/minute | 60      | `ASSIST_RATE_LIMIT_PER_MINUTE`        |
| Per IP/minute      | 60      | `ASSIST_IP_RATE_LIMIT_PER_MINUTE`     |
| Per device/minute  | 30      | `ASSIST_DEVICE_RATE_LIMIT_PER_MINUTE` |
| Daily quota        | 200     | `ASSIST_DAILY_QUOTA`                  |

These limits protect both your OpenAI budget and the backend resources.
