#pragma once

#include <string>
#include <memory>
#include <unordered_map>
#include <mutex>
#include <chrono>

namespace arcs {
namespace websocket {

/**
 * Session information
 */
struct Session {
    std::string session_id;
    std::string device_id;
    std::string controller_id;
    std::chrono::system_clock::time_point created_at;
    std::chrono::system_clock::time_point last_activity;
    bool is_active;
    
    bool is_expired() const {
        auto now = std::chrono::system_clock::now();
        auto idle = std::chrono::duration_cast<std::chrono::seconds>(
            now - last_activity
        ).count();
        return idle > 300;  // 5 minute timeout
    }
};

/**
 * Session manager
 * Manages active sessions between devices and controllers
 */
class SessionManager {
public:
    SessionManager();
    
    /**
     * Create new session for device
     */
    std::string create_session(const std::string& device_id);
    
    /**
     * Join session from controller
     */
    bool join_session(const std::string& session_id,
                     const std::string& controller_id);
    
    /**
     * Get session info
     */
    std::shared_ptr<Session> get_session(const std::string& session_id);
    
    /**
     * Update last activity time
     */
    void update_activity(const std::string& session_id);
    
    /**
     * Close session
     */
    bool close_session(const std::string& session_id);
    
    /**
     * Get session by device ID
     */
    std::shared_ptr<Session> get_session_by_device(const std::string& device_id);
    
    /**
     * Get session by controller ID
     */
    std::shared_ptr<Session> get_session_by_controller(const std::string& controller_id);
    
    /**
     * Get active session count
     */
    size_t get_active_count() const;
    
    /**
     * Clean up expired sessions
     */
    void cleanup_expired();

private:
    std::unordered_map<std::string, std::shared_ptr<Session>> sessions_;
    std::mutex mutex_;
};

} // namespace websocket
} // namespace arcs
