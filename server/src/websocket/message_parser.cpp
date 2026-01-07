#include "message_parser.h"
#include <iostream>

namespace arcs {
namespace websocket {

MessageParser::MessageType MessageParser::get_message_type(const std::string& json_str) {
    try {
        auto msg = parse_json(json_str);
        if (msg.contains("type")) {
            return string_to_type(msg["type"]);
        }
    } catch (...) {
    }
    return MessageType::UNKNOWN;
}

json MessageParser::parse_json(const std::string& json_str) {
    try {
        return json::parse(json_str);
    } catch (const json::parse_error& e) {
        std::cerr << "JSON parse error: " << e.what() << std::endl;
        throw;
    }
}

bool MessageParser::validate_message(const json& msg) {
    // Basic validation: must have "type" field
    if (!msg.contains("type")) {
        return false;
    }
    
    // Additional validation based on type
    std::string type = msg["type"];
    
    if (type == "auth_request") {
        return msg.contains("device_id") && msg.contains("secret");
    }
    else if (type == "join_session") {
        return msg.contains("session_id") && msg.contains("jwt_token");
    }
    else if (type == "touch" || type == "key" || type == "system") {
        return msg.contains("action");
    }
    
    return true;
}

std::string MessageParser::create_auth_response(
    bool success,
    const std::string& session_id,
    const std::string& jwt_token,
    int64_t expires_at)
{
    json response = {
        {"type", "auth_response"},
        {"success", success},
        {"session_id", session_id},
        {"jwt_token", jwt_token},
        {"expires_at", expires_at},
        {"server_time", std::chrono::system_clock::now().time_since_epoch().count()}
    };
    
    return response.dump();
}

std::string MessageParser::create_join_response(
    bool success,
    const json& device_info,
    const json& video_config)
{
    json response = {
        {"type", "join_response"},
        {"success", success},
        {"device_info", device_info},
        {"video_config", video_config}
    };
    
    return response.dump();
}

std::string MessageParser::create_error(
    const std::string& code,
    const std::string& message)
{
    json error = {
        {"type", "error"},
        {"code", code},
        {"message", message}
    };
    
    return error.dump();
}

std::string MessageParser::create_pong() {
    json pong = {
        {"type", "pong"},
        {"timestamp", std::chrono::system_clock::now().time_since_epoch().count()}
    };
    
    return pong.dump();
}

MessageParser::MessageType MessageParser::string_to_type(const std::string& type_str) {
    if (type_str == "auth_request") return MessageType::AUTH_REQUEST;
    if (type_str == "auth_response") return MessageType::AUTH_RESPONSE;
    if (type_str == "join_session") return MessageType::JOIN_SESSION;
    if (type_str == "join_response") return MessageType::JOIN_RESPONSE;
    if (type_str == "touch") return MessageType::TOUCH;
    if (type_str == "key") return MessageType::KEY;
    if (type_str == "system") return MessageType::SYSTEM;
    if (type_str == "app_control") return MessageType::APP_CONTROL;
    if (type_str == "macro") return MessageType::MACRO;
    if (type_str == "ai") return MessageType::AI;
    if (type_str == "ping") return MessageType::PING;
    if (type_str == "pong") return MessageType::PONG;
    if (type_str == "status") return MessageType::STATUS;
    if (type_str == "error") return MessageType::ERROR;
    
    return MessageType::UNKNOWN;
}

} // namespace websocket
} // namespace arcs
