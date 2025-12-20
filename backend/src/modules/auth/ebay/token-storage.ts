import { prisma } from '../../../infra/db/prisma.js';
import { Config } from '../../../config/index.js';
import { DatabaseError } from '../../../shared/errors/index.js';
import { encryptSecret } from '../../../shared/security/secret-crypto.js';

/**
 * Get or create default user
 * For PoC: single user system
 * TODO: Replace with proper user authentication later
 */
async function getOrCreateDefaultUser(): Promise<string> {
  try {
    const email = 'default@scanium.app';

    let user = await prisma.user.findUnique({
      where: { email },
    });

    if (!user) {
      user = await prisma.user.create({
        data: {
          email,
          displayName: 'Scanium User',
        },
      });
    }

    return user.id;
  } catch (error) {
    throw new DatabaseError('Failed to get or create user', {
      error: error instanceof Error ? error.message : 'Unknown error',
    });
  }
}

/**
 * Store eBay OAuth tokens
 * Creates or updates the connection for the user
 */
export async function storeEbayTokens(
  config: Config,
  tokens: {
    accessToken: string;
    refreshToken: string;
    expiresIn: number;
    tokenType: string;
    scope: string;
  }
): Promise<void> {
  try {
    const userId = await getOrCreateDefaultUser();
    const expiresAt = new Date(Date.now() + tokens.expiresIn * 1000);
    const encryptedAccessToken = encryptSecret(
      tokens.accessToken,
      config.ebay.tokenEncryptionKey
    );
    const encryptedRefreshToken = encryptSecret(
      tokens.refreshToken,
      config.ebay.tokenEncryptionKey
    );

    await prisma.ebayConnection.upsert({
      where: {
        userId_environment: {
          userId,
          environment: config.ebay.env,
        },
      },
      create: {
        userId,
        environment: config.ebay.env,
        accessToken: encryptedAccessToken,
        refreshToken: encryptedRefreshToken,
        expiresAt,
        tokenType: tokens.tokenType,
        scopes: tokens.scope,
      },
      update: {
        accessToken: encryptedAccessToken,
        refreshToken: encryptedRefreshToken,
        expiresAt,
        tokenType: tokens.tokenType,
        scopes: tokens.scope,
        updatedAt: new Date(),
      },
    });
  } catch (error) {
    throw new DatabaseError('Failed to store eBay tokens', {
      error: error instanceof Error ? error.message : 'Unknown error',
    });
  }
}

/**
 * Get eBay connection status
 */
export async function getEbayConnectionStatus(config: Config): Promise<{
  connected: boolean;
  environment?: string;
  scopes?: string;
  expiresAt?: Date;
}> {
  try {
    const userId = await getOrCreateDefaultUser();

    const connection = await prisma.ebayConnection.findUnique({
      where: {
        userId_environment: {
          userId,
          environment: config.ebay.env,
        },
      },
    });

    if (!connection) {
      return { connected: false };
    }

    return {
      connected: true,
      environment: connection.environment,
      scopes: connection.scopes,
      expiresAt: connection.expiresAt,
    };
  } catch (error) {
    throw new DatabaseError('Failed to get eBay connection status', {
      error: error instanceof Error ? error.message : 'Unknown error',
    });
  }
}
