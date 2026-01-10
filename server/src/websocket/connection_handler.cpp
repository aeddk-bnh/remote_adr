#include "connection_handler.h"
#include "session_manager.h"
#include "message_parser.h"
#include "../auth/jwt_manager.h"
#include <iostream>
#include <uuid/uuid.h>

namespace arcs {
namespace websocket {

ConnectionHandler::ConnectionHandler(
    std::shared_ptr<SessionManager> session_manager,
    uint16_t port)
    : session_manager_(session_manager),
      port_(port)
{
    // Initialize WebSocket server
    ws_server_.init_asio();
    ws_server_.set_reuse_addr(true);
    
    // Set handlers
    ws_server_.set_open_handler(bind(&ConnectionHandler::on_open, this, _1));
    ws_server_.set_close_handler(bind(&ConnectionHandler::on_close, this, _1));
    ws_server_.set_message_handler(bind(&ConnectionHandler::on_message, this, _1, _2));
    ws_server_.set_fail_handler(bind(&ConnectionHandler::on_fail, this, _1));
    
    std::cout << "WebSocket server initialized on port " << port_ << std::endl;
}

void ConnectionHandler::start() {
    ws_server_.listen(port_);
    ws_server_.start_accept();
    
    std::cout << "WebSocket server started" << std::endl;
    
    // Run in current thread
    ws_server_.run();
}

void ConnectionHandler::stop() {
    ws_server_.stop_listening();
    ws_server_.stop();
    
    std::cout << "WebSocket server stopped" << std::endl;
}

void ConnectionHandler::send(const std::string& connection_id, const std::string& message) {
    std::lock_guard<std::mutex> lock(connections_mutex_);
    
    auto it = connections_.find(connection_id);
    if (it != connections_.end()) {
        try {
            ws_server_.send(it->second->hdl, message, websocketpp::frame::opcode::text);
        } catch (const std::exception& e) {
            std::cerr << "Failed to send message: " << e.what() << std::endl;
        }
    }
}

void ConnectionHandler::send_to_device(const std::string& session_id, const std::string& message) {
    std::lock_guard<std::mutex> lock(connections_mutex_);
    
    for (const auto& [id, conn] : connections_) {
        if (conn->session_id == session_id && conn->is_device) {
            try {
                ws_server_.send(conn->hdl, message, websocketpp::frame::opcode::text);
            } catch (const std::exception& e) {
                std::cerr << "Failed to send to device: " << e.what() << std::endl;
            }
            break;
        }
    }
}

void ConnectionHandler::send_to_controller(const std::string& session_id, const std::string& message) {
    std::lock_guard<std::mutex> lock(connections_mutex_);
    
    for (const auto& [id, conn] : connections_) {
        if (conn->session_id == session_id && !conn->is_device) {
            try {
                ws_server_.send(conn->hdl, message, websocketpp::frame::opcode::text);
            } catch (const std::exception& e) {
                std::cerr << "Failed to send to controller: " << e.what() << std::endl;
            }
            break;
        }
    }
}

void ConnectionHandler::broadcast_to_session(const std::string& session_id, const std::string& message) {
    std::lock_guard<std::mutex> lock(connections_mutex_);
    
    for (const auto& [id, conn] : connections_) {
        if (conn->session_id == session_id) {
            try {
                ws_server_.send(conn->hdl, message, websocketpp::frame::opcode::text);
            } catch (const std::exception& e) {
                std::cerr << "Failed to broadcast: " << e.what() << std::endl;
            }
        }
    }
}

void ConnectionHandler::close_connection(const std::string& connection_id) {
    std::lock_guard<std::mutex> lock(connections_mutex_);
    
    auto it = connections_.find(connection_id);
    if (it != connections_.end()) {
        try {
            ws_server_.close(it->second->hdl, websocketpp::close::status::normal, "Closing");
        } catch (const std::exception& e) {
            std::cerr << "Failed to close connection: " << e.what() << std::endl;
        }
    }
}

size_t ConnectionHandler::get_connection_count() const {
    std::lock_guard<std::mutex> lock(connections_mutex_);
    return connections_.size();
}

void ConnectionHandler::on_open(connection_hdl hdl) {
    std::lock_guard<std::mutex> lock(connections_mutex_);
    
    // Generate connection ID
    uuid_t uuid;
    char uuid_str[37];
    uuid_generate(uuid);
    uuid_unparse(uuid, uuid_str);
    std::string connection_id(uuid_str);
    
    // Create connection info
    auto conn = std::make_shared<ConnectionInfo>();
    conn->hdl = hdl;
    conn->connection_id = connection_id;
    conn->authenticated = false;
    conn->connected_at = std::chrono::system_clock::now();
    
    connections_[connection_id] = conn;
    hdl_to_id_[hdl] = connection_id;
    
    std::cout << "Connection opened: " << connection_id << std::endl;
}

void ConnectionHandler::on_close(connection_hdl hdl) {
    std::lock_guard<std::mutex> lock(connections_mutex_);
    
    auto it = hdl_to_id_.find(hdl);
    if (it != hdl_to_id_.end()) {
        std::string connection_id = it->second;
        
        // Clean up session if authenticated
        auto conn_it = connections_.find(connection_id);
        if (conn_it != connections_.end() && conn_it->second->authenticated) {
            session_manager_->close_session(conn_it->second->session_id);
        }
        
        connections_.erase(connection_id);
        hdl_to_id_.erase(it);
        
        std::cout << "Connection closed: " << connection_id << std::endl;
    }
}

void ConnectionHandler::on_message(connection_hdl hdl, message_ptr msg) {
    std::string connection_id = get_connection_id(hdl);
    if (connection_id.empty()) {
        return;
    }
    
    std::string payload = msg->get_payload();
    
    try {
        auto msg_type = MessageParser::get_message_type(payload);
        
        switch (msg_type) {
            case MessageParser::MessageType::AUTH_REQUEST:
                handle_auth_request(hdl, connection_id, payload);
                break;
                
            case MessageParser::MessageType::JOIN_SESSION:
                handle_join_session(hdl, connection_id, payload);
                break;
                
            case MessageParser::MessageType::PING:
                {
                    std::string pong = MessageParser::create_pong();
                    send(connection_id, pong);
                }
                break;
                
            default:
                handle_command(hdl, connection_id, payload);
                break;
        }
    } catch (const std::exception& e) {
        std::cerr << "Message handling error: " << e.what() << std::endl;
        std::string error = MessageParser::create_error("INVALID_MESSAGE", e.what());
        send(connection_id, error);
    }
}

void ConnectionHandler::on_fail(connection_hdl hdl) {
    std::string connection_id = get_connection_id(hdl);
    std::cerr << "Connection failed: " << connection_id << std::endl;
}

void ConnectionHandler::handle_auth_request(
    connection_hdl hdl,
    const std::string& connection_id,
    const std::string& message)
{
    auto msg = MessageParser::parse_json(message);
    
    std::string device_id = msg["device_id"];
    std::string secret = msg["secret"];
    
    // Validate device credentials using DeviceRegistry
    if (device_registry_) {
        if (!device_registry_->authenticate(device_id, secret)) {
            std::cerr << "Authentication failed for device: " << device_id << std::endl;
            
            std::string error = MessageParser::create_error(
                "ERR_AUTH_FAILED",
                "Invalid device credentials"
            );
            send(connection_id, error);
            return;
        }
        std::cout << "Device authenticated via registry: " << device_id << std::endl;
    } else {
        // Fallback: Accept any device if registry not configured (development mode)
        std::cout << "WARNING: DeviceRegistry not configured, accepting device: " << device_id << std::endl;
    }
    
    // Create session
    std::string session_id = session_manager_->create_session(device_id);
    
    // Generate JWT token
    auth::JWTManager jwt_mgr("secret_key");  // TODO: Load from config
    std::string jwt_token = jwt_mgr.generate_token(device_id);
    
    // Update connection info
    {
        std::lock_guard<std::mutex> lock(connections_mutex_);
        auto it = connections_.find(connection_id);
        if (it != connections_.end()) {
            it->second->session_id = session_id;
            it->second->user_id = device_id;
            it->second->is_device = true;
            it->second->authenticated = true;
        }
    }
    
    // Send response
    std::string response = MessageParser::create_auth_response(
        true,
        session_id,
        jwt_token,
        std::chrono::system_clock::now().time_since_epoch().count() + 3600000
    );
    
    send(connection_id, response);
    
    std::cout << "Device authenticated: " << device_id 
              << " session: " << session_id << std::endl;
}

void ConnectionHandler::handle_join_session(
    connection_hdl hdl,
    const std::string& connection_id,
    const std::string& message)
{
    auto msg = MessageParser::parse_json(message);
    
    std::string session_id = msg["session_id"];
    std::string jwt_token = msg["jwt_token"];
    
    // TODO: Validate JWT token
    auth::JWTManager jwt_mgr("secret_key");
    if (!jwt_mgr.validate_token(jwt_token)) {
        std::string error = MessageParser::create_error("INVALID_TOKEN", "JWT validation failed");
        send(connection_id, error);
        return;
    }
    
    std::string controller_id = connection_id;  // Use connection ID as controller ID
    
    // Join session
    if (!session_manager_->join_session(session_id, controller_id)) {
        std::string error = MessageParser::create_error("SESSION_NOT_FOUND", "Session does not exist");
        send(connection_id, error);
        return;
    }
    
    // Update connection info
    {
        std::lock_guard<std::mutex> lock(connections_mutex_);
        auto it = connections_.find(connection_id);
        if (it != connections_.end()) {
            it->second->session_id = session_id;
            it->second->user_id = controller_id;
            it->second->is_device = false;
            it->second->authenticated = true;
        }
    }
    
    // Send response
    nlohmann::json device_info = {
        {"device_id", "device_123"},  // TODO: Get from session
        {"model", "Pixel 6"},
        {"android_version", "13"}
    };
    
    nlohmann::json video_config = {
        {"width", 1080},
        {"height", 2400},
        {"codec", "h264"}
    };
    
    std::string response = MessageParser::create_join_response(true, device_info, video_config);
    send(connection_id, response);
    
    std::cout << "Controller joined session: " << session_id << std::endl;
}

void ConnectionHandler::handle_command(
    connection_hdl hdl,
    const std::string& connection_id,
    const std::string& message)
{
    std::lock_guard<std::mutex> lock(connections_mutex_);
    
    auto it = connections_.find(connection_id);
    if (it == connections_.end() || !it->second->authenticated) {
        std::string error = MessageParser::create_error("UNAUTHORIZED", "Not authenticated");
        send(connection_id, error);
        return;
    }
    
    std::string session_id = it->second->session_id;
    bool is_device = it->second->is_device;
    
    // Route message to other party
    if (is_device) {
        send_to_controller(session_id, message);
    } else {
        send_to_device(session_id, message);
    }
}

std::string ConnectionHandler::get_connection_id(connection_hdl hdl) {
    std::lock_guard<std::mutex> lock(connections_mutex_);
    
    auto it = hdl_to_id_.find(hdl);
    if (it != hdl_to_id_.end()) {
        return it->second;
    }
    return "";
}

} // namespace websocket
} // namespace arcs
