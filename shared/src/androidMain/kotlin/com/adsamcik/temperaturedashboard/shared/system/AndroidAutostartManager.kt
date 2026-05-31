package com.adsamcik.temperaturedashboard.shared.system

/**
 * Android: handled by the OS schedule + foreground service. There is no
 * "start at user login" concept here — the system can wake us via boot
 * receiver / WorkManager / etc.
 */
class AndroidAutostartManager : AutostartManager {
    override val supported: Boolean = false
    override fun isEnabled(): Boolean = false
    override fun enable(): AutostartResult = AutostartResult.NotSupported
    override fun disable(): AutostartResult = AutostartResult.NotSupported
}
