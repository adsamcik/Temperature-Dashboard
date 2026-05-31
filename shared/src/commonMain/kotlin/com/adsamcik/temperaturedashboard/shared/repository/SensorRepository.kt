package com.adsamcik.temperaturedashboard.shared.repository

import com.adsamcik.temperaturedashboard.core.database.SensorDao
import com.adsamcik.temperaturedashboard.core.database.SensorEntity
import com.adsamcik.temperaturedashboard.core.database.toDomain
import com.adsamcik.temperaturedashboard.core.database.toEntity
import com.adsamcik.temperaturedashboard.core.model.Sensor
import com.adsamcik.temperaturedashboard.core.model.SensorAddress
import com.adsamcik.temperaturedashboard.core.model.SensorId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlin.random.Random

/**
 * Sensor catalogue — what the user has explicitly added to the dashboard.
 *
 * Distinct from the *scanning* layer: every BLE advertisement passes through
 * the scanner, but only sensors that exist in this repository ever get their
 * readings persisted. The scan UI uses [findByAddress] to check whether an
 * advertised device is already known and to surface unknowns as "add" candidates.
 */
class SensorRepository(private val dao: SensorDao) {

    fun observeAll(): Flow<List<Sensor>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    /** Flow includes hidden sensors — used by Settings → Hidden sensors panel. */
    fun observeAllIncludingHidden(): Flow<List<Sensor>> =
        dao.observeAllIncludingHidden().map { rows -> rows.map { it.toDomain() } }

    fun observe(id: SensorId): Flow<Sensor?> = dao.observeById(id.raw).map { it?.toDomain() }

    suspend fun findByAddress(address: SensorAddress): Sensor? =
        dao.findByAddress(address.raw)?.toDomain()

    suspend fun findById(id: SensorId): Sensor? =
        dao.findById(id.raw)?.toDomain()

    /**
     * Idempotent registration — if a sensor at [address] is already in the DB,
     * return it unchanged. Otherwise insert a fresh row and return it.
     */
    suspend fun registerIfAbsent(
        address: SensorAddress,
        profileId: String,
        displayName: String,
        modelHint: String? = null,
    ): Sensor {
        dao.findByAddress(address.raw)?.let { return it.toDomain() }
        val entity = SensorEntity(
            address = address.raw,
            profileId = profileId,
            displayName = displayName,
            modelHint = modelHint,
            createdAt = Clock.System.now().toEpochMilliseconds(),
            lastSeenAt = null,
            colorSeed = Random.nextInt(),
        )
        dao.insert(entity)
        // OnConflict.IGNORE may return -1 if another writer inserted concurrently;
        // re-read by address to get the canonical row either way.
        return checkNotNull(dao.findByAddress(address.raw)) {
            "Sensor insert for $address returned no row"
        }.toDomain()
    }

    suspend fun rename(id: SensorId, name: String) {
        require(name.isNotBlank()) { "Sensor name must not be blank" }
        dao.rename(id.raw, name.trim())
    }

    suspend fun setHidden(id: SensorId, hidden: Boolean) {
        dao.setHidden(id.raw, hidden)
    }

    suspend fun setColor(id: SensorId, colorArgb: Int) {
        dao.setColor(id.raw, colorArgb)
    }

    suspend fun update(sensor: Sensor) {
        dao.update(sensor.toEntity())
    }

    suspend fun touch(id: SensorId, at: Long = Clock.System.now().toEpochMilliseconds()) {
        dao.touch(id.raw, at)
    }

    suspend fun delete(id: SensorId) {
        dao.delete(id.raw)
    }
}
