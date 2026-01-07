#pragma once

#include <string>
#include <memory>
#include <nlohmann/json.hpp>

namespace arcs {
namespace router {

using json = nlohmann::json;

/**
 * Routes commands between controllers and devices
 */
class CommandRouter {
public:
    /**
     * Route command from controller to device
     * @return Routed message or empty string if routing failed
     */
    static std::string route_to_device(
        const std::string& session_id,
        const json& command
    );
    
    /**
     * Route response from device to controller
     * @return Routed message or empty string if routing failed
     */
    static std::string route_to_controller(
        const std::string& session_id,
        const json& response
    );
    
    /**
     * Validate command
     */
    static bool validate_command(const json& command);
    
    /**
     * Sanitize command (remove sensitive data for logging)
     */
    static json sanitize_command(const json& command);
};

} // namespace router
} // namespace arcs
