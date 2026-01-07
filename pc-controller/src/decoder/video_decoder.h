#pragma once

#include <QObject>
#include <QImage>
#include <memory>

extern "C" {
#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavutil/imgutils.h>
#include <libswscale/swscale.h>
}

/**
 * FFmpeg-based H.264 video decoder
 */
class VideoDecoder : public QObject {
    Q_OBJECT

public:
    explicit VideoDecoder(QObject *parent = nullptr);
    ~VideoDecoder();
    
    bool initialize();
    void decodeFrame(const uint8_t* data, size_t size);
    void reset();

signals:
    void frameDecoded(const QImage& frame);
    void decodingError(const QString& error);

private:
    QImage avFrameToQImage(AVFrame* frame);
    
    AVCodec* codec_;
    AVCodecContext* codecCtx_;
    AVCodecParserContext* parser_;
    AVFrame* frame_;
    AVPacket* packet_;
    SwsContext* swsCtx_;
    
    bool initialized_;
    int frameWidth_;
    int frameHeight_;
};
