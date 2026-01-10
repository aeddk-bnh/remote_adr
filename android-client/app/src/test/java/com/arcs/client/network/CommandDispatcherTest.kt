package com.arcs.client.network

import com.arcs.client.input.KeyInjector
import com.arcs.client.input.TouchInjector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

/**
 * Unit tests for CommandDispatcher
 * Verifies command parsing and routing logic
 */
class CommandDispatcherTest {

    @Mock
    private lateinit var touchInjector: TouchInjector
    
    @Mock
    private lateinit var keyInjector: KeyInjector
    
    private lateinit var dispatcher: CommandDispatcher
    private val testScope = TestCoroutineScope(TestCoroutineDispatcher())
    
    // Capture result callbacks
    private var lastResult: String? = null
    
    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        
        dispatcher = CommandDispatcher(
            touchInjector,
            keyInjector,
            testScope
        ) { result ->
            lastResult = result
        }
    }
    
    @Test
    fun testDispatchTouchTap() {
        val json = """
            {
                "type": "touch",
                "action": "tap",
                "x": 100,
                "y": 200
            }
        """.trimIndent()
        
        // Mock success
        runBlockingTest {
            // Needed to use 'runBlockingTest' or similar for suspend functions
            // But dispatcher launches its own coroutine 'scope.launch(Dispatchers.IO)'
            // This is tricky because Dispatchers.IO is hardcoded in CommandDispatcher
            // However, verify logic should still work if we wait or if testScope controls execution
            
            // Note: Since CommandDispatcher hardcodes Dispatchers.IO, tests might be flaky without swapping Dispatchers.
            // But let's assume for this test we verify that THE CALL was parsing parameters correctly.
            
            // Mocking suspend function requires specific syntax if using blocking verification
            // validation of 'tap' call parameters is the goal
        }
        
        dispatcher.dispatch(json)
        
        // Wait for coroutine? TestScope doesn't control Dispatchers.IO unless injected.
        // CommandDispatcher uses injected 'scope' but launches with 'Dispatchers.IO'.
        // Code: scope.launch(Dispatchers.IO)
        // If we want to unit test this properly, we should refactor CommandDispatcher to inject Dispatcher.
        // Or we rely on 'eventually' or just sleep for a moment (integration style).
        // Let's rely on simple sleep for this quick test environment.
        
        Thread.sleep(100)
        
        runBlockingTest {
             verify(touchInjector).tap(100, 200)
        }
    }
    
    @Test
    fun testDispatchSwipe() {
        val json = """
            {
                "type": "touch",
                "action": "swipe",
                "start_x": 10,
                "start_y": 20,
                "end_x": 30,
                "end_y": 40,
                "duration": 500
            }
        """.trimIndent()
        
        dispatcher.dispatch(json)
        Thread.sleep(100)
        
        runBlockingTest {
            verify(touchInjector).swipe(10, 20, 30, 40, 500L)
        }
    }
    
    @Test
    fun testDispatchText() {
        val json = """
            {
                "type": "key",
                "action": "text",
                "text": "Hello"
            }
        """.trimIndent()
        
        dispatcher.dispatch(json)
        
        // Key handler logic is NOT suspend in current code (based on CommandDispatcher I read)
        // Wait, check CommandDispatcher.kt code:
        // handleKeyCommand -> keyInjector.sendText (not suspend in KeyInjector)
        // AND handleKeyCommand is NOT launched in coroutine?
        // Let's assert: 'dispatch' calls 'handleKeyCommand'.
        // 'handleKeyCommand' (step 414 line 167) does NOT use scope.launch. It runs synchronously!
        
        verify(keyInjector).sendText("Hello")
    }
    
    @Test
    fun testDispatchKeyPress() {
        val json = """
            {
                "type": "key",
                "action": "press",
                "keycode": "KEYCODE_ENTER"
            }
        """.trimIndent()
        
        // Mock parsing
        `when`(keyInjector.parseKeyCode("KEYCODE_ENTER")).thenReturn(66) // 66 is ENTER
        
        dispatcher.dispatch(json)
        
        verify(keyInjector).parseKeyCode("KEYCODE_ENTER")
        verify(keyInjector).sendKeyCode(66)
    }
    
    @Test
    fun testUnknownCommandRespondsWithError() {
        val json = """
            {
                "type": "unknown_type"
            }
        """.trimIndent()
        
        dispatcher.dispatch(json)
        
        assertNotNull(lastResult)
        assertTrue(lastResult!!.contains("unknown_command"))
        assertTrue(lastResult!!.contains("\"success\":false"))
    }
    
    @Test
    fun testMalformedJson() {
        val json = "not a json"
        
        dispatcher.dispatch(json)
        
        assertNotNull(lastResult)
        assertTrue(lastResult!!.contains("parsing_error"))
    }
}
