#pragma once

#include <string>
#include <memory>
#include <functional>
#include <websocketpp/config/asio_no_tls.hpp>
#include <websocketpp/server.hpp>

namespace arcs {
namespace websocket {

class SessionManager;

using websocketpp::connection_hdl;
using websocketpp::lib::placeholders::_1;
using websocketpp::lib::placeholders::_2;
using websocketpp::lib::bind;

typedef websocketpp::server<websocketpp::config::asio> server;
typedef server::message_ptr message_ptr;

/**
 * Connection information
 */
struct ConnectionInfo {
    connection_hdl hdl;
    std::string connection_id;
    std::string session_id;
    std::string user_id;  // device_id or controller_id
    bool is_device;
    bool authenticated;
    std::chrono::system_clock::time_point connected_at;
};

/**
 * WebSocket connection handler
 */
class ConnectionHandler {
public:
    ConnectionHandler(
        std::shared_ptr<SessionManager> session_manager,
        uint16_t port = 8080
    );
    
    /**
     * Start server
     */
    void start();
    
    /**
     * Stop server
     */
    void stop();
    
    /**
     * Send message to specific connection
     */
    void send(const std::string& connection_id, const std::string& message);
    
    /**
     * Send message to device in session
     */
    void send_to_device(const std::string& session_id, const std::string& message);
    
    /**
     * Send message to controller in session
     */
    void send_to_controller(const std::string& session_id, const std::string& message);
    
    /**
     * Broadcast to all connections in session
     */
    void broadcast_to_session(const std::string& session_id, const std::string& message);
    
    /**
     * Close connection
     */
    void close_connection(const std::string& connection_id);
    
    /**
     * Get active connection count
     */
    size_t get_connection_count() const;

private:
    void on_open(connection_hdl hdl);
    void on_close(connection_hdl hdl);
    void on_message(connection_hdl hdl, message_ptr msg);
    void on_fail(connection_hdl hdl);
    
    void handle_auth_request(
        connection_hdl hdl,
        const std::string& connection_id,
        const std::string& message
    );
    
    void handle_join_session(
        connection_hdl hdl,
        const std::string& connection_id,
        const std::string& message
    );
    
    void handle_command(
        connection_hdl hdl,
        const std::string& connection_id,
        const std::string& message
    );
    
    std::string get_connection_id(connection_hdl hdl);
    
    server ws_server_;
    std::shared_ptr<SessionManager> session_manager_;
    std::map<std::string, std::shared_ptr<ConnectionInfo>> connections_;
    std::map<connection_hdl, std::string, std::owner_less<connection_hdl>> hdl_to_id_;
    std::mutex connections_mutex_;
    uint16_t port_;
};

} // namespace websocket
} // namespace arcs
