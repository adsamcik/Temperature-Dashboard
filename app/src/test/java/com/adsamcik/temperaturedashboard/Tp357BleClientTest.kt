package com.adsamcik.temperaturedashboard

import com.adsamcik.temperaturedashboard.networking.ApiMode
import com.adsamcik.temperaturedashboard.networking.Tp357BleClient
import com.adsamcik.temperaturedashboard.storage.Device
import org.junit.Assert.*
import org.junit.Test

class Tp357BleClientTest {
    private val client = Tp357BleClient()

    @Test
    fun `isCompatible returns true for TP357 device name`() {
        val device = Device(
            macAddress = "AA:BB:CC:DD:EE:FF",
            name = "TP357 (7216)",
            manufacturerId = null,
            serviceUuid = null,
            lastSeen = 0L
        )
        assertTrue(client.isCompatible(device))
    }

    @Test
    fun `isCompatible returns true for partial TP357 name`() {
        val device = Device(
            macAddress = "AA:BB:CC:DD:EE:FF",
            name = "Some TP357 Device",
            manufacturerId = null,
            serviceUuid = null,
            lastSeen = 0L
        )
        assertTrue(client.isCompatible(device))
    }

    @Test
    fun `isCompatible returns false for non-TP357 device`() {
        val device = Device(
            macAddress = "AA:BB:CC:DD:EE:FF",
            name = "SomeOtherSensor",
            manufacturerId = null,
            serviceUuid = null,
            lastSeen = 0L
        )
        assertFalse(client.isCompatible(device))
    }

    @Test
    fun `isCompatible returns false for null name`() {
        val device = Device(
            macAddress = "AA:BB:CC:DD:EE:FF",
            name = null,
            manufacturerId = null,
            serviceUuid = null,
            lastSeen = 0L
        )
        assertFalse(client.isCompatible(device))
    }

    @Test
    fun `isCompatible returns true for matching service UUID`() {
        val device = Device(
            macAddress = "AA:BB:CC:DD:EE:FF",
            name = "Unknown",
            manufacturerId = null,
            serviceUuid = "00010203-0405-0607-0809-0a0b0c0d2b10",
            lastSeen = 0L
        )
        assertTrue(client.isCompatible(device))
    }

    @Test
    fun `getRequestCommand returns correct bytes for LATEST`() {
        val command = client.getRequestCommand(ApiMode.LATEST)
        assertNotNull(command)
        assertEquals(6, command!!.size)
        assertEquals(0xa7.toByte(), command[0])
        assertEquals(0x7a.toByte(), command[5])
    }

    @Test
    fun `getRequestCommand returns correct bytes for DELTA`() {
        val command = client.getRequestCommand(ApiMode.DELTA)
        assertNotNull(command)
        assertEquals(0xa6.toByte(), command!![0])
        assertEquals(0x6a.toByte(), command[5])
    }

    @Test
    fun `getRequestCommand returns correct bytes for HISTORY`() {
        val command = client.getRequestCommand(ApiMode.HISTORY)
        assertNotNull(command)
        assertEquals(0xa8.toByte(), command!![0])
        assertEquals(0x8a.toByte(), command[5])
    }

    @Test
    fun `name is ThermoPro TP357`() {
        assertEquals("ThermoPro TP357", client.name)
    }

    @Test
    fun `iconRes is null`() {
        assertNull(client.iconRes)
    }
}
