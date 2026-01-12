// Grafana Alloy Configuration
// Receives OTLP from Scanium mobile app and routes to LGTM stack

// ============================================================================
// OTLP Receivers
// ============================================================================

// OTLP HTTP receiver (primary - used by Android app)
otelcol.receiver.otlp "mobile_http" {
  http {
    endpoint = "0.0.0.0:4318"
  }

  output {
    logs    = [otelcol.processor.batch.mobile.input]
    metrics = [otelcol.processor.batch.mobile.input]
    traces  = [otelcol.processor.batch.mobile.input]
  }
}

// OTLP gRPC receiver (for future use)
otelcol.receiver.otlp "mobile_grpc" {
  grpc {
    endpoint = "0.0.0.0:4317"
  }

  output {
    logs    = [otelcol.processor.batch.mobile.input]
    metrics = [otelcol.processor.batch.mobile.input]
    traces  = [otelcol.processor.batch.mobile.input]
  }
}

// OTLP HTTP receiver (backend API)
otelcol.receiver.otlp "backend_http" {
  http {
    endpoint = "0.0.0.0:4319"
  }

  output {
    logs    = [otelcol.processor.batch.backend.input]
    metrics = [otelcol.processor.batch.backend.input]
    traces  = [otelcol.processor.batch.backend.input]
  }
}

// ============================================================================
// Processors
// ============================================================================

// Batch processor to reduce network overhead
otelcol.processor.batch "mobile" {
  // Batch size and timeout
  send_batch_size         = 100
  send_batch_max_size     = 200
  timeout                 = "5s"

  output {
    logs    = [otelcol.processor.attributes.mobile.input]
    metrics = [otelcol.exporter.prometheus.mobile.input]
    traces  = [otelcol.exporter.otlp.tempo.input]
  }
}

// Extract key attributes for Loki labels (low-cardinality only)
otelcol.processor.attributes "mobile" {
  // Promote specific attributes to resource attributes
  // This allows otelcol.exporter.loki to map them to Loki labels
  action {
    key = "loki.attribute.labels"
    action = "insert"
    value = "event_name, platform, app_version, build_type, env"
  }

  output {
    logs = [otelcol.exporter.loki.mobile.input]
  }
}

// Extract mobile telemetry labels from backend OTLP logs
otelcol.processor.attributes "backend_logs" {
  // Promote mobile telemetry attributes to resource attributes for Loki labeling
  // This allows otelcol.exporter.loki to map them to Loki labels
  action {
    key = "loki.attribute.labels"
    action = "insert"
    value = "source, event_name, platform, app_version, build_type, env"
  }

  output {
    logs = [otelcol.exporter.loki.backend.input]
  }
}

otelcol.processor.batch "backend" {
  send_batch_size         = 100
  send_batch_max_size     = 200
  timeout                 = "5s"

  output {
    logs    = [otelcol.processor.attributes.backend_logs.input]
    metrics = [otelcol.exporter.prometheus.backend.input]
    traces  = [otelcol.exporter.otlp.tempo.input]
  }
}

// ============================================================================
// Exporters
// ============================================================================

// Loki exporter for mobile OTLP logs
// Automatically maps OTLP log attributes specified in loki.attribute.labels to Loki labels
otelcol.exporter.loki "mobile" {
  forward_to = [loki.write.mobile.receiver]
}

otelcol.exporter.loki "backend" {
  forward_to = [loki.write.backend_logs.receiver]
}

// Loki write component for mobile telemetry
loki.write "mobile" {
  endpoint {
    url = "http://loki:3100/loki/api/v1/push"
  }

  external_labels = {
    source = "scanium-mobile",
  }
}


// Docker container discovery for logs
discovery.docker "backend" {
  host = "unix:///var/run/docker.sock"
}

// Docker logs -> Loki (backend)
// This captures logs from scanium-backend container and parses mobile telemetry events
loki.source.docker "backend" {
  host = "unix:///var/run/docker.sock"
  targets = discovery.docker.backend.targets

  // Initial labels (will be overridden by relabeling if mobile telemetry)
  labels = {
    source = "scanium-backend",
    env = "dev",
  }

  // Forward to processing pipeline that extracts mobile telemetry labels
  forward_to = [loki.process.backend_logs.receiver]
}

// Process backend container logs to extract mobile telemetry events
loki.process "backend_logs" {
  forward_to = [loki.write.backend_logs.receiver]

  // Try to parse as JSON
  stage.json {
    expressions = {
      source       = "source",
      event_name   = "event_name",
      platform     = "platform",
      app_version  = "app_version",
      build_type   = "build_type",
      session_id   = "session_id",
      timestamp_ms = "timestamp_ms",
    }
  }

  // Conditionally relabel if source=scanium-mobile
  // This extracts low-cardinality labels for mobile telemetry
  stage.labels {
    values = {
      source       = "source",
      event_name   = "event_name",
      platform     = "platform",
      app_version  = "app_version",
      build_type   = "build_type",
    }
  }
}

