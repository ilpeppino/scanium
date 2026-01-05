import { buildApp } from './app.js';
import { loadConfig } from './config/index.js';
import { disconnectPrisma } from './infra/db/prisma.js';
import { initTelemetry, shutdownTelemetry } from './infra/telemetry/index.js';

/**
 * Main application entry point
 */
async function main() {
  let app;

  try {
    // Load and validate configuration
    console.log('📝 Loading configuration...');
    const config = loadConfig();
    console.log(`✅ Configuration loaded (env: ${config.nodeEnv})`);

    // Initialize OpenTelemetry
    initTelemetry({
      serviceName: process.env.OTEL_SERVICE_NAME || 'scanium-backend',
      environment: config.nodeEnv,
      otlpEndpoint: process.env.OTEL_EXPORTER_OTLP_ENDPOINT || 'http://localhost:4318',
      enabled: process.env.OTEL_ENABLED !== 'false',
    });

    // Build Fastify app
    console.log('🚀 Building application...');
    app = await buildApp(config);
    console.log('✅ Application built');

    // Start server
    const address = await app.listen({
      port: config.port,
      host: '0.0.0.0', // Bind to all interfaces for Docker
    });

    console.log(`✅ Server listening on ${address}`);
    console.log(`🌍 Public URL: ${config.publicBaseUrl}`);
    console.log(`🏪 eBay environment: ${config.ebay.env}`);
  } catch (error) {
    console.error('❌ Failed to start server:', error);
    process.exit(1);
  }

  // Graceful shutdown handlers
  const shutdown = async (signal: string) => {
    console.log(`\n🛑 ${signal} received, shutting down gracefully...`);

    try {
      if (app) {
        await app.close();
        console.log('✅ HTTP server closed');
      }

      await disconnectPrisma();
      console.log('✅ Database disconnected');

      await shutdownTelemetry();
      console.log('✅ Telemetry shut down');

      console.log('✅ Shutdown complete');
      process.exit(0);
    } catch (error) {
      console.error('❌ Error during shutdown:', error);
      process.exit(1);
    }
  };

  process.on('SIGTERM', () => shutdown('SIGTERM'));
  process.on('SIGINT', () => shutdown('SIGINT'));
}

// Start application
main();
