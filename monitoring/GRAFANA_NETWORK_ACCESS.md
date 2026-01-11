# Grafana Network Access Solutions

**Issue**: `ERR_CONNECTION_REFUSED` when accessing http://192.168.178.45:3000
**Cause**: Network routing/firewall issue preventing direct access to Grafana

---

## Diagnosis

✅ **Grafana is running correctly**:
- Container: `scanium-grafana` is UP and healthy
- Port 3000 is listening on 0.0.0.0:3000
- API responds correctly when accessed via SSH

❌ **Network Issue**:
- NAS IP: `192.168.178.45/24` (eth1 interface)
- Your device cannot reach this IP address
- This could be due to:
  - Different network subnet
  - Firewall blocking the connection
  - VPN or proxy interference
  - Accessing from outside local network

---

## Solution 1: SSH Tunnel (Recommended)

Create an SSH tunnel to access Grafana through localhost:

### Mac/Linux:
```bash
ssh -L 3000:localhost:3000 nas
```

Then access Grafana at:
```
http://localhost:3000
```

Keep the SSH session running while using Grafana.

### Alternative Port (if 3000 is in use):
```bash
ssh -L 8080:localhost:3000 nas
```

Then access at: http://localhost:8080

---

## Solution 2: Background SSH Tunnel

Run the tunnel in the background:

```bash
# Create background tunnel
ssh -f -N -L 3000:localhost:3000 nas

# Access Grafana
open http://localhost:3000

# When done, kill the tunnel
pkill -f "ssh.*3000:localhost:3000"
```

---

## Solution 3: Check Your Network

Find out what network you're on:

```bash
# Get your device's IP address
ifconfig | grep "inet " | grep -v 127.0.0.1
```

**If you're on 192.168.178.x subnet**:
- You should be able to access http://192.168.178.45:3000 directly
- If not, check your device's firewall settings

**If you're on a different subnet** (e.g., 192.168.68.x):
- You need SSH tunnel (Solution 1)
- OR configure router to allow routing between subnets
- OR connect to the same network as the NAS

---

## Solution 4: Access via NAS Hostname

Try accessing via hostname:

```
http://DSPlay418:3000
http://DSPlay418.local:3000
```

This works if:
- Your device's DNS can resolve the NAS hostname
- mDNS/Bonjour is working on your network

Test hostname resolution:
```bash
ping DSPlay418
# or
ping DSPlay418.local
```

---

## Solution 5: Port Forward from Router

If your router supports it, set up port forwarding:

1. Log into your router admin interface
2. Find "Port Forwarding" or "Virtual Server" settings
3. Forward external port (e.g., 3000) to 192.168.178.45:3000
4. Access via your router's IP or external IP

⚠️ **Security Warning**: Only do this if you understand the security implications. Grafana is configured with anonymous access.

---

## Quick Test Commands

### From Mac - Test if you can reach the NAS IP:
```bash
# Test TCP connection
nc -zv 192.168.178.45 3000

# Test HTTP
curl -v http://192.168.178.45:3000/api/health

# Test via SSH tunnel
ssh nas "curl -s http://localhost:3000/api/health"
```

### Check NAS network configuration:
```bash
ssh nas "ip addr show eth1"
ssh nas "ip route"
```

---

## Recommended: Use SSH Tunnel

**For regular access**, I recommend setting up a permanent SSH tunnel:

### Create alias in ~/.zshrc or ~/.bashrc:
```bash
# Add this to your shell config
alias grafana-tunnel='ssh -L 3000:localhost:3000 nas'
```

### Then use it:
```bash
# Start tunnel
grafana-tunnel

# In another terminal or browser
open http://localhost:3000
```

---

## Troubleshooting

### "Port 3000 already in use"
```bash
# Find what's using port 3000
lsof -i :3000

# Use a different port
ssh -L 8080:localhost:3000 nas
# Then access: http://localhost:8080
```

### "SSH tunnel keeps disconnecting"
```bash
# Keep tunnel alive with keep-alive option
ssh -o ServerAliveInterval=60 -L 3000:localhost:3000 nas
```

### "Cannot resolve hostname 'nas'"
Update your SSH config (`~/.ssh/config`):
```
Host nas
  HostName 192.168.178.45
  User <your-username>
  ServerAliveInterval 60
```

---

## Current Grafana Status

✅ **Container Running**:
```
NAMES             STATUS                    PORTS
scanium-grafana   Up 11 minutes (healthy)   0.0.0.0:3000->3000/tcp
```

✅ **Port Listening**:
```
tcp  0  0  0.0.0.0:3000  0.0.0.0:*  LISTEN
```

✅ **API Health Check** (via SSH):
```json
{
  "commit": "00a22ff8b28550d593ec369ba3da1b25780f0a4a",
  "database": "ok",
  "version": "10.3.1"
}
```

---

## All Dashboard URLs (via SSH Tunnel)

Once tunnel is running, access dashboards at:

- **Backend Errors**: http://localhost:3000/d/scanium-backend-errors/scanium-backend-errors
- **System Overview**: http://localhost:3000/d/scanium-system-overview/scanium-system-overview-red
- **All Dashboards**: http://localhost:3000/dashboards
- **Scanium Folder**: http://localhost:3000/dashboards?folderIds=2

---

## Next Steps

1. **Open terminal**
2. **Run**: `ssh -L 3000:localhost:3000 nas`
3. **Open browser**: http://localhost:3000
4. **Start exploring** your dashboards!

---

**Updated**: 2026-01-11T10:20:00Z
