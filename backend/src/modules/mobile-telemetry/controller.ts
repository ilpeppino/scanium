import { FastifyReply, FastifyRequest } from 'fastify';
import { OTLPLogExporter } from '@opentelemetry/exporter-logs-otlp-http';
import { LoggerProvider, BatchLogRecordProcessor } from '@opentelemetry/sdk-logs';
import { Resource } from '@opentelemetry/resources';
import { ATTR_SERVICE_NAME } from '@opentelemetry/semantic-conventions';
import { SeverityNumber } from '@opentelemetry/api-logs';

// Create a dedicated exporter for mobile telemetry
// This points to Alloy's mobile receiver which adds source="scanium-mobile"
const OTLP_ENDPOINT = process.env.MOBILE_TELEMETRY_OTLP_ENDPOINT || 'http://scanium-alloy:4318';

const exporter = new OTLPLogExporter({
  url: `${OTLP_ENDPOINT}/v1/logs`,
});

const resource = new Resource({
  [ATTR_SERVICE_NAME]: 'scanium-mobile', // This matches what we want, though Alloy overrides it via external_labels
});

const loggerProvider = new LoggerProvider({ resource });
loggerProvider.addLogRecordProcessor(new BatchLogRecordProcessor(exporter));
const logger = loggerProvider.getLogger('scanium-mobile');

export async function postMobileTelemetryHandler(
  request: FastifyRequest<{
    Body: {
      event_name: string;
      platform: string;
      app_version: string;
      env?: string;
      session_id?: string;
      properties?: Record<string, any>;
      count?: number;
      duration_ms?: number;
    };
  }>,
  reply: FastifyReply
) {
  const { event_name, platform, app_version, env, session_id, properties, count, duration_ms } = request.body;

  // Emit log to OTLP
  logger.emit({
    severityNumber: SeverityNumber.INFO,
    severityText: 'INFO',
    body: `Mobile event: ${event_name}`,
    attributes: {
      event_name,
      platform,
      app_version,
      env: env || 'unknown',
      session_id: session_id || 'unknown',
      ...properties, // Flatten properties for easier querying
      ...(count !== undefined ? { count } : {}),
      ...(duration_ms !== undefined ? { duration_ms } : {}),
      source: 'scanium-mobile', // Explicitly add source attribute as well
    },
  });

  request.log.debug({ msg: 'Mobile telemetry ingested', event_name });

  return reply.status(202).send({ success: true });
}
