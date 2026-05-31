package com.adsamcik.temperaturedashboard.shared.system

/**
 * "Start at login" — registers the app to launch automatically when the
 * user logs in to their OS. Inherently platform-specific.
 *
 * Implementations are best-effort — any error is surfaced as
 * [AutostartResult.Failed] rather than thrown.
 */
interface AutostartManager {
    val supported: Boolean
    fun isEnabled(): Boolean
    fun enable(): AutostartResult
    fun disable(): AutostartResult
}

sealed interface AutostartResult {
    data object Ok : AutostartResult
    data object NotSupported : AutostartResult
    data class Failed(val message: String) : AutostartResult
}
