package com.adsamcik.temperaturedashboard.shared.navigation

import com.adsamcik.temperaturedashboard.core.model.SensorId

/**
 * In-memory navigation backstack used by the [TdashAppShell]. Deliberately
 * minimal — we don't need NavController-grade serialization for an app whose
 * navigation graph is a handful of screens.
 *
 * Top of stack = current screen. Push opens a new screen; pop returns; reset
 * replaces the entire stack (used when the bottom-nav destination changes).
 */
class NavStack(initial: NavTarget = NavTarget.Shell(ShellDestination.Dashboard)) {
    private val backing: MutableList<NavTarget> = mutableListOf(initial)

    val current: NavTarget get() = backing.last()

    val canGoBack: Boolean get() = backing.size > 1

    fun push(target: NavTarget) {
        backing.add(target)
    }

    fun pop(): Boolean {
        if (backing.size <= 1) return false
        backing.removeAt(backing.lastIndex)
        return true
    }

    fun resetTo(target: NavTarget) {
        backing.clear()
        backing.add(target)
    }
}

sealed interface NavTarget {
    data class Shell(val destination: ShellDestination) : NavTarget
    data class SensorDetail(val sensorId: SensorId) : NavTarget
}
