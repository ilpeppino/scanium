import { NodeSDK } from '@opentelemetry/sdk-node';
import { OTLPTraceExporter } from '@opentelemetry/exporter-trace-otlp-http';
import { OTLPMetricExporter } from '@opentelemetry/exporter-metrics-otlp-http';
import { PeriodicExportingMetricReader } from '@opentelemetry/sdk-metrics';
import { Resource } from '@opentelemetry/resources';
import { ATTR_SERVICE_NAME } from '@opentelemetry/semantic-conventions';
import { HttpInstrumentation } from '@opentelemetry/instrumentation-http';
import { FastifyInstrumentation } from '@opentelemetry/instrumentation-fastify';

/**
 * OpenTelemetry initialization for Scanium Backend
 *
 * Configures traces and metrics export to Alloy/LGTM stack
 * Environment variables:
 * - OTEL_EXPORTER_OTLP_ENDPOINT: OTLP endpoint (default: http://localhost:4318)
 * - OTEL_SERVICE_NAME: Service name (default: scanium-backend)
 * - NODE_ENV: Environment (dev/stage/prod)
 * - OTEL_ENABLED: Enable/disable telemetry (default: true)
 */

let sdk: NodeSDK | null = null;

export interface TelemetryConfig {
  serviceName: string;
  environment: string;
  otlpEndpoint: string;
  enabled: boolean;
}

export function initTelemetry(config: TelemetryConfig): void {
  if (!config.enabled) {
    console.log('📊 OpenTelemetry disabled');
    return;
  }

  const resource = new Resource({
    [ATTR_SERVICE_NAME]: config.serviceName,
    'deployment.environment': config.environment,
  });

  // Trace exporter
  const traceExporter = new OTLPTraceExporter({
    url: `${config.otlpEndpoint}/v1/traces`,
  });

  // Metrics exporter with 30s interval
  const metricExporter = new OTLPMetricExporter({
    url: `${config.otlpEndpoint}/v1/metrics`,
  });

  const metricReader = new PeriodicExportingMetricReader({
    exporter: metricExporter,
    exportIntervalMillis: 30000, // 30 seconds
  });

  // Initialize SDK with auto-instrumentation
  sdk = new NodeSDK({
    resource,
    traceExporter,
    metricReader: metricReader as any, // Type workaround for PeriodicExportingMetricReader
    instrumentations: [
      new HttpInstrumentation({
        ignoreIncomingRequestHook: (req) => {
          // Ignore health check requests
          const url = req.url || '';
          return url.includes('/health') || url.includes('/metrics');
        },
      }),
      new FastifyInstrumentation(),
    ],
  });

  sdk.start();
  console.log(`📊 OpenTelemetry initialized: ${config.serviceName} (${config.environment})`);
  console.log(`   Exporting to: ${config.otlpEndpoint}`);
}

export function shutdownTelemetry(): Promise<void> {
  if (!sdk) {
    return Promise.resolve();
  }

  return sdk.shutdown().then(
    () => console.log('📊 OpenTelemetry shut down'),
    (error) => console.error('📊 Error shutting down OpenTelemetry:', error)
  );
}
