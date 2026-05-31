package com.adsamcik.temperaturedashboard.decoder.builtins.util

internal fun ByteArray.leU16At(offset: Int): Int =
    (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)

internal fun ByteArray.leS16At(offset: Int): Int {
    val value = leU16At(offset)
    return if (value and 0x8000 != 0) value or -0x10000 else value
}

internal fun ByteArray.leU24At(offset: Int): Int =
    (this[offset].toInt() and 0xFF) or
        ((this[offset + 1].toInt() and 0xFF) shl 8) or
        ((this[offset + 2].toInt() and 0xFF) shl 16)

internal fun ByteArray.leU32At(offset: Int): Long =
    (this[offset].toLong() and 0xFF) or
        ((this[offset + 1].toLong() and 0xFF) shl 8) or
        ((this[offset + 2].toLong() and 0xFF) shl 16) or
        ((this[offset + 3].toLong() and 0xFF) shl 24)
