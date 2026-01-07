#include "stream_router.h"
#include <iostream>

namespace arcs {
namespace stream {

void StreamRouter::register_device(const std::string& session_id, const std::string& device_id) {
    std::lock_guard<std::mutex> lock(mutex_);
    
    auto it = endpoints_.find(session_id);
    if (it == endpoints_.end()) {
        auto endpoint = std::make_shared<StreamEndpoint>();
        endpoint->session_id = session_id;
        endpoint->device_id = device_id;
        endpoint->stats = {0, 0, 0, 0.0};
        endpoints_[session_id] = endpoint;
        
        std::cout << "Registered device stream: " << device_id 
                  << " for session: " << session_id << std::endl;
    }
}

void StreamRouter::register_controller(const std::string& session_id, const std::string& controller_id) {
    std::lock_guard<std::mutex> lock(mutex_);
    
    auto it = endpoints_.find(session_id);
    if (it != endpoints_.end()) {
        std::lock_guard<std::mutex> endpoint_lock(it->second->mutex);
        
        it->second->controller_ids.push_back(controller_id);
        it->second->frame_queues[controller_id] = std::queue<std::vector<uint8_t>>();
        
        std::cout << "Registered controller stream: " << controller_id 
                  << " for session: " << session_id << std::endl;
    }
}

void StreamRouter::route_frame(
    const std::string& session_id,
    const uint8_t* data,
    size_t size)
{
    std::lock_guard<std::mutex> lock(mutex_);
    
    auto it = endpoints_.find(session_id);
    if (it == endpoints_.end()) {
        return;
    }
    
    std::lock_guard<std::mutex> endpoint_lock(it->second->mutex);
    
    // Update stats
    it->second->stats.total_frames++;
    it->second->stats.total_bytes += size;
    it->second->stats.avg_frame_size = 
        static_cast<double>(it->second->stats.total_bytes) / 
        it->second->stats.total_frames;
    
    // Copy frame data
    std::vector<uint8_t> frame(data, data + size);
    
    // Route to all controllers
    for (const auto& controller_id : it->second->controller_ids) {
        auto& queue = it->second->frame_queues[controller_id];
        
        // Drop old frames if queue is full
        if (queue.size() >= MAX_QUEUE_SIZE) {
            queue.pop();
            it->second->stats.dropped_frames++;
        }
        
        queue.push(frame);
    }
}

bool StreamRouter::get_frame(
    const std::string& session_id,
    const std::string& controller_id,
    std::vector<uint8_t>& out_data)
{
    std::lock_guard<std::mutex> lock(mutex_);
    
    auto it = endpoints_.find(session_id);
    if (it == endpoints_.end()) {
        return false;
    }
    
    std::lock_guard<std::mutex> endpoint_lock(it->second->mutex);
    
    auto queue_it = it->second->frame_queues.find(controller_id);
    if (queue_it == it->second->frame_queues.end() || queue_it->second.empty()) {
        return false;
    }
    
    out_data = std::move(queue_it->second.front());
    queue_it->second.pop();
    
    return true;
}

void StreamRouter::unregister_device(const std::string& session_id) {
    std::lock_guard<std::mutex> lock(mutex_);
    
    auto it = endpoints_.find(session_id);
    if (it != endpoints_.end()) {
        std::cout << "Unregistered device stream for session: " << session_id << std::endl;
        endpoints_.erase(it);
    }
}

void StreamRouter::unregister_controller(
    const std::string& session_id,
    const std::string& controller_id)
{
    std::lock_guard<std::mutex> lock(mutex_);
    
    auto it = endpoints_.find(session_id);
    if (it != endpoints_.end()) {
        std::lock_guard<std::mutex> endpoint_lock(it->second->mutex);
        
        // Remove controller from list
        auto& controllers = it->second->controller_ids;
        controllers.erase(
            std::remove(controllers.begin(), controllers.end(), controller_id),
            controllers.end()
        );
        
        // Remove frame queue
        it->second->frame_queues.erase(controller_id);
        
        std::cout << "Unregistered controller stream: " << controller_id 
                  << " from session: " << session_id << std::endl;
    }
}

StreamRouter::Stats StreamRouter::get_stats(const std::string& session_id) const {
    std::lock_guard<std::mutex> lock(mutex_);
    
    auto it = endpoints_.find(session_id);
    if (it != endpoints_.end()) {
        std::lock_guard<std::mutex> endpoint_lock(it->second->mutex);
        return it->second->stats;
    }
    
    return {0, 0, 0, 0.0};
}

} // namespace stream
} // namespace arcs
