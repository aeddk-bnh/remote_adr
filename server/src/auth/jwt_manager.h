#pragma once

#include <string>
#include <chrono>
#include <memory>
#include <optional>

namespace arcs {
namespace auth {

/**
 * JWT token manager
 * Handles token generation, validation, and parsing
 */
class JWTManager {
public:
    struct TokenPayload {
        std::string device_id;
        std::string session_id;
        std::chrono::system_clock::time_point issued_at;
        std::chrono::system_clock::time_point expires_at;
        std::vector<std::string> permissions;
    };
    
    /**
     * Constructor
     * @param secret Secret key for signing
     * @param expiry_hours Token validity in hours
     */
    explicit JWTManager(const std::string& secret, int expiry_hours = 24);
    
    /**
     * Generate JWT token
     */
    std::string generate_token(const std::string& device_id,
                               const std::string& session_id,
                               const std::vector<std::string>& permissions);
    
    /**
     * Validate and parse token
     * @return Payload if valid, nullopt otherwise
     */
    std::optional<TokenPayload> validate_token(const std::string& token);
    
    /**
     * Check if token is expired
     */
    bool is_expired(const std::string& token);
    
    /**
     * Revoke token (add to blacklist)
     */
    void revoke_token(const std::string& token);
    
    /**
     * Check if token is revoked
     */
    bool is_revoked(const std::string& token);

private:
    std::string secret_;
    int expiry_hours_;
    std::unordered_set<std::string> revoked_tokens_;
    std::mutex mutex_;
};

} // namespace auth
} // namespace arcs
