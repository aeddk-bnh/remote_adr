#include "websocket_client.h"
#include "../decoder/video_decoder.h"
#include <iostream>

WebSocketClient::WebSocketClient(QObject *parent)
    : QObject(parent),
      isConnected_(false)
{
    // Initialize WebSocket client
    wsClient_.init_asio();
    wsClient_.set_tls_init_handler([](connection_hdl) {
        return websocketpp::lib::make_shared<boost::asio::ssl::context>(
            boost::asio::ssl::context::tlsv12
        );
    });
    
    decoder_ = std::make_shared<VideoDecoder>();
    
    // Connect decoder signals
    connect(decoder_.get(), &VideoDecoder::frameDecoded,
            this, &WebSocketClient::videoFrameReceived);
}

WebSocketClient::~WebSocketClient() {
    if (isConnected_) {
        disconnect();
    }
}

void WebSocketClient::connectToServer(const QString& url, const QString& sessionId) {
    sessionId_ = sessionId;
    
    try {
        // Set handlers
        wsClient_.set_open_handler([this](connection_hdl hdl) { onOpen(hdl); });
        wsClient_.set_close_handler([this](connection_hdl hdl) { onClose(hdl); });
        wsClient_.set_message_handler([this](connection_hdl hdl, client::message_ptr msg) {
            onMessage(hdl, msg);
        });
        wsClient_.set_fail_handler([this](connection_hdl hdl) { onFail(hdl); });
        
        // Create connection
        websocketpp::lib::error_code ec;
        client::connection_ptr con = wsClient_.get_connection(url.toStdString(), ec);
        
        if (ec) {
            emit errorOccurred(QString::fromStdString(ec.message()));
            return;
        }
        
        connection_ = con->get_handle();
        wsClient_.connect(con);
        
        // Run in separate thread
        std::thread([this]() {
            wsClient_.run();
        }).detach();
        
    } catch (const std::exception& e) {
        emit errorOccurred(QString::fromStdString(e.what()));
    }
}

void WebSocketClient::disconnect() {
    if (isConnected_) {
        websocketpp::lib::error_code ec;
        wsClient_.close(connection_, websocketpp::close::status::normal, "Closing", ec);
        isConnected_ = false;
    }
}

void WebSocketClient::sendTouchCommand(const QString& action, float x, float y, int duration) {
    json cmd = {
        {"type", "touch"},
        {"action", action.toStdString()}
    };
    
    if (action == "tap" || action == "long_press") {
        cmd["x"] = x;
        cmd["y"] = y;
        if (duration > 0) {
            cmd["duration"] = duration;
        }
    } else if (action == "swipe") {
        cmd["start_x"] = x;
        cmd["start_y"] = y;
        cmd["end_x"] = x + duration; // Using duration as delta
        cmd["end_y"] = y;
    }
    
    sendMessage(cmd);
}

void WebSocketClient::sendKeyCommand(const QString& action, int keycode, const QString& text) {
    json cmd = {
        {"type", "key"},
        {"action", action.toStdString()}
    };
    
    if (action == "text") {
        cmd["text"] = text.toStdString();
    } else if (action == "press") {
        cmd["keycode"] = keycode;
    }
    
    sendMessage(cmd);
}

void WebSocketClient::sendSystemCommand(const QString& action) {
    json cmd = {
        {"type", "system"},
        {"action", action.toStdString()}
    };
    
    sendMessage(cmd);
}

void WebSocketClient::onOpen(connection_hdl hdl) {
    std::cout << "Connection opened" << std::endl;
    
    // Send join session message
    json joinMsg = {
        {"type", "join_session"},
        {"session_id", sessionId_.toStdString()},
        {"jwt_token", jwtToken_.toStdString()}
    };
    
    sendMessage(joinMsg);
}

void WebSocketClient::onClose(connection_hdl hdl) {
    std::cout << "Connection closed" << std::endl;
    isConnected_ = false;
    emit disconnected();
}

void WebSocketClient::onMessage(connection_hdl hdl, client::message_ptr msg) {
    auto opcode = msg->get_opcode();
    
    if (opcode == websocketpp::frame::opcode::text) {
        handleJsonMessage(msg->get_payload());
    } else if (opcode == websocketpp::frame::opcode::binary) {
        handleBinaryMessage(msg->get_payload());
    }
}

void WebSocketClient::onFail(connection_hdl hdl) {
    std::cerr << "Connection failed" << std::endl;
    emit errorOccurred("Connection failed");
}

void WebSocketClient::handleJsonMessage(const std::string& message) {
    try {
        json msg = json::parse(message);
        std::string type = msg["type"];
        
        if (type == "join_response") {
            bool success = msg["success"];
            if (success) {
                isConnected_ = true;
                
                // Extract device info
                if (msg.contains("device_info")) {
                    auto devInfo = msg["device_info"];
                    QString model = QString::fromStdString(devInfo.value("model", "Unknown"));
                    QString version = QString::fromStdString(devInfo.value("android_version", "Unknown"));
                    emit deviceInfoReceived(model, version);
                }
                
                emit connected();
            } else {
                emit errorOccurred("Failed to join session");
            }
        }
        else if (type == "error") {
            QString error = QString::fromStdString(msg.value("message", "Unknown error"));
            emit errorOccurred(error);
        }
        
    } catch (const std::exception& e) {
        std::cerr << "JSON parse error: " << e.what() << std::endl;
    }
}

void WebSocketClient::handleBinaryMessage(const std::string& message) {
    // Decode video frame
    const uint8_t* data = reinterpret_cast<const uint8_t*>(message.data());
    decoder_->decodeFrame(data, message.size());
}

void WebSocketClient::sendMessage(const json& msg) {
    if (!isConnected_) {
        return;
    }
    
    try {
        std::string payload = msg.dump();
        websocketpp::lib::error_code ec;
        wsClient_.send(connection_, payload, websocketpp::frame::opcode::text, ec);
        
        if (ec) {
            std::cerr << "Send error: " << ec.message() << std::endl;
        }
    } catch (const std::exception& e) {
        std::cerr << "Send exception: " << e.what() << std::endl;
    }
}
