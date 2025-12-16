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

  // OAuth specific
  OAUTH_STATE_MISMATCH = 'OAUTH_STATE_MISMATCH',
  OAUTH_TOKEN_EXCHANGE_FAILED = 'OAUTH_TOKEN_EXCHANGE_FAILED',
  OAUTH_INVALID_CODE = 'OAUTH_INVALID_CODE',

  // Database
  DATABASE_ERROR = 'DATABASE_ERROR',

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
    return {
      error: {
        code: this.code,
        message: this.message,
        ...(this.details && { details: this.details }),
      },
    };
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
