# ARCS Security Design

## Security Principles

1. **Defense in Depth**: Multiple security layers
2. **Zero Trust**: Verify all requests
3. **Least Privilege**: Minimal permissions
4. **Encryption Everywhere**: All data encrypted
5. **Audit Everything**: Comprehensive logging

## Threat Model

### Threats

1. **Network Eavesdropping**: Attacker intercepts traffic
2. **Man-in-the-Middle**: Attacker modifies traffic
3. **Unauthorized Access**: Attacker gains device control
4. **Replay Attacks**: Attacker replays captured commands
5. **DoS/DDoS**: Overload server or client
6. **Data Exfiltration**: Sensitive data leaked
7. **Privilege Escalation**: Bypass security boundaries

### Assets to Protect

- Screen content (visual data)
- User input (touch, keyboard)
- Device information
- Session credentials
- Automation macros
- AI model outputs

## Security Layers

### Layer 1: Transport Security (TLS)

**TLS 1.3 Configuration**

```
Minimum Version: TLS 1.3
Cipher Suites:
  - TLS_AES_256_GCM_SHA384
  - TLS_CHACHA20_POLY1305_SHA256
  - TLS_AES_128_GCM_SHA256

Certificate Requirements:
  - 2048-bit RSA or 256-bit ECC
  - Valid CA-signed certificate
  - Certificate pinning (optional, mobile apps)
```

**Implementation:**
- Server must use valid TLS certificate
- Client validates certificate chain
- No fallback to TLS 1.2 or below
- HSTS enabled for web controller

### Layer 2: Authentication

#### Device Registration

**First-Time Setup:**

1. User generates device secret on Android
2. Device registers with server (manual or QR code)
3. Server stores: `device_id → device_secret`
4. Device stores secret in Android Keystore

**Android Keystore Usage:**

```kotlin
// Generate or retrieve key
val keyGenerator = KeyGenerator.getInstance(
    KeyProperties.KEY_ALGORITHM_AES,
    "AndroidKeyStore"
)
val keyGenSpec = KeyGenParameterSpec.Builder(
    "arcs_device_key",
    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
)
    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
    .setUserAuthenticationRequired(false)
    .build()
    
keyGenerator.init(keyGenSpec)
keyGenerator.generateKey()
```

#### JWT Authentication

**Token Structure:**

```json
{
  "header": {
    "alg": "HS256",
    "typ": "JWT"
  },
  "payload": {
    "iss": "arcs-server",
    "sub": "device-id-12345",
    "iat": 1704672000,
    "exp": 1704758400,
    "session_id": "uuid-v4",
    "device_info": {
      "model": "Pixel 7",
      "android_version": "14"
    },
    "permissions": [
      "screen_capture",
      "input_control",
      "app_control"
    ]
  },
  "signature": "..."
}
```

**Token Lifecycle:**
- Issued on successful authentication
- Valid for 24 hours
- Refresh before expiry
- Revocable on server side
- Single-use session binding

**Token Validation:**
```cpp
bool validateJWT(const std::string& token) {
    // 1. Parse token
    // 2. Verify signature
    // 3. Check expiration
    // 4. Verify issuer
    // 5. Check revocation list
    // 6. Validate claims
    return valid;
}
```

### Layer 3: Payload Encryption

#### AES-256-GCM Encryption

**Key Derivation:**

```python
import hashlib
import hmac

def derive_session_key(jwt_token: str, device_id: str) -> bytes:
    """Derive 256-bit encryption key from JWT"""
    return hashlib.pbkdf2_hmac(
        hash_name='sha256',
        password=jwt_token.encode(),
        salt=device_id.encode(),
        iterations=100000,
        dklen=32  # 256 bits
    )
```

**Encryption Process:**

```kotlin
fun encryptPayload(plaintext: ByteArray, key: SecretKey): EncryptedData {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    val iv = ByteArray(12)  // 96-bit IV
    SecureRandom().nextBytes(iv)
    
    cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
    val ciphertext = cipher.doFinal(plaintext)
    
    // ciphertext contains: [encrypted data][16-byte auth tag]
    return EncryptedData(iv, ciphertext)
}

data class EncryptedData(
    val iv: ByteArray,
    val ciphertext: ByteArray  // includes auth tag
)
```

**Message Format:**

```
[IV: 12 bytes][Encrypted Payload][Auth Tag: 16 bytes]
```

**Why GCM?**
- Authenticated encryption (integrity + confidentiality)
- Fast (hardware acceleration)
- Prevents tampering
- Nonce misuse-resistant (if used correctly)

### Layer 4: Authorization

