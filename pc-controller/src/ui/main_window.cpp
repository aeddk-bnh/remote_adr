#include "main_window.h"
#include "video_widget.h"
#include "control_panel.h"
#include "../network/websocket_client.h"
#include "../decoder/video_decoder.h"
#include <QVBoxLayout>
#include <QHBoxLayout>
#include <QMessageBox>
#include <QInputDialog>

MainWindow::MainWindow(QWidget *parent)
    : QMainWindow(parent),
      isConnected_(false)
{
    setupUI();
    setupConnections();
    
    // Initialize components
    wsClient_ = std::make_shared<WebSocketClient>();
    decoder_ = std::make_shared<VideoDecoder>();
    
    setWindowTitle("ARCS - PC Controller");
    resize(1200, 800);
}

MainWindow::~MainWindow() {
    if (isConnected_) {
        wsClient_->disconnect();
    }
}

void MainWindow::setupUI() {
    QWidget* centralWidget = new QWidget(this);
    QVBoxLayout* mainLayout = new QVBoxLayout(centralWidget);
    
    // Video display area
    videoWidget_ = new VideoWidget(this);
    mainLayout->addWidget(videoWidget_, 1);
    
    // Control panel
    controlPanel_ = new ControlPanel(this);
    mainLayout->addWidget(controlPanel_);
    
    setCentralWidget(centralWidget);
    
    // Status bar
    statusLabel_ = new QLabel("Disconnected", this);
    statusBar()->addPermanentWidget(statusLabel_);
    
    updateConnectionStatus(false);
}

void MainWindow::setupConnections() {
    // Control panel signals
    connect(controlPanel_, &ControlPanel::connectClicked, 
            this, &MainWindow::onConnectClicked);
    connect(controlPanel_, &ControlPanel::disconnectClicked, 
            this, &MainWindow::onDisconnectClicked);
}

void MainWindow::onConnectClicked() {
    if (isConnected_) {
        return;
    }
    
    // Get session ID from user
    bool ok;
    QString sessionId = QInputDialog::getText(
        this,
        "Join Session",
        "Enter Session ID:",
        QLineEdit::Normal,
        "",
        &ok
    );
    
    if (!ok || sessionId.isEmpty()) {
        return;
    }
    
    sessionId_ = sessionId;
    
    // Connect WebSocket client signals
    connect(wsClient_.get(), &WebSocketClient::connected,
            this, &MainWindow::onConnectionEstablished);
    connect(wsClient_.get(), &WebSocketClient::disconnected,
            this, &MainWindow::onConnectionClosed);
    connect(wsClient_.get(), &WebSocketClient::errorOccurred,
            this, &MainWindow::onConnectionError);
    connect(wsClient_.get(), &WebSocketClient::videoFrameReceived,
            this, &MainWindow::onVideoFrameReceived);
    connect(wsClient_.get(), &WebSocketClient::deviceInfoReceived,
            this, &MainWindow::onDeviceInfoReceived);
    
    // Connect to server
    QString serverUrl = controlPanel_->getServerUrl();
    wsClient_->connectToServer(serverUrl, sessionId_);
    
    statusLabel_->setText("Connecting...");
}

void MainWindow::onDisconnectClicked() {
    if (!isConnected_) {
        return;
    }
    
    wsClient_->disconnect();
    updateConnectionStatus(false);
}

void MainWindow::onConnectionEstablished() {
    isConnected_ = true;
    updateConnectionStatus(true);
    statusLabel_->setText("Connected to session: " + sessionId_);
    
    // Connect video widget input to WebSocket client
    connect(videoWidget_, &VideoWidget::touchEvent,
            wsClient_.get(), &WebSocketClient::sendTouchCommand);
    connect(videoWidget_, &VideoWidget::keyEvent,
            wsClient_.get(), &WebSocketClient::sendKeyCommand);
}

void MainWindow::onConnectionClosed() {
    isConnected_ = false;
    updateConnectionStatus(false);
    statusLabel_->setText("Disconnected");
    videoWidget_->clearFrame();
}

void MainWindow::onConnectionError(const QString& error) {
    QMessageBox::critical(this, "Connection Error", error);
    statusLabel_->setText("Error: " + error);
    updateConnectionStatus(false);
}

void MainWindow::onVideoFrameReceived(const QImage& frame) {
    videoWidget_->displayFrame(frame);
}

void MainWindow::onDeviceInfoReceived(const QString& model, const QString& version) {
    QString info = QString("Device: %1 (Android %2)").arg(model, version);
    statusBar()->showMessage(info, 5000);
}

void MainWindow::updateConnectionStatus(bool connected) {
    controlPanel_->setConnectionState(connected);
    videoWidget_->setEnabled(connected);
}
