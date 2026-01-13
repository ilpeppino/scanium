# Play Console Data Safety Form - Scanium

**Last Updated:** 2026-01-13
**Prepared for:** Google Play Console Data Safety Declaration

This document provides accurate answers for the Play Console Data Safety form based on Scanium's actual code behavior and data flows. All statements reflect the current implementation in Phase D (production-ready with Google OAuth and account deletion).

---

## Quick Reference Links

- **Privacy Policy URL:** `https://scanium-site-url/PRIVACY.html` (update with actual deployed URL)
- **Account Deletion URL:** `https://scanium-site-url/account-deletion.html` (for Play Console field)
- **Support Email:** `contact@scanium.app` (update if different)

---

## Section 1: Data Collection and Security

### Does your app collect or share any of the required user data types?
**Answer:** ✅ **Yes**

Scanium collects:
1. Account information (when using Google Sign-In)
2. Photos and videos (camera for scanning)
3. App activity data
4. Device identifiers (for rate limiting and security)

---

## Section 2: Data Types Collected

### Personal Information

#### Account Info
- **Collected:** ✅ Yes
- **Data Type:** Email address, name, user account ID
- **Required/Optional:** Optional (only if user signs in with Google)
- **Collection Purpose:**
  - App functionality (authentication, data sync)
  - Account management
- **Shared with third parties:** ❌ No
- **Encrypted in transit:** ✅ Yes (HTTPS)
- **Encryption at rest:** ✅ Yes (tokens encrypted with AES-256-GCM)
- **User can request deletion:** ✅ Yes (in-app + web form)

### Photos and Videos

#### Photos
- **Collected:** ✅ Yes
- **Data Type:** Photos (captured via camera for item scanning)
- **Required/Optional:** Required for core functionality
- **Collection Purpose:**
  - App functionality (item recognition and classification)
- **Shared with third parties:** ✅ Yes
  - **Third parties:** Google Cloud Vision API, OpenAI (depending on feature used)
  - **Purpose:** Image analysis and classification
  - **Data processing agreement:** Yes (cloud service providers with DPAs)
- **Encrypted in transit:** ✅ Yes (HTTPS)
- **Ephemeral:** ✅ Yes (images not stored long-term on backend; processed and discarded)
- **User can request deletion:** ✅ Yes (local images in app can be deleted; backend images not retained)

### App Activity

#### App interactions
- **Collected:** ✅ Yes
- **Data Type:** User actions within app, usage data
- **Required/Optional:** Required
- **Collection Purpose:**
  - Analytics (app performance and stability)
  - Product personalization (e.g., assistant responses)
- **Shared with third parties:** ❌ No
- **Encrypted in transit:** ✅ Yes
- **User can request deletion:** ✅ Yes

### Device or other IDs

#### Device or other IDs
- **Collected:** ✅ Yes
- **Data Type:** IP address, device identifiers
- **Required/Optional:** Required
- **Collection Purpose:**
  - Fraud prevention, security, and compliance (rate limiting, abuse prevention)
- **Shared with third parties:** ❌ No
- **Encrypted in transit:** ✅ Yes
- **User can request deletion:** ⚠️ Partial (anonymized logs may be retained for security/legal compliance)

---

## Section 3: Data Sharing

### Do you share user data with third parties?
**Answer:** ✅ **Yes** (for core app functionality only)

#### Third Party Services Used:
1. **Google Cloud Vision API**
   - **Purpose:** Image classification and OCR
   - **Data shared:** Photos (temporarily, for processing)
   - **Privacy Policy:** https://cloud.google.com/terms/cloud-privacy-notice

2. **OpenAI API** (if assistant enabled)
   - **Purpose:** AI-powered item descriptions and insights
   - **Data shared:** Item attributes, user prompts (no images)
   - **Privacy Policy:** https://openai.com/policies/privacy-policy

3. **eBay OAuth** (if user connects eBay)
   - **Purpose:** Marketplace integration for listing items
   - **Data shared:** OAuth tokens (stored encrypted), listing data
   - **Privacy Policy:** https://www.ebay.com/help/policies/member-behaviour-policies/user-privacy-notice-privacy-policy

**Note:** We do NOT share data for advertising or marketing purposes.

---