#### Permission Model

**Android Permissions:**
```xml
<!-- Minimal required permissions -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

<!-- Runtime permissions -->
<!-- MediaProjection: Granted via user consent dialog -->
<!-- Accessibility: User enables in Settings -->
```

**Role-Based Access Control (Server):**

```json
{
  "roles": {
    "device": {
      "permissions": ["stream_screen", "receive_commands"]
    },
    "controller_viewer": {
      "permissions": ["view_stream"]
    },
    "controller_full": {
      "permissions": ["view_stream", "send_commands", "app_control"]
    },
    "admin": {
      "permissions": ["all", "manage_devices", "view_logs"]
    }
  }
}
```

**Command Authorization:**

```cpp
bool authorizeCommand(const User& user, const Command& cmd) {
    // Check if user's role allows this command type
    const auto& permissions = getRolePermissions(user.role);
    return permissions.contains(cmd.requiredPermission());
}
```

### Layer 5: Input Validation

**Command Validation:**

```kotlin
fun validateTouchCommand(cmd: TouchCommand): Result<Unit> {
    // Validate coordinates
    if (cmd.x !in 0..screenWidth || cmd.y !in 0..screenHeight) {
        return Result.failure(InvalidCoordinatesException())
    }
    
    // Validate action type
    if (cmd.action !in listOf("tap", "swipe", "long_press", "pinch")) {
        return Result.failure(InvalidActionException())
    }
    
    // Validate duration
    if (cmd.duration != null && cmd.duration !in 0..10000) {
        return Result.failure(InvalidDurationException())
    }
    
    return Result.success(Unit)
}
```

**JSON Schema Validation:**

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["type", "action"],
  "properties": {
    "type": {
      "type": "string",
      "enum": ["touch", "key", "app_control", "system"]
    },
    "x": {
      "type": "integer",
      "minimum": 0,
      "maximum": 10000
    },
    "y": {
      "type": "integer",
      "minimum": 0,
      "maximum": 10000
    }
  }
}
```

### Layer 6: Rate Limiting

**Token Bucket Algorithm:**

```cpp
class RateLimiter {
private:
    size_t capacity;
    size_t tokens;
    std::chrono::steady_clock::time_point lastRefill;
    size_t refillRate;  // tokens per second
    
public:
    bool allowRequest() {
        refillTokens();
        if (tokens > 0) {
            tokens--;
            return true;
        }
        return false;
    }
    
    void refillTokens() {
        auto now = std::chrono::steady_clock::now();
        auto elapsed = std::chrono::duration_cast<std::chrono::seconds>(
            now - lastRefill
        ).count();
        
        tokens = std::min(capacity, tokens + elapsed * refillRate);
        lastRefill = now;
    }
};
```

**Rate Limits:**

| Operation | Limit | Window |
|-----------|-------|--------|
| Touch events | 100 | per second |
| Text input | 10 | per second |
| App control | 5 | per second |
| Macro execution | 1 | per second |
| OCR requests | 5 | per second |
| Authentication | 5 | per minute |
| Failed auth | 3 | per 15 minutes |

### Layer 7: Session Security

**Session Management:**

```cpp
struct Session {
    std::string sessionId;
    std::string deviceId;
    std::string controllerId;
    std::chrono::steady_clock::time_point createdAt;
    std::chrono::steady_clock::time_point lastActivity;
    bool isActive;
    
    bool isExpired() const {
        auto now = std::chrono::steady_clock::now();
        auto idle = std::chrono::duration_cast<std::chrono::seconds>(
            now - lastActivity
        ).count();
        return idle > 300;  // 5 minute timeout
    }
};
```

**Session Lifecycle:**
1. Created on successful auth
2. Heartbeat every 30 seconds
3. Timeout after 5 minutes inactivity
4. Explicit disconnect on close
5. Cleanup of stale sessions

**Session Hijacking Prevention:**
- Session ID bound to device/controller
- IP address validation (optional)
- User-agent validation
- Concurrent session detection

### Layer 8: Audit Logging

**What to Log:**

```cpp
enum class AuditEventType {
    AUTH_SUCCESS,
    AUTH_FAILURE,
    SESSION_START,
    SESSION_END,
    COMMAND_RECEIVED,
    PERMISSION_DENIED,
    RATE_LIMIT_EXCEEDED,
    ENCRYPTION_ERROR,
    SUSPICIOUS_ACTIVITY
};

