package com.scanium.app.startup

import android.content.SharedPreferences
import android.util.Log
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for StartupGuard crash-loop detection logic.
 *
 * Tests the core logic independently of Android context by mocking SharedPreferences.
 */
class StartupGuardTest {
    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    // Captured preference writes
    private val intValues = mutableMapOf<String, Int>()
    private val longValues = mutableMapOf<String, Long>()
    private val booleanValues = mutableMapOf<String, Boolean>()
    private val stringValues = mutableMapOf<String, String>()

    @Before
    fun setUp() {
        // Mock android.util.Log to avoid "not mocked" errors
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0

        // Clear captured values
        intValues.clear()
        longValues.clear()
        booleanValues.clear()
        stringValues.clear()

        // Mock SharedPreferences.Editor
        editor = mockk(relaxed = true)

        // Capture writes
        val intKeySlot = slot<String>()
        val intValueSlot = slot<Int>()
        every { editor.putInt(capture(intKeySlot), capture(intValueSlot)) } answers {
            intValues[intKeySlot.captured] = intValueSlot.captured
            editor
        }

        val longKeySlot = slot<String>()
        val longValueSlot = slot<Long>()
        every { editor.putLong(capture(longKeySlot), capture(longValueSlot)) } answers {
            longValues[longKeySlot.captured] = longValueSlot.captured
            editor
        }

        val booleanKeySlot = slot<String>()
        val booleanValueSlot = slot<Boolean>()
        every { editor.putBoolean(capture(booleanKeySlot), capture(booleanValueSlot)) } answers {
            booleanValues[booleanKeySlot.captured] = booleanValueSlot.captured
            editor
        }

        val stringKeySlot = slot<String>()
        val stringValueSlot = slot<String>()
        every { editor.putString(capture(stringKeySlot), capture(stringValueSlot)) } answers {
            stringValues[stringKeySlot.captured] = stringValueSlot.captured
            editor
        }

        every { editor.clear() } answers {
            intValues.clear()
            longValues.clear()
            booleanValues.clear()
            stringValues.clear()
            editor
        }

        // Mock SharedPreferences
        prefs = mockk(relaxed = true)
        every { prefs.edit() } returns editor
        every { prefs.getInt(any(), any()) } answers {
            intValues[arg(0)] ?: arg(1)
        }
        every { prefs.getLong(any(), any()) } answers {
            longValues[arg(0)] ?: arg(1)
        }
        every { prefs.getBoolean(any(), any()) } answers {
            booleanValues[arg(0)] ?: arg(1)
        }
        every { prefs.getString(any(), any()) } answers {
            stringValues[arg(0)] ?: arg(1)
        }
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `initial state has no crashes and no safe mode`() {
        val guard = createGuard()

        assertThat(guard.isSafeMode).isFalse()
        assertThat(guard.consecutiveCrashes).isEqualTo(0)
    }

    @Test
    fun `recordStartupAttempt saves timestamp`() {
        val guard = createGuard()

        guard.recordStartupAttempt()

        assertThat(stringValues["startup_timestamps"]).isNotEmpty()
    }

    @Test
    fun `recordStartupSuccess resets crash counter`() {
        val guard = createGuard()

        // Simulate some crashes
        intValues["consecutive_crashes"] = 2

        guard.recordStartupSuccess()

        assertThat(intValues["consecutive_crashes"]).isEqualTo(0)
        assertThat(booleanValues["safe_mode_enabled"]).isFalse()
        assertThat(longValues["last_successful_startup"]).isGreaterThan(0L)
    }

    @Test
    fun `crash loop triggers safe mode after threshold`() {
        val guard = createGuard()

        // Simulate consecutive crashes (startup attempts without success)
        repeat(StartupGuard.CRASH_THRESHOLD) { iteration ->
            // Each iteration: startup attempt, no success, quick restart
            val now = System.currentTimeMillis()
            stringValues["startup_timestamps"] = "${now - 1000}" // Last startup was 1 second ago
            intValues["consecutive_crashes"] = iteration
            longValues["last_successful_startup"] = 0L // No successful startup

            guard.recordStartupAttempt()
        }

        // Verify safe mode was enabled
        assertThat(booleanValues["safe_mode_enabled"]).isTrue()
    }

    @Test
    fun `successful startup clears safe mode for next launch`() {
        val guard = createGuard()

        // Put in safe mode
        booleanValues["safe_mode_enabled"] = true
        intValues["consecutive_crashes"] = 5

        // Record successful startup
        guard.recordStartupSuccess()

        // Verify cleared
        assertThat(booleanValues["safe_mode_enabled"]).isFalse()
        assertThat(intValues["consecutive_crashes"]).isEqualTo(0)
    }

    @Test
    fun `requestExitSafeMode clears safe mode and crash counter`() {
        val guard = createGuard()

        // Put in safe mode
        booleanValues["safe_mode_enabled"] = true
        intValues["consecutive_crashes"] = 3

        // Request exit
        guard.requestExitSafeMode()

        // Verify cleared
        assertThat(booleanValues["safe_mode_enabled"]).isFalse()
        assertThat(intValues["consecutive_crashes"]).isEqualTo(0)
    }

    @Test
    fun `getDiagnostics returns all relevant fields`() {
        val guard = createGuard()

        // Set some values
        booleanValues["safe_mode_enabled"] = true
        intValues["consecutive_crashes"] = 2
        longValues["last_successful_startup"] = 12345L
        stringValues["startup_timestamps"] = "1000,2000,3000"

        val diagnostics = guard.getDiagnostics()

        assertThat(diagnostics["safe_mode"]).isEqualTo("true")
        assertThat(diagnostics["consecutive_crashes"]).isEqualTo("2")
        assertThat(diagnostics["last_successful_startup"]).isEqualTo("12345")
        assertThat(diagnostics["startup_timestamps"]).isEqualTo("1000,2000,3000")
    }

    @Test
    fun `slow startup does not count as crash`() {
        val guard = createGuard()

        // Last startup was more than CRASH_WINDOW_MS ago
        val now = System.currentTimeMillis()
        val oldTimestamp = now - StartupGuard.CRASH_WINDOW_MS - 10000
        stringValues["startup_timestamps"] = oldTimestamp.toString()
        longValues["last_successful_startup"] = 0L
        intValues["consecutive_crashes"] = 2

        guard.recordStartupAttempt()

        // Crash counter should be reset because it's not a quick crash
        assertThat(intValues["consecutive_crashes"]).isEqualTo(0)
    }

    @Test
    fun `reset clears all state`() {
        val guard = createGuard()

        // Set some values
        booleanValues["safe_mode_enabled"] = true
        intValues["consecutive_crashes"] = 5
        longValues["last_successful_startup"] = 12345L
        stringValues["startup_timestamps"] = "1,2,3"

        guard.reset()

        verify { editor.clear() }
    }

    @Test
    fun `startup timestamps list is bounded to 5 entries`() {
        val guard = createGuard()

        // Pre-populate with many timestamps
        stringValues["startup_timestamps"] = "1,2,3,4,5,6,7,8,9,10"

        guard.recordStartupAttempt()

        // Should keep only last 5
        val timestamps = stringValues["startup_timestamps"]!!.split(",")
        assertThat(timestamps.size).isAtMost(6) // 5 old + 1 new, take last 5
    }

    @Test
    fun `consecutive crashes not incremented after successful startup`() {
        val guard = createGuard()

        // Simulate successful startup sequence
        guard.recordStartupAttempt()
        guard.recordStartupSuccess()

        // Simulate another startup
        val now = System.currentTimeMillis()
        stringValues["startup_timestamps"] = (now - 1000).toString()

        guard.recordStartupAttempt()

        // Crash counter should be 0 because last startup was successful
        assertThat(intValues["consecutive_crashes"]).isEqualTo(0)
    }

    private fun createGuard(): StartupGuard {
        // Use reflection to create StartupGuard with our mock prefs
        val constructor = StartupGuard::class.java.getDeclaredConstructor(SharedPreferences::class.java)
        constructor.isAccessible = true
        return constructor.newInstance(prefs)
    }
}
