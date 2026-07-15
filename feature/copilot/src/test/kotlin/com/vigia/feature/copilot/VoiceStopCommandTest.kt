package com.vigia.feature.copilot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceStopCommandTest {

    @Test
    fun `recognises explicit hands free stop phrases`() {
        assertTrue(isVoiceSessionStopCommand("stop"))
        assertTrue(isVoiceSessionStopCommand("Okay, thank you."))
        assertTrue(isVoiceSessionStopCommand("Please stop"))
        assertTrue(isVoiceSessionStopCommand("Stop it now"))
        assertTrue(isVoiceSessionStopCommand("Could you stop"))
        assertTrue(isVoiceSessionStopCommand("Thankyou"))
        assertTrue(isVoiceSessionStopCommand("Thank you very much, Vigia"))
        assertTrue(isVoiceSessionStopCommand("That's all!"))
        assertTrue(isVoiceSessionStopCommand("Ruk jao"))
        assertTrue(isVoiceSessionStopCommand("நன்றி"))
    }

    @Test
    fun `does not treat road questions containing stop as commands`() {
        assertFalse(isVoiceSessionStopCommand("Where is the nearest bus stop?"))
        assertFalse(isVoiceSessionStopCommand("Show me the stop sign rules"))
    }

    @Test
    fun `converts internal progress into natural voice acknowledgements`() {
        assertEquals(null, naturalVoiceProgress("Classifying intent"))
        assertEquals(
            "One moment while I check the road records.",
            naturalVoiceProgress("Searching NHAI documents"),
        )
    }

    @Test
    fun `detects playback echo without suppressing stop commands`() {
        val answer = "The road authority recommends reducing speed near the damaged shoulder."
        assertTrue(isLikelyPlaybackEcho("road authority recommends reducing speed near the damaged shoulder", answer))
        assertFalse(isLikelyPlaybackEcho("stop", answer))
        assertFalse(isLikelyPlaybackEcho("Who is the contractor?", answer))
    }
}
