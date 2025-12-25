import { z } from 'zod';

const weakSessionSecrets = [
  'change_me_to_a_random_secret_min_32_chars',
  'change_me_generate_random_32_chars',
  'replace_with_random_base64_64_bytes',
];

const allowedCorsProtocols = new Set(['http:', 'https:', 'scanium:']);

export function isValidCorsOrigin(origin: string): boolean {
  if (!origin || origin.includes('*')) {
    return false;
  }

  try {
    const url = new URL(origin);
    const hasAllowedProtocol = allowedCorsProtocols.has(url.protocol);
    const hasHostForWebProtocols = ['http:', 'https:'].includes(url.protocol)
      ? Boolean(url.hostname)
      : true;
    const hasNoPathOrQuery =
      (url.pathname === '/' || url.pathname === '') && !url.search && !url.hash;

    if (url.protocol === 'scanium:') {
      return hasAllowedProtocol && hasNoPathOrQuery && origin.startsWith('scanium://');
    }

    return hasAllowedProtocol && hasHostForWebProtocols && origin === url.origin && hasNoPathOrQuery;
  } catch (error) {
    return false;
  }
}

/**
 * Configuration schema with strict validation
 * Server will not start if validation fails
 */
export const configSchema = z.object({
  // Application
  nodeEnv: z.enum(['development', 'production', 'test']).default('development'),
  port: z.coerce.number().int().min(1).max(65535).default(8080),
  publicBaseUrl: z.string().url(),

  // Database
  databaseUrl: z.string().min(1),

  // Cloud classification proxy
  classifier: z.object({
    provider: z.enum(['mock', 'google']).default('mock'),
    visionFeature: z
      .enum(['LABEL_DETECTION', 'OBJECT_LOCALIZATION'])
      .default('LABEL_DETECTION'),
    maxUploadBytes: z
      .coerce.number()
      .int()
      .min(1024)
      .max(10 * 1024 * 1024)
      .default(5 * 1024 * 1024),
    rateLimitPerMinute: z.coerce.number().int().min(1).default(60),
    ipRateLimitPerMinute: z.coerce.number().int().min(1).default(60),
    rateLimitWindowSeconds: z.coerce.number().int().min(1).default(60),
    rateLimitBackoffSeconds: z.coerce.number().int().min(1).default(30),
    rateLimitBackoffMaxSeconds: z.coerce.number().int().min(1).default(900),
    rateLimitRedisUrl: z.string().optional(),
    concurrentLimit: z.coerce.number().int().min(1).default(2),
    apiKeys: z
      .string()
      .default('')
      .transform((val) =>
        val
          .split(',')
          .map((v) => v.trim())
          .filter(Boolean)
      ),
    domainPackId: z.string().default('home_resale'),
    domainPackPath: z.string().default('src/modules/classifier/domain/home-resale.json'),
    retainUploads: z.coerce.boolean().default(false),
    mockSeed: z.string().default('scanium-mock'),
    visionTimeoutMs: z.coerce.number().int().min(1000).max(20000).default(10000),
    visionMaxRetries: z.coerce.number().int().min(0).max(5).default(2),
    cacheTtlSeconds: z.coerce.number().int().min(1).default(300),
    cacheMaxEntries: z.coerce.number().int().min(1).default(1000),
    circuitBreakerFailureThreshold: z.coerce.number().int().min(1).default(5),
    circuitBreakerCooldownSeconds: z.coerce.number().int().min(1).default(60),
    circuitBreakerMinimumRequests: z.coerce.number().int().min(1).default(3),
  }),

  assistant: z
    .object({
      provider: z.enum(['mock', 'openai', 'disabled']).default('mock'),
      openaiApiKey: z.string().optional(),
      openaiModel: z.string().default('gpt-4o-mini'),
      apiKeys: z
        .string()
        .default('')
        .transform((val) =>
          val
            .split(',')
            .map((v) => v.trim())
            .filter(Boolean)
        ),
      rateLimitPerMinute: z.coerce.number().int().min(1).default(60),
      ipRateLimitPerMinute: z.coerce.number().int().min(1).default(60),
      deviceRateLimitPerMinute: z.coerce.number().int().min(1).default(30),
      rateLimitWindowSeconds: z.coerce.number().int().min(1).default(60),
      rateLimitBackoffSeconds: z.coerce.number().int().min(1).default(30),
      rateLimitBackoffMaxSeconds: z.coerce.number().int().min(1).default(900),
      rateLimitRedisUrl: z.string().optional(),
      dailyQuota: z.coerce.number().int().min(1).default(200),
      maxInputChars: z.coerce.number().int().min(100).max(10000).default(2000),
      maxOutputTokens: z.coerce.number().int().min(50).max(2000).default(500),
      maxContextItems: z.coerce.number().int().min(1).max(50).default(10),
      maxAttributesPerItem: z.coerce.number().int().min(1).max(100).default(20),
      providerTimeoutMs: z.coerce.number().int().min(1000).max(120000).default(30000),
      logContent: z.coerce.boolean().default(false),
      circuitBreakerFailureThreshold: z.coerce.number().int().min(1).default(5),
      circuitBreakerCooldownSeconds: z.coerce.number().int().min(1).default(60),
      circuitBreakerMinimumRequests: z.coerce.number().int().min(1).default(3),
      /** Response cache TTL in seconds (default 1h) */
      responseCacheTtlSeconds: z.coerce.number().int().min(60).max(86400).default(3600),
      /** Maximum response cache entries */
      responseCacheMaxEntries: z.coerce.number().int().min(10).max(5000).default(1000),
      /** Enable request deduplication (in-flight coalescing) */
      enableRequestDedup: z.coerce.boolean().default(true),
      /** Staged response timeout for pending requests (ms) */
      stagedResponseTimeoutMs: z.coerce.number().int().min(5000).max(120000).default(60000),
    })
    .default({}),

  // Vision extraction for assistant
  vision: z
    .object({
      /** Enable vision extraction for assistant requests */
      enabled: z.coerce.boolean().default(true),
      /** Vision provider: 'mock' for testing, 'google' for production */
      provider: z.enum(['mock', 'google']).default('mock'),
      /** Enable OCR text detection */
      enableOcr: z.coerce.boolean().default(true),
      /** Enable label detection */
      enableLabels: z.coerce.boolean().default(true),
      /** Enable logo detection (additional cost) */
      enableLogos: z.coerce.boolean().default(true),
      /** Enable dominant color extraction */
      enableColors: z.coerce.boolean().default(true),
      /** Vision API timeout in milliseconds */
      timeoutMs: z.coerce.number().int().min(1000).max(30000).default(10000),
      /** Maximum retries for Vision API calls */
      maxRetries: z.coerce.number().int().min(0).max(5).default(2),
      /** Cache TTL in seconds (default 6h for VisualFacts) */
      cacheTtlSeconds: z.coerce.number().int().min(60).max(86400).default(21600),
      /** Maximum cache entries */
      cacheMaxEntries: z.coerce.number().int().min(10).max(10000).default(500),
      /** Maximum OCR snippets per item */
      maxOcrSnippets: z.coerce.number().int().min(1).max(50).default(10),
      /** Maximum label hints per item */
      maxLabelHints: z.coerce.number().int().min(1).max(50).default(10),
      /** Maximum logo hints per item */
      maxLogoHints: z.coerce.number().int().min(1).max(10).default(5),
      /** Maximum dominant colors per item */
      maxColors: z.coerce.number().int().min(1).max(10).default(5),
      /** Maximum OCR snippet length */
      maxOcrSnippetLength: z.coerce.number().int().min(10).max(500).default(100),
      /** Minimum OCR confidence (0-1) */
      minOcrConfidence: z.coerce.number().min(0).max(1).default(0.5),
      /** Minimum label confidence (0-1) */
      minLabelConfidence: z.coerce.number().min(0).max(1).default(0.5),
      /** Minimum logo confidence (0-1) */
      minLogoConfidence: z.coerce.number().min(0).max(1).default(0.5),
    })
    .default({}),

  googleCredentialsPath: z.string().optional(),

  // eBay OAuth
  ebay: z.object({
    env: z.enum(['sandbox', 'production']),
    clientId: z.string().min(1),
    clientSecret: z.string().min(1),
    redirectPath: z.string().default('/auth/ebay/callback'),
    scopes: z.string().min(1),
    tokenEncryptionKey: z.string().min(32),
  }),

  // Security
  sessionSigningSecret: z
    .string()
    .min(64)
    .refine(
      (secret) => !weakSessionSecrets.includes(secret.trim().toLowerCase()),
      'SESSION_SIGNING_SECRET must be a strong, unique value'
    ),
  security: z
    .object({
      enforceHttps: z.coerce.boolean().default(true),
      enableHsts: z.coerce.boolean().default(true),
      apiKeyRotationEnabled: z.coerce.boolean().default(true),
      apiKeyExpirationDays: z.coerce.number().int().min(0).default(90),
      logApiKeyUsage: z.coerce.boolean().default(true),
    })
    .default({}),

  admin: z
    .object({
      enabled: z.coerce.boolean().default(false),
      adminKey: z.string().optional(),
    })
    .default({}),

// CORS
  corsOrigins: z
    .string()
    .transform((val) => val.split(',').map((o) => o.trim()))
    .pipe(
      z
        .array(z.string().min(1))
        .nonempty('CORS_ORIGINS must include at least one origin')
        .refine(
          (origins) => origins.every(isValidCorsOrigin),
          'CORS origins must be fully-qualified URLs with http/https/custom schemes and no wildcards'
        )
    ),
});

