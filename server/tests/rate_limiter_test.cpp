#include <gtest/gtest.h>
#include "../src/security/rate_limiter.h"
#include <thread>
#include <chrono>

using namespace arcs::security;

class RateLimiterTest : public ::testing::Test {
protected:
    RateLimiter limiter;
    
    void SetUp() override {
        // Fresh limiter for each test
    }
};

// Test touch command rate limiting
TEST_F(RateLimiterTest, AllowsTouchUnderLimit) {
    std::string session = "session_1";
    
    // Should allow up to 100 touch commands
    for (int i = 0; i < 100; i++) {
        EXPECT_TRUE(limiter.allow_touch(session)) 
            << "Touch command " << i << " should be allowed";
    }
}

TEST_F(RateLimiterTest, BlocksTouchOverLimit) {
    std::string session = "session_1";
    
    // Consume all 100 tokens
    for (int i = 0; i < 100; i++) {
        limiter.allow_touch(session);
    }
    
    // 101st should be blocked
    EXPECT_FALSE(limiter.allow_touch(session));
}

// Test text input rate limiting
TEST_F(RateLimiterTest, AllowsTextUnderLimit) {
    std::string session = "session_1";
    
    // Should allow up to 10 text inputs
    for (int i = 0; i < 10; i++) {
        EXPECT_TRUE(limiter.allow_text(session))
            << "Text input " << i << " should be allowed";
    }
}

TEST_F(RateLimiterTest, BlocksTextOverLimit) {
    std::string session = "session_1";
    
    // Consume all 10 tokens
    for (int i = 0; i < 10; i++) {
        limiter.allow_text(session);
    }
    
    // 11th should be blocked
    EXPECT_FALSE(limiter.allow_text(session));
}

// Test macro rate limiting
TEST_F(RateLimiterTest, AllowsOneMacroPerSecond) {
    std::string session = "session_1";
    
    EXPECT_TRUE(limiter.allow_macro(session));
    EXPECT_FALSE(limiter.allow_macro(session));  // Second should be blocked
}

// Test OCR rate limiting
TEST_F(RateLimiterTest, AllowsTwoOCRPerSecond) {
    std::string session = "session_1";
    
    EXPECT_TRUE(limiter.allow_ocr(session));
    EXPECT_TRUE(limiter.allow_ocr(session));
    EXPECT_FALSE(limiter.allow_ocr(session));  // Third should be blocked
}

// Test token refill
TEST_F(RateLimiterTest, RefillsTokensOverTime) {
    std::string session = "session_1";
    
    // Consume all tokens
    for (int i = 0; i < 10; i++) {
        limiter.allow_text(session);
    }
    EXPECT_FALSE(limiter.allow_text(session));
    
    // Wait for 1 second - should refill ~10 tokens
    std::this_thread::sleep_for(std::chrono::seconds(1));
    
    // Should be allowed again
    EXPECT_TRUE(limiter.allow_text(session));
}

// Test session isolation
TEST_F(RateLimiterTest, SessionIsolation) {
    std::string session1 = "session_1";
    std::string session2 = "session_2";
    
    // Exhaust session1's tokens
    for (int i = 0; i < 10; i++) {
        limiter.allow_text(session1);
    }
    EXPECT_FALSE(limiter.allow_text(session1));
    
    // session2 should still have tokens
    EXPECT_TRUE(limiter.allow_text(session2));
}

// Test auth rate limiting
TEST_F(RateLimiterTest, AllowsFiveAuthPerMinute) {
    std::string device = "device_1";
    
    for (int i = 0; i < 5; i++) {
        EXPECT_TRUE(limiter.allow_auth(device))
            << "Auth attempt " << i << " should be allowed";
    }
    
    // 6th should be blocked
    EXPECT_FALSE(limiter.allow_auth(device));
}

// Test session reset
TEST_F(RateLimiterTest, ResetSessionRestoresTokens) {
    std::string session = "session_1";
    
    // Exhaust tokens
    for (int i = 0; i < 10; i++) {
        limiter.allow_text(session);
    }
    EXPECT_FALSE(limiter.allow_text(session));
    
    // Reset session
    limiter.reset_session(session);
    
    // Should have fresh tokens
    EXPECT_TRUE(limiter.allow_text(session));
}

// Test different command types are independent
TEST_F(RateLimiterTest, CommandTypesAreIndependent) {
    std::string session = "session_1";
    
    // Exhaust text tokens
    for (int i = 0; i < 10; i++) {
        limiter.allow_text(session);
    }
    EXPECT_FALSE(limiter.allow_text(session));
    
    // Touch should still work
    EXPECT_TRUE(limiter.allow_touch(session));
    
    // OCR should still work
    EXPECT_TRUE(limiter.allow_ocr(session));
}

// Test rate limiter limits constants
TEST(RateLimiterLimits, VerifyLimitConstants) {
    EXPECT_EQ(100, RateLimiter::Limits::TOUCH_MAX);
    EXPECT_EQ(10, RateLimiter::Limits::TEXT_MAX);
    EXPECT_EQ(1, RateLimiter::Limits::MACRO_MAX);
    EXPECT_EQ(2, RateLimiter::Limits::OCR_MAX);
    EXPECT_EQ(5, RateLimiter::Limits::AUTH_MAX);
}
