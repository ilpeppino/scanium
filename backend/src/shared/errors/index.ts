/**
 * Application error codes
 */
export enum ErrorCode {
  // Generic
  INTERNAL_ERROR = 'INTERNAL_ERROR',
  VALIDATION_ERROR = 'VALIDATION_ERROR',
  NOT_FOUND = 'NOT_FOUND',
  UNAUTHORIZED = 'UNAUTHORIZED',
  FORBIDDEN = 'FORBIDDEN',

  // Auth specific (Phase B)
  AUTH_REQUIRED = 'AUTH_REQUIRED',
  AUTH_INVALID = 'AUTH_INVALID',
  RATE_LIMITED = 'RATE_LIMITED',

  // OAuth specific
  OAUTH_STATE_MISMATCH = 'OAUTH_STATE_MISMATCH',
  OAUTH_TOKEN_EXCHANGE_FAILED = 'OAUTH_TOKEN_EXCHANGE_FAILED',
  OAUTH_INVALID_CODE = 'OAUTH_INVALID_CODE',

  // Database
  DATABASE_ERROR = 'DATABASE_ERROR',

  // Sync specific (Phase E)
  CONFLICT = 'CONFLICT',

  // eBay specific
  EBAY_API_ERROR = 'EBAY_API_ERROR',
  EBAY_CONNECTION_NOT_FOUND = 'EBAY_CONNECTION_NOT_FOUND',
}

/**
 * Base application error
 * All custom errors should extend this
 */
export class AppError extends Error {
  constructor(
    public readonly code: ErrorCode,
    message: string,
    public readonly httpStatus: number = 500,
    public readonly details?: unknown
  ) {
    super(message);
    this.name = 'AppError';
    Error.captureStackTrace(this, this.constructor);
  }

  toJSON() {
    const payload: { code: ErrorCode; message: string; details?: unknown } = {
      code: this.code,
      message: this.message,
    };

    if (this.details !== undefined) {
      payload.details = this.details;
    }

    return { error: payload };
  }
}

/**
 * Validation error (400)
 */
export class ValidationError extends AppError {
  constructor(message: string, details?: unknown) {
    super(ErrorCode.VALIDATION_ERROR, message, 400, details);
    this.name = 'ValidationError';
  }
}

/**
 * Not found error (404)
 */
export class NotFoundError extends AppError {
  constructor(resource: string) {
    super(ErrorCode.NOT_FOUND, `${resource} not found`, 404);
    this.name = 'NotFoundError';
  }
}

/**
 * Unauthorized error (401)
 */
export class UnauthorizedError extends AppError {
  constructor(message: string = 'Unauthorized') {
    super(ErrorCode.UNAUTHORIZED, message, 401);
    this.name = 'UnauthorizedError';
  }
}

/**
 * Forbidden error (403)
 */
export class ForbiddenError extends AppError {
  constructor(message: string = 'Forbidden') {
    super(ErrorCode.FORBIDDEN, message, 403);
    this.name = 'ForbiddenError';
  }
}

/**
 * Auth required error (401) - Phase B
 * Used when authentication is required but not provided
 */
export class AuthRequiredError extends AppError {
  constructor(message: string = 'Sign in is required to use this feature.') {
    super(ErrorCode.AUTH_REQUIRED, message, 401);
    this.name = 'AuthRequiredError';
  }
}

/**
 * Auth invalid error (401) - Phase B
 * Used when authentication is provided but invalid/expired
 */
export class AuthInvalidError extends AppError {
  constructor(message: string = 'Your session is invalid or expired. Please sign in again.') {
    super(ErrorCode.AUTH_INVALID, message, 401);
    this.name = 'AuthInvalidError';
  }
}

/**
 * Rate limit error (429) - Phase B
 * Used when rate limit is exceeded
 */
export class RateLimitError extends AppError {
  constructor(
    message: string = 'Rate limit reached. Please try again later.',
    public readonly resetAt?: string
  ) {
    super(ErrorCode.RATE_LIMITED, message, 429, { resetAt });
    this.name = 'RateLimitError';
  }

  override toJSON() {
    return {
      error: {
        code: this.code,
        message: this.message,
        resetAt: this.resetAt,
      },
    };
  }
}

/**
 * OAuth state mismatch error (400)
 */
export class OAuthStateMismatchError extends AppError {
  constructor() {
    super(
      ErrorCode.OAUTH_STATE_MISMATCH,
      'OAuth state mismatch - possible CSRF attack',
      400
    );
    this.name = 'OAuthStateMismatchError';
  }
}

/**
 * OAuth token exchange failed error (502)
 */
export class OAuthTokenExchangeError extends AppError {
  constructor(details?: unknown) {
    super(
      ErrorCode.OAUTH_TOKEN_EXCHANGE_FAILED,
      'Failed to exchange authorization code for tokens',
      502,
      details
    );
    this.name = 'OAuthTokenExchangeError';
  }
}

/**
 * Database error (500)
 */
export class DatabaseError extends AppError {
  constructor(message: string, details?: unknown) {
    super(ErrorCode.DATABASE_ERROR, message, 500, details);
    this.name = 'DatabaseError';
  }
}

/**
 * Conflict error (409) - Phase E
 * Used for optimistic locking conflicts during sync
 */
export class ConflictError extends AppError {
  constructor(message: string = 'Resource conflict detected', details?: unknown) {
    super(ErrorCode.CONFLICT, message, 409, details);
    this.name = 'ConflictError';
  }
}
