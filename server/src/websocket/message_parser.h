#pragma once

#include <string>
#include <nlohmann/json.hpp>

namespace arcs {
namespace websocket {

using json = nlohmann::json;

/**
 * Message parser
 * Parses and validates JSON messages
 */
class MessageParser {
public:
    enum class MessageType {
        AUTH_REQUEST,
        AUTH_RESPONSE,
        JOIN_SESSION,
        JOIN_RESPONSE,
        TOUCH,
        KEY,
        SYSTEM,
        APP_CONTROL,
        MACRO,
        AI,
        PING,
        PONG,
        STATUS,
        ERROR,
        UNKNOWN
    };
    
    /**
     * Parse message type
     */
    static MessageType get_message_type(const std::string& json_str);
    
    /**
     * Parse JSON
     */
    static json parse_json(const std::string& json_str);
    
    /**
     * Validate message structure
     */
    static bool validate_message(const json& msg);
    
    /**
     * Create auth response
     */
    static std::string create_auth_response(
        bool success,
        const std::string& session_id,
        const std::string& jwt_token,
        int64_t expires_at
    );
    
    /**
     * Create join response
     */
    static std::string create_join_response(
        bool success,
        const json& device_info,
        const json& video_config
    );
    
    /**
     * Create error message
     */
    static std::string create_error(
        const std::string& code,
        const std::string& message
    );
    
    /**
     * Create pong message
     */
    static std::string create_pong();

private:
    static MessageType string_to_type(const std::string& type_str);
};

} // namespace websocket
} // namespace arcs
