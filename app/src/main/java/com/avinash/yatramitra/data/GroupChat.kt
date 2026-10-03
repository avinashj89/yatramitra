package com.avinash.yatramitra.data

import com.avinash.yatramitra.model.ChatMessage
import com.avinash.yatramitra.model.ItinerarySuggestion
import com.avinash.yatramitra.model.RouteSuggestion

/** Pure helpers for the Group chat screen, covered by GroupChatTest. */
object GroupChat {

    const val MAX_LENGTH = 1000

    /** The Group chat as one timeline, oldest first: new chat messages plus the route and
     *  itinerary suggestions posted before the chat existed, so no earlier message is lost. */
    fun timeline(
        chat: List<ChatMessage>,
        routeSuggestions: List<RouteSuggestion>,
        itinerarySuggestions: List<ItinerarySuggestion>
    ): List<ChatMessage> {
        val older = routeSuggestions.map { ChatMessage("route-${it.id}", it.authorMemberId, it.authorName, it.text, it.createdAtMillis) } +
            itinerarySuggestions.map { ChatMessage("itinerary-${it.id}", it.authorMemberId, it.authorName, it.text, it.createdAtMillis) }
        return (chat + older).sortedWith(compareBy<ChatMessage> { it.createdAtMillis }.thenBy { it.id })
    }

    /** What actually gets sent for typed [text]: trimmed and capped, or null if there's nothing to send. */
    fun cleanMessage(text: String): String? = text.trim().take(MAX_LENGTH).takeIf { it.isNotBlank() }

    /** Whether to show the sender's name above [current]: not when the same person also sent
     *  the message just before it within five minutes, like other chat apps. */
    fun showsAuthor(previous: ChatMessage?, current: ChatMessage): Boolean =
        previous == null ||
            previous.authorMemberId != current.authorMemberId ||
            current.createdAtMillis - previous.createdAtMillis > 5 * 60_000L
}
