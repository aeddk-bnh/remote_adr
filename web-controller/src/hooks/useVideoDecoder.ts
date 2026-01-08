import { useEffect, useCallback, useRef, RefObject } from 'react'

export function useVideoDecoder(canvasRef: RefObject<HTMLCanvasElement>) {
  const decoderRef = useRef<VideoDecoder | null>(null)
  const ctxRef = useRef<CanvasRenderingContext2D | null>(null)

  const initializeDecoder = useCallback((width: number, height: number) => {
    if (!canvasRef.current) return

    // Get canvas context
    ctxRef.current = canvasRef.current.getContext('2d')
    if (!ctxRef.current) return

    // Check WebCodecs support
    if (!('VideoDecoder' in window)) {
      console.error('WebCodecs not supported')
      return
    }

    // Create video decoder
    decoderRef.current = new VideoDecoder({
      output: (frame) => {
        if (!ctxRef.current || !canvasRef.current) return

        console.log('[VideoDecoder] Frame decoded and rendering:', frame.codedWidth, 'x', frame.codedHeight)
        // Draw frame to canvas
        ctxRef.current.drawImage(frame, 0, 0, canvasRef.current.width, canvasRef.current.height)
        frame.close()
      },
      error: (error) => {
        console.error('[VideoDecoder] Decoder error:', error)
      },
    })

    // Configure decoder for H.264
    decoderRef.current.configure({
      codec: 'avc1.42E01E', // H.264 Baseline Profile Level 3.0
      codedWidth: width,
      codedHeight: height,
      hardwareAcceleration: 'prefer-hardware',
    })

    console.log('[VideoDecoder] Decoder initialized and configured:', width, 'x', height, 'codec: avc1.42E01E')
  }, [canvasRef])

  const decodeFrame = useCallback((data: ArrayBuffer, isKey: boolean, timestamp: number) => {
    if (!decoderRef.current || decoderRef.current.state !== 'configured') {
      console.warn('[VideoDecoder] Decoder not ready, state:', decoderRef.current?.state)
      return
    }

    try {
      const chunk = new EncodedVideoChunk({
        type: isKey ? 'key' : 'delta',
        timestamp: timestamp, // packet timestamp is in microseconds
        data: data,
      })

      console.log('[VideoDecoder] Decoding frame: type=', isKey ? 'KEY' : 'DELTA', 'size=', data.byteLength, 'ts=', timestamp)
      decoderRef.current.decode(chunk)
    } catch (error) {
      console.error('[VideoDecoder] Failed to decode frame:', error)
    }
  }, [])

  useEffect(() => {
    // Listen for video frames (detail: { data: ArrayBuffer, isKey: boolean, timestamp: number })
    const handleVideoFrame = (event: Event) => {
      const customEvent = event as CustomEvent<{ data: ArrayBuffer, isKey: boolean, timestamp: number }>
      const { data, isKey, timestamp } = customEvent.detail
      console.log('[VideoDecoder] Received videoframe event: size=', data.byteLength, 'isKey=', isKey, 'ts=', timestamp)
      decodeFrame(data, !!isKey, Number(timestamp))
    }

    console.log('[VideoDecoder] Registering videoframe event listener')
    window.addEventListener('videoframe', handleVideoFrame)

    return () => {
      window.removeEventListener('videoframe', handleVideoFrame)
      
      if (decoderRef.current) {
        decoderRef.current.close()
        decoderRef.current = null
      }
    }
  }, [decodeFrame])

  return {
    initializeDecoder,
    decodeFrame,
  }
}
