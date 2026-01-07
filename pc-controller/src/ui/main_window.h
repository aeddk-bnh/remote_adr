#pragma once

#include <QMainWindow>
#include <QLabel>
#include <QStatusBar>
#include <memory>

class VideoWidget;
class ControlPanel;
class WebSocketClient;
class VideoDecoder;

/**
 * Main application window
 */
class MainWindow : public QMainWindow {
    Q_OBJECT

public:
    explicit MainWindow(QWidget *parent = nullptr);
    ~MainWindow();

private slots:
    void onConnectClicked();
    void onDisconnectClicked();
    void onConnectionEstablished();
    void onConnectionClosed();
    void onConnectionError(const QString& error);
    void onVideoFrameReceived(const QImage& frame);
    void onDeviceInfoReceived(const QString& model, const QString& version);

private:
    void setupUI();
    void setupConnections();
    void updateConnectionStatus(bool connected);

    VideoWidget* videoWidget_;
    ControlPanel* controlPanel_;
    QLabel* statusLabel_;
    
    std::shared_ptr<WebSocketClient> wsClient_;
    std::shared_ptr<VideoDecoder> decoder_;
    
    QString sessionId_;
    bool isConnected_;
};
