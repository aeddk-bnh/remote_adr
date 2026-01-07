#include "video_decoder.h"
#include <iostream>

VideoDecoder::VideoDecoder(QObject *parent)
    : QObject(parent),
      codec_(nullptr),
      codecCtx_(nullptr),
      parser_(nullptr),
      frame_(nullptr),
      packet_(nullptr),
      swsCtx_(nullptr),
      initialized_(false),
      frameWidth_(0),
      frameHeight_(0)
{
    initialize();
}

VideoDecoder::~VideoDecoder() {
    if (swsCtx_) {
        sws_freeContext(swsCtx_);
    }
    if (frame_) {
        av_frame_free(&frame_);
    }
    if (packet_) {
        av_packet_free(&packet_);
    }
    if (parser_) {
        av_parser_close(parser_);
    }
    if (codecCtx_) {
        avcodec_free_context(&codecCtx_);
    }
}

bool VideoDecoder::initialize() {
    // Find H.264 decoder
    codec_ = avcodec_find_decoder(AV_CODEC_ID_H264);
    if (!codec_) {
        emit decodingError("H.264 codec not found");
        return false;
    }
    
    // Create parser
    parser_ = av_parser_init(codec_->id);
    if (!parser_) {
        emit decodingError("Failed to create parser");
        return false;
    }
    
    // Create codec context
    codecCtx_ = avcodec_alloc_context3(codec_);
    if (!codecCtx_) {
        emit decodingError("Failed to allocate codec context");
        return false;
    }
    
    // Open codec
    if (avcodec_open2(codecCtx_, codec_, nullptr) < 0) {
        emit decodingError("Failed to open codec");
        return false;
    }
    
    // Allocate frame and packet
    frame_ = av_frame_alloc();
    packet_ = av_packet_alloc();
    
    if (!frame_ || !packet_) {
        emit decodingError("Failed to allocate frame/packet");
        return false;
    }
    
    initialized_ = true;
    std::cout << "Video decoder initialized" << std::endl;
    
    return true;
}

void VideoDecoder::decodeFrame(const uint8_t* data, size_t size) {
    if (!initialized_) {
        return;
    }
    
    // Parse data
    uint8_t* parseData = const_cast<uint8_t*>(data);
    int parseSize = static_cast<int>(size);
    
    while (parseSize > 0) {
        int ret = av_parser_parse2(
            parser_,
            codecCtx_,
            &packet_->data,
            &packet_->size,
            parseData,
            parseSize,
            AV_NOPTS_VALUE,
            AV_NOPTS_VALUE,
            0
        );
        
        if (ret < 0) {
            emit decodingError("Error parsing frame");
            return;
        }
        
        parseData += ret;
        parseSize -= ret;
        
        if (packet_->size > 0) {
            // Decode packet
            ret = avcodec_send_packet(codecCtx_, packet_);
            if (ret < 0) {
                emit decodingError("Error sending packet for decoding");
                continue;
            }
            
            while (ret >= 0) {
                ret = avcodec_receive_frame(codecCtx_, frame_);
                if (ret == AVERROR(EAGAIN) || ret == AVERROR_EOF) {
                    break;
                }
                if (ret < 0) {
                    emit decodingError("Error during decoding");
                    break;
                }
                
                // Update frame dimensions if changed
                if (frame_->width != frameWidth_ || frame_->height != frameHeight_) {
                    frameWidth_ = frame_->width;
                    frameHeight_ = frame_->height;
                    
                    if (swsCtx_) {
                        sws_freeContext(swsCtx_);
                    }
                    
                    // Create conversion context
                    swsCtx_ = sws_getContext(
                        frameWidth_,
                        frameHeight_,
                        codecCtx_->pix_fmt,
                        frameWidth_,
                        frameHeight_,
                        AV_PIX_FMT_RGB24,
                        SWS_BILINEAR,
                        nullptr,
                        nullptr,
                        nullptr
                    );
                }
                
                // Convert frame to QImage
                QImage image = avFrameToQImage(frame_);
                if (!image.isNull()) {
                    emit frameDecoded(image);
                }
            }
        }
    }
}

void VideoDecoder::reset() {
    if (codecCtx_) {
        avcodec_flush_buffers(codecCtx_);
    }
}

QImage VideoDecoder::avFrameToQImage(AVFrame* frame) {
    if (!swsCtx_ || !frame) {
        return QImage();
    }
    
    // Allocate RGB buffer
    int numBytes = av_image_get_buffer_size(
        AV_PIX_FMT_RGB24,
        frameWidth_,
        frameHeight_,
        1
    );
    
    std::vector<uint8_t> buffer(numBytes);
    
    uint8_t* dest[4] = {buffer.data(), nullptr, nullptr, nullptr};
    int destLinesize[4] = {frameWidth_ * 3, 0, 0, 0};
    
    // Convert YUV to RGB
    sws_scale(
        swsCtx_,
        frame->data,
        frame->linesize,
        0,
        frameHeight_,
        dest,
        destLinesize
    );
    
    // Create QImage
    QImage image(
        buffer.data(),
        frameWidth_,
        frameHeight_,
        frameWidth_ * 3,
        QImage::Format_RGB888
    );
    
    return image.copy();
}
