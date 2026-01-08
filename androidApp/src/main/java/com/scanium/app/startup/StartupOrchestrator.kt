package com.scanium.app.startup

import android.util.Log

/**
 * Orchestrates phased app startup to ensure reliability and responsiveness.
 *
 * **Startup Phases:**
 *
 * **Phase 1 - Critical (Application.onCreate)**
 * - Crash detection and safe mode check
 * - Minimal DI container creation
 * - Locale setup (with fallback)
 * - Crash reporting initialization
 *
 * **Phase 2 - Deferred (After first frame rendered)**
 * - Telemetry warmup
 * - Cache preloading
 * - ML model initialization
 * - Non-critical preferences loading
 *
 * **Phase 3 - Background (Idle callback or delayed)**
 * - Network preflight checks
 * - Version update checks
 * - Analytics session start
 *
 * In safe mode, Phase 2 and 3 tasks are skipped or minimized.
 */
object StartupOrchestrator {
    private const val TAG = "StartupOrchestrator"

    /**
     * Phase 1 tasks that MUST complete before UI is shown.
     * These should be fast and have fallbacks.
     */
    private val phase1Tasks = mutableListOf<StartupTask>()

    /**
     * Phase 2 tasks that run after first frame is rendered.
     * These can be slower but shouldn't block UI.
     */
    private val phase2Tasks = mutableListOf<StartupTask>()

    /**
     * Phase 3 tasks that run when the app is idle.
     * These are non-essential and can be skipped in safe mode.
     */
    private val phase3Tasks = mutableListOf<StartupTask>()

    @Volatile
    private var phase1Complete = false

    @Volatile
    private var phase2Complete = false

    @Volatile
    private var phase3Complete = false

    /**
     * Register a Phase 1 (critical) task.
     */
    fun registerPhase1Task(
        name: String,
        skipInSafeMode: Boolean = false,
        action: () -> Unit,
    ) {
        phase1Tasks.add(StartupTask(name, skipInSafeMode, action))
    }

    /**
     * Register a Phase 2 (deferred) task.
     */
    fun registerPhase2Task(
        name: String,
        skipInSafeMode: Boolean = true,
        action: () -> Unit,
    ) {
        phase2Tasks.add(StartupTask(name, skipInSafeMode, action))
    }

    /**
     * Register a Phase 3 (idle) task.
     */
    fun registerPhase3Task(
        name: String,
        skipInSafeMode: Boolean = true,
        action: () -> Unit,
    ) {
        phase3Tasks.add(StartupTask(name, skipInSafeMode, action))
    }

    /**
     * Run Phase 1 tasks. Called from Application.onCreate().
     *
     * @param safeMode Whether safe mode is active
     * @return true if all tasks succeeded, false if any failed
     */
    fun runPhase1(safeMode: Boolean): Boolean {
        Log.i(TAG, "Running Phase 1 (critical) - ${phase1Tasks.size} tasks, safeMode=$safeMode")
        val allSucceeded = runTasks(phase1Tasks, safeMode, "Phase1")
        phase1Complete = true
        return allSucceeded
    }

    /**
     * Run Phase 2 tasks. Called after first Activity is rendered.
     *
     * @param safeMode Whether safe mode is active
     * @return true if all tasks succeeded, false if any failed
     */
    fun runPhase2(safeMode: Boolean): Boolean {
        if (!phase1Complete) {
            Log.w(TAG, "Phase 2 called before Phase 1 complete")
        }
        Log.i(TAG, "Running Phase 2 (deferred) - ${phase2Tasks.size} tasks, safeMode=$safeMode")
        val allSucceeded = runTasks(phase2Tasks, safeMode, "Phase2")
        phase2Complete = true
        return allSucceeded
    }

    /**
     * Run Phase 3 tasks. Called when app is idle.
     *
     * @param safeMode Whether safe mode is active
     * @return true if all tasks succeeded, false if any failed
     */
    fun runPhase3(safeMode: Boolean): Boolean {
        if (!phase2Complete) {
            Log.w(TAG, "Phase 3 called before Phase 2 complete")
        }
        Log.i(TAG, "Running Phase 3 (idle) - ${phase3Tasks.size} tasks, safeMode=$safeMode")
        val allSucceeded = runTasks(phase3Tasks, safeMode, "Phase3")
        phase3Complete = true
        return allSucceeded
    }

    private fun runTasks(
        tasks: List<StartupTask>,
        safeMode: Boolean,
        phaseName: String,
    ): Boolean {
        var allSucceeded = true
        for (task in tasks) {
            if (safeMode && task.skipInSafeMode) {
                Log.d(TAG, "[$phaseName] Skipping '${task.name}' (safe mode)")
                continue
            }
            try {
                val startTime = System.currentTimeMillis()
                task.action()
                val duration = System.currentTimeMillis() - startTime
                Log.d(TAG, "[$phaseName] '${task.name}' completed in ${duration}ms")
            } catch (e: Exception) {
                Log.e(TAG, "[$phaseName] '${task.name}' failed", e)
                allSucceeded = false
                // Continue with other tasks - don't let one failure block startup
            }
        }
        return allSucceeded
    }

    /**
     * Check if a specific phase has completed.
     */
    fun isPhaseComplete(phase: Int): Boolean =
        when (phase) {
            1 -> phase1Complete
            2 -> phase2Complete
            3 -> phase3Complete
            else -> false
        }

    /**
     * Reset orchestrator state (for testing).
     */
    fun reset() {
        phase1Tasks.clear()
        phase2Tasks.clear()
        phase3Tasks.clear()
        phase1Complete = false
        phase2Complete = false
        phase3Complete = false
    }

    private data class StartupTask(
        val name: String,
        val skipInSafeMode: Boolean,
        val action: () -> Unit,
    )
}
