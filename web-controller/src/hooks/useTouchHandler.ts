import { useCallback, useRef, RefObject } from 'react'
import { useWebSocket } from './useWebSocket'

interface DeviceInfo {
  width: number
  height: number
}

export function useTouchHandler(
  containerRef: RefObject<HTMLElement>,
  deviceInfo: DeviceInfo | null
) {
  const { sendTouchCommand } = useWebSocket()
  const startPosRef = useRef<{ x: number; y: number } | null>(null)
  const startTimeRef = useRef<number>(0)

  const mapToDevice = useCallback(
    (clientX: number, clientY: number) => {
      if (!containerRef.current || !deviceInfo) {
        return { x: 0, y: 0 }
      }

      const rect = containerRef.current.getBoundingClientRect()
      const relX = (clientX - rect.left) / rect.width
      const relY = (clientY - rect.top) / rect.height

      return {
        x: Math.round(relX * deviceInfo.width),
        y: Math.round(relY * deviceInfo.height),
      }
    },
    [containerRef, deviceInfo]
  )

  const handleTouchStart = useCallback(
    (event: PointerEvent) => {
      const pos = mapToDevice(event.clientX, event.clientY)
      startPosRef.current = pos
      startTimeRef.current = Date.now()
    },
    [mapToDevice]
  )

  const handleTouchMove = useCallback(
    (event: PointerEvent) => {
      // Could implement drag visualization here
    },
    []
  )

  const handleTouchEnd = useCallback(
    (event: PointerEvent) => {
      if (!startPosRef.current) return

      const endPos = mapToDevice(event.clientX, event.clientY)
      const duration = Date.now() - startTimeRef.current

      const dx = endPos.x - startPosRef.current.x
      const dy = endPos.y - startPosRef.current.y
      const distance = Math.sqrt(dx * dx + dy * dy)

      const SWIPE_THRESHOLD = 20
      const LONG_PRESS_THRESHOLD = 500

      if (distance < SWIPE_THRESHOLD) {
        // Tap or long press
        if (duration >= LONG_PRESS_THRESHOLD) {
          sendTouchCommand('long_press', startPosRef.current.x, startPosRef.current.y, duration)
        } else {
          sendTouchCommand('tap', startPosRef.current.x, startPosRef.current.y)
        }
      } else {
        // Swipe
        sendTouchCommand('swipe', startPosRef.current.x, startPosRef.current.y, dx)
      }

      startPosRef.current = null
    },
    [mapToDevice, sendTouchCommand]
  )

  return {
    handleTouchStart,
    handleTouchMove,
    handleTouchEnd,
  }
}
