#pragma once

#include <QObject>
#include <QString>
#include <QImage>
#include <memory>
#include <websocketpp/config/asio_client.hpp>
#include <websocketpp/client.hpp>
#include <nlohmann/json.hpp>

using websocketpp::connection_hdl;
using json = nlohmann::json;

typedef websocketpp::client<websocketpp::config::asio_tls_client> client;

/**
 * WebSocket client for server communication
 */
class WebSocketClient : public QObject {
    Q_OBJECT

public:
    explicit WebSocketClient(QObject *parent = nullptr);
    ~WebSocketClient();
    
    void connectToServer(const QString& url, const QString& sessionId);
    void disconnect();
    
    void sendTouchCommand(const QString& action, float x, float y, int duration = 0);
    void sendKeyCommand(const QString& action, int keycode, const QString& text = "");
    void sendSystemCommand(const QString& action);

signals:
    void connected();
    void disconnected();
    void errorOccurred(const QString& error);
    void videoFrameReceived(const QImage& frame);
    void deviceInfoReceived(const QString& model, const QString& version);

private:
    void onOpen(connection_hdl hdl);
    void onClose(connection_hdl hdl);
    void onMessage(connection_hdl hdl, client::message_ptr msg);
    void onFail(connection_hdl hdl);
    
    void handleJsonMessage(const std::string& message);
    void handleBinaryMessage(const std::string& message);
    
    void sendMessage(const json& msg);
    
    client wsClient_;
    connection_hdl connection_;
    QString sessionId_;
    QString jwtToken_;
    bool isConnected_;
    
    std::shared_ptr<class VideoDecoder> decoder_;
};
