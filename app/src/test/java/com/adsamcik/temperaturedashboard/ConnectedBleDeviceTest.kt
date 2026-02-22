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
    fun `getService returns null when gatt is null`() = runTest {
        val device = ConnectedBleDevice()
        assertNull(device.getService(UUID.fromString("00000001-0000-0000-0000-000000000000")))
    }

    @Test
    fun `getCharacteristic returns null when gatt is null`() = runTest {
        val device = ConnectedBleDevice()
        assertNull(device.getCharacteristic(
            UUID.fromString("00000001-0000-0000-0000-000000000000"),
            UUID.fromString("00000002-0000-0000-0000-000000000000")
        ))
    }

    @Test
    fun `readCharacteristic returns null when gatt is null`() = runTest {
        val device = ConnectedBleDevice()
        val characteristic = BluetoothGattCharacteristic(
            UUID.fromString("00000002-0000-0000-0000-000000000000"), 0, 0
        )
        assertNull(device.readCharacteristic(characteristic))
    }

    @Test
    fun `writeCharacteristic returns false when gatt is null`() = runTest {
        val device = ConnectedBleDevice()
        val characteristic = BluetoothGattCharacteristic(
            UUID.fromString("00000002-0000-0000-0000-000000000000"), 0, 0
        )
        assertFalse(device.writeCharacteristic(characteristic, byteArrayOf(0x01)))
    }

    @Test
    fun `enableNotifications returns false when gatt is null`() = runTest {
        val device = ConnectedBleDevice()
        val characteristic = BluetoothGattCharacteristic(
            UUID.fromString("00000002-0000-0000-0000-000000000000"), 0, 0
        )
        assertFalse(device.enableNotifications(characteristic))
    }

    @Test
    fun `disableNotifications returns false when gatt is null`() = runTest {
        val device = ConnectedBleDevice()
        val characteristic = BluetoothGattCharacteristic(
            UUID.fromString("00000002-0000-0000-0000-000000000000"), 0, 0
        )
        assertFalse(device.disableNotifications(characteristic))
    }

    @Test
    fun `waitForNotification timeout returns null without hanging`() = runTest {
        val connectedDevice = ConnectedBleDevice()

        val result = withTimeout(1_000) {
            connectedDevice.waitForNotification(timeoutMillis = 50)
        }

        assertNull(result)
    }
}
