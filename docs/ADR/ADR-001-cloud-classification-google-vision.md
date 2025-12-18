***REMOVED*** ADR-001: Cloud Classification via Google Vision API

**Status:** Proposed
**Date:** 2025-12-18
**Deciders:** Architecture Team
**Context:** Phase 1 - Target Architecture Definition

---

***REMOVED******REMOVED*** Context and Problem Statement

Scanium currently uses ML Kit for on-device object detection, which provides only 5 coarse categories (Fashion, Food, Home goods, Places, Plants). This limitation prevents accurate item categorization for resale scenarios where users need fine-grained categories like "furniture_sofa" vs "furniture_chair" or "electronics_laptop" vs "electronics_monitor".

**Requirements:**
1. Improve categorization beyond ML Kit's 5 coarse categories to 23+ fine-grained categories
2. Extract item attributes (color, brand, material, condition, size) for better pricing
3. Keep scanning pipeline responsive (detection/tracking must not block on cloud calls)
4. Support offline mode with graceful degradation
5. Prevent API key leakage in mobile APK/IPA
6. Enable cross-platform implementation (Android + future iOS)

---

***REMOVED******REMOVED*** Decision Drivers

1. **Accuracy**: Need state-of-the-art vision AI for reliable item classification
2. **Security**: No API keys in mobile app; must use secure backend proxy
3. **Performance**: Cloud classification must be async, not block camera/detection
4. **Cost**: Must optimize API call volume (classify only stable items, not every frame)
5. **Maintainability**: Clean separation between on-device detection and cloud classification
6. **Cross-platform**: Solution must work for both Android and iOS

---

***REMOVED******REMOVED*** Considered Options

***REMOVED******REMOVED******REMOVED*** Option 1: Direct Google Vision API calls from mobile
**Approach:** Mobile app directly calls Google Cloud Vision API with API key/credentials

**Pros:**
- Simple implementation, no backend needed
- Lower latency (one fewer network hop)
- Direct access to Google Vision features

**Cons:**
- ❌ **Security risk**: API keys must be embedded in APK/IPA (can be extracted)
- ❌ **No rate limiting**: Can't control/throttle API usage per user
- ❌ **Cost exposure**: Malicious users can abuse API keys, causing cost spikes
- ❌ **No analytics**: Can't track usage patterns or classify behavior
- ❌ **Limited auth**: Can't tie requests to authenticated users

**Verdict:** ❌ **Rejected** - Security risks outweigh benefits. API keys in mobile apps are a critical vulnerability.

---

***REMOVED******REMOVED******REMOVED*** Option 2: Backend proxy with Google Vision (**CHOSEN**)
**Approach:** Mobile app calls YOUR backend API, which proxies requests to Google Vision

```
┌──────────┐         ┌──────────────┐         ┌──────────────────┐
│  Mobile  │────────▶│  Your Backend│────────▶│  Google Vision   │
│   App    │  HTTPS  │  (Node/Go)   │  API Key│      API         │
└──────────┘         └──────────────┘         └──────────────────┘
   No API key         - Auth user token        - Securely stored key
   in APK             - Rate limiting          - Request signing
                      - Usage tracking
```

**Pros:**
- ✅ **Secure**: API keys never leave backend infrastructure
- ✅ **Rate limiting**: Control requests per user, prevent abuse
- ✅ **Analytics**: Track classification metrics, costs per user
- ✅ **Flexible**: Can add caching, fallbacks, A/B testing
- ✅ **User auth**: Tie requests to authenticated users with JWT
- ✅ **Cross-platform**: Same backend for Android + iOS

**Cons:**
- Extra latency (~50-100ms) for backend hop
- Requires backend development and hosting
- Additional infrastructure costs

**Mitigation:**
- Backend deployed to same region as Google Cloud Vision (minimize latency)
- Efficient backend implementation (Go, Node.js, or serverless)
- Use CDN/edge functions for global distribution

**Verdict:** ✅ **CHOSEN** - Security and control benefits justify the extra complexity.

---

***REMOVED******REMOVED******REMOVED*** Option 3: On-device CLIP model
**Approach:** Use on-device CLIP (Contrastive Language-Image Pre-training) for zero-shot classification

**Pros:**
- Offline-first, no network needed
- No API costs
- Privacy-preserving (all on-device)
- Fast inference on modern devices

**Cons:**
- ❌ **Model size**: CLIP models are large (200MB+), impacts APK size
- ❌ **Accuracy**: Smaller on-device CLIP models less accurate than cloud Vision AI
- ❌ **Device requirements**: Needs GPU/NPU for acceptable performance
- ❌ **Complexity**: Requires model optimization, quantization, testing
- ❌ **No attributes**: CLIP does labels, not structured attributes (color, brand, etc.)

