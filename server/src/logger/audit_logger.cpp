#include "audit_logger.h"
#include <iostream>
#include <iomanip>
#include <sstream>

namespace arcs {
namespace logger {

AuditLogger::AuditLogger(const std::string& log_file) {
    log_file_.open(log_file, std::ios::app);
    if (!log_file_.is_open()) {
        std::cerr << "Failed to open audit log: " << log_file << std::endl;
    }
}

AuditLogger::~AuditLogger() {
    flush();
    if (log_file_.is_open()) {
        log_file_.close();
    }
}

void AuditLogger::log(
    EventType event_type,
    LogLevel level,
    const std::string& user_id,
    const std::string& message,
    const std::string& details)
{
    std::lock_guard<std::mutex> lock(mutex_);
    
    if (!log_file_.is_open()) {
        return;
    }
    
    std::string log_entry = 
        get_timestamp() + " | " +
        log_level_to_string(level) + " | " +
        event_type_to_string(event_type) + " | " +
        "user=" + user_id + " | " +
        message;
    
    if (!details.empty()) {
        log_entry += " | " + details;
    }
    
    log_file_ << log_entry << std::endl;
    
    // Also print to console for critical events
    if (level == LogLevel::CRITICAL || level == LogLevel::ERROR) {
        std::cout << log_entry << std::endl;
    }
}

void AuditLogger::log_auth(
    bool success,
    const std::string& device_id,
    const std::string& ip_address)
{
    log(
        success ? EventType::AUTH_SUCCESS : EventType::AUTH_FAILURE,
        success ? LogLevel::INFO : LogLevel::WARNING,
        device_id,
        success ? "Authentication successful" : "Authentication failed",
        "ip=" + ip_address
    );
}

void AuditLogger::log_session(
    const std::string& session_id,
    const std::string& device_id,
    bool start)
{
    log(
        start ? EventType::SESSION_START : EventType::SESSION_END,
        LogLevel::INFO,
        device_id,
        start ? "Session started" : "Session ended",
        "session_id=" + session_id
    );
}

void AuditLogger::log_command(
    const std::string& session_id,
    const std::string& command_type)
{
    log(
        EventType::COMMAND_RECEIVED,
        LogLevel::INFO,
        session_id,
        "Command: " + command_type,
        ""
    );
}

void AuditLogger::flush() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (log_file_.is_open()) {
        log_file_.flush();
    }
}

std::string AuditLogger::event_type_to_string(EventType type) {
    switch (type) {
        case EventType::AUTH_SUCCESS: return "AUTH_SUCCESS";
        case EventType::AUTH_FAILURE: return "AUTH_FAILURE";
        case EventType::SESSION_START: return "SESSION_START";
        case EventType::SESSION_END: return "SESSION_END";
        case EventType::COMMAND_RECEIVED: return "COMMAND_RECEIVED";
        case EventType::PERMISSION_DENIED: return "PERMISSION_DENIED";
        case EventType::RATE_LIMIT_EXCEEDED: return "RATE_LIMIT_EXCEEDED";
        case EventType::ENCRYPTION_ERROR: return "ENCRYPTION_ERROR";
        case EventType::SUSPICIOUS_ACTIVITY: return "SUSPICIOUS_ACTIVITY";
        default: return "UNKNOWN";
    }
}

std::string AuditLogger::log_level_to_string(LogLevel level) {
    switch (level) {
        case LogLevel::INFO: return "INFO";
        case LogLevel::WARNING: return "WARN";
        case LogLevel::ERROR: return "ERROR";
        case LogLevel::CRITICAL: return "CRIT";
        default: return "UNKNOWN";
    }
}

std::string AuditLogger::get_timestamp() {
    auto now = std::chrono::system_clock::now();
    auto time_t = std::chrono::system_clock::to_time_t(now);
    
    std::stringstream ss;
    ss << std::put_time(std::localtime(&time_t), "%Y-%m-%d %H:%M:%S");
    return ss.str();
}

} // namespace logger
} // namespace arcs
