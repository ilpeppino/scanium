***REMOVED***!/bin/bash
***REMOVED*** Create GitHub labels for go-live backlog
***REMOVED*** Run this script first before creating issues

set -e

echo "Creating severity labels..."
gh label create "severity:critical" --description "Critical issue blocking go-live" --color "B60205" || true
gh label create "severity:high" --description "High priority issue for go-live" --color "D93F0B" || true
gh label create "severity:medium" --description "Medium priority issue" --color "FBCA04" || true
gh label create "severity:low" --description "Low priority issue" --color "0E8A16" || true

echo "Creating epic labels..."
gh label create "epic:backend" --description "Backend API and services" --color "1D76DB" || true
gh label create "epic:mobile" --description "Mobile application (Android/iOS)" --color "5319E7" || true
gh label create "epic:observability" --description "Monitoring, logging, alerting" --color "0E8A16" || true
gh label create "epic:security" --description "Security and compliance" --color "B60205" || true
gh label create "epic:docs" --description "Documentation" --color "0075CA" || true
gh label create "epic:scale-ios" --description "iOS platform support" --color "FEF2C0" || true

echo "Creating area labels..."
gh label create "area:android" --description "Android-specific" --color "C5DEF5" || true
gh label create "area:backend" --description "Backend-specific" --color "C5DEF5" || true
gh label create "area:network" --description "Network and API" --color "C5DEF5" || true
gh label create "area:auth" --description "Authentication and authorization" --color "C5DEF5" || true
gh label create "area:ml" --description "Machine learning" --color "C5DEF5" || true
gh label create "area:camera" --description "Camera functionality" --color "C5DEF5" || true
gh label create "area:logging" --description "Logging and diagnostics" --color "C5DEF5" || true
gh label create "area:ci" --description "CI/CD pipelines" --color "C5DEF5" || true
gh label create "area:privacy" --description "Privacy and data protection" --color "C5DEF5" || true
gh label create "area:docs" --description "Documentation" --color "C5DEF5" || true

echo "Creating priority labels..."
gh label create "priority:p0" --description "P0 - Must be done before go-live" --color "B60205" || true
gh label create "priority:p1" --description "P1 - Required shortly after beta/early launch" --color "D93F0B" || true
gh label create "priority:p2" --description "P2 - Scale-up and future-proofing" --color "FBCA04" || true

echo "✅ Labels created successfully!"
echo "Now run: bash docs/go-live/CREATE_ISSUES.sh"
