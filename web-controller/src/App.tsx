import { useEffect } from 'react'
import VideoDisplay from './components/VideoDisplay'
import ControlPanel from './components/ControlPanel'
import ConnectionDialog from './components/ConnectionDialog'
import StatusBar from './components/StatusBar'
import { useConnectionStore } from './store/connectionStore'
import './App.css'

function App() {
  const { isConnected, initialize } = useConnectionStore()

  useEffect(() => {
    initialize()
  }, [initialize])

  return (
    <div className="app">
      <StatusBar />
      <main className="main-content">
        <VideoDisplay />
        <ControlPanel />
      </main>
      {!isConnected && <ConnectionDialog />}
    </div>
  )
}

export default App
