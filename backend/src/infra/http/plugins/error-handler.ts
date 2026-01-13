import { FastifyError, FastifyReply, FastifyRequest } from 'fastify';
import { AppError } from '../../../shared/errors/index.js';
import { ZodError } from 'zod';
import { errorReporter } from '../../observability/error-reporter.js';

/**
 * Global error handler plugin
 * Ensures all errors are returned as consistent JSON
 */
export async function errorHandlerPlugin(
  error: FastifyError | Error,
  request: FastifyRequest,
  reply: FastifyReply
): Promise<void> {
  // Log error (Fastify logger)
  request.log.error(
    {
      err: error,
      url: request.url,
      method: request.method,
    },
    'Request error'
  );

  errorReporter.report({
    message: error.message,
    correlationId: request.correlationId,
    url: request.url,
    method: request.method,
    statusCode: 'statusCode' in error ? error.statusCode ?? 500 : 500,
    stack: error.stack,
  });

  // Handle AppError (custom application errors)
  if (error instanceof AppError) {
    const errorResponse = error.toJSON();
    // Ensure correlationId is included in all error responses
    if (errorResponse.error && !errorResponse.error.correlationId) {
      errorResponse.error.correlationId = request.correlationId;
    }
    return reply.status(error.httpStatus).send(errorResponse);
  }

  // Handle Zod validation errors
  if (error instanceof ZodError) {
    return reply.status(400).send({
      error: {
        code: 'VALIDATION_ERROR',
        message: 'Validation failed',
        details: error.errors,
        correlationId: request.correlationId,
      },
    });
  }

  // Handle Fastify validation errors
  if ('validation' in error && error.validation) {
    return reply.status(400).send({
      error: {
        code: 'VALIDATION_ERROR',
        message: error.message,
        details: error.validation,
        correlationId: request.correlationId,
      },
    });
  }

  // Default to 500 Internal Server Error
  const statusCode = 'statusCode' in error ? error.statusCode ?? 500 : 500;

  return reply.status(statusCode).send({
    error: {
      code: 'INTERNAL_ERROR',
      message:
        process.env.NODE_ENV === 'development'
          ? error.message
          : 'An internal error occurred',
      correlationId: request.correlationId,
    },
  });
}
