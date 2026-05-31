package com.adsamcik.temperaturedashboard.ble.desktop

import com.adsamcik.temperaturedashboard.ble.api.BleAdvertisement
import com.adsamcik.temperaturedashboard.ble.api.BluetoothAdapterMonitor
import com.adsamcik.temperaturedashboard.ble.api.BluetoothAdapterState
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable

/**
 * Thin Kotlin wrapper over the `btleplug_jni` native library.
 *
 * The native ABI is intentionally polling-based and JSON-typed:
 * - `btleplug_open()` returns an opaque handle.
 * - `btleplug_close(handle)` releases it.
 * - `btleplug_start_scan(handle)` and `btleplug_stop_scan(handle)`.
 * - `btleplug_next_event(handle, buf, bufLen)` returns the size in bytes of
 *   a JSON-encoded [NativeAdvert] event written to `buf`, `-1` when the queue
 *   is empty, or `-2` on error.
 *
 * JSON keeps the FFI surface trivially small: no struct layout to keep in
 * sync, no callback marshalling, no GC vs Rust memory ownership pitfalls.
 * The performance cost is negligible at sensor advertisement rates
 * (a handful of events per second).
 */
internal class BtleplugNative private constructor(
    private val lib: BtleplugLib,
    private val handle: Pointer,
) {

    fun startScan() {
        val rc = lib.btleplug_start_scan(handle)
        require(rc == 0) { "btleplug_start_scan failed: rc=$rc" }
    }

    fun stopScan() {
        lib.btleplug_stop_scan(handle)
    }

    fun advertisements(): Flow<BleAdvertisement> = flow {
        val buf = ByteArray(EVENT_BUFFER_BYTES)
        while (true) {
            val n = lib.btleplug_next_event(handle, buf, buf.size)
            when {
                n > 0 -> {
                    val raw = String(buf, 0, n, Charsets.UTF_8)
                    runCatching { json.decodeFromString<NativeAdvert>(raw) }
                        .onSuccess { emit(it.toDomain()) }
                        .onFailure { Napier.w("btleplug event parse failed: ${it.message}") }
                }
                n == -1 -> delay(POLL_INTERVAL_MS)
                else -> {
                    Napier.w("btleplug_next_event error rc=$n")
                    delay(POLL_INTERVAL_MS_ON_ERROR)
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        private const val EVENT_BUFFER_BYTES = 8 * 1024
        private const val POLL_INTERVAL_MS = 50L
        private const val POLL_INTERVAL_MS_ON_ERROR = 500L
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Locates and loads the native library. Throws if it can't be found on
         * `java.library.path` — caller decides how to surface that.
         */
        fun load(): BtleplugNative {
            val lib = Native.load("btleplug_jni", BtleplugLib::class.java)
            val handle = lib.btleplug_open()
                ?: error("btleplug_open returned NULL")
            return BtleplugNative(lib, handle)
        }
    }

    @Suppress("unused")
    internal interface BtleplugLib : Library {
        fun btleplug_open(): Pointer?
        fun btleplug_close(handle: Pointer)
        fun btleplug_start_scan(handle: Pointer): Int
        fun btleplug_stop_scan(handle: Pointer): Int
        fun btleplug_next_event(handle: Pointer, buf: ByteArray, bufLen: Int): Int
    }
}

@Serializable
internal data class NativeAdvert(
    val address: String,
    val name: String? = null,
    val rssi: Int,
    val timestampMs: Long,
    val serviceUuids: List<String> = emptyList(),
    val manufacturerData: Map<String, String> = emptyMap(),
    val serviceData: Map<String, String> = emptyMap(),
) {
    fun toDomain(): BleAdvertisement = BleAdvertisement(
        address = address,
        name = name,
        rssi = rssi,
        timestamp = Instant.fromEpochMilliseconds(timestampMs),
        serviceUuids = serviceUuids.map { it.uppercase() },
        manufacturerData = manufacturerData.mapKeys { it.key.toInt(radix = 16) }
            .mapValues { hexToBytes(it.value) },
        serviceData = serviceData.mapKeys { it.key.uppercase() }
            .mapValues { hexToBytes(it.value) },
    )
}

private fun hexToBytes(hex: String): ByteArray {
    if (hex.isEmpty()) return ByteArray(0)
    val clean = hex.replace(" ", "")
    require(clean.length % 2 == 0) { "hex string must have even length: '$hex'" }
    val out = ByteArray(clean.length / 2)
    for (i in out.indices) {
        out[i] = ((Character.digit(clean[i * 2], 16) shl 4) +
            Character.digit(clean[i * 2 + 1], 16)).toByte()
    }
    return out
}

/**
 * Desktop adapter monitor — a no-op until btleplug exposes adapter state.
 * For now we assume On when the native library loaded, Unavailable otherwise.
 */
class DesktopBluetoothAdapterMonitor(
    nativeAvailable: Boolean,
) : BluetoothAdapterMonitor {
    private val _state = MutableStateFlow(
        if (nativeAvailable) BluetoothAdapterState.On else BluetoothAdapterState.Unavailable,
    )
    override val state: StateFlow<BluetoothAdapterState> = _state.asStateFlow()
}
