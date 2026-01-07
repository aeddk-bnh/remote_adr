#!/bin/bash

###############################################################################
# ARCS Complete Flow Test Script
# Builds and runs all components: Server, Web Controller, Android Client
###############################################################################

set -e  # Exit on error

echo "======================================================================"
echo "ARCS - Android Remote Control System"
echo "Complete Flow Build & Run Script"
echo "======================================================================"

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="$PROJECT_ROOT/build"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Step counter
STEP=1

print_step() {
    echo -e "\n${GREEN}[Step $STEP]${NC} $1"
    STEP=$((STEP + 1))
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

###############################################################################
# 1. Build Server
###############################################################################
print_step "Building C++ Server..."

cd "$PROJECT_ROOT/server"
mkdir -p build
cd build

cmake .. \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_INSTALL_PREFIX="$BUILD_DIR/server"

make -j$(nproc)
make install

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓${NC} Server built successfully"
else
    print_error "Server build failed"
    exit 1
fi

###############################################################################
# 2. Build Web Controller
###############################################################################
print_step "Building Web Controller..."

cd "$PROJECT_ROOT/web-controller"

# Install dependencies
if [ ! -d "node_modules" ]; then
    echo "Installing npm dependencies..."
    npm install
fi

# Build for production
npm run build

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓${NC} Web controller built successfully"
    # Copy build output
    mkdir -p "$BUILD_DIR/web"
    cp -r dist/* "$BUILD_DIR/web/"
else
    print_error "Web controller build failed"
    exit 1
fi

###############################################################################
# 3. Build Android Client
###############################################################################
print_step "Building Android Client..."

cd "$PROJECT_ROOT/android-client"

# Check for Android SDK
if [ -z "$ANDROID_HOME" ]; then
    print_warning "ANDROID_HOME not set. Trying to detect..."
    if [ -d "$HOME/Android/Sdk" ]; then
        export ANDROID_HOME="$HOME/Android/Sdk"
        echo "Found Android SDK at: $ANDROID_HOME"
    else
        print_error "Android SDK not found. Please set ANDROID_HOME"
        exit 1
    fi
fi

# Build debug APK
./gradlew assembleDebug

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓${NC} Android client built successfully"
    APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
    cp "$APK_PATH" "$BUILD_DIR/arcs-client.apk"
    echo "APK saved to: $BUILD_DIR/arcs-client.apk"
else
    print_error "Android client build failed"
    exit 1
fi

###############################################################################
# 4. Generate Configuration
###############################################################################
print_step "Generating runtime configuration..."

cd "$BUILD_DIR"

cat > server-config.env << EOF
# ARCS Server Configuration
JWT_SECRET=$(openssl rand -hex 32)
LOG_LEVEL=info
MAX_SESSIONS=100
DB_PATH=./arcs.db
TLS_ENABLED=true
TLS_CERT_PATH=./server.crt
TLS_KEY_PATH=./server.key
EOF

echo -e "${GREEN}✓${NC} Configuration generated: server-config.env"

###############################################################################
# 5. Generate Self-Signed Certificate (for testing)
###############################################################################
print_step "Generating self-signed certificate..."

if [ ! -f "server.key" ]; then
    openssl req -x509 -newkey rsa:4096 -keyout server.key -out server.crt \
        -days 365 -nodes \
        -subj "/C=US/ST=State/L=City/O=ARCS/CN=localhost"
    
    echo -e "${GREEN}✓${NC} Certificate generated (valid for 365 days)"
else
    echo "Certificate already exists, skipping..."
fi

###############################################################################
# 6. Build Summary
###############################################################################
print_step "Build Summary"

echo ""
echo "======================================================================"
echo "Build Complete!"
echo "======================================================================"
echo ""
echo "📦 Build Artifacts:"
echo "  Server:      $BUILD_DIR/server/bin/arcs_server"
echo "  Web:         $BUILD_DIR/web/ (static files)"
echo "  Android APK: $BUILD_DIR/arcs-client.apk"
echo ""
echo "🔧 Configuration:"
echo "  Server config: $BUILD_DIR/server-config.env"
echo "  TLS cert:      $BUILD_DIR/server.crt"
echo "  TLS key:       $BUILD_DIR/server.key"
echo ""
echo "======================================================================"
echo "Next Steps:"
echo "======================================================================"
echo ""
echo "1. Start the server:"
echo "   cd $BUILD_DIR"
echo "   source server-config.env"
echo "   ./server/bin/arcs_server --host 0.0.0.0 --port 8080"
echo ""
echo "2. Serve the web controller:"
echo "   cd $BUILD_DIR/web"
echo "   python3 -m http.server 3000"
echo "   # Or use nginx/caddy for production"
echo ""
echo "3. Install Android app:"
echo "   adb install $BUILD_DIR/arcs-client.apk"
echo "   # Then open the app and configure server URL"
echo ""
echo "4. Connect to server:"
echo "   - Android: Enter server URL (wss://YOUR_IP:8080)"
echo "   - Web: Open http://localhost:3000 and enter session ID"
echo ""
echo "======================================================================"
echo ""

print_warning "This is a development build. For production:"
print_warning "  - Use Docker Compose (see DEPLOYMENT.md)"
print_warning "  - Configure proper SSL/TLS certificates"
print_warning "  - Set up nginx reverse proxy"
print_warning "  - Enable monitoring and logging"

echo ""
echo -e "${GREEN}Done!${NC}"
