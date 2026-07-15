package com.vigia.core.sensor.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveSpeechDetectorTest {

    @Test
    fun `steady ambient noise does not count as speech`() {
        val detector = detector()
        val results = List(40) { detector.process(0.03f) }

        assertTrue(results.contains(AdaptiveSpeechDetector.Result.Ready))
        assertFalse(results.contains(AdaptiveSpeechDetector.Result.SpeechStarted))
    }

    @Test
    fun `speech ends when voice stops despite continuing ambient noise`() {
        val detector = detector()
        repeat(10) { detector.process(0.03f) }

        val speechResults = List(8) { detector.process(0.065f) }
        val ambientResults = List(12) { detector.process(0.03f) }

        assertTrue(speechResults.contains(AdaptiveSpeechDetector.Result.SpeechStarted))
        assertTrue(ambientResults.contains(AdaptiveSpeechDetector.Result.SpeechEnded))
    }

    @Test
    fun `brief noise spike does not open a turn`() {
        val detector = detector()
        repeat(10) { detector.process(0.02f) }

        val spike = detector.process(0.09f)
        val afterSpike = detector.process(0.02f)

        assertEquals(AdaptiveSpeechDetector.Result.None, spike)
        assertEquals(AdaptiveSpeechDetector.Result.None, afterSpike)
    }

    private fun detector() = AdaptiveSpeechDetector(
        calibrationFrames = 5,
        onsetFrames = 3,
        hangoverFrames = 8,
    )
}