struct AuditLog {
    std::chrono::system_clock::time_point timestamp;
    AuditEventType eventType;
    std::string userId;
    std::string deviceId;
    std::string ipAddress;
    std::string details;
    LogLevel severity;
};
```

**Log Storage:**
- Encrypted at rest
- Tamper-evident (append-only)
- Retention: 90 days
- Regular review for anomalies

**What NOT to Log:**
- Passwords or secrets
- Full screen content
- Personal data (comply with GDPR)
- JWT tokens in plaintext

## Attack Mitigation

### Replay Attack Prevention

**Timestamp Validation:**

```kotlin
fun validateTimestamp(msgTimestamp: Long): Boolean {
    val now = System.currentTimeMillis()
    val diff = abs(now - msgTimestamp)
    return diff < 5000  // 5 second tolerance
}
```

**Nonce Tracking:**

```cpp
class NonceTracker {
    std::unordered_set<std::string> usedNonces;
    std::chrono::steady_clock::time_point cleanupTime;
    
    bool isNonceUsed(const std::string& nonce) {
        cleanupExpiredNonces();
        if (usedNonces.contains(nonce)) {
            return true;
        }
        usedNonces.insert(nonce);
        return false;
    }
};
```

### Man-in-the-Middle Prevention

- TLS with certificate validation
- Certificate pinning (mobile apps)
- HSTS for web controller
- No mixed content (HTTPS only)

### DoS Prevention

- Connection limits per IP
- Rate limiting (see Layer 6)
- Request size limits
- Timeout policies
- Resource quotas

**Connection Limits:**

```cpp
constexpr size_t MAX_CONNECTIONS_PER_IP = 5;
constexpr size_t MAX_TOTAL_CONNECTIONS = 1000;
constexpr size_t MAX_MESSAGE_SIZE = 10 * 1024 * 1024;  // 10 MB
```

### Data Exfiltration Prevention

- Encrypt all data in transit and at rest
- Minimize logged sensitive data
- Access control on server logs
- No debug endpoints in production
- Disable unnecessary services

## Secure Development Practices

### Code Security

- Input validation on all boundaries
- No hardcoded secrets
- Use parameterized queries (if using SQL)
- Safe deserialization
- Memory-safe languages where possible

### Dependency Management

- Pin dependency versions
- Regular security updates
- Vulnerability scanning
- Minimal dependencies
- Audit third-party libraries

### Build Security

- Reproducible builds
- Code signing
- Obfuscation (Android APK)
- Strip debug symbols
- Enable compiler security flags

**Android ProGuard:**

```
-keepattributes *Annotation*
-dontwarn **
-optimizationpasses 5
-repackageclasses ''
-allowaccessmodification
```

## Compliance & Privacy

### Data Minimization

- Collect only necessary data
- Don't store screen content
- Anonymize logs where possible
- Clear session data after disconnect

### User Consent

- Explicit MediaProjection permission
- Clear privacy policy
- Opt-in for analytics
- Right to data deletion

### GDPR Compliance

- Data access requests
- Data deletion requests
- Data portability
- Breach notification

## Security Checklist

### Android Client
- [ ] Use Android Keystore for secrets
- [ ] Validate TLS certificates
- [ ] Encrypt all outbound data
- [ ] Validate all server responses
- [ ] Implement rate limiting
- [ ] Clear sensitive data from memory
- [ ] Use ProGuard obfuscation
- [ ] Enable R8 full mode

### Server
- [ ] TLS 1.3 with strong ciphers
- [ ] JWT signature validation
- [ ] Session timeout enforcement
- [ ] Rate limiting per IP/session
- [ ] Audit logging enabled
- [ ] Input validation
- [ ] CORS properly configured
- [ ] Security headers set

### Controllers
- [ ] Validate server certificate
- [ ] Encrypt commands
- [ ] Sanitize user input
- [ ] XSS prevention (web)
- [ ] CSP headers (web)
- [ ] Secure storage of credentials

## Incident Response

### Detection
- Monitor audit logs
- Alert on suspicious patterns
- Track failed auth attempts
- Monitor resource usage

### Response
1. Identify scope of breach
2. Revoke compromised sessions
3. Block malicious IPs
4. Rotate secrets if needed
5. Notify affected users
6. Document incident

## Security Testing

### Penetration Testing
- Annual third-party audit
- Automated vulnerability scanning
- Fuzzing of protocol parsers
- SSL/TLS testing (testssl.sh)

### Security Reviews
- Code reviews with security focus
- Threat modeling updates
- Dependency audits
- Configuration reviews

## Future Enhancements

- Hardware security module (HSM) support
- Mutual TLS (mTLS)
- FIDO2/WebAuthn support
- End-to-end encryption (P2P mode)
- Quantum-resistant algorithms
