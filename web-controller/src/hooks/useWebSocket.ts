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
              // The mock-server uses `session_joined` for successful joins.
              if (msg.type === 'join_response' || msg.type === 'session_joined') {
                // Accept either format; treat as success when session exists.
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

  // Reassembly state for fragmented ARCS frames
  const fragments = new Map<number, { total: number, parts: Map<number, Uint8Array>, timestamp: number, isKey: boolean }>()

  const handleBinaryMessage = async (blob: Blob) => {
    const buffer = await blob.arrayBuffer()
    const dv = new DataView(buffer)

    console.log('[WebSocket] Received binary message:', buffer.byteLength, 'bytes')

    // Minimal header validation: magic "ARCS"
    try {
      const magic = String.fromCharCode(
        dv.getUint8(0), dv.getUint8(1), dv.getUint8(2), dv.getUint8(3)
      )
      if (magic !== 'ARCS') {
        console.warn('[WebSocket] Received non-ARCS packet, magic:', magic)
        return
      }
      console.log('[WebSocket] Valid ARCS packet received')

      const version = dv.getUint8(4)
      const type = dv.getUint8(5)
      // Only handle video frame type (0x02)
      if (type !== 0x02) return

      const frameNum = dv.getUint32(6, false)
      // timestamp is 8 bytes big-endian
      const tsHigh = dv.getUint32(10, false)
      const tsLow = dv.getUint32(14, false)
      const timestamp = Number((BigInt(tsHigh) << 32n) | BigInt(tsLow))

      const flags = dv.getUint8(18)
      const payloadLen = dv.getUint32(19, false)

      const isFragment = (flags & 0x04) !== 0
      const isKey = (flags & 0x01) !== 0

      let payloadOffset = 23
      let fragmentIndex = 0
      let totalFragments = 1

      if (isFragment) {
        // fragmentIndex (2 bytes) + totalFragments (2 bytes)
        fragmentIndex = dv.getUint16(23, false)
        totalFragments = dv.getUint16(25, false)
        payloadOffset = 27
      }

      const payload = new Uint8Array(buffer, payloadOffset, payloadLen)

      if (!isFragment) {
        // Single-packet frame: dispatch directly
        console.log('[WebSocket] Dispatching single-packet frame:', frameNum, 'size:', payload.byteLength, 'isKey:', isKey, 'ts:', timestamp)
        window.dispatchEvent(new CustomEvent('videoframe', { detail: { data: payload.buffer, isKey, timestamp } }))
        return
      }

      // Fragmented frame: store and assemble when complete
      let entry = fragments.get(frameNum)
      if (!entry) {
        entry = { total: totalFragments, parts: new Map(), timestamp, isKey }
        fragments.set(frameNum, entry)
      }

      entry.parts.set(fragmentIndex, payload)

      if (entry.parts.size === entry.total) {
        // Reassemble
        const partsArray: Uint8Array[] = []
        for (let i = 0; i < entry.total; i++) {
          const p = entry.parts.get(i)
          if (!p) {
            console.error('Missing fragment', i, 'for frame', frameNum)
            fragments.delete(frameNum)
            return
          }
          partsArray.push(p)
        }

        const totalLen = partsArray.reduce((s, p) => s + p.byteLength, 0)
        const assembled = new Uint8Array(totalLen)
        let off = 0
        for (const p of partsArray) {
          assembled.set(p, off)
          off += p.byteLength
        }

        fragments.delete(frameNum)
        console.log('[WebSocket] Dispatching reassembled frame:', frameNum, 'size:', assembled.byteLength, 'isKey:', entry.isKey, 'ts:', entry.timestamp)
        window.dispatchEvent(new CustomEvent('videoframe', { detail: { data: assembled.buffer, isKey: entry.isKey, timestamp: entry.timestamp } }))
      }
    } catch (e) {
      console.error('[WebSocket] Failed to parse ARCS packet:', e)
    }
  }

  return {
    connect,
    disconnect,
    sendTouchCommand,
    sendKeyCommand,
    sendSystemCommand,
  }
}
