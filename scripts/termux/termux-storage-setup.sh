#!/usr/bin/env bash
# Termux storage setup helper for Scanium
# Run this once after installing Termux to enable access to shared storage.

set -euo pipefail

DOWNLOADS_PATH="$HOME/storage/downloads"

echo "=== Termux Storage Setup ==="
echo ""

if [[ -d "$DOWNLOADS_PATH" ]]; then
    echo "OK: Storage is already configured."
    echo "    $DOWNLOADS_PATH exists and is accessible."
    exit 0
fi

echo "ERROR: Shared storage is not configured."
echo ""
echo "Termux needs permission to access Android shared storage (Downloads folder)."
echo ""
echo "To fix this:"
echo "  1. Run:  termux-setup-storage"
echo "  2. Grant storage permission when prompted"
echo "  3. Re-run this script to verify"
echo ""
echo "Note: This only needs to be done once per Termux installation."
exit 1
