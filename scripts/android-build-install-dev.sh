***REMOVED***!/usr/bin/env bash
***REMOVED*** =============================================================================
***REMOVED*** DEPRECATED: This script has been moved to scripts/android/build-install-devdebug.sh
***REMOVED*** =============================================================================
***REMOVED*** This stub forwards to the new location for backwards compatibility.
***REMOVED*** Please update your workflows to use the new path.
***REMOVED*** =============================================================================

echo ""
echo "WARNING: This script is DEPRECATED!"
echo ""
echo "  Old: scripts/android-build-install-dev.sh"
echo "  New: scripts/android/build-install-devdebug.sh"
echo ""
echo "Forwarding to new location..."
echo ""

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "$SCRIPT_DIR/android/build-install-devdebug.sh" "$@"
