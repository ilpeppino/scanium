// =======================
// LOGS -> Loki
// =======================
loki.write "default" {
  endpoint {
    url = "http://loki:3100/loki/api/v1/push"
  }
}

// =======================
// METRICS -> Mimir (Prometheus remote_write)
// =======================
prometheus.remote_write "mimir" {
  endpoint {
    url = "http://mimir:9009/api/v1/push"
  }
}

// Scrape Alloy's own metrics
prometheus.scrape "alloy_self" {
  targets = [
    { __address__ = "127.0.0.1:12345" }
  ]
  forward_to = [prometheus.remote_write.mimir.receiver]
}

// =======================
// TRACES -> Tempo (OTLP)
// =======================
otelcol.exporter.otlp "tempo" {
  client {
    endpoint = "tempo:4317"
    tls {
      insecure = true
    }
  }
}

// Receive OTLP from your apps
otelcol.receiver.otlp "otlp" {
  grpc {
    endpoint = "0.0.0.0:4317"
  }
  http {
    endpoint = "0.0.0.0:4318"
  }

  output {
    traces = [otelcol.exporter.otlp.tempo.input]
  }
}