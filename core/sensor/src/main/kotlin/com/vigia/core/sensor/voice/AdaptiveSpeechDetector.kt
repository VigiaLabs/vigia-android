package com.vigia.core.sensor.voice

import kotlin.math.max

internal class AdaptiveSpeechDetector(
    private val calibrationFrames: Int,
    private val onsetFrames: Int,
    private val hangoverFrames: Int,
    private val minimumSpeechRms: Float = 0.008f,
    private val onsetNoiseRatio: Float = 1.5f,
    private val offsetNoiseRatio: Float = 1.35f,
    private val thresholdMargin: Float = 0.003f,
) {
    enum class Result { None, Ready, SpeechStarted, SpeechEnded }

    private var calibratedFrames = 0
    private var calibrationSum = 0f
    private var onsetCount = 0
    private var hangoverCount = 0
    private var speaking = false
    private var noiseFloor = minimumSpeechRms / 2f

    fun process(rms: Float): Result {
        val level = rms.coerceIn(0f, 1f)
        if (calibratedFrames < calibrationFrames) {
            calibrationSum += level
            calibratedFrames++
            if (calibratedFrames == calibrationFrames) {
                noiseFloor = max(calibrationSum / calibrationFrames, 0.001f)
                return Result.Ready
            }
            return Result.None
        }

        if (!speaking) {
            val onsetThreshold = max(minimumSpeechRms, noiseFloor * onsetNoiseRatio + thresholdMargin)
            if (level >= onsetThreshold) {
                onsetCount++
                if (onsetCount >= onsetFrames) {
                    speaking = true
                    onsetCount = 0
                    hangoverCount = 0
                    return Result.SpeechStarted
                }
            } else {
                onsetCount = 0
                noiseFloor = noiseFloor * 0.94f + level * 0.06f
            }
            return Result.None
        }

        val offsetThreshold = max(minimumSpeechRms, noiseFloor * offsetNoiseRatio + thresholdMargin)
        if (level < offsetThreshold) {
            hangoverCount++
            if (hangoverCount >= hangoverFrames) {
                speaking = false
                hangoverCount = 0
                noiseFloor = noiseFloor * 0.8f + level * 0.2f
                return Result.SpeechEnded
            }
        } else {
            hangoverCount = 0
        }
        return Result.None
    }
}
