#!/usr/bin/env bash
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"

echo "Running Gradle configuration sanity check..."

if ./gradlew -q :androidApp:tasks > /dev/null 2>&1; then
    echo "PASS: Gradle configuration is valid"
    exit 0
else
    echo "FAIL: Gradle configuration check failed"
    echo "Run './gradlew :androidApp:tasks' for details"
    exit 1
fi
