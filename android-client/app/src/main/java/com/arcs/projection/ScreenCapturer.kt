package com.arcs.client.projection

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber

/**
 * Captures screen frames using MediaProjection API
 * Outputs to ImageReader for encoding
 */
class ScreenCapturer(
    private val context: Context,
    private val width: Int,
    private val height: Int,
    private val dpi: Int
) {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    
    private val _state = MutableStateFlow<CaptureState>(CaptureState.Stopped)
    val state: StateFlow<CaptureState> = _state
    
    sealed class CaptureState {
        object Stopped : CaptureState()
        object Running : CaptureState()
        data class Error(val message: String) : CaptureState()
    }
    
    /**
     * Start screen capture with MediaProjection intent result
     * @param resultCode Activity result code from permission dialog
     * @param data Intent data from permission dialog
     * @param onFrame Callback for each captured frame
     */
    fun start(
        resultCode: Int,
        data: Intent,
        onFrame: (ImageReader.OnImageAvailableListener) -> Unit
    ): Boolean {
        try {
            // Create handler thread for capture
            handlerThread = HandlerThread("ScreenCapture").apply { start() }
            handler = Handler(handlerThread!!.looper)
            
            // Get MediaProjection
            val mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) 
                as MediaProjectionManager
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
            
            if (mediaProjection == null) {
                Timber.e("Failed to get MediaProjection")
                _state.value = CaptureState.Error("Failed to get MediaProjection")
                return false
            }
            
            // Create ImageReader for frame data
            imageReader = ImageReader.newInstance(
                width,
                height,
                PixelFormat.RGBA_8888,
                2  // Max images in buffer
            )
            
            // Set frame callback
            imageReader?.setOnImageAvailableListener(
                ImageReader.OnImageAvailableListener { reader ->
                    onFrame(ImageReader.OnImageAvailableListener { reader })
                },
                handler
            )
            
            // Create VirtualDisplay
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ARCS_Screen",
                width,
                height,
                dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                imageReader?.surface,
                null,
                handler
            )
            
            if (virtualDisplay == null) {
                Timber.e("Failed to create VirtualDisplay")
                _state.value = CaptureState.Error("Failed to create VirtualDisplay")
                return false
            }
            
            _state.value = CaptureState.Running
            Timber.i("Screen capture started: ${width}x${height}@${dpi}dpi")
            return true
            
        } catch (e: Exception) {
            Timber.e(e, "Error starting screen capture")
            _state.value = CaptureState.Error(e.message ?: "Unknown error")
            stop()
            return false
        }
    }
    
    /**
     * Stop screen capture and release resources
     */
    fun stop() {
        try {
            virtualDisplay?.release()
            virtualDisplay = null
            
            imageReader?.close()
            imageReader = null
            
            mediaProjection?.stop()
            mediaProjection = null
            
            handlerThread?.quitSafely()
            handlerThread = null
            handler = null
            
            _state.value = CaptureState.Stopped
            Timber.i("Screen capture stopped")
            
        } catch (e: Exception) {
            Timber.e(e, "Error stopping screen capture")
        }
    }
    
    /**
     * Check if capturing
     */
    fun isCapturing(): Boolean {
        return _state.value is CaptureState.Running
    }
}
