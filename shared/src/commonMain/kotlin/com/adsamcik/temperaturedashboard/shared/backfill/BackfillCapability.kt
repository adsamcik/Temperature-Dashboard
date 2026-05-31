package com.adsamcik.temperaturedashboard.shared.backfill

import com.adsamcik.temperaturedashboard.ble.api.BleConnector
import com.adsamcik.temperaturedashboard.ble.api.ConnectionFailure
import com.adsamcik.temperaturedashboard.ble.api.ConnectionResult
import com.adsamcik.temperaturedashboard.core.model.Sensor
import com.adsamcik.temperaturedashboard.shared.repository.ReadingRepository
import io.github.aakira.napier.Napier

/**
 * A device-specific way to import historical readings the sensor has been
 * buffering on-board. Each [BackfillCapability] knows:
 *
 *  - which device profile id prefix it can handle ([supportedProfilePrefix]),
 *  - a label to show on the "sync" button,
 *  - how to drive the GATT connection to pull the data
 *    ([performBackfill]).
 *
 * Implementations are registered in [BackfillRegistry]; the detail screen
 * looks up by sensor profile id and shows the button when a capability exists.
 *
 * Backfill is supported on both Android and Desktop. The Desktop path goes
 * through the `btleplug_jni` Rust shim ([DesktopBleConnector] →
 * [DesktopBleConnection]); the Android path uses native `BluetoothGatt`.
 *
 * Other-sensor research (as of v0.3.x):
 *  - ThermoPro TP35x — IMPLEMENTED (see [ThermoProDayHistoryCapability]).
 *  - Govee H5072/H5074/H5075 — older firmware allows unencrypted history
 *    download via the `494e5445-4c4c-495f-524f-434b535f4857` (`INTELLI_ROCKS_HW`)
 *    GATT service. Newer firmware uses AES + RC4. Not implemented because
 *    the team has no Govee hardware on hand to validate; see GoveeBTTempLogger
 *    for the protocol details when adding.
 *  - SwitchBot Meter Pro CO2 (model 5) — has on-device history with its own
 *    proprietary protocol. Not implemented (no hardware on hand).
 *  - SwitchBot Meter / Meter Plus / Hub 2 / Outdoor — no on-device history,
 *    advertisement-only.
 *  - BTHome v2 / ESS / Health Thermometer — by spec, live-only.
 */
interface BackfillCapability {
    val supportedProfilePrefix: String
    val displayLabel: String

    /** Runs against a live, just-opened [com.adsamcik.temperaturedashboard.ble.api.BleConnection]. */
    suspend fun performBackfill(
        sensor: Sensor,
        connection: com.adsamcik.temperaturedashboard.ble.api.BleConnection,
        readingRepository: ReadingRepository,
    ): BackfillResult
}

sealed interface BackfillResult {
    data class Ok(val readingsIngested: Int) : BackfillResult
    data class Skipped(val reason: String) : BackfillResult
    data class Failed(val message: String) : BackfillResult
}

/** Registry of all known [BackfillCapability]s. Add new sensors here. */
object BackfillRegistry {
    val all: List<BackfillCapability> = listOf(
        ThermoProDayHistoryCapability,
        // Govee H5072/H5074/H5075 history could go here once we add an
        // implementation; newer Govee + SwitchBot use AES + RC4 encryption
        // for downloaded history which is genuinely complex.
    )

    fun forSensor(profileId: String): BackfillCapability? =
        all.firstOrNull { profileId.startsWith(it.supportedProfilePrefix) }
}

/**
 * Drives a complete connect → run capability → disconnect lifecycle. The
 * `:shared` layer's only entry point for backfill — UI calls [run] and
 * gets a result it can show in a snackbar without knowing about GATT.
 */
class BackfillCoordinator(
    private val connector: BleConnector,
    private val readingRepository: ReadingRepository,
) {
    suspend fun run(sensor: Sensor): BackfillResult {
        val capability = BackfillRegistry.forSensor(sensor.profileId)
            ?: return BackfillResult.Skipped("No backfill capability for ${sensor.profileId}")

        val connection = runCatching {
            (connector.connect(sensor.address.raw) as ConnectionResult.Connected).connection
        }.getOrElse {
            Napier.w("Backfill connect failed: ${it.message}", tag = LOG_TAG)
            val msg = it.message ?: "Unknown connection failure"
            return BackfillResult.Failed(
                if (msg.contains("not found on java.library.path", ignoreCase = true) ||
                    msg.contains("Desktop GATT unavailable", ignoreCase = true)
                ) {
                    "Backfill requires the btleplug_jni native library — not bundled with this build."
                } else "Connect failed: $msg",
            )
        }

        return try {
            capability.performBackfill(sensor, connection, readingRepository)
        } catch (t: Throwable) {
            Napier.w("Backfill ${capability.supportedProfilePrefix} failed: ${t.message}", tag = LOG_TAG)
            BackfillResult.Failed(t.message ?: "Backfill failed")
        } finally {
            runCatching { connection.close() }
        }
    }

    private companion object { const val LOG_TAG = "BackfillCoordinator" }
}
