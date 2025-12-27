***REMOVED***!/usr/bin/env bash
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"

JDK17="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
if [ -z "$JDK17" ]; then
  echo "Error: JDK 17 not found. Install JDK 17 (e.g. Temurin) and ensure /usr/libexec/java_home -v 17 works." >&2
  exit 1
fi

export JAVA_HOME="$JDK17"
export PATH="$JAVA_HOME/bin:$PATH"

exec ./gradlew "$@"
