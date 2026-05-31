package com.adsamcik.temperaturedashboard.core.model

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class SensorAlertTest {

    private val sensorId = SensorId(1L)
    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000L)

    private fun reading(temp: Double? = 22.0, hum: Double? = 50.0, batt: Int? = 100): Reading =
        Reading(
            temperatureC = temp,
            humidityPct = hum,
            batteryPct = batt,
            rssi = -60,
            timestamp = now,
            source = ReadingSource.ADVERTISEMENT,
        )

    private fun alert(kind: AlertKind, lastFired: Instant? = null): SensorAlert = SensorAlert(
        id = 1,
        sensorId = sensorId,
        kind = kind,
        enabled = true,
        cooldown = 30.minutes,
        lastFired = lastFired,
    )

    @Test
    fun `TempAbove fires when reading exceeds threshold`() {
        val a = alert(AlertKind.TempAbove(20.0))
        val msg = a.check(reading(temp = 22.0))
        assertNotNull(msg)
        assertTrue(msg.contains("above"))
    }

    @Test
    fun `TempAbove silent when reading at-or-below threshold`() {
        val a = alert(AlertKind.TempAbove(25.0))
        assertNull(a.check(reading(temp = 25.0)))
        assertNull(a.check(reading(temp = 20.0)))
    }

    @Test
    fun `TempBelow fires when reading drops below threshold`() {
        val a = alert(AlertKind.TempBelow(5.0))
        assertNotNull(a.check(reading(temp = 3.0)))
        assertNull(a.check(reading(temp = 5.0)))
        assertNull(a.check(reading(temp = 10.0)))
    }

    @Test
    fun `HumidityBelow fires when value drops below threshold`() {
        val a = alert(AlertKind.HumidityBelow(30.0))
        assertNotNull(a.check(reading(hum = 25.0)))
        assertNull(a.check(reading(hum = 35.0)))
    }

    @Test
    fun `BatteryBelow fires for low battery`() {
        val a = alert(AlertKind.BatteryBelow(20))
        assertNotNull(a.check(reading(batt = 10)))
        assertNull(a.check(reading(batt = 50)))
    }

    @Test
    fun `null reading field never trips alert for that field`() {
        assertNull(alert(AlertKind.TempAbove(0.0)).check(reading(temp = null)))
        assertNull(alert(AlertKind.HumidityBelow(99.0)).check(reading(hum = null)))
        assertNull(alert(AlertKind.BatteryBelow(99)).check(reading(batt = null)))
    }

    @Test
    fun `isInCooldown is true within window, false after`() {
        val a = alert(AlertKind.TempAbove(0.0), lastFired = now)
        assertTrue(a.isInCooldown(now + 5.minutes))
        assertTrue(a.isInCooldown(now + 29.minutes))
        assertFalse(a.isInCooldown(now + 31.minutes))
    }

    @Test
    fun `isInCooldown is false when never fired`() {
        val a = alert(AlertKind.TempAbove(0.0), lastFired = null)
        assertFalse(a.isInCooldown(now))
    }

    @Test
    fun `storageKey round-trips through fromStorage`() {
        val kinds = listOf(
            AlertKind.TempAbove(21.5),
            AlertKind.TempBelow(-3.0),
            AlertKind.HumidityAbove(75.0),
            AlertKind.HumidityBelow(25.0),
            AlertKind.BatteryBelow(15),
        )
        for (k in kinds) {
            val back = AlertKind.fromStorage(k.storageKey, k.threshold)
            assertEquals(k::class, back::class)
            assertEquals(k.threshold, back.threshold, "threshold for $k")
        }
    }
}
