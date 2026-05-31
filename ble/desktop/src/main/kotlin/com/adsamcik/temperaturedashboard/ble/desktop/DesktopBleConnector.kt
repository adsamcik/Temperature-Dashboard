package com.adsamcik.temperaturedashboard.ble.desktop

import com.adsamcik.temperaturedashboard.ble.api.BleConnector
import com.adsamcik.temperaturedashboard.ble.api.ConnectionFailure
import com.adsamcik.temperaturedashboard.ble.api.ConnectionResult
import io.github.aakira.napier.Napier

/**
 * Desktop GATT connector. Loads the shared `btleplug_jni` cdylib on first
 * use, opens a handle, and delegates `connect()` to the Rust side. If the
 * native library cannot be located on `java.library.path`, every call
 * returns [ConnectionResult.Failed] with [ConnectionFailure.NotSupported]
 * so the UI can surface a graceful message.
 */
class DesktopBleConnector : BleConnector {

    private val nativeOrNull: BtleplugNative? by lazy {
        runCatching { BtleplugNative.load() }
            .onFailure { Napier.w("btleplug_jni unavailable: ${it.message}") }
            .getOrNull()
    }

    override suspend fun connect(address: String): ConnectionResult.Connected {
        val native = nativeOrNull
            ?: throw IllegalStateException(
                "Desktop GATT unavailable: btleplug_jni native library not found on " +
                    "java.library.path. Address=$address.",
            )
        val rc = native.connect(address)
        if (rc <= 0) {
            throw IllegalStateException(translateRc(rc, address))
        }
        return ConnectionResult.Connected(DesktopBleConnection(native, rc, address))
    }

    private fun translateRc(rc: Int, address: String): String = when (rc) {
        -2 -> "Desktop GATT connect failed (generic error) for $address"
        -4 -> "No Bluetooth adapter available for connect to $address"
        -5 -> "Lost connection while opening session for $address"
        -8, -9 -> "Invalid address or UUID passed to native shim: $address"
        else -> "Desktop GATT connect failed rc=$rc for $address"
    }
}