**Verdict:** ⚠️ **Deferred** - Potential future optimization, but not primary solution. Consider for offline mode.

---

***REMOVED******REMOVED*** Decision Outcome

**Chosen option: Option 2 - Backend proxy with Google Vision API**

***REMOVED******REMOVED******REMOVED*** Implementation Strategy

***REMOVED******REMOVED******REMOVED******REMOVED*** Mobile App (Android/iOS)
```kotlin
// shared:core-data/src/commonMain/kotlin/

interface CloudClassificationRepository {
    suspend fun classifyItem(
        thumbnail: ImageRef,
        coarseLabel: String?,
        domainPackId: String
    ): Result<ClassificationResult>
}

// androidApp/src/main/java/com/scanium/app/data/

class GoogleVisionClassifierAndroid(
    private val httpClient: HttpClient,
    private val config: ApiConfig,
    private val authProvider: AuthProvider
) : CloudClassificationRepository {
    override suspend fun classifyItem(...): Result<ClassificationResult> {
        val response = httpClient.post("${config.baseUrl}/api/v1/classify") {
            headers {
                append("Authorization", "Bearer ${authProvider.getToken()}")
            }
            setBody(/* multipart form with image */)
        }
        // Parse response, handle errors
    }
}
```

***REMOVED******REMOVED******REMOVED******REMOVED*** Backend API (Node.js/Go/Python)

**Endpoint:** `POST /api/v1/classify`

**Request:**
```json
{
  "image": "<base64_jpeg>",
  "domainPackId": "home_resale",
  "hint": "Home good"  // Optional ML Kit coarse label
}
```

**Response:**
```json
{
  "domainCategoryId": "furniture_sofa",
  "confidence": 0.87,
  "attributes": {
    "color": "brown",
    "material": "leather",
    "condition": "good"
  },
  "requestId": "req_abc123",
  "latencyMs": 234
}
```

**Backend Flow:**
1. Validate user auth token (JWT)
2. Check rate limits (e.g., 100 classifications/hour per user)
3. Call Google Cloud Vision API with service account key
4. Parse Vision API response
5. Map to domain categories using Domain Pack config
6. Return structured result
7. Log metrics (latency, success rate, category distribution)

***REMOVED******REMOVED******REMOVED******REMOVED*** Security Measures

1. **No API keys in mobile app**
   - BuildConfig checked to ensure no Google Vision keys
   - Only backend URL exposed (e.g., `https://api.scanium.com`)

2. **User authentication**
   - JWT tokens with short expiry (1 hour)
   - Refresh token flow for long sessions
   - Revocation support for compromised tokens

3. **Rate limiting**
   - Per-user limits: 100 classifications/hour (adjustable)
   - Global limits: 10,000 requests/hour (cost control)
   - 429 responses with Retry-After header

4. **Backend security**
   - Google Vision API key stored in environment variables / secret manager
   - Service account with minimum required permissions
   - Request logging for audit trail
   - HTTPS only, no HTTP fallback

***REMOVED******REMOVED******REMOVED******REMOVED*** Fallback Strategy

```
1. Try cloud classification (Google Vision via backend)
   └─▶ Success: Use domainCategoryId + attributes

2. If network error or timeout (5s timeout):
   └─▶ Fallback: Use ML Kit coarse label → map to ItemCategory

3. If ML Kit label unavailable:
   └─▶ Default: Assign UNKNOWN category

4. Display confidence indicator:
   - Cloud (high confidence): Green badge "Verified"
   - ML Kit (medium): Yellow badge "Auto-detected"
   - Unknown (low): Gray badge "Unclassified"
```

***REMOVED******REMOVED******REMOVED******REMOVED*** Cost Optimization

**Strategies to minimize Google Vision API calls:**

1. **Classify only stable items**
   - Use ObjectTracker to confirm items across multiple frames
   - Only classify items seen for 3+ frames (reduces flickering detections)
   - Avoids classifying every frame (30+ FPS → ~1 classification/2 seconds)

