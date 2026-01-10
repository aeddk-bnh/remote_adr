#pragma once

#include <string>
#include <map>
#include <mutex>
#include <chrono>

namespace arcs {
namespace security {

/**
 * Token bucket rate limiter
 * Implements rate limiting per session with configurable limits
 */
class RateLimiter {
public:
    struct Bucket {
        double tokens;
        double max_tokens;
        double refill_rate;  // tokens per second
        std::chrono::steady_clock::time_point last_update;
    };
    
    struct Limits {
        static constexpr int TOUCH_MAX = 100;      // 100 touch commands per second
        static constexpr int TEXT_MAX = 10;        // 10 text inputs per second
        static constexpr int MACRO_MAX = 1;        // 1 macro execution per second
        static constexpr int OCR_MAX = 2;          // 2 OCR requests per second
        static constexpr int AUTH_MAX = 5;         // 5 auth attempts per minute
    };
    
    RateLimiter() = default;
    
    /**
     * Check if touch command is allowed
     */
    bool allow_touch(const std::string& session_id);
    
    /**
     * Check if text input is allowed
     */
    bool allow_text(const std::string& session_id);
    
    /**
     * Check if macro execution is allowed
     */
    bool allow_macro(const std::string& session_id);
    
    /**
     * Check if OCR request is allowed
     */
    bool allow_ocr(const std::string& session_id);
    
    /**
     * Check if auth attempt is allowed (per IP/device)
     */
    bool allow_auth(const std::string& device_id);
    
    /**
     * Reset limits for a session (on disconnect)
     */
    void reset_session(const std::string& session_id);
    
private:
    bool check_and_consume(const std::string& key, double max_tokens, double refill_rate);
    void refill_bucket(Bucket& bucket);
    
    std::map<std::string, Bucket> buckets_;
    std::mutex mutex_;
};

} // namespace security
} // namespace arcs
