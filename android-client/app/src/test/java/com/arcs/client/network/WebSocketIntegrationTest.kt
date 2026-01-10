package com.arcs.client.network

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import timber.log.Timber

@RunWith(RobolectricTestRunner::class)
class WebSocketIntegrationTest {
    
    private lateinit var mockWebServer: MockWebServer
    private lateinit var client: WebSocketClient
    private lateinit var messageQueue: BlockingQueue<String>
    private lateinit var statusQueue: BlockingQueue<String>
    
    @Before
    fun setUp() {
        // Plant timber to avoid NPE if any logs called
        if (Timber.treeCount == 0) {
            Timber.plant(Timber.DebugTree())
        }

        mockWebServer = MockWebServer()
        mockWebServer.start()
        
        messageQueue = LinkedBlockingQueue()
        statusQueue = LinkedBlockingQueue()
        
        val url = mockWebServer.url("/").toString()
        
        client = WebSocketClient(
            serverUrl = url,
            onMessage = { msg -> messageQueue.offer(msg) },
            onBinaryMessage = { },
            onConnected = { statusQueue.offer("CONNECTED") },
            onDisconnected = { _, _ -> statusQueue.offer("DISCONNECTED") },
            onError = { t -> 
                statusQueue.offer("ERROR: ${t.message}") 
            }
        )
    }
    
    @After
    fun tearDown() {
        try {
            client.shutdown()
            mockWebServer.shutdown()
        } catch (e: Exception) {
            // Ignore shutdown errors
        }
    }
    
    @Test
    fun testConnectionAndMessageExchange() {
        // Enqueue upgrade response
        mockWebServer.enqueue(MockResponse().withWebSocketUpgrade(object : okhttp3.WebSocketListener() {
            override fun onOpen(webSocket: okhttp3.WebSocket, response: okhttp3.Response) {
                // Server sends auth request upon connection
                webSocket.send("""{"type":"auth_request","device_id":"test_id"}""")
            }
            
            override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
                // Server receives response and replies
                if (text.contains("auth_response")) {
                     webSocket.send("""{"type":"auth_success"}""")
                }
            }
        }))
        
        // Connect
        client.connect()
        
        // Verify Connected
        val status = statusQueue.poll(5, TimeUnit.SECONDS)
        assertEquals("CONNECTED", status)
        
        // Verify Auth Request received
        val msg1 = messageQueue.poll(5, TimeUnit.SECONDS)
        assertNotNull("Should receive auth_request", msg1)
        assertTrue(msg1!!.contains("auth_request"))
        
        // Send Response
        val sent = client.sendText("""{"type":"auth_response"}""")
        assertTrue("Send text should return true", sent)
        
        // Verify Success received
        val msg2 = messageQueue.poll(5, TimeUnit.SECONDS)
        assertNotNull("Should receive auth_success", msg2)
        assertTrue(msg2!!.contains("auth_success"))
    }
}
