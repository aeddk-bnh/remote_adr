#pragma once

#include <string>
#include <map>
#include <mutex>
#include <queue>
#include <memory>

namespace arcs {
namespace stream {

/**
 * Stream router
 * Routes binary video stream data between devices and controllers
 */
class StreamRouter {
public:
    /**
     * Register stream endpoint
     */
    void register_device(const std::string& session_id, const std::string& device_id);
    
    /**
     * Register stream receiver
     */
    void register_controller(const std::string& session_id, const std::string& controller_id);
    
    /**
     * Route video frame from device to controllers
     */
    void route_frame(
        const std::string& session_id,
        const uint8_t* data,
        size_t size
    );
    
    /**
     * Get pending frames for controller
     */
    bool get_frame(
        const std::string& session_id,
        const std::string& controller_id,
        std::vector<uint8_t>& out_data
    );
    
    /**
     * Unregister endpoints
     */
    void unregister_device(const std::string& session_id);
    void unregister_controller(const std::string& session_id, const std::string& controller_id);
    
    /**
     * Get statistics
     */
    struct Stats {
        size_t total_frames;
        size_t total_bytes;
        size_t dropped_frames;
        double avg_frame_size;
    };
    
    Stats get_stats(const std::string& session_id) const;

private:
    struct StreamEndpoint {
        std::string session_id;
        std::string device_id;
        std::vector<std::string> controller_ids;
        std::map<std::string, std::queue<std::vector<uint8_t>>> frame_queues;
        Stats stats;
        std::mutex mutex;
    };
    
    std::map<std::string, std::shared_ptr<StreamEndpoint>> endpoints_;
    std::mutex mutex_;
    
    static constexpr size_t MAX_QUEUE_SIZE = 30;  // 1 second at 30fps
};

} // namespace stream
} // namespace arcs
