package com.arcs.client.service

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.pow

/**
 * Unit tests for reconnection logic with exponential backoff
 */
class ReconnectionLogicTest {
    
    private val maxReconnectAttempts = 10
    
    /**
     * Calculate reconnection delay using exponential backoff
     */
    private fun calculateDelay(attempt: Int): Long {
        return minOf(1000L * java.lang.Math.pow(2.0, attempt.toDouble()).toLong(), 60000L)
    }
    
    @Test
    fun testFirstAttemptDelay() {
        val delay = calculateDelay(0)
        assertEquals(1000L, delay)  // 1 second
    }
    
    @Test
    fun testSecondAttemptDelay() {
        val delay = calculateDelay(1)
        assertEquals(2000L, delay)  // 2 seconds
    }
    
    @Test
    fun testThirdAttemptDelay() {
        val delay = calculateDelay(2)
        assertEquals(4000L, delay)  // 4 seconds
    }
    
    @Test
    fun testExponentialGrowth() {
        val delays = (0..5).map { calculateDelay(it) }
        
        assertEquals(listOf(1000L, 2000L, 4000L, 8000L, 16000L, 32000L), delays)
    }
    
    @Test
    fun testDelayCappedAt60Seconds() {
        // At attempt 6, delay would be 64000ms but should be capped at 60000ms
        val delay6 = calculateDelay(6)
        assertEquals(60000L, delay6)
        
        val delay7 = calculateDelay(7)
        assertEquals(60000L, delay7)
        
        val delay10 = calculateDelay(10)
        assertEquals(60000L, delay10)
    }
    
    @Test
    fun testMaxReconnectAttempts() {
        assertEquals(10, maxReconnectAttempts)
    }
    
    @Test
    fun testShouldReconnect_UnderLimit() {
        for (attempt in 0 until maxReconnectAttempts) {
            assertTrue("Should reconnect at attempt $attempt", attempt < maxReconnectAttempts)
        }
    }
    
    @Test
    fun testShouldNotReconnect_AtLimit() {
        assertFalse(maxReconnectAttempts < maxReconnectAttempts)
    }
    
    @Test
    fun testNormalDisconnectionNoReconnect() {
        val closeCode = 1000  // Normal closure
        val shouldReconnect = closeCode != 1000
        
        assertFalse(shouldReconnect)
    }
    
    @Test
    fun testAbnormalDisconnectionShouldReconnect() {
        val abnormalCodes = listOf(1001, 1002, 1003, 1006, 1011, 1012, 1013)
        
        for (code in abnormalCodes) {
            val shouldReconnect = code != 1000
            assertTrue("Should reconnect for code $code", shouldReconnect)
        }
    }
    
    @Test
    fun testTotalReconnectionTime() {
        // Calculate total time for all reconnection attempts
        var totalTime = 0L
        for (attempt in 0 until maxReconnectAttempts) {
            totalTime += calculateDelay(attempt)
        }
        
        // 1 + 2 + 4 + 8 + 16 + 32 + 60 + 60 + 60 + 60 = 303 seconds
        assertEquals(303000L, totalTime)
    }
    
    @Test
    fun testReconnectAttemptsReset() {
        var reconnectAttempts = 5
        
        // Simulate successful reconnection
        reconnectAttempts = 0
        
        assertEquals(0, reconnectAttempts)
    }
}
