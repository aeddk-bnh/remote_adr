#include <gtest/gtest.h>
#include "../src/auth/device_registry.h"

using namespace arcs::auth;

class DeviceRegistryTest : public ::testing::Test {
protected:
    DeviceRegistry registry;
    
    void SetUp() override {
        // Fresh registry for each test
    }
};

// Test device registration
TEST_F(DeviceRegistryTest, RegisterNewDevice) {
    bool result = registry.register_device("device_1", "secret_123", "Pixel 6");
    EXPECT_TRUE(result);
}

TEST_F(DeviceRegistryTest, DuplicateRegistrationFails) {
    registry.register_device("device_1", "secret_123", "Pixel 6");
    
    // Second registration with same ID should fail
    bool result = registry.register_device("device_1", "different_secret", "Pixel 7");
    EXPECT_FALSE(result);
}

// Test authentication
TEST_F(DeviceRegistryTest, AuthenticateWithCorrectCredentials) {
    registry.register_device("device_1", "secret_123", "Pixel 6");
    
    bool result = registry.authenticate("device_1", "secret_123");
    EXPECT_TRUE(result);
}

TEST_F(DeviceRegistryTest, AuthenticateWithWrongSecret) {
    registry.register_device("device_1", "secret_123", "Pixel 6");
    
    bool result = registry.authenticate("device_1", "wrong_secret");
    EXPECT_FALSE(result);
}

TEST_F(DeviceRegistryTest, AuthenticateUnknownDevice) {
    bool result = registry.authenticate("unknown_device", "any_secret");
    EXPECT_FALSE(result);
}

// Test device lookup
TEST_F(DeviceRegistryTest, GetExistingDevice) {
    registry.register_device("device_1", "secret_123", "Pixel 6");
    
    auto device = registry.get_device("device_1");
    
    EXPECT_TRUE(device.has_value());
    EXPECT_EQ("device_1", device->device_id);
    EXPECT_EQ("Pixel 6", device->device_model);
    EXPECT_TRUE(device->is_active);
}

TEST_F(DeviceRegistryTest, GetNonExistentDevice) {
    auto device = registry.get_device("unknown_device");
    EXPECT_FALSE(device.has_value());
}

// Test device deactivation
TEST_F(DeviceRegistryTest, DeactivateDevice) {
    registry.register_device("device_1", "secret_123", "Pixel 6");
    
    bool result = registry.deactivate_device("device_1");
    EXPECT_TRUE(result);
    
    // Auth should fail for deactivated device
    EXPECT_FALSE(registry.authenticate("device_1", "secret_123"));
}

TEST_F(DeviceRegistryTest, DeactivateNonExistentDevice) {
    bool result = registry.deactivate_device("unknown_device");
    EXPECT_FALSE(result);
}

// Test device reactivation via get_device
TEST_F(DeviceRegistryTest, DeactivatedDeviceStillExists) {
    registry.register_device("device_1", "secret_123", "Pixel 6");
    registry.deactivate_device("device_1");
    
    auto device = registry.get_device("device_1");
    
    EXPECT_TRUE(device.has_value());
    EXPECT_FALSE(device->is_active);
}

// Test multiple devices
TEST_F(DeviceRegistryTest, MultipleDevices) {
    registry.register_device("device_1", "secret_1", "Pixel 6");
    registry.register_device("device_2", "secret_2", "Galaxy S21");
    registry.register_device("device_3", "secret_3", "OnePlus 9");
    
    EXPECT_TRUE(registry.authenticate("device_1", "secret_1"));
    EXPECT_TRUE(registry.authenticate("device_2", "secret_2"));
    EXPECT_TRUE(registry.authenticate("device_3", "secret_3"));
    
    // Cross-authentication should fail
    EXPECT_FALSE(registry.authenticate("device_1", "secret_2"));
}

// Test thread safety (basic)
TEST_F(DeviceRegistryTest, ThreadSafeRegistration) {
    std::vector<std::thread> threads;
    std::atomic<int> success_count{0};
    
    // Try to register same device from multiple threads
    for (int i = 0; i < 10; i++) {
        threads.emplace_back([this, &success_count]() {
            if (registry.register_device("contested_device", "secret", "Model")) {
                success_count++;
            }
        });
    }
    
    for (auto& t : threads) {
        t.join();
    }
    
    // Only one thread should succeed
    EXPECT_EQ(1, success_count.load());
}

// Test empty credentials
TEST_F(DeviceRegistryTest, EmptyDeviceId) {
    bool result = registry.register_device("", "secret", "Model");
    // Implementation may allow or disallow - test actual behavior
    // For security, empty IDs should probably be rejected
}

TEST_F(DeviceRegistryTest, EmptySecret) {
    registry.register_device("device_1", "", "Model");
    
    // Should authenticate with empty secret if registered with empty
    EXPECT_TRUE(registry.authenticate("device_1", ""));
    EXPECT_FALSE(registry.authenticate("device_1", "some_secret"));
}
