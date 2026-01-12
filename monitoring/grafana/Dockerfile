***REMOVED*** Grafana Image Fallback
***REMOVED*** Bakes in provisioning config and dashboards so they persist across rollbacks
***REMOVED*** and survive bind mount failures.
***REMOVED***
***REMOVED*** Precedence:
***REMOVED*** 1. Bind mounts (if present) override image content
***REMOVED*** 2. Image content provides baseline/fallback
***REMOVED***
***REMOVED*** This ensures that even if repo diverges or volumes are lost, Grafana starts
***REMOVED*** with a known-good dashboard set from the image.

FROM grafana/grafana:10.3.1

***REMOVED*** Copy provisioning config (datasources, dashboards, plugins)
COPY provisioning/ /etc/grafana/provisioning/

***REMOVED*** Copy dashboards to image (as fallback)
COPY dashboards/ /var/lib/grafana/dashboards/

***REMOVED*** Metadata
LABEL maintainer="Scanium Observability"
LABEL description="Grafana with baked-in dashboards and provisioning for rollback safety"
LABEL version="1.0.0"

***REMOVED*** Note: Environment variables and volumes are still configured via docker-compose.yml
***REMOVED*** This image only provides the baseline config as fallback.
