# Android Remote Control System - Server

C++ backend server for ARCS using Pistache framework.

## Features

- WebSocket server for real-time communication
- JWT authentication
- Session management
- Video stream relay
- Command routing
- Rate limiting
- Audit logging

## Prerequisites

- CMake 3.15+
- C++17 compiler (GCC 9+, Clang 10+)
- OpenSSL
- Pistache
- SQLite3
- jwt-cpp

## Building

```bash
mkdir build
cd build
cmake ..
make
```

## Configuration

Edit `config/server.conf`:

```ini
[server]
port=9080
threads=4
jwt_secret=your-secret-key-change-me
jwt_expiry_hours=24

[websocket]
max_connections=1000
ping_interval=30
timeout=300

[security]
tls_cert=/path/to/cert.pem
tls_key=/path/to/key.pem
enable_encryption=true

[database]
path=/var/lib/arcs/devices.db
```

## Running

```bash
./arcs-server [port]
```

Default port: 9080

## API Endpoints

### REST API

- `GET /health` - Health check
- `POST /api/devices/register` - Register new device
- `POST /api/auth/login` - Authenticate device
- `GET /api/sessions` - List active sessions

### WebSocket

- `wss://server:9080/ws` - WebSocket endpoint

## Development

### Project Structure

```
server/
├── src/
│   ├── auth/           # Authentication
│   ├── websocket/      # WebSocket handling
│   ├── router/         # Message routing
│   ├── security/       # Encryption, rate limiting
│   └── logger/         # Audit logging
├── include/            # Public headers
├── config/             # Configuration files
└── CMakeLists.txt
```

### Testing

```bash
make test
```

## Deployment

### Systemd Service

```bash
sudo cp arcs-server.service /etc/systemd/system/
sudo systemctl enable arcs-server
sudo systemctl start arcs-server
```

### Docker

```bash
docker build -t arcs-server .
docker run -p 9080:9080 arcs-server
```

## License

MIT
