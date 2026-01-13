import crypto from 'node:crypto';
import { prisma } from '../../infra/db/prisma.js';

const VERIFICATION_TOKEN_EXPIRY_SECONDS = 3600; // 1 hour

interface DeletionRequest {
  email: string;
  verificationToken: string;
  expiresAt: Date;
}

// In-memory store for deletion verification tokens (in production, use Redis or DB)
const pendingDeletions = new Map<string, DeletionRequest>();

/**
 * Delete user account and all associated data (authenticated flow)
 * This is the in-app deletion triggered by an authenticated user
 */
export async function deleteUserAccount(userId: string): Promise<void> {
  // Verify user exists
  const user = await prisma.user.findUnique({
    where: { id: userId },
  });

  if (!user) {
    throw new Error('User not found');
  }

  // Delete all sessions first (to invalidate tokens)
  await prisma.session.deleteMany({
    where: { userId },
  });

  // Delete user (CASCADE will handle EbayConnection, Listing, etc.)
  await prisma.user.delete({
    where: { id: userId },
  });
}

/**
 * Get deletion status for a user (authenticated flow)
 * Returns whether the account still exists or has been deleted
 */
export async function getDeletionStatus(userId: string): Promise<{
  status: 'ACTIVE' | 'DELETED';
}> {
  const user = await prisma.user.findUnique({
    where: { id: userId },
    select: { id: true },
  });

  return {
    status: user ? 'ACTIVE' : 'DELETED',
  };
}

/**
 * Request account deletion via email (web flow, unauthenticated)
 * Generates a verification token and returns it (caller sends email)
 */
export function requestDeletionByEmail(email: string): {
  verificationToken: string;
  expiresAt: Date;
} {
  // Generate secure random token
  const verificationToken = crypto.randomBytes(32).toString('base64url');
  const expiresAt = new Date(Date.now() + VERIFICATION_TOKEN_EXPIRY_SECONDS * 1000);

  // Store pending deletion request
  pendingDeletions.set(verificationToken, {
    email: email.toLowerCase().trim(),
    verificationToken,
    expiresAt,
  });

  return { verificationToken, expiresAt };
}

/**
 * Confirm account deletion using verification token (web flow)
 * Deletes the user account if the token is valid
 */
export async function confirmDeletionByToken(token: string): Promise<{
  success: boolean;
  email?: string;
  reason?: string;
}> {
  const request = pendingDeletions.get(token);

  if (!request) {
    return { success: false, reason: 'Invalid or expired verification token' };
  }

  // Check if token expired
  if (request.expiresAt < new Date()) {
    pendingDeletions.delete(token);
    return { success: false, reason: 'Verification token expired' };
  }

  // Find user by email
  const user = await prisma.user.findUnique({
    where: { email: request.email },
  });

  if (!user) {
    // Clean up token even if user doesn't exist
    pendingDeletions.delete(token);
    return { success: false, reason: 'Account not found' };
  }

  // Delete all sessions
  await prisma.session.deleteMany({
    where: { userId: user.id },
  });

  // Delete user (CASCADE will handle related data)
  await prisma.user.delete({
    where: { id: user.id },
  });

  // Clean up verification token
  pendingDeletions.delete(token);

  return { success: true, email: request.email };
}

/**
 * Cleanup expired verification tokens (should be called periodically)
 */
export function cleanupExpiredVerificationTokens(): number {
  const now = new Date();
  let cleanedCount = 0;

  for (const [token, request] of pendingDeletions.entries()) {
    if (request.expiresAt < now) {
      pendingDeletions.delete(token);
      cleanedCount++;
    }
  }

  return cleanedCount;
}
