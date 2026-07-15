package com.vigia.feature.copilot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceStopCommandTest {

    @Test
    fun `recognises explicit hands free stop phrases`() {
        assertTrue(isVoiceSessionStopCommand("stop"))
        assertTrue(isVoiceSessionStopCommand("Okay, thank you."))
        assertTrue(isVoiceSessionStopCommand("Please stop"))
        assertTrue(isVoiceSessionStopCommand("Thank you very much, Vigia"))
        assertTrue(isVoiceSessionStopCommand("That's all!"))
    }

    @Test
    fun `does not treat road questions containing stop as commands`() {
        assertFalse(isVoiceSessionStopCommand("Where is the nearest bus stop?"))
        assertFalse(isVoiceSessionStopCommand("Show me the stop sign rules"))
    }
}
