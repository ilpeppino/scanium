# Telemetry Setup Guides

This directory contains guides for configuring telemetry collection from various sources.

## Available Guides

### [Mobile OTLP Setup](./mobile-otlp-setup.md)
**Source:** Android mobile app
**Protocol:** OTLP (OpenTelemetry Protocol)
**Destination:** Alloy → Loki/Mimir/Tempo
**Covers:** Mobile telemetry pipeline configuration, log shipping, metrics collection

## Telemetry Architecture

```
Mobile App → OTLP/HTTP (4318) → Alloy → Batch Processing → LGTM Stack
                                                            ├─ Loki (logs)
                                                            ├─ Mimir (metrics)
                                                            └─ Tempo (traces)
```

## Adding New Telemetry Sources

1. Configure receiver in `monitoring/alloy/config.alloy`
2. Add processor (batching, filtering)
3. Configure exporter to appropriate backend
4. Test with sample data
5. Document in this directory
