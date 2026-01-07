import { useConnectionStore } from '../store/connectionStore'
import './StatusBar.css'

export default function StatusBar() {
  const { isConnected, deviceInfo, sessionId } = useConnectionStore()

  return (
    <div className="status-bar">
      <div className="status-left">
        <h1 className="app-title">ARCS Web Controller</h1>
      </div>
      
      <div className="status-right">
        {isConnected && deviceInfo ? (
          <>
            <div className="device-info">
              <span className="device-model">{deviceInfo.model}</span>
              <span className="device-version">Android {deviceInfo.androidVersion}</span>
            </div>
            <div className="session-info">
              Session: <span className="session-id">{sessionId?.slice(0, 8)}...</span>
            </div>
          </>
        ) : (
          <div className="connection-status disconnected">
            <span className="status-dot"></span>
            Disconnected
          </div>
        )}
        
        {isConnected && (
          <div className="connection-status connected">
            <span className="status-dot"></span>
            Connected
          </div>
        )}
      </div>
    </div>
  )
}
