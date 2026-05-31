package com.adsamcik.temperaturedashboard.shared.alerts

import com.adsamcik.temperaturedashboard.core.model.Sensor

interface Notifier {
    fun notify(sensor: Sensor, title: String, body: String)
}
