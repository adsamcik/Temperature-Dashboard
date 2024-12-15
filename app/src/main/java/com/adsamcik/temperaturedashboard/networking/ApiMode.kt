package com.adsamcik.temperaturedashboard.networking

/**
 * API Modes supported by the connector.
 */
enum class ApiMode {
    LATEST,  // Get the latest value
    DELTA,   // Get delta results since some date (device decides how)
    HISTORY  // Get the full history (device decides how)
}