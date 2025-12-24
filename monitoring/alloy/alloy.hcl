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
    logs    = [otelcol.exporter.loki.mobile.input]
    metrics = [otelcol.exporter.prometheus.mobile.input]
    traces  = [otelcol.exporter.otlp.tempo.input]
  }
}

// ============================================================================
// Exporters
// ============================================================================

// Loki exporter for logs
otelcol.exporter.loki "mobile" {
  forward_to = [loki.write.mobile.receiver]
}

// Loki write component
loki.write "mobile" {
  endpoint {
    url = "http://loki:3100/loki/api/v1/push"

    // Retry configuration
    retry_on_http_429 = true
    max_backoff       = "5s"
    min_backoff       = "100ms"
  }

  external_labels = {
    source = "scanium-mobile",
    env    = "dev",
  }
}

// Prometheus remote write exporter for metrics (to Mimir)
otelcol.exporter.prometheus "mobile" {
  forward_to = [prometheus.remote_write.mobile.receiver]
}

prometheus.remote_write "mobile" {
  endpoint {
    url = "http://mimir:9009/api/v1/push"

    // Retry configuration
    retry_on_http_429 = true
    max_backoff       = "5s"
    min_backoff       = "100ms"

    // Queue configuration
    capacity              = 10000
    max_shards            = 5
    min_shards            = 1
    max_samples_per_send  = 2000
    batch_send_deadline   = "5s"
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

    // Retry configuration
    retry_on_failure {
      enabled         = true
      initial_interval = "500ms"
      max_interval    = "5s"
      max_elapsed_time = "30s"
    }

    // Timeout
    timeout = "10s"
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
// logs    = [otelcol.exporter.logging.debug.input, otelcol.exporter.loki.mobile.input]
// metrics = [otelcol.exporter.logging.debug.input, otelcol.exporter.prometheus.mobile.input]
// traces  = [otelcol.exporter.logging.debug.input, otelcol.exporter.otlp.tempo.input]
