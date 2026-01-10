#include <gtest/gtest.h>
#include "../src/router/command_router.h"
#include "../src/security/rate_limiter.h"
#include <memory>

using namespace arcs::router;
using namespace arcs::security;

class CommandRouterTest : public ::testing::Test {
protected:
    void SetUp() override {
        // Set up rate limiter for tests
        auto limiter = std::make_shared<RateLimiter>();
        CommandRouter::set_rate_limiter(limiter);
    }
    
    void TearDown() override {
        // Clear rate limiter
        CommandRouter::set_rate_limiter(nullptr);
    }
};

// Test command validation
TEST_F(CommandRouterTest, ValidateTouchCommand) {
    json cmd = {
        {"type", "touch"},
        {"action", "tap"},
        {"x", 100},
        {"y", 200}
    };
    
    EXPECT_TRUE(CommandRouter::validate_command(cmd));
}

TEST_F(CommandRouterTest, ValidateSwipeCommand) {
    json cmd = {
        {"type", "touch"},
        {"action", "swipe"},
        {"start_x", 100},
        {"start_y", 200},
        {"end_x", 300},
        {"end_y", 400}
    };
    
    EXPECT_TRUE(CommandRouter::validate_command(cmd));
}

TEST_F(CommandRouterTest, InvalidTouchMissingCoordinates) {
    json cmd = {
        {"type", "touch"},
        {"action", "tap"}
        // Missing x, y
    };
    
    EXPECT_FALSE(CommandRouter::validate_command(cmd));
}

TEST_F(CommandRouterTest, ValidateKeyCommand) {
    json cmd = {
        {"type", "key"},
        {"action", "press"},
        {"keycode", 66}  // ENTER
    };
    
    EXPECT_TRUE(CommandRouter::validate_command(cmd));
}

TEST_F(CommandRouterTest, ValidateTextCommand) {
    json cmd = {
        {"type", "key"},
        {"action", "text"},
        {"text", "Hello World"}
    };
    
    EXPECT_TRUE(CommandRouter::validate_command(cmd));
}

TEST_F(CommandRouterTest, InvalidKeyMissingText) {
    json cmd = {
        {"type", "key"},
        {"action", "text"}
        // Missing text
    };
    
    EXPECT_FALSE(CommandRouter::validate_command(cmd));
}

// Test command sanitization
TEST_F(CommandRouterTest, SanitizeRemovesJwtToken) {
    json cmd = {
        {"type", "auth"},
        {"jwt_token", "sensitive_token_here"}
    };
    
    json sanitized = CommandRouter::sanitize_command(cmd);
    
    EXPECT_EQ("***", sanitized["jwt_token"]);
}

TEST_F(CommandRouterTest, SanitizeRemovesSecret) {
    json cmd = {
        {"type", "auth"},
        {"secret", "my_secret_password"}
    };
    
    json sanitized = CommandRouter::sanitize_command(cmd);
    
    EXPECT_EQ("***", sanitized["secret"]);
}

TEST_F(CommandRouterTest, SanitizePreservesNonSensitiveData) {
    json cmd = {
        {"type", "touch"},
        {"action", "tap"},
        {"x", 100},
        {"y", 200}
    };
    
    json sanitized = CommandRouter::sanitize_command(cmd);
    
    EXPECT_EQ("touch", sanitized["type"]);
    EXPECT_EQ("tap", sanitized["action"]);
    EXPECT_EQ(100, sanitized["x"]);
    EXPECT_EQ(200, sanitized["y"]);
}

// Test rate limiting integration
TEST_F(CommandRouterTest, RateLimitTouchCommands) {
    std::string session = "test_session";
    json cmd = {
        {"type", "touch"},
        {"action", "tap"},
        {"x", 100},
        {"y", 200}
    };
    
    // First 100 should pass
    for (int i = 0; i < 100; i++) {
        std::string result = CommandRouter::route_to_device(session, cmd);
        EXPECT_FALSE(result.empty()) << "Command " << i << " should pass";
        
        // Check it's not an error
        json response = json::parse(result);
        EXPECT_NE("error", response.value("type", ""));
    }
    
    // 101st should be rate limited
    std::string result = CommandRouter::route_to_device(session, cmd);
    json error = json::parse(result);
    EXPECT_EQ("error", error["type"]);
    EXPECT_EQ("ERR_RATE_LIMIT", error["code"]);
}

// Test routing returns proper format
TEST_F(CommandRouterTest, RouteToDeviceReturnsJson) {
    std::string session = "test_session";
    json cmd = {
        {"type", "touch"},
        {"action", "tap"},
        {"x", 100},
        {"y", 200}
    };
    
    std::string result = CommandRouter::route_to_device(session, cmd);
    
    EXPECT_FALSE(result.empty());
    
    // Should be valid JSON
    EXPECT_NO_THROW({
        json parsed = json::parse(result);
    });
}

TEST_F(CommandRouterTest, RouteToControllerReturnsJson) {
    std::string session = "test_session";
    json response = {
        {"type", "command_ack"},
        {"success", true}
    };
    
    std::string result = CommandRouter::route_to_controller(session, response);
    
    EXPECT_FALSE(result.empty());
    EXPECT_NO_THROW({
        json parsed = json::parse(result);
    });
}
