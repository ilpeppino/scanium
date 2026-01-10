***REMOVED*** Monitoring Testing Tools

This directory contains scripts and tools for testing the monitoring infrastructure.

***REMOVED******REMOVED*** Available Tools

***REMOVED******REMOVED******REMOVED*** [generate-openai-traffic.sh](./generate-openai-traffic.sh)
**Purpose:** Generate controlled OpenAI/Assistant API traffic for dashboard testing
**Generates:** 3 successful requests + 1 error over ~60 seconds
**Tests:** Request rate, latency, token usage metrics

**Usage:**
```bash
ssh nas
cd /volume1/docker/scanium/repo

***REMOVED*** Get API key and backend IP
API_KEY=$(grep SCANIUM_API_KEYS backend/.env | cut -d= -f2 | cut -d, -f1)
BACKEND_IP=$(docker inspect -f '{{range.NetworkSettings.Networks}}{{.IPAddress}}{{end}}' scanium-backend)

***REMOVED*** Run traffic generator
SCANIUM_API_KEY=$API_KEY bash monitoring/howto/testing/generate-openai-traffic.sh http://$BACKEND_IP:8080
```

***REMOVED******REMOVED*** Creating New Test Scripts

1. Use minimal, controlled traffic (avoid spam)
2. Include both success and error cases
3. Print timestamps and summary statistics
4. Never log secrets/API keys/PII
5. Document usage in this README
