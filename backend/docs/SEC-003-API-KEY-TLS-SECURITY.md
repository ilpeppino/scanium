# SEC-003: API Key TLS Security Implementation

## Overview

This document describes the security improvements implemented to address SEC-003, which identified vulnerabilities in API key transmission and TLS verification.

## Security Issues Addressed

### 1. API Keys Transmitted Without TLS Verification

**Problem**: API keys were transmitted in X-API-Key headers but TLS certificate validation was not explicitly enforced.

**Solution**: Implemented explicit HTTPS enforcement middleware that:
- Checks the protocol of incoming requests in production environments
- Rejects HTTP requests with a 403 Forbidden status
- Logs security violations for monitoring
- Respects the `X-Forwarded-Proto` header for proxy/load balancer setups

### 2. No API Key Rotation Mechanism

**Problem**: No mechanism existed to rotate API keys, making it difficult to respond to potential key compromises.

**Solution**: Implemented comprehensive `ApiKeyManager` class with:
- Cryptographically secure key generation using `crypto.randomBytes(32)`
- Key metadata tracking (creation date, expiration, rotation history)
- Key rotation support with automatic old key deprecation
- Expiration and revocation capabilities
- Usage statistics and audit logging

### 3. Insufficient Security Monitoring

**Problem**: No logging of API key usage for security monitoring and incident response.

**Solution**: Implemented comprehensive usage logging:
- All API key authentication attempts (success and failure) are logged
- Structured logging includes: timestamp, endpoint, IP address, user agent, error codes
- Usage statistics tracking per API key
- Failed authentication attempts are logged at WARN level
- Successful authentications logged at INFO level

### 4. Missing Security Headers

**Problem**: Application did not set security headers to protect against common web vulnerabilities.

**Solution**: Added comprehensive security headers middleware:
- `Strict-Transport-Security` (HSTS): Forces HTTPS for 1 year
- `Content-Security-Policy`: Prevents XSS and data injection attacks
- `X-Content-Type-Options`: Prevents MIME type sniffing
- `X-Frame-Options`: Prevents clickjacking attacks
- `X-XSS-Protection`: Enables browser XSS protection
- `Referrer-Policy`: Controls referrer information leakage
- `Permissions-Policy`: Restricts browser features

## Implementation Details

### Files Modified

1. **backend/src/infra/http/plugins/security.ts** (NEW)
   - Security plugin for HTTPS enforcement
   - Security headers middleware
   - Configurable based on environment

2. **backend/src/modules/classifier/api-key-manager.ts** (NEW)
   - API key lifecycle management
   - Rotation and expiration tracking
   - Usage logging and statistics

3. **backend/src/modules/classifier/routes.ts**
   - Updated to use `ApiKeyManager`
   - Added comprehensive logging for API key usage
   - Enhanced error handling and security events

4. **backend/src/app.ts**
   - Registered security plugin
   - Integrated into application startup

5. **backend/src/config/index.ts**
   - Added security configuration schema
   - Environment variable mappings for security settings

### Configuration

New environment variables added:

```bash
# HTTPS Enforcement
SECURITY_ENFORCE_HTTPS=true          # Reject HTTP requests in production
SECURITY_ENABLE_HSTS=true            # Enable HSTS header

# API Key Management
SECURITY_API_KEY_ROTATION_ENABLED=true
SECURITY_API_KEY_EXPIRATION_DAYS=90  # Default: 90 days
SECURITY_LOG_API_KEY_USAGE=true      # Log all API key usage
```

### Default Values

- `SECURITY_ENFORCE_HTTPS`: `true` (enabled by default)
- `SECURITY_ENABLE_HSTS`: `true` (enabled by default)
- `SECURITY_API_KEY_ROTATION_ENABLED`: `true` (enabled by default)
- `SECURITY_API_KEY_EXPIRATION_DAYS`: `90` days
- `SECURITY_LOG_API_KEY_USAGE`: `true` (enabled by default)

## API Key Management

### Key Generation

Keys are generated using `crypto.randomBytes(32)` and encoded as base64url for safe transmission in HTTP headers. This provides 256 bits of entropy.

```typescript
const key = randomBytes(32).toString('base64url');
```

### Key Rotation

To rotate an API key:

```typescript
const newKey = apiKeyManager.rotateKey(oldKey, expirationDays);
```

