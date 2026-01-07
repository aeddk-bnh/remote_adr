#include "control_panel.h"
#include <QHBoxLayout>
#include <QVBoxLayout>
#include <QGroupBox>

ControlPanel::ControlPanel(QWidget *parent)
    : QWidget(parent)
{
    setupUI();
}

void ControlPanel::setupUI() {
    QVBoxLayout* mainLayout = new QVBoxLayout(this);
    
    // Connection group
    QGroupBox* connectionGroup = new QGroupBox("Connection", this);
    QHBoxLayout* connectionLayout = new QHBoxLayout(connectionGroup);
    
    QLabel* urlLabel = new QLabel("Server URL:", this);
    connectionLayout->addWidget(urlLabel);
    
    serverUrlEdit_ = new QLineEdit("ws://localhost:8080", this);
    serverUrlEdit_->setPlaceholderText("ws://server:port");
    connectionLayout->addWidget(serverUrlEdit_);
    
    connectBtn_ = new QPushButton("Connect", this);
    connect(connectBtn_, &QPushButton::clicked, this, &ControlPanel::connectClicked);
    connectionLayout->addWidget(connectBtn_);
    
    disconnectBtn_ = new QPushButton("Disconnect", this);
    disconnectBtn_->setEnabled(false);
    connect(disconnectBtn_, &QPushButton::clicked, this, &ControlPanel::disconnectClicked);
    connectionLayout->addWidget(disconnectBtn_);
    
    mainLayout->addWidget(connectionGroup);
    
    // Control buttons
    QGroupBox* controlGroup = new QGroupBox("System Controls", this);
    QHBoxLayout* controlLayout = new QHBoxLayout(controlGroup);
    
    homeBtn_ = new QPushButton("Home", this);
    homeBtn_->setEnabled(false);
    connect(homeBtn_, &QPushButton::clicked, this, &ControlPanel::homeClicked);
    controlLayout->addWidget(homeBtn_);
    
    backBtn_ = new QPushButton("Back", this);
    backBtn_->setEnabled(false);
    connect(backBtn_, &QPushButton::clicked, this, &ControlPanel::backClicked);
    controlLayout->addWidget(backBtn_);
    
    recentBtn_ = new QPushButton("Recent Apps", this);
    recentBtn_->setEnabled(false);
    connect(recentBtn_, &QPushButton::clicked, this, &ControlPanel::recentAppsClicked);
    controlLayout->addWidget(recentBtn_);
    
    controlLayout->addStretch();
    
    mainLayout->addWidget(controlGroup);
}

QString ControlPanel::getServerUrl() const {
    return serverUrlEdit_->text();
}

void ControlPanel::setConnectionState(bool connected) {
    connectBtn_->setEnabled(!connected);
    disconnectBtn_->setEnabled(connected);
    serverUrlEdit_->setEnabled(!connected);
    
    homeBtn_->setEnabled(connected);
    backBtn_->setEnabled(connected);
    recentBtn_->setEnabled(connected);
}
