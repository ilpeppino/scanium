import { FastifyError, FastifyReply, FastifyRequest } from 'fastify';
import { AppError } from '../../../shared/errors/index.js';
import { ZodError } from 'zod';

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

  // Handle AppError (custom application errors)
  if (error instanceof AppError) {
    return reply.status(error.httpStatus).send(error.toJSON());
  }

  // Handle Zod validation errors
  if (error instanceof ZodError) {
    return reply.status(400).send({
      error: {
        code: 'VALIDATION_ERROR',
        message: 'Validation failed',
        details: error.errors,
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
    },
  });
}
