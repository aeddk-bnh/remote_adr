package com.arcs.client.service

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ServiceState state machine
 */
class ServiceStateTest {
    
    // Simulated state machine for testing
    private var currentState = ServiceState.STOPPED
    private val stateLock = Any()
    
    private fun transitionState(
        from: ServiceState, 
        to: ServiceState
    ): Boolean {
        synchronized(stateLock) {
            // Validate allowed transitions
            val isValid = when (from) {
                ServiceState.STOPPED -> to == ServiceState.CONNECTING
                ServiceState.CONNECTING -> to == ServiceState.CONNECTED || to == ServiceState.STOPPED
                ServiceState.CONNECTED -> to == ServiceState.AUTHENTICATED || to == ServiceState.STOPPING || to == ServiceState.CONNECTING
                ServiceState.AUTHENTICATED -> to == ServiceState.STREAMING || to == ServiceState.STOPPING || to == ServiceState.CONNECTING
                ServiceState.STREAMING -> to == ServiceState.STOPPING || to == ServiceState.CONNECTING
                ServiceState.STOPPING -> to == ServiceState.STOPPED
            }
            
            if (currentState == from && isValid) {
                currentState = to
                return true
            }
            return false
        }
    }
    
    private fun isInState(vararg validStates: ServiceState): Boolean {
        synchronized(stateLock) {
            return currentState in validStates
        }
    }
    
    @Before
    fun setUp() {
        currentState = ServiceState.STOPPED
    }
    
    @Test
    fun testInitialState() {
        assertEquals(ServiceState.STOPPED, currentState)
    }
    
    @Test
    fun testValidTransition_StoppedToConnecting() {
        val result = transitionState(
            ServiceState.STOPPED,
            ServiceState.CONNECTING
        )
        
        assertTrue(result)
        assertEquals(ServiceState.CONNECTING, currentState)
    }
    
    @Test
    fun testValidTransition_ConnectedToAuthenticated() {
        currentState = ServiceState.CONNECTED
        
        val result = transitionState(
            ServiceState.CONNECTED,
            ServiceState.AUTHENTICATED
        )
        
        assertTrue(result)
        assertEquals(ServiceState.AUTHENTICATED, currentState)
    }
    
    @Test
    fun testValidTransition_AuthenticatedToStreaming() {
        currentState = ServiceState.AUTHENTICATED
        
        val result = transitionState(
            ServiceState.AUTHENTICATED,
            ServiceState.STREAMING
        )
        
        assertTrue(result)
        assertEquals(ServiceState.STREAMING, currentState)
    }
    
    @Test
    fun testInvalidTransition_StoppedToStreaming() {
        val result = transitionState(
            ServiceState.STOPPED,
            ServiceState.STREAMING
        )
        
        assertFalse(result)
        assertEquals(ServiceState.STOPPED, currentState)
    }
    
    @Test
    fun testInvalidTransition_WrongFromState() {
        currentState = ServiceState.CONNECTING
        
        val result = transitionState(
            ServiceState.STOPPED,  // Wrong from state
            ServiceState.CONNECTED
        )
        
        assertFalse(result)
        assertEquals(ServiceState.CONNECTING, currentState)
    }
    
    @Test
    fun testIsInState_SingleState() {
        currentState = ServiceState.STREAMING
        
        assertTrue(isInState(ServiceState.STREAMING))
        assertFalse(isInState(ServiceState.STOPPED))
    }
    
    @Test
    fun testIsInState_MultipleStates() {
        currentState = ServiceState.CONNECTED
        
        assertTrue(isInState(
            ServiceState.CONNECTED,
            ServiceState.AUTHENTICATED
        ))
        
        assertFalse(isInState(
            ServiceState.STOPPED,
            ServiceState.STREAMING
        ))
    }
    
    @Test
    fun testFullLifecycle() {
        // STOPPED -> CONNECTING
        assertTrue(transitionState(
            ServiceState.STOPPED,
            ServiceState.CONNECTING
        ))
        
        // CONNECTING -> CONNECTED
        assertTrue(transitionState(
            ServiceState.CONNECTING,
            ServiceState.CONNECTED
        ))
        
        // CONNECTED -> AUTHENTICATED
        assertTrue(transitionState(
            ServiceState.CONNECTED,
            ServiceState.AUTHENTICATED
        ))
        
        // AUTHENTICATED -> STREAMING
        assertTrue(transitionState(
            ServiceState.AUTHENTICATED,
            ServiceState.STREAMING
        ))
        
        // STREAMING -> STOPPING
        assertTrue(transitionState(
            ServiceState.STREAMING,
            ServiceState.STOPPING
        ))
        
        // STOPPING -> STOPPED
        assertTrue(transitionState(
            ServiceState.STOPPING,
            ServiceState.STOPPED
        ))
        
        assertEquals(ServiceState.STOPPED, currentState)
    }
    
    @Test
    fun testConcurrentStateTransitions() {
        val threads = mutableListOf<Thread>()
        var successCount = 0
        
        // Try to transition from STOPPED to CONNECTING from multiple threads
        for (i in 1..10) {
            threads.add(Thread {
                if (transitionState(
                    ServiceState.STOPPED,
                    ServiceState.CONNECTING
                )) {
                    synchronized(this) {
                        successCount++
                    }
                }
            })
        }
        
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        
        // Only one thread should succeed
        assertEquals(1, successCount)
        assertEquals(ServiceState.CONNECTING, currentState)
    }
}
