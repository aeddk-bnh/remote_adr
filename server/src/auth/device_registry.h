#pragma once

#include <string>
#include <unordered_map>
#include <mutex>
#include <optional>

namespace arcs {
namespace auth {

/**
 * Device registry
 * Stores and validates device credentials
 */
class DeviceRegistry {
public:
    struct DeviceEntry {
        std::string device_id;
        std::string device_secret;
        std::string device_model;
        std::chrono::system_clock::time_point registered_at;
        bool is_active;
    };
    
    DeviceRegistry();
    
    /**
     * Register new device
     */
    bool register_device(const std::string& device_id,
                        const std::string& device_secret,
                        const std::string& device_model);
    
    /**
     * Authenticate device
     */
    bool authenticate(const std::string& device_id,
                     const std::string& device_secret);
    
    /**
     * Get device info
     */
    std::optional<DeviceEntry> get_device(const std::string& device_id);
    
    /**
     * Deactivate device
     */
    bool deactivate_device(const std::string& device_id);
    
    /**
     * Load from database
     */
    bool load_from_db(const std::string& db_path);
    
    /**
     * Save to database
     */
    bool save_to_db(const std::string& db_path);

private:
    std::unordered_map<std::string, DeviceEntry> devices_;
    std::mutex mutex_;
};

} // namespace auth
} // namespace arcs
