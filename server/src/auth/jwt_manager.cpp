#include "jwt_manager.h"
#include <jwt-cpp/jwt.h>
#include <iostream>

namespace arcs {
namespace auth {

JWTManager::JWTManager(const std::string& secret, int expiry_hours)
    : secret_(secret), expiry_hours_(expiry_hours) {
}

std::string JWTManager::generate_token(
    const std::string& device_id,
    const std::string& session_id,
    const std::vector<std::string>& permissions)
{
    auto now = std::chrono::system_clock::now();
    auto expires = now + std::chrono::hours(expiry_hours_);
    
    auto token = jwt::create()
        .set_issuer("arcs-server")
        .set_type("JWT")
        .set_subject(device_id)
        .set_issued_at(now)
        .set_expires_at(expires)
        .set_payload_claim("session_id", jwt::claim(session_id))
        .set_payload_claim("device_id", jwt::claim(device_id))
        .sign(jwt::algorithm::hs256{secret_});
    
    return token;
}

std::optional<JWTManager::TokenPayload> JWTManager::validate_token(
    const std::string& token)
{
    try {
        // Check if revoked
        if (is_revoked(token)) {
            return std::nullopt;
        }
        
        // Verify token
        auto verifier = jwt::verify()
            .allow_algorithm(jwt::algorithm::hs256{secret_})
            .with_issuer("arcs-server");
        
        auto decoded = jwt::decode(token);
        verifier.verify(decoded);
        
        // Extract payload
        TokenPayload payload;
        payload.device_id = decoded.get_payload_claim("device_id").as_string();
        payload.session_id = decoded.get_payload_claim("session_id").as_string();
        payload.issued_at = decoded.get_issued_at();
        payload.expires_at = decoded.get_expires_at();
        
        // Check expiration
        if (std::chrono::system_clock::now() > payload.expires_at) {
            return std::nullopt;
        }
        
        return payload;
        
    } catch (const std::exception& e) {
        std::cerr << "Token validation error: " << e.what() << std::endl;
        return std::nullopt;
    }
}

bool JWTManager::is_expired(const std::string& token) {
    try {
        auto decoded = jwt::decode(token);
        return std::chrono::system_clock::now() > decoded.get_expires_at();
    } catch (...) {
        return true;
    }
}

void JWTManager::revoke_token(const std::string& token) {
    std::lock_guard<std::mutex> lock(mutex_);
    revoked_tokens_.insert(token);
}

bool JWTManager::is_revoked(const std::string& token) {
    std::lock_guard<std::mutex> lock(mutex_);
    return revoked_tokens_.find(token) != revoked_tokens_.end();
}

} // namespace auth
} // namespace arcs
