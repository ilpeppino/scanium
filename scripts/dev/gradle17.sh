***REMOVED***!/usr/bin/env bash
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"

JDK17="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
if [ -z "$JDK17" ]; then
  if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    echo "Warning: JDK 17 not found; using existing JAVA_HOME=$JAVA_HOME" >&2
    export PATH="$JAVA_HOME/bin:$PATH"
    exec ./gradlew "$@"
  fi
  JDK21="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
  if [ -n "$JDK21" ]; then
    echo "Warning: JDK 17 not found; using JDK 21 at $JDK21" >&2
    export JAVA_HOME="$JDK21"
    export PATH="$JAVA_HOME/bin:$PATH"
    exec ./gradlew "$@"
  fi
  if command -v java >/dev/null 2>&1; then
    echo "Warning: JDK 17 not found; using java from PATH." >&2
    exec ./gradlew "$@"
  fi
  echo "Error: JDK 17 not found. Install JDK 17 (e.g. Temurin) and ensure /usr/libexec/java_home -v 17 works." >&2
  exit 1
fi

export JAVA_HOME="$JDK17"
export PATH="$JAVA_HOME/bin:$PATH"

exec ./gradlew "$@"
