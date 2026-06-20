package com.vigia.core.sensor.voice

import com.vigia.core.model.ConversationTurn
import java.util.LinkedList
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maintains a bounded rolling window of conversational turns for context injection.
 *
 * Injecting history into each VigiaSearch query enables follow-up questions
 * ("What about the road after that?" / "How fast should I go through it?") without
 * the user re-explaining the topic — the same mechanism Gemini Live uses to maintain
 * multi-turn conversational coherence.
 *
 * The backend receives [conversationHistory] in the search request body and can
 * prepend it as conversation context to the system prompt before the LLM reasons
 * over the current query.
 */
@Singleton
class ConversationContextManager @Inject constructor() {

    private val turns = LinkedList<ConversationTurn>()

    fun addUserTurn(text: String) {
        turns.addLast(ConversationTurn(ConversationTurn.Role.USER, text, System.currentTimeMillis()))
        trim()
    }

    fun addAssistantTurn(text: String) {
        turns.addLast(ConversationTurn(ConversationTurn.Role.ASSISTANT, text, System.currentTimeMillis()))
        trim()
    }

    fun history(): List<ConversationTurn> = turns.toList()

    fun clear() = turns.clear()

    private fun trim() {
        while (turns.size > MAX_TURNS) turns.removeFirst()
    }

    companion object {
        private const val MAX_TURNS = 10  // 5 user + 5 assistant turns
    }
}