This will:
1. Generate a new cryptographically secure key
2. Link it to the old key in metadata
3. Set the old key to expire in 30 days (transition period)
4. Return the new key metadata

### Key Revocation

To immediately revoke a key:

```typescript
apiKeyManager.revokeKey(apiKey);
```

This marks the key as inactive and sets expiration to now.

### Usage Statistics

Get usage statistics for a key:

```typescript
const stats = apiKeyManager.getKeyUsageStats(apiKey);
// Returns: { totalRequests, successfulRequests, failedRequests, lastUsed, recentFailures }
```

### Cleanup

Periodically clean up expired keys:

```typescript
const removedCount = apiKeyManager.cleanupExpiredKeys();
```

## Security Headers Explained

### Strict-Transport-Security (HSTS)

```
Strict-Transport-Security: max-age=31536000; includeSubDomains; preload
```

- Forces browsers to use HTTPS for 1 year
- Applies to all subdomains
- Eligible for browser HSTS preload list

### Content-Security-Policy (CSP)

```
Content-Security-Policy: default-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'
```

- Only allow resources from same origin
- Prevent framing (clickjacking protection)
- Restrict base URI and form actions

### X-Content-Type-Options

```
X-Content-Type-Options: nosniff
```

- Prevents MIME type sniffing attacks

### X-Frame-Options

```
X-Frame-Options: DENY
```

- Prevents the page from being framed (clickjacking protection)

### X-XSS-Protection

```
X-XSS-Protection: 1; mode=block
```

- Enables browser XSS filter in blocking mode

### Referrer-Policy

```
Referrer-Policy: strict-origin-when-cross-origin
```

- Controls referrer information sent to other sites

### Permissions-Policy

```
Permissions-Policy: camera=(), microphone=(), geolocation=(), interest-cohort=()
```

- Restricts access to sensitive browser features

## HTTPS Enforcement

### Production Behavior

When `SECURITY_ENFORCE_HTTPS=true` and `NODE_ENV=production`:

1. All incoming requests are checked for protocol
2. The check respects `X-Forwarded-Proto` header (for proxies/load balancers)
3. HTTP requests are rejected with 403 Forbidden
4. Security violations are logged with IP address and endpoint

### Development Behavior

HTTPS enforcement is **disabled** in development mode (`NODE_ENV=development`) to allow local testing.

### Load Balancer/Proxy Setup

If your application runs behind a load balancer or reverse proxy that terminates TLS, ensure the proxy sets the `X-Forwarded-Proto` header:

```
X-Forwarded-Proto: https
```

Common proxy configurations:

**Nginx:**
```nginx
proxy_set_header X-Forwarded-Proto $scheme;
```

**AWS ALB/ELB:**
Automatically sets `X-Forwarded-Proto`

**Cloudflare:**
Automatically sets `X-Forwarded-Proto`

## Logging and Monitoring

### API Key Usage Events

All API key usage is logged with structured data:

```typescript
{
  apiKey: "abc12345***",           // Partial key (first 8 chars)
  timestamp: "2025-12-20T10:30:00Z",
  endpoint: "/v1/classify",
  method: "POST",
  success: true,
  ip: "203.0.113.42",
  userAgent: "Scanium-Client/1.0"
}
```

### Failed Authentication

Failed authentication attempts are logged at WARN level:

```json
{
  "level": "warn",
  "apiKeyPrefix": "abc12345",
  "ip": "203.0.113.42",
  "endpoint": "/v1/classify",
  "msg": "Invalid or expired API key attempt"
}
```

### Successful Authentication

Successful requests are logged at INFO level:

```json
{
  "level": "info",
  "apiKeyPrefix": "abc12345",
  "ip": "203.0.113.42",
  "endpoint": "/v1/classify",
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "msg": "API key usage: classification request successful"
}
```

### HTTPS Violations

HTTP requests in production are logged at WARN level:

```json
{
  "level": "warn",
  "url": "/v1/classify",
  "method": "POST",
  "protocol": "http",
  "ip": "203.0.113.42",
  "msg": "HTTPS required - rejecting HTTP request"
}
```

## Testing

Comprehensive test suites have been added:

1. **backend/src/modules/classifier/api-key-manager.test.ts**
   - Key generation and validation
   - Key rotation and revocation
   - Usage logging and statistics
   - Expiration handling

2. **backend/src/infra/http/plugins/security.test.ts**
   - HTTPS enforcement
   - Security headers validation
   - Environment-specific behavior

