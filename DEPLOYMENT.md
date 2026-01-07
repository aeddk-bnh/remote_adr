# ARCS Deployment Guide

## Quick Start with Docker Compose

### Prerequisites

- Docker 20.10+
- Docker Compose 2.0+

### 1. Clone Repository

```bash
git clone https://github.com/your-org/arcs.git
cd arcs
```

### 2. Configure Environment

```bash
# Copy environment template
cp .env.example .env

# Edit configuration
nano .env
```

### 3. Start Services

```bash
# Development mode
docker-compose up

# Production mode
docker-compose --profile production up -d
```

### 4. Access Services

- **Web Controller**: http://localhost:3000
- **Server API**: http://localhost:8080
- **WebSocket**: ws://localhost:8080

---

## Production Deployment

### 1. Server Deployment

#### Using Docker

```bash
# Build server image
docker build -f Dockerfile.server -t arcs-server:latest .

# Run server
docker run -d \
  --name arcs-server \
  -p 8080:8080 \
  -v $(pwd)/data:/app/data \
  -v $(pwd)/logs:/app/logs \
  -e JWT_SECRET=your-secret-key \
  arcs-server:latest
```

#### Manual Build (Linux)

```bash
# Install dependencies
sudo apt-get install build-essential cmake libssl-dev libsqlite3-dev \
  libboost-all-dev uuid-dev pkg-config

# Clone and install Pistache
git clone https://github.com/pistacheio/pistache.git
cd pistache && mkdir build && cd build
cmake .. && make && sudo make install

# Build ARCS server
cd server
mkdir build && cd build
cmake -DCMAKE_BUILD_TYPE=Release ..
make -j$(nproc)

# Run server
./arcs-server
```

### 2. Web Controller Deployment

#### Using Docker

```bash
# Build web image
docker build -f Dockerfile.web -t arcs-web:latest .

# Run web controller
docker run -d \
  --name arcs-web \
  -p 3000:80 \
  arcs-web:latest
```

#### Static Hosting (Netlify/Vercel)

```bash
# Build for production
cd web-controller
npm install
npm run build

# Deploy dist/ folder to hosting service
```

### 3. Reverse Proxy (Nginx)

```bash
# Start with production profile
docker-compose --profile production up -d nginx-proxy
```

Or manually configure Nginx:

```nginx
# Copy nginx-proxy.conf to /etc/nginx/nginx.conf
sudo cp nginx-proxy.conf /etc/nginx/nginx.conf

# Generate SSL certificates
sudo certbot --nginx -d your-domain.com

# Restart Nginx
sudo systemctl restart nginx
```

---

## Android Client Deployment

### 1. Build APK

```bash
cd android-client
./gradlew assembleRelease
```

### 2. Sign APK

```bash
# Generate keystore (first time only)
keytool -genkey -v -keystore arcs.keystore -alias arcs -keyalg RSA -keysize 2048 -validity 10000

# Sign APK
jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 \
  -keystore arcs.keystore \
  app/build/outputs/apk/release/app-release-unsigned.apk arcs

# Verify
jarsigner -verify -verbose -certs app/build/outputs/apk/release/app-release-unsigned.apk
```

### 3. Distribute

- **Google Play**: Upload to Play Console
- **Direct**: Host APK for download
- **Enterprise**: Use MDM solution

---

## PC Controller Deployment

### Linux

```bash
cd pc-controller
mkdir build && cd build
cmake -DCMAKE_BUILD_TYPE=Release ..
make -j$(nproc)
sudo make install
```

### Windows

```bash
# Using Qt Creator
1. Open CMakeLists.txt in Qt Creator
2. Configure project with Qt 6 kit
3. Build > Build All
4. Deploy > Create Installer (NSIS)
```

### macOS

```bash
mkdir build && cd build
cmake -DCMAKE_BUILD_TYPE=Release ..
make -j$(sysctl -n hw.ncpu)
macdeployqt arcs-pc-controller.app -dmg
```

---

## Environment Variables

### Server

```bash
# JWT secret key (REQUIRED)
JWT_SECRET=your-256-bit-secret-key

# Log level (info, debug, warning, error)
LOG_LEVEL=info

# Maximum concurrent sessions
MAX_SESSIONS=100

# Database path
DB_PATH=/app/data/devices.db

# Audit log path
AUDIT_LOG_PATH=/app/logs/audit.log
```

### Web Controller

```bash
# Default server URL
VITE_DEFAULT_SERVER_URL=wss://your-domain.com

# Environment (development, production)
NODE_ENV=production
```

---

## SSL/TLS Configuration

### Generate Self-Signed Certificates (Development)

```bash
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout ssl/key.pem \
  -out ssl/cert.pem \
  -subj "/CN=localhost"
```

### Production Certificates (Let's Encrypt)

```bash
# Install Certbot
sudo apt-get install certbot python3-certbot-nginx

# Generate certificates
sudo certbot --nginx -d your-domain.com -d www.your-domain.com

# Auto-renewal
sudo certbot renew --dry-run
```

---

## Monitoring

### Logs

```bash
# Server logs
docker logs -f arcs-server

# Web controller logs
docker logs -f arcs-web

# Nginx logs
docker logs -f arcs-proxy
```

### Metrics

```bash
# Server health
curl http://localhost:8080/health

# Active sessions
curl http://localhost:8080/api/sessions/count

# System stats
docker stats arcs-server arcs-web
```

---

## Backup

### Database

```bash
# Backup SQLite database
cp data/devices.db backups/devices-$(date +%Y%m%d).db

# Automated backup
0 2 * * * cp /app/data/devices.db /backups/devices-$(date +\%Y\%m\%d).db
```

### Logs

```bash
# Archive logs
tar -czf logs-$(date +%Y%m%d).tar.gz logs/

# Rotate logs
logrotate /etc/logrotate.d/arcs
```

---

## Scaling

### Horizontal Scaling

```bash
# Scale server instances
docker-compose up -d --scale arcs-server=3

# Load balancer configuration required
```

### Database Scaling

```bash
# Migrate to PostgreSQL for multi-instance support
# Update device_registry.cpp to use PostgreSQL
```

---

## Security Checklist

- [ ] Change default JWT_SECRET
- [ ] Enable HTTPS/WSS in production
- [ ] Configure firewall rules
- [ ] Enable rate limiting
- [ ] Implement IP whitelisting (if needed)
- [ ] Regular security updates
- [ ] Audit log monitoring
- [ ] Database encryption at rest
- [ ] Secure API keys storage

---

## Troubleshooting

### Server won't start

```bash
# Check logs
docker logs arcs-server

# Verify port availability
netstat -tulpn | grep 8080

# Check dependencies
ldd server/build/arcs-server
```

### WebSocket connection fails

```bash
# Test WebSocket
wscat -c ws://localhost:8080

# Check firewall
sudo ufw status
sudo ufw allow 8080/tcp
```

### High latency

```bash
# Check network
ping server-ip

# Monitor bandwidth
iftop

# Check CPU usage
top
```

---

**End of Deployment Guide**
