package com.adsamcik.temperaturedashboard.core.database

import com.adsamcik.temperaturedashboard.core.model.ReadingInterval
import com.adsamcik.temperaturedashboard.core.model.ReadingSource
import com.adsamcik.temperaturedashboard.core.model.Sensor
import com.adsamcik.temperaturedashboard.core.model.SensorAddress
import com.adsamcik.temperaturedashboard.core.model.SensorId
import kotlinx.datetime.Instant

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
