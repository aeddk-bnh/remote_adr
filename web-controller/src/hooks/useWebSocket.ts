import { useCallback } from 'react'
import { useConnectionStore } from '../store/connectionStore'

export function useWebSocket() {
  const { ws, setConnected, setSessionId, setDeviceInfo, setWebSocket, reset } = useConnectionStore()

  const connect = useCallback(async (serverUrl: string, sessionId: string) => {
    return new Promise<void>((resolve, reject) => {
      try {
        const websocket = new WebSocket(serverUrl)

        websocket.onopen = () => {
          console.log('WebSocket connected')
          
          // Send join session message
          const joinMsg = {
            type: 'join_session',
            session_id: sessionId,
            jwt_token: '', // Will be populated from auth flow
          }
          
          websocket.send(JSON.stringify(joinMsg))
          setWebSocket(websocket)
          setSessionId(sessionId)
        }

        websocket.onmessage = (event) => {
          if (typeof event.data === 'string') {
            handleJsonMessage(event.data)
          } else if (event.data instanceof Blob) {
            handleBinaryMessage(event.data)
          }
        }

        websocket.onerror = (error) => {
          console.error('WebSocket error:', error)
          reject(new Error('WebSocket connection failed'))
        }

        websocket.onclose = () => {
          console.log('WebSocket disconnected')
          reset()
        }

        // Resolve after receiving join_response
        const originalOnMessage = websocket.onmessage
        websocket.onmessage = (event) => {
          if (typeof event.data === 'string') {
            try {
              const msg = JSON.parse(event.data)
              if (msg.type === 'join_response') {
                if (msg.success) {
                  setConnected(true)
                  if (msg.device_info) {
                    setDeviceInfo({
                      model: msg.device_info.model || 'Unknown',
                      androidVersion: msg.device_info.android_version || 'Unknown',
                      width: msg.video_config?.width || 1080,
                      height: msg.video_config?.height || 2400,
                    })
                  }
                  resolve()
                } else {
                  reject(new Error('Failed to join session'))
                }
                websocket.onmessage = originalOnMessage
              }
            } catch (e) {
              console.error('Failed to parse message:', e)
            }
          }
          originalOnMessage?.call(websocket, event)
        }
      } catch (error) {
        reject(error)
      }
    })
  }, [setConnected, setSessionId, setDeviceInfo, setWebSocket, reset])

  const disconnect = useCallback(() => {
    if (ws) {
      ws.close()
      reset()
    }
  }, [ws, reset])

  const sendTouchCommand = useCallback((action: string, x: number, y: number, duration?: number) => {
    if (!ws) return

    const cmd: any = {
      type: 'touch',
      action,
    }

    if (action === 'tap' || action === 'long_press') {
      cmd.x = x
      cmd.y = y
      if (duration) cmd.duration = duration
    } else if (action === 'swipe') {
      cmd.start_x = x
      cmd.start_y = y
      cmd.end_x = x + (duration || 0)
      cmd.end_y = y
    }

    ws.send(JSON.stringify(cmd))
  }, [ws])

  const sendKeyCommand = useCallback((action: string, keycode?: number, text?: string) => {
    if (!ws) return

    const cmd: any = {
      type: 'key',
      action,
    }

    if (action === 'text' && text) {
      cmd.text = text
    } else if (action === 'press' && keycode) {
      cmd.keycode = keycode
    }

    ws.send(JSON.stringify(cmd))
  }, [ws])

  const sendSystemCommand = useCallback((action: string) => {
    if (!ws) return

    const cmd = {
      type: 'system',
      action,
    }

    ws.send(JSON.stringify(cmd))
  }, [ws])

  const handleJsonMessage = (data: string) => {
    try {
      const msg = JSON.parse(data)
      console.log('Received message:', msg.type)

      if (msg.type === 'error') {
        console.error('Server error:', msg.message)
      }
    } catch (e) {
      console.error('Failed to parse JSON message:', e)
    }
  }

  const handleBinaryMessage = async (blob: Blob) => {
    // Binary video frames are handled by the video decoder
    const arrayBuffer = await blob.arrayBuffer()
    window.dispatchEvent(new CustomEvent('videoframe', { detail: arrayBuffer }))
  }

  return {
    connect,
    disconnect,
    sendTouchCommand,
    sendKeyCommand,
    sendSystemCommand,
  }
}
