# Telemetry Truth Report

Generated: Fri Jan  9 20:47:19 CET 2026

## Summary

✅ Mimir has data (80 series)
❌ Loki has NO labels
❌ Tempo has NO services

## Mimir (Metrics)

- **Series count**: 80
- **Jobs found**:
  - alloy
  - loki
  - mimir
  - scanium-backend
  - tempo

## Loki (Logs)

- **Label count**: 0
- **Has 'source' label**: N/A
- **Labels found**:


## Tempo (Traces)

- **Service count**: 0
- **Services found**:


## Decision Gate



## Raw Data

```json
{
  "timestamp": "2026-01-09T19:47:19Z",
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
      "label_count": 0,
      "labels": [],
      "has_source_label": false
    },
    "tempo": {
      "status": "queried",
      "service_count": 0,
      "services": []
    }
  }
}
```
