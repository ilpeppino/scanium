***REMOVED*** Grafana alerting reconcile (NAS)

***REMOVED******REMOVED*** Root cause

Grafana alert rule provisioning failed with `UNIQUE constraint failed: alert_rule.guid` and later
`alert_rule_version.rule_guid, alert_rule_version.version`. The DB stored empty `guid`/`rule_guid`
values, so multiple rules inserted in one provisioning run collided on uniqueness constraints.

***REMOVED******REMOVED*** Token and URL

- Token file: `/volume1/docker/scanium/secrets/grafana_service_token.txt`
- Base URL: `http://127.0.0.1:3000`

***REMOVED******REMOVED*** Reconcile script

The script deletes specific alert rules by UID using the Grafana provisioning API. It does not print
secrets.

```bash
export GRAFANA_TOKEN="$(cat /volume1/docker/scanium/secrets/grafana_service_token.txt)"
export GRAFANA_URL="http://127.0.0.1:3000"
export GRAFANA_ORG_ID=1

scripts/monitoring/grafana-alerting-reconcile.sh <rule_uid>
```

***REMOVED******REMOVED*** Safe workflow

1) Temporarily disable alerting provisioning:
    - Rename `monitoring/grafana/provisioning/alerting/rules.yaml` to `rules.yaml.disabled`.
2) Restart Grafana.
3) Delete only the conflicting rule UIDs with the reconcile script.
4) Re-enable `rules.yaml`, restart Grafana, and verify logs are clean.

***REMOVED******REMOVED*** DB cleanup (used on NAS)

When API deletes did not clear the conflicts, we performed a DB cleanup that also drops the
`UQE_alert_rule_version_rule_guid_version` index.

```bash
scripts/monitoring/grafana-alerting-db-reset.sh
```

The script:

- Stops Grafana.
- Backs up `grafana.db`.
- Clears alerting tables.
- Drops `UQE_alert_rule_version_rule_guid_version` (empty `rule_guid` values caused collisions).
- Recreates helper triggers to populate `guid`/`rule_guid`.
- Restarts Grafana.

***REMOVED******REMOVED*** Notes

- Dropping the index is a DB-level workaround for Grafana 11.x provisioning behavior.
- Avoid DB changes without approval; always keep a backup.
