#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
dash_dir="$repo_root/monitoring/grafana/dashboards"
out_file="$repo_root/monitoring/grafana/DASHBOARD_WIRING_FINDINGS.md"

tmp_input="$(mktemp)"
cleanup() {
  rm -f "$tmp_input"
}
trap cleanup EXIT

if command -v jq >/dev/null 2>&1; then
  for file in "$dash_dir"/*.json; do
    rel="${file#"$repo_root/"}"
    jq -c --arg file "$rel" '.. | objects | select(has("targets")) | {dashboard:$file, panel_title:(.title // "Untitled"), panel_ds:(.datasource // null), targets:.targets}' "$file" >> "$tmp_input"
  done
  python3 - "$tmp_input" "$out_file" <<'PY'
import json
import os
import re
import sys

input_path = sys.argv[1]
out_path = sys.argv[2]

UID_TYPES = {"MIMIR": "prometheus", "LOKI": "loki", "TEMPO": "tempo"}
NAME_TO_UID = {"mimir": "MIMIR", "loki": "LOKI", "tempo": "TEMPO"}
TYPE_TO_UID = {"prometheus": "MIMIR", "loki": "LOKI", "tempo": "TEMPO"}

PROMQL_PATTERNS = [
    r"\brate\(", r"\birate\(", r"\bincrease\(", r"histogram_quantile\(",
    r"\bavg\(", r"\bmax\(", r"\bmin\(", r"\bsum\(", r"\bcount\(",
    r"\bquantile\(", r"\bstddev\(", r"\bstdvar\(", r"\bderiv\(", r"\bdelta\("
]
LOGQL_PATTERNS = [
    r"\|=", r"\|~", r"\|\s*json", r"\|\s*logfmt", r"\|\s*regexp",
    r"\|\s*pattern", r"\|\s*line_format", r"\bunwrap\b",
    r"count_over_time\(", r"sum_over_time\(", r"rate\(\{", r"bytes_over_time\(",
    r"avg_over_time\(", r"min_over_time\(", r"max_over_time\(",
    r"timestamp\(\{"
]
TRACEQL_PATTERNS = [r"\bresource\.", r"\bspan\.", r"\btrace:", r"\bstatus\."]


def normalize_datasource(ds, ds_type_hint):
    if ds is None:
        return "default", None, False
    if isinstance(ds, dict):
        uid = ds.get("uid") or ds.get("name") or ds.get("type")
        dtype = ds.get("type") or ds_type_hint
    else:
        uid = ds
        dtype = ds_type_hint
    if isinstance(uid, str):
        uid_str = uid
        uid_lower = uid_str.lower()
        if uid_str.upper() in UID_TYPES:
            return uid_str.upper(), UID_TYPES[uid_str.upper()], False
        if uid_lower in NAME_TO_UID:
            canonical = NAME_TO_UID[uid_lower]
            return canonical, UID_TYPES.get(canonical), True
    if isinstance(dtype, str) and dtype in TYPE_TO_UID:
        canonical = TYPE_TO_UID[dtype]
        return canonical, UID_TYPES.get(canonical), False
    return str(uid) if uid is not None else "unknown", None, False


def infer_language(query):
    if not query:
        return "unknown"
    q = query.strip()
    for pat in TRACEQL_PATTERNS:
        if re.search(pat, q):
            return "traceql"
    for pat in LOGQL_PATTERNS:
        if re.search(pat, q):
            return "logql"
    for pat in PROMQL_PATTERNS:
        if re.search(pat, q):
            return "promql"
    return "unknown"


def invalid_traceql(query):
    if not query:
        return False
    return re.search(r"\b(?:resource|span|status)\.[A-Za-z0-9_.]+\s*(?<![=!~])=(?![=~])", query) is not None


def query_snippet(query, max_len=120):
    if query is None:
        return ""
    snippet = " ".join(query.strip().split())
    if len(snippet) <= max_len:
        return snippet
    return snippet[: max_len - 3] + "..."


findings = []

with open(input_path, "r", encoding="utf-8") as handle:
    for line in handle:
        if not line.strip():
            continue
        payload = json.loads(line)
        dashboard = payload.get("dashboard", "unknown")
        panel_title = payload.get("panel_title", "Untitled")
        panel_ds = payload.get("panel_ds")
        targets = payload.get("targets", []) or []
        for target in targets:
            if not isinstance(target, dict):
                continue
            target_ds = target.get("datasource")
            ds_uid, ds_kind, name_instead = normalize_datasource(target_ds or panel_ds, target.get("datasource", {}).get("type") if isinstance(target.get("datasource"), dict) else None)
            query = None
            for key in ("expr", "query", "expression", "rawQuery"):
                if isinstance(target.get(key), str):
                    query = target.get(key)
                    break
            suspected = infer_language(query or "")
            problems = []
            recommendation = ""
            if name_instead:
                problems.append("datasource name used instead of UID")
                recommendation = f"set datasource UID to {NAME_TO_UID[str(ds_uid).lower()]}"
            if ds_kind == "tempo" and suspected in {"promql", "logql"}:
                problems.append("tempo datasource with non-TraceQL query")
                recommendation = "set datasource UID to MIMIR" if suspected == "promql" else "set datasource UID to LOKI"
            if ds_kind == "prometheus" and suspected in {"logql", "traceql"}:
                problems.append("mimir datasource with non-PromQL query")
                recommendation = "set datasource UID to LOKI" if suspected == "logql" else "set datasource UID to TEMPO"
            if ds_kind == "loki" and suspected == "promql":
                problems.append("loki datasource with PromQL-like query")
                recommendation = "set datasource UID to MIMIR"
            if ds_kind == "tempo" and suspected == "traceql" and invalid_traceql(query or ""):
                problems.append("tempo TraceQL may use invalid attribute syntax")
                recommendation = "confirm trace attributes or remove/disable trace link"
            if problems:
                findings.append({
                    "dashboard": dashboard,
                    "panel": panel_title,
                    "ds_uid": ds_uid,
                    "language": suspected,
                    "snippet": query_snippet(query or ""),
                    "problem": "; ".join(problems),
                    "recommendation": recommendation or "review datasource/query"
                })

findings.sort(key=lambda x: (x["dashboard"], x["panel"], x["ds_uid"], x["snippet"]))

with open(out_path, "w", encoding="utf-8") as handle:
    handle.write("# Dashboard Wiring Findings\n\n")
    handle.write("| dashboard_file | panel_title | datasource_uid | suspected_language | query_snippet | problem | recommended_fix |\n")
    handle.write("| --- | --- | --- | --- | --- | --- | --- |\n")
    if not findings:
        handle.write("| _none_ | _none_ | _none_ | _none_ | _none_ | _none_ | _none_ |\n")
    else:
        for row in findings:
            handle.write("| {dashboard} | {panel} | {ds_uid} | {language} | {snippet} | {problem} | {recommendation} |\n".format(**row))
PY
else
  python3 - "$dash_dir" "$out_file" <<'PY'
import json
import os
import re
import sys

root = sys.argv[1]
out_path = sys.argv[2]

UID_TYPES = {"MIMIR": "prometheus", "LOKI": "loki", "TEMPO": "tempo"}
NAME_TO_UID = {"mimir": "MIMIR", "loki": "LOKI", "tempo": "TEMPO"}
TYPE_TO_UID = {"prometheus": "MIMIR", "loki": "LOKI", "tempo": "TEMPO"}

PROMQL_PATTERNS = [
    r"\brate\(", r"\birate\(", r"\bincrease\(", r"histogram_quantile\(",
    r"\bavg\(", r"\bmax\(", r"\bmin\(", r"\bsum\(", r"\bcount\(",
    r"\bquantile\(", r"\bstddev\(", r"\bstdvar\(", r"\bderiv\(", r"\bdelta\("
]
LOGQL_PATTERNS = [
    r"\|=", r"\|~", r"\|\s*json", r"\|\s*logfmt", r"\|\s*regexp",
    r"\|\s*pattern", r"\|\s*line_format", r"\bunwrap\b",
    r"count_over_time\(", r"sum_over_time\(", r"rate\(\{", r"bytes_over_time\(",
    r"avg_over_time\(", r"min_over_time\(", r"max_over_time\(",
    r"timestamp\(\{"
]
TRACEQL_PATTERNS = [r"\bresource\.", r"\bspan\.", r"\btrace:", r"\bstatus\."]


def normalize_datasource(ds, ds_type_hint):
    if ds is None:
        return "default", None, False
    if isinstance(ds, dict):
        uid = ds.get("uid") or ds.get("name") or ds.get("type")
        dtype = ds.get("type") or ds_type_hint
    else:
        uid = ds
        dtype = ds_type_hint
    if isinstance(uid, str):
        uid_str = uid
        uid_lower = uid_str.lower()
        if uid_str.upper() in UID_TYPES:
            return uid_str.upper(), UID_TYPES[uid_str.upper()], False
        if uid_lower in NAME_TO_UID:
            canonical = NAME_TO_UID[uid_lower]
            return canonical, UID_TYPES.get(canonical), True
    if isinstance(dtype, str) and dtype in TYPE_TO_UID:
        canonical = TYPE_TO_UID[dtype]
        return canonical, UID_TYPES.get(canonical), False
    return str(uid) if uid is not None else "unknown", None, False


def infer_language(query):
    if not query:
        return "unknown"
    q = query.strip()
    for pat in TRACEQL_PATTERNS:
        if re.search(pat, q):
            return "traceql"
    for pat in LOGQL_PATTERNS:
        if re.search(pat, q):
            return "logql"
    for pat in PROMQL_PATTERNS:
        if re.search(pat, q):
            return "promql"
    return "unknown"


def invalid_traceql(query):
    if not query:
        return False
    return re.search(r"\b(?:resource|span|status)\.[A-Za-z0-9_.]+\s*(?<![=!~])=(?![=~])", query) is not None


def query_snippet(query, max_len=120):
    if query is None:
        return ""
    snippet = " ".join(query.strip().split())
    if len(snippet) <= max_len:
        return snippet
    return snippet[: max_len - 3] + "..."


def iter_panels(data):
    if isinstance(data, dict):
        if "targets" in data and isinstance(data.get("targets"), list):
            yield data
        for value in data.values():
            yield from iter_panels(value)
    elif isinstance(data, list):
        for item in data:
            yield from iter_panels(item)


findings = []

for filename in sorted(os.listdir(root)):
    if not filename.endswith(".json"):
        continue
    path = os.path.join(root, filename)
    rel = os.path.join("monitoring/grafana/dashboards", filename)
    with open(path, "r", encoding="utf-8") as handle:
        payload = json.load(handle)
    for panel in iter_panels(payload):
        panel_title = panel.get("title", "Untitled")
        panel_ds = panel.get("datasource")
        for target in panel.get("targets", []) or []:
            if not isinstance(target, dict):
                continue
            target_ds = target.get("datasource")
            ds_uid, ds_kind, name_instead = normalize_datasource(target_ds or panel_ds, target.get("datasource", {}).get("type") if isinstance(target.get("datasource"), dict) else None)
            query = None
            for key in ("expr", "query", "expression", "rawQuery"):
                if isinstance(target.get(key), str):
                    query = target.get(key)
                    break
            suspected = infer_language(query or "")
            problems = []
            recommendation = ""
            if name_instead:
                problems.append("datasource name used instead of UID")
                recommendation = f"set datasource UID to {NAME_TO_UID[str(ds_uid).lower()]}"
            if ds_kind == "tempo" and suspected in {"promql", "logql"}:
                problems.append("tempo datasource with non-TraceQL query")
                recommendation = "set datasource UID to MIMIR" if suspected == "promql" else "set datasource UID to LOKI"
            if ds_kind == "prometheus" and suspected in {"logql", "traceql"}:
                problems.append("mimir datasource with non-PromQL query")
                recommendation = "set datasource UID to LOKI" if suspected == "logql" else "set datasource UID to TEMPO"
            if ds_kind == "loki" and suspected == "promql":
                problems.append("loki datasource with PromQL-like query")
                recommendation = "set datasource UID to MIMIR"
            if ds_kind == "tempo" and suspected == "traceql" and invalid_traceql(query or ""):
                problems.append("tempo TraceQL may use invalid attribute syntax")
                recommendation = "confirm trace attributes or remove/disable trace link"
            if problems:
                findings.append({
                    "dashboard": rel,
                    "panel": panel_title,
                    "ds_uid": ds_uid,
                    "language": suspected,
                    "snippet": query_snippet(query or ""),
                    "problem": "; ".join(problems),
                    "recommendation": recommendation or "review datasource/query"
                })

findings.sort(key=lambda x: (x["dashboard"], x["panel"], x["ds_uid"], x["snippet"]))

with open(out_path, "w", encoding="utf-8") as handle:
    handle.write("# Dashboard Wiring Findings\n\n")
    handle.write("| dashboard_file | panel_title | datasource_uid | suspected_language | query_snippet | problem | recommended_fix |\n")
    handle.write("| --- | --- | --- | --- | --- | --- | --- |\n")
    if not findings:
        handle.write("| _none_ | _none_ | _none_ | _none_ | _none_ | _none_ | _none_ |\n")
    else:
        for row in findings:
            handle.write("| {dashboard} | {panel} | {ds_uid} | {language} | {snippet} | {problem} | {recommendation} |\n".format(**row))
PY
fi

echo "Wrote findings to $out_file"
