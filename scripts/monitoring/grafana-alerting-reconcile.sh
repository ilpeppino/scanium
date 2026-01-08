#!/usr/bin/env sh
set -euo pipefail

if [ -z "${GRAFANA_URL:-}" ]; then
  echo "GRAFANA_URL is not set" >&2
  exit 1
fi

if [ -z "${GRAFANA_TOKEN:-}" ]; then
  echo "GRAFANA_TOKEN is not set" >&2
  exit 1
fi

org_header=""
if [ -n "${GRAFANA_ORG_ID:-}" ]; then
  org_header="X-Grafana-Org-Id: ${GRAFANA_ORG_ID}"
fi

api_get() {
  if [ -n "$org_header" ]; then
    curl -sS -H "Authorization: Bearer ${GRAFANA_TOKEN}" -H "$org_header" "${GRAFANA_URL}$1"
  else
    curl -sS -H "Authorization: Bearer ${GRAFANA_TOKEN}" "${GRAFANA_URL}$1"
  fi
}

api_delete() {
  if [ -n "$org_header" ]; then
    curl -sS -o /dev/null -w "%{http_code}" -H "Authorization: Bearer ${GRAFANA_TOKEN}" -H "$org_header" -X DELETE "${GRAFANA_URL}$1"
  else
    curl -sS -o /dev/null -w "%{http_code}" -H "Authorization: Bearer ${GRAFANA_TOKEN}" -X DELETE "${GRAFANA_URL}$1"
  fi
}

list_rules() {
  api_get "/api/v1/provisioning/alert-rules"
}

get_rule() {
  api_get "/api/v1/provisioning/alert-rules/$1"
}

delete_rule() {
  api_delete "/api/v1/provisioning/alert-rules/$1"
}

rule_uid="$1"
if [ -z "$rule_uid" ]; then
  echo "Usage: $0 <rule_uid>" >&2
  exit 1
fi

if ! list_rules >/dev/null; then
  echo "Failed to list alert rules" >&2
  exit 1
fi

if get_rule "$rule_uid" >/dev/null 2>&1; then
  echo "Found rule uid: $rule_uid"
  status="$(delete_rule "$rule_uid")"
  if [ "$status" = "200" ] || [ "$status" = "204" ]; then
    echo "Deleted rule uid: $rule_uid"
  else
    echo "Delete failed for $rule_uid (HTTP $status)" >&2
    exit 1
  fi
else
  echo "Rule uid not found: $rule_uid"
fi

echo "Done"
