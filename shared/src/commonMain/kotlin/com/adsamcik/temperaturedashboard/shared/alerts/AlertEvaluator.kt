package com.adsamcik.temperaturedashboard.shared.alerts

import com.adsamcik.temperaturedashboard.core.database.SensorAlertDao
import com.adsamcik.temperaturedashboard.core.database.toDomain
import com.adsamcik.temperaturedashboard.core.model.Reading
import com.adsamcik.temperaturedashboard.core.model.Sensor
import io.github.aakira.napier.Napier
import kotlinx.datetime.Clock

class AlertEvaluator(
    private val dao: SensorAlertDao,
    private val notifier: Notifier,
    private val clock: Clock = Clock.System,
) {
    suspend fun evaluate(sensor: Sensor, reading: Reading) {
        val enabled = dao.enabledForSensor(sensor.id.raw)
        if (enabled.isEmpty()) return
        val now = clock.now()
        for (entity in enabled) {
            val alert = entity.toDomain()
            if (alert.isInCooldown(now)) continue
            val message = alert.check(reading) ?: continue
            try {
                notifier.notify(sensor = sensor, title = sensor.displayName, body = message)
                dao.markFired(entity.id, now.toEpochMilliseconds())
            } catch (t: Throwable) {
                Napier.w("Notifier rejected alert ${alert.id}: ${t.message}", tag = LOG_TAG)
            }
        }
    }

    private companion object { const val LOG_TAG = "AlertEvaluator" }
}
