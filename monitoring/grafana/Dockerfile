# Grafana Image Fallback
# Bakes in provisioning config and dashboards so they persist across rollbacks
# and survive bind mount failures.
#
# Precedence:
# 1. Bind mounts (if present) override image content
# 2. Image content provides baseline/fallback
#
# This ensures that even if repo diverges or volumes are lost, Grafana starts
# with a known-good dashboard set from the image.

FROM grafana/grafana:10.3.1

# Copy provisioning config (datasources, dashboards, plugins)
COPY provisioning/ /etc/grafana/provisioning/

# Copy dashboards to image (as fallback)
COPY dashboards/ /var/lib/grafana/dashboards/

# Metadata
LABEL maintainer="Scanium Observability"
LABEL description="Grafana with baked-in dashboards and provisioning for rollback safety"
LABEL version="1.0.0"

# Note: Environment variables and volumes are still configured via docker-compose.yml
# This image only provides the baseline config as fallback.
