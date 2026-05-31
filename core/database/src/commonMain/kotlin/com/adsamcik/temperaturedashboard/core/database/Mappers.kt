package com.adsamcik.temperaturedashboard.core.database

import com.adsamcik.temperaturedashboard.core.model.AlertKind
import com.adsamcik.temperaturedashboard.core.model.ReadingInterval
import com.adsamcik.temperaturedashboard.core.model.ReadingSource
import com.adsamcik.temperaturedashboard.core.model.Sensor
import com.adsamcik.temperaturedashboard.core.model.SensorAddress
import com.adsamcik.temperaturedashboard.core.model.SensorAlert
import com.adsamcik.temperaturedashboard.core.model.SensorId
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.minutes

fun SensorEntity.toDomain(): Sensor = Sensor(
    id = SensorId(id),
    address = SensorAddress(address),
    profileId = profileId,
    displayName = displayName,
    modelHint = modelHint,
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    lastSeenAt = lastSeenAt?.let(Instant::fromEpochMilliseconds),
    colorSeed = colorSeed,
)

fun Sensor.toEntity(): SensorEntity = SensorEntity(
    id = id.raw,
    address = address.raw,
    profileId = profileId,
    displayName = displayName,
    modelHint = modelHint,
    createdAt = createdAt.toEpochMilliseconds(),
    lastSeenAt = lastSeenAt?.toEpochMilliseconds(),
    colorSeed = colorSeed,
)

fun ReadingIntervalEntity.toDomain(): ReadingInterval = ReadingInterval(
    id = id,
    sensorId = SensorId(sensorId),
    temperatureC = temperatureC,
    humidityPct = humidityPct,
    batteryPct = batteryPct,
    rssiAvg = rssiAvg,
    validFrom = Instant.fromEpochMilliseconds(validFrom),
    validUntil = Instant.fromEpochMilliseconds(validUntil),
    sampleCount = sampleCount,
    source = ReadingSource.valueOf(source),
)

fun SensorAlertEntity.toDomain(): SensorAlert = SensorAlert(
    id = id,
    sensorId = SensorId(sensorId),
    kind = AlertKind.fromStorage(kind, threshold),
    enabled = enabled,
    cooldown = cooldownMinutes.minutes,
    lastFired = lastFiredAt?.let(Instant::fromEpochMilliseconds),
)

fun SensorAlert.toEntity(): SensorAlertEntity = SensorAlertEntity(
    id = id,
    sensorId = sensorId.raw,
    kind = kind.storageKey,
    threshold = kind.threshold,
    enabled = enabled,
    cooldownMinutes = cooldown.inWholeMinutes.toInt(),
    lastFiredAt = lastFired?.toEpochMilliseconds(),
)

