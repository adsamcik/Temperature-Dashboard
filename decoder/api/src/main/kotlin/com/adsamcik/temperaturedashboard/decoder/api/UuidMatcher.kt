package com.adsamcik.temperaturedashboard.decoder.api

object UuidMatcher {
    private const val BaseSuffix = "-0000-1000-8000-00805F9B34FB"

    fun matches(target: String, candidate: String?): Boolean {
        if (candidate == null) return false
        val normalizedTarget = target.uppercase()
        val normalizedCandidate = candidate.uppercase()
        if (normalizedTarget == normalizedCandidate) return true

        val targetFull = if (normalizedTarget.length == 4) {
            "0000$normalizedTarget$BaseSuffix"
        } else {
            normalizedTarget
        }
        val candidateFull = if (normalizedCandidate.length == 4) {
            "0000$normalizedCandidate$BaseSuffix"
        } else {
            normalizedCandidate
        }
        return targetFull == candidateFull
    }
}
