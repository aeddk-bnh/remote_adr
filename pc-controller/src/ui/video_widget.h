#pragma once

#include <QWidget>
#include <QImage>
#include <QMouseEvent>
#include <QKeyEvent>

/**
 * Video display widget with touch input simulation
 */
class VideoWidget : public QWidget {
    Q_OBJECT

public:
    explicit VideoWidget(QWidget *parent = nullptr);
    
    void displayFrame(const QImage& frame);
    void clearFrame();

signals:
    void touchEvent(const QString& action, float x, float y, int duration = 0);
    void keyEvent(const QString& action, int keycode, const QString& text = "");

protected:
    void paintEvent(QPaintEvent* event) override;
    void mousePressEvent(QMouseEvent* event) override;
    void mouseReleaseEvent(QMouseEvent* event) override;
    void mouseMoveEvent(QMouseEvent* event) override;
    void keyPressEvent(QKeyEvent* event) override;
    void resizeEvent(QResizeEvent* event) override;

private:
    QPointF mapToDevice(const QPoint& widgetPos) const;
    void handleTap(const QPointF& devicePos);
    void handleSwipe(const QPointF& start, const QPointF& end);
    
    QImage currentFrame_;
    QImage scaledFrame_;
    
    QPoint pressPosition_;
    QPoint currentPosition_;
    bool isPressed_;
    qint64 pressTime_;
    
    int deviceWidth_;
    int deviceHeight_;
    
    static constexpr int LONG_PRESS_THRESHOLD_MS = 500;
    static constexpr int SWIPE_MIN_DISTANCE = 20;
};
