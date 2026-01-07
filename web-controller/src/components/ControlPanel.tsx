import { useConnectionStore } from '../store/connectionStore'
import { useWebSocket } from '../hooks/useWebSocket'
import './ControlPanel.css'

export default function ControlPanel() {
  const { isConnected } = useConnectionStore()
  const { sendSystemCommand, disconnect } = useWebSocket()

  const handleHome = () => sendSystemCommand('home')
  const handleBack = () => sendSystemCommand('back')
  const handleRecent = () => sendSystemCommand('recent_apps')
  const handlePowerMenu = () => sendSystemCommand('power_menu')
  const handleNotifications = () => sendSystemCommand('notifications')

  return (
    <div className="control-panel">
      <div className="control-section">
        <h3>System Controls</h3>
        <div className="button-grid">
          <button
            className="control-button"
            onClick={handleHome}
            disabled={!isConnected}
            title="Home"
          >
            🏠
          </button>
          <button
            className="control-button"
            onClick={handleBack}
            disabled={!isConnected}
            title="Back"
          >
            ◀️
          </button>
          <button
            className="control-button"
            onClick={handleRecent}
            disabled={!isConnected}
            title="Recent Apps"
          >
            ⊞
          </button>
          <button
            className="control-button"
            onClick={handlePowerMenu}
            disabled={!isConnected}
            title="Power Menu"
          >
            ⏻
          </button>
          <button
            className="control-button"
            onClick={handleNotifications}
            disabled={!isConnected}
            title="Notifications"
          >
            🔔
          </button>
        </div>
      </div>

      <div className="control-section">
        <h3>Connection</h3>
        <button
          className="disconnect-button"
          onClick={disconnect}
          disabled={!isConnected}
        >
          Disconnect
        </button>
      </div>
    </div>
  )
}
