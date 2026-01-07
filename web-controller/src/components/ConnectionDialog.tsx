import { useState } from 'react'
import { useWebSocket } from '../hooks/useWebSocket'
import './ConnectionDialog.css'

export default function ConnectionDialog() {
  const [serverUrl, setServerUrl] = useState('ws://localhost:8080')
  const [sessionId, setSessionId] = useState('')
  const [isConnecting, setIsConnecting] = useState(false)
  const [error, setError] = useState('')
  const { connect } = useWebSocket()

  const handleConnect = async () => {
    if (!serverUrl || !sessionId) {
      setError('Please enter both server URL and session ID')
      return
    }

    setIsConnecting(true)
    setError('')

    try {
      await connect(serverUrl, sessionId)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Connection failed')
      setIsConnecting(false)
    }
  }

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      handleConnect()
    }
  }

  return (
    <div className="connection-dialog-overlay">
      <div className="connection-dialog">
        <h2>Connect to Device</h2>
        <p className="dialog-subtitle">
          Enter the server URL and session ID to connect to your Android device
        </p>

        <div className="input-group">
          <label htmlFor="server-url">Server URL</label>
          <input
            id="server-url"
            type="text"
            value={serverUrl}
            onChange={(e) => setServerUrl(e.target.value)}
            onKeyPress={handleKeyPress}
            placeholder="ws://192.168.1.100:8080"
            disabled={isConnecting}
          />
        </div>

        <div className="input-group">
          <label htmlFor="session-id">Session ID</label>
          <input
            id="session-id"
            type="text"
            value={sessionId}
            onChange={(e) => setSessionId(e.target.value)}
            onKeyPress={handleKeyPress}
            placeholder="Enter session ID from Android app"
            disabled={isConnecting}
          />
        </div>

        {error && <div className="error-message">{error}</div>}

        <button
          className="connect-button"
          onClick={handleConnect}
          disabled={isConnecting}
        >
          {isConnecting ? 'Connecting...' : 'Connect'}
        </button>
      </div>
    </div>
  )
}