export type Config = z.infer<typeof configSchema>;

/**
 * Load and validate environment variables
 * Throws if validation fails - server will not start
 */
export function loadConfig(): Config {
  const rawConfig = {
    nodeEnv: process.env.NODE_ENV,
    port: process.env.PORT,
    publicBaseUrl: process.env.PUBLIC_BASE_URL,
    databaseUrl: process.env.DATABASE_URL,
    classifier: {
      provider: process.env.SCANIUM_CLASSIFIER_PROVIDER,
      visionFeature: process.env.VISION_FEATURE,
      maxUploadBytes: process.env.MAX_UPLOAD_BYTES,
      rateLimitPerMinute: process.env.CLASSIFIER_RATE_LIMIT_PER_MINUTE,
      ipRateLimitPerMinute: process.env.CLASSIFIER_IP_RATE_LIMIT_PER_MINUTE,
      rateLimitWindowSeconds: process.env.CLASSIFIER_RATE_LIMIT_WINDOW_SECONDS,
      rateLimitBackoffSeconds: process.env.CLASSIFIER_RATE_LIMIT_BACKOFF_SECONDS,
      rateLimitBackoffMaxSeconds: process.env.CLASSIFIER_RATE_LIMIT_BACKOFF_MAX_SECONDS,
      rateLimitRedisUrl: process.env.RATE_LIMIT_REDIS_URL,
      concurrentLimit: process.env.CLASSIFIER_CONCURRENCY_LIMIT,
      apiKeys: process.env.SCANIUM_API_KEYS,
      domainPackId: process.env.DOMAIN_PACK_ID,
      domainPackPath: process.env.DOMAIN_PACK_PATH,
      retainUploads: process.env.CLASSIFIER_RETAIN_UPLOADS,
      mockSeed: process.env.CLASSIFIER_MOCK_SEED,
      visionTimeoutMs: process.env.VISION_TIMEOUT_MS,
      visionMaxRetries: process.env.VISION_MAX_RETRIES,
      cacheTtlSeconds: process.env.CLASSIFIER_CACHE_TTL_SECONDS,
      cacheMaxEntries: process.env.CLASSIFIER_CACHE_MAX_ENTRIES,
      circuitBreakerFailureThreshold: process.env.CLASSIFIER_CIRCUIT_FAILURE_THRESHOLD,
      circuitBreakerCooldownSeconds: process.env.CLASSIFIER_CIRCUIT_COOLDOWN_SECONDS,
      circuitBreakerMinimumRequests: process.env.CLASSIFIER_CIRCUIT_MIN_REQUESTS,
    },
    assistant: {
      provider: process.env.SCANIUM_ASSISTANT_PROVIDER,
      openaiApiKey: process.env.OPENAI_API_KEY,
      openaiModel: process.env.OPENAI_MODEL,
      apiKeys: process.env.SCANIUM_ASSISTANT_API_KEYS,
      rateLimitPerMinute: process.env.ASSIST_RATE_LIMIT_PER_MINUTE,
      ipRateLimitPerMinute: process.env.ASSIST_IP_RATE_LIMIT_PER_MINUTE,
      deviceRateLimitPerMinute: process.env.ASSIST_DEVICE_RATE_LIMIT_PER_MINUTE,
      rateLimitWindowSeconds: process.env.ASSIST_RATE_LIMIT_WINDOW_SECONDS,
      rateLimitBackoffSeconds: process.env.ASSIST_RATE_LIMIT_BACKOFF_SECONDS,
      rateLimitBackoffMaxSeconds: process.env.ASSIST_RATE_LIMIT_BACKOFF_MAX_SECONDS,
      rateLimitRedisUrl: process.env.ASSIST_RATE_LIMIT_REDIS_URL,
      dailyQuota: process.env.ASSIST_DAILY_QUOTA,
      maxInputChars: process.env.ASSIST_MAX_INPUT_CHARS,
      maxOutputTokens: process.env.ASSIST_MAX_OUTPUT_TOKENS,
      maxContextItems: process.env.ASSIST_MAX_CONTEXT_ITEMS,
      maxAttributesPerItem: process.env.ASSIST_MAX_ATTRIBUTES_PER_ITEM,
      providerTimeoutMs: process.env.ASSIST_PROVIDER_TIMEOUT_MS,
      logContent: process.env.ASSIST_LOG_CONTENT,
      circuitBreakerFailureThreshold: process.env.ASSIST_CIRCUIT_FAILURE_THRESHOLD,
      circuitBreakerCooldownSeconds: process.env.ASSIST_CIRCUIT_COOLDOWN_SECONDS,
      circuitBreakerMinimumRequests: process.env.ASSIST_CIRCUIT_MIN_REQUESTS,
      responseCacheTtlSeconds: process.env.ASSIST_RESPONSE_CACHE_TTL_SECONDS,
      responseCacheMaxEntries: process.env.ASSIST_RESPONSE_CACHE_MAX_ENTRIES,
      enableRequestDedup: process.env.ASSIST_ENABLE_REQUEST_DEDUP,
      stagedResponseTimeoutMs: process.env.ASSIST_STAGED_RESPONSE_TIMEOUT_MS,
    },
    vision: {
      enabled: process.env.VISION_ENABLED,
      provider: process.env.VISION_PROVIDER,
      enableOcr: process.env.VISION_ENABLE_OCR,
      enableLabels: process.env.VISION_ENABLE_LABELS,
      enableLogos: process.env.VISION_ENABLE_LOGOS,
      enableColors: process.env.VISION_ENABLE_COLORS,
      timeoutMs: process.env.VISION_TIMEOUT_MS,
      maxRetries: process.env.VISION_MAX_RETRIES,
      cacheTtlSeconds: process.env.VISION_CACHE_TTL_SECONDS,
      cacheMaxEntries: process.env.VISION_CACHE_MAX_ENTRIES,
      maxOcrSnippets: process.env.VISION_MAX_OCR_SNIPPETS,
      maxLabelHints: process.env.VISION_MAX_LABEL_HINTS,
      maxLogoHints: process.env.VISION_MAX_LOGO_HINTS,
      maxColors: process.env.VISION_MAX_COLORS,
      maxOcrSnippetLength: process.env.VISION_MAX_OCR_SNIPPET_LENGTH,
      minOcrConfidence: process.env.VISION_MIN_OCR_CONFIDENCE,
      minLabelConfidence: process.env.VISION_MIN_LABEL_CONFIDENCE,
      minLogoConfidence: process.env.VISION_MIN_LOGO_CONFIDENCE,
    },
    googleCredentialsPath: process.env.GOOGLE_APPLICATION_CREDENTIALS,
    ebay: {
      env: process.env.EBAY_ENV,
      clientId: process.env.EBAY_CLIENT_ID,
      clientSecret: process.env.EBAY_CLIENT_SECRET,
      redirectPath: process.env.EBAY_REDIRECT_PATH,
      scopes: process.env.EBAY_SCOPES,
      tokenEncryptionKey: process.env.EBAY_TOKEN_ENCRYPTION_KEY,
    },
    sessionSigningSecret: process.env.SESSION_SIGNING_SECRET,
    security: {
      enforceHttps: process.env.SECURITY_ENFORCE_HTTPS,
      enableHsts: process.env.SECURITY_ENABLE_HSTS,
      apiKeyRotationEnabled: process.env.SECURITY_API_KEY_ROTATION_ENABLED,
      apiKeyExpirationDays: process.env.SECURITY_API_KEY_EXPIRATION_DAYS,
      logApiKeyUsage: process.env.SECURITY_LOG_API_KEY_USAGE,
    },
    admin: {
      enabled: process.env.ADMIN_USAGE_ENABLED,
      adminKey: process.env.ADMIN_USAGE_KEY,
    },
    corsOrigins: process.env.CORS_ORIGINS,
  };

  const result = configSchema.safeParse(rawConfig);

  if (!result.success) {
    console.error('❌ Configuration validation failed:');
    console.error(result.error.format());
    throw new Error('Invalid configuration - check environment variables');
  }

  return result.data;
}

/**
 * Get eBay authorization endpoint based on environment
 */
export function getEbayAuthEndpoint(config: Config): string {
  return config.ebay.env === 'production'
    ? 'https://auth.ebay.com/oauth2/authorize'
    : 'https://auth.sandbox.ebay.com/oauth2/authorize';
}

/**
 * Get eBay token endpoint based on environment
 */
export function getEbayTokenEndpoint(config: Config): string {
  return config.ebay.env === 'production'
    ? 'https://api.ebay.com/identity/v1/oauth2/token'
    : 'https://api.sandbox.ebay.com/identity/v1/oauth2/token';
}
