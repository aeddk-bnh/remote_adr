#pragma once

#include <string>
#include <fstream>
#include <chrono>
#include <mutex>

namespace arcs {
namespace logger {

/**
 * Audit logger
 * Records all security-relevant events
 */
class AuditLogger {
public:
    enum class EventType {
        AUTH_SUCCESS,
        AUTH_FAILURE,
        SESSION_START,
        SESSION_END,
        COMMAND_RECEIVED,
        PERMISSION_DENIED,
        RATE_LIMIT_EXCEEDED,
        ENCRYPTION_ERROR,
        SUSPICIOUS_ACTIVITY
    };
    
    enum class LogLevel {
        INFO,
        WARNING,
        ERROR,
        CRITICAL
    };
    
    explicit AuditLogger(const std::string& log_file);
    ~AuditLogger();
    
    /**
     * Log event
     */
    void log(
        EventType event_type,
        LogLevel level,
        const std::string& user_id,
        const std::string& message,
        const std::string& details = ""
    );
    
    /**
     * Log authentication attempt
     */
    void log_auth(
        bool success,
        const std::string& device_id,
        const std::string& ip_address
    );
    
    /**
     * Log session event
     */
    void log_session(
        const std::string& session_id,
        const std::string& device_id,
        bool start
    );
    
    /**
     * Log command
     */
    void log_command(
        const std::string& session_id,
        const std::string& command_type
    );
    
    /**
     * Flush log
     */
    void flush();

private:
    std::string event_type_to_string(EventType type);
    std::string log_level_to_string(LogLevel level);
    std::string get_timestamp();
    
    std::ofstream log_file_;
    std::mutex mutex_;
};

} // namespace logger
} // namespace arcs
