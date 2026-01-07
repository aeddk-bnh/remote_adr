#include "command_router.h"
#include <iostream>

namespace arcs {
namespace router {

std::string CommandRouter::route_to_device(
    const std::string& session_id,
    const json& command)
{
    if (!validate_command(command)) {
        std::cerr << "Invalid command for session: " << session_id << std::endl;
        return "";
    }
    
    // Log sanitized command
    auto sanitized = sanitize_command(command);
    std::cout << "Routing to device [" << session_id << "]: " 
              << sanitized.dump() << std::endl;
    
    // Forward command as-is
    return command.dump();
}

std::string CommandRouter::route_to_controller(
    const std::string& session_id,
    const json& response)
{
    std::cout << "Routing to controller [" << session_id << "]: " 
              << response.dump() << std::endl;
    
    return response.dump();
}

bool CommandRouter::validate_command(const json& command) {
    // Must have type
    if (!command.contains("type")) {
        return false;
    }
    
    std::string type = command["type"];
    
    // Validate coordinates for touch commands
    if (type == "touch") {
        if (!command.contains("action")) return false;
        
        std::string action = command["action"];
        if (action == "tap" || action == "long_press") {
            if (!command.contains("x") || !command.contains("y")) {
                return false;
            }
        }
        else if (action == "swipe") {
            if (!command.contains("start_x") || !command.contains("start_y") ||
                !command.contains("end_x") || !command.contains("end_y")) {
                return false;
            }
        }
    }
    
    // Validate key commands
    if (type == "key") {
        if (!command.contains("action")) return false;
        
        std::string action = command["action"];
        if (action == "text" && !command.contains("text")) {
            return false;
        }
        if (action == "press" && !command.contains("keycode")) {
            return false;
        }
    }
    
    return true;
}

json CommandRouter::sanitize_command(const json& command) {
    json sanitized = command;
    
    // Remove sensitive fields
    if (sanitized.contains("jwt_token")) {
        sanitized["jwt_token"] = "***";
    }
    if (sanitized.contains("secret")) {
        sanitized["secret"] = "***";
    }
    if (sanitized.contains("password")) {
        sanitized["password"] = "***";
    }
    
    return sanitized;
}

} // namespace router
} // namespace arcs
