package com.adsamcik.temperaturedashboard.ble.desktop

import com.adsamcik.temperaturedashboard.ble.api.BleConnector
import com.adsamcik.temperaturedashboard.ble.api.ConnectionFailure
import com.adsamcik.temperaturedashboard.ble.api.ConnectionResult

/**
 * Desktop GATT connection is a TODO — would need an extension to the
 * btleplug-JNI ABI plus quite a bit more state on the Rust side. The
 * passive-advertisement path covers everything our current decoders need.
 */
class DesktopBleConnector : BleConnector {
    override suspend fun connect(address: String): ConnectionResult.Connected {
        error("GATT connect is not yet implemented on Desktop (btleplug-JNI extension required). " +
            "Address: $address. Tracked in roadmap.")
    }
}
