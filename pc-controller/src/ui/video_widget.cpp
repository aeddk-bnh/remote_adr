#include "video_widget.h"
#include <QPainter>
#include <QDateTime>
#include <QtMath>

VideoWidget::VideoWidget(QWidget *parent)
    : QWidget(parent),
      isPressed_(false),
      pressTime_(0),
      deviceWidth_(1080),
      deviceHeight_(2400)
{
    setFocusPolicy(Qt::StrongFocus);
    setMouseTracking(false);
    setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Expanding);
    setMinimumSize(400, 600);
}

void VideoWidget::displayFrame(const QImage& frame) {
    currentFrame_ = frame;
    
    if (!frame.isNull()) {
        deviceWidth_ = frame.width();
        deviceHeight_ = frame.height();
        
        // Scale to widget size maintaining aspect ratio
        scaledFrame_ = frame.scaled(
            size(),
            Qt::KeepAspectRatio,
            Qt::SmoothTransformation
        );
    }
    
    update();
}

void VideoWidget::clearFrame() {
    currentFrame_ = QImage();
    scaledFrame_ = QImage();
    update();
}

void VideoWidget::paintEvent(QPaintEvent* event) {
    Q_UNUSED(event);
    
    QPainter painter(this);
    painter.fillRect(rect(), Qt::black);
    
    if (!scaledFrame_.isNull()) {
        // Center the image
        int x = (width() - scaledFrame_.width()) / 2;
        int y = (height() - scaledFrame_.height()) / 2;
        painter.drawImage(x, y, scaledFrame_);
    } else {
        // Show placeholder text
        painter.setPen(Qt::white);
        painter.drawText(
            rect(),
            Qt::AlignCenter,
            "No video stream\nConnect to a device to begin"
        );
    }
}

void VideoWidget::mousePressEvent(QMouseEvent* event) {
    if (event->button() == Qt::LeftButton && !currentFrame_.isNull()) {
        isPressed_ = true;
        pressPosition_ = event->pos();
        currentPosition_ = event->pos();
        pressTime_ = QDateTime::currentMSecsSinceEpoch();
    }
}

void VideoWidget::mouseReleaseEvent(QMouseEvent* event) {
    if (event->button() == Qt::LeftButton && isPressed_) {
        isPressed_ = false;
        
        qint64 duration = QDateTime::currentMSecsSinceEpoch() - pressTime_;
        QPoint releasePos = event->pos();
        
        int distance = qSqrt(
            qPow(releasePos.x() - pressPosition_.x(), 2) +
            qPow(releasePos.y() - pressPosition_.y(), 2)
        );
        
        QPointF deviceStart = mapToDevice(pressPosition_);
        QPointF deviceEnd = mapToDevice(releasePos);
        
        if (distance < SWIPE_MIN_DISTANCE) {
            // Tap or long press
            if (duration >= LONG_PRESS_THRESHOLD_MS) {
                emit touchEvent("long_press", deviceStart.x(), deviceStart.y(), duration);
            } else {
                emit touchEvent("tap", deviceStart.x(), deviceStart.y());
            }
        } else {
            // Swipe
            handleSwipe(deviceStart, deviceEnd);
        }
    }
}

void VideoWidget::mouseMoveEvent(QMouseEvent* event) {
    if (isPressed_) {
        currentPosition_ = event->pos();
    }
}

void VideoWidget::keyPressEvent(QKeyEvent* event) {
    int key = event->key();
    QString text = event->text();
    
    // Map Qt keys to Android keycodes
    int androidKeycode = 0;
    
    switch (key) {
        case Qt::Key_Backspace:
            androidKeycode = 67; // KEYCODE_DEL
            break;
        case Qt::Key_Return:
        case Qt::Key_Enter:
            androidKeycode = 66; // KEYCODE_ENTER
            break;
        case Qt::Key_Home:
            androidKeycode = 3; // KEYCODE_HOME
            break;
        case Qt::Key_Back:
            androidKeycode = 4; // KEYCODE_BACK
            break;
        default:
            if (!text.isEmpty()) {
                emit keyEvent("text", 0, text);
                return;
            }
            break;
    }
    
    if (androidKeycode != 0) {
        emit keyEvent("press", androidKeycode);
    }
}

void VideoWidget::resizeEvent(QResizeEvent* event) {
    Q_UNUSED(event);
    
    if (!currentFrame_.isNull()) {
        scaledFrame_ = currentFrame_.scaled(
            size(),
            Qt::KeepAspectRatio,
            Qt::SmoothTransformation
        );
    }
}

QPointF VideoWidget::mapToDevice(const QPoint& widgetPos) const {
    if (scaledFrame_.isNull()) {
        return QPointF(0, 0);
    }
    
    // Calculate scaled image position
    int imageX = (width() - scaledFrame_.width()) / 2;
    int imageY = (height() - scaledFrame_.height()) / 2;
    
    // Map widget coordinates to image coordinates
    float imageRelX = static_cast<float>(widgetPos.x() - imageX) / scaledFrame_.width();
    float imageRelY = static_cast<float>(widgetPos.y() - imageY) / scaledFrame_.height();
    
    // Clamp to [0, 1]
    imageRelX = qBound(0.0f, imageRelX, 1.0f);
    imageRelY = qBound(0.0f, imageRelY, 1.0f);
    
    // Map to device coordinates
    float deviceX = imageRelX * deviceWidth_;
    float deviceY = imageRelY * deviceHeight_;
    
    return QPointF(deviceX, deviceY);
}

void VideoWidget::handleTap(const QPointF& devicePos) {
    emit touchEvent("tap", devicePos.x(), devicePos.y());
}

void VideoWidget::handleSwipe(const QPointF& start, const QPointF& end) {
    emit touchEvent(
        "swipe",
        start.x(),
        start.y(),
        qRound(end.x() - start.x()) // Pass delta as duration for swipe
    );
}
