package com.adsamcik.temperaturedashboard.shared.alerts

import com.adsamcik.temperaturedashboard.core.database.SensorAlertDao
import com.adsamcik.temperaturedashboard.core.database.toDomain
import com.adsamcik.temperaturedashboard.core.database.toEntity
import com.adsamcik.temperaturedashboard.core.model.AlertKind
import com.adsamcik.temperaturedashboard.core.model.SensorAlert
import com.adsamcik.temperaturedashboard.core.model.SensorId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SensorAlertRepository(private val dao: SensorAlertDao) {
    fun observeForSensor(sensorId: SensorId): Flow<List<SensorAlert>> =
        dao.observeForSensor(sensorId.raw).map { rows -> rows.map { it.toDomain() } }

    suspend fun upsert(alert: SensorAlert): Long = dao.upsert(alert.toEntity())

    suspend fun setEnabled(alert: SensorAlert, enabled: Boolean) {
        dao.update(alert.copy(enabled = enabled).toEntity())
    }

    suspend fun delete(alertId: Long) {
        dao.delete(alertId)
    }

    suspend fun add(
        sensorId: SensorId,
        kind: AlertKind,
        cooldown: kotlin.time.Duration = SensorAlert.DEFAULT_COOLDOWN,
    ): Long = dao.upsert(
        SensorAlert(
            id = 0,
            sensorId = sensorId,
            kind = kind,
            enabled = true,
            cooldown = cooldown,
            lastFired = null,
        ).toEntity(),
    )
}