2. **Local caching**
   - Cache classifications by image hash (if user re-scans same item)
   - Session-level cache (valid for 5 minutes)
   - Persistent cache for common items (user's home items)

3. **Batch optimization** (future)
   - Group multiple items into single Vision API request
   - Google Vision supports batch inference (up to 16 images)

4. **Smart triggers**
   - Only classify when user explicitly "captures" item (tap shutter)
   - Don't auto-classify in preview/scan mode unless user opts in

**Estimated costs:**
- Google Vision API: $1.50 per 1,000 images (first 1,000 free/month)
- With optimization: ~10 classifications per user session
- 1,000 users/day → 10,000 classifications/day → $15/day → $450/month
- Acceptable for MVP; can optimize further with caching

---

***REMOVED******REMOVED*** Consequences

***REMOVED******REMOVED******REMOVED*** Positive

- ✅ **Secure**: No API keys in mobile app, no risk of key extraction
- ✅ **Accurate**: Google Vision provides best-in-class object recognition
- ✅ **Flexible**: Backend can add features (caching, A/B tests, analytics)
- ✅ **Cross-platform**: Same API for Android and iOS
- ✅ **Scalable**: Backend can handle rate limiting, load balancing
- ✅ **Auditable**: All classification requests logged for debugging/analytics

***REMOVED******REMOVED******REMOVED*** Negative

- ⚠️ **Requires backend**: Additional infrastructure to develop and maintain
- ⚠️ **Network dependency**: Classification fails offline (mitigated by fallback)
- ⚠️ **Latency**: Extra 50-100ms for backend hop (acceptable for async flow)
- ⚠️ **Cost**: Google Vision API costs ($1.50/1k images, optimized to ~$450/month)

***REMOVED******REMOVED******REMOVED*** Risks and Mitigation

**Risk 1: Backend downtime breaks classification**
- Mitigation: Graceful fallback to ML Kit coarse labels
- SLA: Backend should have 99.5% uptime (serverless/managed hosting)

**Risk 2: API cost spikes from abuse**
- Mitigation: Aggressive rate limiting (100/hour per user, 10k/hour global)
- Monitoring: Alert when daily costs exceed $50

**Risk 3: Classification latency impacts UX**
- Mitigation: Async design - classification doesn't block detection/overlay
- User sees item immediately with "Classifying..." indicator
- Classification result updates in 1-2 seconds

---

***REMOVED******REMOVED*** Privacy & EU GDPR Considerations

***REMOVED******REMOVED******REMOVED*** Data Minimization

**What is sent to backend/Google Vision:**
- ✅ **Cropped item thumbnail only** (200x200 pixels max, JPEG format)
- ✅ **EXIF metadata stripped** (location, device info, timestamps removed)
- ✅ **Domain pack ID** (e.g., "home_resale" - non-personal data)
- ✅ **Optional ML Kit hint** (e.g., "Home good" - generic label)
- ✅ **User auth token** (JWT, for rate limiting and attribution only)

**What is NOT sent:**
- ❌ Full camera frames
- ❌ User location or GPS coordinates
- ❌ Device identifiers (IMEI, serial number)
- ❌ User personal information (name, email, address)
- ❌ Metadata beyond the cropped item image

***REMOVED******REMOVED******REMOVED*** GDPR Compliance Strategy

***REMOVED******REMOVED******REMOVED******REMOVED*** 1. Legal Basis for Processing
**Article 6(1)(b) GDPR - Contract Performance:**
- Classification is necessary for core app functionality (item categorization for resale)
- Users explicitly request classification by scanning items
- Service cannot be provided without classification

**Article 6(1)(f) GDPR - Legitimate Interest:**
- Improving classification accuracy benefits users (better pricing, faster selling)
- Minimal data processing (cropped thumbnails only)
- User expectations align with processing (scanning app needs to identify items)

***REMOVED******REMOVED******REMOVED******REMOVED*** 2. Data Retention Policy

**Backend Storage:**
```
Default: NO RETENTION
├─▶ Images deleted immediately after classification response
├─▶ requestId logged for debugging (30 days max)
└─▶ Aggregate metrics only (no PII): category counts, latency stats

Optional (User Opt-In):
├─▶ "Improve classification quality" opt-in checkbox in settings
├─▶ If enabled: Store anonymized crops for 90 days for model training
└─▶ Full deletion after 90 days, no exceptions
```

**Google Vision API:**
- Google Cloud Vision does NOT retain images by default (per Google's data processing terms)
- Images processed transiently, deleted after response
- Customer data not used for Google's model training (per Cloud Vision SLA)
- Verify: Use Google Cloud Vision with "Customer-managed encryption keys" (CMEK) option

***REMOVED******REMOVED******REMOVED******REMOVED*** 3. User Consent & Transparency

**In-App Notice (First Classification):**
```
┌─────────────────────────────────────────────────────────┐
│  🔍 Cloud Classification                                 │
│                                                          │
│  To improve item identification, we'll send cropped     │
│  images to our servers for analysis. We:                │
│                                                          │
│  ✓ Only send the item image (not full camera frames)    │
│  ✓ Remove location and device info                      │
│  ✓ Delete images immediately after processing           │
│  ✓ Use secure EU-based servers (GDPR compliant)         │
│                                                          │
│  You can disable cloud classification in Settings.      │
│                                                          │
│  [ Learn More ]  [ Disable ]  [ Continue ]              │
└─────────────────────────────────────────────────────────┘
```

**Settings Toggle:**
```
Settings > Privacy > Cloud Classification
  ☑ Enable cloud classification (recommended)
      Sends cropped item images to our servers for better
      categorization. Images are deleted immediately.

  ☐ Help improve classification quality (optional)
      Store anonymized image crops for 90 days to train models.
      You can opt out anytime.
```

***REMOVED******REMOVED******REMOVED******REMOVED*** 4. Data Subject Rights (GDPR Chapter III)

**Right to Access (Art. 15):**
- User can request: "What data do you have about my classifications?"
- Response: "We store requestId logs for 30 days (no images retained)"
- Provide: Export of requestId, timestamp, category, confidence (CSV format)

**Right to Erasure (Art. 17):**
- User can delete: All classification history
- Implementation: Backend `/api/v1/user/delete-classification-data` endpoint
- Timeline: Immediate deletion (< 24 hours)

**Right to Object (Art. 21):**
- User can disable cloud classification entirely
- Fallback: App uses on-device ML Kit only (reduced accuracy)

**Right to Data Portability (Art. 20):**
- User can export: Classification history (requestId, category, timestamp)
- Format: JSON or CSV download

***REMOVED******REMOVED******REMOVED******REMOVED*** 5. EU Data Hosting

**Backend Location:**
```
Primary: EU region (e.g., GCP europe-west1, AWS eu-central-1)
└─▶ Google Cloud Vision API: Use EU endpoint (vision-eu.googleapis.com)
    └─▶ Data processed entirely within EU
    └─▶ No cross-border transfers to US

If US backend required:
├─▶ Use EU-US Data Privacy Framework (DPF) certified provider
├─▶ Standard Contractual Clauses (SCCs) with backend provider
└─▶ Update privacy policy with international transfer notice
```

**Google Cloud Vision EU Endpoint:**
```kotlin
// Backend configuration
const GOOGLE_VISION_ENDPOINT = "https://vision-eu.googleapis.com/v1/images:annotate"
// Ensures data stays in EU
```

***REMOVED******REMOVED******REMOVED******REMOVED*** 6. Privacy Policy Requirements

**Must Include:**
- What data is collected (cropped images, requestId, timestamps)
- Why it's collected (item classification for app functionality)
- Who processes it (Scanium backend, Google Cloud Vision)
- How long it's retained (default: no retention; optional 90 days with opt-in)
- User rights (access, erasure, objection, portability)
- How to exercise rights (email, in-app settings)
- Data security measures (encryption, HTTPS, access controls)

**Sample Privacy Policy Section:**
```markdown
***REMOVED******REMOVED******REMOVED*** Cloud Classification Feature

When you scan an item, Scanium may send a cropped image of the
item to our servers for enhanced identification. We:

- Only send the cropped item image (not full camera frames)
- Strip all location and device metadata before transmission
- Use EU-based servers and Google Cloud Vision (EU endpoint)
- Delete images immediately after classification (no retention)
- Do not share data with third parties for marketing purposes

You can disable this feature in Settings > Privacy. When disabled,
the app uses on-device detection only (less accurate).

If you opt in to "Help improve classification quality," we may
store anonymized image crops for up to 90 days to improve our
models. You can opt out anytime.

For data deletion requests: privacy@scanium.com
```

***REMOVED******REMOVED******REMOVED******REMOVED*** 7. Data Processing Agreement (DPA)

**With Google Cloud:**
- ✅ Google Cloud Vision includes GDPR-compliant DPA by default
- ✅ Google is a processor, Scanium is controller
- ✅ Google's standard contractual clauses cover EU requirements

**With Backend Provider (if external):**
- If using managed hosting (Heroku, Render, etc.), ensure they have:
  - GDPR-compliant DPA
  - EU data residency options
  - Security certifications (ISO 27001, SOC 2)

***REMOVED******REMOVED******REMOVED******REMOVED*** 8. Security Measures (Art. 32 GDPR)

**Technical Safeguards:**
- ✅ HTTPS/TLS 1.3 for all data transmission
- ✅ AES-256 encryption at rest (backend database)
- ✅ JWT token authentication (short-lived, 1-hour expiry)
- ✅ Rate limiting to prevent abuse
- ✅ Access logs with retention limits (90 days)

**Organizational Safeguards:**
- Limited access: Only authorized engineers can access logs
- Audit trail: All backend access logged
- Incident response plan: Data breach notification within 72 hours (Art. 33)

***REMOVED******REMOVED******REMOVED*** Minimal Data Retention (Default Configuration)

**Recommendation: Stateless Processing**

```
┌──────────┐    1. Upload crop    ┌──────────────┐
│  Mobile  │───────────────────▶│   Backend    │
│   App    │                     │              │
│          │    2. Call Google    │              │
│          │◀───────────────────│ ┌──────────┐ │
│          │   3. Return result  │ │ Google   │ │
│          │                     │ │ Vision   │ │
└──────────┘                     │ └──────────┘ │
                                 │              │
                                 │ NO STORAGE   │
                                 │ (Stateless)  │
                                 └──────────────┘
```

**Backend Processing:**
1. Receive image upload
2. Forward to Google Vision API (EU endpoint)
3. Receive classification response
4. Map to domain categories
5. Return result to mobile app
6. **Delete image from memory immediately**
7. Log only: `requestId, userId, timestamp, categoryId, confidence` (no image data)

**Logging for Debugging:**
```json
{
  "requestId": "req_abc123",
  "userId": "hashed_user_id",
  "timestamp": "2025-12-18T15:30:00Z",
  "domainCategoryId": "furniture_sofa",
  "confidence": 0.87,
  "latencyMs": 234,
  "source": "google_vision"
}
```
- No image data stored
- userId hashed/anonymized
- Auto-delete after 30 days

***REMOVED******REMOVED******REMOVED*** User Control & Transparency

**Settings Screen:**
```
Privacy & Data

Cloud Classification
  ☑ Enable enhanced classification
      Uses Google Cloud Vision for better accuracy.
      Images deleted immediately after processing.
      [Learn More]

  ☐ Help improve Scanium
      Store anonymized crops for 90 days to train models.
      [Learn More]

Data Residency
  📍 Your data is processed in: European Union
      Classification uses EU-based servers only.

Your Rights
  • View classification history
  • Download your data (JSON/CSV)
  • Delete all classification data
  • Disable cloud classification
      [Manage Privacy Settings]
```

**Explicit User Actions Required:**
- First classification: Show consent dialog (one-time)
- Optional model training: Separate opt-in checkbox (off by default)
- Opt-out: Always available in settings (instant disable)

---

***REMOVED******REMOVED*** Follow-up Actions

1. **Immediate (Phase 2-3):**
   - [ ] Implement `CloudClassificationRepository` interface in `shared:core-data`
   - [ ] Build Android implementation using Ktor HTTP client
   - [ ] Add feature flag `cloudClassificationEnabled` (disabled by default)
   - [ ] Add fallback logic to `ClassifyStableItemUseCase`

2. **Backend Development (Parallel track):**
   - [ ] Design `/api/v1/classify` endpoint spec
   - [ ] Implement backend service (Go/Node.js recommended)
   - [ ] Set up Google Vision API service account
   - [ ] Implement rate limiting and auth middleware
   - [ ] Deploy to cloud (GCP Cloud Run / AWS Lambda recommended)

3. **Security Audit (Before production):**
   - [ ] Verify no API keys in APK (decompile and search)
   - [ ] Penetration test rate limiting
   - [ ] Validate JWT token flow
   - [ ] Load test backend (1000 req/sec)

4. **Cost Monitoring (Post-launch):**
   - [ ] Set up billing alerts (>$500/month)
   - [ ] Dashboard for classification metrics
   - [ ] Monthly cost review and optimization

---

***REMOVED******REMOVED*** References

- [Google Cloud Vision API Documentation](https://cloud.google.com/vision/docs)
- [API Security Best Practices](https://owasp.org/www-project-api-security/)
- [Rate Limiting Strategies](https://www.cloudflare.com/learning/bots/what-is-rate-limiting/)
- Scanium Domain Pack specification: `docs/domain-pack-spec.md` (TBD)

---

***REMOVED******REMOVED*** Appendix: Alternative Google Vision Integration

If backend proxy is not feasible initially, consider **Google Cloud Identity-Aware Proxy (IAP)**:
- Mobile app gets short-lived token from Firebase Auth
- Token exchanged for Google Cloud access via IAP
- Direct Vision API calls with ephemeral credentials
- More complex than backend proxy, but removes backend requirement

**Decision:** Deferred to post-MVP. Backend proxy is simpler and more flexible.