Run tests with:

```bash
npm test
```

## Migration Guide

### Existing Deployments

1. **Update Environment Variables**

   Add the new security environment variables to your deployment configuration. The defaults are secure, but you can customize:

   ```bash
   SECURITY_ENFORCE_HTTPS=true
   SECURITY_ENABLE_HSTS=true
   SECURITY_API_KEY_ROTATION_ENABLED=true
   SECURITY_API_KEY_EXPIRATION_DAYS=90
   SECURITY_LOG_API_KEY_USAGE=true
   ```

2. **Configure Proxy/Load Balancer**

   Ensure your reverse proxy or load balancer sets `X-Forwarded-Proto` header correctly.

3. **Monitor Logs**

   After deployment, monitor logs for:
   - Failed authentication attempts (potential security incidents)
   - HTTPS violations (misconfigured clients)
   - API key usage patterns (for capacity planning)

4. **Plan Key Rotation**

   - Review existing API keys
   - Plan rotation schedule (recommend 90 days)
   - Communicate rotation to API clients
   - Use the rotation feature to generate new keys

### New Deployments

All security features are enabled by default. Simply ensure:

1. Application runs behind TLS termination (load balancer or reverse proxy)
2. `X-Forwarded-Proto` header is set correctly
3. `NODE_ENV=production` for production deployments

## Security Best Practices

1. **API Key Rotation**
   - Rotate API keys every 90 days (configurable)
   - Rotate immediately if key compromise is suspected
   - Maintain transition period to avoid service disruption

2. **Monitoring**
   - Enable usage logging in production
   - Set up alerts for failed authentication attempts
   - Monitor for unusual usage patterns
   - Review logs regularly for security incidents

3. **Key Storage**
   - Store API keys securely (environment variables, secrets manager)
   - Never commit keys to version control
   - Use different keys for different environments

4. **TLS Configuration**
   - Use TLS 1.2 or higher
   - Use strong cipher suites
   - Keep TLS certificates up to date
   - Consider certificate pinning for high-security applications

5. **Rate Limiting**
   - Keep rate limiting enabled (already configured)
   - Monitor rate limit violations
   - Adjust limits based on legitimate usage patterns

## Threat Model

This implementation addresses the following threats:

1. **Man-in-the-Middle (MITM) Attacks**
   - Mitigated by HTTPS enforcement
   - HSTS prevents protocol downgrade attacks

2. **API Key Interception**
   - Mitigated by mandatory TLS encryption
   - Logging enables detection of compromised keys

3. **Key Compromise**
   - Mitigated by rotation mechanism
   - Revocation enables immediate response

4. **Unauthorized Access**
   - Mitigated by key validation and expiration
   - Rate limiting prevents brute force attacks

5. **Common Web Attacks**
   - Mitigated by comprehensive security headers
   - CSP prevents XSS attacks
   - X-Frame-Options prevents clickjacking

## Compliance

This implementation helps meet requirements for:

- **PCI DSS**: TLS encryption, key rotation, logging
- **SOC 2**: Security monitoring, access control, audit logging
- **GDPR**: Security of processing, data protection by design
- **OWASP Top 10**: Addresses broken authentication, security misconfiguration

## Future Enhancements

Potential future security improvements:

1. **Certificate Pinning**
   - Pin specific certificates for critical API endpoints
   - Requires client-side implementation

2. **API Key Scopes**
   - Grant fine-grained permissions per key
   - Principle of least privilege

3. **Multi-factor Authentication**
   - Additional authentication factors for sensitive operations
   - Time-based or challenge-response

4. **Anomaly Detection**
   - Machine learning-based detection of unusual patterns
   - Automatic key suspension on suspicious activity

5. **Key Encryption at Rest**
   - Encrypt API keys in storage
   - Hardware security module (HSM) integration

## Support

For questions or issues related to these security features:

1. Review this documentation
2. Check the test files for usage examples
3. Review application logs for security events
4. Open an issue in the repository

## References

- [OWASP API Security Top 10](https://owasp.org/www-project-api-security/)
- [Mozilla Security Headers](https://infosec.mozilla.org/guidelines/web_security)
- [OWASP Secure Headers Project](https://owasp.org/www-project-secure-headers/)
- [RFC 6797: HTTP Strict Transport Security](https://tools.ietf.org/html/rfc6797)
