#include <iostream>
#include <cassert>
#include <thread>
#include <chrono>
#include <vector>
#include "../src/security/rate_limiter.cpp"
#include "../src/auth/device_registry.cpp"

// Helpers needed for standalone compilation if not linking objects
// We include .cpp files directly for this simple test harness 
// (Not best practice but effective for environment-agnostic testing)

using namespace arcs::security;
using namespace arcs::auth;

void test_rate_limiter() {
    std::cout << "Testing RateLimiter..." << std::endl;
    RateLimiter limiter;
    std::string session = "session_test";

    // Test 1: Allow touch under limit
    for(int i=0; i<100; i++) {
        assert(limiter.allow_touch(session) == true);
    }
    std::cout << "  [PASS] Allow touch under limit" << std::endl;

    // Test 2: Block touch over limit
    assert(limiter.allow_touch(session) == false);
    std::cout << "  [PASS] Block touch over limit" << std::endl;

    // Test 3: Allow macro (1 per sec)
    assert(limiter.allow_macro(session) == true);
    assert(limiter.allow_macro(session) == false);
    std::cout << "  [PASS] Macro rate limiting" << std::endl;

    // Test 4: Refill tokens
    std::cout << "  Waiting for token refill (1s)..." << std::endl;
    std::this_thread::sleep_for(std::chrono::milliseconds(1100));
    assert(limiter.allow_macro(session) == true);
    std::cout << "  [PASS] Token refill" << std::endl;
}

void test_device_registry() {
    std::cout << "Testing DeviceRegistry..." << std::endl;
    DeviceRegistry registry;

    // Test 1: Register and Authenticate
    assert(registry.register_device("dev1", "sec1", "Pixel"));
    assert(registry.authenticate("dev1", "sec1") == true);
    std::cout << "  [PASS] Register and Authenticate" << std::endl;

    // Test 2: Wrong secret
    assert(registry.authenticate("dev1", "wrong") == false);
    std::cout << "  [PASS] Wrong secret rejection" << std::endl;

    // Test 3: Duplicate device ID
    assert(registry.register_device("dev1", "sec2", "Pixel2") == false); // Should fail? Depends on implementation
    // implementation check: if (devices_.find(device_id) != devices_.end()) return false;
    // So yes.
    std::cout << "  [PASS] Duplicate registration prevention" << std::endl;
}

int main() {
    std::cout << "=== ARCS Server Integrity Tests ===" << std::endl;
    
    try {
        test_rate_limiter();
        test_device_registry();
        
        std::cout << "\nALL TESTS PASSED!" << std::endl;
        return 0;
    } catch (const std::exception& e) {
        std::cerr << "Test failed with exception: " << e.what() << std::endl;
        return 1;
    }
}
