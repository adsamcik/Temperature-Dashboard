package com.adsamcik.temperaturedashboard

import android.bluetooth.BluetoothGattCharacteristic
import com.adsamcik.temperaturedashboard.networking.ConnectedBleDevice
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class ConnectedBleDeviceTest {

    @Test
    fun operations_gattNull_returnNullOrFalse() = runTest {
        val connectedDevice = ConnectedBleDevice()
        val serviceUuid = UUID.randomUUID()
        val characteristicUuid = UUID.randomUUID()
        val characteristic = BluetoothGattCharacteristic(characteristicUuid, 0, 0)

        assertNull(connectedDevice.getService(serviceUuid))
        assertNull(connectedDevice.getCharacteristic(serviceUuid, characteristicUuid))
        assertNull(connectedDevice.readCharacteristic(characteristic))
        assertFalse(connectedDevice.writeCharacteristic(characteristic, byteArrayOf(0x01)))
        assertFalse(connectedDevice.enableNotifications(characteristic))
        assertFalse(connectedDevice.disableNotifications(characteristic))
    }

    @Test
    fun waitForNotification_timeout_returnsNullWithoutHanging() = runTest {
        val connectedDevice = ConnectedBleDevice()

        val result = withTimeout(1_000) {
            connectedDevice.waitForNotification(timeoutMillis = 50)
        }

        assertNull(result)
    }
}
