package com.adsamcik.temperaturedashboard.decoder.builtins

import com.adsamcik.temperaturedashboard.decoder.api.DecodeContext
import com.adsamcik.temperaturedashboard.decoder.api.DecodedValue
import com.adsamcik.temperaturedashboard.decoder.builtins.sig.CurrentTimeDecoder
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class CurrentTimeDecoderTest {
    @Test
    fun `decodes 2024-05-30T15_28_00 Friday`() {
        val payload = byteArrayOf(0xE8.toByte(), 0x07, 0x05, 0x1E, 0x0F, 0x1C, 0x00, 0x05, 0x00, 0x00)
        val result = CurrentTimeDecoder.decode(payload, DecodeContext(characteristicUuid = "2A2B"))!!
        val dateTimeField = result.fields.first { it.name == "Date Time" }
        assertThat((dateTimeField.value as DecodedValue.StringValue).v).contains("2024-05-30T15:28")
        val dayOfWeekField = result.fields.first { it.name == "Day of Week" }
        assertThat((dayOfWeekField.value as DecodedValue.StringValue).v).isEqualTo("FRIDAY")
    }

    @Test
    fun `too short returns null`() {
        assertThat(CurrentTimeDecoder.decode(byteArrayOf(0x00), DecodeContext(characteristicUuid = "2A2B"))).isNull()
    }
}
