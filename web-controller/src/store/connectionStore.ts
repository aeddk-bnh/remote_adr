import { create } from 'zustand'

interface DeviceInfo {
  model: string
  androidVersion: string
  width: number
  height: number
}

interface ConnectionState {
  isConnected: boolean
  sessionId: string | null
  deviceInfo: DeviceInfo | null
  ws: WebSocket | null
  
  setConnected: (connected: boolean) => void
  setSessionId: (sessionId: string) => void
  setDeviceInfo: (info: DeviceInfo) => void
  setWebSocket: (ws: WebSocket | null) => void
  reset: () => void
  initialize: () => void
}

export const useConnectionStore = create<ConnectionState>((set) => ({
  isConnected: false,
  sessionId: null,
  deviceInfo: null,
  ws: null,
  
  setConnected: (connected) => set({ isConnected: connected }),
  
  setSessionId: (sessionId) => set({ sessionId }),
  
  setDeviceInfo: (info) => set({ deviceInfo: info }),
  
  setWebSocket: (ws) => set({ ws }),
  
  reset: () => set({
    isConnected: false,
    sessionId: null,
    deviceInfo: null,
    ws: null,
  }),
  
  initialize: () => {
    // Initialization logic if needed
  },
}))
