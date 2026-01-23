# Runtime Inventory - NAS Docker State

**Timestamp:** 2026-01-12 (captured after repo alignment to d5bf933)
**Purpose:** Source of truth for monitoring stack deployment on NAS

## Container Status

```
NAMES                   IMAGE                           STATUS
scanium-backend         compose_backend                 Up 8 hours (healthy)
scanium-cloudflared     cloudflare/cloudflared:latest   Up 21 hours
scanium-tempo           grafana/tempo:2.7.0             Up 32 hours (healthy)
scanium-postgres        postgres:16-alpine              Up 33 hours (healthy)
scanium-grafana         grafana/grafana:10.3.1          Up 31 hours (healthy)
scanium-alloy           grafana/alloy:v1.0.0            Up 33 hours (healthy)
scanium-loki            grafana/loki:2.9.3              Up 44 hours (healthy)
scanium-mimir           grafana/mimir:2.11.0            Up 44 hours (healthy)
scanium-smoke-monitor   alpine:3.19                     Up 2 days
```

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

### Backend (scanium-backend)

- **Network:** compose_scanium_net (172.21.0.0/16)
- **IP:** 172.21.0.3
- **Gateway:** 172.21.0.1
- **Aliases:** backend, 6fa9132f0daf
- **Working Dir:** /volume1/docker/scanium/repo/deploy/nas/compose
- **Compose Project:** compose
- **Service Name:** backend

### Alloy (scanium-alloy)

- **Networks:**
    1. backend_scanium-network (172.23.0.0/16) - IP: 172.23.0.2, Gateway: 172.23.0.1
    2. scanium-observability (172.25.0.0/16) - IP: 172.25.0.5, Gateway: 172.25.0.1
- **Aliases:** alloy, 839dbd6c42ba
- **Working Dir:** /volume1/docker/scanium/repo/monitoring
- **Compose Project:** monitoring
- **Service Name:** alloy
- **Note:** Bridge container between backend and observability networks

### Grafana (scanium-grafana)

- **Networks:**
    1. backend_scanium-network (172.23.0.0/16) - IP: 172.23.0.6, Gateway: 172.23.0.1
    2. scanium-observability (172.25.0.0/16) - IP: 172.25.0.6, Gateway: 172.25.0.1
- **Aliases:** grafana, 1609ff1f2166
- **Working Dir:** /volume1/docker/scanium/repo/monitoring
- **Compose Project:** monitoring
- **Service Name:** grafana
- **Note:** Bridge container between backend and observability networks

### Loki (scanium-loki)

- **Network:** scanium-observability (172.25.0.0/16)
- **IP:** 172.25.0.3
- **Gateway:** 172.25.0.1
- **Aliases:** loki, 0c69815ed056

### Mimir (scanium-mimir)

- **Network:** scanium-observability (172.25.0.0/16)
- **IP:** 172.25.0.4
- **Gateway:** 172.25.0.1
- **Aliases:** mimir, 660c90df46a0

### Tempo (scanium-tempo)

- **Network:** scanium-observability (172.25.0.0/16)
- **IP:** 172.25.0.2
- **Gateway:** 172.25.0.1
- **Aliases:** tempo, e0b48b0270eb

## Persistence Mounts (Verified)

### Grafana

```
bind /volume1/docker/scanium/repo/monitoring/data/grafana -> /var/lib/grafana
bind /volume1/docker/scanium/repo/monitoring/grafana/provisioning -> /etc/grafana/provisioning
bind /volume1/docker/scanium/repo/monitoring/grafana/dashboards -> /var/lib/grafana/dashboards
```

### Loki

```
bind /volume1/docker/scanium/repo/monitoring/data/loki -> /loki
bind /volume1/docker/scanium/repo/monitoring/loki/loki.yaml -> /etc/loki/config.yaml
```

### Mimir

```
bind /volume1/docker/scanium/repo/monitoring/mimir/mimir.yaml -> /etc/mimir/config.yaml
bind /volume1/docker/scanium/repo/monitoring/data/mimir -> /data
```

### Tempo

```
bind /volume1/docker/scanium/repo/monitoring/tempo/overrides.yaml -> /etc/tempo/overrides.yaml
bind /volume1/docker/scanium/repo/monitoring/data/tempo -> /var/tempo
bind /volume1/docker/scanium/repo/monitoring/tempo/tempo.yaml -> /etc/tempo/config.yaml
```

### Alloy

```
bind /volume1/docker/scanium/repo/monitoring/alloy/alloy.hcl -> /etc/alloy/config.alloy
bind /var/run/docker.sock -> /var/run/docker.sock
bind /volume1/docker/scanium/repo/monitoring/data/alloy -> /var/lib/alloy/data
```

## Data Directories Status

```
drwxr-xr-x  1 ilpeppino users 384 Jan 11 11:36 alloy
drwxrwxrwx  1 ilpeppino users  82 Jan 12 20:30 grafana
drwxrwxrwx  1 ilpeppino users  78 Jan  9 16:52 loki
drwxrwxrwx  1 ilpeppino users  56 Jan  9 19:48 mimir
drwxrwxrwx  1 ilpeppino users  36 Jan  9 16:52 tempo
```

✅ All persistence directories exist on NAS

## Network Drift Analysis

- **Backend is isolated:** runs only on compose_scanium_net, not on scanium-observability
- **Alloy bridges:** backend_scanium-network ↔ scanium-observability
- **Grafana bridges:** backend_scanium-network ↔ scanium-observability
- **LGTM stack (Loki, Mimir, Tempo):** isolated to scanium-observability

## Critical Paths for Telemetry

1. **Backend → Alloy:** Backend must reach Alloy on backend_scanium-network (172.23.0.2)
2. **Alloy → Loki/Mimir/Tempo:** Alloy writes to scanium-observability network
3. **Grafana → Loki/Mimir/Tempo:** Grafana queries from scanium-observability network
4. **External → Grafana:** Via cloudflared tunnel (grafana.gtemp1.com)
