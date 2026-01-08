package com.arcs.client.projection

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import java.nio.ByteBuffer

/**
 * Encodes screen frames to H.264 using MediaCodec
 */
class VideoEncoder(
    private val width: Int,
    private val height: Int,
    private val bitrate: Int = 4_000_000,  // 4 Mbps
    private val fps: Int = 30,
    private val iFrameInterval: Int = 1  // Keyframe every 1 second
) {
    private var encoder: MediaCodec? = null
    private var inputSurface: Surface? = null
    
    private val _state = MutableStateFlow<EncoderState>(EncoderState.Stopped)
    val state: StateFlow<EncoderState> = _state
    
    sealed class EncoderState {
        object Stopped : EncoderState()
        object Running : EncoderState()
        data class Error(val message: String) : EncoderState()
    }
    
    /**
     * Start encoder
     * @return Input Surface for rendering frames
     */
    fun start(onEncodedData: (ByteBuffer, MediaCodec.BufferInfo) -> Unit): Surface? {
        try {
            // Create H.264 encoder
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            
            // Configure video format
            val format = MediaFormat.createVideoFormat(
                MediaFormat.MIMETYPE_VIDEO_AVC,
                width,
                height
            ).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                )
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameInterval)
                
                // H.264 baseline profile for compatibility
                setInteger(
                    MediaFormat.KEY_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline
                )
                
                // Low latency encoding
                setInteger(MediaFormat.KEY_LATENCY, 0)
                setInteger(MediaFormat.KEY_PRIORITY, 0)  // Realtime priority
            }
            
            encoder?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = encoder?.createInputSurface()
            
            // Set callback for encoded data
            encoder?.setCallback(object : MediaCodec.Callback() {
                override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                    // Input via Surface, not used
                }
                
                override fun onOutputBufferAvailable(
                    codec: MediaCodec,
                    index: Int,
                    info: MediaCodec.BufferInfo
                ) {
                    try {
                        val outputBuffer = codec.getOutputBuffer(index) ?: return
                        
                        if (info.size > 0) {
                            val isKeyFrame = (info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
                            Timber.d("[VideoEncoder] Frame encoded: size=%d bytes, pts=%d us, flags=0x%02X, isKey=%s",
                                info.size, info.presentationTimeUs, info.flags, isKeyFrame)
                            onEncodedData(outputBuffer, info)
                        }
                        
                        codec.releaseOutputBuffer(index, false)
                        
                    } catch (e: Exception) {
                        Timber.e(e, "Error processing output buffer")
                    }
                }
                
                override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                    Timber.e(e, "Encoder error")
                    _state.value = EncoderState.Error(e.message ?: "Encoder error")
                }
                
                override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                    Timber.i("Encoder format changed: $format")
                }
            })
            
            encoder?.start()
            _state.value = EncoderState.Running
            
            Timber.i("Video encoder started: ${width}x${height}, ${bitrate}bps, ${fps}fps")
            return inputSurface
            
        } catch (e: Exception) {
            Timber.e(e, "Error starting encoder")
            _state.value = EncoderState.Error(e.message ?: "Unknown error")
            stop()
            return null
        }
    }
    
    /**
     * Stop encoder and release resources
     */
    fun stop() {
        try {
            encoder?.stop()
            encoder?.release()
            encoder = null
            
            inputSurface?.release()
            inputSurface = null
            
            _state.value = EncoderState.Stopped
            Timber.i("Video encoder stopped")
            
        } catch (e: Exception) {
            Timber.e(e, "Error stopping encoder")
        }
    }
    
    /**
     * Request keyframe (I-frame)
     */
    fun requestKeyFrame() {
        try {
            encoder?.let {
                val params = android.os.Bundle().apply {
                    putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
                }
                it.setParameters(params)
                Timber.d("Keyframe requested")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error requesting keyframe")
        }
    }
    
    /**
     * Adjust bitrate dynamically
     */
    fun setBitrate(newBitrate: Int) {
        try {
            encoder?.let {
                val params = android.os.Bundle().apply {
                    putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, newBitrate)
                }
                it.setParameters(params)
                Timber.i("Bitrate changed to $newBitrate")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error changing bitrate")
        }
    }
}