## Section 4: Data Security Practices

### Encryption in transit
✅ **Yes** - All data transmitted between app and backend uses HTTPS (TLS 1.2+)

### Encryption at rest
✅ **Yes** - Sensitive data (OAuth tokens, session tokens) encrypted with AES-256-GCM

### Data deletion
✅ **Yes** - Users can delete their data through:
1. In-app Settings → Account → Delete Account
2. Web form: https://scanium-site-url/account-deletion.html

**Deletion URL for Play Console:** `https://scanium-site-url/account-deletion.html`

---

## Section 5: Data Retention and Deletion

### Data Retention Policy
- **Account data:** Retained until user deletes account
- **Images:** Not retained long-term; processed and discarded immediately after analysis
- **Session tokens:** Retained until expiry or logout (30-90 days)
- **Usage logs:** Aggregated/anonymized logs retained up to 90 days for security and compliance

### What gets deleted when user deletes account:
✅ Account profile (email, name, picture)
✅ All authentication sessions and tokens
✅ eBay marketplace connections (OAuth tokens)
✅ All draft and published listings
✅ Scan history and item data

### What may be retained (anonymized):
⚠️ Aggregated usage statistics (not linked to identity)
⚠️ Security logs (IP addresses, anonymized identifiers) for fraud prevention
⚠️ Transaction records required by law

**Retention period after deletion:** Up to 30 days for complete removal

---

## Section 6: App Access for Review (if applicable)

### Does your app restrict access that would prevent review?
**Answer:** ❌ **No**

Scanium's core features (camera scanning, item classification) are available without sign-in. Google Sign-In is **optional** for data sync and personalized features.

If reviewer needs to test signed-in features:
- **Approach:** Use any Google account (no special credentials needed)
- **Alternative:** Provide test Google account if needed (coordinate with review team)

**Document this in:** `howto/project/play-review-access.md` (see next section)

---

## Section 7: Advertising ID

### Does your app use the Advertising ID?
**Answer:** ❌ **No**

Scanium does NOT use `com.google.android.gms.permission.AD_ID` permission and does not collect advertising identifiers.

**Verification:**
- Check `androidApp/src/main/AndroidManifest.xml` - no AD_ID permission declared
- No advertising SDKs integrated (no AdMob, no Meta Ads, etc.)
- If adding ads in future, must update Data Safety form

---

## Section 8: Declaration Checklist

Before submitting to Play Console:

- [ ] Privacy policy URL is live and accessible: `https://scanium-site-url/PRIVACY.html`
- [ ] Account deletion URL is live and accessible: `https://scanium-site-url/account-deletion.html`
- [ ] Privacy policy accurately describes all data collection (updated 2026-01-13)
- [ ] Account deletion works in-app (tested in Settings)
- [ ] Account deletion works via web (tested with email verification)
- [ ] Data Safety answers match actual code behavior
- [ ] No advertising SDKs are included (if adding later, update form)
- [ ] Third-party services (Google Vision, OpenAI, eBay) are disclosed
- [ ] OAuth production client ID is configured (not test/dev client)

---

## Common Mistakes to Avoid

1. ❌ **Don't claim "no data collected" if using Google Sign-In** - account info must be declared
2. ❌ **Don't forget to provide account deletion URL** - mandatory for apps with user accounts
3. ❌ **Don't share data for ads/marketing** without declaring it explicitly
4. ❌ **Don't use test OAuth clients in production** - see `howto/project/oauth-production-readiness.md`
5. ❌ **Don't claim encryption if not implemented** - verify AES-256-GCM for sensitive tokens

---

## References

- **Play Console Data Safety Help:** https://support.google.com/googleplay/android-developer/answer/10787469
- **Account Deletion Requirements:** https://support.google.com/googleplay/android-developer/answer/13316080
- **Scanium Privacy Policy:** `../scanium-site/PRIVACY.html`
- **Scanium Deletion Flow:** `../scanium-site/account-deletion.html`

---

## Updates and Maintenance

This document should be updated whenever:
- New data types are collected (e.g., location, contacts)
- New third-party services are added
- Data retention policies change
- Advertising or analytics SDKs are integrated

**Last code audit:** Phase D (2026-01-13) - includes Google OAuth, account deletion, backend API
