/**
 * Test setup file - runs before all tests
 * Sets up required environment variables for test execution
 */

// Set required environment variables if not already set
if (!process.env.NODE_ENV) {
  process.env.NODE_ENV = 'test';
}

if (!process.env.PORT) {
  process.env.PORT = '3000';
}

if (!process.env.PUBLIC_BASE_URL) {
  process.env.PUBLIC_BASE_URL = 'http://localhost:3000';
}

if (!process.env.DATABASE_URL) {
  process.env.DATABASE_URL = 'postgresql://user:pass@localhost:5432/testdb';
}

if (!process.env.EBAY_ENV) {
  process.env.EBAY_ENV = 'sandbox';
}

if (!process.env.EBAY_CLIENT_ID) {
  process.env.EBAY_CLIENT_ID = 'test-client-id';
}

if (!process.env.EBAY_CLIENT_SECRET) {
  process.env.EBAY_CLIENT_SECRET = 'test-client-secret-minimum-length';
}

if (!process.env.EBAY_SCOPES) {
  process.env.EBAY_SCOPES = 'https://api.ebay.com/oauth/api_scope';
}

if (!process.env.EBAY_TOKEN_ENCRYPTION_KEY) {
  process.env.EBAY_TOKEN_ENCRYPTION_KEY = 'x'.repeat(32);
}

if (!process.env.SESSION_SIGNING_SECRET) {
  process.env.SESSION_SIGNING_SECRET = 'x'.repeat(64);
}

if (!process.env.CORS_ORIGINS) {
  process.env.CORS_ORIGINS = 'http://localhost:3000';
}

if (!process.env.CLASSIFIER_PROVIDER) {
  process.env.CLASSIFIER_PROVIDER = 'mock';
}

if (!process.env.CLASSIFIER_API_KEYS) {
  process.env.CLASSIFIER_API_KEYS = 'test-api-key';
}

if (!process.env.ASSISTANT_PROVIDER) {
  process.env.ASSISTANT_PROVIDER = 'mock';
}

if (!process.env.ASSISTANT_API_KEYS) {
  process.env.ASSISTANT_API_KEYS = 'test-assistant-key';
}

if (!process.env.VISION_ENABLED) {
  process.env.VISION_ENABLED = 'true';
}

if (!process.env.VISION_PROVIDER) {
  process.env.VISION_PROVIDER = 'mock';
}

if (!process.env.VISION_ENABLE_OCR) {
  process.env.VISION_ENABLE_OCR = 'true';
}

if (!process.env.VISION_ENABLE_LABELS) {
  process.env.VISION_ENABLE_LABELS = 'true';
}

if (!process.env.VISION_ENABLE_LOGOS) {
  process.env.VISION_ENABLE_LOGOS = 'true';
}

if (!process.env.VISION_ENABLE_COLORS) {
  process.env.VISION_ENABLE_COLORS = 'true';
}

if (!process.env.SECURITY_ENFORCE_HTTPS) {
  process.env.SECURITY_ENFORCE_HTTPS = 'false';
}

if (!process.env.SECURITY_ENABLE_HSTS) {
  process.env.SECURITY_ENABLE_HSTS = 'false';
}

if (!process.env.SECURITY_API_KEY_ROTATION_ENABLED) {
  process.env.SECURITY_API_KEY_ROTATION_ENABLED = 'false';
}

if (!process.env.SECURITY_API_KEY_EXPIRATION_DAYS) {
  process.env.SECURITY_API_KEY_EXPIRATION_DAYS = '90';
}

if (!process.env.SECURITY_LOG_API_KEY_USAGE) {
  process.env.SECURITY_LOG_API_KEY_USAGE = 'false';
}

// Phase C: Auth configuration
if (!process.env.GOOGLE_OAUTH_CLIENT_ID) {
  process.env.GOOGLE_OAUTH_CLIENT_ID = 'test-google-client-id.apps.googleusercontent.com';
}

if (!process.env.AUTH_SESSION_SECRET) {
  process.env.AUTH_SESSION_SECRET = 'x'.repeat(32);
}

if (!process.env.AUTH_SESSION_EXPIRY_SECONDS) {
  process.env.AUTH_SESSION_EXPIRY_SECONDS = '3600'; // 1 hour (minimum required)
}

if (!process.env.AUTH_REFRESH_TOKEN_EXPIRY_SECONDS) {
  process.env.AUTH_REFRESH_TOKEN_EXPIRY_SECONDS = '7200'; // 2 hours (minimum required)
}
