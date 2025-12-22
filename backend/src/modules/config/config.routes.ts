import { FastifyInstance } from 'fastify';
import { Config } from '../../config/index.js';
import { ConfigService } from './config.service.js';
import { ApiKeyManager } from '../classifier/api-key-manager.js';

export async function configRoutes(
  app: FastifyInstance,
  options: { config: Config }
) {
  const { config } = options;
  const service = new ConfigService(config);
  // Reusing classifier keys for now as requested ("same scheme")
  const apiKeyManager = new ApiKeyManager(config.classifier.apiKeys);

  app.get('/config', async (request, reply) => {
      const header = request.headers['x-api-key'];
      const apiKey = Array.isArray(header) ? header[0] : header;
      
      // Validate API Key
      if (!apiKey || !apiKeyManager.validateKey(apiKey)) {
          return reply.status(401).send({
              error: {
                  code: 'UNAUTHORIZED',
                  message: 'Missing or invalid API key'
              }
          });
      }

      const headers = request.headers;
      const deviceHash = headers['x-scanium-device-hash'] as string | undefined;
      const region = headers['x-scanium-region'] as string | undefined;

      const remoteConfig = await service.getConfig(deviceHash, region);
      
      // Auditing
      request.log.info({
          msg: "Served remote config",
          configVersion: remoteConfig.version,
          deviceHashPrefix: deviceHash ? deviceHash.substring(0, 8) : 'unknown'
      });

      return reply.send(remoteConfig);
    }
  );
}
