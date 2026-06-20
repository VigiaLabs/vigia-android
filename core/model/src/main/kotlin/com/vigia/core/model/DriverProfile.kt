package com.vigia.core.model

/**
 * Driver profile that reshapes every advisory threshold and TTS delivery globally.
 *
 * [sProfile] is a linear scale factor applied to all warning distances / lead times.
 * A value < 1.0 compresses thresholds (expert needs less warning); > 1.0 expands them.
 *
 * Threshold derivation (canonical formulas):
 *   WarningDistance   = BaseWarningDistance  × sProfile
 *   TtcAlertThreshold = BaseTtc             × sProfile   // EXPERT 1.5s, NEW 4.5s, ELDERLY 9.0s
 *   DriftSensitivity  = BaseDriftDeg        ÷ sProfile   // ELDERLY trips at smaller oscillation
 */
enum class DriverProfile(
    val sProfile: Float,
    val ttsRate: Float,
    val ttsGainDb: Float,
) {
    EXPERT (sProfile = 0.5f, ttsRate = 1.10f, ttsGainDb = 0f),   // terse, only CRITICAL
    NEW    (sProfile = 1.5f, ttsRate = 0.95f, ttsGainDb = 2f),   // verbose coaching
    ELDERLY(sProfile = 3.0f, ttsRate = 0.80f, ttsGainDb = 4f),   // early, slow, loud
}
