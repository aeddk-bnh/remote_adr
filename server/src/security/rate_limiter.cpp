#include "rate_limiter.h"
#include <iostream>

namespace arcs {
namespace security {

bool RateLimiter::allow_touch(const std::string& session_id) {
    return check_and_consume(session_id + ":touch", Limits::TOUCH_MAX, Limits::TOUCH_MAX);
}

bool RateLimiter::allow_text(const std::string& session_id) {
    return check_and_consume(session_id + ":text", Limits::TEXT_MAX, Limits::TEXT_MAX);
}

bool RateLimiter::allow_macro(const std::string& session_id) {
    return check_and_consume(session_id + ":macro", Limits::MACRO_MAX, Limits::MACRO_MAX);
}

bool RateLimiter::allow_ocr(const std::string& session_id) {
    return check_and_consume(session_id + ":ocr", Limits::OCR_MAX, Limits::OCR_MAX);
}

bool RateLimiter::allow_auth(const std::string& device_id) {
    // Auth limit is per minute, so divide refill rate
    return check_and_consume(device_id + ":auth", Limits::AUTH_MAX, Limits::AUTH_MAX / 60.0);
}

void RateLimiter::reset_session(const std::string& session_id) {
    std::lock_guard<std::mutex> lock(mutex_);
    
    // Remove all buckets for this session
    auto it = buckets_.begin();
    while (it != buckets_.end()) {
        if (it->first.find(session_id) == 0) {
            it = buckets_.erase(it);
        } else {
            ++it;
        }
    }
}

bool RateLimiter::check_and_consume(const std::string& key, double max_tokens, double refill_rate) {
    std::lock_guard<std::mutex> lock(mutex_);
    
    auto it = buckets_.find(key);
    if (it == buckets_.end()) {
        // Create new bucket with full tokens
        Bucket bucket;
        bucket.tokens = max_tokens;
        bucket.max_tokens = max_tokens;
        bucket.refill_rate = refill_rate;
        bucket.last_update = std::chrono::steady_clock::now();
        buckets_[key] = bucket;
        it = buckets_.find(key);
    }
    
    Bucket& bucket = it->second;
    
    // Refill tokens
    refill_bucket(bucket);
    
    // Check if we have tokens
    if (bucket.tokens >= 1.0) {
        bucket.tokens -= 1.0;
        return true;
    }
    
    std::cerr << "Rate limit exceeded for: " << key 
              << " (tokens: " << bucket.tokens << ")" << std::endl;
    return false;
}

void RateLimiter::refill_bucket(Bucket& bucket) {
    auto now = std::chrono::steady_clock::now();
    auto elapsed = std::chrono::duration<double>(now - bucket.last_update).count();
    
    // Add tokens based on time elapsed
    bucket.tokens = std::min(bucket.max_tokens, bucket.tokens + elapsed * bucket.refill_rate);
    bucket.last_update = now;
}

} // namespace security
} // namespace arcs