// Write processed logs to Loki
loki.write "backend_logs" {
  endpoint {
    url = "http://loki:3100/loki/api/v1/push"
  }

  external_labels = {
    env = "dev",
  }
}

// Prometheus remote write exporter for metrics (to Mimir)
otelcol.exporter.prometheus "mobile" {
  forward_to = [prometheus.remote_write.mobile.receiver]
}

otelcol.exporter.prometheus "backend" {
  forward_to = [prometheus.remote_write.backend.receiver]
}

prometheus.remote_write "mobile" {
  endpoint {
    url = "http://mimir:9009/api/v1/push"
  }

  external_labels = {
    source = "scanium-mobile",
    env    = "dev",
  }
}

// OTLP exporter for traces (to Tempo)
otelcol.exporter.otlp "tempo" {
  client {
    endpoint = "tempo:4317"

    // Disable TLS for internal communication
    tls {
      insecure             = true
      insecure_skip_verify = true
    }
  }
}

// ============================================================================
// Pipeline Self-Observability (Prometheus Scraping)
// ============================================================================
// Scrape metrics from Alloy itself and LGTM backend services for self-monitoring.
// Metrics are forwarded to Mimir for dashboarding and alerting.
//
// NOTE: 60s scrape interval is optimized for NAS deployment (Synology DS418play).
// For development workstations, 15s provides higher resolution if needed.

// Scrape Alloy's own metrics (exposed on the HTTP server port)
prometheus.scrape "alloy" {
  targets = [
    {
      __address__ = "localhost:12345",
      job         = "alloy",
      instance    = "scanium-alloy",
    },
  ]
  forward_to      = [prometheus.remote_write.pipeline.receiver]
  scrape_interval = "60s"
  scrape_timeout  = "10s"
}

// Scrape Loki metrics
prometheus.scrape "loki" {
  targets = [
    {
      __address__ = "loki:3100",
      job         = "loki",
      instance    = "scanium-loki",
    },
  ]
  forward_to      = [prometheus.remote_write.pipeline.receiver]
  scrape_interval = "60s"
  scrape_timeout  = "10s"
}

// Scrape Tempo metrics
prometheus.scrape "tempo" {
  targets = [
    {
      __address__ = "tempo:3200",
      job         = "tempo",
      instance    = "scanium-tempo",
    },
  ]
  forward_to      = [prometheus.remote_write.pipeline.receiver]
  scrape_interval = "60s"
  scrape_timeout  = "10s"
}

// Scrape Mimir metrics
prometheus.scrape "mimir" {
  targets = [
    {
      __address__ = "mimir:9009",
      job         = "mimir",
      instance    = "scanium-mimir",
    },
  ]
  forward_to      = [prometheus.remote_write.pipeline.receiver]
  scrape_interval = "60s"
  scrape_timeout  = "10s"
}


// Scrape Backend metrics
prometheus.scrape "backend" {
  targets = [
    {
      __address__      = "scanium-backend:8080",
      __metrics_path__ = "/metrics",
      job              = "scanium-backend",
      instance         = "scanium-backend",
    },
  ]
  forward_to      = [prometheus.remote_write.backend.receiver]
  scrape_interval = "60s"  // Reduced frequency for NAS resource constraints
  scrape_timeout  = "10s"
}


// Remote write for backend metrics
prometheus.remote_write "backend" {
  endpoint {
    url = "http://mimir:9009/api/v1/push"
  }

  external_labels = {
    source                  = "scanium-backend",
    env                     = "dev",
    service_name            = "scanium-backend",
    deployment_environment  = "dev",
  }
}

// Remote write for pipeline self-observability metrics
// Kept separate from app metrics with source="pipeline"
prometheus.remote_write "pipeline" {
  endpoint {
    url = "http://mimir:9009/api/v1/push"
  }

  external_labels = {
    source = "pipeline",
    env    = "dev",
  }
}

// ============================================================================
// Debugging (optional - uncomment to log telemetry to console)
// ============================================================================

// Uncomment to see all received telemetry in Alloy logs
// otelcol.exporter.logging "debug" {
//   verbosity           = "detailed"
//   sampling_initial    = 10
//   sampling_thereafter = 10
// }

// To enable debugging, also add this to processors output:
// logs    = [otelcol.exporter.logging.debug.input, otelcol.processor.attributes.mobile.input]
// metrics = [otelcol.exporter.logging.debug.input, otelcol.exporter.prometheus.mobile.input]
// traces  = [otelcol.exporter.logging.debug.input, otelcol.exporter.otlp.tempo.input]
