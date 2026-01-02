import { FastifyInstance } from 'fastify';
import { z } from 'zod';

const verifySchema = z.object({
  purchaseToken: z.string(),
  productId: z.string(),
  packageName: z.string()
});

export async function billingRoutes(app: FastifyInstance) {
  app.post('/billing/verify/google', async (request, _reply) => {
    // In the future, this will verify against Google Play Developer API
    // For now, it logs and returns valid
    
    const body = verifySchema.parse(request.body);
    
    request.log.info({ ...body }, 'Received billing verification request');

    // MOCK RESPONSE
    return {
      status: 'PRO',
      source: 'SERVER_VERIFIED',
      expiresAt: Date.now() + 30 * 24 * 60 * 60 * 1000, // +30 days
      verified: true
    };
  });
}
