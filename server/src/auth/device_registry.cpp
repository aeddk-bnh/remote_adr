#include "device_registry.h"
#include <sqlite3.h>
#include <iostream>

namespace arcs {
namespace auth {

DeviceRegistry::DeviceRegistry() {
}

bool DeviceRegistry::register_device(
    const std::string& device_id,
    const std::string& device_secret,
    const std::string& device_model)
{
    std::lock_guard<std::mutex> lock(mutex_);
    
    // Check if already registered
    if (devices_.find(device_id) != devices_.end()) {
        return false;
    }
    
    DeviceEntry entry;
    entry.device_id = device_id;
    entry.device_secret = device_secret;
    entry.device_model = device_model;
    entry.registered_at = std::chrono::system_clock::now();
    entry.is_active = true;
    
    devices_[device_id] = entry;
    return true;
}

bool DeviceRegistry::authenticate(
    const std::string& device_id,
    const std::string& device_secret)
{
    std::lock_guard<std::mutex> lock(mutex_);
    
    auto it = devices_.find(device_id);
    if (it == devices_.end()) {
        return false;
    }
    
    const auto& entry = it->second;
    return entry.is_active && entry.device_secret == device_secret;
}

std::optional<DeviceRegistry::DeviceEntry> DeviceRegistry::get_device(
    const std::string& device_id)
{
    std::lock_guard<std::mutex> lock(mutex_);
    
    auto it = devices_.find(device_id);
    if (it != devices_.end()) {
        return it->second;
    }
    return std::nullopt;
}

bool DeviceRegistry::deactivate_device(const std::string& device_id) {
    std::lock_guard<std::mutex> lock(mutex_);
    
    auto it = devices_.find(device_id);
    if (it != devices_.end()) {
        it->second.is_active = false;
        return true;
    }
    return false;
}

bool DeviceRegistry::load_from_db(const std::string& db_path) {
    // TODO: Implement SQLite loading
    return true;
}

bool DeviceRegistry::save_to_db(const std::string& db_path) {
    // TODO: Implement SQLite saving
    return true;
}

} // namespace auth
} // namespace arcs
