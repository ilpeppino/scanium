***REMOVED***!/usr/bin/env sh
set -euo pipefail

GRAFANA_CONTAINER="${GRAFANA_CONTAINER:-scanium-grafana}"
GRAFANA_DB_DIR="${GRAFANA_DB_DIR:-/volume1/docker/scanium/monitoring/grafana}"
DOCKER_BIN="${DOCKER_BIN:-/usr/local/bin/docker}"
SQL_FILE="/tmp/grafana_alerting_reset.sql"

if [ ! -f "$GRAFANA_DB_DIR/grafana.db" ]; then
  echo "grafana.db not found in $GRAFANA_DB_DIR" >&2
  exit 1
fi

$DOCKER_BIN stop "$GRAFANA_CONTAINER" >/dev/null

$DOCKER_BIN run --rm -v "$GRAFANA_DB_DIR:/var/lib/grafana" alpine:3.19 sh -c "cp /var/lib/grafana/grafana.db /var/lib/grafana/grafana.db.bak-$(date +%Y%m%d%H%M%S)"

cat <<SQL > "$SQL_FILE"
BEGIN;
DROP INDEX IF EXISTS UQE_alert_rule_version_rule_guid_version;
DELETE FROM alert_rule_version;
DELETE FROM alert_rule_tag;
DELETE FROM alert_rule_state;
DELETE FROM alert_rule;
DELETE FROM alert_instance;
DELETE FROM alert_notification;
DELETE FROM alert_notification_state;
DELETE FROM alert;
DELETE FROM alert_configuration;
DELETE FROM alert_configuration_history;
DELETE FROM alert_image;
DELETE FROM ngalert_configuration;
CREATE TRIGGER IF NOT EXISTS alert_rule_guid_default AFTER INSERT ON alert_rule
FOR EACH ROW WHEN NEW.guid = 
BEGIN
  UPDATE alert_rule SET guid = NEW.uid WHERE id = NEW.id;
END;
CREATE TRIGGER IF NOT EXISTS alert_rule_version_guid_default AFTER INSERT ON alert_rule_version
FOR EACH ROW WHEN NEW.rule_guid = 
BEGIN
  UPDATE alert_rule_version SET rule_guid = NEW.rule_uid WHERE id = NEW.id;
END;
COMMIT;
SQL

$DOCKER_BIN run --rm -v "$GRAFANA_DB_DIR:/var/lib/grafana" -v "$SQL_FILE:$SQL_FILE" alpine:3.19 sh -c "apk add --no-cache sqlite >/dev/null && sqlite3 /var/lib/grafana/grafana.db < $SQL_FILE"

rm -f "$SQL_FILE"

$DOCKER_BIN start "$GRAFANA_CONTAINER" >/dev/null

echo "Grafana alerting DB reset complete"
