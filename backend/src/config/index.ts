import { z } from 'zod';

const weakSessionSecrets = [
  'change_me_to_a_random_secret_min_32_chars',
  'change_me_generate_random_32_chars',
  'replace_with_random_base64_64_bytes',
];

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
  }),

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

  // CORS
  corsOrigins: z
    .string()
    .transform((val) => val.split(',').map((o) => o.trim()))
    .pipe(z.array(z.string().min(1))),
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
