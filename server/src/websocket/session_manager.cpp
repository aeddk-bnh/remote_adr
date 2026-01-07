#include "session_manager.h"
#include <uuid/uuid.h>
#include <iostream>

namespace arcs {
namespace websocket {

SessionManager::SessionManager() {
}

std::string SessionManager::create_session(const std::string& device_id) {
    std::lock_guard<std::mutex> lock(mutex_);
    
    // Check if device already has a session
    for (const auto& [id, session] : sessions_) {
        if (session->device_id == device_id && session->is_active) {
            std::cout << "Device already has active session: " << id << std::endl;
            return id;
        }
    }
    
    // Generate session ID
    uuid_t uuid;
    char uuid_str[37];
    uuid_generate(uuid);
    uuid_unparse(uuid, uuid_str);
    std::string session_id(uuid_str);
    
    // Create session
    auto session = std::make_shared<Session>();
    session->session_id = session_id;
    session->device_id = device_id;
    session->created_at = std::chrono::system_clock::now();
    session->last_activity = session->created_at;
    session->is_active = true;
    
    sessions_[session_id] = session;
    
    std::cout << "Created session: " << session_id 
              << " for device: " << device_id << std::endl;
    
    return session_id;
}

bool SessionManager::join_session(
    const std::string& session_id,
    const std::string& controller_id)
{
    std::lock_guard<std::mutex> lock(mutex_);
    
    auto it = sessions_.find(session_id);
    if (it == sessions_.end()) {
        std::cerr << "Session not found: " << session_id << std::endl;
        return false;
    }
    
    auto& session = it->second;
    if (!session->is_active) {
        std::cerr << "Session not active: " << session_id << std::endl;
        return false;
    }
    
    session->controller_id = controller_id;
    session->last_activity = std::chrono::system_clock::now();
    
    std::cout << "Controller " << controller_id 
              << " joined session: " << session_id << std::endl;
    
    return true;
}

std::shared_ptr<Session> SessionManager::get_session(const std::string& session_id) {
    std::lock_guard<std::mutex> lock(mutex_);
    
    auto it = sessions_.find(session_id);
    if (it != sessions_.end()) {
        return it->second;
    }
    return nullptr;
}

void SessionManager::update_activity(const std::string& session_id) {
    std::lock_guard<std::mutex> lock(mutex_);
    
    auto it = sessions_.find(session_id);
    if (it != sessions_.end()) {
        it->second->last_activity = std::chrono::system_clock::now();
    }
}

bool SessionManager::close_session(const std::string& session_id) {
    std::lock_guard<std::mutex> lock(mutex_);
    
    auto it = sessions_.find(session_id);
    if (it != sessions_.end()) {
        it->second->is_active = false;
        sessions_.erase(it);
        std::cout << "Closed session: " << session_id << std::endl;
        return true;
    }
    return false;
}

std::shared_ptr<Session> SessionManager::get_session_by_device(
    const std::string& device_id)
{
    std::lock_guard<std::mutex> lock(mutex_);
    
    for (const auto& [id, session] : sessions_) {
        if (session->device_id == device_id && session->is_active) {
            return session;
        }
    }
    return nullptr;
}

std::shared_ptr<Session> SessionManager::get_session_by_controller(
    const std::string& controller_id)
{
    std::lock_guard<std::mutex> lock(mutex_);
    
    for (const auto& [id, session] : sessions_) {
        if (session->controller_id == controller_id && session->is_active) {
            return session;
        }
    }
    return nullptr;
}

size_t SessionManager::get_active_count() const {
    std::lock_guard<std::mutex> lock(mutex_);
    
    size_t count = 0;
    for (const auto& [id, session] : sessions_) {
        if (session->is_active) {
            count++;
        }
    }
    return count;
}

void SessionManager::cleanup_expired() {
    std::lock_guard<std::mutex> lock(mutex_);
    
    auto it = sessions_.begin();
    while (it != sessions_.end()) {
        if (it->second->is_expired()) {
            std::cout << "Removing expired session: " << it->first << std::endl;
            it = sessions_.erase(it);
        } else {
            ++it;
        }
    }
}

} // namespace websocket
} // namespace arcs
