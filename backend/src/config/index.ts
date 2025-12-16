import { z } from 'zod';

/**
 * Configuration schema with strict validation
 * Server will not start if validation fails
 */
const configSchema = z.object({
  // Application
  nodeEnv: z.enum(['development', 'production', 'test']).default('development'),
  port: z.coerce.number().int().min(1).max(65535).default(8080),
  publicBaseUrl: z.string().url(),

  // Database
  databaseUrl: z.string().min(1),

  // eBay OAuth
  ebay: z.object({
    env: z.enum(['sandbox', 'production']),
    clientId: z.string().min(1),
    clientSecret: z.string().min(1),
    redirectPath: z.string().default('/auth/ebay/callback'),
    scopes: z.string().min(1),
  }),

  // Security
  sessionSigningSecret: z.string().min(32),

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
    ebay: {
      env: process.env.EBAY_ENV,
      clientId: process.env.EBAY_CLIENT_ID,
      clientSecret: process.env.EBAY_CLIENT_SECRET,
      redirectPath: process.env.EBAY_REDIRECT_PATH,
      scopes: process.env.EBAY_SCOPES,
    },
    sessionSigningSecret: process.env.SESSION_SIGNING_SECRET,
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
