***REMOVED*** Grafana provisioning fix (NAS)

***REMOVED******REMOVED*** What was wrong
- The NAS compose file only mounted the Grafana data directory, so provisioning and dashboards from the repo were never loaded.
- One dashboard (pipeline-health) used lowercase datasource UIDs that did not match the provisioned UIDs.
- Alerting provisioning failed due to DB uniqueness conflicts on empty `guid`/`rule_guid` values.

***REMOVED******REMOVED*** What changed
- Enabled provisioning and dashboards mounts in the NAS monitoring compose file.
- Normalized pipeline-health datasource UIDs to match the provisioned UIDs (LOKI/MIMIR/TEMPO).
- Reset alerting tables and dropped the `UQE_alert_rule_version_rule_guid_version` index, then added triggers to populate `guid`/`rule_guid`.

***REMOVED******REMOVED*** Expected result
- Grafana provisions datasources from `monitoring/grafana/provisioning/datasources/datasources.yaml`.
- Dashboards in `monitoring/grafana/dashboards/` are auto-loaded into the "Scanium" folder.
- Alert rules provision without restart loops.

***REMOVED******REMOVED*** Notes
- If dashboards are still missing, check Grafana startup logs for provisioning errors and verify the mounts.
- Alerting DB reset is documented in `monitoring/grafana/ALERTING_RECONCILE.md`.
