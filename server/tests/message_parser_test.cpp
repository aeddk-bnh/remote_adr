#include <iostream>
#include <cassert>
#include <string>
#include "../src/websocket/message_parser.cpp"

// Standalone test for MessageParser
// Note: Requires nlohmann/json available in include path
// Since environment has issues, this code serves as Logic Verification Source.

using namespace arcs::websocket;
using json = nlohmann::json;

void test_get_message_type() {
    std::cout << "Testing get_message_type..." << std::endl;
    
    // Valid types
    assert(MessageParser::get_message_type(R"({"type": "touch"})") == MessageParser::MessageType::TOUCH);
    assert(MessageParser::get_message_type(R"({"type": "auth_request"})") == MessageParser::MessageType::AUTH_REQUEST);
    assert(MessageParser::get_message_type(R"({"type": "ping"})") == MessageParser::MessageType::PING);
    
    // Unknown/Invalid
    assert(MessageParser::get_message_type(R"({"type": "invalid_type"})") == MessageParser::MessageType::UNKNOWN);
    assert(MessageParser::get_message_type(R"({"not_type": "val"})") == MessageParser::MessageType::UNKNOWN);
    assert(MessageParser::get_message_type("not json") == MessageParser::MessageType::UNKNOWN);
    
    std::cout << "  [PASS] validation" << std::endl;
}

void test_validate_message() {
    std::cout << "Testing validate_message..." << std::endl;
    
    // Auth Request validation
    json auth = {{"type", "auth_request"}, {"device_id", "d1"}, {"secret", "s1"}};
    assert(MessageParser::validate_message(auth) == true);
    
    json auth_missing = {{"type", "auth_request"}, {"device_id", "d1"}}; // missing secret
    assert(MessageParser::validate_message(auth_missing) == false);
    
    // Join Session validation
    json join = {{"type", "join_session"}, {"session_id", "sess1"}, {"jwt_token", "jwt"}};
    assert(MessageParser::validate_message(join) == true);
    
    // Touch validation
    json touch = {{"type", "touch"}, {"action", "tap"}};
    assert(MessageParser::validate_message(touch) == true);
    
    json touch_invalid = {{"type", "touch"}}; // missing action
    assert(MessageParser::validate_message(touch_invalid) == false);
    
    std::cout << "  [PASS] structural validation" << std::endl;
}

void test_creation_methods() {
    std::cout << "Testing creation methods..." << std::endl;
    
    // Auth Response
    std::string auth_res = MessageParser::create_auth_response(true, "sess1", "token", 123456);
    json j_auth = json::parse(auth_res);
    assert(j_auth["type"] == "auth_response");
    assert(j_auth["success"] == true);
    assert(j_auth["session_id"] == "sess1");
    
    // Error
    std::string err = MessageParser::create_error("404", "Not Found");
    json j_err = json::parse(err);
    assert(j_err["type"] == "error");
    assert(j_err["code"] == "404");
    
    std::cout << "  [PASS] response creation" << std::endl;
}

int main() {
    std::cout << "=== MessageParser Tests ===" << std::endl;
    try {
        test_get_message_type();
        test_validate_message();
        test_creation_methods();
        std::cout << "ALL PARSER TESTS PASSED!" << std::endl;
        return 0;
    } catch (const std::exception& e) {
        std::cerr << "Test Exception: " << e.what() << std::endl;
        return 1;
    }
}
