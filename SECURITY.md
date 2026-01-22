# Security Policy

## Supported Versions

We release patches for security vulnerabilities in the following versions:

| Version | Supported          |
| ------- | ------------------ |
| 1.7.x   | :white_check_mark: |
| 1.6.x   | :white_check_mark: |
| < 1.6   | :x:                |

## Reporting a Vulnerability

We take the security of Scanium seriously. If you believe you have found a security vulnerability, please report it to us as described below.

### Where to Report

**Please DO NOT report security vulnerabilities through public GitHub issues.**

Instead, please report them via email to:
- **Email**: ilpeppino@gmail.com
- **Subject**: [SECURITY] Brief description of the issue

### What to Include

Please include the following information in your report:

- **Type of issue** (e.g., buffer overflow, SQL injection, cross-site scripting, etc.)
- **Full paths of source file(s)** related to the manifestation of the issue
- **Location of the affected source code** (tag/branch/commit or direct URL)
- **Step-by-step instructions** to reproduce the issue
- **Proof-of-concept or exploit code** (if possible)
- **Impact of the issue**, including how an attacker might exploit it

This information will help us triage your report more quickly.

### Response Timeline

- **Initial Response**: Within 48 hours
- **Status Update**: Within 7 days
- **Fix Timeline**: Varies based on severity (critical: 1-7 days, high: 7-30 days, medium/low: 30-90 days)

### What to Expect

After you submit a report, we will:

1. **Acknowledge receipt** of your vulnerability report within 48 hours
2. **Confirm the problem** and determine affected versions
3. **Audit code** to find any similar problems
4. **Prepare fixes** for all supported versions
5. **Release security patches** and publicly disclose the vulnerability

### Disclosure Policy

- **Coordinated Disclosure**: We ask that you allow us reasonable time to address the issue before public disclosure
- **Credit**: We will credit you for the discovery in the release notes (unless you prefer to remain anonymous)
- **Public Advisory**: After a fix is released, we will publish a security advisory on GitHub

## Security Best Practices for Users

### API Keys and Secrets

- **Never commit** API keys, passwords, or secrets to your repository
- Use **environment variables** for all sensitive configuration
- Rotate credentials regularly
- Use **different keys** for development, staging, and production

### Android App Security

- **Keep the app updated** to the latest version
- **Don't sideload** APKs from untrusted sources
- **Review permissions** before installing
- **Enable Google Play Protect** for additional security

### Backend Security

- **Use HTTPS** for all API communications
- **Rotate database passwords** regularly
- **Keep dependencies updated** to patch known vulnerabilities
- **Enable firewall rules** to restrict access to backend services
- **Monitor logs** for suspicious activity

### Local Development

- **Don't use production credentials** in development
- **Keep local.properties** out of version control (already in .gitignore)
- **Use test/sandbox credentials** for third-party services (eBay, OpenAI, etc.)
- **Review git history** before pushing to ensure no secrets are included

## Known Security Considerations

### Camera Permissions

The app requires camera access for object detection. Camera data is:
- **Processed locally** using Google ML Kit
- **Only sent to cloud** when user explicitly requests AI classification
- **Never stored** on servers without explicit user consent
- **Deleted after processing** when cloud classification is used

### Data Storage

- **Local SQLite database**: Not encrypted by default (consider using SQLCipher for sensitive data)
- **Network traffic**: Uses HTTPS/TLS for all API calls
- **Session tokens**: Stored securely using Android Keystore
- **API keys**: Compiled into the app (should be rotated if compromised)

### Third-Party Services

The app integrates with:
- **Google ML Kit**: On-device processing, see [Google's Privacy Policy](https://policies.google.com/privacy)
- **OpenAI API**: Cloud classification, see [OpenAI's Data Policy](https://openai.com/policies/privacy-policy)
- **eBay API**: Product information, see [eBay's Privacy Policy](https://www.ebay.com/help/policies/member-behaviour-policies/user-privacy-notice-privacy-policy)

### Telemetry and Analytics

- **OTLP telemetry**: Collected for debugging and performance monitoring
- **PII redaction**: Credentials and personal data are automatically redacted (see `AttributeSanitizer.kt`)
- **Opt-out**: Users can disable telemetry in app settings
- **Data retention**: Telemetry data is retained for 30 days

## Security Features

### Pre-commit Hooks

This repository uses [gitleaks](https://github.com/gitleaks/gitleaks) to prevent committing secrets:
- **Automatic scanning** on every commit
- **Blocks commits** containing API keys, passwords, tokens
- **Custom rules** for Scanium-specific secrets
- See `.gitleaks.toml` for configuration

### Dependency Scanning

- **Gradle**: Run `./gradlew dependencyCheckAnalyze` to scan for known vulnerabilities
- **npm**: Run `npm audit` in the backend directory
- **Automated updates**: Consider using Dependabot or Renovate

### Code Review

All changes go through:
- **Automated checks**: Pre-commit hooks, CI/CD pipelines
- **Manual review**: At least one maintainer review required
- **Security review**: For changes affecting authentication, authorization, or data handling

## Responsible Disclosure Examples

### ✅ Good Examples

- "I found a SQL injection vulnerability in the backend API endpoint `/v1/items` that allows reading arbitrary database records"
- "The Android app stores OAuth tokens in SharedPreferences without encryption, making them accessible to other apps with root access"
- "A timing attack on the API key validation allows enumerating valid API keys"

### ❌ Don't Report Publicly

- Opening a public GitHub issue with vulnerability details
- Posting exploit code on social media before coordinated disclosure
- Sharing user data or proof-of-concept that could harm users

## Security Updates

Subscribe to security updates:
- **Watch this repository** for security advisories
- **Enable notifications** for releases
- **Follow releases** to stay informed about security patches

## Questions?

If you have questions about this security policy, please contact:
- **Email**: ilpeppino@gmail.com
- **Subject**: [SECURITY POLICY] Your question

---

**Last Updated**: 2026-01-23
**Version**: 1.0

Thank you for helping keep Scanium and its users safe!
