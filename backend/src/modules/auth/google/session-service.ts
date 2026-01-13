import crypto from 'node:crypto';
import { prisma } from '../../../infra/db/prisma.js';

const TOKEN_BYTE_LENGTH = 32;

export interface SessionInfo {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
  refreshToken?: string; // Phase C: Optional refresh token
  refreshTokenExpiresIn?: number; // Phase C
  user: {
    id: string;
    email: string | null;
    displayName: string | null;
    pictureUrl: string | null;
  };
}

export async function createSession(
  userId: string,
  accessTokenExpirySeconds: number,
  refreshTokenExpirySeconds?: number
): Promise<SessionInfo> {
  // Generate random access token
  const accessToken = crypto.randomBytes(TOKEN_BYTE_LENGTH).toString('base64url');
  const tokenHash = crypto.createHash('sha256').update(accessToken).digest('hex');
  const expiresAt = new Date(Date.now() + accessTokenExpirySeconds * 1000);

  // Phase C: Generate refresh token if expiry provided
  let refreshToken: string | undefined;
  let refreshTokenHash: string | undefined;
  let refreshTokenExpiresAt: Date | undefined;

  if (refreshTokenExpirySeconds) {
    refreshToken = crypto.randomBytes(TOKEN_BYTE_LENGTH).toString('base64url');
    refreshTokenHash = crypto.createHash('sha256').update(refreshToken).digest('hex');
    refreshTokenExpiresAt = new Date(Date.now() + refreshTokenExpirySeconds * 1000);
  }

  // Store session in DB
  await prisma.session.create({
    data: {
      userId,
      tokenHash,
      expiresAt,
      refreshTokenHash,
      refreshTokenExpiresAt,
    },
  });

  // Fetch user info
  const user = await prisma.user.findUnique({
    where: { id: userId },
    select: {
      id: true,
      email: true,
      displayName: true,
      pictureUrl: true,
    },
  });

  if (!user) {
    throw new Error('User not found after session creation');
  }

  return {
    accessToken,
    tokenType: 'Bearer',
    expiresIn: accessTokenExpirySeconds,
    refreshToken,
    refreshTokenExpiresIn: refreshTokenExpirySeconds,
    user,
  };
}

export async function verifySession(token: string): Promise<string | null> {
  const tokenHash = crypto.createHash('sha256').update(token).digest('hex');

  const session = await prisma.session.findUnique({
    where: { tokenHash },
    select: {
      userId: true,
      expiresAt: true,
    },
  });

  if (!session) {
    return null;
  }

  if (session.expiresAt < new Date()) {
    // Session expired, clean up
    await prisma.session.delete({ where: { tokenHash } });
    return null;
  }

  // Update lastUsedAt
  await prisma.session.update({
    where: { tokenHash },
    data: { lastUsedAt: new Date() },
  });

  return session.userId;
}

/**
 * Phase C: Refresh session using refresh token
 * Returns new access token (and optionally new refresh token)
 */
export async function refreshSession(
  refreshToken: string,
  accessTokenExpirySeconds: number,
  refreshTokenExpirySeconds?: number
): Promise<{ accessToken: string; expiresIn: number; refreshToken?: string; refreshTokenExpiresIn?: number } | null> {
  const refreshTokenHash = crypto.createHash('sha256').update(refreshToken).digest('hex');

  const session = await prisma.session.findUnique({
    where: { refreshTokenHash },
    select: {
      id: true,
      userId: true,
      refreshTokenExpiresAt: true,
      refreshTokenHash: true,
    },
  });

  if (!session || !session.refreshTokenExpiresAt) {
    return null;
  }

  // Check if refresh token is expired
  if (session.refreshTokenExpiresAt < new Date()) {
    // Refresh token expired, delete session
    await prisma.session.delete({ where: { id: session.id } });
    return null;
  }

  // Generate new access token
  const newAccessToken = crypto.randomBytes(TOKEN_BYTE_LENGTH).toString('base64url');
  const newTokenHash = crypto.createHash('sha256').update(newAccessToken).digest('hex');
  const newExpiresAt = new Date(Date.now() + accessTokenExpirySeconds * 1000);

  // Optionally rotate refresh token
  let newRefreshToken: string | undefined;
  let newRefreshTokenHash: string | undefined;
  let newRefreshTokenExpiresAt: Date | undefined;

  if (refreshTokenExpirySeconds) {
    newRefreshToken = crypto.randomBytes(TOKEN_BYTE_LENGTH).toString('base64url');
    newRefreshTokenHash = crypto.createHash('sha256').update(newRefreshToken).digest('hex');
    newRefreshTokenExpiresAt = new Date(Date.now() + refreshTokenExpirySeconds * 1000);
  }

  // Update session with new tokens
  await prisma.session.update({
    where: { id: session.id },
    data: {
      tokenHash: newTokenHash,
      expiresAt: newExpiresAt,
      refreshTokenHash: newRefreshTokenHash || session.refreshTokenHash,
      refreshTokenExpiresAt: newRefreshTokenExpiresAt || session.refreshTokenExpiresAt,
      lastUsedAt: new Date(),
    },
  });

  return {
    accessToken: newAccessToken,
    expiresIn: accessTokenExpirySeconds,
    refreshToken: newRefreshToken,
    refreshTokenExpiresIn: refreshTokenExpirySeconds,
  };
}

/**
 * Phase C: Revoke session (logout)
 * Deletes the session from the database
 */
export async function revokeSession(token: string): Promise<boolean> {
  const tokenHash = crypto.createHash('sha256').update(token).digest('hex');

  const result = await prisma.session.deleteMany({
    where: { tokenHash },
  });

  return result.count > 0;
}

/**
 * Phase C: Cleanup expired sessions
 * Should be called on startup and periodically
 */
export async function cleanupExpiredSessions(): Promise<number> {
  const result = await prisma.session.deleteMany({
    where: {
      OR: [
        { expiresAt: { lt: new Date() } },
        {
          AND: [
            { refreshTokenExpiresAt: { not: null } },
            { refreshTokenExpiresAt: { lt: new Date() } },
          ],
        },
      ],
    },
  });

  return result.count;
}
