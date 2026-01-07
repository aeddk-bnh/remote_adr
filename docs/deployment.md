# ARCS Deployment Guide

## Prerequisites

### Server
- Linux server (Ubuntu 20.04+ recommended)
- 2+ CPU cores
- 4GB+ RAM
- SSL certificate (Let's Encrypt recommended)
- Public IP or domain name

### Android Device
- Android 8.0+ (API 26+)
- 2GB+ RAM
- Stable network connection

### Controller
- PC: Windows 10+, Linux, macOS
- Web: Modern browser (Chrome, Firefox, Edge)

## Server Deployment

### 1. Build Server

```bash
# Install dependencies
sudo apt update
sudo apt install -y build-essential cmake libssl-dev \
    libsqlite3-dev pkg-config

# Install Pistache
git clone https://github.com/pistacheio/pistache.git
cd pistache
mkdir build && cd build
cmake ..
make -j$(nproc)
sudo make install

# Install jwt-cpp
git clone https://github.com/Thalhammer/jwt-cpp.git
cd jwt-cpp
sudo cp -r include/jwt-cpp /usr/local/include/

# Build ARCS server
cd /path/to/remote_adr/server
mkdir build && cd build
cmake -DCMAKE_BUILD_TYPE=Release ..
make -j$(nproc)
```

### 2. Configure Server

```bash
# Create config directory
sudo mkdir -p /etc/arcs
sudo cp ../config/server.conf /etc/arcs/

# Edit configuration
sudo nano /etc/arcs/server.conf
```

**server.conf:**
```ini
[server]
port=9080
threads=8
jwt_secret=CHANGE_THIS_TO_RANDOM_SECRET
jwt_expiry_hours=24

[websocket]
max_connections=1000
ping_interval=30
timeout=300

[security]
tls_cert=/etc/letsencrypt/live/yourdomain.com/fullchain.pem
tls_key=/etc/letsencrypt/live/yourdomain.com/privkey.pem
enable_encryption=true

[database]
path=/var/lib/arcs/devices.db
```

### 3. Setup SSL Certificate

```bash
# Install Certbot
sudo apt install -y certbot

# Get certificate
sudo certbot certonly --standalone -d yourdomain.com
```

### 4. Create Systemd Service

```bash
sudo nano /etc/systemd/system/arcs-server.service
```

**arcs-server.service:**
```ini
[Unit]
Description=ARCS Remote Control Server
After=network.target

[Service]
Type=simple
User=arcs
Group=arcs
WorkingDirectory=/opt/arcs
ExecStart=/opt/arcs/arcs-server
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

### 5. Start Service

```bash
# Create user
sudo useradd -r -s /bin/false arcs

# Create directories
sudo mkdir -p /var/lib/arcs
sudo chown arcs:arcs /var/lib/arcs

# Copy binary
sudo cp build/arcs-server /opt/arcs/
sudo chown arcs:arcs /opt/arcs/arcs-server

# Start service
sudo systemctl daemon-reload
sudo systemctl enable arcs-server
sudo systemctl start arcs-server

# Check status
sudo systemctl status arcs-server
```

### 6. Configure Firewall

```bash
# Allow HTTPS
sudo ufw allow 443/tcp

# Allow WebSocket port
sudo ufw allow 9080/tcp

# Enable firewall
sudo ufw enable
```

## Android Client Deployment

### 1. Build APK

```bash
cd android-client
./gradlew assembleRelease
```

### 2. Sign APK (Production)

```bash
# Generate keystore (first time)
keytool -genkey -v -keystore arcs-release.keystore \
    -alias arcs -keyalg RSA -keysize 2048 -validity 10000

# Sign APK
jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 \
    -keystore arcs-release.keystore \
    app/build/outputs/apk/release/app-release-unsigned.apk arcs

# Align APK
zipalign -v 4 app/build/outputs/apk/release/app-release-unsigned.apk \
    app/build/outputs/apk/release/app-release.apk
```

### 3. Install on Device

```bash
# Via ADB
adb install app/build/outputs/apk/release/app-release.apk

# Or distribute via:
# - Google Play Store
# - Enterprise MDM
# - Direct download
```

### 4. Initial Setup

1. Open ARCS app
2. Grant permissions:
   - Notification (Android 13+)
   - Screen capture (on first use)
3. Enable services:
   - Settings > Accessibility > ARCS Accessibility Service
   - Settings > Keyboard > ARCS Input Method
4. Configure server:
   - Server URL: `wss://yourdomain.com:9080`
   - Device secret: Auto-generated (save securely)

## PC Controller Deployment

### 1. Build (Qt version)

```bash
cd controller-pc
mkdir build && cd build
cmake ..
make
```

### 2. Install

```bash
# Linux
sudo make install

# Windows
# Use installer or portable version

# macOS
# Create .app bundle
```

## Web Controller Deployment

### 1. Build

```bash
cd controller-web
npm install
npm run build
```

### 2. Deploy

**Static Hosting (Netlify, Vercel):**
```bash
# Deploy dist/ folder
npm run deploy
```

**Self-hosted (Nginx):**
```nginx
server {
    listen 80;
    server_name controller.yourdomain.com;
    
    root /var/www/arcs-web;
    index index.html;
    
    location / {
        try_files $uri $uri/ /index.html;
    }
    
    # WebSocket proxy
    location /ws {
        proxy_pass https://yourdomain.com:9080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
```

## Production Checklist

### Security
- [ ] Change JWT secret
- [ ] Enable TLS/SSL
- [ ] Configure firewall
- [ ] Set up rate limiting
- [ ] Enable audit logging
- [ ] Regular security updates

### Performance
- [ ] Tune server threads
- [ ] Configure connection limits
- [ ] Optimize video bitrate
- [ ] Enable compression
- [ ] Set up monitoring

### Reliability
- [ ] Configure auto-restart
- [ ] Set up backup
- [ ] Configure log rotation
- [ ] Monitor disk space
- [ ] Set up health checks

### Monitoring
- [ ] Server logs
- [ ] Connection metrics
- [ ] Error rates
- [ ] Resource usage
- [ ] Alert configuration

## Monitoring

### Logs

```bash
# Server logs
sudo journalctl -u arcs-server -f

# Application logs
tail -f /var/log/arcs/server.log
```

### Metrics

```bash
# Active connections
curl https://yourdomain.com:9080/api/metrics

# Health check
curl https://yourdomain.com:9080/health
```

## Troubleshooting

### Server won't start
```bash
# Check logs
sudo journalctl -u arcs-server -n 50

# Check port
sudo netstat -tulpn | grep 9080

# Check permissions
ls -la /opt/arcs/
```

### Connection issues
```bash
# Test WebSocket
wscat -c wss://yourdomain.com:9080/ws

# Check firewall
sudo ufw status

# Check SSL
openssl s_client -connect yourdomain.com:9080
```

### Performance issues
```bash
# Check resources
top
free -h
df -h

# Check connections
ss -s
```

## Scaling

### Horizontal Scaling

```bash
# Use load balancer (HAProxy, Nginx)
# Configure session affinity
# Use Redis for shared sessions
```

### Vertical Scaling

```bash
# Increase threads in config
threads=16

# Optimize database
# Use connection pooling
```

## Backup

```bash
# Database backup
sudo sqlite3 /var/lib/arcs/devices.db .dump > backup.sql

# Configuration backup
sudo tar -czf config-backup.tar.gz /etc/arcs/

# Automated backup
sudo crontab -e
# Add: 0 2 * * * /usr/local/bin/arcs-backup.sh
```

## Updates

```bash
# Pull latest code
git pull origin main

# Rebuild
cd server/build
make

# Restart service
sudo systemctl restart arcs-server
```
