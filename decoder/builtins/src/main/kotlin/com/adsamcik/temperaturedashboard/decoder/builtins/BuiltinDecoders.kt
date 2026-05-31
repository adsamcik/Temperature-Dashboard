package com.adsamcik.temperaturedashboard.decoder.builtins

import com.adsamcik.temperaturedashboard.decoder.api.Decoder
import com.adsamcik.temperaturedashboard.decoder.api.DecoderRegistry
import com.adsamcik.temperaturedashboard.decoder.api.DeviceProfile
import com.adsamcik.temperaturedashboard.decoder.api.DeviceProfileRegistry
import com.adsamcik.temperaturedashboard.decoder.builtins.bthome.BTHomeV2Decoder
import com.adsamcik.temperaturedashboard.decoder.builtins.profile.ThermoProTP35xNotifyDecoder
import com.adsamcik.temperaturedashboard.decoder.builtins.profile.ThermoProTP35xProfile
import com.adsamcik.temperaturedashboard.decoder.builtins.sig.BatteryLevelDecoder
import com.adsamcik.temperaturedashboard.decoder.builtins.sig.BloodPressureDecoder
import com.adsamcik.temperaturedashboard.decoder.builtins.sig.CurrentTimeDecoder
import com.adsamcik.temperaturedashboard.decoder.builtins.sig.DeviceNameDecoder
import com.adsamcik.temperaturedashboard.decoder.builtins.sig.EssHumidityDecoder
import com.adsamcik.temperaturedashboard.decoder.builtins.sig.EssPressureDecoder
import com.adsamcik.temperaturedashboard.decoder.builtins.sig.EssTemperatureDecoder
import com.adsamcik.temperaturedashboard.decoder.builtins.sig.FirmwareRevisionDecoder
import com.adsamcik.temperaturedashboard.decoder.builtins.sig.HealthThermometerDecoder
import com.adsamcik.temperaturedashboard.decoder.builtins.sig.HeartRateMeasurementDecoder
import com.adsamcik.temperaturedashboard.decoder.builtins.sig.ManufacturerNameDecoder
import com.adsamcik.temperaturedashboard.decoder.builtins.sig.ModelNumberDecoder
import com.adsamcik.temperaturedashboard.decoder.builtins.sig.WeightMeasurementDecoder

object BuiltinDecoders {
    val ALL: List<Decoder> = listOf(
        // Vendor-specific decoders take precedence over generic SIG decoders.
        ThermoProTP35xNotifyDecoder,
        BatteryLevelDecoder,
        EssTemperatureDecoder,
        EssHumidityDecoder,
        EssPressureDecoder,
        HealthThermometerDecoder,
        HeartRateMeasurementDecoder,
        CurrentTimeDecoder,
        WeightMeasurementDecoder,
        BloodPressureDecoder,
        ManufacturerNameDecoder,
        ModelNumberDecoder,
        FirmwareRevisionDecoder,
        DeviceNameDecoder,
        BTHomeV2Decoder,
    )

    val PROFILES: List<DeviceProfile> = listOf(
        ThermoProTP35xProfile,
    )

    fun newRegistry(): DecoderRegistry = DecoderRegistry(ALL)
    fun newProfileRegistry(): DeviceProfileRegistry = DeviceProfileRegistry(PROFILES)
}

