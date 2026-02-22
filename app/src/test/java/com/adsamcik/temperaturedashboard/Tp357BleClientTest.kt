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
        assertArrayEquals(byteArrayOf(0xa7.toByte(), 0x00, 0x00, 0x00, 0x00, 0x7a.toByte()), command)
    }

    @Test
    fun `getRequestCommand returns correct bytes for DELTA`() {
        val command = client.getRequestCommand(ApiMode.DELTA)
        assertNotNull(command)
        assertArrayEquals(byteArrayOf(0xa6.toByte(), 0x00, 0x00, 0x00, 0x00, 0x6a.toByte()), command)
    }

    @Test
    fun `getRequestCommand returns correct bytes for HISTORY`() {
        val command = client.getRequestCommand(ApiMode.HISTORY)
        assertNotNull(command)
        assertArrayEquals(byteArrayOf(0xa8.toByte(), 0x00, 0x00, 0x00, 0x00, 0x8a.toByte()), command)
    }

    @Test
    fun `name is ThermoPro TP357`() {
        assertEquals("ThermoPro TP357", client.name)
    }

    @Test
    fun `iconRes is null`() {
        assertNull(client.iconRes)
    }

    @Test
    fun `validateChecksum valid packet returns true`() {
        val packet = createDataPacket(
            pageIndex = 7,
            points = listOf(215 to 50, 220 to 51, 225 to 52, 230 to 53, 235 to 54)
        )

        assertTrue(client.validateChecksum(packet))
    }

    @Test
    fun `validateChecksum corrupted packet returns false`() {
        val packet = createDataPacket(
            pageIndex = 7,
            points = listOf(215 to 50, 220 to 51, 225 to 52, 230 to 53, 235 to 54)
        ).apply {
            this[lastIndex] = (this[lastIndex] + 1).toByte()
        }

        assertFalse(client.validateChecksum(packet))
    }

    @Test
    fun `parseDataPacket valid five point data returns all points`() {
        val packet = createDataPacket(
            pageIndex = 12,
            points = listOf(215 to 50, 220 to 51, 225 to 52, 230 to 53, 235 to 54)
        )

        val parsed = client.parseDataPacket(packet)

        assertNotNull(parsed)
        assertEquals(5, parsed!!.size)
        assertEquals(21.5, parsed[0].temperature, 0.0001)
        assertEquals(50.0, parsed[0].humidity, 0.0001)
        assertEquals(23.5, parsed[4].temperature, 0.0001)
        assertEquals(54.0, parsed[4].humidity, 0.0001)
        assertEquals(22.5, parsed[2].temperature, 0.0001)
        assertEquals(52.0, parsed[2].humidity, 0.0001)
    }

    @Test
    fun `parsePageIndex valid header extracts little-endian index`() {
        val packet = createDataPacket(
            pageIndex = 513,
            points = listOf(200 to 40, 210 to 41, 220 to 42, 230 to 43, 240 to 44)
        )

        val pageIndex = client.parsePageIndex(packet)

        assertEquals(513, pageIndex)
    }

    @Test
    fun `parseDataPacket negative temperature handles signed Int16`() {
        val packet = createDataPacket(
            pageIndex = 1,
            points = listOf(-55 to 60, 120 to 61, 130 to 62, 140 to 63, 150 to 64)
        )

        val parsed = client.parseDataPacket(packet)

        assertNotNull(parsed)
        assertEquals(-5.5, parsed!![0].temperature, 0.0001)
        assertEquals(60.0, parsed[0].humidity, 0.0001)
    }

    @Test
    fun `parseDataPacket boundary temp 600 is included`() {
        val packet = createDataPacket(
            pageIndex = 1,
            points = listOf(600 to 50, 200 to 40, 200 to 40, 200 to 40, 200 to 40)
        )
        val parsed = client.parseDataPacket(packet)
        assertNotNull(parsed)
        assertEquals(60.0, parsed!![0].temperature, 0.0001)
    }

    @Test
    fun `parseDataPacket boundary temp negative 200 is included`() {
        val packet = createDataPacket(
            pageIndex = 1,
            points = listOf(-200 to 50, 200 to 40, 200 to 40, 200 to 40, 200 to 40)
        )
        val parsed = client.parseDataPacket(packet)
        assertNotNull(parsed)
        assertEquals(-20.0, parsed!![0].temperature, 0.0001)
    }

    @Test
    fun `parseDataPacket boundary humidity 100 is included`() {
        val packet = createDataPacket(
            pageIndex = 1,
            points = listOf(200 to 100, 200 to 40, 200 to 40, 200 to 40, 200 to 40)
        )
        val parsed = client.parseDataPacket(packet)
        assertNotNull(parsed)
        assertEquals(100.0, parsed!![0].humidity, 0.0001)
    }

    @Test
    fun `parseDataPacket out of range temp excluded`() {
        val packet = createDataPacket(
            pageIndex = 1,
            points = listOf(601 to 50, 200 to 40, 200 to 40, 200 to 40, 200 to 40)
        )
        val parsed = client.parseDataPacket(packet)
        assertNotNull(parsed)
        assertEquals(4, parsed!!.size)
    }

    @Test
    fun `parseDataPacket corrupted checksum returns null`() {
        val packet = createDataPacket(
            pageIndex = 1,
            points = listOf(200 to 40, 200 to 40, 200 to 40, 200 to 40, 200 to 40)
        ).apply {
            this[lastIndex] = (this[lastIndex] + 1).toByte()
        }
        assertNull(client.parseDataPacket(packet))
    }

    @Test
    fun `validateChecksum empty array returns false`() {
        assertFalse(client.validateChecksum(byteArrayOf()))
    }

    @Test
    fun `validateChecksum single byte returns false`() {
        assertFalse(client.validateChecksum(byteArrayOf(0x00)))
    }

    private fun createDataPacket(pageIndex: Int, points: List<Pair<Int, Int>>): ByteArray {
        require(points.size == 5)

        val bytes = mutableListOf<Byte>()
        bytes.add(0xA7.toByte())
        bytes.add((pageIndex and 0xFF).toByte())
        bytes.add(((pageIndex ushr 8) and 0xFF).toByte())
        bytes.add(0x00)

        points.forEach { (tempRaw, humidity) ->
            bytes.add((tempRaw and 0xFF).toByte())
            bytes.add(((tempRaw ushr 8) and 0xFF).toByte())
            bytes.add((humidity and 0xFF).toByte())
        }

        val checksum = bytes.fold(0) { acc, byte -> (acc + (byte.toInt() and 0xFF)) and 0xFF }
        bytes.add(checksum.toByte())

        return bytes.toByteArray()
    }
}
