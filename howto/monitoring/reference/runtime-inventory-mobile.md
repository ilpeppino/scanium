# Runtime Inventory - Mobile Telemetry Implementation
**Date:** 2026-01-12
**Commit:** 929c56b6063fe731e3e94dd94c8f7455290db752
**Purpose:** Document NAS container/network state before implementing mobile telemetry

## Container Status

```
NAMES                   IMAGE                           STATUS
scanium-grafana         scanium-grafana:latest          Up 56 minutes (healthy)
scanium-alloy           grafana/alloy:v1.0.0            Up 36 minutes (unhealthy)
scanium-tempo           grafana/tempo:2.7.0             Up 56 minutes (healthy)
scanium-mimir           grafana/mimir:2.11.0            Up 56 minutes (healthy)
scanium-loki            grafana/loki:2.9.3              Up 56 minutes (healthy)
scanium-backend         compose_backend                 Up About an hour (healthy)
scanium-postgres        postgres:16-alpine              Up About an hour (healthy)
scanium-cloudflared     cloudflare/cloudflared:latest   Up 22 hours
scanium-smoke-monitor   alpine:3.19                     Up 2 days
```

**Note:** `scanium-alloy` is unhealthy - requires investigation.

## Docker Networks

```
NETWORK ID     NAME                            DRIVER    SCOPE
0ab89bf15d6a   backend_scanium-network         bridge    local
68710cda63a0   barcode-scanner-clean_default   bridge    local
e83a6efb1739   bridge                          bridge    local
a5b5f3eb6677   cloudflared_default             bridge    local
609fe8765b8f   compose_default                 bridge    local
a743d3c04332   compose_scanium_net             bridge    local
602b2c39e831   host                            host      local
0361938ac29d   none                            null      local
bf9936db109f   pihole_net                      macvlan   local
54fa3351692c   scanium-observability           bridge    local
31e5fc07f258   scanium_net                     bridge    local
```

## Network Topology

### scanium-backend
- **Networks:** backend_scanium-network, compose_scanium_net
- **backend_scanium-network:** 172.23.0.4/16 (Gateway: 172.23.0.1)
- **compose_scanium_net:** 172.21.0.4/16 (Gateway: 172.21.0.1)

### scanium-alloy
- **Networks:** backend_scanium-network, scanium-observability
- **backend_scanium-network:** 172.23.0.2/16 (Gateway: 172.23.0.1)
- **scanium-observability:** 172.25.0.5/16 (Gateway: 172.25.0.1)
- **Status:** Bridge container between backend and monitoring stacks

### scanium-loki
- **Networks:** scanium-observability
- **scanium-observability:** 172.25.0.3/16 (Gateway: 172.25.0.1)

### scanium-mimir
- **Networks:** scanium-observability
- **scanium-observability:** 172.25.0.4/16 (Gateway: 172.25.0.1)

### scanium-grafana
- **Networks:** backend_scanium-network, scanium-observability
- **backend_scanium-network:** 172.23.0.5/16 (Gateway: 172.23.0.1)
- **scanium-observability:** 172.25.0.6/16 (Gateway: 172.25.0.1)
- **Status:** Bridge container between backend and monitoring stacks

## Key Observations

1. **Network Segmentation:**
   - Backend stack: `backend_scanium-network` (172.23.0.0/16)
   - Monitoring stack: `scanium-observability` (172.25.0.0/16)
   - Bridge containers: alloy (172.23.0.2, 172.25.0.5), grafana (172.23.0.5, 172.25.0.6)

2. **Mobile Telemetry Path:**
   - Backend exposes /v1/mobile/events endpoint
   - Backend emits structured logs via Pino OTLP transport
   - Alloy scrapes backend (can reach 172.23.0.4)
   - Alloy forwards to Loki (172.25.0.3) and Mimir (172.25.0.4)
   - Grafana queries Loki/Mimir for dashboard

3. **Alloy Health Issue:**
   - Alloy is unhealthy - may affect log/metric collection
   - Should investigate /alloy/api/v0/component/* endpoints

4. **Docker Binary Location:**
   - NAS: `/usr/local/bin/docker` (not in default PATH for SSH)
