import { useEffect, useRef, useState } from 'react'
import { useConnectionStore } from '../store/connectionStore'
import { useVideoDecoder } from '../hooks/useVideoDecoder'
import { useTouchHandler } from '../hooks/useTouchHandler'
import './VideoDisplay.css'

export default function VideoDisplay() {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const containerRef = useRef<HTMLDivElement>(null)
  const { isConnected, deviceInfo } = useConnectionStore()
  const { initializeDecoder, decodeFrame } = useVideoDecoder(canvasRef)
  const { handleTouchStart, handleTouchMove, handleTouchEnd } = useTouchHandler(
    containerRef,
    deviceInfo
  )
  const [dimensions, setDimensions] = useState({ width: 0, height: 0 })

  useEffect(() => {
    if (isConnected && deviceInfo) {
      initializeDecoder(deviceInfo.width, deviceInfo.height)
      updateDimensions()
    }
  }, [isConnected, deviceInfo, initializeDecoder])

  useEffect(() => {
    const handleResize = () => updateDimensions()
    window.addEventListener('resize', handleResize)
    return () => window.removeEventListener('resize', handleResize)
  }, [])

  const updateDimensions = () => {
    if (!containerRef.current || !deviceInfo) return

    const container = containerRef.current
    const containerWidth = container.clientWidth
    const containerHeight = container.clientHeight
    const aspectRatio = deviceInfo.width / deviceInfo.height

    let width = containerWidth
    let height = containerWidth / aspectRatio

    if (height > containerHeight) {
      height = containerHeight
      width = containerHeight * aspectRatio
    }

    setDimensions({ width, height })
  }

  const handlePointerDown = (e: React.PointerEvent) => {
    e.preventDefault()
    handleTouchStart(e.nativeEvent)
  }

  const handlePointerMove = (e: React.PointerEvent) => {
    e.preventDefault()
    handleTouchMove(e.nativeEvent)
  }

  const handlePointerUp = (e: React.PointerEvent) => {
    e.preventDefault()
    handleTouchEnd(e.nativeEvent)
  }

  return (
    <div className="video-display" ref={containerRef}>
      {isConnected ? (
        <canvas
          ref={canvasRef}
          width={dimensions.width}
          height={dimensions.height}
          className="video-canvas"
          onPointerDown={handlePointerDown}
          onPointerMove={handlePointerMove}
          onPointerUp={handlePointerUp}
          onPointerCancel={handlePointerUp}
          style={{
            width: `${dimensions.width}px`,
            height: `${dimensions.height}px`,
          }}
        />
      ) : (
        <div className="video-placeholder">
          <div className="placeholder-content">
            <div className="placeholder-icon">📱</div>
            <p>Connect to a device to start</p>
          </div>
        </div>
      )}
    </div>
  )
}
