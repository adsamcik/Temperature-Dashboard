package com.adsamcik.temperaturedashboard.networking

import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Collects and organizes results from BLE protocol analysis to help identify patterns
 * and determine the most likely protocol structure.
 */
class BleProtocolCollector {
    private val _protocolFindings = MutableStateFlow<Map<UUID, ProtocolFindings>>(emptyMap())
    val protocolFindings: StateFlow<Map<UUID, ProtocolFindings>> = _protocolFindings
    
    fun addResult(result: AnalysisResult) {
        if (result.characteristicUuid == null) return
        
        val findings = _protocolFindings.value.toMutableMap()
        val characteristicFindings = findings.getOrPut(result.characteristicUuid) { 
            ProtocolFindings() 
        }
        
        characteristicFindings.addResult(result)
        
        if (characteristicFindings.confidence > 0.7) {
            characteristicFindings.confirmedPattern = result.pattern
        }
        
        _protocolFindings.value = findings
    }
    
    fun getConfirmedProtocols(): List<ConfirmedProtocol> {
        return _protocolFindings.value
            .filter { it.value.confidence > 0.7 }
            .map { (uuid, findings) ->
                ConfirmedProtocol(
                    serviceUuid = findings.serviceUuid,
                    characteristicUuid = uuid,
                    pattern = findings.confirmedPattern ?: "",
                    confidence = findings.confidence,
                    dataType = when {
                        findings.hasTemperatureAndHumidity -> ProtocolType.TEMPERATURE_AND_HUMIDITY
                        findings.hasTemperature -> ProtocolType.TEMPERATURE
                        else -> ProtocolType.UNKNOWN
                    }
                )
            }
    }
}

class ProtocolFindings {
    private val results = mutableListOf<AnalysisResult>()
    private val maxResults = 10
    
    var confirmedPattern: String? = null
    var serviceUuid: UUID? = null
    val confidence: Double
        get() = calculateConfidence()
    
    val hasTemperature: Boolean
        get() = results.any { it.potentialTemperature != null }
    
    val hasTemperatureAndHumidity: Boolean
        get() = results.any { it.potentialTemperature != null && it.potentialHumidity != null }
    
    fun addResult(result: AnalysisResult) {
        results.add(result)
        if (results.size > maxResults) {
            results.removeAt(0)
        }
        if (result.serviceUuid != null) {
            serviceUuid = result.serviceUuid
        }
    }
    
    private fun calculateConfidence(): Double {
        if (results.isEmpty()) return 0.0
        
        // Calculate confidence based on:
        // 1. Consistency of the pattern
        // 2. Number of samples
        // 3. Individual result confidences
        
        val patternGroups = results.groupBy { it.pattern }
        val mostCommonPattern = patternGroups.maxByOrNull { it.value.size }
        
        val patternConsistency = mostCommonPattern?.value?.size?.toDouble() ?: 0.0
        val normalizedConsistency = patternConsistency / results.size
        
        val sampleWeight = (results.size.toDouble() / maxResults).coerceAtMost(1.0)
        
        val averageConfidence = results
            .filter { it.pattern == mostCommonPattern?.key }
            .map { it.confidence }
            .average()
        
        val tempValues = results.mapNotNull { it.potentialTemperature }
        val temporalBonus = if (tempValues.size >= 3) {
            val range = (tempValues.maxOrNull() ?: 0.0) - (tempValues.minOrNull() ?: 0.0)
            if (range < 5.0) 0.15 else if (range < 10.0) 0.05 else 0.0
        } else {
            0.0
        }

        val patternBonus = if (
            mostCommonPattern?.key?.let { pattern ->
                listOf("TH", "HT", "xTHx", "xTH", "THx").any { pattern.contains(it) }
            } == true
        ) {
            0.1
        } else {
            0.0
        }

        return (normalizedConsistency * 0.35 + sampleWeight * 0.15 + averageConfidence * 0.25 + temporalBonus + patternBonus)
            .coerceIn(0.0, 1.0)
    }
}

data class ConfirmedProtocol(
    val serviceUuid: UUID?,
    val characteristicUuid: UUID,
    val pattern: String,
    val confidence: Double,
    val dataType: ProtocolType
)

enum class ProtocolType {
    TEMPERATURE,
    TEMPERATURE_AND_HUMIDITY,
    UNKNOWN
}
