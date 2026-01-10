***REMOVED*** Telemetry Truth Report

Generated: Fri Jan  9 21:37:04 CET 2026

***REMOVED******REMOVED*** Summary

✅ Mimir has data (80 series)
✅ Loki has labels (5 labels)
✅ Tempo has services (1 services)

***REMOVED******REMOVED*** Mimir (Metrics)

- **Series count**: 80
- **Jobs found**:
  - alloy
  - loki
  - mimir
  - scanium-backend
  - tempo

***REMOVED******REMOVED*** Loki (Logs)

- **Label count**: 5
- **Has 'source' label**: true
- **Labels found**:
  - env
  - exporter
  - job
  - level
  - source

***REMOVED******REMOVED*** Tempo (Traces)

- **Service count**: 1
- **Services found**:
  - scanium-backend

***REMOVED******REMOVED*** Decision Gate



***REMOVED******REMOVED*** Raw Data

```json
{
  "timestamp": "2026-01-09T20:37:04Z",
  "datasources": {
    "mimir": {
      "status": "queried",
      "series_count": 80,
      "jobs": [
        "alloy",
        "loki",
        "mimir",
        "scanium-backend",
        "tempo"
      ]
    },
    "loki": {
      "status": "queried",
      "label_count": 5,
      "labels": [
        "env",
        "exporter",
        "job",
        "level",
        "source"
      ],
      "has_source_label": true
    },
    "tempo": {
      "status": "queried",
      "service_count": 1,
      "services": [
        "scanium-backend"
      ]
    }
  }
}
```
