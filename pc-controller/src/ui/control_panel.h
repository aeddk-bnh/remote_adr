#pragma once

#include <QWidget>
#include <QPushButton>
#include <QLineEdit>
#include <QLabel>

/**
 * Control panel with connection buttons and settings
 */
class ControlPanel : public QWidget {
    Q_OBJECT

public:
    explicit ControlPanel(QWidget *parent = nullptr);
    
    QString getServerUrl() const;
    void setConnectionState(bool connected);

signals:
    void connectClicked();
    void disconnectClicked();
    void homeClicked();
    void backClicked();
    void recentAppsClicked();

private:
    void setupUI();
    
    QLineEdit* serverUrlEdit_;
    QPushButton* connectBtn_;
    QPushButton* disconnectBtn_;
    QPushButton* homeBtn_;
    QPushButton* backBtn_;
    QPushButton* recentBtn_;
};
