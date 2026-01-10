# Monitoring How-To Guides

This directory contains operational guides and documentation for the Scanium monitoring stack (Grafana, Mimir, Loki, Tempo, Alloy).

## Directory Structure

### `dashboards/`
Step-by-step guides for troubleshooting and fixing Grafana dashboards:
- **openai-runtime-dashboard.md** - OpenAI/Assistant API metrics dashboard
- **backend-api-performance-dashboard.md** - Backend API performance metrics
- **backend-errors-dashboard.md** - Backend error tracking and alerting
- **errors-and-failures-dashboard.md** - System-wide error monitoring

### `telemetry/`
Guides for setting up telemetry collection:
- **mobile-otlp-setup.md** - Mobile app OTLP telemetry integration with Alloy/Loki

### `testing/`
Scripts and tools for testing monitoring infrastructure:
- **generate-openai-traffic.sh** - Traffic generator for OpenAI metrics testing

## Common Operations

### Verify Metrics in Mimir
```bash
ssh nas
curl -sG 'http://127.0.0.1:9009/prometheus/api/v1/query' \
  --data-urlencode 'query=your_metric_name'
```

### Verify Logs in Loki
```bash
ssh nas
curl -sG 'http://127.0.0.1:3100/loki/api/v1/query' \
  --data-urlencode 'query={source="scanium-backend"}'
```

### Restart Monitoring Stack
```bash
ssh nas
cd /volume1/docker/scanium/repo/monitoring
docker restart scanium-grafana scanium-mimir scanium-loki scanium-tempo scanium-alloy
```

## Contributing

When adding new documentation:
1. Place dashboard fixes in `dashboards/`
2. Place telemetry setup guides in `telemetry/`
3. Place testing scripts in `testing/`
4. Use descriptive filenames with lowercase and hyphens
5. Update this README with links to new guides
