package com.adsamcik.temperaturedashboard

import com.adsamcik.temperaturedashboard.networking.AnalysisResult
import com.adsamcik.temperaturedashboard.networking.BleProtocolCollector
import com.adsamcik.temperaturedashboard.networking.ProtocolFindings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class BleProtocolCollectorTest {

    @Test
    fun addResult_confidenceThreshold70_rejectsBelowAndAcceptsAbove() {
        val collector = BleProtocolCollector()
        val lowUuid = UUID.randomUUID()
        val highUuid = UUID.randomUUID()
        val serviceUuid = UUID.randomUUID()

        collector.addResult(
            AnalysisResult(
                serviceUuid = serviceUuid,
                characteristicUuid = lowUuid,
                pattern = "AB",
                potentialTemperature = null,
                potentialHumidity = null,
                confidence = 0.4
            )
        )

        repeat(5) { index ->
            collector.addResult(
                AnalysisResult(
                    serviceUuid = serviceUuid,
                    characteristicUuid = highUuid,
                    pattern = "TH",
                    potentialTemperature = 20.0 + index,
                    potentialHumidity = 40.0 + index,
                    confidence = 0.9
                )
            )
        }

        val confirmed = collector.getConfirmedProtocols()

        assertFalse(confirmed.any { it.characteristicUuid == lowUuid })
        assertTrue(confirmed.any { it.characteristicUuid == highUuid })
    }

    @Test
    fun confidence_temporalConsistencyBonus_increasesScoreForSimilarReadings() {
        val stable = ProtocolFindings()
        val unstable = ProtocolFindings()
        val characteristicUuid = UUID.randomUUID()

        listOf(20.0, 21.0, 22.0).forEach { temp ->
            stable.addResult(
                AnalysisResult(
                    serviceUuid = null,
                    characteristicUuid = characteristicUuid,
                    pattern = "AB",
                    potentialTemperature = temp,
                    potentialHumidity = null,
                    confidence = 0.8
                )
            )
        }

        listOf(10.0, 25.0, 40.0).forEach { temp ->
            unstable.addResult(
                AnalysisResult(
                    serviceUuid = null,
                    characteristicUuid = characteristicUuid,
                    pattern = "AB",
                    potentialTemperature = temp,
                    potentialHumidity = null,
                    confidence = 0.8
                )
            )
        }

        assertTrue(stable.confidence > unstable.confidence)
        assertEquals(0.15, stable.confidence - unstable.confidence, 0.0001)
    }

    @Test
    fun confidence_commonPatternBonus_addsExpectedBoost() {
        val withCommonPattern = ProtocolFindings()
        val withoutCommonPattern = ProtocolFindings()
        val characteristicUuid = UUID.randomUUID()

        repeat(2) { index ->
            withCommonPattern.addResult(
                AnalysisResult(
                    serviceUuid = null,
                    characteristicUuid = characteristicUuid,
                    pattern = "TH",
                    potentialTemperature = 20.0 + index,
                    potentialHumidity = 50.0 + index,
                    confidence = 0.8
                )
            )
            withoutCommonPattern.addResult(
                AnalysisResult(
                    serviceUuid = null,
                    characteristicUuid = characteristicUuid,
                    pattern = "AB",
                    potentialTemperature = 20.0 + index,
                    potentialHumidity = 50.0 + index,
                    confidence = 0.8
                )
            )
        }

        assertEquals(0.1, withCommonPattern.confidence - withoutCommonPattern.confidence, 0.0001)
    }

    @Test
    fun addResult_getConfirmedProtocols_returnsBestProtocolByConfidence() {
        val collector = BleProtocolCollector()
        val lowerUuid = UUID.randomUUID()
        val higherUuid = UUID.randomUUID()

        repeat(3) { index ->
            collector.addResult(
                AnalysisResult(
                    serviceUuid = null,
                    characteristicUuid = lowerUuid,
                    pattern = "TH",
                    potentialTemperature = 20.0 + index,
                    potentialHumidity = 40.0 + index,
                    confidence = 0.6
                )
            )
        }

        repeat(5) { index ->
            collector.addResult(
                AnalysisResult(
                    serviceUuid = null,
                    characteristicUuid = higherUuid,
                    pattern = "TH",
                    potentialTemperature = 22.0 + index,
                    potentialHumidity = 42.0 + index,
                    confidence = 0.9
                )
            )
        }

        val best = collector.getConfirmedProtocols().maxByOrNull { it.confidence }

        assertEquals(higherUuid, best?.characteristicUuid)
    }
}
